package ibis.satin.impl.syncrewriter;


import java.io.PrintStream;

import java.util.ArrayList;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.Repository;

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.StackConsumer;
import org.apache.bcel.generic.StackProducer;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.DASTORE;
import org.apache.bcel.generic.ALOAD;




/* Three possible implementations:
 *
 * naieve:
 *  res = spawnableCall();
 *  sync();
 *
 * first load
 *  res = spawnableCall();
 *  doStuff();
 *  sync();
 *  read(res);
 *
 * control flow analysis
 *
 * This is the first load implementation. 
 * It fails with Checkers.play();
 *  res = spawnableCall();
 *  if (condition) {
 *	sync();
 *	read(res);
 *  }
 *  read(res);
 *
 */
class SyncRewriter2 {


    private Debug d;


    private class SpawnableMethodCall {


	private InstructionHandle ih;
	private InstructionHandle objectReference;
	private int resultIndex;


	private SpawnableMethodCall(InstructionHandle ih, 
		InstructionHandle objectReference, int resultIndex) {
	    this.ih = ih;
	    this.objectReference = objectReference;
	    this.resultIndex = resultIndex;
	}

	public String toString() {
	    return String.format(
		    "InstructionHandle: %s\nobjectReference: %s\nresultIndex: %s\n", ih, objectReference, resultIndex);
	}
    }


    SyncRewriter2() {
	d = new Debug();
	d.turnOn();
    }


    JavaClass getClassFromName(String fileName) {
	String className = fileName.endsWith(".class") ? 
	    fileName.substring(0, fileName.length() - 6) : fileName;
	JavaClass javaClass = Repository.lookupClass(className);

	if (javaClass == null) {
	    System.out.println("class " + className + " not found");
	    System.exit(1);
	}

	return javaClass;
    }


    void printUsage() {
	System.out.println("Usage:");
	System.out.println("syncrewriter [options] classname...");
	System.out.println("  example syncrewriter mypackage.MyClass");
	System.out.println("Options:");
	System.out.println("  -debug      print debug information");
	System.out.println("  -help       this information");
    }


    Method[] getSpawnableMethods(JavaClass spawnableClass) {
	Method[] spawnableMethods = null;/*new Method[0];*/

	JavaClass[] interfaces = spawnableClass.getInterfaces();
	for (JavaClass javaInterface : interfaces) {
	    if (isSpawnable(javaInterface)) {
		spawnableMethods = javaInterface.getMethods();
		// it is certain that only one interface is spawnable
	    }
	}

	if (spawnableMethods == null) {
	    throw new Error("no methods specified in " +
		    spawnableClass.getClassName());
	}

	return spawnableMethods;
    }


    /*
    int getNrWordsArguments(MethodGen methodGen) {
	int nrWords = 0;
	for (Type type : methodGen.getArgumentTypes()) {
	    nrWords += type.getSize();
	}
	return nrWords;
    }
    */


    /*
    InstructionHandle getArrayReferenceLoadInstruction(InstructionHandle ih,
	    int nrWords, ConstantPoolGen constantPoolGen) {
	int count = 10;
	while (count != 0) {
	    ih = ih.getPrev();
	    Instruction instruction = ih.getInstruction();
	    System.out.println("instructie " + instruction);
	    if (instruction instanceof StackProducer) {
		System.out.printf("produceert %d op de stack\n", 
			instruction.produceStack(constantPoolGen));
	    }
	    if (instruction instanceof StackConsumer) {
		System.out.printf("consumeert %d op de stack\n", 
			instruction.consumeStack(constantPoolGen));
	    }
	    System.out.println();
	    count--;
	}
	return ih;
    }
    */


    /* Get the corresponding object reference load instruction of instruction
     * ih.
     */
    InstructionHandle getObjectReferenceLoadInstruction(InstructionHandle ih, 
	    ConstantPoolGen constantPoolGen) {
	Instruction instruction = ih.getInstruction();
	int stackConsumption = instruction.consumeStack(constantPoolGen);
	//stackConsumption--; // we're interested in the first one

	while (stackConsumption != 0) {
	    ih = ih.getPrev();
	    Instruction previousInstruction = ih.getInstruction();
	    //out.println(instruction);
	    //if (instruction instanceof StackProducer) {
	    stackConsumption -= 
		previousInstruction.produceStack(constantPoolGen);
	    //}
	    //if (instruction instanceof StackConsumer) {
	    stackConsumption += 
		previousInstruction.consumeStack(constantPoolGen);
	    //}
	}
	return ih;
    }


