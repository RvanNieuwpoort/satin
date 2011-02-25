package ibis.satin.impl.syncrewriter;

import ibis.satin.impl.syncrewriter.bcel.MethodGen;
import ibis.satin.impl.syncrewriter.bcel.Util;
import ibis.satin.impl.syncrewriter.util.Debug;

import java.util.ArrayList;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LineNumberTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.ObjectType;


/** This class represents a spawnable method. 
 *
 * A spawning method contains spawnable calls.
 */
public class SpawningMethod extends MethodGen {

    private static final long serialVersionUID = 1L;

    private class ResultNotStored extends Exception {

        private static final long serialVersionUID = 1L;
    }


    private ArrayList<SpawnableCall> spawnableCalls;
    private int indexSync;
    // private SpawnSignature spawnSignature;

    private Debug d;

    /* public methods */

    /** Returns the spawnable calls in this spawnable method.
     *
     * @return The spawnable calls in this method.
     */
    public ArrayList<SpawnableCall> getSpawnableCalls() {
	return spawnableCalls;
    }

    /* package methods */

    SpawningMethod (Method method, String className, ConstantPoolGen constantPoolGen, 
	    SpawnSignature spawnSignature, int indexSync, Debug d) 
	throws NoSpawningMethodException, AssumptionFailure {

	super(method, className, constantPoolGen);
	
	if (callsSync()) {
	    throw new MethodCallsSyncException();
	}

	MethodGen spawnSignatureGen = new MethodGen(spawnSignature.getMethod(), spawnSignature.getClassName(), constantPoolGen);

	ArrayList<SpawnableCall> spawnableCalls = getSpawnableCalls(constantPoolGen, spawnSignatureGen);

	if (spawnableCalls.size() > 0) {
	    this.spawnableCalls = spawnableCalls;
	    this.indexSync = indexSync;
	    // this.spawnSignature = spawnSignature;
	    this.d = d;
	}
	else {
	    throw new NoSpawningMethodException();
	}	
    }

    void rewrite(Analyzer analyzer) throws MethodRewriteFailure {
	d.log(0, "rewriting %s\n", getName());
	insertSync(analyzer);
	d.log(0, "rewrote %s\n", getName());
    }
    
    void adviseSync(Analyzer analyzer) throws SyncInsertionProposalFailure {
        d.log(0, "adviseSync %s\n", getName());
        d.log(1, "trying to find sync statement location(s)\n");
        InstructionHandle[] ihs = 
            analyzer.proposeSyncInsertion(this, new Debug(d.turnedOn(), d.getStartLevel() + 2));

        LineNumberTable t = getLineNumberTable(getConstantPool());
        
        for (InstructionHandle ih : ihs) {
            InstructionHandle h = ih.getNext();
            if (h == null) {
                h = ih;
            }
            int l = t.getSourceLine(h.getPosition());
            System.out.println("Insert a sync() in method "
                    + getName() + " at line "+ l + " in class " + getClassName());
        }
    }

    /* private methods */
    
    private void insertSync(Analyzer analyzer) throws MethodRewriteFailure {
	d.log(1, "trying to insert sync statement(s)\n");
	InstructionHandle[] ihs = 
	    analyzer.proposeSyncInsertion(this, new Debug(d.turnedOn(), d.getStartLevel() + 2));

	InstructionList instructionList = getInstructionList();

	for (InstructionHandle ih : ihs) {
	    InstructionHandle syncInvoke = 
		instructionList.insert(ih, 
			new INVOKEVIRTUAL(indexSync));
	    InstructionHandle newTarget = instructionList.insert(syncInvoke, 
		    spawnableCalls.get(0).getObjectReference().getInstruction());
	    // the same objectReference for every sync insertion
	    instructionList.redirectBranches(ih, newTarget);
	    d.log(2, "inserted sync()\n");
	}
	d.log(1, "inserted sync statement(s)\n");
    }


