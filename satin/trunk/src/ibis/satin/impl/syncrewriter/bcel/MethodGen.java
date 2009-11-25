package ibis.satin.impl.syncrewriter.bcel;

import java.util.ArrayList;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.AALOAD;
import org.apache.bcel.generic.AASTORE;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.BALOAD;
import org.apache.bcel.generic.BASTORE;
import org.apache.bcel.generic.CALOAD;
import org.apache.bcel.generic.CASTORE;
import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DALOAD;
import org.apache.bcel.generic.DASTORE;
import org.apache.bcel.generic.DUP;
import org.apache.bcel.generic.FALOAD;
import org.apache.bcel.generic.FASTORE;
import org.apache.bcel.generic.GETSTATIC;
import org.apache.bcel.generic.IALOAD;
import org.apache.bcel.generic.IASTORE;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LALOAD;
import org.apache.bcel.generic.LASTORE;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.SALOAD;
import org.apache.bcel.generic.SASTORE;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.verifier.structurals.ControlFlowGraph;
import org.apache.bcel.verifier.structurals.InstructionContext;


/** An extension on the MethodGen of the bcel library containing a little bit
 * more information about which instruction consumes what another produced on
 * the stack.
 */
public class MethodGen extends org.apache.bcel.generic.MethodGen {

    private static final long serialVersionUID = 1L;
    
    private static boolean EXACT = true;


    /** Instantiate from an existing method.
     *
     * @param method method
     * @param className class name containing this method
     * @param constantPoolGen constant pool
     */
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
    public InstructionHandle[] findExactInstructionConsumers(InstructionHandle ih) {
	ConstantPoolGen constantPoolGen = getConstantPool();
	int wordsOnStack = ih.getInstruction().produceStack(constantPoolGen);
	ControlFlowGraph controlFlowGraph = new ControlFlowGraph(this);
	ArrayList<InstructionHandle> consumers = getInstructionsConsuming(wordsOnStack, ih.getNext(), constantPoolGen, controlFlowGraph, EXACT);
	InstructionHandle[] consumersArray = new InstructionHandle[consumers.size()];
	return consumers.toArray(consumersArray);
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
	ArrayList<InstructionHandle> consumers = getInstructionsConsuming(wordsOnStack, ih.getNext(), constantPoolGen, controlFlowGraph, !EXACT);
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
	    InstructionHandle[] loadInstructionConsumers = findExactInstructionConsumers(loadInstruction);
	    if (loadInstructionConsumers.length == 0) {
		return false;
	    }
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
	    InstructionHandle[] loadInstructionConsumers = findExactInstructionConsumers(loadInstruction);
	    if (loadInstructionConsumers.length == 0) return false;
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
	ControlFlowGraph controlFlowGraph = new ControlFlowGraph(this);
	return consumes(consumer, consumee, constantPoolGen, controlFlowGraph);
    }


    /** Tests whether an instruction consumes what another instruction puts
     * onto the stack.
     *
     * This function also tries beyond multidimensional arrays and dup
     * instructions. 
     *
     * @param consumer The instruction that consumes what consumee produces
     * @param consumee The instruction of which the stack production is
     * consumed.
     * @return true if consumer consumes what consumee produces on the stack,
     * false otherwise.
     */
    public boolean consumesExtensively(InstructionHandle consumer, InstructionHandle consumee) {
	ConstantPoolGen constantPoolGen = getConstantPool();
	ControlFlowGraph controlFlowGraph = new ControlFlowGraph(this);
	return consumesExtensively(consumer, consumee, constantPoolGen, controlFlowGraph);
    }




    /** Get the object reference load instruction of an instruction invoked on
     * an object.
     *
     * A putfield, arraystore or an invoke instruction, etc. are invoked on an
     * object. This method returns the instruction handle in which the object
     * reference was loaded. 
     *
     * It uses {@link #consumesExtensively(InstructionHandle,
     * InstructionHandle)} to find out which instruction consumes another. 
     *
     * There may be cases, for example with complicated DUP structures where
     * the analysis can't find out where object reference is, the method will
     * then throw an error. These cases should be added to the code then...
     *
     * @param ih The instructionHandle that invokes something on an object.
     * @return The instruction handle which loads the object reference that is
     * used by instruction handle ih.
     * @see #consumesExtensively(InstructionHandle, InstructionHandle)
     */
    public InstructionHandle getObjectReferenceLoadInstruction(InstructionHandle ih) {
	ArrayList<InstructionHandle> objectLoadInstructions = getAllObjectLoadInstructions(getInstructionList());

	for (InstructionHandle objectLoadInstruction : objectLoadInstructions) {
	    if (consumesExtensively(ih, objectLoadInstruction)) {
		return objectLoadInstruction;
	    }
	}
	throw new Error("Can't find the object reference load instruction");
    }


