package ibis.satin.impl.syncrewriter.analyzer.controlflow;



import ibis.satin.impl.syncrewriter.bcel.MethodGen;
import ibis.satin.impl.syncrewriter.controlflow.BasicBlock;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ACONST_NULL;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.ANEWARRAY;
import org.apache.bcel.generic.CHECKCAST;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPushInstruction;
import org.apache.bcel.generic.INVOKESPECIAL;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.MULTIANEWARRAY;
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


    boolean noAliasesLoadWithIndex(LocalVariableGen lg) {
	return noAliasesLoadWithIndex(null, !IGNORE_FIRST_INSTRUCTIONS, lg);
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
    

    boolean noAliasesLoadWithIndexBefore(InstructionHandle ih, LocalVariableGen lg) {
        for (InstructionContext ic : instructions) {
            InstructionHandle current = ic.getInstruction();
            if (current.equals(ih)) {
                break;
            }
            LocalVariableGen l = methodGen.findLocalVar(current, lg.getIndex(), false);
            if (l == lg && methodGen.instructionLoadsTo(current, lg.getIndex())) {
		// OK, there is a load, but maybe it does not create an alias?
		if (! methodGen.isUsedForArrayLoad(current) && ! methodGen.isUsedForArrayLength(current)
			&& ! methodGen.isUsedForArrayStore(current) && ! methodGen.isUsedForPutField(current)
			&& ! methodGen.isUsedForGetField(current)) {
		    return false;
		}
            }
        }
        return true;
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
                            && isNonEscapingConstructor(invoker)) {
                            continue;
                        }
                    }
                    if (i instanceof CHECKCAST) {
                	InstructionHandle pp = prev.getPrev();
                	if (pp != null) {
                	    i = pp.getInstruction();
                	}
                    }
                    if (i instanceof NEWARRAY
                            || i instanceof ANEWARRAY || i instanceof MULTIANEWARRAY
                            || i instanceof ConstantPushInstruction || i instanceof ACONST_NULL) {
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
    
    boolean isNonEscapingConstructor(INVOKESPECIAL invoker) {
        String className = invoker.getClassName(methodGen.getConstantPool());
        JavaClass javaClass;
        try {
            javaClass = Repository.lookupClass(className);
        } catch(ClassNotFoundException e) {
            return false;
        }
        return nonEscapingConstructor(javaClass, invoker.getSignature(methodGen.getConstantPool()));
    }
    
    boolean nonEscapingConstructor(JavaClass javaClass, String constructorSignature) {
        if (javaClass.getClassName().equals("java.lang.Object")) {
            return true;
        }
        JavaClass superClass;
        try {
            superClass = javaClass.getSuperClass();
        } catch(ClassNotFoundException e) {
            throw new Error("Superclass of " + javaClass.getClassName()
                    + " not found");
        }
        Method[] methods = javaClass.getMethods();
        for (Method method : methods) {
            if (method.getName().equals("<init>") && method.getSignature().equals(constructorSignature)) {
        	if (nonEscapingConstructor(superClass, "()V")) {
        	    ClassGen cg = new ClassGen(javaClass);
        	    MethodGen mg = new MethodGen(method, javaClass.getClassName(), cg.getConstantPool());
        	    // Now check all ALOAD 0 instructions. They may only be used for
        	    // PUTFIELD and for calling super().
        	    InstructionHandle h = mg.getInstructionList().getStart();
        	    while (h != null) {
        		Instruction i = h.getInstruction();
        		if (i instanceof ALOAD && ((ALOAD) i).getIndex() == 0) {
        		    if (! mg.isUsedForPutField(h)) {
        			// Find instructions that consume exactly this load (but not more,
        			// otherwise it could be a parameter to another constructor).
        			InstructionHandle[] users = mg.findExactInstructionConsumers(h);
        			if (users.length != 1) {
        			    return false;
        			}
        	                i = users[0].getInstruction();
        	                if (i instanceof INVOKESPECIAL) {
        	                    INVOKESPECIAL invoker = (INVOKESPECIAL) i;
        	                    if (! invoker.getMethodName(mg.getConstantPool()).equals("<init>")
        	                	    || ! nonEscapingConstructor(superClass, invoker.getSignature(mg.getConstantPool()))) {
        	                	return false;
        	                    }
        	                } else {
        	                    return false;
        	                }
        		    }
        		}
        		h = h.getNext();
        	    }

        	    return true;
        	}
            }
        }
        return false;
    }
    
    boolean noAliasesStoreWithIndex(LocalVariableGen lg) {
        return noAliasesStoreWithIndexBefore(null, lg);
    }

    /* private methods */

    private boolean containsLoadWithIndexAfter(InstructionHandle ih, boolean ignoreInstructions, LocalVariableGen lg) {
        InstructionHandle start = lg.getStart();
        InstructionHandle prev = start.getPrev();
        // The initial store is not included in the start/end range, so include the previous
        // instruction if it exists.
        InstructionHandle end = lg.getEnd();
        int startPosition = prev != null ? prev.getPosition() : start.getPosition();
        int endPosition = end.getPosition();
	for (InstructionContext ic : instructions) {
	    InstructionHandle current = ic.getInstruction();
	    if (ignoreInstructions) { 
	        ignoreInstructions = !current.equals(ih);
	    } else if (current.getPosition() >= startPosition && current.getPosition() <= endPosition
		    && methodGen.instructionLoadsTo(current, lg.getIndex())) {
	        return true;
	    }
	}
	return false;
    }
    

    private boolean noAliasesLoadWithIndex(InstructionHandle ih, boolean ignoreInstructions, LocalVariableGen lg) {
        InstructionHandle start = lg.getStart();
        InstructionHandle prev = start.getPrev();
        // The initial store is not included in the start/end range, so include the previous
        // instruction if it exists.
        InstructionHandle end = lg.getEnd();
        int startPosition = prev != null ? prev.getPosition() : start.getPosition();
        int endPosition = end.getPosition();
	for (InstructionContext ic : instructions) {
	    InstructionHandle current = ic.getInstruction();
	    if (ignoreInstructions) { 
	        ignoreInstructions = !current.equals(ih);
	    } else if (current.getPosition() >= startPosition && current.getPosition() <= endPosition
		    && methodGen.instructionLoadsTo(current, lg.getIndex())) {
		// OK, there is a load, but maybe it does not create an alias?
		if (! methodGen.isUsedForArrayLoad(current) && ! methodGen.isUsedForArrayLength(current)
			&& ! methodGen.isUsedForArrayStore(current) && ! methodGen.isUsedForPutField(current)
			&& ! methodGen.isUsedForGetField(current)) {
		    return false;
		}
	    }
	}
	return true;
    }
    
    
}
