package ibis.satin.impl.syncrewriter;


import org.apache.bcel.generic.InstructionHandle;



class StoreLoadPath extends Path {


    private InstructionHandle store;
    private int resultIndexLoad;
    private int indexCodeBlockEarliestLoad;


    StoreLoadPath(InstructionHandle store, Path path, int resultIndexLoad) throws NeverReadException {
	super(path);

	this.store = store;
	this.indexCodeBlockEarliestLoad = getIndexEarliestLoad(resultIndexLoad);
	this.resultIndexLoad = resultIndexLoad;

	if (indexCodeBlockEarliestLoad < size()) {
	    removeRange(indexCodeBlockEarliestLoad + 1, size());
	}
    }


    private int getIndexEarliestLoad(int resultIndexLoadInstruction) 
	throws NeverReadException {

	for (int i = 0; i < size(); i++) {
	    CodeBlock codeBlock = get(i);
	    if (i == 0) {
		if (codeBlock.containsLoadWithIndexAfter(store, 
			    resultIndexLoadInstruction)) {
		    return i;
		}
	    }
	    else {
		if (codeBlock.containsLoadWithIndex(
			    resultIndexLoadInstruction)) {
		    return i;
			    }
	    }
	}
	throw new NeverReadException();
    }
}
