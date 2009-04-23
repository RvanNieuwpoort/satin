package ibis.satin.impl.syncrewriter;



import java.io.PrintStream;



class Debug {



    public static final int NR_CHARS_ON_LINE = 80;


    private boolean debug;
    private PrintStream out;
    private int startLevel;



    Debug() {
	this.debug = false;
	this.out = System.out;
	this.startLevel = 0;
    }


    Debug(boolean turnOn, int startLevel) {
	this.debug = turnOn;
	this.out = System.out;
	this.startLevel = startLevel;
    }


    int getStartLevel() {
	return startLevel;
    }


    void turnOn() {
	debug = true;
    }


    void turnOff() {
	debug = false;
    }


    boolean turnedOn() {
	return debug;
    }


    void warning(String warningMessage, Object... arguments) {
	out.printf("WARNING: " + warningMessage, arguments);
    }


    void log(int level, String debugMessage, Object... arguments) {
	if (!debug) return;

	level = startLevel + level;
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
