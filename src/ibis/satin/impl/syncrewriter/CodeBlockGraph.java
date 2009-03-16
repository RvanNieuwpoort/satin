package ibis.satin.impl.syncrewriter;

import java.util.ArrayList;

import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.ATHROW;

import org.apache.bcel.verifier.structurals.ControlFlowGraph;
import org.apache.bcel.verifier.structurals.InstructionContext;
import org.apache.bcel.verifier.structurals.ExceptionHandler;

/*
   import java.io.PrintStream;


   import org.apache.bcel.classfile.JavaClass;
   import org.apache.bcel.classfile.Method;
   import org.apache.bcel.Repository;

   import org.apache.bcel.generic.ConstantPoolGen;
   import org.apache.bcel.generic.INVOKEVIRTUAL;
   import org.apache.bcel.generic.InstructionTargeter;
   import org.apache.bcel.generic.Instruction;
   import org.apache.bcel.generic.LocalVariableInstruction;
   import org.apache.bcel.generic.StoreInstruction;
   import org.apache.bcel.generic.LoadInstruction;
   import org.apache.bcel.generic.Type;
   import org.apache.bcel.generic.StackConsumer;
   import org.apache.bcel.generic.StackProducer;

*/


class CodeBlockGraph {


    private Path codeBlocks;


    CodeBlockGraph(MethodGen methodGen) {
	codeBlocks = constructBasicCodeBlocks(methodGen);
	setTargetsCodeBlocks();
	setLevelsCodeBlocks();
    }


    private boolean jumpInstruction(InstructionContext currentContext, 
	    InstructionContext nextContext, InstructionContext[] successors) {
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

	    if (jumpInstruction(currentContext, nextContext, successors)) {
		targets.add(successors[0]);
	    }
	    else if (successors.length > 1) {
		for (InstructionContext successor : successors) {
		    targets.add(successor);
		}
	    }
	    ExceptionHandler[] handlers = currentContext.getExceptionHandlers();
	    for (ExceptionHandler handler : handlers) {
		InstructionContext handlerContext = graph.contextOf(
			handler.getHandlerStart());
		if (!targets.contains(handlerContext)) {
		    targets.add(handlerContext);
		}
	    }
	}

	return targets;
	    }


    private InstructionContext[] getContexts(MethodGen methodGen, 
	    ControlFlowGraph graph) {
	InstructionList il = methodGen.getInstructionList();
	InstructionHandle[] instructionHandles = il.getInstructionHandles();
	return graph.contextsOf(instructionHandles);
    }


    private void addCodeBlock(Path codeBlocks, 
	    InstructionContext start, 
	    ArrayList<InstructionContext> instructions, 
	    InstructionContext end) {
	if (start == null || instructions == null || end == null) {
	    throw new Error("CodeBlock start/end out of sync");
	} else {
	    codeBlocks.add(new CodeBlock(start, instructions, end, 
			codeBlocks.size()));
	}
    }


    private boolean isTarget(InstructionContext context, 
	    ArrayList<InstructionContext> targets) {
	return targets.contains(context);
    }

    private boolean isEndInstruction(InstructionContext context) {
	return context.getInstruction().getInstruction() 
	    instanceof ReturnInstruction ||
	    context.getInstruction().getInstruction() 
	    instanceof ATHROW;
    }



    private Path constructBasicCodeBlocks(MethodGen methodGen) {
	Path codeBlocks = new Path();

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


	    if (i == 0 || targets.contains(currentContext)) { 
		if (start == null && instructions == null) {
		    start = currentContext;
		    instructions = new ArrayList<InstructionContext>();
		}
		else {
		    throw new Error("CodeBlock start/end out of sync");
		}
	    }

	    instructions.add(currentContext);

	    if ((jumpInstruction(currentContext, nextContext, successors))
		    ||
		    (successors.length > 1)
		    ||
		    (isTarget(nextContext, targets))
		    ||
		    (isEndInstruction(currentContext))) {

		addCodeBlock(codeBlocks, start, instructions, currentContext);
		start = null;
		instructions = null;
		    }
	}
	return codeBlocks;
    }


    private CodeBlock findCodeBlock(InstructionContext startInstruction) {
	for (CodeBlock codeBlock : codeBlocks) {
	    if ((codeBlock.getStart()).equals(startInstruction)) {
		return codeBlock;
	    }
	}
	throw new Error("start instruction of codeblock not found");
    }


    private boolean canReach(CodeBlock target1, CodeBlock target2, 
	    CodeBlock parent) {
	return target1.targets(target2) || target1.targets(parent);
    }


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


    ArrayList<Path> getEndingPathsFrom(int index) {
	ArrayList<Path> paths = 
	    new ArrayList<Path>();

	CodeBlock start = codeBlocks.get(index);
	Path visited = new Path();

	fillPaths(paths, start, visited);

	return paths;
    }


    int getIndexCodeBlock(InstructionHandle ih) {
	for (CodeBlock codeBlock : codeBlocks) {
	    if (codeBlock.contains(ih)) {
		return codeBlock.getIndex();
	    }
	}
	throw new Error("getIndexCodeBlock(), can't find instruction");
    }
}


/*
   void buildCodeBlock(int indexCurrentCodeBlock, 
   boolean[] codeBlocksFinished) {
   CodeBlock currentCodeBlock = codeBlocks.get(indexCurrentCodeBlock);

   setTargets(currentCodeBlock);


   int currentLevel = codeBlocks.get(i).getLevel();
   CodeBlock nextCodeBlock = i < codeBlocks.size() - 1 ? 
   codeBlocks.get(i + 1) : null;
   */




/*


   {

   InstructionContext[] successors = 
   currentCodeBlock.getEnd().getSuccessors();


   if ((successors.length == 1) && 
   (successors[0].equals(nextCodeBlock.getStart()))) 
   {
   nextCodeBlock.setLevel(currentLevel);
   currentCodeBlock.setTarget1Index(i + 1);
   codeBlocksFinished[i] = true;
   }
   else if (successors.length == 2) {
   int index1 = findIndex(successors[0]);
   int index2 = findIndex(successors[1]);
   currentCodeBlock.setTarget1Index(index1);
   currentCodeBlock.setTarget2Index(index2);

   CodeBlock target1 = codeBlocks.get(index1);
   CodeBlock target2 = codeBlocks.get(index2);

   target1.setLevel(currentLevel + 1);
   target2.setLevel(currentLevel + 1);

// als block1 block2 als target heeft, dan is block1 van het
// level van currentBlock.
   }
   */



