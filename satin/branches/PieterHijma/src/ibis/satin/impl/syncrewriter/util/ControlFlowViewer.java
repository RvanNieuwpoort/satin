package ibis.satin.impl.syncrewriter.util;

import ibis.satin.impl.syncrewriter.analyzer.controlflow.*;

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
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.ATHROW;

import org.apache.bcel.verifier.structurals.ControlFlowGraph;
import org.apache.bcel.verifier.structurals.InstructionContext;
import org.apache.bcel.verifier.structurals.ExceptionHandler;


class ControlFlowViewer {


    public static final int NR_CHARS_ON_LINE = 80;

    PrintStream out;


    ControlFlowViewer() {
	out = System.out;
    }


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


    void printUsage() {
	out.println("Usage:");
	out.println("classviewer [options] classname...");
	out.println("  example classviewer mypackage.MyClass");
	out.println("Options:");
	out.println("  -help       this information");
    }


    void print(int level, String string, Object... arguments) {
	if (level < 0) throw new Error("print(), level < 0");

	StringBuilder sb = new StringBuilder("INFO: ");
	for (int i = 0; i < level; i++) sb.append("  ");
	sb.append(string);

	String completeMessage = String.format(sb.toString(), arguments);
	if (completeMessage.length() > NR_CHARS_ON_LINE) {
	    out.print(completeMessage.substring(0, NR_CHARS_ON_LINE));
	    print(level + 1, completeMessage.substring(NR_CHARS_ON_LINE, 
			completeMessage.length()));
	}
	else {
	    out.print(completeMessage);
	}
    }


