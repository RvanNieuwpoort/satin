package ibis.satin.impl.syncrewriter;


import ibis.compile.IbiscComponent;
import ibis.satin.impl.syncrewriter.util.Debug;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;



public class SyncRewriter extends IbiscComponent {


    static final String[] ANALYZER_NAMES = {"Naive", "EarliestLoad", 
	"ControlFlow"};
    static final boolean RETAIN_ORIGINAL_CLASSFILES = true;
    static final boolean BACKUP_ORIGINAL_CLASSFILES = true;


    protected Debug d;
    protected Analyzer analyzer;
    
    protected ArrayList<String> classNames = new ArrayList<String>();

    public SyncRewriter() {
	d = new Debug();
	// d.turnOn();
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
	 ClassRewriteFailure, AssumptionFailure {

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
    
    void rewrite(JavaClass javaClass, SpawnSignature[] spawnSignatures)
            throws NoSpawningClassException, ClassRewriteFailure, AssumptionFailure {
        String className = javaClass.getClassName();
        SpawningClass spawnableClass = 
            new SpawningClass(javaClass, spawnSignatures, new Debug(d.turnedOn(), 2));
        d.log(0, "%s is a spawning class\n", className);
        d.log(1, "it contains calls with spawn signatures:\n");
        print(spawnableClass.getSpawnSignatures(), 2);
        d.log(1, "rewriting %s\n", className);
        if (spawnableClass.rewrite(analyzer)) {
            Repository.removeClass(javaClass);
            javaClass = spawnableClass.getJavaClass();
            Repository.addClass(javaClass);
            setModified(wrapper.getInfo(javaClass));
        }
    }

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
        try {
            JavaClass[] interfaces = javaClass.getAllInterfaces();

            for (JavaClass javaInterface : interfaces) {
                if (javaInterface.getClassName().equals("ibis.satin.Spawnable")) {
                    return true;
                }
            }
            return false;
        } catch(ClassNotFoundException e) {
            throw new Error(e);
        }
    }


    void getSpawnSignatures(JavaClass javaClass, ArrayList<SpawnSignature> spawnSignatures) {
        try {
            JavaClass[] interfaces = javaClass.getInterfaces();
            for (JavaClass javaInterface : interfaces) {
                if (isSpawnable(javaInterface)) {
                    getSpawnSignatures(javaClass.getClassName(), javaInterface.getMethods(), spawnSignatures);
                }
            }
        } catch(ClassNotFoundException e) {
            throw new Error(e);
        }
    }


    JavaClass getClassFromName(String className) {

        try {
            return  Repository.lookupClass(className);
        } catch(ClassNotFoundException e) {
	    System.err.println("class " + className + " not found");
            throw new Error(e);
	}
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


    void processArguments(String[] argv) {

	for (int i = 0; i < argv.length; i++) {
	    String arg = argv[i];

	    if (!arg.startsWith("-")) {
	        addToClassList(arg);
	    } else if (arg.equals("-help")) {
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
    }


    void start(String[] argv) {
	int returnCode = 0;
	processArguments(argv);
	if (analyzer == null) setAnalyzer("ControlFlow");

	d.log(0, "rewriting for following spawnsignatures:\n");
	SpawnSignature[] spawnSignatures = getSpawnSignatures(classNames);
	print(spawnSignatures, 1);

	for (String classFileName : classNames) {
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
	    catch (AssumptionFailure e) {
	        d.error("%s has spawns that don't return a value or throw an inlet."
	                + " The syncrewriter cannot handle this.", className);
	        System.exit(1);
        }
	}
	System.exit(returnCode);
    }

    public String getUsageString() {
        return "[-syncrewriter <classlist>]";
    }


    public void process(Iterator<?> classes) {
        if (classNames.size() == 0) {
            return;
        }
        
        if (analyzer == null) {
            setAnalyzer("ControlFlow");
        }
        
        d.log(0, "rewriting for following spawnsignatures:");
        SpawnSignature[] spawnSignatures = getSpawnSignatures(classNames);
        print(spawnSignatures, 1);
        
        while (classes.hasNext()) {
            JavaClass clazz = (JavaClass) classes.next();
            if (classNames.contains(clazz.getClassName())) {
                try {
                    rewrite(clazz, spawnSignatures);
                }
                catch (NoSpawningClassException e) {
                    d.warning("%s is not a spawning class", clazz.getClassName());
                }
                catch (ClassRewriteFailure e) {
                    d.error("Syncrewriter failed to rewrite %s", clazz.getClassName());
                    System.exit(1);
                }
                catch (AssumptionFailure e) {
                    d.error("%s has spawns that don't return a value or throw an inlet."
                            + " The syncRewriter cannot handle this.", clazz.getClassName());
                    System.exit(1);
                }
            }
        }
        
    }
    
    void addToClassList(String list) {
        StringTokenizer st = new StringTokenizer(list, ", ");
        while (st.hasMoreTokens()) {
            classNames.add(st.nextToken());
        }
    }


    public boolean processArgs(ArrayList<String> args) {

        boolean retval = false;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (false) {
                // nothing
            } else if (arg.equals("-syncrewriter-debug")) {
                d.turnOn();
                args.remove(i--);
            } else if (arg.equals("-syncrewriter-analyzer")) {
                args.remove(i);
                if (i >= args.size()) {
                    throw new IllegalArgumentException("-syncrewriter-analyzer needs analyzer");
                }
                setAnalyzer(args.get(i));
                args.remove(i--);
            } else if (arg.equals("-syncrewriter")) {
                args.remove(i);
                retval = true;
                if (i >= args.size()) {
                    throw new IllegalArgumentException("-syncrewriter needs classlist");
                }
                addToClassList(args.get(i));
                args.remove(i--);
            }
        }
        return retval;
    }


    public String rewriterImpl() {
        return "BCEL";
    }
    

    public static void main(String[] argv) {
        new SyncRewriter().start(argv);
    }
}
