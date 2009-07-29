package ibis.satin.impl.syncrewriter;

import ibis.satin.impl.syncrewriter.bcel.MethodGen;
import ibis.satin.impl.syncrewriter.util.Debug;

import java.io.PrintStream;

import java.util.ArrayList;

import java.lang.annotation.Annotation;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.Repository;

import org.apache.bcel.verifier.structurals.ControlFlowGraph;
import org.apache.bcel.verifier.structurals.InstructionContext;

import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.ConstantPoolGen;
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


/** This class represents a spawnable method. 
 *
 * A spawning method contains spawnable calls.
 */
public class SpawningMethod extends MethodGen {



    private class ResultNotStored extends Exception {}


    private ArrayList<SpawnableCall> spawnableCalls;
    private int indexSync;
    private SpawnSignature spawnSignature;

    private Debug d;



    /* public methods */

    /** Returns the spawnable calls in this spawnable method.
     *
     * @return The spawnable calls in this method.
     */
    public ArrayList<SpawnableCall> getSpawnableCalls() {
	return spawnableCalls;
    }



    /* package methods */

    SpawningMethod (Method method, String className, ConstantPoolGen constantPoolGen, 
	    SpawnSignature spawnSignature, int indexSync, Debug d) 
	throws NoSpawningMethodException, AssumptionFailure {

	super(method, className, constantPoolGen);

	MethodGen spawnSignatureGen = new MethodGen(spawnSignature.getMethod(), spawnSignature.getClassName(), constantPoolGen);

	ArrayList<SpawnableCall> spawnableCalls = getSpawnableCalls(constantPoolGen, spawnSignatureGen);

	if (spawnableCalls.size() > 0) {
	    this.spawnableCalls = spawnableCalls;
	    this.indexSync = indexSync;
	    this.spawnSignature = spawnSignature;
	    this.d = d;
	}
	else {
	    throw new NoSpawningMethodException();
	}
    }


    void rewrite(Analyzer analyzer) throws MethodRewriteFailure {
	d.log(0, "rewriting %s\n", getName());
	insertSync(analyzer);
	d.log(0, "rewrote %s\n", getName());
    }



    /* private methods */

    private void insertSync(Analyzer analyzer) throws MethodRewriteFailure {
	d.log(1, "trying to insert sync statement(s)\n");
	InstructionHandle[] ihs = 
	    analyzer.proposeSyncInsertion(this, new Debug(d.turnedOn(), d.getStartLevel() + 2));

	InstructionList instructionList = getInstructionList();

	for (InstructionHandle ih : ihs) {
	    InstructionHandle syncInvoke = 
		instructionList.insert(ih, 
			new INVOKEVIRTUAL(indexSync));
	    InstructionHandle newTarget = instructionList.insert(syncInvoke, 
		    spawnableCalls.get(0).getObjectReference().getInstruction());
	    // the same objectReference for every sync insertion
	    instructionList.redirectBranches(ih, newTarget);
	    d.log(2, "inserted sync()\n");
	}
	d.log(1, "inserted sync statement(s)\n");
    }





    /* determine the spawnable calls */

    private boolean containsType(ObjectType type, ObjectType[] types) {
	for (ObjectType i : types) {
	    if (i.equals(type) || type.subclassOf(i)) return true;
	}
	return false;
    }

    private boolean hasRightType(CodeExceptionGen codeException, MethodGen method) {
	ObjectType catchType = codeException.getCatchType();
	if (catchType == null) return false;
	String[] exceptionTypeNames = method.getExceptions();
	ObjectType[] exceptionTypes = new ObjectType[exceptionTypeNames.length];
	for (int i = 0; i < exceptionTypeNames.length; i++) {
	    exceptionTypes[i] = new ObjectType(exceptionTypeNames[i]);
	}
	return containsType(catchType, exceptionTypes);
    }


    private ArrayList<CodeExceptionGen> getExceptionHandlers(InstructionHandle ih, MethodGen spawnSignature) {
	CodeExceptionGen[] codeExceptions = getExceptionHandlers();
	ArrayList<CodeExceptionGen> result = new ArrayList<CodeExceptionGen>();

	for (CodeExceptionGen codeException : codeExceptions) {
	    // codeException.containsTarget has a BUG????
	    if (codeException.containsTarget(ih) && hasRightType(codeException, spawnSignature)) {
		result.add(codeException);
	    }
	}

	return result;
    }


    private void getIndicesStores(InstructionHandle start,
	    InstructionHandle end, ArrayList<Integer> resultIndices) {

	for (InstructionHandle current = start.getNext() ;current != end.getNext(); current = current.getNext()) {
	    try {
		int indexStore = getIndexStore(current);
		if (!resultIndices.contains(indexStore)) {
		    resultIndices.add(indexStore);
		}
	    }
	    catch (ClassCastException e) {
		// no problem, just not a store 
	    }
	}
    }


