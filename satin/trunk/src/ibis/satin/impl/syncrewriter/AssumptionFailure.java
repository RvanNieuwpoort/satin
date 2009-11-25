package ibis.satin.impl.syncrewriter;


class AssumptionFailure extends Exception {

    private static final long serialVersionUID = 1L;

    AssumptionFailure(String message) {
	super(message);
    }
}
