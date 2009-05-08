package ibis.satin.impl.syncrewriter;


public class SyncInsertionProposalFailure extends MethodRewriteFailure {


    public static final String MESSAGE = "Failed to propose a sync insertion";


    public SyncInsertionProposalFailure() {
	super(MESSAGE);
    }


    public SyncInsertionProposalFailure(String message) {
	super(MESSAGE + ", " + message);
    }
}
