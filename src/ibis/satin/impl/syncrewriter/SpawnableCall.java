package ibis.satin.impl.syncrewriter;


import org.apache.bcel.generic.InstructionHandle;


public class SpawnableCall {


    enum Type { NORMAL, RESULT_NOT_STORED, EXCEPTIONS_NOT_HANDLED };



    private InstructionHandle invokeInstruction;
    private InstructionHandle objectReference;

    private Integer[] indicesStores;
    private Type type;


    public boolean exceptionsHandled() {
	return type != Type.EXCEPTIONS_NOT_HANDLED;
    }


    public boolean resultIsStored() {
	return type != Type.RESULT_NOT_STORED;
    }


    public Integer[] getIndicesStores() {
	return indicesStores;
    }


    public InstructionHandle getInvokeInstruction() {
	return invokeInstruction;
    }


    public boolean storesIn(int index) {
	    return contains(indicesStores, index);
    }


    public InstructionHandle getObjectReference() {
	return objectReference;
    }


    public String toString() {
	StringBuilder sb = new StringBuilder(String.format("%s, resultIndices: ", invokeInstruction));
	if (indicesStores == null) {
	    sb.append("(none), exceptions are not handled");
	}
	else {
	    for (Integer i : indicesStores) {
		sb.append(i);
		sb.append(", ");
	    }
	    sb.delete(sb.length()-2, sb.length());
	}
	return sb.toString();
    }




    SpawnableCall(InstructionHandle invokeInstruction, 
	    InstructionHandle objectReference, Integer[] indicesStores) {
	this.invokeInstruction = invokeInstruction;
	this.objectReference = objectReference;
	this.indicesStores = indicesStores;
	this.type = Type.NORMAL;
    }


    SpawnableCall(InstructionHandle invokeInstruction, InstructionHandle objectReference, Type type) {
	this.invokeInstruction = invokeInstruction;
	this.objectReference = objectReference;
	this.indicesStores = null;
	this.type = type;
    }


    /* eigenlijk voor andere class */
    private boolean contains(Integer[] array, int x) {
	for (int i = 0; i < array.length; i++) {
	    if (array[i] == x) return true;
	}
	return false;
    }
}