    /** Returns the index of the variable of a store instruction. 
     *
     * When the store is a store into an array or a putfield instruction, then
     * the index of the local variable which contains the object reference is
     * returned.
     *
     * It may use {@link
     * #getObjectReferenceLoadInstruction(InstructionHandle)}.
     *
     *
     * @param instructionHandle The instruction that is a store instruction.
     * @return The index of the local variable in which is stored.
     * @throws ClassCastException The instructionHandle is not a store
     * instruction. 
     * @see #getObjectReferenceLoadInstruction(InstructionHandle)
     */
    public int getIndexStore(InstructionHandle instructionHandle) throws ClassCastException {
	try {
	    StoreInstruction storeInstruction = (StoreInstruction) 
		(instructionHandle.getInstruction());
	    return storeInstruction.getIndex();
	}
	catch (ClassCastException e) {
	}

	if (isArrayStore(instructionHandle.getInstruction())) {
	    InstructionHandle ih  = getObjectReferenceLoadInstruction
		(instructionHandle);
	    ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
	    return objectLoadInstruction.getIndex();
	}

	InstructionHandle ih  = getObjectReferenceLoadInstruction
	    (instructionHandle);
	ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
	return objectLoadInstruction.getIndex();
    }



    /** Returns the end of an exception handler.
     *
     * @param codeException The codeException which end is returned.
     * @return The instructionHandle that is the end of the exception handler.
     */
    public InstructionHandle getEndExceptionHandler(CodeExceptionGen codeException) {
	LocalVariableGen[] localVars = getLocalVariables();
	InstructionHandle startHandler = codeException.getHandlerPC();

	for (LocalVariableGen localVar : localVars) {
	    InstructionHandle startScope = localVar.getStart();
	    InstructionHandle endScope = localVar.getEnd();

	    if (startScope == startHandler || startScope == startHandler.getNext() || 
		    startScope == startHandler.getNext().getNext() &&
		    localVar.getType().equals(codeException.getCatchType()))
		return endScope.getPrev();
	}
	throw new Error("no end exceptionhandler...");
    }


    private ArrayList<InstructionHandle> getAllObjectLoadInstructions(InstructionList il) {
	ArrayList<InstructionHandle> objectLoadInstructions = new ArrayList<InstructionHandle>();

	InstructionHandle current = il.getStart();
	while(current != null) {
	    Instruction instruction = current.getInstruction();
	    if (instruction instanceof ALOAD || instruction instanceof GETSTATIC) {
		objectLoadInstructions.add(current);
	    }
	    current = current.getNext();
	}

	return objectLoadInstructions;
    }


    private ArrayList<InstructionHandle> getInstructionsConsuming(int nrWords, InstructionHandle current, 
	    ConstantPoolGen constantPoolGen, ControlFlowGraph controlFlowGraph, boolean exact) {

	ArrayList<InstructionHandle> consumers = new ArrayList<InstructionHandle>();
	int wordsOnStack = nrWords;
	wordsOnStack -= current.getInstruction().consumeStack(constantPoolGen);

	if (exact) {
	    if (wordsOnStack < 0) {
		return consumers;
	    }
	    else if (wordsOnStack == 0) {
		consumers.add(current);
		return consumers;
	    }
	}
	else {
	    if (wordsOnStack <= 0) {
		consumers.add(current);
		return consumers;
	    }
	}

	wordsOnStack += current.getInstruction().produceStack(constantPoolGen);

	InstructionContext currentContext = controlFlowGraph.contextOf(current);
	for (InstructionContext successorContext : currentContext.getSuccessors()) {
	    InstructionHandle successor = successorContext.getInstruction();
	    consumers.addAll(getInstructionsConsuming(wordsOnStack, successor, constantPoolGen, controlFlowGraph, exact));
	}

	return consumers;
    }


