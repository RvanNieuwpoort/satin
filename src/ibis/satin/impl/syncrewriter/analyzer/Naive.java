package ibis.satin.impl.syncrewriter.analyzer;

import java.util.ArrayList;

import ibis.satin.impl.syncrewriter.Analyzer;
import ibis.satin.impl.syncrewriter.SpawnableCall;
import ibis.satin.impl.syncrewriter.SpawnableMethod;
import ibis.satin.impl.syncrewriter.SyncInsertionProposalFailure;
import ibis.satin.impl.syncrewriter.util.Debug;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;


public class Naive implements Analyzer {


    public InstructionHandle[] proposeSyncInsertion(SpawnableMethod spawnableMethod, Debug d)
	throws SyncInsertionProposalFailure {
	ArrayList<SpawnableCall> spawnableCalls = 
	    spawnableMethod.getSpawnableCalls();
	InstructionHandle[] instructionHandles = 
	    new InstructionHandle[spawnableCalls.size()];

	for (int i = 0; i < spawnableCalls.size(); i++) {
	    SpawnableCall call = spawnableCalls.get(i);
	    InstructionHandle invoke = call.getInvokeInstruction();
	    InstructionHandle store = invoke.getNext();
	    InstructionHandle rightAfterStore = store.getNext();

	    instructionHandles[i] = rightAfterStore;
	}

	return instructionHandles;
    }
}