    private boolean callsSync() {
	ConstantPoolGen cpg = getConstantPool();
	InstructionList instructionList = getInstructionList();
	InstructionHandle handle = instructionList.getStart();
	while (handle != null) {
	    Instruction ins = handle.getInstruction();
	    if (ins instanceof INVOKEVIRTUAL) {
		INVOKEVIRTUAL inv = (INVOKEVIRTUAL) ins;
		System.err.println("Invoke " + inv.getClassName(cpg) + "." + inv.getMethodName(cpg) + ": "+ inv.getSignature(cpg));
		if (inv.getMethodName(cpg).equals("sync") &&
			inv.getSignature(cpg).equals("()V")) {
		    JavaClass cl = findMethodClass(inv, cpg);
		    if (cl != null && cl.getClassName().equals("ibis.satin.SatinObject")) {
			System.out.println("Method "
				+ getName() + " in class " + getClassName() + " already contains a sync call");
			return true;
		    }
		}
	    }
	    handle = handle.getNext();
	}
	return false;
    }
    
    private JavaClass findMethodClass(InvokeInstruction ins, ConstantPoolGen cpg) {
        String name = ins.getMethodName(cpg);
        String sig = ins.getSignature(cpg);
        String cls = ins.getClassName(cpg);

        if (cls.startsWith("[")) {
            cls = "java.lang.Object";
        }
        JavaClass cl = Repository.lookupClass(cls);

        if (cl == null) {
            return null;
        }

        while (cl != null) {
            Method[] methods = cl.getMethods();
            for (int i = 0; i < methods.length; i++) {
                    if (methods[i].getName().equals(name)
                    && methods[i].getSignature().equals(sig)) {
                    return cl;
                }
            }
            cls = cl.getSuperclassName();
            if (cls != null) {
                cl = Repository.lookupClass(cls);
            } else {
                cl = null;
            }
        }
        return null;
    }


    /* determine the spawnable calls */

    private boolean containsType(ObjectType type, ObjectType[] types) {
	for (ObjectType i : types) {
	    if (i.equals(type) || type.subclassOf(i)) return true;
	}
	return false;
    }

    private boolean hasRightType(CodeExceptionGen codeException, MethodGen method) {
	ObjectType catchType = codeException.getCatchType();
	if (catchType == null) return false;
	String[] exceptionTypeNames = method.getExceptions();
	ObjectType[] exceptionTypes = new ObjectType[exceptionTypeNames.length];
	for (int i = 0; i < exceptionTypeNames.length; i++) {
	    exceptionTypes[i] = new ObjectType(exceptionTypeNames[i]);
	}
	return containsType(catchType, exceptionTypes);
    }


    private ArrayList<CodeExceptionGen> getExceptionHandlers(InstructionHandle ih, MethodGen spawnSignature) {
	CodeExceptionGen[] codeExceptions = getExceptionHandlers();
	ArrayList<CodeExceptionGen> result = new ArrayList<CodeExceptionGen>();

	for (CodeExceptionGen codeException : codeExceptions) {
	    // codeException.containsTarget has a BUG????
	    /*
	    if (codeException.containsTarget(ih) && hasRightType(codeException, spawnSignature)) {
		result.add(codeException);
	    }
	    */
	    if (Util.containsTarget(codeException, ih) && hasRightType(codeException, spawnSignature)) {
		result.add(codeException);
	    }
	}

	return result;
    }


    private void getIndicesStores(InstructionHandle start,
	    InstructionHandle end, ArrayList<LocalVariableGen> resultIndices) {

	for (InstructionHandle current = start.getNext() ;current != end.getNext(); current = current.getNext()) {
	    try {
		LocalVariableGen l = getIndexStore(current);
		if (! resultIndices.contains(l)) {
		    resultIndices.add(l);
		}
		for (LocalVariableGen ll : this.getLocalVariables()) {
		    if (ll != l && l.getIndex() == ll.getIndex() && l.getName().equals(ll.getName())) {
			if (! resultIndices.contains(ll)) {
			    resultIndices.add(ll);
			}
		    }		    
		}
	    }
	    catch (ClassCastException e) {
		// no problem, just not a store 
	    }
	}
    }


    private SpawnableCall getSpawnableCallWithException(InstructionHandle invokeInstruction, 
	    ArrayList<CodeExceptionGen> exceptionHandlers) {

	ArrayList<LocalVariableGen> resultIndices = new ArrayList<LocalVariableGen>();
	for (CodeExceptionGen exceptionHandler : exceptionHandlers) {
	    InstructionHandle start = exceptionHandler.getHandlerPC(); 
	    InstructionHandle end = getEndExceptionHandler(exceptionHandler);
	    getIndicesStores(start, end, resultIndices);
	}

	LocalVariableGen[] dummy = new LocalVariableGen[resultIndices.size()];

	return new SpawnableCall(invokeInstruction,
		getObjectReferenceLoadInstruction(invokeInstruction),
		resultIndices.toArray(dummy));
    }


