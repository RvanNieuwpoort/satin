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
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.StackConsumer;
import org.apache.bcel.generic.StackProducer;
import org.apache.bcel.generic.ExceptionThrower;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.DASTORE;
import org.apache.bcel.generic.ALOAD;


public class SpawnableMethod extends MethodGen {


    private ArrayList<SpawnableCall> spawnableCalls;
    private int indexSync;
    private Method spawnSignature;

    private Debug d;


    SpawnableMethod (Method method, String className, ConstantPoolGen constantPoolGen, 
	    Method spawnSignature, int indexSync, Debug d) 
	throws NoSpawnableMethodException, AssumptionFailure {

	super(method, className, constantPoolGen);

	MethodGen spawnSignatureGen = new MethodGen(spawnSignature, className, constantPoolGen);
	ArrayList<SpawnableCall> spawnableCalls = 
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


    void rewrite(Analyzer analyzer) throws MethodRewriteFailure {
	d.log(0, "rewriting %s\n", getName());
	insertSync(analyzer);
	d.log(0, "rewrote %s\n", getName());
    }


    public ArrayList<SpawnableCall> getSpawnableCalls() {
	return spawnableCalls;
    }


    private void insertSync(Analyzer analyzer) throws MethodRewriteFailure {
	d.log(1, "trying to insert sync statement(s)\n");
	InstructionHandle[] ihs = 
	    analyzer.proposeSyncInsertion(this, new Debug(d.turnedOn(), d.getStartLevel() + 2));

	InstructionList instructionList = getInstructionList();

	for (InstructionHandle ih : ihs) {
	    InstructionHandle syncInvoke = 
		instructionList.insert(ih, 
			new INVOKEVIRTUAL(indexSync));
	    instructionList.insert(syncInvoke, 
		    spawnableCalls.get(0).getObjectReference().getInstruction());
	    // the same objectReference for every sync insertion
	    d.log(2, "inserted sync()\n");
	}
	d.log(1, "inserted sync statement(s)\n");
    }


    /*
    private InstructionHandle findStackConsumer(InstructionHandle start, int consumingWords, ConstantPoolGen constantPoolGen) {
	int stackConsumption = 0;
	InstructionHandle stackConsumer = start;
	do {
	    Instruction stackConsumerInstruction = stackConsumer.getInstruction();
	    stackConsumption-=stackConsumerInstruction.produceStack(constantPoolGen);
	    stackConsumption+=stackConsumerInstruction.consumeStack(constantPoolGen);
	}
	while (stackConsumption == consumingWords);

	return stackConsumer;
    }
    */



    /* Get the local variable index of the result of the spawnable methode
     * invoke.
     */
    // assumption that the result needs to be stored and that it happens right
    // after the invoke of the spawnable call
    private int getLocalVariableIndexResult(InstructionHandle spawnableMethodInvoke,
	    InstructionList instructionList, ConstantPoolGen constantPoolGen) throws AssumptionFailure {

	/*
	System.err.printf("spawnableMethodInvoke: %s\n", spawnableMethodInvoke);
	    int producedOnStack = spawnableMethodInvoke.getInstruction().produceStack(constantPoolGen);
	    System.err.printf("producedOnStack: %d\n", producedOnStack);
	    if (producedOnStack <= 0) {
		throw new Error("The spawnable invoke doesn't return anything");
	    }
	    InstructionHandle stackConsumer = findStackConsumer(spawnableMethodInvoke.getNext(), producedOnStack, constantPoolGen);
	    System.err.printf("stackConsumer: %s\n", stackConsumer);
	    */


	/* assumption that the next instruction is going to be the store
	 * instruction
	 */
	InstructionHandle stackConsumer = spawnableMethodInvoke.getNext();


	try {
	    StoreInstruction storeInstruction = (StoreInstruction) 
		(stackConsumer.getInstruction());
	    return storeInstruction.getIndex();
	}
	catch (ClassCastException e) {
	}
	try {
	    ArrayInstruction arrayStoreInstruction = (ArrayInstruction)
		/*((StackConsumer)(*/stackConsumer.getInstruction()/*))*/;
	    InstructionHandle ih  = getObjectReferenceLoadInstruction
		(stackConsumer, constantPoolGen);
	    ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
	    return objectLoadInstruction.getIndex();
	}
	catch (ClassCastException e) {
	}
	try {
	    PUTFIELD putFieldInstruction = (PUTFIELD) stackConsumer.getInstruction();
	    InstructionHandle ih  = getObjectReferenceLoadInstruction
		(stackConsumer, constantPoolGen);
	    ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
	    return objectLoadInstruction.getIndex();
	}
	catch (ClassCastException e) {
	}
	
	throw new AssumptionFailure("Instruction following spawnableCall doesn't store the result");
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


    private ArrayList<SpawnableCall> getSpawnableCalls(InstructionList il, 
	    ConstantPoolGen constantPoolGen, MethodGen spawnSignatureGen) throws 
    AssumptionFailure {

	int indexSpawnableCall = 
	    constantPoolGen.lookupMethodref(spawnSignatureGen);
	INVOKEVIRTUAL spawnableInvoke = 
	    new INVOKEVIRTUAL(indexSpawnableCall);

	ArrayList<SpawnableCall> spawnableCalls = 
	    new ArrayList<SpawnableCall>();

	InstructionHandle ih = il.getStart();
	do {
	    Instruction instruction = ih.getInstruction();
	    if (instruction.equals(spawnableInvoke) && (instruction.produceStack(constantPoolGen) > 0)) {
		SpawnableCall spawnableCall = new SpawnableCall(ih, 
			getObjectReferenceLoadInstruction(ih, constantPoolGen), 
			getLocalVariableIndexResult(ih, il, 
			    constantPoolGen));
		spawnableCalls.add(spawnableCall);
	    }
	    else if (instruction instanceof ExceptionThrower) {
		SpawnableCall spawnableCall = new SpawnableCall(ih,
			getObjectReferenceLoadInstruction(ih, constantPoolGen));
	    }
	} while((ih = ih.getNext()) != null);

	return spawnableCalls;
    }
}
