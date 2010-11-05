package ibis.satin.impl.syncrewriter.analyzer.controlflow;



import ibis.satin.impl.syncrewriter.controlflow.BasicBlock;

import org.apache.bcel.generic.ANEWARRAY;
import org.apache.bcel.generic.ConstantPushInstruction;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.MULTIANEWARRAY;
import org.apache.bcel.generic.NEW;
import org.apache.bcel.generic.NEWARRAY;
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
    
    /**
     * Checks that any store in this basic block to the specified variable is the
     * result of a new() or a null.
     * @param ih handle up to where to investigate.
     * @param localVarIndex the local variable index
     * @return true if all stores are OK or there are no stores.
     */
    boolean noAliasesStoreWithIndexBefore(InstructionHandle ih, 
            int localVarIndex) {
        InstructionHandle prev = null;
        for (InstructionContext ic : instructions) {
            InstructionHandle current = ic.getInstruction();
            if (current.equals(ih)) {
                break;
            }
            if (methodGen.instructionStoresTo(current, localVarIndex)) {
                if (prev != null) {
                    Instruction i = prev.getInstruction();
                    if (i instanceof NEW || i instanceof NEWARRAY
                            || i instanceof ANEWARRAY || i instanceof MULTIANEWARRAY
                            || i instanceof ConstantPushInstruction) {
                    } else {
                        return false;
                    }
                } else {
                    return false;   // don't know ...
                }
            }
            prev = current;
        }
        return false;
    }
    
    boolean noAliasesStoreWithIndex(int localVarIndex) {
        InstructionHandle prev = null;
        for (InstructionContext ic : instructions) {
            InstructionHandle current = ic.getInstruction();
            if (methodGen.instructionStoresTo(current, localVarIndex)) {
                if (prev != null) {
                    Instruction i = prev.getInstruction();
                    if (i instanceof NEW || i instanceof NEWARRAY
                            || i instanceof ANEWARRAY || i instanceof MULTIANEWARRAY
                            || i instanceof ConstantPushInstruction) {
                    } else {
                        return false;
                    }
                } else {
                    return false;   // don't know ...
                }
            }
            prev = current;
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
