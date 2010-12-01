package ibis.satin.impl.syncrewriter;


import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.LocalVariableGen;


/** This class represents a spawnable call.
 */
public class SpawnableCall {


    enum Type { NORMAL, RESULT_NOT_STORED, EXCEPTIONS_NOT_HANDLED };


    private InstructionHandle invokeInstruction;
    private InstructionHandle objectReference;

    private LocalVariableGen[] indicesStores;
    private Type type;
    
    private boolean resultMayHaveAliases = false;

    /* public methods */

    public boolean resultMayHaveAliases() {
        return resultMayHaveAliases;
    }


    public void setResultMayHaveAliases(boolean resultMayHaveAliases) {
        this.resultMayHaveAliases = resultMayHaveAliases;
    }


    /** Tests whether exceptions are handled for this spawnable call.
     *
     * @return true if exceptions are handled; false otherwise.
     */
    public boolean exceptionsHandled() {
	return type != Type.EXCEPTIONS_NOT_HANDLED;
    }


    /** Tests whether the result for this spawnable call is stored with a store
     * instruction.
     * @return true if the result is stored; false otherwise.
     */
    public boolean resultIsStored() {
	return type != Type.RESULT_NOT_STORED;
    }


    /** Returns the indices in which the spawnable call stores.
     *
     * This can be multiple indices when dealing with an exception.
     *
     * @return The indices in which the spawnable call stores.
     */
    public LocalVariableGen[] getIndicesStores() {
	return indicesStores;
    }


    /** Returns the invoke instruction of the spawnable call.
     *
     * @return The invoke instruction of the spawnable call.
     */
    public InstructionHandle getInvokeInstruction() {
	return invokeInstruction;
    }


    /** Tests whether this spawnable call stores in variables with index index.
     *
     * @param index The index which is tested.
     * @return true if the spawnable call stores in a variable with index
     * index; false otherwise.
     */
    public boolean storesIn(int index, InstructionHandle ih) {
	int insNo = ih.getPosition();
        for (LocalVariableGen g : indicesStores) {
            if (g.getIndex() == index) {
        	InstructionHandle end = g.getEnd();
        	InstructionHandle start = g.getStart();
        	if (start.getPrev() != null) {
        	    start = start.getPrev();
        	}
        	if (ih.getPosition() >= start.getPosition() && ih.getPosition() <= end.getPosition()) {
        	    return true;
        	}
            }
        }
	return false;
    }


    /** Returns the object reference of the spawnable call.
     *
     * @return The object reference of the spawnable call.
     */
    public InstructionHandle getObjectReference() {
	return objectReference;
    }


    /** Returns a string representation.
     */
    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append(invokeInstruction.toString());
	sb.append(", resultIndices: ");
	if (indicesStores == null) {
	    sb.append("(none), exceptions are not handled");
	}
	else {
	    for (LocalVariableGen i : indicesStores) {
		sb.append(i);
		sb.append(", ");
	    }
	    sb.delete(sb.length()-2, sb.length());
	}
	return sb.toString();
    }



    /* package methods */

    SpawnableCall(InstructionHandle invokeInstruction, 
	    InstructionHandle objectReference, LocalVariableGen[] indicesStores) {
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


    /* Should be for more common class */

    private boolean contains(Integer[] array, int x) {
	for (int i = 0; i < array.length; i++) {
	    if (array[i] == x) return true;
	}
	return false;
    }
}
