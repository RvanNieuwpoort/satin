package ibis.satin.impl.syncrewriter;


import java.io.PrintStream;

import java.util.ArrayList;

import java.lang.annotation.Annotation;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.Repository;

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.StackConsumer;
import org.apache.bcel.generic.StackProducer;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.DASTORE;
import org.apache.bcel.generic.ALOAD;


public class SpawnableMethod extends MethodGen {


    private ArrayList<SpawnableMethodCall> spawnableCalls;
    private int indexSync;
    private Method spawnSignature;

    private Debug d;


    SpawnableMethod (Method method, String className, ConstantPoolGen constantPoolGen, 
	    Method spawnSignature, int indexSync, Debug d) 
	throws NoSpawnableMethodException {

	super(method, className, constantPoolGen);

	MethodGen spawnSignatureGen = new MethodGen(spawnSignature, className, constantPoolGen);
	ArrayList<SpawnableMethodCall> spawnableCalls = 
	    getSpawnableCalls(getInstructionList(), constantPoolGen, spawnSignatureGen);

	if (spawnableCalls.size() > 0) {
	    this.spawnableCalls = spawnableCalls;
	    this.indexSync = indexSync;
	    this.spawnSignature = spawnSignature;
	    this.d = d;
	}
	else {
	    throw new NoSpawnableMethodException();
	}
    }


    void rewrite(Analyzer analyzer) {
	d.log(0, "rewriting %s\n", getName());
	try {
	    d.log(1, "trying to insert a sync()\n");
	    insertSync(analyzer);
	}
	catch (NeverReadException e) {
	    d.warning("result of spawnable call %s is never read\n", 
		    spawnSignature.getName());
	}
    }


    public ArrayList<SpawnableMethodCall> getSpawnableCalls() {
	return spawnableCalls;
    }


    private void insertSync(Analyzer analyzer) throws NeverReadException {
	InstructionHandle[] ihs = 
	    analyzer.proposeSyncInsertion(this);

	InstructionList instructionList = getInstructionList();

	for (InstructionHandle ih : ihs) {
	    InstructionHandle syncInvoke = 
		instructionList.insert(ih, 
			new INVOKEVIRTUAL(indexSync));
	    instructionList.insert(syncInvoke, 
		    spawnableCalls.get(0).getObjectReference().getInstruction());
	    d.log(2, "inserted sync()\n");
	}
    }


    /* Get the local variable index of the result of the spawnable methode
     * invoke.
     */
    private int getLocalVariableIndexResult(InstructionHandle spawnableMethodInvoke,
	    InstructionList instructionList, ConstantPoolGen constantPoolGen) {

	InstructionHandle storeInstructionHandle = 
	    spawnableMethodInvoke.getNext();

	try {
	    StoreInstruction storeInstruction = (StoreInstruction) 
		(storeInstructionHandle.getInstruction());
	    return storeInstruction.getIndex();
	}
	catch (ClassCastException e) {
	}
	try {
	    ArrayInstruction arrayStoreInstruction = (ArrayInstruction)
		((StackConsumer)(storeInstructionHandle.getInstruction()));
	    InstructionHandle ih  = getObjectReferenceLoadInstruction
		(storeInstructionHandle, constantPoolGen);
	    ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
	    return objectLoadInstruction.getIndex();
	}
	catch (ClassCastException e) {
	}
	//out.printf("He, hier gaat het niet goed: %s\n");
	throw new Error("He, hier gaat het niet goed\n");
    }


    /* Get the corresponding object reference load instruction of instruction
     * ih.
     */
    private InstructionHandle getObjectReferenceLoadInstruction(InstructionHandle ih, 
	    ConstantPoolGen constantPoolGen) {
	Instruction instruction = ih.getInstruction();
	int stackConsumption = instruction.consumeStack(constantPoolGen);
	//stackConsumption--; // we're interested in the first one

	while (stackConsumption != 0) {
	    ih = ih.getPrev();
	    Instruction previousInstruction = ih.getInstruction();
	    //out.println(instruction);
	    //if (instruction instanceof StackProducer) {
	    stackConsumption -= 
		previousInstruction.produceStack(constantPoolGen);
	    //}
	    //if (instruction instanceof StackConsumer) {
	    stackConsumption += 
		previousInstruction.consumeStack(constantPoolGen);
	    //}
	}
	return ih;
    }


    private ArrayList<SpawnableMethodCall> getSpawnableCalls(InstructionList il, 
	    ConstantPoolGen constantPoolGen, MethodGen spawnSignatureGen) {

	int indexSpawnableCall = 
	    constantPoolGen.lookupMethodref(spawnSignatureGen);
	INVOKEVIRTUAL spawnableInvoke = 
	    new INVOKEVIRTUAL(indexSpawnableCall);

	ArrayList<SpawnableMethodCall> spawnableCalls = 
	    new ArrayList<SpawnableMethodCall>();

	InstructionHandle ih = il.getStart();
	do {
	    if (ih.getInstruction().equals(spawnableInvoke)) {
		SpawnableMethodCall spawnableCall = new SpawnableMethodCall(ih, 
			getObjectReferenceLoadInstruction(ih, constantPoolGen), 
			getLocalVariableIndexResult(ih, il, 
			    constantPoolGen));
		spawnableCalls.add(spawnableCall);
	    }
	} while((ih = ih.getNext()) != null);

	return spawnableCalls;
    }
}
