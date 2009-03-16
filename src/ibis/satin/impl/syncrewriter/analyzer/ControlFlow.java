package ibis.satin.impl.syncrewriter.analyzer;

import ibis.satin.impl.syncrewriter.analyzer.controlflow.*;
import ibis.satin.impl.syncrewriter.NeverReadException;

import java.util.ArrayList;

import ibis.satin.impl.syncrewriter.Analyzer;
import ibis.satin.impl.syncrewriter.SpawnableMethodCall;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.verifier.structurals.InstructionContext;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.LoadInstruction;




public class ControlFlow implements Analyzer {


    public InstructionHandle[] proposeSyncInsertion(MethodGen methodGen, 
	    ArrayList<SpawnableMethodCall> spawnableCalls) 
	throws NeverReadException {

	InstructionHandle[] instructionHandles = 
	    new InstructionHandle[1];


	CodeBlockGraph codeBlockGraph = new CodeBlockGraph(methodGen);

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
