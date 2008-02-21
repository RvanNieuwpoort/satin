package ibis.satin.impl.rewriter;


import java.io.PrintStream;

import java.util.ArrayList;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.Repository;


class SyncRewriter {


	PrintStream out;

	private boolean debug;


	SyncRewriter() {
		out = System.out;
		debug = /*false*/ true;
	}


	private JavaClass getClassFromName(String className) {
		JavaClass javaClass = Repository.lookupClass(className);

		if (javaClass == null) {
			System.out.println("class " + className + " not found");
			System.exit(1);
		}

		return javaClass;
	}


	void printUsage() {
		out.println("Usage:");
		out.println("syncrewriter [options] classname...");
		out.println("  example syncrewriter mypackage.MyClass");
		out.println("Options:");
		out.println("  -debug      print debug information");
		out.println("  -help       this information");
	}


	void printDebug(String debugMessage, Object... arguments) {
		out.print("DEBUG: ");
		out.printf(debugMessage, arguments);
	}


	void rewriteClass(String className) {
		if (debug) printDebug("rewriting class %s\n", className);
		out.println(getClassFromName(className));
	}


	void rewriteClasses(ArrayList<String> classNames) {
		for (String className : classNames) {
			rewriteClass(className);
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
			else if (arg.equals("-debug")) {
				debug = true;
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
		rewriteClasses(classNames);
	}


	public static void main(String[] argv) {
		new SyncRewriter().start(argv);
	}
}
