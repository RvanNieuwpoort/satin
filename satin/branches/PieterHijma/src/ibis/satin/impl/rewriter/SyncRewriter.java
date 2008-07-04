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
import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.DASTORE;
import org.apache.bcel.generic.ALOAD;


class SyncRewriter {


	public static final int NR_CHARS_ON_LINE = 80;

	PrintStream out;

	private boolean debug;


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
	}


	SyncRewriter() {
		out = System.out;
		debug = /*false*/ true;
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


	int getNrWordsArguments(MethodGen methodGen) {
		int nrWords = 0;
		for (Type type : methodGen.getArgumentTypes()) {
			nrWords += type.getSize();
		}
		return nrWords;
	}


	InstructionHandle getArrayReferenceLoadInstruction(InstructionHandle ih,
			int nrWords, ConstantPoolGen constantPoolGen) {
		int count = 10;
		while (count != 0) {
			ih = ih.getPrev();
			Instruction instruction = ih.getInstruction();
			out.println("instructie " + instruction);
			if (instruction instanceof StackProducer) {
				out.printf("produceert %d op de stack\n", 
						instruction.produceStack(constantPoolGen));
			}
			if (instruction instanceof StackConsumer) {
				out.printf("consumeert %d op de stack\n", 
						instruction.consumeStack(constantPoolGen));
			}
			out.println();
			count--;
		}
		return ih;
	}


	/* Get the corresponding object reference load instruction of instruction
	 * ih.
	 */
	InstructionHandle getObjectReferenceLoadInstruction(InstructionHandle ih, 
			ConstantPoolGen constantPoolGen) {
		Instruction instruction = ih.getInstruction();
		int stackConsumation = instruction.consumeStack(constantPoolGen);
		//stackConsumation--; // we're interested in the first one

		while (stackConsumation != 0) {
			ih = ih.getPrev();
			Instruction previousInstruction = ih.getInstruction();
			//out.println(instruction);
			//if (instruction instanceof StackProducer) {
			stackConsumation -= 
				previousInstruction.produceStack(constantPoolGen);
			//}
			//if (instruction instanceof StackConsumer) {
			stackConsumation += 
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


	void insertSync(InstructionList instructionList, 
			ArrayList<SpawnableMethodCall> spawnableCalls, int indexSync) 
		throws NeverReadException {

		if (debug) printDebug(5, "trying to insert a sync statement\n");

		InstructionHandle earliestLoadInstruction = 
			getEarliestLoadInstruction(instructionList, spawnableCalls);

		InstructionHandle syncInvoke = 
			instructionList.insert(earliestLoadInstruction, 
					new INVOKEVIRTUAL(indexSync));
		instructionList.insert(syncInvoke, 
				spawnableCalls.get(0).objectReference.getInstruction());
		if (debug) printDebug(6, "sync statement inserted\n");
	}


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
				if (debug) printDebug(5, 
						"the spawnable method is invoked, rewriting\n");

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
		if (debug) {
			printDebug(4, "evaluating method %s\n", method);
		}

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
				insertSync(instructionList, spawnableCalls, indexSync);
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
		//if (debug) printDebug(3, "class %s backed up in %s\n", 
		if (debug) printDebug(3, "class %s still in %s\n", 
				javaClass.getClassName(), name);
	}


	void rewriteSpawnableClass(JavaClass spawnableClass) {
		if (debug) printDebug(2, "rewriting spawnable class %s\n", 
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
			if (debug) printDebug(3, "spawnable method for which %s %s\n", 
					"methods will be rewritten: ", 
					spawnableMethod);
			rewriteForSpawnableMethod(newSpawnableClass, spawnableMethod,
					indexSync);
			if (debug) printDebug(3, 
					"methods rewritten for spawnable method %s\n", 
					spawnableMethod);
		}

		dumpNewSpawnableClass(newSpawnableClass);
		if (debug) printDebug(2, 
				"spawnable class %s rewritten\n", 
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
		if (debug) printDebug(0, "evaluating class %s\n", className);

		JavaClass javaClass = getClassFromName(className);

		if (javaClass.isClass() && isSpawnable(javaClass)) {
			if (debug) printDebug(1, "%s is a spawnable class\n", className);
			rewriteSpawnableClass(javaClass);
		}
		else {
			// nothing
			if (debug) printDebug(1, 
					"%s not a spawnable class, not rewritten\n", className);
		}
		if (debug) printDebug(0, "class %s evaluated\n\n", className);
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
