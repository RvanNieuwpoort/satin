package ibis.satin.impl.syncrewriter;


import org.apache.bcel.generic.InstructionHandle;


public class SpawnableCall {


    private InstructionHandle ih;
    private InstructionHandle objectReference;
    private int resultIndex;
    private boolean throwsException;


    SpawnableCall(InstructionHandle ih, 
	    InstructionHandle objectReference, int resultIndex) {
	this.ih = ih;
	this.objectReference = objectReference;
	this.resultIndex = resultIndex;
	this.throwsException = false;
    }


    SpawnableCall(InstructionHandle ih, 
	    InstructionHandle objectReference) {
	this.ih = ih;
	this.objectReference = objectReference;
	this.resultIndex = resultIndex;
	this.throwsException = true;
    }


    public boolean throwsException() {
	return throwsException;
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


    public String toString() {
	return String.format("%s, resultIndex: %d", ih, resultIndex);
	/*
	return String.format("SpawnableCall:\n\tinstructionHandle: %s\n\tobjectReference: %s\n\tresultIndex: %d\n", 
		ih, objectReference, resultIndex);
		*/
    }
}
