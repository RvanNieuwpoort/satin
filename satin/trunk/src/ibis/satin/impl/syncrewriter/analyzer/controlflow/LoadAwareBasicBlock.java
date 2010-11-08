package ibis.satin.impl.syncrewriter.analyzer.controlflow;



import ibis.satin.impl.syncrewriter.controlflow.BasicBlock;

import org.apache.bcel.generic.ANEWARRAY;
import org.apache.bcel.generic.ConstantPushInstruction;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.LocalVariableGen;
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


    boolean containsLoadWithIndex(LocalVariableGen lg) {
	return containsLoadWithIndexAfter(null, !IGNORE_FIRST_INSTRUCTIONS, lg);
    }


    /* tests whether this basic block contains load with index localVarIndex
     * after instructionHandle ih */
    boolean containsLoadWithIndexAfter(InstructionHandle ih, LocalVariableGen lg) {
	return containsLoadWithIndexAfter(ih, IGNORE_FIRST_INSTRUCTIONS, lg);
    }


    boolean containsLoadWithIndexBefore(InstructionHandle ih, LocalVariableGen lg) {
        for (InstructionContext ic : instructions) {
            InstructionHandle current = ic.getInstruction();
            if (current.equals(ih)) {
                break;
            }
            if (lg.containsTarget(current) && methodGen.instructionLoadsTo(current, lg.getIndex())) {
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
    boolean noAliasesStoreWithIndexBefore(InstructionHandle ih, LocalVariableGen lg) {
        InstructionHandle prev = null;
        for (InstructionContext ic : instructions) {
            InstructionHandle current = ic.getInstruction();
            if (current.equals(ih)) {
                break;
            }
            if (lg.containsTarget(current) && methodGen.instructionStoresTo(current, lg.getIndex())) {
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
        return true;
    }
    
    boolean noAliasesStoreWithIndex(LocalVariableGen lg) {
        InstructionHandle prev = null;
        for (InstructionContext ic : instructions) {
            InstructionHandle current = ic.getInstruction();
            if (lg.containsTarget(current) && methodGen.instructionStoresTo(current, lg.getIndex())) {
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
        return true;
    }

    /* private methods */

    private boolean containsLoadWithIndexAfter(InstructionHandle ih, boolean ignoreInstructions, LocalVariableGen lg) {
	for (InstructionContext ic : instructions) {
	    InstructionHandle current = ic.getInstruction();
	    if (ignoreInstructions) { 
	        ignoreInstructions = !current.equals(ih);
	    }
	    else if (lg.containsTarget(current) && methodGen.instructionLoadsTo(current, lg.getIndex())) {
	        return true;
	    }
	}
	return false;
    }
    
    
}
