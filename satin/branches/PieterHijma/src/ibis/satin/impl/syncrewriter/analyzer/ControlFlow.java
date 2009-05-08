package ibis.satin.impl.syncrewriter.analyzer;

import ibis.satin.impl.syncrewriter.analyzer.controlflow.*;

import ibis.satin.impl.syncrewriter.Debug;

import java.util.ArrayList;

import ibis.satin.impl.syncrewriter.Analyzer;
import ibis.satin.impl.syncrewriter.SyncInsertionProposalFailure;
import ibis.satin.impl.syncrewriter.SpawnableCall;
import ibis.satin.impl.syncrewriter.SpawnableMethod;
import ibis.satin.impl.syncrewriter.NeverReadException;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.verifier.structurals.InstructionContext;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.LoadInstruction;




public class ControlFlow implements Analyzer {


    private class PathPartOfLoopException extends Exception {
	private static final String MESSAGE = "Sync insertion is part of a loop";
	private PathPartOfLoopException() {
	    super(MESSAGE);
	}
    }

    private class ResultNotLoadedException extends Exception {
	private static final String MESSAGE = "Sync insertion is part of a loop";
	private ResultNotLoadedException() {
	    super(MESSAGE);
	}
    }



    private Debug d;


    public InstructionHandle[] proposeSyncInsertion(SpawnableMethod spawnableMethod, Debug d)
	throws SyncInsertionProposalFailure {

	this.d = d;
	/* We only want to return one sync statement at exactly the right spot
	*/ 
	InstructionHandle[] instructionHandles = new InstructionHandle[1];

	d.log(0, "proposing codeblock\n");
	CodeBlock proposedCodeBlock = proposeCodeBlock(spawnableMethod);
	d.log(0, "propesed codeblock: %d\n", proposedCodeBlock.getIndex());

	d.log(0, "trying to get the earliest load associated with spawnable calls\n");
	try {
	    instructionHandles[0] = getEarliestLoadInstruction(proposedCodeBlock.getInstructions(), 
		spawnableMethod.getSpawnableCalls());
	    d.log(0, "succeeded to get the earliest load\n");
	}
	catch (ResultNotLoadedException e) {
	    instructionHandles[0] = proposedCodeBlock.getLastInstruction();
		d.log(0, "result is not loaded in this codeblock, defaulting to last instruction of the codeblock\n");
	}
	return instructionHandles;
    }






    /* returns the load instruction that goes with the spawnable call or the
     * last one, if there is no load instruction for this spawnable call in the
     * instructions.
     */
    private InstructionHandle getLoadInstruction(ArrayList<InstructionContext> instructions, SpawnableCall spawnableCall) throws 
    ResultNotLoadedException {
	int startIndex = 0;
	for (int i = 0; i < instructions.size(); i++) {
	    InstructionHandle ih = instructions.get(i).getInstruction();
	    if (ih.equals(spawnableCall.getInstructionHandle())) {
		startIndex = i;
		break;
	    }
	}

	for (int i = startIndex; i < instructions.size(); i++) {
	    InstructionHandle ih = instructions.get(i).getInstruction();
	    try {
		LoadInstruction loadInstruction = 
		    (LoadInstruction) (ih.getInstruction());
		if (loadInstruction.getIndex() == spawnableCall.getResultIndex()) {
		    return ih;
		}
	    }
	    catch (ClassCastException e) {
	    }
	}
	throw new ResultNotLoadedException();
    }



    /* Get the earliest load instruction of the results of the spawnable calls.
     *
     * result1 = spawnableCall();
     * result2 = spawnableCall();
     *
     * read(result2); <---- this is returned
     * read(result1);
     */
    private InstructionHandle getEarliestLoadInstruction(ArrayList<InstructionContext> instructions, 
	    ArrayList<SpawnableCall> spawnableCalls) throws ResultNotLoadedException {

	InstructionHandle earliestLoadInstruction = null;

	d.log(1, "looking for load instruction for:\n"); 
	for (SpawnableCall spawnableCall : spawnableCalls) {
	    d.log(2, "spawnable call: %s\n", spawnableCall);
	    try {
		InstructionHandle loadInstruction = null;
		/*
		if (spawnableCall.getResultIndex() == -1) {
		       loadInstruction = getStackConsumer(spawnableCall, instructions);
		       d.log(3, "found load instruction %s on position %d\n", loadInstruction, loadInstruction.getPosition());
		}
		else {
		       */
		/* TODO hier nog iets voor exceptions */
		loadInstruction = getLoadInstruction(instructions, spawnableCall);
		d.log(3, "found load instruction %s on position %d\n", loadInstruction, loadInstruction.getPosition());
		if (earliestLoadInstruction == null 
			|| loadInstruction.getPosition() < earliestLoadInstruction.getPosition()) {
		    earliestLoadInstruction = loadInstruction;
			}
		}
	    catch (ResultNotLoadedException e) {
		d.log(3, "result of spawnable call not loaded\n");
	    }
	    }
	    if (earliestLoadInstruction == null) {
		d.log(1, "didn't find loadinstructions\n");
		throw new ResultNotLoadedException();
	    }
	    d.log(1, "earliest load instruction: %s\n", earliestLoadInstruction);
	    return earliestLoadInstruction;
	}


