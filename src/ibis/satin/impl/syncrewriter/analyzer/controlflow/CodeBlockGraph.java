package ibis.satin.impl.syncrewriter.analyzer.controlflow;

import java.util.ArrayList;

import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.ATHROW;
import org.apache.bcel.classfile.ConstantPool;

import org.apache.bcel.verifier.structurals.ControlFlowGraph;
import org.apache.bcel.verifier.structurals.InstructionContext;
import org.apache.bcel.verifier.structurals.ExceptionHandler;

public class CodeBlockGraph {


    private Path codeBlocks;


    public CodeBlockGraph(MethodGen methodGen) {
	codeBlocks = constructBasicCodeBlocks(methodGen);
	setTargetsCodeBlocks();
	setLevelsCodeBlocks();
    }


    public CodeBlock getCodeBlock(int i) {
	return codeBlocks.get(i);
    }


    public boolean isPartOfLoop(int index) {
	CodeBlock start = codeBlocks.get(index);
	Path visited = new Path();

	return isPartOfLoop(start, visited);
    }


    public ArrayList<Path> getEndingPathsFrom(int index) {
	ArrayList<Path> paths = 
	    new ArrayList<Path>();

	CodeBlock start = codeBlocks.get(index);
	Path visited = new Path();

	fillPaths(paths, start, visited);

	return paths;
    }



    public int getIndexCodeBlock(InstructionHandle ih) {
	for (CodeBlock codeBlock : codeBlocks) {
	    if (codeBlock.contains(ih)) {
		return codeBlock.getIndex();
	    }
	}
	throw new Error("getIndexCodeBlock(), can't find instruction");
    }


    public String toString() {
	StringBuilder sb = new StringBuilder();
	for (CodeBlock codeBlock : codeBlocks) {
	    sb.append(codeBlock);
	}
	sb.append("\n");
	/*
	   for (CodeBlock codeBlock : codeBlocks) {
	   sb.append(codeBlock.toStringNoInstructions());
	   }
	   sb.append("\n");
	   */

	return sb.toString();
    }


    private void fillPaths(ArrayList<Path> paths, 
	    CodeBlock current, Path visited) {
	//	System.out.printf("fillPaths() for %d\n", current.getIndex());
	//	System.out.printf("\tvisited: %s\n", visited);
	if (current.isEnding()) {
	    visited.add(current);
	    //	    System.out.printf("\tending, found a path: %s\n", visited);
	    paths.add((Path)visited.clone());
	    visited.removeLast(current);
	    //	    System.out.printf("end fillPaths() for %d\n", current.getIndex());
	    return;
	}

	int nrOfOccurences = visited.nrOfOccurences(current);
	if (nrOfOccurences == current.getNrOfTargets()) { // all posibilities done
	    ///	    System.out.printf("\tnrOfOccurences (%d) == nrOfTargets\n", 
	    //		    nrOfOccurences);
	    //	    System.out.printf("end fillPaths() for %d\n", current.getIndex());
	    return; // all loops handled
	}
	else if (nrOfOccurences == 0) { // do every target
	    //	    System.out.println("\tnrOfOccurences == 0\n");
	    visited.add(current);
	    for (int i = 0; i < current.getNrOfTargets(); i++) {
		fillPaths(paths, current.getTarget(i), visited);
	    }
	    visited.removeLast(current);
	    //	    System.out.printf("end fillPaths() for %d\n", current.getIndex());
	    return;
	}
	else { // already visited current, now take the other route
	    //	    System.out.printf("\tnrOfOccurences: %d\n", nrOfOccurences);
	    visited.add(current);
	    fillPaths(paths, current.getTarget(nrOfOccurences), visited);
	    visited.removeLast(current);
	    //	    System.out.printf("end fillPaths() for %d\n", current.getIndex());
	    return;
	}
	//System.out.printf("HEEEEEEE\n");
    }


    private boolean isPartOfLoop(CodeBlock current, Path visited) {
	if (visited.size() > 0 && current.equals(visited.get(0))) {
	    return true;
	}
	else if (current.isEnding()) {
	    return false;
	}

	int nrOfOccurences = visited.nrOfOccurences(current);
	if (nrOfOccurences == current.getNrOfTargets()) {
	    // all loops handled, the first in visited is not found
	    return false;
	}
	else if (nrOfOccurences == 0) {
	    visited.add(current);
	    for (int i = 0; i < current.getNrOfTargets(); i++) {
		if (isPartOfLoop(current.getTarget(i), visited)) {
		    visited.removeLast(current);
		    return true;
		}
	    }
	    visited.removeLast(current);
	    return false;
	}
	else { // already visited current, now take the other route
	    visited.add(current);
	    if (isPartOfLoop(current.getTarget(nrOfOccurences), visited)) {
		visited.removeLast(current);
		return true;
	    }
	    visited.remove(current);
	    return false;
	}
    }




