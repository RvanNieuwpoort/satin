package ibis.satin.impl.syncrewriter;


import java.io.PrintStream;

import java.util.ArrayList;

import java.lang.annotation.Annotation;

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



class SyncRewriter {


    static final String[] ANALYZER_NAMES = {"Naive", "EarliestLoad", 
	"ControlFlow"};
    static final boolean RETAIN_ORIGINAL_CLASSFILES = true;
    static final boolean BACKUP_ORIGINAL_CLASSFILES = true;


    private Debug d;
    private Analyzer analyzer;


    SyncRewriter() {
	d = new Debug();
	d.turnOn();
	analyzer = null;
    }


    /*
    Method[] getSpawnableMethods(JavaClass spawnableClass) {
	Method[] spawnableMethods = null;

	JavaClass[] interfaces = spawnableClass.getInterfaces();
	for (JavaClass javaInterface : interfaces) {
	    if (isSpawnable(javaInterface)) {
		spawnableMethods = javaInterface.getMethods();
	    }
	}

	if (spawnableMethods == null) {
	    throw new Error("no methods specified in " +
		    spawnableClass.getClassName());
	}

	return spawnableMethods;
    }
    */


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
    /*
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
    */


    /*
    void insertSync(MethodGen methodGen, 
	    ArrayList<SpawnableMethodCall> spawnableCalls, int indexSync) 
	throws NeverReadException {

	d.log(5, "trying to insert sync statement(s)\n");

	InstructionHandle[] ihs = 
	    analyzer.proposeSyncInsertion(methodGen, spawnableCalls);

	InstructionList instructionList = methodGen.getInstructionList();

	for (InstructionHandle ih : ihs) {
	    InstructionHandle syncInvoke = 
		instructionList.insert(ih, 
			new INVOKEVIRTUAL(indexSync));
	    instructionList.insert(syncInvoke, 
		    spawnableCalls.get(0).getObjectReference().getInstruction());
	    d.log(6, "sync statement inserted\n");
	}
    }
    */


    /* Get the local variable index of the result of the spawnable methode
     * invoke.
     */
    /*
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


    MethodGen getMethodGen(Method method, ClassGen classGen) {
	ConstantPoolGen constantPoolGen = classGen.getConstantPool();
	String className = classGen.getClassName();
	return new MethodGen(method, className, constantPoolGen);
    }
    */


    /* Evaluate the method and rewrite in case spawnableMethod is called. 
    */
    /*
    Method evaluateMethod(Method method, Method spawnableMethod, 
	    ClassGen spawnableClassGen, int indexSync) {
	d.log(4, "evaluating method %s for spawnable calls\n", method);

	MethodGen methodGen = getMethodGen(method, spawnableClassGen);
	MethodGen spawnableMethodGen = 
	    getMethodGen(spawnableMethod, spawnableClassGen);
	ArrayList<SpawnableMethodCall> spawnableCalls = 
	    getSpawnableCalls(methodGen.getInstructionList(), 
		    spawnableClassGen.getConstantPool(), spawnableMethodGen);

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
    */


    /* Rewrite the methods that invoke spawnableMethod.
    */
    /*
    void rewriteForSpawnableMethod(ClassGen spawnableClassGen, 
	    Method spawnableMethod, int indexSync) {

	Method[] methods = spawnableClassGen.getMethods();

	for (Method method : methods) {
	    Method newMethod = evaluateMethod(method, spawnableMethod, 
		    spawnableClassGen, indexSync);
	    spawnableClassGen.removeMethod(method);
	    spawnableClassGen.addMethod(newMethod);
	}
    }
    */


    /*
    JavaClass rewrite(JavaClass spawnableClass) {
	d.log(3, "rewriting spawnable class %s\n", 
	    spawnableClass.getClassName());

	ClassGen spawnableClassGen = new ClassGen(spawnableClass);
	int indexSync = spawnableClassGen.getConstantPool().addMethodref(
		spawnableClass.getClassName(), "sync", "()V");
	Method[] spawnableMethods = getSpawnableMethods(spawnableClass);

	for (Method spawnableMethod : spawnableMethods) {
	    d.log(4, "spawnable method for which %s %s\n", 
		    "methods will be rewritten: ", spawnableMethod);
	    rewriteForSpawnableMethod(spawnableClassGen, spawnableMethod,
		    indexSync);
	    d.log(4, "methods spawnableClassGen for spawnable method %s\n", 
		    spawnableMethod);
	}

	d.log(3, "spawnable class %s spawnableClassGen\n", 
		spawnableClass.getClassName());
	return spawnableClassGen.getJavaClass();
    }
    */


    /*
    boolean isSpawnable(JavaClass javaClass) {
	JavaClass[] interfaces = javaClass.getAllInterfaces();

	for (JavaClass javaInterface : interfaces) {
	    if (javaInterface.getClassName().equals("ibis.satin.Spawnable")) {
		return true;
	    }
	}

	return false;
    }


    String getClassName(String fileName) {
	return fileName.endsWith(".class") ? 
	    fileName.substring(0, fileName.length() - 6) : fileName;
    }
    */


