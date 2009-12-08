package ibis.satin.impl.syncrewriter;


class ClassRewriteFailure extends Exception {

    private static final long serialVersionUID = 1L;
    
    static final String MESSAGE = "Failed to rewrite a spawnable class";


    ClassRewriteFailure() {
	super(MESSAGE);
    }


    ClassRewriteFailure(String message) {
	super(MESSAGE + ", " + message);
    }
}
