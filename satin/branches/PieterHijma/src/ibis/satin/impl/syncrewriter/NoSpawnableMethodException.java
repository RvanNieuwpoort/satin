package ibis.satin.impl.syncrewriter;


public class NoSpawnableMethodException extends Exception {


    public static final String MESSAGE = "No spawnable method";


    public NoSpawnableMethodException() {
	super(MESSAGE);
    }


    public NoSpawnableMethodException(String message) {
	super(MESSAGE + ", " + message);
    }
}
