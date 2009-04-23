package ibis.satin.impl.syncrewriter.analyzer;

import ibis.satin.impl.syncrewriter.analyzer.controlflow.*;
import ibis.satin.impl.syncrewriter.NeverReadException;

import java.util.ArrayList;

import ibis.satin.impl.syncrewriter.Analyzer;
import ibis.satin.impl.syncrewriter.SpawnableMethodCall;
import ibis.satin.impl.syncrewriter.SpawnableMethod;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.verifier.structurals.InstructionContext;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.LoadInstruction;




public class ControlFlow implements Analyzer {



    public InstructionHandle[] proposeSyncInsertion(SpawnableMethod spawnableMethod)
	throws NeverReadException {

	InstructionHandle[] instructionHandles = new InstructionHandle[1];

	Path[] spawnToStorePaths = calculateSpawnToStorePaths(spawnableMethod);

	if (lastCodeBlocksTheSame(spawnToStorePaths)) {
	    CodeBlock lastCodeBlock = spawnToStorePaths[0].getLastCodeBlock();
	    instructionHandles[0] = getEarliestLoadInstruction(lastCodeBlock.getInstructions(), spawnableMethod.getSpawnableCalls());
	    return instructionHandles;
	}
	else {
	    System.out.println("NOT ACCOUNTED FOR YET");
	}

	return instructionHandles;
    }




    /* returns the load instruction that goes with the spawnable call or the
     * last one, if there is no load instruction for this spawnable call in the
     * instructions.
     */
    private InstructionHandle getLoadInstruction(ArrayList<InstructionContext> instructions, SpawnableMethodCall spawnableCall) {
	InstructionHandle resultingInstruction = null;

	for (int i = 0; i < instructions.size(); i++) {
	    InstructionHandle ih = instructions.get(i).getInstruction();
	    try {
		LoadInstruction loadInstruction = 
		    (LoadInstruction) (ih.getInstruction());
		if (loadInstruction.getIndex() == spawnableCall.getResultIndex()) {
		    resultingInstruction = ih;
		}
	    }
	    catch (ClassCastException e) {
	    }
	    if (i == instructions.size() - 1 && resultingInstruction == null) {
		resultingInstruction = ih;
	    }
	}

	return resultingInstruction;
    }


    /* Get the earliest load instruction of the results of the spawnable calls.
     *
     * result1 = spawnableCall();
     * result2 = spawnableCall();
     *
     * read(result2); <---- this is returned
     * read(result1);
     */
    private InstructionHandle getEarliestLoadInstruction(ArrayList<InstructionContext> instructions, ArrayList<SpawnableMethodCall> spawnableCalls) 
	throws NeverReadException {

	InstructionHandle earliestLoadInstruction = null;
	for (SpawnableMethodCall spawnableCall : spawnableCalls) {
	    InstructionHandle loadInstruction = 
		getLoadInstruction(instructions, spawnableCall);
	    if (earliestLoadInstruction == null || loadInstruction.getPosition()
		    < earliestLoadInstruction.getPosition()) {
		earliestLoadInstruction = loadInstruction;
		    }
	}
	return earliestLoadInstruction;
    }


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


    private Path getCommonSubPathFromStart(ArrayList<StoreLoadPath> paths) {
	Path subPath = (Path) paths.get(0);
	for (int i = 1; i < paths.size(); i++) {
	    subPath = subPath.getCommonSubPathFromStart(paths.get(i));
	}
	return subPath;
    }


    private ArrayList<StoreLoadPath> getStoreLoadPaths(ArrayList<Path> endingPaths, SpawnableMethodCall spawnableCall) {
	ArrayList<StoreLoadPath> storeLoadPaths = new ArrayList<StoreLoadPath>();

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


    private Path[] calculateSpawnToStorePaths(SpawnableMethod spawnableMethod) {
	ArrayList<SpawnableMethodCall> spawnableCalls = 
	    spawnableMethod.getSpawnableCalls();
	Path[] spawnToStorePaths = new Path[spawnableCalls.size()];
	CodeBlockGraph codeBlockGraph = new CodeBlockGraph(spawnableMethod);

	for (int i = 0; i < spawnableCalls.size(); i++) {
	    int indexCodeBlock = 
		codeBlockGraph.getIndexCodeBlock(spawnableCalls.get(i).getInstructionHandle());
	    ArrayList<Path> endingPaths = 
		codeBlockGraph.getEndingPathsFrom(indexCodeBlock);
	    System.out.println("Ending paths:");
	    System.out.println(endingPaths);

	    ArrayList<StoreLoadPath> storeLoadPaths = getStoreLoadPaths(endingPaths, spawnableCalls.get(i));
	    System.out.println("StoreLoadPaths:");
	    System.out.println(storeLoadPaths);

	    Path finalPath = getCommonSubPathFromStart(storeLoadPaths);
	    spawnToStorePaths[i] = finalPath;
	}

	return spawnToStorePaths;
    }


}
