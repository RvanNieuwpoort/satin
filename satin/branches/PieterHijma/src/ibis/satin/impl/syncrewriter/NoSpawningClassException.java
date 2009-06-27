package ibis.satin.impl.syncrewriter;


class NoSpawningClassException extends Exception {


    static final String MESSAGE = "No spawnable class";


    NoSpawningClassException() {
	super(MESSAGE);
    }


    NoSpawningClassException(String message) {
	super(MESSAGE + ", " + message);
    }
}
