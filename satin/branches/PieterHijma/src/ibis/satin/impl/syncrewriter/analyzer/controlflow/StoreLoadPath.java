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



    /*
    public StoreLoadPath(InstructionHandle storeInstruction, Path path, int resultIndexLoad, 
	    InstructionHandle endExceptionHandler)
	throws NeverReadException {
	super(path);

	this.storeInstruction = storeInstruction;
	this.indexEarliestBasicBlock = getIndexEarliestBasicBlock(resultIndexLoad, storeInstruction, endExceptionHandler);
	this.resultIndexLoad = resultIndexLoad;

	if (indexEarliestBasicBlock < size()) {
	    removeRange(indexEarliestBasicBlock + 1, size());
	}
    }



    public StoreLoadPath(InstructionHandle storeInstruction, Path path, int resultIndexLoad)
	throws NeverReadException {
	super(path);

	this.storeInstruction = storeInstruction;
	this.indexEarliestBasicBlock = getIndexEarliestBasicBlock(resultIndexLoad, null, null);
	this.resultIndexLoad = resultIndexLoad;

	if (indexEarliestBasicBlock < size()) {
	    removeRange(indexEarliestBasicBlock + 1, size());
	}
    }


    private boolean inRange(int x, int start, int end) {
	return x >= start && x < end;
    }




    boolean isInBetween(InstructionHandle begin, BasicBlock basicBlock, InstructionHandle end) {
	if (begin == null || end == null) return false;
	return inRange(basicBlock.getStart().getInstruction().getPosition(), 
		begin.getPosition(), end.getPosition());
    }





    private int getIndexEarliestBasicBlock(int resultIndexLoadInstruction, InstructionHandle ignoreStart, InstructionHandle ignoreEnd) 
	throws NeverReadException {

	for (int i = 0; i < size(); i++) {
	    BasicBlock basicBlock = get(i);
	    if (isInBetween(ignoreStart, basicBlock, ignoreEnd)) {
		// ignore this...
	    }
	    else if (i == 0) {
		if (basicBlock.containsLoadWithIndexAfter(storeInstruction, 
			    resultIndexLoadInstruction)) {
		    return i;
		}
	    }
	    else {
		if (basicBlock.containsLoadWithIndex(
			    resultIndexLoadInstruction)) {
		    return i;
			    }
	    }
	}
	throw new NeverReadException();
    }
    */