    /* set the levels of the codeblocks */

    private void setLevels(int index, CodeBlock currentCodeBlock) {

	int currentLevel = currentCodeBlock.getLevel();
	CodeBlock nextCodeBlock = index < codeBlocks.size() - 1 ? 
	    codeBlocks.get(index + 1) : null;

	if (currentCodeBlock.getNrOfTargets() == 1 /*&& 
						     currentCodeBlock.targets(nextCodeBlock)*/) 
	{
	    currentCodeBlock.setLevelTargets(currentLevel);
	    /* this is probably not necessary */
	    /*
	       if (!currentCodeBlock.targets(nextCodeBlock)) {
	       currentCodeBlock.setLevelTargets(currentLevel-1);
	       }
	       else {
	       currentCodeBlock.setLevelTargets(currentLevel);
	       }
	       */
	}
	else if (currentCodeBlock.getNrOfTargets() > 1) {
	    currentCodeBlock.setLevelTargets(currentLevel + 1);
	}

	/*
	   if (currentCodeBlock.getNrOfTargets() == 2) {
	   System.out.printf("1 targets 2: %b\n", 
	   currentCodeBlock.getTarget(0).
	   targets(currentCodeBlock.getTarget(1)));
	   }
	   */

	if (currentCodeBlock.getNrOfTargets() == 2) {
	    CodeBlock target1 = currentCodeBlock.getTarget(0);
	    CodeBlock target2 = currentCodeBlock.getTarget(1);
	    if (target1.targets(target2) 
		    /*|| target1.targets(currentCodeBlock)*/) {
		/* probably not necessary */
		currentCodeBlock.setLevelTarget(1, currentLevel);
		    }
	}
    }


    private void setLevelsCodeBlocks() {
	for (int i = 0; i < codeBlocks.size(); i++) {
	    CodeBlock currentCodeBlock = codeBlocks.get(i);
	    if (i == 0) {
		currentCodeBlock.setLevel(0);
	    }
	    if (!currentCodeBlock.allLevelsTargetsSet()) {
		setLevels(i, codeBlocks.get(i));
	    }
	}
    }





    /* set the targets of the codeblocks right */


    private CodeBlock findCodeBlock(InstructionContext startInstruction) {
	for (CodeBlock codeBlock : codeBlocks) {
	    if ((codeBlock.getStart()).equals(startInstruction)) {
		return codeBlock;
	    }
	}
	throw new Error("start instruction of codeblock not found");
    }


    private void setTargets(CodeBlock codeBlock) {
	InstructionContext[] successors = codeBlock.getEnd().getSuccessors();
	for (int i = 0; i < successors.length; i++) {
	    CodeBlock target = findCodeBlock(successors[i]);
	    codeBlock.setTarget(i, target);
	}
    }


    /* set the target(s), set the level */
    private void setTargetsCodeBlocks() {
	for (CodeBlock codeBlock : codeBlocks) {
	    setTargets(codeBlock);
	}
    }

    





    /* construct basic codeblocks (without the targets right) */

    private void addCodeBlock(Path codeBlocks, 
	    InstructionContext start, 
	    ArrayList<InstructionContext> instructions, 
	    InstructionContext end,
	    ConstantPool constantPool) {
	if (start == null || instructions == null || end == null) {
	    throw new Error("CodeBlock start/end out of sync");
	} else {
	    codeBlocks.add(new CodeBlock(start, instructions, end, 
			codeBlocks.size(), constantPool));
	}
    }

    private boolean isEndInstruction(InstructionContext context) {
	return context.getInstruction().getInstruction() 
	    instanceof ReturnInstruction ||
	    context.getInstruction().getInstruction() 
	    instanceof ATHROW;
    }


    private boolean isTarget(InstructionContext context, 
	    ArrayList<InstructionContext> targets) {
	return targets.contains(context);
    }


    private boolean isEndOfCodeBlock(InstructionContext currentContext, ArrayList<InstructionContext> targets, InstructionContext nextContext) {
	return isOnlySuccessor(nextContext, successors) || successors.length > 1 || isTarget(nextContext, targets) || isEndInstruction(currentContext);
    }

    private boolean isStartOfCodeBlock(int i, InstructionContext currentContext, 
	    ArrayList<InstructionContext> targets, InstructionContext start, ArrayList<InstructionContext> instructions) {
	return (i == 0 || targets.contains(currentContext)) && start == null && instructions == null;
    }

