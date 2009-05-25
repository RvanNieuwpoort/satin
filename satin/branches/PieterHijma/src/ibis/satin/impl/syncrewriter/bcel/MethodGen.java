package ibis.satin.impl.syncrewriter.bcel;

import java.util.ArrayList;

import org.apache.bcel.classfile.Method;

import org.apache.bcel.verifier.structurals.ControlFlowGraph;
import org.apache.bcel.verifier.structurals.InstructionContext;

import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.AASTORE;
import org.apache.bcel.generic.BASTORE;
import org.apache.bcel.generic.CASTORE;
import org.apache.bcel.generic.DASTORE;
import org.apache.bcel.generic.FASTORE;
import org.apache.bcel.generic.IASTORE;
import org.apache.bcel.generic.LASTORE;
import org.apache.bcel.generic.SASTORE;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.ConstantPoolGen;


/** An extension on the MethodGen of the bcel library containing a little bit
 * more information about which instruction consumes what another produced on
 * the stack.
 */
public class MethodGen extends org.apache.bcel.generic.MethodGen {



    public MethodGen(Method method, String className, ConstantPoolGen constantPoolGen) {
	super(method, className, constantPoolGen);
    }




    /** Returns the instruction handles that consume what instructionHandle
     * ih put onto the stack. 
     *
     * When a branch instruction is found, this method may return multiple
     * instructions that consume the result of ih.
     *
     * @param ih The instructionHandle that produced something on the stack
     *
     * @return The instruction handles that consume what ih produced on the
     * stack.
     */
    public InstructionHandle[] findInstructionConsumers(InstructionHandle ih) {
	ConstantPoolGen constantPoolGen = getConstantPool();
	int wordsOnStack = ih.getInstruction().produceStack(constantPoolGen);
	ControlFlowGraph controlFlowGraph = new ControlFlowGraph(this);
	ArrayList<InstructionHandle> consumers = getInstructionsConsuming(wordsOnStack, ih.getNext(), constantPoolGen, controlFlowGraph);
	InstructionHandle[] consumersArray = new InstructionHandle[consumers.size()];
	return consumers.toArray(consumersArray);
    }



    /** Tests whether an object load instruction is used for puting something
     * in a field of an object. 
     *
     * @param loadInstruction the load instruction that loads the object
     * reference onto the stack. 
     *
     * @return true if the instruction is a load instruction of an object which
     * is used for puting something in a field. 
     */
    public boolean isUsedForPutField(InstructionHandle loadInstruction) {
	if (loadInstruction.getInstruction() instanceof ALOAD) {
	    InstructionHandle[] loadInstructionConsumers = findInstructionConsumers(loadInstruction);
	    boolean isUsedForPutField = true;
	    for (InstructionHandle loadInstructionConsumer : loadInstructionConsumers) {
		Instruction consumer = loadInstructionConsumer.getInstruction();
		isUsedForPutField &= consumer instanceof PUTFIELD;
	    }
	    return isUsedForPutField;
	}
	else {
	    return false;
	}
    }


    /** Tests whether an object load instruction is used for storing something
     * in an array. 
     *
     * @param loadInstruction the load instruction that loads the object
     * reference onto the stack. 
     * @return true if the instruction is a load instruction of an object which
     * is used for storing something in an array. 
     */
    public boolean isUsedForArrayStore(InstructionHandle loadInstruction) {
	if (loadInstruction.getInstruction() instanceof ALOAD) {
	    InstructionHandle[] loadInstructionConsumers = findInstructionConsumers(loadInstruction);
	    boolean isUsedForArrayStore = true;
	    for (InstructionHandle loadInstructionConsumer : loadInstructionConsumers) {
		Instruction consumer = loadInstructionConsumer.getInstruction();
		isUsedForArrayStore &= isArrayStore(consumer);
	    }
	    return isUsedForArrayStore;
	}
	else {
	    return false;
	}
    }


