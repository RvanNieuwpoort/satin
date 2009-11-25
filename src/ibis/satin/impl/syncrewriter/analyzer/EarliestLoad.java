package ibis.satin.impl.syncrewriter.analyzer;

import ibis.satin.impl.syncrewriter.Analyzer;
import ibis.satin.impl.syncrewriter.SpawnableCall;
import ibis.satin.impl.syncrewriter.SpawningMethod;
import ibis.satin.impl.syncrewriter.SyncInsertionProposalFailure;
import ibis.satin.impl.syncrewriter.util.Debug;

import java.util.ArrayList;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LoadInstruction;


public class EarliestLoad implements Analyzer {


    public InstructionHandle[] proposeSyncInsertion(SpawningMethod spawnableMethod, Debug debug)
	throws SyncInsertionProposalFailure {

	ArrayList<SpawnableCall> spawnableCalls = 
	    spawnableMethod.getSpawnableCalls();

	InstructionHandle[] instructionHandles = new InstructionHandle[1];

	InstructionList instructionList = spawnableMethod.getInstructionList();

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
	    ArrayList<SpawnableCall> spawnableCalls) 
	throws SyncInsertionProposalFailure {

	InstructionHandle earliestLoadInstruction = null;
	for (SpawnableCall spawnableCall : spawnableCalls) {
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
	    SpawnableCall spawnableCall) throws SyncInsertionProposalFailure {

	InstructionHandle ih = spawnableCall.getInvokeInstruction();
	while ((ih = ih.getNext()) != null) {
	    try {
		LoadInstruction loadInstruction = 
		    (LoadInstruction) (ih.getInstruction());
		if (spawnableCall.storesIn(loadInstruction.getIndex())) {
		    return ih;
			}
	    }
	    catch (ClassCastException e) {
	    }
	}
	throw new SyncInsertionProposalFailure();
    }
}