	private CodeBlock getLastCodeBlockNotPartOfLoop(CodeBlockGraph codeBlockGraph, Path path) throws PathPartOfLoopException {
	    for (int i = path.size() - 1; i >= 0; i--) {
		CodeBlock codeBlock = path.get(i);
		if (!codeBlockGraph.isPartOfLoop(codeBlock.getIndex())) {
		    return codeBlock;
		}
	    }
	    throw new PathPartOfLoopException();
	}


	private String toString(Path[] paths) {
	    StringBuilder sb = new StringBuilder("[");
	    for (int i = 0; i < paths.length - 1; i++) {
		sb.append(paths[i]);
		sb.append(", ");
	    }
	    sb.append(paths[paths.length - 1]);
	    sb.append("]");
	    return sb.toString();
	}


	private Path getCommonSubPathFromEnd(Path[] paths) {
	    if (paths.length == 0) {
		return new Path();
	    }
	    Path subPath = paths[0];
	    for (int i = 1; i < paths.length; i++) {
		subPath = subPath.getCommonSubPathFromEnd(paths[i]);
	    }
	    return subPath;
	}


	private Path getCommonSubPathFromStart(Path[] paths) {
	    if (paths.length == 0) {
		return new Path();
	    }
	    Path subPath = paths[0];
	    for (int i = 1; i < paths.length; i++) {
		subPath = subPath.getCommonSubPathFromStart(paths[i]);
	    }
	    return subPath;
	}



	private Path getLatestCommonSubPath(Path[] paths) {
	    Path latestCommonSubPath = getCommonSubPathFromEnd(paths);
	    if (latestCommonSubPath.size() == 0) {
		latestCommonSubPath = getCommonSubPathFromStart(paths);
	    }

	    return latestCommonSubPath;
	}


	private ArrayList<StoreLoadPath> getStoreLoadPaths(ArrayList<Path> 
		endingPaths, SpawnableCall spawnableCall) {

	    ArrayList<StoreLoadPath> storeLoadPaths = 
		new ArrayList<StoreLoadPath>();

	    for (Path path : endingPaths) {
		StoreLoadPath storeLoadPath = null;
		try {
		    storeLoadPath = new StoreLoadPath(
			    spawnableCall.getInstructionHandle(), path, spawnableCall.getResultIndex());
		}
		catch (NeverReadException e) {
		}
		if (storeLoadPath != null && !storeLoadPaths.contains(storeLoadPath)) {
		    storeLoadPaths.add(storeLoadPath);
		}
	    }

	    return storeLoadPaths;
		}



	private Path calculateSpawnToLoadPath(CodeBlockGraph codeBlockGraph, SpawnableCall spawnableCall) {
	    int indexCodeBlock = 
		codeBlockGraph.getIndexCodeBlock(spawnableCall.getInstructionHandle());

	    d.log(1, "the spawnable call: %s\n", spawnableCall);

	    ArrayList<Path> endingPaths = 
		codeBlockGraph.getEndingPathsFrom(indexCodeBlock);
	    d.log(2, "ending paths from %d:\n", indexCodeBlock);
	    d.log(3, "%s\n", endingPaths);

	    ArrayList<StoreLoadPath> storeLoadPaths = getStoreLoadPaths(endingPaths, spawnableCall);
	    d.log(2, "paths from store to load:");
	    d.log(3, "%s\n", storeLoadPaths);

	    Path latestCommonSubPath;
	    if (storeLoadPaths.size() == 0) {
		d.warning("The result of spawnable call %s is never read, %s\n", spawnableCall,
			"defaulting to the latest common subpath of all ending paths");
		Path[] endingPathsArray = new Path[endingPaths.size()];
		latestCommonSubPath = 
		    getLatestCommonSubPath(endingPaths.toArray(endingPathsArray));
	    }
	    else {
		Path[] storeLoadPathsArray = new Path[storeLoadPaths.size()];
		latestCommonSubPath = 
		    getLatestCommonSubPath(storeLoadPaths.toArray(storeLoadPathsArray));
	    }

	    d.log(2, "latest common subbpath:\n");
	    d.log(3, "%s\n", latestCommonSubPath);

	    return latestCommonSubPath;
	}


