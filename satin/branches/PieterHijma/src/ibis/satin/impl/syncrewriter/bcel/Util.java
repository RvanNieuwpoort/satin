package ibis.satin.impl.syncrewriter.bcel;

import java.util.ArrayList;

import org.apache.bcel.classfile.Method;

import org.apache.bcel.verifier.structurals.ControlFlowGraph;
import org.apache.bcel.verifier.structurals.InstructionContext;

import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.AASTORE;
import org.apache.bcel.generic.BASTORE;
import org.apache.bcel.generic.CASTORE;
import org.apache.bcel.generic.DASTORE;
import org.apache.bcel.generic.FASTORE;
import org.apache.bcel.generic.IASTORE;
import org.apache.bcel.generic.LASTORE;
import org.apache.bcel.generic.SASTORE;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.ConstantPoolGen;


/** Utility class for some typical bcel methods.
 *
 */
public class Util {

    /** Tests whether an instruction is handled by an exception handler.
     *
     * @param codeExceptionGen The exception handler handling the instruction
     * @param ih The instruction handle that should be handled by the exception
     * handler.
     * @return true if the exception handler handles instruction handle ih;
     * false otherwise.
     */
    public static boolean containsTarget(CodeExceptionGen codeExceptionGen, InstructionHandle ih) {
	int startPositionHandler = codeExceptionGen.getStartPC().getPosition();
	int endPositionHandler = codeExceptionGen.getEndPC().getPosition();
	int positionInstruction = ih.getPosition();
	return positionInstruction >= startPositionHandler && positionInstruction <= endPositionHandler;
    }
}