    /* Get the load instruction corresponding to this spawnable call.
    */
    InstructionHandle getLoadInstruction(InstructionList il, 
	    SpawnableMethodCall spawnableCall) throws NeverReadException {

	InstructionHandle ih = spawnableCall.ih;
	while ((ih = ih.getNext()) != null) {
	    try {
		LoadInstruction loadInstruction = 
		    (LoadInstruction) (ih.getInstruction());
		if (loadInstruction.getIndex() == spawnableCall.resultIndex) {
		    return ih;
		}
	    }
	    catch (ClassCastException e) {
	    }
	}
	throw new NeverReadException();
    }


    /* Get the earliest load instruction of the results of the spawnable calls.
     *
     * result1 = spawnableCall();
     * result2 = spawnableCall();
     *
     * read(result2); <---- this is returned
     * read(result1);
     */
    InstructionHandle getEarliestLoadInstruction(InstructionList il,
	    ArrayList<SpawnableMethodCall> spawnableCalls) 
	throws NeverReadException {

	InstructionHandle earliestLoadInstruction = null;
	for (SpawnableMethodCall spawnableCall : spawnableCalls) {
	    InstructionHandle loadInstruction = 
		getLoadInstruction(il, spawnableCall);
	    if (earliestLoadInstruction == null || loadInstruction.getPosition()
		    < earliestLoadInstruction.getPosition()) {
		earliestLoadInstruction = loadInstruction;
		    }
	}
	return earliestLoadInstruction;
    }


    void warning(String warningMessage, Object... arguments) {
	System.out.printf("WARNING: " + warningMessage, arguments);
    }


    void insertSync(MethodGen methodGen, 
	    ArrayList<SpawnableMethodCall> spawnableCalls, int indexSync) 
	throws NeverReadException {

	d.log(5, "trying to insert a sync statement\n");


	CodeBlockGraph codeBlockGraph = new CodeBlockGraph(methodGen);

	if (spawnableCalls.size() > 1) {
	    SpawnableMethodCall firstSpawnableCall = spawnableCalls.get(0);
	    int indexCodeBlock = 
		codeBlockGraph.getIndexCodeBlock(firstSpawnableCall.ih);
	    for (int i = 1; i < spawnableCalls.size(); i++) {
		SpawnableMethodCall spawnableCall = spawnableCalls.get(i);
		int tmpIndexCodeBlock = 
		    codeBlockGraph.getIndexCodeBlock(spawnableCall.ih);
		if (tmpIndexCodeBlock != indexCodeBlock) {
		    warning("the following case is unaccounted for:\n%s\n",
			    "\tmultiple spawnable calls, but not in the same control flow situation");
		    return;
		}
	    }
		System.out.println("can successfully insert a sync\n");
	}
	else {
	    SpawnableMethodCall spawnableCall = spawnableCalls.get(0);
	    System.out.println(spawnableCall);
	    int indexCodeBlock = 
		codeBlockGraph.getIndexCodeBlock(spawnableCall.ih);
	    ArrayList<Path> endingPaths = 
		codeBlockGraph.getEndingPathsFrom(indexCodeBlock);
	    System.out.println("endingPaths:");
	    for (Path path : endingPaths) System.out.println(path);
	    ArrayList<StoreLoadPath> storeLoadPaths = 
		new ArrayList<StoreLoadPath>();
	    System.out.println("storeLoadPaths:");
	    for (Path path : endingPaths) {
		StoreLoadPath storeLoadPath = null;
		try {
		    storeLoadPath = new StoreLoadPath(
			spawnableCall.ih, path, spawnableCall.resultIndex);
		}
		catch (NeverReadException e) {
		}
		if (storeLoadPath != null && !storeLoadPaths.contains(storeLoadPath)) {
		    storeLoadPaths.add(storeLoadPath);
		    System.out.println(storeLoadPath);
		}
	    }

	    Path finalPath = (Path) storeLoadPaths.get(0);
	    for (int i = 1; i < storeLoadPaths.size(); i++) {
		finalPath = finalPath.getCommonSubPathFromStart(
			(Path)(storeLoadPaths.get(i)));
	    }

	    System.out.printf("final path: %s\n", finalPath);




	    /*
	       for (Path path : endingPaths) {
	       d.log(6, "path: %s\n", path);
	       try {
	       int indexCodeBlockEarliestLoad = 
	       path.getIndexEarliestLoadAfter(spawnableCall.ih, 
	       spawnableCall.resultIndex);
	       d.log(7, "the result is read in codeBlock: %d\n", 
	       indexCodeBlockEarliestLoad);
	       }
	       catch (NeverReadException e) {
	       d.log(7, "the result is not read in this path\n");
	       }
	       }
	       */
	}
    }


