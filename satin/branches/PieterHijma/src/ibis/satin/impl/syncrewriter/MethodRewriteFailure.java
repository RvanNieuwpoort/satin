package ibis.satin.impl.syncrewriter;


class MethodRewriteFailure extends Exception {


    static final String MESSAGE = "Failed to rewrite a spawnable method";


    MethodRewriteFailure() {
	super(MESSAGE);
    }


    MethodRewriteFailure(String message) {
	super(MESSAGE + ", " + message);
    }
}
