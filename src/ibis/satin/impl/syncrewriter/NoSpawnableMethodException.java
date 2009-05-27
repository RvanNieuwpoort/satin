package ibis.satin.impl.syncrewriter;


class NoSpawnableMethodException extends Exception {


    static final String MESSAGE = "No spawnable method";


    NoSpawnableMethodException() {
	super(MESSAGE);
    }


    NoSpawnableMethodException(String message) {
	super(MESSAGE + ", " + message);
    }
}
