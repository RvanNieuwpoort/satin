package ibis.satin.impl.syncrewriter.util;


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


class ClassViewer {


    public static final int NR_CHARS_ON_LINE = 80;



    ClassViewer() {
    }


    /*
       private JavaClass getClassFromName(String className) {
       JavaClass javaClass = Repository.lookupClass(className);

       if (javaClass == null) {
       System.out.println("class " + className + " not found");
       System.exit(1);
       }

       return javaClass;
       }



       Method[] getSpawnableMethods(JavaClass spawnableClass) {
       Method[] spawnableMethods = null;/*new Method[0];*/

/*
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


    void evaluateMethods(Method spawnableMethod, 
	    JavaClass spawnableClass) {


	Method[] methods = spawnableClass.getMethods();
	ConstantPoolGen constantPoolGen = 
	    new ConstantPoolGen(spawnableClass.getConstantPool());

	for (Method method : methods) {
	    if (debug) {
		print(3, "evaluating %s\n", method);
	    }

	    evaluateMethod(method, spawnableMethod, spawnableClass, 
		    constantPoolGen);
	}
    }
    */
	/*
	private JavaClass getClassFromName(String fileName) {
	    String className = fileName.endsWith(".class") ? 
		fileName.substring(0, fileName.length() - 6) : fileName;
	    JavaClass javaClass = Repository.lookupClass(className);

	    if (javaClass == null) {
		System.out.println("class " + className + " not found");
		System.exit(1);
	    }

	    return javaClass;
	}
	*/


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


    void showClass(PrintStream out, JavaClass javaClass) {
	print(out, 0, "constantpool:\n");
	out.println(javaClass.getConstantPool());

	Method[] methods = javaClass.getMethods();

	for (Method method : methods) {
	    print(out, 0, "%s\n", method);
	    if (method.getCode() != null) out.println(method.getCode());
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
