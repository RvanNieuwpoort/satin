package ibis.satin.impl.syncrewriter.analyzer.controlflow;

class NeverReadException extends Exception {

    private static final long serialVersionUID = 1L;

    static final String MESSAGE = "The result is never read";

    NeverReadException() {
	super(MESSAGE);
    }

    NeverReadException(String message) {
	super(MESSAGE + ", " + message);
    }
}
