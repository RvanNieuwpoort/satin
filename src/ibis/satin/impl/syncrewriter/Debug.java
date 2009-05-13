package ibis.satin.impl.syncrewriter;



import java.io.PrintStream;



public class Debug {



    public static final int NR_CHARS_ON_LINE = 150;
    public static final int INDENTATION_WIDTH = 2;

    public static final boolean IS_FIRST_LINE = true;


    private boolean debug;
    private PrintStream out;
    private int startLevel;



    public Debug() {
	this.debug = false;
	this.out = System.out;
	this.startLevel = 0;
    }


    public Debug(boolean turnOn, int startLevel) {
	this.debug = turnOn;
	this.out = System.out;
	this.startLevel = startLevel;
    }


    public int getStartLevel() {
	return startLevel;
    }


    public void turnOn() {
	debug = true;
    }


    public void turnOff() {
	debug = false;
    }


    public boolean turnedOn() {
	return debug;
    }

    public void error(String warningMessage, Object... arguments) {
	print("ERROR: ", 0, String.format(warningMessage, arguments));
    }


    public void warning(String warningMessage, Object... arguments) {
	print("WARNING: ", 0, String.format(warningMessage, arguments));
    }


    public void log(int level, String debugMessage, Object... arguments) {
	if (!debug) return;

	print("DEBUG: ", level + startLevel, String.format(debugMessage, arguments));
    }

   
    private void print(String prefix, int indentation, String message, boolean isFirstLine) {
	StringBuilder sb = new StringBuilder(prefix);
	for (int i = 0; i < indentation * INDENTATION_WIDTH; i++) {
	    sb.append(' ');
	}
	sb.append(((message.replace('\n', ' ')).replace('\t', ' ')).trim());
	if (sb.length() > NR_CHARS_ON_LINE) {
	    String firstLine = sb.substring(0, NR_CHARS_ON_LINE);
	    String followingLines = sb.substring(NR_CHARS_ON_LINE, sb.length());
	    out.println(firstLine);
	    print(prefix, isFirstLine ? indentation + 2 : indentation , followingLines, !IS_FIRST_LINE);
	    // all following lines are indented two times more
	}
	else {
	    out.println(sb.toString());
	}
    }

 
    private void print(String prefix, int indentation, String message) {
	print(prefix, indentation, message, IS_FIRST_LINE);
    }


    /*
    public void log2(int level, String debugMessage, Object... arguments) {
	if (!debug) return;

	if (level < 0) throw new Error("printDebug(), level < 0");

	StringBuilder sb = new StringBuilder("DEBUG: ");
	for (int i = 0; i < level + startLevel; i++) sb.append("  ");
	sb.append((debugMessage.replace('\n', ' ')).replace('\t', ' '));
	sb.append('\n');

	String completeMessage = String.format(sb.toString(), arguments);
	if (completeMessage.length() > NR_CHARS_ON_LINE) {
	    out.println(completeMessage.substring(0, NR_CHARS_ON_LINE));
	    log(level + 2, completeMessage.substring(NR_CHARS_ON_LINE, 
			completeMessage.length()));
	}
	else {
	    out.print(completeMessage);
	}
    }
    */
}