    /** Tests whether an instruction loads to a local variable with a certain
     * index.
     *
     * When the load is used for an array store or a putfield, the result will
     * be false. It
     * uses {@link #isUsedForArrayStore(InstructionHandle)} and {@link
     * #isUsedForPutField(InstructionHandle)}.
     *
     * @param ih The instruction that is tested to load to a local variable
     * index.
     * @param localVarIndex The local variable index to which the instruction
     * may load
     *
     * @return true if the instruction loads to the local variable index, false
     * otherwise. 
     *
     * @see #isUsedForArrayStore(InstructionHandle)
     * @see #isUsedForPutField(InstructionHandle)
     */
    public boolean instructionLoadsTo(InstructionHandle ih, int localVarIndex) {
	try {
	    LoadInstruction loadInstruction = (LoadInstruction) (ih.getInstruction());
	    return loadInstruction.getIndex() == localVarIndex && 
		!isUsedForArrayStore(ih) && !isUsedForPutField(ih);
	}
	catch(ClassCastException e) {
	    return false;
	}
    }


    /** Tests whether an instruction consumes what another instruction puts
     * onto the stack.
     *
     * @param consumer The instruction that consumes what consumee produces
     * @param consumee The instruction of which the stack production is
     * consumed.
     * @return true if consumer consumes what consumee produces on the stack,
     * false otherwise.
     */
    public boolean consumes(InstructionHandle consumer, InstructionHandle consumee) {
	ConstantPoolGen constantPoolGen = getConstantPool();

	int wordsOnStack = consumee.getInstruction().produceStack(constantPoolGen);
	//System.out.printf("Does consumer %s, consume %s\n", consumer, consumee);

	ControlFlowGraph controlFlowGraph = new ControlFlowGraph(this);
	ArrayList<InstructionHandle> consumers = getInstructionsConsuming(wordsOnStack, consumee.getNext(), constantPoolGen, controlFlowGraph);

	/*
	System.out.printf("The consumers are: %s\n", consumers);
	System.out.printf("So consumer %s consumes %s?: %b\n", consumer, consumee, consumers.contains(consumer));
	*/

	return consumers.contains(consumer);
    }






    /** Get the object reference load instruction of an instruction invoked on
     * an object.
     *
     * A putfield, arraystore or an invoke instruction, etc. are invoked on an
     * object. This method returns the instruction handle in which the object
     * reference was loaded. 
     *
     * @param ih The instructionHandle that invokes something on an object.
     * @return The instruction handle which loads the object reference that is
     * used by instruction handle ih.
     */
    public InstructionHandle getObjectReferenceLoadInstruction(InstructionHandle ih) {
	ArrayList<InstructionHandle> objectLoadInstructions = getAllObjectLoadInstructions(getInstructionList());

	for (InstructionHandle objectLoadInstruction : objectLoadInstructions) {
	    if (consumes(ih, objectLoadInstruction)) {
		return objectLoadInstruction;
	    }
	}
	throw new Error("Can't find the object reference load instruction");
    }


    public int getIndexStore(InstructionHandle instructionHandle) throws ClassCastException {
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
		(instructionHandle);
	    ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
	    return objectLoadInstruction.getIndex();
	}
	catch (ClassCastException e) {
	}

