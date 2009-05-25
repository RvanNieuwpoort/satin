package ibis.satin.impl.syncrewriter.controlflow;

import java.util.ArrayList;

import ibis.satin.impl.syncrewriter.bcel.MethodGen;

import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.ATHROW;
import org.apache.bcel.classfile.ConstantPool;

import org.apache.bcel.verifier.structurals.ControlFlowGraph;
import org.apache.bcel.verifier.structurals.InstructionContext;
import org.apache.bcel.verifier.structurals.ExceptionHandler;

public class BasicBlockGraph {



    private static final boolean SHOULD_BE_NULL = true;


    private Path basicBlocks;

    

    /* public methods */

    public BasicBlockGraph(MethodGen methodGen) {
	basicBlocks = constructBasicBasicBlocks(methodGen);
	setTargetsBasicBlocks();
	setLevelsBasicBlocks();
    }


    public BasicBlock getBasicBlock(int i) {
	return basicBlocks.get(i);
    }


    public boolean isPartOfLoop(int index) {
	BasicBlock start = basicBlocks.get(index);
	Path visited = new Path();

	return isPartOfLoop(start, visited);
    }


    public ArrayList<Path> getEndingPathsFrom(int index) {
	ArrayList<Path> paths = 
	    new ArrayList<Path>();

	BasicBlock start = basicBlocks.get(index);
	Path visited = new Path();

	fillPaths(paths, start, visited);

	return paths;
    }



    public int getIndexBasicBlock(InstructionHandle ih) {
	for (BasicBlock basicBlock : basicBlocks) {
	    if (basicBlock.contains(ih)) {
		return basicBlock.getIndex();
	    }
	}
	throw new Error("getIndexBasicBlock(), can't find instruction");
    }


    public String toString() {
	StringBuilder sb = new StringBuilder();
	for (BasicBlock basicBlock : basicBlocks) {
	    sb.append(basicBlock);
	}
	sb.append("\n");
	return sb.toString();
    }





    /* private methods */


    private void fillPaths(ArrayList<Path> paths, 
	    BasicBlock current, Path visited) {
	if (current.isEnding()) {
	    visited.add(current);
	    paths.add((Path)visited.clone());
	    visited.removeLast(current);
	    return;
	}

	int nrOfOccurences = visited.nrOfOccurences(current);
	if (nrOfOccurences == current.getNrOfTargets()) { // all posibilities done
	    return; // all loops handled
	}
	else if (nrOfOccurences == 0) { // do every target
	    visited.add(current);
	    for (int i = 0; i < current.getNrOfTargets(); i++) {
		fillPaths(paths, current.getTarget(i), visited);
	    }
	    visited.removeLast(current);
	    return;
	}
	else { // already visited current, now take the other route
	    visited.add(current);
	    fillPaths(paths, current.getTarget(nrOfOccurences), visited);
	    visited.removeLast(current);
	    return;
	}
    }


