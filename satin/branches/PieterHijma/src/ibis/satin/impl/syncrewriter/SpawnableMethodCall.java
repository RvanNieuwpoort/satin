package ibis.satin.impl.syncrewriter;


import org.apache.bcel.generic.InstructionHandle;


public class SpawnableMethodCall {


    private InstructionHandle ih;
    private InstructionHandle objectReference;
    private int resultIndex;


    SpawnableMethodCall(InstructionHandle ih, 
	    InstructionHandle objectReference, int resultIndex) {
	this.ih = ih;
	this.objectReference = objectReference;
	this.resultIndex = resultIndex;
    }


    public InstructionHandle getInstructionHandle() {
	return ih;
    }

    public int getResultIndex() {
	return resultIndex;
    }

    public InstructionHandle getObjectReference() {
	return objectReference;
    }

}
