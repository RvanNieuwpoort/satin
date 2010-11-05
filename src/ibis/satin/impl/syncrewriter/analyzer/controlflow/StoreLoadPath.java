package ibis.satin.impl.syncrewriter.analyzer.controlflow;


import ibis.satin.impl.syncrewriter.controlflow.BasicBlock;
import ibis.satin.impl.syncrewriter.controlflow.Path;

import org.apache.bcel.generic.InstructionHandle;



/** A store-to-load path is a special kind of path where the first basic block
 * contains some store that is loaded in the last basic block.
 */
public class StoreLoadPath extends Path {

    private static final long serialVersionUID = 1L;
    
    private InstructionHandle storeInstruction;
    private Integer[] localVariableIndices;
    private boolean aliasProblem = false;



    /* package methods */
    
    StoreLoadPath(InstructionHandle storeInstruction, BasicBlock block, Integer[] localVariableIndices) {
        super(new Path());
        add(block);
        this.storeInstruction = storeInstruction;
        this.localVariableIndices = localVariableIndices;
        aliasProblem = true;
    }

    StoreLoadPath(InstructionHandle storeInstruction, Path path, Integer[] localVariableIndices)
	throws NeverReadException {
	super(path);

	boolean hasLoad;
	boolean hasPause;

	this.storeInstruction = storeInstruction;
	this.localVariableIndices = localVariableIndices;

	try {
	    int indexEarliestBasicBlock = getIndexEarliestBasicBlock(this.localVariableIndices, this.storeInstruction);
	    if (indexEarliestBasicBlock < size()) {
		removeRange(indexEarliestBasicBlock + 1, size());
	    }
	    hasLoad = true;
	}
	catch (NeverReadException e) {
	    hasLoad = false;
	}

	try {
	    int indexPauseBasicBlock = getIndexPauseBasicBlock(storeInstruction);
	    if (indexPauseBasicBlock < size()) {
		removeRange(indexPauseBasicBlock + 1, size());
	    }
	    hasPause = true;
	}
	catch (NeverReadException e) {
	    hasPause = false;
	}


	if (!hasLoad && !hasPause) throw new NeverReadException();
    }


    /* protected methods */

    protected StoreLoadPath(InstructionHandle storeInstruction, Path path) {
	super(path);
	this.storeInstruction = storeInstruction;
	localVariableIndices = null;
    }


    /* private methods */


    /* Returns the index of the earliest basic block after instruction
     * storeInstruction where a SatinObject.pause() is executed.
     */
    private int getIndexPauseBasicBlock(InstructionHandle storeInstruction) throws NeverReadException {
	for (int i = 0; i < size(); i++) {
	    PauseAwareBasicBlock basicBlock = new PauseAwareBasicBlock(get(i));
	    if (i == 0) {
		if (basicBlock.containsPauseAfter(storeInstruction)) {
		    return i;
		}
	    }
	    else {
		if (basicBlock.containsPause()) {
		    return i;
		}
	    }
	}
	throw new NeverReadException();
    }


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
