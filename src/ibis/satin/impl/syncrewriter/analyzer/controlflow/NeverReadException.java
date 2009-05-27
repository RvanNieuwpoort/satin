package ibis.satin.impl.syncrewriter.analyzer.controlflow;


class NeverReadException extends Exception {


    static final String MESSAGE = "The result is never read";


    NeverReadException() {
	super(MESSAGE);
    }


    NeverReadException(String message) {
	super(MESSAGE + ", " + message);
    }
}
