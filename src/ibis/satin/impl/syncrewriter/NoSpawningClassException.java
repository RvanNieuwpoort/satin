package ibis.satin.impl.syncrewriter;

class NoSpawningClassException extends Exception {

    private static final long serialVersionUID = 1L;

    static final String MESSAGE = "No spawnable class";

    NoSpawningClassException() {
	super(MESSAGE);
    }

    NoSpawningClassException(String message) {
	super(MESSAGE + ", " + message);
    }
}