    /*


       int indexCodeBlock = 0;
       for (SpawnableMethodCall spawnableCall : spawnableCalls) {
       d.log(6, "indexCodeBlock: %d\n", indexCodeBlock);
       }

    //InstructionHandle earliestLoadInstruction = path.getEarliestLoadInstruction(
}
d.log(6, "sync statement inserted\n");
*/










/*
   InstructionHandle earliestLoadInstruction = 
   getEarliestLoadInstruction(instructionList, spawnableCalls);

   InstructionHandle syncInvoke = 
   instructionList.insert(earliestLoadInstruction, 
   new INVOKEVIRTUAL(indexSync));
   instructionList.insert(syncInvoke, 
   spawnableCalls.get(0).objectReference.getInstruction());
   */


/* Get the local variable index of the result of the spawnable methode
 * invoke.
 */
int getLocalVariableIndexResult(InstructionHandle spawnableMethodInvoke,
	InstructionList instructionList, ConstantPoolGen constantPoolGen) {

    InstructionHandle storeInstructionHandle = 
	spawnableMethodInvoke.getNext();

    try {
	StoreInstruction storeInstruction = (StoreInstruction) 
	    (storeInstructionHandle.getInstruction());
	return storeInstruction.getIndex();
    }
    catch (ClassCastException e) {
    }
    try {
	ArrayInstruction arrayStoreInstruction = (ArrayInstruction)
	    ((StackConsumer)(storeInstructionHandle.getInstruction()));
	InstructionHandle ih  = getObjectReferenceLoadInstruction
	    (storeInstructionHandle, constantPoolGen);
	ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
	return objectLoadInstruction.getIndex();
    }
    catch (ClassCastException e) {
    }
    //out.printf("He, hier gaat het niet goed: %s\n");
    throw new Error("He, hier gaat het niet goed\n");
}


ArrayList<SpawnableMethodCall> getSpawnableCalls(InstructionList il, 
	ConstantPoolGen constantPoolGen, MethodGen spawnableMethodGen) {

    int indexSpawnableMethod = 
	constantPoolGen.lookupMethodref(spawnableMethodGen);
    INVOKEVIRTUAL spawnableInvoke = 
	new INVOKEVIRTUAL(indexSpawnableMethod);

    ArrayList<SpawnableMethodCall> spawnableCalls = 
	new ArrayList<SpawnableMethodCall>();

    InstructionHandle ih = il.getStart();
    do {
	if (ih.getInstruction().equals(spawnableInvoke)) {
	    d.log(5, "the spawnable method is invoked, rewriting\n");

	    SpawnableMethodCall spawnableCall = new SpawnableMethodCall(ih, 
		    getObjectReferenceLoadInstruction(ih, constantPoolGen), 
		    getLocalVariableIndexResult(ih, il, 
			constantPoolGen));
	    spawnableCalls.add(spawnableCall);
	}
    } while((ih = ih.getNext()) != null);

    return spawnableCalls;
}


/* Evaluate the method and rewrite in case spawnableMethod is called. 
*/
Method evaluateMethod(Method method, Method spawnableMethod, 
	ClassGen newSpawnableClass, int indexSync) {

    d.log(4, "evaluating method %s for spawnable calls\n", method);

    ConstantPoolGen constantPoolGen = newSpawnableClass.getConstantPool();
    MethodGen methodGen = new MethodGen(method, 
	    newSpawnableClass.getClassName(), constantPoolGen);
    MethodGen spawnableMethodGen = new MethodGen(spawnableMethod, 
	    newSpawnableClass.getClassName(), constantPoolGen);

    InstructionList instructionList = methodGen.getInstructionList();

    ArrayList<SpawnableMethodCall> spawnableCalls = 
	getSpawnableCalls(instructionList, constantPoolGen, 
		spawnableMethodGen);

    if (spawnableCalls.size() > 0) {
	try {
	    insertSync(methodGen, spawnableCalls, indexSync);
	}
	catch (NeverReadException e) {
	    System.out.println(e.getMessage());
	}
    }
    return methodGen.getMethod();
}


/* Rewrite the methods that invoke spawnableMethod.
*/
void rewriteForSpawnableMethod(ClassGen newSpawnableClass, 
	Method spawnableMethod, int indexSync) {

    Method[] methods = newSpawnableClass.getMethods();

    for (Method method : methods) {
	Method newMethod = evaluateMethod(method, spawnableMethod, 
		newSpawnableClass, indexSync);
	newSpawnableClass.removeMethod(method);
	newSpawnableClass.addMethod(newMethod);
    }
}


void dumpNewSpawnableClass(ClassGen newSpawnableClass) {
    JavaClass c = newSpawnableClass.getJavaClass();
    //c.setConstantPool
    //	(newSpawnableClass.getConstantPool().getFinalConstantPool());
    dump(c, c.getClassName() + "_.class");
    d.log(3, "spawnable class dumped in %s_.class\n", c.getClassName());
}


void dump(JavaClass javaClass, String name) {
    try {
	javaClass.dump(name);
    }
    catch (Exception e) {
	e.printStackTrace();
    }
}


void backup(JavaClass javaClass) {
    String name = javaClass.getClassName() + ".class";// hier _ 
    //dump(javaClass, name);
    //d.log(3, "class %s backed up in %s\n", 
    d.log(3, "class %s remains in %s\n", javaClass.getClassName(), name);
}


void rewriteSpawnableClass(JavaClass spawnableClass) {
    d.log(2, "rewriting spawnable class %s\n", 
	    spawnableClass.getClassName());

    backup(spawnableClass);

    //CONSTANTPOOL/////////////////////////////////////////////////////////
    //out.println(spawnableClass.getConstantPool());
    ///////////////////////////////////////////////////////////////////////

    ClassGen newSpawnableClass = new ClassGen(spawnableClass);
    int indexSync = newSpawnableClass.getConstantPool().addMethodref(
	    spawnableClass.getClassName(), "sync", "()V");
    Method[] spawnableMethods = getSpawnableMethods(spawnableClass);

    for (Method spawnableMethod : spawnableMethods) {
	d.log(3, "spawnable method for which %s %s\n", 
		"methods will be rewritten: ", 
		spawnableMethod);
	rewriteForSpawnableMethod(newSpawnableClass, spawnableMethod,
		indexSync);
	d.log(3, "methods rewritten for spawnable method %s\n", 
		spawnableMethod);
    }

    dumpNewSpawnableClass(newSpawnableClass);
    d.log(2, "spawnable class %s rewritten\n", 
	    spawnableClass.getClassName());
}


boolean isSpawnable(JavaClass javaClass) {
    JavaClass[] interfaces = javaClass.getAllInterfaces();

    for (JavaClass javaInterface : interfaces) {
	if (javaInterface.getClassName().equals("ibis.satin.Spawnable")) {
	    return true;
	}
    }

    return false;
}


/* Evaluate a class and rewrite if spawnable.
*/
void evaluateClass(String className) {
    d.log(0, "evaluating class %s\n", className);

    JavaClass javaClass = getClassFromName(className);

    if (javaClass.isClass() && isSpawnable(javaClass)) {
	d.log(1, "%s is a spawnable class\n", className);
	rewriteSpawnableClass(javaClass);
    }
    else {
	// nothing
	d.log(1, "%s not a spawnable class, not rewritten\n", className);
    }
    d.log(0, "class %s evaluated\n\n", className);
}


/* Evaluate classes and rewrite the spawnable classes.
*/ 
void evaluateClasses(ArrayList<String> classNames) {
    for (String className : classNames) {
	evaluateClass(className);
    }
}


/* Process the arguments.
 *
 * Everything that doesn't start with a - is considered a class file.
 * A class file can end with .class or not.
 */
ArrayList<String> processArguments(String[] argv) {
    ArrayList<String> classNames = new ArrayList<String>();

    for (String arg : argv) {
	if (!arg.startsWith("-")) {
	    classNames.add(arg);
	}
	else if (arg.equals("-help")) {
	    printUsage();
	    System.exit(0);
	}
	else if (arg.equals("-debug")) {
	    d.turnOn();
	}
    }

    if (classNames.isEmpty()) {
	System.out.println("No classFiles specified");
	printUsage();
	System.exit(1);
    }

    return classNames;
}


void start(String[] argv) {
    ArrayList<String> classNames = processArguments(argv);
    evaluateClasses(classNames);
}


public static void main(String[] argv) {
    new SyncRewriter2().start(argv);
}
}
