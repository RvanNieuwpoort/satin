package ibis.satin.impl.syncrewriter.analyzer;

import ibis.satin.impl.syncrewriter.NeverReadException;

import java.util.ArrayList;

import ibis.satin.impl.syncrewriter.Analyzer;
import ibis.satin.impl.syncrewriter.SpawnableMethodCall;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;


public class EarliestLoad implements Analyzer {


    public InstructionHandle[] proposeSyncInsertion(MethodGen methodGen, 
	    ArrayList<SpawnableMethodCall> spawnableCalls) throws 
	NeverReadException {
	    InstructionHandle[] instructionHandles = new InstructionHandle[1];

	    InstructionList instructionList = methodGen.getInstructionList();

	    instructionHandles[0] = 
		getEarliestLoadInstruction(instructionList, spawnableCalls);

	    return instructionHandles;
	}



    /* Get the earliest load instruction of the results of the spawnable calls.
     *
     * result1 = spawnableCall();
     * result2 = spawnableCall();
     *
     * read(result2); <---- this is returned
     * read(result1);
     */
    private InstructionHandle getEarliestLoadInstruction(InstructionList il,
	    ArrayList<SpawnableMethodCall> spawnableCalls) 
	throws NeverReadException {

	InstructionHandle earliestLoadInstruction = null;
	for (SpawnableMethodCall spawnableCall : spawnableCalls) {
	    InstructionHandle loadInstruction = 
		getLoadInstruction(il, spawnableCall);
	    if (earliestLoadInstruction == null || loadInstruction.getPosition()
		    < earliestLoadInstruction.getPosition()) {
		earliestLoadInstruction = loadInstruction;
		    }
	}
	return earliestLoadInstruction;
    }


    /* Get the load instruction corresponding to this spawnable call.
    */
    private InstructionHandle getLoadInstruction(InstructionList il, 
	    SpawnableMethodCall spawnableCall) throws NeverReadException {

	InstructionHandle ih = spawnableCall.getInstructionHandle();
	while ((ih = ih.getNext()) != null) {
	    try {
		LoadInstruction loadInstruction = 
		    (LoadInstruction) (ih.getInstruction());
		if (loadInstruction.getIndex() == 
			spawnableCall.getResultIndex()) {
		    return ih;
			}
	    }
	    catch (ClassCastException e) {
	    }
	}
	throw new NeverReadException();
    }
}
