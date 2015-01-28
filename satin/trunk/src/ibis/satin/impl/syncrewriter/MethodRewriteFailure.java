package ibis.satin.impl.syncrewriter;

class MethodRewriteFailure extends Exception {

    private static final long serialVersionUID = 1L;

    static final String MESSAGE = "Failed to rewrite a spawnable method";

    MethodRewriteFailure() {
	super(MESSAGE);
    }

    MethodRewriteFailure(String message) {
	super(MESSAGE + ", " + message);
    }
}
