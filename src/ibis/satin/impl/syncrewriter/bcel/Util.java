package ibis.satin.impl.syncrewriter.bcel;

import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.InstructionHandle;

/**
 * Utility class for some typical bcel methods.
 * 
 */
public class Util {

    /**
     * Tests whether an instruction is handled by an exception handler.
     * 
     * @param codeExceptionGen
     *            The exception handler handling the instruction
     * @param ih
     *            The instruction handle that should be handled by the exception
     *            handler.
     * @return true if the exception handler handles instruction handle ih;
     *         false otherwise.
     */
    public static boolean containsTarget(CodeExceptionGen codeExceptionGen,
	    InstructionHandle ih) {
	int startPositionHandler = codeExceptionGen.getStartPC().getPosition();
	int endPositionHandler = codeExceptionGen.getEndPC().getPosition();
	int positionInstruction = ih.getPosition();
	return positionInstruction >= startPositionHandler
		&& positionInstruction <= endPositionHandler;
    }
}
