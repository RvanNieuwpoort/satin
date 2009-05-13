package ibis.satin.impl.syncrewriter.analyzer.controlflow;


public class NeverReadException extends Exception {


    public static final String MESSAGE = "The result is never read";


    public NeverReadException() {
	super(MESSAGE);
    }


    public NeverReadException(String message) {
	super(MESSAGE + ", " + message);
    }
}
