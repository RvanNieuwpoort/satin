package ibis.satin.impl.syncrewriter.util;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;


class ClassViewer {


    public static final int NR_CHARS_ON_LINE = 80;


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


    void printUsage() {
	System.out.println("Usage:");
	System.out.println("classviewer [options] classname...");
	System.out.println("  example classviewer mypackage.MyClass");
	System.out.println("Options:");
	System.out.println("  -help       this information");
    }


    void print(PrintStream out, int level, String string, Object... arguments) {
	if (level < 0) throw new Error("print(), level < 0");

	StringBuilder sb = new StringBuilder("INFO: ");
	for (int i = 0; i < level; i++) sb.append("  ");
	sb.append(string);

	String completeMessage = String.format(sb.toString(), arguments);
	if (completeMessage.length() > NR_CHARS_ON_LINE) {
	    out.print(completeMessage.substring(0, NR_CHARS_ON_LINE));
	    print(out, level + 1, completeMessage.substring(NR_CHARS_ON_LINE, 
			completeMessage.length()));
	}
	else {
	    out.print(completeMessage);
	}
    }


    void showInstruction(PrintStream out, InstructionHandle ih, ConstantPool cp) {
    }


    void showMethod(PrintStream out, MethodGen methodGen, ConstantPool cp) {
	InstructionList il = methodGen.getInstructionList();
	InstructionHandle[] instructionHandles = il.getInstructionHandles();
	ConstantPoolGen cpg = new ConstantPoolGen(cp);
	int balanceOnStack = 0;
	for (InstructionHandle ih : instructionHandles) {
	    int produced = ih.getInstruction().produceStack(cpg);
	    int consumed = ih.getInstruction().consumeStack(cpg);
	    balanceOnStack = balanceOnStack + produced - consumed;
	    out.printf("%d:\t(c: %d, p: %d, t: %d)\t%s\n", 
		ih.getPosition(), consumed, produced, balanceOnStack,
		ih.getInstruction().toString(cp));
	}
    }


    void showClass(PrintStream out, JavaClass javaClass) {
	print(out, 0, "constantpool:\n");
	out.println(javaClass.getConstantPool());
	ConstantPoolGen cp = new ConstantPoolGen(javaClass.getConstantPool());

	Method[] methods = javaClass.getMethods();

	for (Method method : methods) {
	    MethodGen methodGen = new MethodGen(method, javaClass.getClassName(), cp);
	    print(out, 0, "%s (with stack consumption)\n", method);
	    showMethod(out, methodGen, javaClass.getConstantPool());
	    out.println();

	    print(out, 0, "%s\n", method);
	    if (method.getCode() != null) out.println((method.getCode()).toString(true));
	    out.println();
	}
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


    void showClass(String fileName) {
	String className = getClassName(fileName);
	String outputFileName = className + ".bc";
	try {
	    PrintStream out = new PrintStream(new File(outputFileName));

	    print(out, 0, "showing class %s\n", className);

	    JavaClass javaClass = getClassFromName(className);

	    if (javaClass.isClass() && isSpawnable(javaClass)) {
		print(out, 1, "%s is a spawnable class\n\n", className);
	    }
	    else {
		// nothing
		print(out, 1, "%s not a spawnable class\n\n", className);
	    }
	    showClass(out, javaClass);
	}
	catch (FileNotFoundException e) {
	    System.out.printf("Can't write to file: %s\n", outputFileName);
	    System.exit(1);
	}
	catch (SecurityException e) {
	    System.out.printf("No permission to write file: %s\n", outputFileName);
	    System.exit(1);
	}
    }


    void showClasses(ArrayList<String> classNames) {
	for (String className : classNames) {
	    showClass(className);
	}
    }


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
	showClasses(classNames);
    }


    public static void main(String[] argv) {
	new ClassViewer().start(argv);
    }
}
