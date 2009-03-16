package ibis.satin.impl.rewriter;



import java.io.PrintStream;



class Debug {



    public static final int NR_CHARS_ON_LINE = 80;


    boolean debug;
    PrintStream out;



    Debug() {
	this.debug = false;
	this.out = System.out;
    }


    void turnOn() {
	debug = true;
    }


    void turnOff() {
	debug = false;
    }


    void log(int level, String debugMessage, Object... arguments) {
	if (!debug) return;

	if (level < 0) throw new Error("printDebug(), level < 0");

	StringBuilder sb = new StringBuilder("DEBUG: ");
	for (int i = 0; i < level; i++) sb.append("  ");
	sb.append((debugMessage.replace('\n', ' ')).replace('\t', ' '));
	sb.append('\n');

	String completeMessage = String.format(sb.toString(), arguments);
	if (completeMessage.length() > NR_CHARS_ON_LINE) {
	    out.print(completeMessage.substring(0, NR_CHARS_ON_LINE));
	    log(level + 2, completeMessage.substring(NR_CHARS_ON_LINE, 
			completeMessage.length()));
	}
	else {
	    out.print(completeMessage);
	}
    }
}