    private SpawnableCall getSpawnableCallWithException(InstructionHandle invokeInstruction, 
	    ArrayList<CodeExceptionGen> exceptionHandlers) {

	ArrayList<Integer> resultIndices = new ArrayList<Integer>();
	for (CodeExceptionGen exceptionHandler : exceptionHandlers) {
	    InstructionHandle start = exceptionHandler.getHandlerPC(); 
	    InstructionHandle end = getEndExceptionHandler(exceptionHandler);
	    getIndicesStores(start, end, resultIndices);
	}

	Integer[] dummy = new Integer[resultIndices.size()];

	return new SpawnableCall(invokeInstruction,
		getObjectReferenceLoadInstruction(invokeInstruction),
		resultIndices.toArray(dummy));
    }


    private SpawnableCall getSpawnableCallWithException(InstructionHandle invokeInstruction, MethodGen spawnSignatureGen) {
	ArrayList<CodeExceptionGen> exceptionHandlers = getExceptionHandlers(invokeInstruction, spawnSignatureGen);

	if (exceptionHandlers.size() == 0) {
	    return new SpawnableCall(invokeInstruction, getObjectReferenceLoadInstruction(invokeInstruction), 
		    SpawnableCall.Type.EXCEPTIONS_NOT_HANDLED);
	}
	else {
	    return getSpawnableCallWithException(invokeInstruction, exceptionHandlers);
	}
    }


    /* Find the index of the variable in which a spawnable call stores.
     */
    private Integer[] findIndexStore(InstructionHandle spawnableMethodInvoke) throws ResultNotStored {
	InstructionHandle[] stackConsumers = findInstructionConsumers(spawnableMethodInvoke);
	if (stackConsumers.length != 1) {
	    /*
	    throw new Error("Uncovered situation, invoke instruction's " + 
		    "result is consumed by multiple instructions. " + 
		    "Is there controlflow right after the spawnable invoke???");
		    */
	    throw new ResultNotStored();
	    // I think this is a better solution. Controlflow right after the
	    // invoke is something that is hard to analyze and rare. Better to
	    // just mark it as a result that isn't stored.
	}
	try {
	    Integer[] indices = new Integer[1];
	    indices[0] = getIndexStore(stackConsumers[0]);
	    return indices;
	}
	catch (ClassCastException e) {
	    throw new ResultNotStored();
	}
    }


    private SpawnableCall getSpawnableCallReturningValue(InstructionHandle invokeInstruction) {
	try {
	    return new SpawnableCall(invokeInstruction, 
		    getObjectReferenceLoadInstruction(invokeInstruction), 
		    findIndexStore(invokeInstruction));
	}
	catch (ResultNotStored e) {
	    return new SpawnableCall(invokeInstruction,
		    getObjectReferenceLoadInstruction(invokeInstruction),
		    SpawnableCall.Type.RESULT_NOT_STORED);
	}
    }


    private ArrayList<SpawnableCall> getSpawnableCalls(ConstantPoolGen constantPoolGen, MethodGen spawnSignatureGen) throws 
	AssumptionFailure {

	    ArrayList<SpawnableCall> spawnableCalls = 
		new ArrayList<SpawnableCall>();

	    InstructionList il = getInstructionList(); 
	    int indexSpawnableCall = 
		constantPoolGen.lookupMethodref(spawnSignatureGen);
	    if (indexSpawnableCall < 0) return spawnableCalls;

	    INVOKEVIRTUAL spawnableInvoke = 
		new INVOKEVIRTUAL(indexSpawnableCall);


	    InstructionHandle ih = il.getStart();

	    do {
		Instruction instruction = ih.getInstruction();
		if (instruction.equals(spawnableInvoke) && (instruction.produceStack(constantPoolGen) > 0)) {
		    spawnableCalls.add(getSpawnableCallReturningValue(ih));
		}
		else if (instruction.equals(spawnableInvoke) && 
			(instruction.produceStack(constantPoolGen) == 0) &&
			spawnSignatureGen.getExceptions().length > 0) {
		    spawnableCalls.add(getSpawnableCallWithException(ih, spawnSignatureGen));
			}
		else if (instruction.equals(spawnableInvoke) && (instruction.produceStack(constantPoolGen) == 0)) {
		    throw new AssumptionFailure("Not satisfying assumption that spawnable method returns something or throws something");
		}
		else {
		    // ok
		}
	    } while((ih = ih.getNext()) != null);

	    return spawnableCalls;
	}
}
