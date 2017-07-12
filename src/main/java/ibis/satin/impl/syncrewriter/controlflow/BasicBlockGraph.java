package ibis.satin.impl.syncrewriter.controlflow;

import ibis.satin.impl.syncrewriter.bcel.MethodGen;

import java.util.ArrayList;

import org.apache.bcel.generic.ATHROW;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.verifier.structurals.ControlFlowGraph;
import org.apache.bcel.verifier.structurals.ExceptionHandler;
import org.apache.bcel.verifier.structurals.InstructionContext;

/**
 * A basic block graph is a graph of basic blocks of a method.
 */
public class BasicBlockGraph {

    private static final boolean SHOULD_BE_NULL = true;

    private Path basicBlocks;

    /* public methods */

    /**
     * Instantiates a basic block graph from a method.
     * 
     * @param methodGen
     *            method
     */
    public BasicBlockGraph(MethodGen methodGen) {
	basicBlocks = constructBasicBasicBlocks(methodGen);
	setTargetsBasicBlocks();
	setLevelsBasicBlocks();
    }

    /**
     * Returns the basic block with identifier id in the graph.
     * 
     * @param id
     *            the identifier of the basic block in the graph.
     * @return the basic block with identifier id.
     */
    public BasicBlock getBasicBlock(int id) {
	return basicBlocks.get(id);
    }

    /**
     * Tests whether basic block with id id is part of a loop.
     * 
     * @param id
     *            The id of the basic block.
     * @return true if basic block with identifier id is part of a loop; false
     *         otherwise.
     */
    public boolean isPartOfLoop(int id) {
	BasicBlock start = basicBlocks.get(id);
	Path visited = new Path();

	return isPartOfLoop(start, visited);
    }

    /**
     * Returns the ending paths from the basic block with identifier id.
     * 
     * @param id
     *            the identifier of the basic block from which all ending paths
     *            have to be calculated.
     * @return a list of paths that exit the method.
     */
    public ArrayList<Path> getEndingPathsFrom(int id) {
	ArrayList<Path> paths = new ArrayList<Path>();

	BasicBlock start = basicBlocks.get(id);
	Path visited = new Path();

	calculateEndingPaths(paths, start, visited);

	return paths;
    }

    /**
     * Returns the id of the basic block that contains an instruction.
     * 
     * @param ih
     *            the instruction that is part of a basic block.
     * @return the id of the basic block that contains instruction ih.
     */
    public int getIdBasicBlock(InstructionHandle ih) {
	for (BasicBlock basicBlock : basicBlocks) {
	    if (basicBlock.contains(ih)) {
		return basicBlock.getId();
	    }
	}
	throw new Error("getIdBasicBlock(), can't find instruction");
    }

    /**
     * Returns a string representation of the basic block graph.
     */
    public String toString() {
	StringBuilder sb = new StringBuilder();
	for (BasicBlock basicBlock : basicBlocks) {
	    sb.append(basicBlock);
	}
	sb.append("\n");
	return sb.toString();
    }

    /* private methods */

    private void calculateEndingPaths(ArrayList<Path> paths,
	    BasicBlock current, Path visited) {
	if (current.isEnding()) {
	    visited.add(current);
	    paths.add((Path) visited.clone());
	    visited.removeLast(current);
	    return;
	}

	int nrOfOccurences = visited.nrOfOccurences(current);
	if (nrOfOccurences == current.getNrOfTargets()) { // all posibilities
							  // done
	    return; // all loops handled
	} else if (nrOfOccurences == 0) { // do every target
	    visited.add(current);
	    for (int i = 0; i < current.getNrOfTargets(); i++) {
		calculateEndingPaths(paths, current.getTarget(i), visited);
	    }
	    visited.removeLast(current);
	    return;
	} else { // already visited current, now take the other route
	    visited.add(current);
	    calculateEndingPaths(paths, current.getTarget(nrOfOccurences),
		    visited);
	    visited.removeLast(current);
	    return;
	}
    }

