package ibis.satin.impl.syncrewriter.util;



import java.io.PrintStream;



/** Class to make debugging a bit easier. 
 *
 * Supports indenting.
 *
 */
public class Debug {



    public static final int NR_CHARS_ON_LINE = 80;
    public static final int INDENTATION_WIDTH = 2;

    private static final boolean IS_FIRST_LINE = true;


    private boolean debug;
    private PrintStream out;
    private int startLevel;



    /** Instantiate a debug class on stdout without debugging on.
     */
    public Debug() {
	this.debug = false;
	this.out = System.out;
	this.startLevel = 0;
    }


    /** Instantiate a debug class on stdout with a certain start level.
     *
     * @param turnOn indicates if debugging should be on or off
     * @param startLevel the start level of the indentation.
     */
    public Debug(boolean turnOn, int startLevel) {
	this.debug = turnOn;
	this.out = System.out;
	this.startLevel = startLevel;
    }


    /** Returns the start level.
     *
     * @return The start level of the indentation.
     */
    public int getStartLevel() {
	return startLevel;
    }


    /** Turns on debugging.
     */
    public void turnOn() {
	debug = true;
    }


    /** Turns debugging off.
     */
    public void turnOff() {
	debug = false;
    }


    /** Tests whether debugging is turned on.
     * @return true if debugging is on; false otherwise.
     */
    public boolean turnedOn() {
	return debug;
    }

    /** Prints an error message.
     *
     * The startLevel will be ignored. So there is no indentation with error
     * messages.
     *
     * @param errorMessage The error message to be printed.
     * @param arguments Arguments satisfying the format specifiers of the
     * errorMessage.
     */
    public void error(String errorMessage, Object... arguments) {
	print("ERROR: ", 0, String.format(errorMessage, arguments));
    }


    /** Prints a warning message.
     *
     * The startLevel will be ignored. So there is no indentation with warning
     * messages.
     *
     * @param warningMessage The warning message to be printed.
     * @param arguments Arguments satisfying the format specifiers of the
     * warningMessage.
     */
    public void warning(String warningMessage, Object... arguments) {
	print("WARNING: ", 0, String.format(warningMessage, arguments));
    }


    /** Log (print) a debug message.
     *
     * If debugging is turned off, nothing will be printed. The warning message
     * will be printed with indentation equal to level + the start level of
     * this debug instance.
     *
     * @param level The level of indentation of the message
     * @param debugMessage The debugMessage to be printed.
     * @param arguments The arguments satisfying the format specifiers of the
     * debugMessage.
     */
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
	    String firstLine = null;
	    String followingLines = null;
	    for (int i = NR_CHARS_ON_LINE; i < sb.length(); i++) {
	        if (sb.charAt(i) == ' ') {
	            firstLine = sb.substring(0, i);
	            if (i+1 < sb.length()) { 
	                followingLines = sb.substring(i+1, sb.length());
	            }
	            break;
	        }
	    }
	    if (firstLine == null) {
	        firstLine = sb.toString();
	    }
	    out.println(firstLine);
	    if (followingLines != null) {
	        print(prefix, isFirstLine ? indentation + 2 : indentation , followingLines, !IS_FIRST_LINE);
	    }
	    // all following lines are indented two times more
	}
	else {
	    out.println(sb.toString());
	}
    }

 
    private void print(String prefix, int indentation, String message) {
	print(prefix, indentation, message, IS_FIRST_LINE);
    }
}
