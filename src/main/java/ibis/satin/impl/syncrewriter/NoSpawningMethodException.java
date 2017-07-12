package ibis.satin.impl.syncrewriter;

class NoSpawningMethodException extends Exception {

    private static final long serialVersionUID = 1L;

    static final String MESSAGE = "No spawnable method";

    NoSpawningMethodException() {
	super(MESSAGE);
    }

    NoSpawningMethodException(String message) {
	super(MESSAGE + ", " + message);
    }
}