    private boolean isPartOfLoop(BasicBlock current, Path visited) {
	if (visited.size() > 0 && current.equals(visited.get(0))) {
	    return true;
	} else if (current.isEnding()) {
	    return false;
	}

	int nrOfOccurences = visited.nrOfOccurences(current);
	if (nrOfOccurences == current.getNrOfTargets()) {
	    // all loops handled, the first in visited is not found
	    return false;
	} else if (nrOfOccurences == 0) {
	    visited.add(current);
	    for (int i = 0; i < current.getNrOfTargets(); i++) {
		if (isPartOfLoop(current.getTarget(i), visited)) {
		    visited.removeLast(current);
		    return true;
		}
	    }
	    visited.removeLast(current);
	    return false;
	} else { // already visited current, now take the other route
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
    /* this could be improved, not really used */

    private void setLevels(int index, BasicBlock currentBasicBlock) {

	int currentLevel = currentBasicBlock.getLevel();
	// BasicBlock nextBasicBlock = index < basicBlocks.size() - 1 ?
	// basicBlocks.get(index + 1) : null;

	if (currentBasicBlock.getNrOfTargets() == 1
	/*
	 * && currentBasicBlock.targets(nextBasicBlock)
	 */) {
	    currentBasicBlock.setLevelTargets(currentLevel);
	} else if (currentBasicBlock.getNrOfTargets() > 1) {
	    currentBasicBlock.setLevelTargets(currentLevel + 1);
	}

	if (currentBasicBlock.getNrOfTargets() == 2) {
	    BasicBlock target1 = currentBasicBlock.getTarget(0);
	    BasicBlock target2 = currentBasicBlock.getTarget(1);
	    if (target1.targets(target2)
	    /* || target1.targets(currentBasicBlock) */) {
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

    private void setTargetsBasicBlocks() {
	for (BasicBlock basicBlock : basicBlocks) {
	    setTargets(basicBlock);
	}
    }

    /* construct basic basic blocks (without the targets right) */

    private void addBasicBlock(Path basicBlocks, InstructionContext start,
	    ArrayList<InstructionContext> instructions, InstructionContext end,
	    MethodGen methodGen) {
	if (start == null || instructions == null || end == null) {
	    throw new Error("BasicBlock start/end out of sync");
	} else {
	    basicBlocks.add(new BasicBlock(start, instructions, end,
		    basicBlocks.size(), methodGen));
	}
    }

    private boolean isEndInstruction(InstructionContext context) {
	return context.getInstruction().getInstruction() instanceof ReturnInstruction
		|| context.getInstruction().getInstruction() instanceof ATHROW;
    }

    private boolean isTarget(InstructionContext context,
	    ArrayList<InstructionContext> targets) {
	return targets.contains(context);
    }

    private boolean isEndOfBasicBlock(InstructionContext currentContext,
	    ArrayList<InstructionContext> targets,
	    InstructionContext nextContext, InstructionContext[] successors) {
	return hasOneSuccessorNotBeingNext(nextContext, successors)
		|| successors.length > 1 || isTarget(nextContext, targets)
		|| isEndInstruction(currentContext);
    }

    /*
     * just a check for consistency for beginnings and endings of basic blocks
     */
    void checkIfSet(InstructionContext start,
	    ArrayList<InstructionContext> instructions, boolean shouldBeNull) {
	if (start == null && instructions == null && shouldBeNull) {
	    // ok
	} else if (start != null && instructions != null && !shouldBeNull) {
	    // ok
	} else {
	    throw new Error("BasicBlock start/end out of sync");
	}
    }

    private boolean isStartOfBasicBlock(int i,
	    InstructionContext currentContext,
	    ArrayList<InstructionContext> targets) {
	return i == 0 || targets.contains(currentContext);
    }

    /* add the exception handlers to the targets */
    private void addExceptionHandlers(InstructionContext instructionContext,
	    ArrayList<InstructionContext> targets, ControlFlowGraph graph) {
	ExceptionHandler[] handlers = instructionContext.getExceptionHandlers();
	for (ExceptionHandler handler : handlers) {
	    InstructionContext handlerContext = graph.contextOf(handler
		    .getHandlerStart());
	    if (!targets.contains(handlerContext)) {
		targets.add(handlerContext);
	    }
	}
    }

    private boolean hasOneSuccessorNotBeingNext(InstructionContext nextContext,
	    InstructionContext[] successors) {
	return successors.length == 1 && !successors[0].equals(nextContext);
    }

    /* get all the instruction contexts that are targeted by any instruction */
    private ArrayList<InstructionContext> getTargets(
	    InstructionContext[] contexts, ControlFlowGraph graph) {

	ArrayList<InstructionContext> targets = new ArrayList<InstructionContext>();

	for (int i = 0; i < contexts.length; i++) {
	    InstructionContext currentContext = contexts[i];
	    InstructionContext nextContext = i < contexts.length - 1 ? contexts[i + 1]
		    : null;
	    InstructionContext[] successors = currentContext.getSuccessors();

	    if (hasOneSuccessorNotBeingNext(nextContext, successors)) {
		targets.add(successors[0]);
	    } else if (successors.length > 1) {
		addAll(successors, targets);
	    }
	    addExceptionHandlers(currentContext, targets, graph);
	}

	return targets;
    }

    /* get the intstruction contexts in the right order */
    private InstructionContext[] getContexts(MethodGen methodGen,
	    ControlFlowGraph graph) {
	InstructionList il = methodGen.getInstructionList();
	InstructionHandle[] instructionHandles = il.getInstructionHandles();
	return graph.contextsOf(instructionHandles);
    }

    /*
     * constructs basic basic blocks, without the targets and the levels set
     * right.
     */
    private Path constructBasicBasicBlocks(MethodGen methodGen) {
	Path basicBlocks = new Path();

	ControlFlowGraph graph = new ControlFlowGraph(methodGen);
	InstructionContext[] contexts = getContexts(methodGen, graph);
	ArrayList<InstructionContext> targets = getTargets(contexts, graph);

	InstructionContext start = null;
	ArrayList<InstructionContext> instructions = null;

	for (int i = 0; i < contexts.length; i++) {
	    InstructionContext currentContext = contexts[i];
	    InstructionContext nextContext = i < contexts.length - 1 ? contexts[i + 1]
		    : null;
	    InstructionContext[] successors = currentContext.getSuccessors();

	    if (isStartOfBasicBlock(i, currentContext, targets)) {
		checkIfSet(start, instructions, SHOULD_BE_NULL);
		start = currentContext;
		instructions = new ArrayList<InstructionContext>();
	    }

	    instructions.add(currentContext);

	    if (isEndOfBasicBlock(currentContext, targets, nextContext,
		    successors)) {
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
