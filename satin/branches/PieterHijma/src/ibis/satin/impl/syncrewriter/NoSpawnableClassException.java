package ibis.satin.impl.syncrewriter;


class NoSpawnableClassException extends Exception {


    static final String MESSAGE = "No spawnable class";


    NoSpawnableClassException() {
	super(MESSAGE);
    }


    NoSpawnableClassException(String message) {
	super(MESSAGE + ", " + message);
    }
}
