package ibis.satin.impl.syncrewriter;

import ibis.satin.impl.syncrewriter.util.Debug;

import java.util.ArrayList;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.MethodGen;


public interface Analyzer {


    InstructionHandle[] proposeSyncInsertion(SpawnableMethod method, Debug debug) 
	throws SyncInsertionProposalFailure;

}
