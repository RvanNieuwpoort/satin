package ibis.satin.impl.syncrewriter;

import java.util.ArrayList;

import org.apache.bcel.verifier.structurals.InstructionContext;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.LoadInstruction;


class CodeBlock {


    public static final boolean IGNORE_FIRST_INSTRUCTIONS = true;


    private InstructionContext start;
    private ArrayList<InstructionContext> instructions;
    private InstructionContext end;
    private int index;

    private CodeBlock[] targets;

    private int level;


    CodeBlock(InstructionContext start, 
	    ArrayList<InstructionContext> instructions, 
	    InstructionContext end, int index) {
	this.start = start;
	this.instructions = instructions;
	this.end = end;
	this.index = index;

	this.targets = new CodeBlock[end.getSuccessors().length];
	this.level = -1;
    }


    boolean contains(InstructionHandle ih) {
	for (InstructionContext instructionContext: instructions) {
	    if (instructionContext.getInstruction().equals(ih)) {
		return true;
	    }
	}
	return false;
    }


    boolean containsLoadWithIndex(int resultIndex) {
	return containsLoadWithIndexAfter(null, resultIndex, 
		!IGNORE_FIRST_INSTRUCTIONS);
    }


    boolean containsLoadWithIndexAfter(InstructionHandle ih, int resultIndex) {
	return containsLoadWithIndexAfter(ih, resultIndex, 
		IGNORE_FIRST_INSTRUCTIONS);
    }


    private boolean containsLoadWithIndexAfter(InstructionHandle ih, 
	    int resultIndex, boolean ignoreInstructions) {
	for (InstructionContext ic : instructions) {
	    if (ignoreInstructions) { 
		ignoreInstructions = !ic.getInstruction().equals(ih);
	    }
	    else {
		try {
		    LoadInstruction loadInstruction = 
			(LoadInstruction) (ic.getInstruction().getInstruction());
		    if (loadInstruction.getIndex() == resultIndex) {
			return true;
		    }
		}
		catch(ClassCastException e) {
		}
	    }
	}
	return false;
    }


    int getLevel() {
	return level;
    }


    int getIndex() {
	return index;
    }


    InstructionContext getStart() {
	return start;
    }


    InstructionContext getEnd() {
	return end;
    }


    void setTarget(int index, CodeBlock target) {
	targets[index] = target;
    }


    CodeBlock getTarget(int index) {
	return targets[index];
    }


    boolean levelIsSet() {
	return level != -1;
    }


    boolean allLevelsTargetsSet() {
	for (CodeBlock target : targets) {
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
	for (CodeBlock target : targets) {
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


    private StringBuilder getTargetString() {
	StringBuilder sb = new StringBuilder();
	sb.append("TARGETS: ");
	if (targets.length == 0) {
	    sb.append("ending Codeblock\n");
	}
	else {
	    sb.append(String.format("Codeblock %d\n", targets[0].getIndex()));
	    for (int i = 1; i < targets.length; i++) {
		sb.append(String.format("         Codeblock %d\n", 
			    targets[i].getIndex()));
	    }
	}
	return sb;
    }


    boolean isEnding() {
	return targets.length == 0;
    }


    boolean targets(CodeBlock codeBlock) {
	for (CodeBlock target : targets) {
	    if (target == codeBlock) {
		return true;
	    }
	}
	return false;
    }


    String toStringNoInstructions() {
	StringBuilder sb = new StringBuilder();
	sb.append(String.format("BEGIN CODEBLOCK: %d\n", index));
	sb.append(String.format("LEVEL: %d\n", level));
	sb.append(getTargetString());
	sb.append(String.format("END CODEBLOCK: %d\n", index));

	return sb.toString();
    }


    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append(String.format("BEGIN CODEBLOCK: %d\n", index));
	sb.append(String.format("LEVEL: %d\n", level));
	sb.append(getTargetString());
	for (InstructionContext context : instructions) {
	    sb.append(context);
	    sb.append('\n');
	}
	sb.append(String.format("END CODEBLOCK: %d\n", index));

	return sb.toString();
    }
}
