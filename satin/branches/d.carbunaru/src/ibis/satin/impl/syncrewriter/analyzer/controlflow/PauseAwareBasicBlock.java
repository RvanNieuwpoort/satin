package ibis.satin.impl.syncrewriter.analyzer.controlflow;

import ibis.satin.impl.syncrewriter.controlflow.BasicBlock;

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.INVOKESTATIC;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.verifier.structurals.InstructionContext;

class PauseAwareBasicBlock extends BasicBlock {

    private static final boolean IGNORE_FIRST_INSTRUCTIONS = true;

    /* package methods */

    PauseAwareBasicBlock(BasicBlock basicBlock) {
	super(basicBlock);
    }

    boolean containsPause() {
	return containsPauseAfter(null, !IGNORE_FIRST_INSTRUCTIONS);
    }

    /*
     * tests whether this basic block contains load with index localVarIndex
     * after instructionHandle ih
     */
    boolean containsPauseAfter(InstructionHandle ih) {
	return containsPauseAfter(ih, IGNORE_FIRST_INSTRUCTIONS);
    }

    /* private methods */

    private boolean instructionisPause(InstructionHandle ih) {
	ConstantPoolGen cp = methodGen.getConstantPool();
	int indexPauseInstruction = cp.lookupMethodref(
		"ibis.satin.SatinObject", "pause", "()V");
	if (indexPauseInstruction < 0)
	    return false;
	return ih.getInstruction().equals(
		new INVOKESTATIC(indexPauseInstruction));
    }

    private boolean containsPauseAfter(InstructionHandle ih,
	    boolean ignoreInstructions) {
	for (InstructionContext ic : instructions) {
	    InstructionHandle current = ic.getInstruction();
	    if (ignoreInstructions) {
		ignoreInstructions = !current.equals(ih);
	    } else if (instructionisPause(current)) {
		return true;
	    }
	}
	return false;
    }
}
