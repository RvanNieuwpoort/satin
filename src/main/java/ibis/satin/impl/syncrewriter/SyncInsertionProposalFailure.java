package ibis.satin.impl.syncrewriter;

public class SyncInsertionProposalFailure extends MethodRewriteFailure {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "Failed to propose a sync insertion";

    public SyncInsertionProposalFailure() {
	super(MESSAGE);
    }

    public SyncInsertionProposalFailure(String message) {
	super(MESSAGE + ", " + message);
    }
}