    private boolean isArrayStore(Instruction instruction) {
	return instruction instanceof AASTORE || instruction instanceof BASTORE || 
	    instruction instanceof CASTORE || instruction instanceof DASTORE ||
	    instruction instanceof FASTORE || instruction instanceof IASTORE ||
	    instruction instanceof LASTORE || instruction instanceof SASTORE;
    }


    private boolean isArrayLoad(Instruction instruction) {
	return instruction instanceof AALOAD || instruction instanceof BALOAD || 
	    instruction instanceof CALOAD || instruction instanceof DALOAD ||
	    instruction instanceof FALOAD || instruction instanceof IALOAD ||
	    instruction instanceof LALOAD || instruction instanceof SALOAD;
    }

    private boolean containsDUP(ArrayList<InstructionHandle> instructionHandles) {
	for (InstructionHandle ih : instructionHandles) {
	    if (ih.getInstruction() instanceof DUP) {
		return true;
	    }
	}
	return false;
    }

    private InstructionHandle getDUP(ArrayList<InstructionHandle> instructionHandles) {
	if (instructionHandles.size() == 1 && 
		instructionHandles.get(0).getInstruction() instanceof DUP) {
	    return instructionHandles.get(0);
	}
	throw new Error("Very strange situation, not handled yet, controlflow in DUP's????\n");
    }


    private boolean containsArrayLoad(ArrayList<InstructionHandle> instructionHandles) {
	for (InstructionHandle ih : instructionHandles) {
	    if (isArrayLoad(ih.getInstruction())) {
		return true;
	    }
	}
	return false;
    }


    private InstructionHandle getArrayLoad(ArrayList<InstructionHandle> instructionHandles) {
	if (instructionHandles.size() == 1 && 
		isArrayLoad(instructionHandles.get(0).getInstruction())) {
	    return instructionHandles.get(0);
	}
	throw new Error("Very strange situation, not handled yet, controlflow in ArrayLoad's????\n");
    }


    /* Special case of consumes().
     *
     * it can be the case that the consumer of the consumee is an DUP
     * instruction. We then want to know if the consumer consumes what the
     * DUP produced on the stack.
     */
    private boolean consumesIncludingDUP(InstructionHandle consumer, InstructionHandle consumee, 
	    ConstantPoolGen constantPoolGen, ControlFlowGraph controlFlowGraph) {
	int wordsOnStack = consumee.getInstruction().produceStack(constantPoolGen);
	ArrayList<InstructionHandle> consumers = getInstructionsConsuming(wordsOnStack, consumee.getNext(), constantPoolGen, controlFlowGraph, EXACT);

	return containsDUP(consumers) && consumes(consumer, getDUP(consumers));
    }



    /* Special case of consumes().
     *
     * it can be the case that the consumer of the consumee is an AALOAD
     * instruction. We then want to know if the consumer consumes what the
     * AALOAD produced on the stack.
     */
    private boolean consumesIncludingArrayLoad(InstructionHandle consumer, InstructionHandle consumee, 
	    ConstantPoolGen constantPoolGen, ControlFlowGraph controlFlowGraph) {
	int wordsOnStack = consumee.getInstruction().produceStack(constantPoolGen);
	ArrayList<InstructionHandle> consumers = getInstructionsConsuming(wordsOnStack, consumee.getNext(), constantPoolGen, controlFlowGraph, EXACT);

	return containsArrayLoad(consumers) && consumes(consumer, getArrayLoad(consumers));
    }


    private boolean consumesExtensively(InstructionHandle consumer, InstructionHandle consumee, 
	    ConstantPoolGen constantPoolGen, ControlFlowGraph controlFlowGraph) {
	return consumes(consumer, consumee, constantPoolGen, controlFlowGraph) ||
	    consumesIncludingArrayLoad(consumer, consumee, constantPoolGen, controlFlowGraph) ||
	    consumesIncludingDUP(consumer, consumee, constantPoolGen, controlFlowGraph);
    }


    private boolean consumes(InstructionHandle consumer, InstructionHandle consumee, 
	    ConstantPoolGen constantPoolGen, ControlFlowGraph controlFlowGraph) {
	int wordsOnStack = consumee.getInstruction().produceStack(constantPoolGen);
	ArrayList<InstructionHandle> consumers = getInstructionsConsuming(wordsOnStack, consumee.getNext(), constantPoolGen, controlFlowGraph, !EXACT);

	return consumers.contains(consumer); 
    }
}
