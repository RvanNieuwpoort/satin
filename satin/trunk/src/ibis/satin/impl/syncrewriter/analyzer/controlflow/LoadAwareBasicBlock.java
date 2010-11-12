package ibis.satin.impl.syncrewriter.analyzer.controlflow;



import ibis.satin.impl.syncrewriter.controlflow.BasicBlock;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ANEWARRAY;
import org.apache.bcel.generic.ConstantPushInstruction;
import org.apache.bcel.generic.DUP;
import org.apache.bcel.generic.INVOKESPECIAL;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.MULTIANEWARRAY;
import org.apache.bcel.generic.NEW;
import org.apache.bcel.generic.NEWARRAY;
import org.apache.bcel.verifier.structurals.InstructionContext;



class LoadAwareBasicBlock extends BasicBlock {


    private static final boolean IGNORE_FIRST_INSTRUCTIONS = true;



    /* package methods */

    LoadAwareBasicBlock(BasicBlock basicBlock) {
	super(basicBlock);
    }


    boolean containsLoadWithIndex(LocalVariableGen lg) {
	return containsLoadWithIndexAfter(null, !IGNORE_FIRST_INSTRUCTIONS, lg);
    }


    /* tests whether this basic block contains load with index localVarIndex
     * after instructionHandle ih */
    boolean containsLoadWithIndexAfter(InstructionHandle ih, LocalVariableGen lg) {
	return containsLoadWithIndexAfter(ih, IGNORE_FIRST_INSTRUCTIONS, lg);
    }


    boolean containsLoadWithIndexBefore(InstructionHandle ih, LocalVariableGen lg) {
        for (InstructionContext ic : instructions) {
            InstructionHandle current = ic.getInstruction();
            if (current.equals(ih)) {
                break;
            }
            LocalVariableGen l = methodGen.findLocalVar(current, lg.getIndex(), false);
            if (l == lg && methodGen.instructionLoadsTo(current, lg.getIndex())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks that any store in this basic block to the specified variable is the
     * result of a new() or a null.
     * @param ih handle up to where to investigate.
     * @param localVarIndex the local variable index
     * @return true if all stores are OK or there are no stores.
     */
    boolean noAliasesStoreWithIndexBefore(InstructionHandle ih, LocalVariableGen lg) {
        InstructionHandle prev = null;
        for (InstructionContext ic : instructions) {
            InstructionHandle current = ic.getInstruction();
            if (current.equals(ih)) {
                break;
            }

            if (methodGen.instructionStoresTo(current, lg.getIndex())) {
                LocalVariableGen l1 = methodGen.findLocalVar(current, lg.getIndex(), false);
                if (l1 != lg) {
                    prev = current;
                    continue;
                }
                if (prev != null) {
                    Instruction i = prev.getInstruction();
                    if (i instanceof INVOKESPECIAL) {
                        INVOKESPECIAL invoker = (INVOKESPECIAL) i;
                        if (invoker.getMethodName(methodGen.getConstantPool()).equals("<init>")
                            && isEmptyConstructor(invoker)) {
                            InstructionHandle d = prev.getPrev();
                            if (d != null) {
                                i = d.getInstruction();
                                if (i instanceof DUP) {
                                    d = d.getPrev();
                                    if (d != null) {
                                        i = d.getInstruction();
                                        if (i instanceof NEW) {
                                            prev = current;
                                            continue;
                                        }
                                    }
                                }
                            }
                        }
                     } else if (i instanceof NEWARRAY
                            || i instanceof ANEWARRAY || i instanceof MULTIANEWARRAY
                            || i instanceof ConstantPushInstruction) {
                         prev = current;
                         continue;
                    }
                } 
                return false;

            }
            prev = current;
        }
        return true;
    }
    
    boolean isEmptyConstructor(INVOKESPECIAL invoker) {
        String className = invoker.getClassName(methodGen.getConstantPool());
        if (className.equals("java.lang.Object")) {
            return true;
        }
        JavaClass javaClass = Repository.lookupClass(className);
        if (javaClass == null) {
            return false;
        }
        return emptyConstructor(javaClass);
    }
    
    boolean emptyConstructor(JavaClass javaClass) {
        if (javaClass.getClassName().equals("java.lang.Object")) {
            return true;
        }
        Method[] methods = javaClass.getMethods();
        for (Method method : methods) {
            if (method.getName().equals("<init>") && method.getSignature().equals("()V")) {
                byte[] code = method.getCode().getCode();
                // empty constructor only invokes super() and returns, which gives 5 bytes of code.
                if (code.length > 5) {
                    // non-empty constructor.
                    // empty constructor only contains call to super:
                    // ALOAD 0; INVOKESPECIAL <init>; RETURN
                    return false;
                }
                return emptyConstructor(javaClass.getSuperClass());
            }
        }
        return false;
    }
    
    boolean noAliasesStoreWithIndex(LocalVariableGen lg) {
        return noAliasesStoreWithIndexBefore(null, lg);
    }

    /* private methods */

    private boolean containsLoadWithIndexAfter(InstructionHandle ih, boolean ignoreInstructions, LocalVariableGen lg) {
	for (InstructionContext ic : instructions) {
	    InstructionHandle current = ic.getInstruction();
	    if (ignoreInstructions) { 
	        ignoreInstructions = !current.equals(ih);
	    }
	    else if (lg.containsTarget(current) && methodGen.instructionLoadsTo(current, lg.getIndex())) {
	        return true;
	    }
	}
	return false;
    }
    
    
}
