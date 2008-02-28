package ibis.satin.impl.rewriter;


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
import org.apache.bcel.generic.Instruction;


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


	void printDebug(int level, String debugMessage, Object... arguments) {
		out.print("DEBUG: ");
		if (level < 0) throw new Error("printDebug(), level < 0");
		for (int i = 0; i < level; i++) out.print("  ");
		out.printf(debugMessage, arguments);
	}


	Method[] getSpawnableMethods(JavaClass javaClass) {
		Method[] spawnableMethods = null;/*new Method[0];*/

		JavaClass[] interfaces = javaClass.getInterfaces();
		for (JavaClass javaInterface : interfaces) {
			if (isSpawnable(javaInterface)) {
				spawnableMethods = javaInterface.getMethods();
				// it is certain that only one interface is spawnable
			}
		}

		if (spawnableMethods == null) {
			throw new Error("no methods specified in " +
					javaClass.getClassName());
		}

		return spawnableMethods;
	}


	boolean invocates(Method method, Method invocatedMethod, 
			JavaClass javaClass) {

		ConstantPoolGen constantPoolGen = 
			new ConstantPoolGen(javaClass.getConstantPool());
		MethodGen invocatedMethodGen = new MethodGen(invocatedMethod, 
				javaClass.getClassName(), constantPoolGen);
		int indexInvocatedMethod = 
			constantPoolGen.lookupMethodref(invocatedMethodGen);

		MethodGen methodGen = new MethodGen(method, 
				javaClass.getClassName(), constantPoolGen);
		Instruction[] instructionsMethod = 
			methodGen.getInstructionList().getInstructions();

		return contains (instructionsMethod, 
				new INVOKEVIRTUAL(indexInvocatedMethod));
	}


	/*
	 * InstructionList.contains(Instruction) doesn't work...
	 */
	boolean contains(Instruction[] instructions, 
			Instruction searchedInstruction) {
		for (Instruction instruction : instructions) {
			if (instruction.equals(searchedInstruction)) {
				return true;
			}
		}
		return false;
	}


	ArrayList<Method> getInvocatingMethods (Method invocatedMethod, 
		JavaClass javaClass) {

		ArrayList<Method> invocatingMethodsList = new ArrayList<Method>();
		Method[] methods = javaClass.getMethods();

		for (Method method : methods) {
			if (invocates(method, invocatedMethod, javaClass)) {
				invocatingMethodsList.add(method);
			}
		}
		return invocatingMethodsList;
	}


	void rewriteClass(JavaClass javaClass) {
		if (debug) printDebug(0, "rewriting %s\n", javaClass.getClassName());

		/*
		if (debug) printDebug(1, "\n\n\nconstantPool: \n%s\n\n\n", 
				javaClass.getConstantPool());
				*/

		Method[] spawnableMethods = getSpawnableMethods(javaClass);


		for (Method spawnableMethod : spawnableMethods) {
			if (debug) printDebug(1, "spawnableMethod: %s\n", 
					spawnableMethod);

			   ArrayList<Method> invocatingMethods = 
			   getInvocatingMethods(spawnableMethod, javaClass);
			   /*
			Method[] invocatingMethods = javaClass.getMethods();
			*/

			for (Method invocatingMethod : invocatingMethods) {
				if (debug) {
					printDebug(2, "methods that invocate this method: %s\n", 
							invocatingMethod);
			//		printDebug(3, "code: \n%s\n\n", 
			//				invocatingMethod.getCode().toString(true));
				}
			}

			// find invocations of the method
			// find the result
			// find where the result is placed
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


	void evaluateClass(String className) {
		if (debug) printDebug(0, "evaluating class %s\n", className);

		JavaClass javaClass = getClassFromName(className);

		if (javaClass.isClass() && isSpawnable(javaClass)) {
			printDebug(1, "%s is a Spawnable class\n", className);
			rewriteClass(javaClass);
		}
		else if (debug) {
			printDebug(1, "%s not a Spawnable class, not rewritten\n", 
					className);
		}
		else {
			// nothing
		}

		if (debug) {
			out.println();
		}
	}


	void evaluateClasses(ArrayList<String> classNames) {
		for (String className : classNames) {
			evaluateClass(className);
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
		evaluateClasses(classNames);
	}


	public static void main(String[] argv) {
		new SyncRewriter().start(argv);
	}
}