    private SpawnableCall getSpawnableCallWithException(InstructionHandle invokeInstruction, MethodGen spawnSignatureGen) {
	ArrayList<CodeExceptionGen> exceptionHandlers = getExceptionHandlers(invokeInstruction, spawnSignatureGen);


	if (exceptionHandlers.size() == 0) {
	    return new SpawnableCall(invokeInstruction, getObjectReferenceLoadInstruction(invokeInstruction), 
		    SpawnableCall.Type.EXCEPTIONS_NOT_HANDLED);
	}
	else {
	    return getSpawnableCallWithException(invokeInstruction, exceptionHandlers);
	}
    }


    /* Find the index of the variable in which a spawnable call stores.
     */
    private LocalVariableGen[] findIndexStore(InstructionHandle spawnableMethodInvoke) throws ResultNotStored {
	InstructionHandle[] stackConsumers = findInstructionConsumers(spawnableMethodInvoke);
	if (stackConsumers.length != 1) {
	    /*
	    throw new Error("Uncovered situation, invoke instruction's " + 
		    "result is consumed by multiple instructions. " + 
		    "Is there controlflow right after the spawnable invoke???");
		    */
	    throw new ResultNotStored();
	    // I think this is a better solution. Controlflow right after the
	    // invoke is something that is hard to analyze and rare. Better to
	    // just mark it as a result that isn't stored.
	}
	try {
	    ArrayList<LocalVariableGen> resultIndices = new ArrayList<LocalVariableGen>();
	    LocalVariableGen l = getIndexStore(stackConsumers[0]);
	    resultIndices.add(l);
	    for (LocalVariableGen ll : this.getLocalVariables()) {
		if (ll != l && l.getIndex() == ll.getIndex() && l.getName().equals(ll.getName())) {
		    if (! resultIndices.contains(ll)) {
			resultIndices.add(ll);
		    }
		}		    
	    }
	    return resultIndices.toArray(new LocalVariableGen[resultIndices.size()]);
	}
	catch (ClassCastException e) {
	    throw new ResultNotStored();
	}
    }


    private SpawnableCall getSpawnableCallReturningValue(InstructionHandle invokeInstruction) {
	try {
	    return new SpawnableCall(invokeInstruction, 
		    getObjectReferenceLoadInstruction(invokeInstruction), 
		    findIndexStore(invokeInstruction));
	}
	catch (ResultNotStored e) {
	    return new SpawnableCall(invokeInstruction,
		    getObjectReferenceLoadInstruction(invokeInstruction),
		    SpawnableCall.Type.RESULT_NOT_STORED);
	}
    }


    private ArrayList<SpawnableCall> getSpawnableCalls(ConstantPoolGen constantPoolGen, MethodGen spawnSignatureGen) throws 
	AssumptionFailure {

	    ArrayList<SpawnableCall> spawnableCalls = 
		new ArrayList<SpawnableCall>();

	    InstructionList il = getInstructionList(); 
	    int indexSpawnableCall = 
		constantPoolGen.lookupMethodref(spawnSignatureGen);
	    if (indexSpawnableCall < 0) return spawnableCalls;

	    INVOKEVIRTUAL spawnableInvoke = 
		new INVOKEVIRTUAL(indexSpawnableCall);


	    InstructionHandle ih = il.getStart();

	    do {
	        Instruction instruction = ih.getInstruction();
    		if (instruction.equals(spawnableInvoke)) {
    		    if (instruction.produceStack(constantPoolGen) > 0) {
    		        spawnableCalls.add(getSpawnableCallReturningValue(ih));
    		    } else if (instruction.produceStack(constantPoolGen) == 0) {
    		        if (spawnSignatureGen.getExceptions().length > 0) {
    		            spawnableCalls.add(getSpawnableCallWithException(ih, spawnSignatureGen));
    		        } else {
    		            throw new AssumptionFailure("Not satisfying assumption that spawnable method returns something or throws something");
    		        }
    		    } else {
    		        throw new AssumptionFailure("Not satisfying assumption that spawnable method returns something or throws something");
    		    }
    		} else {
    		    // OK
    		}
	    } while((ih = ih.getNext()) != null);

	    return spawnableCalls;
	}
}