    private void determineCodeBlock(int i, InstructionContext[] contexts, ArrayList<InstructionContext> targets, 
	    Path codeBlocks, InstructionContext start, ArrayList<InstructionContext> instructions) {

	InstructionContext currentContext = contexts[i];
	InstructionContext nextContext = i < contexts.length - 1 ? 
	    contexts[i+1] :
	    null;
	InstructionContext[] successors = currentContext.getSuccessors();

	if (isStartOfCodeBlock(i, currentContext, targets, start, instructions)) {
	    start = currentContext;
	    instructions = new ArrayList<InstructionContext>();
	}
	else {
	    throw new Error("CodeBlock start/end out of sync");
	}

	instructions.add(currentContext);

	if (isEndOfCodeBlock(currentContext, targets, nextContext)) {
	    addCodeBlock(codeBlocks, start, instructions, currentContext, 
		    methodGen.getConstantPool().getConstantPool());
	    start = null;
	    instructions = null;
	}
    }


    private void add ExceptionHandlers(InstructionContext instructionContext, 
	    ArrayList<InstructionContext> targets) {
	ExceptionHandler[] handlers = instructionContext.getExceptionHandlers();
	for (ExceptionHandler handler : handlers) {
	    InstructionContext handlerContext = graph.contextOf(
		    handler.getHandlerStart());
	    if (!targets.contains(handlerContext)) {
		targets.add(handlerContext);
	    }
	}
    }


    private <T> void add(T[] ts, ArrayList<T> arrayList) {
	for (T t : ts) {
	    arrayList.add(t);
	}

    }


    private boolean isOnlySuccessor(InstructionContext nextContext, InstructionHandle[] successors) {
	return successors.length == 1 && !successors[0].equals(nextContext);
    }


    private boolean jumpInstruction(InstructionContext instructionContext, InstructionContext[] contexts) {
	InstructionContext nextContext = i < contexts.length - 1 ? 
	    contexts[i+1] :
	    null;
	InstructionContext[] successors = currentContext.getSuccessors();
	return isOnlySuccessor(nextContext, successors);
    }


    private ArrayList<InstructionContext> getTargets(InstructionContext[] 
	    contexts, ControlFlowGraph graph) {

	ArrayList<InstructionContext> targets = 
	    new ArrayList<InstructionContext>();

	for (int i = 0; i < contexts.length; i++) {
	    InstructionContext currentContext = contexts[i];
	    InstructionContext[] successors = currentContext.getSuccessors();

	    if (jumpInstruction(currentContext, contexts)) {
		targets.add(successors[0]);
	    }
	    else if (successors.length > 1) {
		add(successors, targets);
		/*
		   for (InstructionContext successor : successors) {
		   targets.add(successor);
		   }
		   */
	    }
	    addExceptionHandlers(currentContext, targets);
	}

	return targets;
	    }


    private InstructionContext[] getContexts(MethodGen methodGen, 
	    ControlFlowGraph graph) {
	InstructionList il = methodGen.getInstructionList();
	InstructionHandle[] instructionHandles = il.getInstructionHandles();
	return graph.contextsOf(instructionHandles);
    }


    private Path constructBasicCodeBlocks(MethodGen methodGen) {
	Path codeBlocks = new Path();

	ControlFlowGraph graph = new ControlFlowGraph(methodGen);
	InstructionContext[] contexts = getContexts(methodGen, graph);
	ArrayList<InstructionContext> targets = getTargets(contexts, graph);

	InstructionContext start = null;
	ArrayList<InstructionContext> instructions = null;

	for (int i = 0; i < contexts.length; i++) {
	    determineCodeBlock(i, contexts, targets, codeBlocks, start, instructions);
	}
	return codeBlocks;
    }
}













    /*
    public ArrayList<Path> getEndingPathsFrom2(int index) {
	ArrayList<Path> paths = 
	    new ArrayList<Path>();

	CodeBlock start = codeBlocks.get(index);
	Path visited = new Path();

	fillPaths(paths, start, visited);

	return paths;
    }
    */



    /*
    private boolean canReach(CodeBlock target1, CodeBlock target2, 
	    CodeBlock parent) {
	return target1.targets(target2) || target1.targets(parent);
    }
    */


    /*
       private void fillPaths(ArrayList<Path> paths, 
       CodeBlock current, Path visited) {
       if (visited.contains(current)) {
       return; // loop
       }
       else if (current.isEnding()) {
       visited.add(current);
       paths.add((Path)visited.clone());
       visited.remove(current);
       return;
       }
       else {
       visited.add(current);
       for (int i = 0; i < current.getNrOfTargets(); i++) {
       fillPaths(paths, current.getTarget(i), visited);
       }
       visited.remove(current);
       return;
       }
       }
       */