    private boolean isPartOfLoop(BasicBlock current, Path visited) {
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




    /* set the levels of the basic blocks */

    private void setLevels(int index, BasicBlock currentBasicBlock) {

	int currentLevel = currentBasicBlock.getLevel();
	BasicBlock nextBasicBlock = index < basicBlocks.size() - 1 ? 
	    basicBlocks.get(index + 1) : null;

	if (currentBasicBlock.getNrOfTargets() == 1 
		/*&& 
		currentBasicBlock.targets(nextBasicBlock)*/) 
	{
	    currentBasicBlock.setLevelTargets(currentLevel);
	}
	else if (currentBasicBlock.getNrOfTargets() > 1) {
	    currentBasicBlock.setLevelTargets(currentLevel + 1);
	}


	if (currentBasicBlock.getNrOfTargets() == 2) {
	    BasicBlock target1 = currentBasicBlock.getTarget(0);
	    BasicBlock target2 = currentBasicBlock.getTarget(1);
	    if (target1.targets(target2) 
		    /*|| target1.targets(currentBasicBlock)*/) {
		/* probably not necessary */
		currentBasicBlock.setLevelTarget(1, currentLevel);
		    }
	}
    }


    private void setLevelsBasicBlocks() {
	for (int i = 0; i < basicBlocks.size(); i++) {
	    BasicBlock currentBasicBlock = basicBlocks.get(i);
	    if (i == 0) {
		currentBasicBlock.setLevel(0);
	    }
	    if (!currentBasicBlock.allLevelsTargetsSet()) {
		setLevels(i, basicBlocks.get(i));
	    }
	}
    }





    /* set the targets of the basic blocks right */


    private BasicBlock findBasicBlock(InstructionContext startInstruction) {
	for (BasicBlock basicBlock : basicBlocks) {
	    if ((basicBlock.getStart()).equals(startInstruction)) {
		return basicBlock;
	    }
	}
	throw new Error("start instruction of basic block not found");
    }


    private void setTargets(BasicBlock basicBlock) {
	InstructionContext[] successors = basicBlock.getEnd().getSuccessors();
	for (int i = 0; i < successors.length; i++) {
	    BasicBlock target = findBasicBlock(successors[i]);
	    basicBlock.setTarget(i, target);
	}
    }


    /* set the target(s), set the level */
    private void setTargetsBasicBlocks() {
	for (BasicBlock basicBlock : basicBlocks) {
	    setTargets(basicBlock);
	}
    }







    /* construct basic basic blocks (without the targets right) */

    private void addBasicBlock(Path basicBlocks, 
	    InstructionContext start, 
	    ArrayList<InstructionContext> instructions, 
	    InstructionContext end,
	    MethodGen methodGen) {
	if (start == null || instructions == null || end == null) {
	    throw new Error("BasicBlock start/end out of sync");
	} else {
	    basicBlocks.add(new BasicBlock(start, instructions, end, 
			basicBlocks.size(), methodGen));
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


    private boolean isEndOfBasicBlock(InstructionContext currentContext, ArrayList<InstructionContext> targets, InstructionContext nextContext,
	    InstructionContext[] successors) {
	return hasOneSuccessorNotBeingNext(nextContext, successors) || 
	    successors.length > 1 || 
	    isTarget(nextContext, targets) || 
	    isEndInstruction(currentContext);
    }


    private boolean isStartOfBasicBlock(int i, InstructionContext currentContext, 
	    ArrayList<InstructionContext> targets) {
	return i == 0 || targets.contains(currentContext);
    }



    private void addExceptionHandlers(InstructionContext instructionContext, 
	    ArrayList<InstructionContext> targets, ControlFlowGraph graph) {
	ExceptionHandler[] handlers = instructionContext.getExceptionHandlers();
	for (ExceptionHandler handler : handlers) {
	    InstructionContext handlerContext = graph.contextOf(
		    handler.getHandlerStart());
	    if (!targets.contains(handlerContext)) {
		targets.add(handlerContext);
	    }
	}
    }


    private boolean hasOneSuccessorNotBeingNext(InstructionContext nextContext, InstructionContext[] successors) {
	return successors.length == 1 && !successors[0].equals(nextContext);
    }


    private ArrayList<InstructionContext> getTargets(InstructionContext[] 
	    contexts, ControlFlowGraph graph) {

	ArrayList<InstructionContext> targets = 
	    new ArrayList<InstructionContext>();

	for (int i = 0; i < contexts.length; i++) {
	    InstructionContext currentContext = contexts[i];
	    InstructionContext nextContext = i < contexts.length - 1 ? 
		contexts[i+1] :
		null;
	    InstructionContext[] successors = currentContext.getSuccessors();

	    if (hasOneSuccessorNotBeingNext(nextContext, successors)) {
		targets.add(successors[0]);
	    }
	    else if (successors.length > 1) {
		addAll(successors, targets);
	    }
	    addExceptionHandlers(currentContext, targets, graph);
	}

	return targets;
	    }



    private InstructionContext[] getContexts(MethodGen methodGen, 
	    ControlFlowGraph graph) {
	InstructionList il = methodGen.getInstructionList();
	InstructionHandle[] instructionHandles = il.getInstructionHandles();
	return graph.contextsOf(instructionHandles);
    }


    void checkIfSet(InstructionContext start, ArrayList<InstructionContext> instructions, boolean shouldBeNull) {
	if (start == null && instructions == null && shouldBeNull) {
	    // ok
	}
	else if (start != null && instructions != null && !shouldBeNull) {
	    // ok
	}
	else {
	    throw new Error("BasicBlock start/end out of sync");
	}
    }



    private Path constructBasicBasicBlocks(MethodGen methodGen) {
	Path basicBlocks = new Path();

	ControlFlowGraph graph = new ControlFlowGraph(methodGen);
	InstructionContext[] contexts = getContexts(methodGen, graph);
	ArrayList<InstructionContext> targets = getTargets(contexts, graph);

	InstructionContext start = null;
	ArrayList<InstructionContext> instructions = null;

	for (int i = 0; i < contexts.length; i++) {
	    InstructionContext currentContext = contexts[i];
	    InstructionContext nextContext = i < contexts.length - 1 ? 
		contexts[i+1] :
		null;
	    InstructionContext[] successors = currentContext.getSuccessors();

	    if (isStartOfBasicBlock(i, currentContext, targets)) {
		checkIfSet(start, instructions, SHOULD_BE_NULL);
		start = currentContext;
		instructions = new ArrayList<InstructionContext>();
	    }

	    instructions.add(currentContext);

	    if (isEndOfBasicBlock(currentContext, targets, nextContext, successors)) {
		checkIfSet(start, instructions, !SHOULD_BE_NULL);
		addBasicBlock(basicBlocks, start, instructions, currentContext, 
			methodGen);
		start = null;
		instructions = null;
	    }
	}
	return basicBlocks;
    }




    /* for other classes */


    private <T> void addAll(T[] ts, ArrayList<T> arrayList) {
	for (T t : ts) {
	    arrayList.add(t);
	}

    }
}
