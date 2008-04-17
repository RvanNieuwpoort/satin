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


	/*
	   InstructionHandle getObjectReferenceLoadInstruction(InstructionHandle ih, 
	   int nrWords, ConstantPoolGen constantPoolGen) {
	   int count = nrWords + 1;
	   while (count != 0) {
	   ih = ih.getPrev();
	   Instruction instruction = ih.getInstruction();
	   out.println(instruction);
	   if (instruction instanceof StackProducer) {
	   count -= instruction.produceStack(constantPoolGen);
	   }
	   if (instruction instanceof StackConsumer) {
	   count += instruction.consumeStack(constantPoolGen);
	   }
	   }
	   return ih;
	   }
	   */


	/*
	   void insertSync(InstructionList instructionList, 
	   InstructionHandle objectReferenceLoad, 
	   ArrayList<Integer> localVariableIndeces, int indexSync) {
	   if (debug) printDebug(5, "trying to insert a sync statement\n");

	   InstructionHandle ih = instructionList.getStart();
	   do {
	   try {
	   LoadInstruction loadInstruction = 
	   (LoadInstruction) (ih.getInstruction());
	   if (localVariableIndeces.contains(loadInstruction.getIndex())) {
	   InstructionHandle syncInvocation = 
	   instructionList.insert(ih, 
	   new INVOKEVIRTUAL(indexSync));
	   instructionList.insert(syncInvocation, 
	   objectReferenceLoad.getInstruction());
	   if (debug) printDebug(6, "sync statement inserted\n");
	   return;
	   }
	   }
	   catch (ClassCastException e) {
	   }
	   }
	   while ((ih = ih.getNext()) != null);
	   }
	   */


	InstructionHandle getLoadInstruction(InstructionList il, 
			SpawnableMethodCall spawnableCall) {

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
		throw new Error("Result of spawnable method never read\n");
	}


	void insertSync(InstructionList instructionList, 
			ArrayList<SpawnableMethodCall> spawnableCalls, int indexSync) {

		if (debug) printDebug(5, "trying to insert a sync statement\n");

		InstructionHandle earliestLoadInstruction = null;
		for (SpawnableMethodCall spawnableCall : spawnableCalls) {
			InstructionHandle loadInstruction = 
				getLoadInstruction(instructionList, spawnableCall);
			if (earliestLoadInstruction == null || 
					loadInstruction.getPosition() < 
					earliestLoadInstruction.getPosition()) {
				earliestLoadInstruction = loadInstruction;
					}
		}

		InstructionHandle syncInvocation = 
			instructionList.insert(earliestLoadInstruction, 
					new INVOKEVIRTUAL(indexSync));
		instructionList.insert(syncInvocation, 
				spawnableCalls.get(0).objectReference.getInstruction());
		if (debug) printDebug(6, "sync statement inserted\n");
	}





	/*
	   void insertSync(InstructionHandle storeInstruction, 
	   InstructionList instructionList, int indexLocalVariable, 
	   InstructionHandle objectReferenceLoad, int indexSync) {

	   InstructionHandle ih = storeInstruction;
	   while ((ih = ih.getNext()) != null) {
	   try {
	   LoadInstruction loadInstruction = 
	   (LoadInstruction) (ih.getInstruction());
	   if (loadInstruction.getIndex() == indexLocalVariable) {
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
	   InstructionList instructionList, ConstantPoolGen constantPoolGen, 
	   int nrWordsSpawnableMethod, int indexSync) {

	   InstructionHandle objectReferenceLoad = 
	   getObjectReferenceLoadInstruction(spawnableMethodInvocation, 
	   nrWordsSpawnableMethod, constantPoolGen);

	   InstructionHandle storeInstructionHandle = 
	   spawnableMethodInvocation.getNext();

	   try {
	   StoreInstruction storeInstruction = (StoreInstruction) 
	   (storeInstructionHandle.getInstruction());
	   int indexLocalVariable = storeInstruction.getIndex();


	   insertSync(storeInstructionHandle, instructionList, 
	   indexLocalVariable, objectReferenceLoad, indexSync);
	   }

	   catch (ClassCastException e) {
	   out.printf("He, hier gaat het niet goed: %s\n", e);
	   }
	   }
	   */


	int getLocalVariableIndexResult(InstructionHandle spawnableMethodInvocation,
			InstructionList instructionList, ConstantPoolGen constantPoolGen) {

		InstructionHandle storeInstructionHandle = 
			spawnableMethodInvocation.getNext();

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
		int nrWordsSpawnableMethod = getNrWordsArguments(spawnableMethodGen);
		int indexSpawnableMethod = 
			constantPoolGen.lookupMethodref(spawnableMethodGen);
		INVOKEVIRTUAL spawnableInvocation = 
			new INVOKEVIRTUAL(indexSpawnableMethod);



		ArrayList<SpawnableMethodCall> spawnableCalls = 
			new ArrayList<SpawnableMethodCall>();

		InstructionList instructionList = methodGen.getInstructionList();
		InstructionHandle ih = instructionList.getStart();
		do {
			if (ih.getInstruction().equals(spawnableInvocation)) {
				if (debug) printDebug(5, 
						"the spawnable method is invoked, rewriting\n");

				SpawnableMethodCall spawnableCall = new SpawnableMethodCall(ih, 
						getObjectReferenceLoadInstruction(ih, constantPoolGen), 
						getLocalVariableIndexResult(ih, instructionList, 
							constantPoolGen));
				spawnableCalls.add(spawnableCall);
				/*
				   objectReferenceLoad = getObjectReferenceLoadInstruction(ih, 
				   constantPoolGen);
				   localVariableIndeces.add(getLocalVariableIndexResult(ih, 
				   instructionList, constantPoolGen));
				   */
			}
		} while((ih = ih.getNext()) != null);

		if (spawnableCalls.size() > 0) {
			insertSync(instructionList, spawnableCalls, indexSync);
		}
		/*
		   else {
		   if (debug) printDebug(5, "nothing to be done\n");
		   }

		   if (debug) {
		   printDebug(4, "method %s evaluated\n", method);
		   }
		   */

		return methodGen.getMethod();
	}



	/*
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
	   int nrWordsSpawnableMethod = getNrWordsArguments(spawnableMethodGen);
	   int indexSpawnableMethod = 
	   constantPoolGen.lookupMethodref(spawnableMethodGen);
	   INVOKEVIRTUAL spawnableInvocation = 
	   new INVOKEVIRTUAL(indexSpawnableMethod);



	   ArrayList<Integer> localVariableIndeces = new ArrayList<Integer>();
	   InstructionHandle objectReferenceLoad = null;

	   InstructionList instructionList = methodGen.getInstructionList();
	   InstructionHandle ih = instructionList.getStart();
	   do {
	   if (ih.getInstruction().equals(spawnableInvocation)) {
	   if (debug) printDebug(5, 
	   "the spawnable method is invoked, rewriting\n");

	   objectReferenceLoad = getObjectReferenceLoadInstruction(ih, 
	   constantPoolGen);
	   localVariableIndeces.add(getLocalVariableIndexResult(ih, 
	   instructionList, constantPoolGen));
	   }
	   } while((ih = ih.getNext()) != null);

	   if (localVariableIndeces.size() > 0) {
	   insertSync(instructionList, objectReferenceLoad, 
	   localVariableIndeces, indexSync);
	   }
	/*
	else {
	if (debug) printDebug(5, "nothing to be done\n");
	}

	if (debug) {
	printDebug(4, "method %s evaluated\n", method);
	}
	*/
	/*
	   return methodGen.getMethod();
	   }
	   */


	/** Rewrite the methods that invocate spawnableMethod.
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
			if (debug) printDebug(3, "methods rewritten for spawnable method %s\n", 
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
