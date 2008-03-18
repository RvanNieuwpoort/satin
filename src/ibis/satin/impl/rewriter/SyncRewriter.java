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
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.StackConsumer;
import org.apache.bcel.generic.StackProducer;


class SyncRewriter {


	public static final int NR_CHARS_ON_LINE = 80;

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
		if (level < 0) throw new Error("printDebug(), level < 0");

		StringBuilder sb = new StringBuilder("DEBUG: ");
		for (int i = 0; i < level; i++) sb.append("  ");
		sb.append(debugMessage);

		String completeMessage = String.format(sb.toString(), arguments);
		if (completeMessage.length() > NR_CHARS_ON_LINE) {
			out.print(completeMessage.substring(0, NR_CHARS_ON_LINE));
			printDebug(level + 1, completeMessage.substring(NR_CHARS_ON_LINE, 
						completeMessage.length()));
		}
		else {
			out.print(completeMessage);
		}
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


	int getIndexMethod(Method method, JavaClass javaClass, 
			ConstantPoolGen constantPoolGen) {
		MethodGen methodGen = new MethodGen(method, javaClass.getClassName(), 
				constantPoolGen);
		return constantPoolGen.lookupMethodref(methodGen);
	}

	int getNrArguments(MethodGen methodGen) {
		return methodGen.getArgumentTypes().length;
	}


	InstructionHandle getObjectReferenceLoadInstruction(InstructionHandle ih, 
			int nrArguments, ConstantPoolGen constantPoolGen) {
		int count = nrArguments + 1;
		while (count != 0) {
			ih = ih.getPrev();
			Instruction instruction = ih.getInstruction();
			//	out.println(instruction);
			if (instruction instanceof StackProducer) {
				count -= instruction.produceStack(constantPoolGen);
			}
			if (instruction instanceof StackConsumer) {
				count += instruction.consumeStack(constantPoolGen);
			}
		}
		return ih;
	}

	/* *************************************************************************
	 * De problemen:
	 *
	 * de sync moet worden uitgevoerd met dezelfde parameter als de spawnable
	 * method call
	 *
	 * dit lijkt de aload te doen: wat doet deze precies?
	 *
	 * Het resultaat wordt opgeslagen, waar wordt het teruggehaald. De store
	 * gebeurt precies na de invokevirtual
	 */

	void evaluateMethod(Method method, Method spawnableMethod, 
			JavaClass spawnableClass, ConstantPoolGen constantPoolGen) {

		out.println(method.getCode());
		//		out.println(constantPoolGen);

		MethodGen spawnableMethodGen = new MethodGen(spawnableMethod, 
				spawnableClass.getClassName(), constantPoolGen);
		int nrArgumentsSpawnableMethod = getNrArguments(spawnableMethodGen);
		int indexSpawnableMethod = 
			constantPoolGen.lookupMethodref(spawnableMethodGen);




		MethodGen methodGen = new MethodGen(method, 
				spawnableClass.getClassName(), constantPoolGen);

		InstructionList instructionList = methodGen.getInstructionList();

		InstructionHandle ih = instructionList.getStart();
		do {
			if (ih.getInstruction().equals
					(new INVOKEVIRTUAL(indexSpawnableMethod))) {
				InstructionHandle objectReferenceLoad = 
					getObjectReferenceLoadInstruction(
							ih, nrArgumentsSpawnableMethod, constantPoolGen);

				ih = ih.getNext();
				try {
					StoreInstruction storeInstruction = 
						(StoreInstruction) (ih.getInstruction());
					int index = storeInstruction.getIndex();

					InstructionHandle ih2 = ih;
					while ((ih2 = ih2.getNext()) != null) {
						try {
							LoadInstruction loadInstruction = 
								(LoadInstruction) (ih2.getInstruction());
							if (loadInstruction.getIndex() == index) {
								out.println(ih2);
							}
						}
						catch (ClassCastException e) {
						}
					}






				}
				catch (ClassCastException e) {
					out.printf("He, hier gaat het niet goed: %s\n", e);
				}



					}

		} while((ih = ih.getNext()) != null);
	}


	/** Rewrite the methods that invocate spawnableMethod.
	*/
	void evaluateMethods(Method spawnableMethod, 
			JavaClass spawnableClass) {

		/*
		   ArrayList<Method> invocatingMethods = 
		   getInvocatingMethods(spawnableMethod, spawnableClass);
		   */

		Method[] methods = spawnableClass.getMethods();
		ConstantPoolGen constantPoolGen = 
			new ConstantPoolGen(spawnableClass.getConstantPool());

		for (Method method : methods) {
			if (debug) {
				printDebug(3, "evaluating %s\n", method);
			}

			evaluateMethod(method, spawnableMethod, spawnableClass, 
					constantPoolGen);
		}
	}


	void rewriteSpawnableClass(JavaClass spawnableClass) {
		if (debug) printDebug(0, 
				"rewriting spawnable class %s\n", 
				spawnableClass.getClassName());

		Method[] spawnableMethods = getSpawnableMethods(spawnableClass);

		//out.println(spawnableClass.getConstantPool());

		for (Method spawnableMethod : spawnableMethods) {
			if (debug) printDebug(1, "spawnable method: %s\n", 
					spawnableMethod);

			evaluateMethods(spawnableMethod, spawnableClass);
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
			if (debug) printDebug(1, "%s is a spawnable class\n", className);
			rewriteSpawnableClass(javaClass);
			if (debug) out.println();
		}
		else {
			// nothing
			if (debug) printDebug(1, 
					"%s not a spawnable class, not rewritten\n\n", className);
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
