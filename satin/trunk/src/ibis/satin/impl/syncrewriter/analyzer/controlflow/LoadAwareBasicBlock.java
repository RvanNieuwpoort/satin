package ibis.satin.impl.syncrewriter.analyzer.controlflow;



import ibis.satin.impl.syncrewriter.controlflow.BasicBlock;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.verifier.structurals.InstructionContext;



class LoadAwareBasicBlock extends BasicBlock {


    private static final boolean IGNORE_FIRST_INSTRUCTIONS = true;



    /* package methods */

    LoadAwareBasicBlock(BasicBlock basicBlock) {
	super(basicBlock);
    }


    boolean containsLoadWithIndex(int localVarIndex) {
	return containsLoadWithIndexAfter(null, localVarIndex, 
		!IGNORE_FIRST_INSTRUCTIONS);
    }


    /* tests whether this basic block contains load with index localVarIndex
     * after instructionHandle ih */
    boolean containsLoadWithIndexAfter(InstructionHandle ih, int localVarIndex) {
	return containsLoadWithIndexAfter(ih, localVarIndex, 
		IGNORE_FIRST_INSTRUCTIONS);
    }


    boolean containsLoadWithIndexBefore(InstructionHandle ih, 
            int localVarIndex) {
        for (InstructionContext ic : instructions) {
            InstructionHandle current = ic.getInstruction();
            if (current.equals(ih)) {
                break;
            }
            if (methodGen.instructionLoadsTo(current, localVarIndex)) {
                return true;
            }
        }
        return false;
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
    
    
}
