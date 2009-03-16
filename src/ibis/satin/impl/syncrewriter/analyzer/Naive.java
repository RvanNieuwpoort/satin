package ibis.satin.impl.syncrewriter.analyzer;

import java.util.ArrayList;

import ibis.satin.impl.syncrewriter.Analyzer;
import ibis.satin.impl.syncrewriter.SpawnableMethodCall;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;


public class Naive implements Analyzer {


    public InstructionHandle[] proposeSyncInsertion(MethodGen methodGen, ArrayList<SpawnableMethodCall> spawnableCalls) {
	InstructionHandle[] instructionHandles = 
	    new InstructionHandle[spawnableCalls.size()];

	for (int i = 0; i < spawnableCalls.size(); i++) {
	    SpawnableMethodCall call = spawnableCalls.get(i);
	    InstructionHandle invoke = call.getInstructionHandle();
	    InstructionHandle store = invoke.getNext();
	    InstructionHandle rightAfterStore = store.getNext();

	    instructionHandles[i] = rightAfterStore;
	}

	return instructionHandles;
    }
}
