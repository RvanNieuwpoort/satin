package ibis.satin.impl.syncrewriter.util;

import ibis.satin.impl.syncrewriter.analyzer.controlflow.*;

import java.io.PrintStream;
import java.io.File;
import java.io.FileNotFoundException;

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
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.ATHROW;

import org.apache.bcel.verifier.structurals.ControlFlowGraph;
import org.apache.bcel.verifier.structurals.InstructionContext;
import org.apache.bcel.verifier.structurals.ExceptionHandler;


class ControlFlowViewer {


    public static final int NR_CHARS_ON_LINE = 80;


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


    void show(PrintStream out, InstructionContext context, int level) {
	if (level == 20) {
	    return;
	}
	StringBuilder sb = new StringBuilder();
	for (int i = 0; i < level; i++) sb.append(" ");
	sb.append(context);
	out.println(sb);

	InstructionContext[] successors = context.getSuccessors();
	for (InstructionContext successor : successors) {
	    if (successors.length == 0) out.println("HOERA");
	    show(out, successor, successors.length > 1 ? level + 1 : level);
	}
    }



    boolean jumpInstruction(InstructionContext currentContext, 
	    InstructionContext nextContext, InstructionContext[] successors) {
	return successors.length == 1 && !successors[0].equals(nextContext);
    }


    boolean isEndInstruction(InstructionContext context) {
	return context.getInstruction().getInstruction() 
	    instanceof ReturnInstruction ||
	    context.getInstruction().getInstruction() 
	    instanceof ATHROW;
    }

    void printPaths(ArrayList<Path> paths) {
	for (int i = 0; i < paths.size(); i++) {
	    System.out.printf("Path %d\n", i);
	    System.out.println(paths.get(i));
	}
    }


    void showControlFlow4(PrintStream out, JavaClass javaClass, Method method) {
	out.println("Control flow:");
	MethodGen methodGen = new MethodGen(method, javaClass.getClassName(), 
		new ConstantPoolGen(javaClass.getConstantPool()));
	BasicBlockGraph basicBlockGraph = new BasicBlockGraph(methodGen);
	out.println(basicBlockGraph);
	out.println();


	
	/*
	out.println("Ending paths:");
	ArrayList<Path> paths = basicBlockGraph.getEndingPathsFrom(0);
	printPaths(paths);
	*/
    }


    void showClass(PrintStream out, JavaClass javaClass) {
	print(out, 0, "constantpool:\n");
	//out.println(javaClass.getConstantPool());

	Method[] methods = javaClass.getMethods();

	for (Method method : methods) {
	    print(out, 0, "%s\n", method);
	    //if (method.getCode() != null) out.println(method.getCode());
	    //out.println();
	    showControlFlow4(out, javaClass, method);

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



    void showClass(String fileName) {
	String className = getClassName(fileName);
	String outputFileName = className + ".cf";
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
	new ControlFlowViewer().start(argv);
    }
}