	PUTFIELD putFieldInstruction = (PUTFIELD) instructionHandle.getInstruction();
	InstructionHandle ih  = getObjectReferenceLoadInstruction
	    (instructionHandle);
	ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
	return objectLoadInstruction.getIndex();
    }



    public InstructionHandle getEndExceptionHandler(CodeExceptionGen codeException) {
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
	throw new Error("no end exceptionhandler...");
    }





    private ArrayList<InstructionHandle> getAllObjectLoadInstructions(InstructionList il) {
	ArrayList<InstructionHandle> objectLoadInstructions = new ArrayList<InstructionHandle>();

	InstructionHandle current = il.getStart();
	while(current != null) {
	    Instruction instruction = current.getInstruction();
	    if (instruction instanceof ALOAD) {
		objectLoadInstructions.add(current);
	    }
	    current = current.getNext();
	}

	return objectLoadInstructions;
    }


    private ArrayList<InstructionHandle> getInstructionsConsuming(int nrWords, InstructionHandle current, 
	    ConstantPoolGen constantPoolGen, ControlFlowGraph controlFlowGraph) {

	ArrayList<InstructionHandle> consumers = new ArrayList<InstructionHandle>();
	//System.out.printf("We're at %s\n", current);

	int wordsOnStack = nrWords;
	/*
	System.out.printf("  We want to know if this consumes %d words\n", wordsOnStack);
	*/

	wordsOnStack -= current.getInstruction().consumeStack(constantPoolGen);
//	System.out.printf("  After this instruction consumed the stack, the stack is %d\n", wordsOnStack);

	if (wordsOnStack <= 0) {
	    //System.out.printf("  The stack is consumed by %s!!\n", current);
	    consumers.add(current);
	    return consumers;
	}

	wordsOnStack += current.getInstruction().produceStack(constantPoolGen);
	//System.out.printf("  After this instruction produced the stack, the stack is %d\n", wordsOnStack);

	InstructionContext currentContext = controlFlowGraph.contextOf(current);
	for (InstructionContext successorContext : currentContext.getSuccessors()) {
	    InstructionHandle successor = successorContext.getInstruction();
	    //System.out.printf("  This instruction goes to %s, checking out..\n", successor);
	    consumers.addAll(getInstructionsConsuming(wordsOnStack, successor, constantPoolGen, controlFlowGraph));
	    //System.out.printf("  Done checking out %s\n", successor);
	}

	return consumers;
    }


    private boolean isArrayStore(Instruction instruction) {
	    return instruction instanceof AASTORE || instruction instanceof BASTORE || 
		instruction instanceof CASTORE || instruction instanceof DASTORE ||
		instruction instanceof FASTORE || instruction instanceof IASTORE ||
		instruction instanceof LASTORE || instruction instanceof SASTORE;
    }






}



    /*
    public static InstructionHandle[] findInstructionConsumers(InstructionHandle ih, ConstantPoolGen constantPoolGen) throws ConsumerNotFoundException {
	int stackConsumption = 0;
	int targetBalance = ih.getInstruction().produceStack(constantPoolGen);
	int lastProducedOnStack = 0;
	InstructionHandle current = ih;
	do {
	    current = current.getNext();
	    if (current.getInstruction() instanceof BranchInstruction) {
		throw new ConsumerNotFoundException();
	    }
	    Instruction currentInstruction = current.getInstruction();
	    lastProducedOnStack = currentInstruction.produceStack(constantPoolGen);
	    stackConsumption-=lastProducedOnStack;
	    stackConsumption+=currentInstruction.consumeStack(constantPoolGen);
	}
	// ignoring the fact that the current instruction might also produce
	// something
	while (stackConsumption + lastProducedOnStack < targetBalance);

	return current;
    }
    */





    /* FOR OTHER CLASSES */

    /* what instruction consumes what ih produces on the stack */
    /*
    private InstructionHandle findInstructionConsumer(InstructionHandle ih, ConstantPoolGen constantPoolGen) {
	int stackConsumption = 0;
	int targetBalance = ih.getInstruction().produceStack(constantPoolGen);
	System.out.printf("targetBalance: %d\n", targetBalance);
	int lastProducedOnStack = 0;
	InstructionHandle current = ih;
	do {
	    current = current.getNext();
	    System.out.printf("\ncurrent instruction: %s\n", current);
	    System.out.printf("stackConsumption: %d\n", stackConsumption);
	    System.out.println("OK, what is consumed...");
	    Instruction currentInstruction = current.getInstruction();
	    lastProducedOnStack = currentInstruction.produceStack(constantPoolGen);
	    System.out.printf("lastProducedOnStack: %d\n", lastProducedOnStack);
	    stackConsumption-=lastProducedOnStack;
	    System.out.printf("stackConsumption: %d\n", stackConsumption);
	    stackConsumption+=currentInstruction.consumeStack(constantPoolGen);
	    System.out.printf("stackConsumption: %d\n", stackConsumption);
	    System.out.printf("stackConsumption + lastProducedOnStack: %d\n", stackConsumption + lastProducedOnStack);
	}
	// ignoring the fact that the current instruction might also produce
	// something
	while (stackConsumption + lastProducedOnStack != targetBalance);

	return current;
    }
    */



