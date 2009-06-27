package ibis.satin.impl.syncrewriter;


class NoSpawningMethodException extends Exception {


    static final String MESSAGE = "No spawnable method";


    NoSpawningMethodException() {
	super(MESSAGE);
    }


    NoSpawningMethodException(String message) {
	super(MESSAGE + ", " + message);
    }
}
