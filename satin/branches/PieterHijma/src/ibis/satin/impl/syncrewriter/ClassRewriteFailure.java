package ibis.satin.impl.syncrewriter;


public class ClassRewriteFailure extends Exception {


    public static final String MESSAGE = "Failed to rewrite a spawnable class";


    public ClassRewriteFailure() {
	super(MESSAGE);
    }


    public ClassRewriteFailure(String message) {
	super(MESSAGE + ", " + message);
    }
}
