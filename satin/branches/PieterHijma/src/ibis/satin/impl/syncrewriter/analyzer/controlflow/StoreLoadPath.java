package ibis.satin.impl.syncrewriter.analyzer.controlflow;


import org.apache.bcel.generic.InstructionHandle;

import ibis.satin.impl.syncrewriter.controlflow.*;



/** A store-to-load path is a special kind of path where the first basic block
 * contains some store that is loaded in the last basic block.
 */
public class StoreLoadPath extends Path {



    private InstructionHandle storeInstruction;
    private Integer[] localVariableIndices;



    /* package methods */

    StoreLoadPath(InstructionHandle storeInstruction, Path path, Integer[] localVariableIndices)
	throws NeverReadException {
	super(path);

	this.storeInstruction = storeInstruction;
	this.localVariableIndices = localVariableIndices;

	int indexEarliestBasicBlock = getIndexEarliestBasicBlock(localVariableIndices, storeInstruction);
	if (indexEarliestBasicBlock < size()) {
	    removeRange(indexEarliestBasicBlock + 1, size());
	}
    }


    /* private methods */

    private boolean containsLoadWithIndex(LoadAwareBasicBlock basicBlock, Integer[] localVariableIndices) {
	for (Integer localVariableIndex : localVariableIndices) {
	    if (basicBlock.containsLoadWithIndex(localVariableIndex)) {
		return true;
	    }
	}
	return false;
    }


    private boolean containsLoadWithIndexAfter(LoadAwareBasicBlock basicBlock, Integer[] localVariableIndices, InstructionHandle instruction) {
	for (Integer localVariableIndex : localVariableIndices) {
	    if (basicBlock.containsLoadWithIndexAfter(instruction, localVariableIndex)) {
		return true;
	    }
	}
	return false;
    }


    /* Returns the index of the earliest basic block after instruction
     * storeInstruction where one of the local variables with indices in
     * localVariableIndices is loaded. 
     */
    private int getIndexEarliestBasicBlock(Integer[] localVariableIndices, InstructionHandle storeInstruction) throws NeverReadException {
	for (int i = 0; i < size(); i++) {
	    LoadAwareBasicBlock basicBlock = new LoadAwareBasicBlock(get(i));
	    if (i == 0) {
		if (containsLoadWithIndexAfter(basicBlock, localVariableIndices, storeInstruction)) {
		    return i;
		}
	    }
	    else {
		if (containsLoadWithIndex(basicBlock, localVariableIndices)) {
		    return i;
		}
	    }
	}
	throw new NeverReadException();
    }
}
