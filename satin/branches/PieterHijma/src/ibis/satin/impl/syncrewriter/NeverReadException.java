package ibis.satin.impl.syncrewriter;


public class NeverReadException extends Exception {


    public static final String MESSAGE = "WARNING: inserting sync failed," + 
	"result of spawnable method is never read";


    public NeverReadException() {
	super(MESSAGE);
    }


    public NeverReadException(String message) {
	super(MESSAGE + ", " + message);
    }
}
