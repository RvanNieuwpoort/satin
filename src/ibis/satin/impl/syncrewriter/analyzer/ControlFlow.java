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
	    ArrayList<StoreLoadPath> storeLoadPaths = getStoreLoadPaths(endingPaths, spawnableCalls.get(i));
	    Path finalPath = getCommonSubPathFromStart(storeLoadPaths);
	    spawnToStorePaths[i] = finalPath;
	}

	return spawnToStorePaths;
    }















    public InstructionHandle[] proposeSyncInsertion2(SpawnableMethod spawnableMethod) 
	throws NeverReadException {

	ArrayList<SpawnableMethodCall> spawnableCalls = spawnableMethod.getSpawnableCalls();

	InstructionHandle[] instructionHandles = 
	    new InstructionHandle[1];


	CodeBlockGraph codeBlockGraph = new CodeBlockGraph(spawnableMethod);

	if (spawnableCalls.size() > 1) {
	    SpawnableMethodCall firstSpawnableCall = spawnableCalls.get(0);
	    int indexCodeBlock = 
		codeBlockGraph.getIndexCodeBlock(firstSpawnableCall.getInstructionHandle());
	    for (int i = 1; i < spawnableCalls.size(); i++) {
		SpawnableMethodCall spawnableCall = spawnableCalls.get(i);
		int tmpIndexCodeBlock = 
		    codeBlockGraph.getIndexCodeBlock(spawnableCall.getInstructionHandle());
		if (tmpIndexCodeBlock != indexCodeBlock) {
		    System.out.printf("the following case is unaccounted for:\n%s\n",
			    "\tmultiple spawnable calls, but not in the same control flow situation");
		    throw new NeverReadException();
		}
		CodeBlock cb = codeBlockGraph.getCodeBlock(indexCodeBlock);
		instructionHandles[0] = cb.getEnd().getInstruction();
	    }
	}
	else {
	    SpawnableMethodCall spawnableCall = spawnableCalls.get(0);
	    System.out.println(spawnableCall);
	    int indexCodeBlock = 
		codeBlockGraph.getIndexCodeBlock(spawnableCall.getInstructionHandle());
	    ArrayList<Path> endingPaths = 
		codeBlockGraph.getEndingPathsFrom(indexCodeBlock);
	    System.out.println("endingPaths:");
	    for (Path path : endingPaths) System.out.println(path);
	    ArrayList<StoreLoadPath> storeLoadPaths = 
		new ArrayList<StoreLoadPath>();
	    System.out.println("storeLoadPaths:");
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
		    System.out.println(storeLoadPath);
		}
	    }

	    Path finalPath = (Path) storeLoadPaths.get(0);
	    for (int i = 1; i < storeLoadPaths.size(); i++) {
		finalPath = finalPath.getCommonSubPathFromStart(
			(Path)(storeLoadPaths.get(i)));
	    }

	    CodeBlock lastCodeBlock = finalPath.get(finalPath.size() - 1);
	    ArrayList<InstructionContext> instructions = lastCodeBlock.getInstructions();
	    for (int i = 0; i < instructions.size(); i++) {
		InstructionHandle ih = instructions.get(i).getInstruction();
		try {
		    LoadInstruction loadInstruction = 
			(LoadInstruction) (ih.getInstruction());
		    if (loadInstruction.getIndex() == spawnableCall.getResultIndex()) {
			instructionHandles[0] = ih.getPrev();
		    }
		}
		catch (ClassCastException e) {
		}
		if (i == instructions.size() - 1 && instructionHandles[0] == null) {
		    instructionHandles[0] = ih;
		}
	    }




	}
	return instructionHandles;
    }
}













/* Get the load instruction corresponding to this spawnable call.
*/
/*
   private InstructionHandle getLoadInstruction(SpawnableMethodCall spawnableCall) throws NeverReadException {
   InstructionHandle ih = spawnableCall.getInstructionHandle();
   while ((ih = ih.getNext()) != null) {
   try {
   LoadInstruction loadInstruction = 
   (LoadInstruction) (ih.getInstruction());
   if (loadInstruction.getIndex() == 
   spawnableCall.getResultIndex()) {
   return ih;
   }
   }
   catch (ClassCastException e) {
   }
   }
   throw new NeverReadException();
   }
   */


