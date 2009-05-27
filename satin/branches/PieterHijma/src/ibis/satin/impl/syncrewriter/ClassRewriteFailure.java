package ibis.satin.impl.syncrewriter;


class ClassRewriteFailure extends Exception {


    static final String MESSAGE = "Failed to rewrite a spawnable class";


    ClassRewriteFailure() {
	super(MESSAGE);
    }


    ClassRewriteFailure(String message) {
	super(MESSAGE + ", " + message);
    }
}
