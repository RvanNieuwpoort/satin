package ibis.satin.impl.syncrewriter;


public class NoSpawnableClassException extends Exception {


    public static final String MESSAGE = "No spawnable class";


    public NoSpawnableClassException() {
	super(MESSAGE);
    }


    public NoSpawnableClassException(String message) {
	super(MESSAGE + ", " + message);
    }
}
