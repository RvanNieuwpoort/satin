package ibis.satin.impl.syncrewriter;

import ibis.satin.impl.syncrewriter.util.Debug;

import java.util.ArrayList;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.MethodGen;


/** Interface that defines what an analyzer for a syncrewriter should do.
 */
public interface Analyzer {


    /** Proposes instruction before which a sync should be inserted.
     *
     * If the analysis fails then a SyncInsertionProposalFailure can be thrown.
     *
     * @param method The spawnable method on which the analysis should happen.
     * @param debug An instance of a debug utility for printing error, warning
     * and debug messages.
     *
     * @return The instructionhandles before which the syncrewriter should
     * insert sync statements.
     * @throws SyncInsertionProposalFailure When the analysis fails.
     */
    public InstructionHandle[] proposeSyncInsertion(SpawnableMethod method, Debug debug) 
	throws SyncInsertionProposalFailure;

}
