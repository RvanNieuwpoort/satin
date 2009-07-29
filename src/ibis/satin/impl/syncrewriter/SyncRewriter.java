package ibis.satin.impl.syncrewriter;


import ibis.satin.impl.syncrewriter.util.Debug;

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


    void dump(JavaClass spawnableClass) {
	String fileName = spawnableClass.getClassName() + 
	    (RETAIN_ORIGINAL_CLASSFILES ? "_.class" : ".class");
	dump(spawnableClass, fileName);
	d.log(2, "spawnable class dumped in %s\n", fileName);
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


    void rewrite(String className, SpawnSignature[] spawnSignatures) throws NoSpawningClassException, 
	 ClassRewriteFailure {

	JavaClass javaClass = getClassFromName(className);
	SpawningClass spawnableClass = 
	    new SpawningClass(javaClass, spawnSignatures, new Debug(d.turnedOn(), 2));
	d.log(0, "%s is a spawning class\n", className);
	d.log(1, "it contains calls with spawn signatures:\n");
	print(spawnableClass.getSpawnSignatures(), 2);
	d.log(1, "backing up spawning class %s\n", className);
	backup(javaClass);
	d.log(1, "rewriting %s\n", className);
	spawnableClass.rewrite(analyzer);
	d.log(1, "dumping %s\n", className);
	dump(spawnableClass.getJavaClass());
    }


    /*
    void addAll(Method[] methods, ArrayList<Method> methodList) {
	if (methods == null) return;

	for (Method method : methods) {
	    if (!methodList.contains(method)) methodList.add(method);
	}
    }
    */


    void print(SpawnSignature[] spawnSignatures, int level) {
	for (SpawnSignature spawnSignature : spawnSignatures) {
	    d.log(level, "%s\n", spawnSignature);
	}
    }


    void getSpawnSignatures(String className, Method[] interfaceMethods, ArrayList<SpawnSignature> spawnSignatures) {
	for (Method method : interfaceMethods) {
	    SpawnSignature spawnSignature = new SpawnSignature(method, className);
	    if (!spawnSignatures.contains(spawnSignature)) spawnSignatures.add(spawnSignature);
	}
    }


    private boolean isSpawnable(JavaClass javaClass) {
	JavaClass[] interfaces = javaClass.getAllInterfaces();

	for (JavaClass javaInterface : interfaces) {
	    if (javaInterface.getClassName().equals("ibis.satin.Spawnable")) {
		return true;
	    }
	}

	return false;
    }


    void getSpawnSignatures(JavaClass javaClass, ArrayList<SpawnSignature> spawnSignatures) {
	JavaClass[] interfaces = javaClass.getInterfaces();
	for (JavaClass javaInterface : interfaces) {
	    if (isSpawnable(javaInterface)) {
		getSpawnSignatures(javaClass.getClassName(), javaInterface.getMethods(), spawnSignatures);
	    }
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


    SpawnSignature[] getSpawnSignatures(ArrayList<String> classFileNames) {
	ArrayList<SpawnSignature> spawnSignatures = new ArrayList<SpawnSignature>();
	for (String classFileName : classFileNames) {
	    String className = getClassName(classFileName);
	    JavaClass javaClass = getClassFromName(className);
	    if (javaClass.isClass()) getSpawnSignatures(javaClass, spawnSignatures);
	}
	SpawnSignature[] spawnSignaturesArray = new SpawnSignature[spawnSignatures.size()];
	return spawnSignatures.toArray(spawnSignaturesArray);
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
	int returnCode = 0;
	ArrayList<String> classFileNames = processArguments(argv);
	if (analyzer == null) setAnalyzer("ControlFlow");

	d.log(0, "rewriting for following spawnsignatures:\n");
	SpawnSignature[] spawnSignatures = getSpawnSignatures(classFileNames);
	print(spawnSignatures, 1);

	for (String classFileName : classFileNames) {
	    String className = getClassName(classFileName);
	    try {
		rewrite(className, spawnSignatures);
	    }
	    catch (NoSpawningClassException e) {
		d.log(0, "%s is not a spawning class\n", className);
	    }
	    catch (ClassRewriteFailure e) {
		d.error("Failed to rewrite %s\n", className);
		returnCode++;
	    }
	}
	System.exit(returnCode);
    }


    public static void main(String[] argv) {
	new SyncRewriter().start(argv);
    }
}
