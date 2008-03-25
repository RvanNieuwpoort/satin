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
import org.apache.bcel.generic.ClassGen;


class SyncRewriter {


	public static final int NR_CHARS_ON_LINE = 80;

	PrintStream out;

	private boolean debug;


	SyncRewriter() {
		out = System.out;
		debug = /*false*/ true;
	}


	JavaClass getClassFromName(String className) {
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
			printDebug(level + 2, completeMessage.substring(NR_CHARS_ON_LINE, 
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


	void insertSync(InstructionHandle storeInstruction, int index, 
			InstructionHandle objectReferenceLoad, int indexSync, 
			InstructionList instructionList) {

		InstructionHandle ih = storeInstruction;
		while ((ih = ih.getNext()) != null) {
			try {
				LoadInstruction loadInstruction = 
					(LoadInstruction) (ih.getInstruction());
				if (loadInstruction.getIndex() == index) {
					InstructionHandle syncInvocation = 
						instructionList.insert(ih, 
								new INVOKEVIRTUAL(indexSync));

					instructionList.insert(syncInvocation, 
							objectReferenceLoad.getInstruction());

					if (debug) printDebug(6, "inserted a sync statement\n");
				}
			}
			catch (ClassCastException e) {
			}
		}
	}


	void rewriteInstructions(InstructionHandle spawnableMethodInvocation, 
			ConstantPoolGen constantPoolGen, int nrArgumentsSpawnableMethod, 
			int indexSync, InstructionList instructionList) {

		InstructionHandle objectReferenceLoad = 
			getObjectReferenceLoadInstruction(spawnableMethodInvocation, 
					nrArgumentsSpawnableMethod, constantPoolGen);

		InstructionHandle storeInstructionHandle = 
			spawnableMethodInvocation.getNext();

		try {
			StoreInstruction storeInstruction = (StoreInstruction) 
				(storeInstructionHandle.getInstruction());
			int indexResultSpawnableMethod = storeInstruction.getIndex();


			insertSync(storeInstructionHandle, indexResultSpawnableMethod, 
					objectReferenceLoad, indexSync, instructionList);
		}

		catch (ClassCastException e) {
			out.printf("He, hier gaat het niet goed: %s\n", e);
		}
	}


	Method rewriteMethod(Method method, Method spawnableMethod, 
			ClassGen newSpawnableClass, int indexSync) {
		if (debug) {
			printDebug(4, "rewriting %s\n", method);
		}

		ConstantPoolGen constantPoolGen = newSpawnableClass.getConstantPool();

		MethodGen spawnableMethodGen = new MethodGen(spawnableMethod, 
				newSpawnableClass.getClassName(), constantPoolGen);

		int nrArgumentsSpawnableMethod = getNrArguments(spawnableMethodGen);
		int indexSpawnableMethod = 
			constantPoolGen.lookupMethodref(spawnableMethodGen);


		MethodGen methodGen = new MethodGen(method, 
				newSpawnableClass.getClassName(), constantPoolGen);
		INVOKEVIRTUAL spawnableInvocation = 
			new INVOKEVIRTUAL(indexSpawnableMethod);


		InstructionList instructionList = methodGen.getInstructionList();

		InstructionHandle ih = instructionList.getStart();
		do {
			if (ih.getInstruction().equals(spawnableInvocation)) {
				if (debug) printDebug(5, "the spawnable method is invoked\n");
				rewriteInstructions(ih, constantPoolGen, 
						nrArgumentsSpawnableMethod, indexSync, instructionList); 
			}
		} while((ih = ih.getNext()) != null);

		return methodGen.getMethod();
	}


	/** Rewrite the methods that invocate spawnableMethod.
	*/
	void rewriteForSpawnableMethod(Method spawnableMethod, 
			ClassGen newSpawnableClass, int indexSync) {

		Method[] methods = newSpawnableClass.getMethods();

		for (Method method : methods) {
			Method rewrittenMethod = rewriteMethod(method, spawnableMethod, 
					newSpawnableClass, indexSync);
			newSpawnableClass.removeMethod(method);
			newSpawnableClass.addMethod(rewrittenMethod);
		}
	}


	void dumpNewSpawnableClass(ClassGen newSpawnableClass) {
		JavaClass c = newSpawnableClass.getJavaClass();
		//c.setConstantPool
		//	(newSpawnableClass.getConstantPool().getFinalConstantPool());
		dump(c, c.getClassName() + "_.class");
		if (debug) printDebug(3, "spawnable class dumped in %s_.class\n", 
				c.getClassName());
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
		if (debug) printDebug(3, "class %s backed up in %s\n", 
				javaClass.getClassName(), name);
	}


	void rewriteSpawnableClass(JavaClass spawnableClass) {
		if (debug) printDebug(2, 
				"rewriting spawnable class %s\n", 
				spawnableClass.getClassName());

		backup(spawnableClass);

		//CONSTANTPOOL///////////////////////////////////////////////////////// 
		//out.println(spawnableClass.getConstantPool());
		////////////////////////////////////////////////////////////////////////



		ClassGen newSpawnableClass = new ClassGen(spawnableClass);


		ConstantPoolGen constantPoolGen = newSpawnableClass.getConstantPool();
		int indexSync = constantPoolGen.addMethodref(
				spawnableClass.getClassName(), "sync", "()V");

		Method[] spawnableMethods = getSpawnableMethods(spawnableClass);


		for (Method spawnableMethod : spawnableMethods) {
			if (debug) printDebug(3, "spawnable method for which %s %s\n", 
					"methods will be rewritten: ", 
					spawnableMethod);

			rewriteForSpawnableMethod(spawnableMethod, newSpawnableClass, 
					indexSync);
		}

		dumpNewSpawnableClass(newSpawnableClass);
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
