package ibis.satin.impl.syncrewriter;

public class AliasingException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    private static final String MESSAGE = "Storing in possibly aliased object";
    
    public AliasingException() {
        super(MESSAGE);
    }

    public AliasingException(String arg0) {
        super(arg0);
    }
}
