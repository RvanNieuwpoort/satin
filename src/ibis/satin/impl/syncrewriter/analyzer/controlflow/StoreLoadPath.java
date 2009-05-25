package ibis.satin.impl.syncrewriter.analyzer.controlflow;


import org.apache.bcel.generic.InstructionHandle;

import ibis.satin.impl.syncrewriter.controlflow.*;



public class StoreLoadPath extends Path {


    private InstructionHandle storeInstruction;
    private Integer[] localVariableIndices;




    public StoreLoadPath(InstructionHandle storeInstruction, Path path, Integer[] localVariableIndices)
	throws NeverReadException {
	super(path);

	this.storeInstruction = storeInstruction;
	this.localVariableIndices = localVariableIndices;


	int indexEarliestBasicBlock = getIndexEarliestBasicBlock(localVariableIndices, storeInstruction);
	if (indexEarliestBasicBlock < size()) {
	    removeRange(indexEarliestBasicBlock + 1, size());
	}
    }



    private boolean containsLoadWithIndex(BasicBlock basicBlock, Integer[] localVariableIndices) {
	for (Integer localVariableIndex : localVariableIndices) {
	    if (basicBlock.containsLoadWithIndex(localVariableIndex)) {
		return true;
	    }
	}
	return false;
    }



    private boolean containsLoadWithIndexAfter(BasicBlock basicBlock, Integer[] localVariableIndices, InstructionHandle instruction) {
	for (Integer localVariableIndex : localVariableIndices) {
	    if (basicBlock.containsLoadWithIndexAfter(instruction, localVariableIndex)) {
		return true;
	    }
	}
	return false;
    }


    private int getIndexEarliestBasicBlock(Integer[] localVariableIndices, InstructionHandle storeInstruction) throws NeverReadException {
	for (int i = 0; i < size(); i++) {
	    BasicBlock basicBlock = get(i);
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