	private Path[] calculateSpawnToLoadPaths(CodeBlockGraph codeBlockGraph, ArrayList<SpawnableCall> spawnableCalls) {
	    Path[] spawnToLoadPaths = new Path[spawnableCalls.size()];

	    for (int i = 0; i < spawnableCalls.size(); i++) {
		spawnToLoadPaths[i] = calculateSpawnToLoadPath(codeBlockGraph, spawnableCalls.get(i));
	    }

	    return spawnToLoadPaths;
	}


	private CodeBlock proposeCodeBlock(SpawnableMethod spawnableMethod) 
	    throws SyncInsertionProposalFailure {

	    ArrayList<SpawnableCall> spawnableCalls = 
		spawnableMethod.getSpawnableCalls();
	    CodeBlockGraph codeBlockGraph = new CodeBlockGraph(spawnableMethod);

	    Path[] spawnToLoadPaths = calculateSpawnToLoadPaths(codeBlockGraph, spawnableCalls);
	    d.log(1, "the paths from spawn to load are:\n");
	    d.log(2, "%s\n", toString(spawnToLoadPaths));

	    Path latestCommonSubPath = getLatestCommonSubPath(spawnToLoadPaths);
	    d.log(1, "the final latest common subpath\n");
	    d.log(2, "%s\n", latestCommonSubPath);

	    try {
		CodeBlock lastCodeBlockNotPartOfLoop = 
		    getLastCodeBlockNotPartOfLoop(codeBlockGraph, latestCommonSubPath);
		d.log(1, "the last codeblock not part of a loop: %d\n", lastCodeBlockNotPartOfLoop.getIndex());
		return lastCodeBlockNotPartOfLoop;
	    }
	    catch (PathPartOfLoopException e) {
		CodeBlock lastCodeBlock = latestCommonSubPath.getLastCodeBlock(); 
		d.warning("taking last codeblock %d, but it is part of a loop\n", lastCodeBlock.getIndex());
		return lastCodeBlock;
	    }
	}
}



	/*
	   if (lastCodeBlocksTheSame(spawnToLoadPaths)) {
	   CodeBlock lastCodeBlock = spawnToLoadPaths[0].getLastCodeBlock();
	   return instructionHandles;
	   }
	   else {
	   System.out.println("NOT ACCOUNTED FOR YET");
	   }

	   return instructionHandles;
    }
    */








    /*
    private InstructionHandle getPositionSpawnableCall(ArrayList<InstructionContext> instructions, SpawnableCall spawnableCall) throws 
	ResultNotLoadedException {
	*/
	    /*
	    for (int i = 0; i < instructions.size(); i++) {
		if (spawnableCall.getInstructionHandle().equals(instructions.get(i).getInstruction())) {
		    return i;
		}
	}
	*/
/*
	throw new ResultNotLoadedException();
	}
	*/


    /*

    private InstructionHandle getStackConsumer(ArrayList<InstructionContext> instructions, SpawnableCall spawnableCall) throws 
	ResultNotLoadedException {
	*/
	
	    /*
	    int positionSpawnableCall = getPositionSpawnableCall(instructions, spawnableCall);
	    int stackProduction = instructions.get(positionSpawnableCall);
	for (int i = positionSpawnableCall; i < instructions.size(); i++) {
	    InstructionHandle ih = instructions.get(i).getInstruction();
	    try {
		LoadInstruction loadInstruction = 
		    (LoadInstruction) (ih.getInstruction());
		if (loadInstruction.getIndex() == spawnableCall.getResultIndex()) {
		    return ih;
		}
	    }
	    catch (ClassCastException e) {
	    }
	}
	*/
/*
	throw new ResultNotLoadedException();
    }
    */


	/*
	private boolean lastCodeBlocksTheSame(Path[] paths) {
	    CodeBlock lastCodeBlock = paths[0].getLastCodeBlock();
	    boolean lastCodeBlocksTheSame = true;
	    for (int i = 0; i < paths.length; i++) {
		if (!lastCodeBlock.equals(paths[i].getLastCodeBlock())) {
		    lastCodeBlocksTheSame = false;
		}
	    }
	    return lastCodeBlocksTheSame;
	}
	*/