    /* Evaluate a class and rewrite if spawnable.
    */
    /*
    void evaluateClass(JavaClass javaClass) {
	if (javaClass.isClass() && isSpawnable(javaClass)) {
	    //d.log(1, "%s is a spawnable class\n", className);

	    backup(javaClass);
	    JavaClass rewritten = rewrite(javaClass);
	    dump(rewritten);
	}
	else {
	    // nothing
	    //d.log(1, "%s not a spawnable class, not rewritten\n", className);
	}
    }
    */


    /* Evaluate classes and rewrite the spawnable classes.
    */ 
    /*
    void evaluateClasses(ArrayList<String> classOrFileNames) {
	for (String classOrFileName : classOrFileNames) {
	    String className = getClassName(classOrFileName);
	    d.log(0, "evaluating class %s\n", className);

	    evaluateClass(getClassFromName(className));

	    d.log(0, "class %s evaluated\n\n", className);
	}
    }
    */

















    void dump(JavaClass javaClass, String name) {
	try {
	    javaClass.dump(name);
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
    }


    void dump(JavaClass spawnableClass) {
	String fileName = spawnableClass.getClassName() + 
	    (RETAIN_ORIGINAL_CLASSFILES ? "_.class" : ".class");
	dump(spawnableClass, fileName);
	d.log(2, "spawnable class dumped in %s\n", fileName);
    }


    void backup(JavaClass javaClass) {
	if (RETAIN_ORIGINAL_CLASSFILES) {
	    String fileName = javaClass.getClassName() + ".class";
	    d.log(2, "class %s remains in file %s\n", 
		    javaClass.getClassName(), fileName);
	}
	else if (BACKUP_ORIGINAL_CLASSFILES) {
	    String fileName = javaClass.getClassName() + "_.class";
	    dump(javaClass, fileName);
	    d.log(2, "class %s backed up in %s\n", fileName);
	}
    }


    JavaClass getClassFromName(String className) {
	JavaClass javaClass = Repository.lookupClass(className);

	if (javaClass == null) {
	    System.out.println("class " + className + " not found");
	    System.exit(1);
	}

	return javaClass;
    }


    String getClassName(String fileName) {
	return fileName.endsWith(".class") ? 
	    fileName.substring(0, fileName.length() - 6) : fileName;
    }




    void printAnalyzerInfo() {
	System.out.printf("Available analyzers:\n");
	for (String analyzerName : ANALYZER_NAMES) {
	    try {
		analyzer = AnalyzerFactory.createAnalyzer(analyzerName);
		System.out.printf("  %s\n", analyzerName);
	    }
	    catch (ClassNotFoundException e) {
		throw new Error(e);
	    }
	    catch (InstantiationException e) {
		throw new Error(e);
	    }
	    catch (IllegalAccessException e) {
		throw new Error(e);
	    }
	}
    }


    void setAnalyzer(String analyzerName) {
	try {
	    analyzer = AnalyzerFactory.createAnalyzer(analyzerName);
	}
	catch (ClassNotFoundException e) {
	    System.out.printf("Loading analyzer failed: %s\n", e.getMessage());
	    System.exit(1);
	}
	catch (InstantiationException e) {
	    System.out.printf("Loading analyzer failed: %s\n", e.getMessage());
	    System.exit(1);
	}
	catch (IllegalAccessException e) {
	    System.out.printf("Loading analyzer failed: %s\n", e.getMessage());
	    System.exit(1);
	}
    }


    void printUsage() {
	System.out.println("Usage:");
	System.out.println("syncrewriter [options] classname...");
	System.out.println("  example syncrewriter mypackage.MyClass");
	System.out.println("Options:");
	System.out.printf("  -debug               %s\n", 
		"print debug information");
	System.out.printf("  -help                %s\n", 
		"this information");
	System.out.printf("  -analyzerinfo        %s\n", 
		"show info about all analyzers");
	System.out.printf("  -analyzer analyzer   %s\n", 
		"load analyzer analyzer");
    }


    ArrayList<String> processArguments(String[] argv) {
	ArrayList<String> classNames = new ArrayList<String>();

	for (int i = 0; i < argv.length; i++) {
	    String arg = argv[i];

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
	    else if (arg.equals("-analyzer")) {
		if (i + 1 < argv.length) {
		    setAnalyzer(argv[i+1]);
		    i++; // skip the following as argument
		}
		else {
		    System.out.printf("No analyzers specified\n"); 
		    printUsage();
		    System.exit(1);
		}
	    }
	    else if (arg.equals("-analyzerinfo")) {
		printAnalyzerInfo();
		System.exit(0);
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
	ArrayList<String> classFileNames = processArguments(argv);
	if (analyzer == null) setAnalyzer("ControlFlow");

	for (String classFileName : classFileNames) {
	    try {
		String className = getClassName(classFileName);
		JavaClass javaClass = getClassFromName(className);
		SpawnableClass spawnableClass = 
		    new SpawnableClass(javaClass, new Debug(d.turnedOn(), 2));
		d.log(0, "%s is a spawnable class\n", className);
		d.log(1, "backing up spawnable class %s\n", className);
		backup(javaClass);
		d.log(1, "rewriting %s\n", className);
		spawnableClass.rewrite(analyzer);
		d.log(1, "dumping %s\n", className);
		dump(spawnableClass.getJavaClass());
	    }
	    catch (NoSpawnableClassException e) {
		d.log(0, "%s is not a spawnable class\n", getClassName(classFileName));
	    }
	}
    }


    public static void main(String[] argv) {
	new SyncRewriter().start(argv);
    }
}
