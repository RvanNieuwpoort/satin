package ibis.satin.impl.syncrewriter.controlflow;

import java.util.ArrayList;

import org.apache.bcel.verifier.structurals.InstructionContext;

import ibis.satin.impl.syncrewriter.bcel.MethodGen;

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




/** A BasicBlock is a sequence of instructions with only one entry point and
 * only one exit point. 
 *
 * Instructions don't branch to other instructions and instructions are not
 * targeted by other basic blocks. 
 */
public class BasicBlock {


    private static final boolean IGNORE_FIRST_INSTRUCTIONS = true;

    private MethodGen methodGen;

    private InstructionContext start;
    private ArrayList<InstructionContext> instructions;
    private InstructionContext end;

    private int index;
    private BasicBlock[] targets;
    private int level;



    /* public methods */



    /** Tests if this basic block contains {@link InstructionHandle} ih
     *
     * @param ih the instruction handle that will be tested
     * @return true if this basic block contains ih; false otherwise
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
	sb.append(String.format("BEGIN BASIC BLOCK: %d\n", index));
	sb.append(String.format("LEVEL: %d\n", level));
	sb.append(getTargetString());
	for (InstructionContext context : instructions) {
	    sb.append(context.getInstruction().getPosition());
	    sb.append(":\t");
	    sb.append(context.getInstruction().getInstruction().toString(methodGen.getConstantPool().getConstantPool()));
	    sb.append('\n');
	}
	sb.append(String.format("END BASIC BLOCK: %d\n", index));

	return sb.toString();
    }


    /** Returns the index of the basic block.
     *
     * This is the index of the basic block in the method. So, basic block with
     * index + 1 is the following basic block in the instruction list of the
     * method. It is not necessarily targeted by the previous one.
     *
     * @return the index of the basic block in the method.
     */
    public int getIndex() {
	return index;
    }


    /** Returns the last {@link InstructionContext} of the basic block.
     *
     * @return the last instruction context of the basic block.
     */
    public InstructionContext getEnd() {
	return end;
    }

    
    /** Returns an {@link ArrayList} of the instructions of the basic block.
     *
     * The instruction are in execution order.
     *
     * @return The instructions of the basic block in execution order.
     */
    public ArrayList<InstructionContext> getInstructions() {
	return instructions;
    }


    /** Tests if this basic block contains a load instruction with a local
     * variable index.
     *
     * This method uses 
     * {@link Util#instructionLoadsTo(InstructionHandle, int,ConstantPoolGen)}.
     * 
     * @param localVarIndex The local variable index of the variable that is to
     * tested. 
     * @return true if the basic block contains a load of the local variable
     * with index localVarIndex.
     * @see Util
     */
    public boolean containsLoadWithIndex(int localVarIndex) {
	return containsLoadWithIndexAfter(null, localVarIndex, 
		!IGNORE_FIRST_INSTRUCTIONS);
    }


    /** Tests if this basic block contains a load instruction with a local
     * variable index after {@link InstructionHandle} ih. 
     **
     * This method uses 
     * {@link Util#instructionLoadsTo(InstructionHandle, int,ConstantPoolGen)}.
     * 
     * @param localVarIndex The local variable index of the variable that is to
     * tested. 
     * @param ih the instruction handle after which the load may happen
     * @return true if the basic block contains a load of the local variable
     * with index localVarIndex.
     * @see Util
     */
    public boolean containsLoadWithIndexAfter(InstructionHandle ih, int localVarIndex) {
	return containsLoadWithIndexAfter(ih, localVarIndex, 
		IGNORE_FIRST_INSTRUCTIONS);
    }






    /* package methods */


    BasicBlock(InstructionContext start, 
	    ArrayList<InstructionContext> instructions, 
	    InstructionContext end, int index, 
	    MethodGen methodGen) {
	this.start = start;
	this.instructions = instructions;
	this.end = end;
	this.index = index;

	this.targets = new BasicBlock[end.getSuccessors().length];
	this.level = -1;

	this.methodGen = methodGen;
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
	    else if (methodGen.instructionLoadsTo(current, localVarIndex)) {
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
}
