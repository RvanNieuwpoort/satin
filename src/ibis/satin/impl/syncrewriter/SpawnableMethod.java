package ibis.satin.impl.syncrewriter;


import java.io.PrintStream;

import java.util.ArrayList;

import java.lang.annotation.Annotation;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.Repository;

import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.LocalVariableGen;
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



    private class ResultNotStored extends Exception {}


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
	    InstructionHandle newTarget = instructionList.insert(syncInvoke, 
		    spawnableCalls.get(0).getObjectReference().getInstruction());
	    instructionList.redirectBranches(ih, newTarget);
	    // the same objectReference for every sync insertion
	    d.log(2, "inserted sync()\n");
	}
	d.log(1, "inserted sync statement(s)\n");
    }


    private InstructionHandle findStackConsumer(InstructionHandle start, int consumingWords, ConstantPoolGen constantPoolGen) {
	int stackConsumption = 0;
	InstructionHandle current = start;
	do {
	    Instruction currentInstruction = current.getInstruction();
	    stackConsumption-=currentInstruction.produceStack(constantPoolGen);
	    stackConsumption+=currentInstruction.consumeStack(constantPoolGen);
	}
	while (stackConsumption < consumingWords && (current = current.getNext()) != null);

	if (stackConsumption < consumingWords) throw new Error("stack is not consumed");
	return current;
    }


    private boolean contains(ObjectType type, ObjectType[] types) {
	/*
	System.out.printf("Looking for type: %s\n", type);
	System.out.println("The method has the following exception types:");
	*/
	for (ObjectType i : types) {
	    /*
	    System.out.printf("  type: %s\n", i);
	    System.out.printf("  type.subclassOf(i): %b\n", type.subclassOf(i));
	    */
	    if (i.equals(type) || type.subclassOf(i)) return true;
	}
	return false;
    }


    private boolean hasRightType(CodeExceptionGen codeException, MethodGen method) {
	ObjectType catchType = codeException.getCatchType();
	/*
	System.out.printf("The catchType: %s\n", catchType);
	*/
	if (catchType == null) return false;
	String[] exceptionTypeNames = method.getExceptions();
	ObjectType[] exceptionTypes = new ObjectType[exceptionTypeNames.length];
	for (int i = 0; i < exceptionTypeNames.length; i++) {
	    exceptionTypes[i] = new ObjectType(exceptionTypeNames[i]);
	}
	return contains(catchType, exceptionTypes);
    }


    /* codeException.containsTarget(ih) has a BUG!! */
    private boolean containsTarget(CodeExceptionGen codeException, InstructionHandle ih) {
	int startPositionHandler = codeException.getStartPC().getPosition();
	int endPositionHandler = codeException.getEndPC().getPosition();
	int positionInstruction = ih.getPosition();
	return positionInstruction >= startPositionHandler && positionInstruction <= endPositionHandler;
    }

    private ArrayList<CodeExceptionGen> getExceptionHandlers(InstructionHandle ih, MethodGen spawnSignature) {
	CodeExceptionGen[] codeExceptions = getExceptionHandlers();
	/*
	System.out.printf("codeExceptions.length: %d\n", codeExceptions.length);
	System.out.printf("ih: %s\n", ih);
	*/
	ArrayList<CodeExceptionGen> result = new ArrayList<CodeExceptionGen>();


	for (CodeExceptionGen codeException : codeExceptions) {
	    /*
	    System.out.printf("codeException: %s\n", codeException);
	    System.out.printf("codeException.getStartPC(): %s\n", codeException.getStartPC());
	    System.out.printf("codeException.getEndPC(): %s\n", codeException.getEndPC());
	    System.out.printf("codeException.containsTarget(ih): %b\n", codeException.containsTarget(ih));
	    */
	    if (containsTarget(codeException, ih) && hasRightType(codeException, spawnSignature)) {
		result.add(codeException);
	    }
	}

	return result;
    }



    private InstructionHandle getEndExceptionHandler(CodeExceptionGen codeException) {
	LocalVariableGen[] localVars = getLocalVariables();
	InstructionHandle startHandler = codeException.getHandlerPC();

	for (LocalVariableGen localVar : localVars) {
	    InstructionHandle startScope = localVar.getStart();
	    InstructionHandle endScope = localVar.getEnd();

	    /*
	       System.out.printf("var: %s\n", localVar);
	       System.out.printf("startScope: %s\n", startScope);
	       System.out.printf("endScope: %s\n", endScope);
	       */

	    if (startScope == startHandler || startScope == startHandler.getNext() || 
		    startScope == startHandler.getNext().getNext() &&
		    localVar.getType().equals(codeException.getCatchType()))
		return localVar.getEnd().getPrev();
	}
	throw new Error("geen einde exceptionhandler...");
    }


    private void getIndicesStores(InstructionHandle start,
	    InstructionHandle end, 
	    ConstantPoolGen constantPoolGen, ArrayList<Integer> resultIndices) {

	/*
	System.out.printf("getLocalVariableIndexResults()\n");
	System.out.printf("  begin: %d\n", start.getPosition());
	System.out.printf("  end: %d\n", end.getPosition());
	*/
	// boolean firstLeftOut = false;
	// TODO

	for (InstructionHandle current = start.getNext() ;current != end.getNext(); current = current.getNext()) {
	    /*
	    System.out.printf("current (exception handler): %s\n", current);
	    */
	    try {
		int indexStore = getIndexStore(current, constantPoolGen);
		if (!resultIndices.contains(indexStore) /*&& !firstLeftOut*/) {
		    //firstLeftOut = true;
		    /*
		    System.out.printf("%d, ", indexStore);
		    */
		    resultIndices.add(indexStore);
		}
	    }
	    catch (ClassCastException e) {
		// no problem, just not a store we're looking for
	    }
	}
	/*resultIndices.remove(0)*/;// always a store of the Throwable
	/*
	   System.out.println(resultIndices);
	   */

    }



    private int getIndexStore(InstructionHandle instructionHandle, ConstantPoolGen constantPoolGen) throws ClassCastException {
	try {
	    StoreInstruction storeInstruction = (StoreInstruction) 
		(instructionHandle.getInstruction());
	    return storeInstruction.getIndex();
	}
	catch (ClassCastException e) {
	}
	try {
	    ArrayInstruction arrayStoreInstruction = (ArrayInstruction)
		/*((StackConsumer)(*/instructionHandle.getInstruction()/*))*/;
	    InstructionHandle ih  = getObjectReferenceLoadInstruction
		(instructionHandle, constantPoolGen);
	    ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
	    return objectLoadInstruction.getIndex();
	}
	catch (ClassCastException e) {
	}

	PUTFIELD putFieldInstruction = (PUTFIELD) instructionHandle.getInstruction();
	InstructionHandle ih  = getObjectReferenceLoadInstruction
	    (instructionHandle, constantPoolGen);
	ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
	return objectLoadInstruction.getIndex();
    }






    /* Get the local variable index of the result of the spawnable methode
     * invoke.
     */
    // assumption that the result needs to be stored and that it happens right
    // after the invoke of the spawnable call
    private Integer[] getIndexStore(InstructionHandle spawnableMethodInvoke,
	    InstructionList instructionList, ConstantPoolGen constantPoolGen) throws ResultNotStored {

	/*
	   System.err.printf("spawnableMethodInvoke: %s\n", spawnableMethodInvoke);
	   */
	int producedOnStack = spawnableMethodInvoke.getInstruction().produceStack(constantPoolGen);
	/*
	   System.err.printf("producedOnStack: %d\n", producedOnStack);
	   */
	if (producedOnStack <= 0) {
	    throw new Error("The spawnable invoke doesn't return anything");
	}
	InstructionHandle stackConsumer = findStackConsumer(spawnableMethodInvoke.getNext(), producedOnStack, constantPoolGen);


	/* assumption that the next instruction is going to be the store
	 * instruction
	 */
	/*
	   InstructionHandle stackConsumer = spawnableMethodInvoke.getNext();
	   */

	try {
	    Integer[] indices = new Integer[1];
	    indices[0] = getIndexStore(stackConsumer, constantPoolGen);
	    return indices;
	}
	catch (ClassCastException e) {
	    throw new ResultNotStored();
	}
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


    private SpawnableCall getSpawnableCallWithException(InstructionHandle invokeInstruction, ConstantPoolGen constantPoolGen, 
	    ArrayList<CodeExceptionGen> exceptionHandlers) {

	ArrayList<Integer> resultIndices = new ArrayList<Integer>();
	for (CodeExceptionGen exceptionHandler : exceptionHandlers) {
	    InstructionHandle start = exceptionHandler.getHandlerPC(); 
	    InstructionHandle end = getEndExceptionHandler(exceptionHandler);
	    getIndicesStores(start, end, constantPoolGen, resultIndices);
	}

	Integer[] dummy = new Integer[resultIndices.size()];

	return new SpawnableCall(invokeInstruction,
		getObjectReferenceLoadInstruction(invokeInstruction, constantPoolGen),
		resultIndices.toArray(dummy));
    }


    private SpawnableCall getSpawnableCallWithException(InstructionHandle invokeInstruction, InstructionList il, ConstantPoolGen constantPoolGen, 
	    MethodGen spawnSignatureGen) {
	ArrayList<CodeExceptionGen> exceptionHandlers = getExceptionHandlers(invokeInstruction, spawnSignatureGen);

	if (exceptionHandlers.size() == 0) {
	    return new SpawnableCall(invokeInstruction, getObjectReferenceLoadInstruction(invokeInstruction, constantPoolGen), 
		    SpawnableCall.Type.EXCEPTIONS_NOT_HANDLED);
	}
	else {
	    return getSpawnableCallWithException(invokeInstruction, constantPoolGen, exceptionHandlers);
	}
    }



    private SpawnableCall getSpawnableCallReturningValue(InstructionHandle invokeInstruction, InstructionList il, 
	    ConstantPoolGen constantPoolGen) {
	try {
	    return new SpawnableCall(invokeInstruction, 
		    getObjectReferenceLoadInstruction(invokeInstruction, constantPoolGen), 
		    getIndexStore(invokeInstruction, il, constantPoolGen));
	}
	catch (ResultNotStored e) {
	    return new SpawnableCall(invokeInstruction,
		    getObjectReferenceLoadInstruction(invokeInstruction, constantPoolGen),
		    SpawnableCall.Type.RESULT_NOT_STORED);
	}
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

	    /*
	       System.out.println("HIEROOO");
	       for (String s : spawnSignatureGen.getExceptions()) {
	       System.out.println(s);
	       }
	       System.out.printf("de lengte :%d\n", spawnSignatureGen.getExceptions().length);
	       */

	    do {
		Instruction instruction = ih.getInstruction();
		if (instruction.equals(spawnableInvoke) && (instruction.produceStack(constantPoolGen) > 0)) {
		    spawnableCalls.add(getSpawnableCallReturningValue(ih, il, constantPoolGen));
		}
		else if (instruction.equals(spawnableInvoke) && 
			(instruction.produceStack(constantPoolGen) == 0) &&
			spawnSignatureGen.getExceptions().length > 0) {
		    spawnableCalls.add(getSpawnableCallWithException(ih, il, constantPoolGen, spawnSignatureGen));
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
