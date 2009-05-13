package ibis.satin.impl.syncrewriter.analyzer.controlflow;

import java.util.ArrayList;

import org.apache.bcel.verifier.structurals.InstructionContext;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.BranchInstruction;
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
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.classfile.ConstantPool;


public class BasicBlock {


    private static final boolean IGNORE_FIRST_INSTRUCTIONS = true;


    private InstructionContext start;
    private ArrayList<InstructionContext> instructions;
    private InstructionContext end;

    private int index;
    private ConstantPool constantPool;
    private BasicBlock[] targets;
    private int level;



    /* public methods */


    /*
    public InstructionHandle getEnd() {
	return instructions.get(instructions.size() - 1).getInstruction();
    }
    */


    public boolean contains(InstructionHandle ih) {
	for (InstructionContext instructionContext: instructions) {
	    if (instructionContext.getInstruction().equals(ih)) {
		return true;
	    }
	}
	return false;
    }


    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append(String.format("BEGIN CODEBLOCK: %d\n", index));
	sb.append(String.format("LEVEL: %d\n", level));
	sb.append(getTargetString());
	for (InstructionContext context : instructions) {
	    sb.append(context.getInstruction().getPosition());
	    sb.append(":\t");
	    sb.append(context.getInstruction().getInstruction().toString(constantPool));
	    sb.append('\n');
	}
	sb.append(String.format("END CODEBLOCK: %d\n", index));