    void show(InstructionContext context, int level) {
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
	    show(successor, successors.length > 1 ? level + 1 : level);
	}
    }


    /*
    CodeBlock addCodeBlock(Path codeBlocks, CodeBlock current,
	    InstructionContext end) {
	current.setEnd(end);
	codeBlocks.add(current);
	return null;
    }
    */


    /*
    CodeBlock newCurrentCodeBlock(CodeBlock current, InstructionContext begin, 
	    int level) {
	if (current == null) {
	    return new CodeBlock(begin, level);
	}
	else {
	    throw new Error("unfinished CodeBlock");
	}
    }
    */


    /*
    void setLevel(ArrayList<Target> targets, InstructionContext context, 
	    int level) {
	int index = targets.indexOf(new Target(context, -1));
	Target target = targets.remove(index);
	target.setLevel(level);
	targets.add(target);
    }
    */


    /*
    int getLevel(ArrayList<Target> targets, InstructionContext context, 
	    int level) {
	int index = targets.indexOf(new Target(context, -1));
	Target target = targets.remove(index);
	targets.add(target);
	if (target.getLevel() == -1) return level+1;
	else return target.getLevel();
    }
    */


    boolean jumpInstruction(InstructionContext currentContext, 
	    InstructionContext nextContext, InstructionContext[] successors) {
	return successors.length == 1 && !successors[0].equals(nextContext);
    }


    /*
    ArrayList<Target> getTargets(InstructionContext[] contexts, 
	    ControlFlowGraph graph) {
	ArrayList<Target> targets = 
	    new ArrayList<Target>();

	for (int i = 0; i < contexts.length; i++) {
	    InstructionContext currentContext = contexts[i];
	    InstructionContext nextContext = i < contexts.length - 1 ? 
		contexts[i+1] :
		null;
	    InstructionContext[] successors = currentContext.getSuccessors();

	    if (jumpInstruction(currentContext, nextContext, successors)) {
		targets.add(new Target(successors[0], -1));
	    }
	    else if (successors.length > 1) {
		for (InstructionContext successor : successors) {
		    targets.add(new Target(successor, -1));
		}
	    }
	    ExceptionHandler[] handlers = currentContext.getExceptionHandlers();
	    for (ExceptionHandler handler : handlers) {
		InstructionContext handlerContext = graph.contextOf(
			handler.getHandlerStart());
		Target target = new Target(handlerContext, -1);
		if (!targets.contains(target)) {
		    targets.add(target);
		}
	    }
	}

	return targets;
    }
    */


    /*
    boolean isTarget(InstructionContext context, ArrayList<Target> targets) {
	return targets.contains(new Target(context, -1));
    }
    */


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


    void showControlFlow4(JavaClass javaClass, Method method) {
	out.println("Control flow:");
	MethodGen methodGen = new MethodGen(method, javaClass.getClassName(), 
		new ConstantPoolGen(javaClass.getConstantPool()));
	CodeBlockGraph codeBlockGraph = new CodeBlockGraph(methodGen);
	out.println(codeBlockGraph);
	out.println();


	
	out.println("Ending paths:");
	ArrayList<Path> paths = codeBlockGraph.getEndingPathsFrom(0);
	printPaths(paths);
    }


    /*
    void showControlFlow3(JavaClass javaClass, Method method) {
	out.println("Control flow:");
	MethodGen methodGen = new MethodGen(method, javaClass.getClassName(), 
		new ConstantPoolGen(javaClass.getConstantPool()));
	ControlFlowGraph graph = new ControlFlowGraph(methodGen);


	InstructionList il = methodGen.getInstructionList();
	InstructionHandle[] instructionHandles = il.getInstructionHandles();
	InstructionContext[] contexts = graph.contextsOf(instructionHandles);


	Path codeBlocks = 
	    new Path();

	CodeBlock current = null;


	ArrayList<Target> targets = getTargets(contexts, graph);


	int level = 0;
	for (int i = 0; i < contexts.length; i++) {
	    int index;
	    InstructionContext currentContext = contexts[i];
	    InstructionContext nextContext = i < contexts.length - 1 ? 
		contexts[i+1] :
		null;

	    // beginning of codeblock
	    if (i == 0) { // begin method
		out.printf("BEGIN CODEBLOCK, level: %d\n", level);
		current = newCurrentCodeBlock(current, currentContext, level);
	    }
	    else if (targets.contains(new Target(currentContext, -1))) { // is target
		level = getLevel(targets, currentContext, level);
		out.printf("BEGIN CODEBLOCK, level: %d\n", level);
		current = newCurrentCodeBlock(current, currentContext, level);
	    }


	    out.println(currentContext);


	    // end of codeblock
	    InstructionContext[] successors = currentContext.getSuccessors();
	    if (jumpInstruction(currentContext, nextContext, successors)) {
		out.println("\tTARGETS:");
		out.println("\t  " + successors[0]);
		current = addCodeBlock(codeBlocks, current, currentContext);
		out.println("END CODEBLOCK");
		setLevel(targets, successors[0], level--);
	    }
	    else if (successors.length > 1) {
		level++;
		for (InstructionContext successor : successors) {
		    out.println("\tTARGETS:");
		    out.println("\t  " + successor);
		    setLevel(targets, successor, level);
		}
		current = addCodeBlock(codeBlocks, current, currentContext);
		out.println("END CODEBLOCK");
	    }
	    else if (isTarget(nextContext, targets)) {
		current = addCodeBlock(codeBlocks, current, currentContext);
		out.println("END CODEBLOCK");
		setLevel(targets, nextContext, --level);
	    }
	    else if (isEndInstruction(currentContext)) {		
		current = addCodeBlock(codeBlocks, current, currentContext);
		if (targets.contains(new Target(nextContext, -1))) {
		    setLevel(targets, nextContext, --level);
		}
		out.println("END CODEBLOCK\n");
	    }
	}
	out.println();

    }
	   */


    /*
    void showControlFlow2(JavaClass javaClass, Method method) {
	out.println("Control flow:");
	MethodGen methodGen = new MethodGen(method, javaClass.getClassName(), 
		new ConstantPoolGen(javaClass.getConstantPool()));
	ControlFlowGraph graph = new ControlFlowGraph(methodGen);


	InstructionList il = methodGen.getInstructionList();
	InstructionHandle[] instructionHandles = il.getInstructionHandles();
	InstructionContext[] contexts = graph.contextsOf(instructionHandles);

	ArrayList<Target> targets = 
	    new ArrayList<Target>();

	Path codeBlocks = 
	    new Path();

	CodeBlock current = null;


	for (int i = 0; i < contexts.length; i++) {
	    InstructionContext context = contexts[i];
	    InstructionContext next = i < contexts.length - 1 ? 
		contexts[i+1] :
		null;
	    InstructionContext[] successors = context.getSuccessors();

	    if (successors.length == 1 && !successors[0].equals(next)) {
		targets.add(new Target(successors[0], -1));
	    }
	    else if (successors.length > 1) {
		for (InstructionContext successor : successors) {
		    targets.add(new Target(successor, -1));
		}
	    }
	    ExceptionHandler[] handlers = context.getExceptionHandlers();
	    for (ExceptionHandler handler : handlers) {
		InstructionContext handlerContext = graph.contextOf(
			handler.getHandlerStart());
		Target target = new Target(handlerContext, -1);
		if (!targets.contains(target)) {
		    targets.add(target);
		}
	    }
	}


	int level = 0;
	for (int i = 0; i < contexts.length; i++) {
	    int index;
	    InstructionContext context = contexts[i];
	    InstructionContext next = i < contexts.length - 1 ? 
		contexts[i+1] :
		null;

	    // beginning of codeblock
	    if (i == 0) { // begin method
		out.printf("BEGIN CODEBLOCK, level: %d\n", level);
		current = newCurrentCodeBlock(current, context, level);
	    }
	    else if (targets.contains(new Target(context, -1))) { // is target
		level = getLevel(targets, context, level);
		out.printf("BEGIN CODEBLOCK, level: %d\n", level);
		current = newCurrentCodeBlock(current, context, level);
	    }


	    out.println(context);


	    // end of codeblock
	    InstructionContext[] successors = context.getSuccessors();
	    if (successors.length == 1 && !successors[0].equals(next)) {
		out.println("\tTARGETS:");
		out.println("\t  " + successors[0]);
		current = addCodeBlock(codeBlocks, current, context);
		out.println("END CODEBLOCK");
		setLevel(targets, successors[0], level);
	    }
	    else if (successors.length == 1 && 
		    (index = targets.indexOf(new Target(successors[0], -1))) 
		    != -1) {
		current = addCodeBlock(codeBlocks, current, context);
		out.println("END CODEBLOCK");
		setLevel(targets, successors[0], --level);
		    }
	    else if (context.getInstruction().getInstruction() instanceof 
		    ReturnInstruction ||
		    context.getInstruction().getInstruction() instanceof
		    ATHROW) {
		current = addCodeBlock(codeBlocks, current, context);
		if (targets.contains(new Target(next, -1))) {
		    setLevel(targets, next, --level);
		}
		out.println("END CODEBLOCK\n");
		    }
	    else if (successors.length > 1) {
		level++;
		for (InstructionContext successor : successors) {
		    out.println("\tTARGETS:");
		    out.println("\t  " + successor);
		    setLevel(targets, successor, level);
		}
		current = addCodeBlock(codeBlocks, current, context);
		out.println("END CODEBLOCK");
	    }
	}
	out.println();
    }
	   */


    /*
    void showControlFlow(JavaClass javaClass, Method method) {
	out.println("Control flow:");
	MethodGen methodGen = new MethodGen(method, javaClass.getClassName(), 
		new ConstantPoolGen(javaClass.getConstantPool()));
	ControlFlowGraph graph = new ControlFlowGraph(methodGen);


	InstructionList il = methodGen.getInstructionList();
	InstructionHandle[] instructionHandles = il.getInstructionHandles();
	InstructionContext[] contexts = graph.contextsOf(instructionHandles);

	ArrayList<Target> targets = 
	    new ArrayList<Target>();

	Path codeBlocks = 
	    new Path();

	CodeBlock current = null;


	for (int i = 0; i < contexts.length;i++) {
	    InstructionContext context = contexts[i];
	    InstructionContext next = i < contexts.length - 1 ? 
		contexts[i+1] :
		null;
	    if (i == 0) {
		out.println("BEGIN CODEBLOCK, level: 0");
		current = newCurrentCodeBlock(current, context, 0);
	    }

	    int index;

	    if ((index = targets.indexOf(new Target(context, 0))) != -1) {
		Target target = targets.remove(index);
		out.printf("BEGIN CODEBLOCK, level: %d\n", target.getLevel());
		current = 
		    newCurrentCodeBlock(current, context, target.getLevel());
	    }

	    out.println(context);


	    InstructionContext[] successors = context.getSuccessors();
	    if (successors.length == 1 && !successors[0].equals(next)) {
		out.println("\tTARGETS:");
		out.println("\t  " + successors[0]);
		targets.add(new Target(successors[0], current.getLevel()));
		current = addCodeBlock(codeBlocks, current, context);
		out.println("END CODEBLOCK");
	    }
	    else if ((successors.length == 1 && 
			(index = targets.indexOf(new Target(successors[0], 0))) 
			!= -1)) {
		Target target = targets.remove(index);
		target.diminishLevel();
		targets.add(target);
		out.println("END CODEBLOCK");
		current = addCodeBlock(codeBlocks, current, context);
			}
	    else if (context.getInstruction().getInstruction() instanceof 
		    ReturnInstruction ||
		    context.getInstruction().getInstruction() instanceof
		    ATHROW) {
		out.println("END CODEBLOCK");
		current = addCodeBlock(codeBlocks, current, context);
		    }
	    else if (successors.length > 1) {
		for (InstructionContext successor : successors) {
		    out.println("\tTARGETS:");
		    out.println("\t  " + successor);
		    targets.add(new Target(successor, current.getLevel() + 1));
		}
		out.println("END CODEBLOCK");
		current = addCodeBlock(codeBlocks, current, context);
	    }

	}
	out.println();

	for(CodeBlock codeBlock : codeBlocks) {
	    out.println(codeBlock);
	}
    }
    */


    void showClass(JavaClass javaClass) {
	print(0, "constantpool:\n");
	//out.println(javaClass.getConstantPool());

	Method[] methods = javaClass.getMethods();

	for (Method method : methods) {
	    print(0, "%s\n", method);
	    //if (method.getCode() != null) out.println(method.getCode());
	    //out.println();
	    showControlFlow4(javaClass, method);

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


    void showClass(String className) {
	print(0, "showing class %s\n", className);

	JavaClass javaClass = getClassFromName(className);

	if (javaClass.isClass() && isSpawnable(javaClass)) {
	    print(1, "%s is a spawnable class\n\n", className);
	}
	else {
	    // nothing
	    print(1, "%s not a spawnable class\n\n", className);
	}
	showClass(javaClass);
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
