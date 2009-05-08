package ibis.satin.impl.syncrewriter;


public class MethodRewriteFailure extends Exception {


    public static final String MESSAGE = "Failed to rewrite a spawnable method";


    public MethodRewriteFailure() {
	super(MESSAGE);
    }


    public MethodRewriteFailure(String message) {
	super(MESSAGE + ", " + message);
    }
}