	return sb.toString();
    }


    public int getIndex() {
	return index;
    }


    public InstructionContext getEnd() {
	return end;
    }

    public ArrayList<InstructionContext> getInstructions() {
	return instructions;
    }






    /* package methods */


    BasicBlock(InstructionContext start, 
	    ArrayList<InstructionContext> instructions, 
	    InstructionContext end, int index, 
	    ConstantPool constantPool) {
	this.start = start;
	this.instructions = instructions;
	this.end = end;
	this.index = index;
	this.constantPool = constantPool;

	this.targets = new BasicBlock[end.getSuccessors().length];
	this.level = -1;
    }


    boolean containsLoadWithIndex(int localVarIndex) {
	return containsLoadWithIndexAfter(null, localVarIndex, 
		!IGNORE_FIRST_INSTRUCTIONS);
    }


    boolean containsLoadWithIndexAfter(InstructionHandle ih, int localVarIndex) {
	return containsLoadWithIndexAfter(ih, localVarIndex, 
		IGNORE_FIRST_INSTRUCTIONS);
    }


    int getLevel() {
	return level;
    }


    InstructionContext getStart() {
	return start;
    }


    void setTarget(int index, BasicBlock target) {
	targets[index] = target;
    }


    BasicBlock getTarget(int index) {
	return targets[index];
    }


    boolean levelIsSet() {
	return level != -1;
    }


    boolean allLevelsTargetsSet() {
	for (BasicBlock target : targets) {
	    if (!target.levelIsSet()) {
		return false;
	    }
	}
	return true;
    }


    void setLevel(int level) {
	this.level = level;
    }


    void setLevelTargets(int level) {
	for (BasicBlock target : targets) {
	    if (!target.levelIsSet()) {
		target.setLevel(level);
	    }
	}
    }


    void setLevelTarget(int index, int level) {
	if (!targets[index].levelIsSet()) {
	    targets[index].setLevel(level);
	}
    }


    int getNrOfTargets() {
	return targets.length;
    }


    boolean isEnding() {
	return targets.length == 0;
    }


    boolean targets(BasicBlock basicBlock) {
	for (BasicBlock target : targets) {
	    if (target == basicBlock) {
		return true;
	    }
	}
	return false;
    }


    String toStringNoInstructions() {
	StringBuilder sb = new StringBuilder();
	sb.append(String.format("BEGIN BASICBLOCK: %d\n", index));
	sb.append(String.format("LEVEL: %d\n", level));
	sb.append(getTargetString());
	sb.append(String.format("END BASICBLOCK %d\n", index));

	return sb.toString();
    }







    /* private methods */


    private boolean containsLoadWithIndexAfter(InstructionHandle ih, 
	    int localVarIndex, boolean ignoreInstructions) {
	for (InstructionContext ic : instructions) {
	    InstructionHandle current = ic.getInstruction();
	    if (ignoreInstructions) { 
		ignoreInstructions = !current.equals(ih);
	    }
	    else if (instructionLoadsTo(current, localVarIndex)) {
		return true;
	    }
	}
	return false;
    }



    private StringBuilder getTargetString() {
	StringBuilder sb = new StringBuilder();
	sb.append("TARGETS: ");
	if (targets.length == 0) {
	    sb.append("ending Basic block\n");
	}
	else {
	    sb.append(String.format("Basic block %d\n", targets[0].getIndex()));
	    for (int i = 1; i < targets.length; i++) {
		sb.append(String.format("         Basic block %d\n", 
			    targets[i].getIndex()));
	    }
	}
	return sb;
    }










    private class NotFoundException extends Exception {}


    /* FOR OTHER CLASSES */

    /* what instruction consumes what ih produces on the stack */
    private InstructionHandle findInstructionConsumer(InstructionHandle ih, ConstantPoolGen constantPoolGen) throws NotFoundException {
	int stackConsumption = 0;
	int targetBalance = ih.getInstruction().produceStack(constantPoolGen);
//	System.out.printf("targetBalance: %d\n", targetBalance);
	int lastProducedOnStack = 0;
	InstructionHandle current = ih;
	do {
	    current = current.getNext();
	    /*
	    System.out.printf("\ncurrent instruction: %s\n", current);
	    System.out.printf("stackConsumption: %d\n", stackConsumption);
	    */
	    if (current.getInstruction() instanceof BranchInstruction) {
		/*
		System.out.println("control flow, can't find the instruction consumer");
		*/
		throw new NotFoundException();
	    }
	    /*
	    System.out.println("OK, what is consumed...");
	    */
	    Instruction currentInstruction = current.getInstruction();
	    lastProducedOnStack = currentInstruction.produceStack(constantPoolGen);
	    /*
	    System.out.printf("lastProducedOnStack: %d\n", lastProducedOnStack);
	    */
	    stackConsumption-=lastProducedOnStack;
	    /*
	    System.out.printf("stackConsumption: %d\n", stackConsumption);
	    */
	    stackConsumption+=currentInstruction.consumeStack(constantPoolGen);
	    /*
	    System.out.printf("stackConsumption: %d\n", stackConsumption);
	    System.out.printf("stackConsumption + lastProducedOnStack: %d\n", stackConsumption + lastProducedOnStack);
	    */
	}
	// ignoring the fact that the current instruction might also produce
	// something
	while (stackConsumption + lastProducedOnStack != targetBalance);

	return current;
    }





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



    private boolean isUsedForArrayStore(InstructionHandle loadInstruction) {
	if (loadInstruction.getInstruction() instanceof ALOAD) {
	    try {
	    InstructionHandle loadInstructionConsumer = findInstructionConsumer(loadInstruction, new ConstantPoolGen(constantPool));
	    Instruction consumer = loadInstructionConsumer.getInstruction();
	    return consumer instanceof AASTORE || consumer instanceof BASTORE || 
		consumer instanceof CASTORE || consumer instanceof DASTORE ||
		consumer instanceof FASTORE || consumer instanceof IASTORE ||
		consumer instanceof LASTORE || consumer instanceof SASTORE;
	    }
	    catch (NotFoundException e) {
		return false;
	    }
	}
	return false;
    }


    /* deze nog korter met instanceof */
    private boolean instructionLoadsTo(InstructionHandle ih, int localVarIndex) {
	try {
	    LoadInstruction loadInstruction = (LoadInstruction) (ih.getInstruction());
	    return loadInstruction.getIndex() == localVarIndex && 
		!isUsedForArrayStore(ih);
	}
	catch(ClassCastException e) {
	    return false;
	}
    }
}
