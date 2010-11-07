package ibis.satin.impl.syncrewriter.analyzer.controlflow;

import ibis.satin.impl.syncrewriter.SpawnableCall;
import ibis.satin.impl.syncrewriter.bcel.MethodGen;
import ibis.satin.impl.syncrewriter.controlflow.BasicBlock;
import ibis.satin.impl.syncrewriter.controlflow.BasicBlockGraph;
import ibis.satin.impl.syncrewriter.controlflow.Path;
import ibis.satin.impl.syncrewriter.util.Debug;

import java.util.ArrayList;
import java.util.Set;

import org.apache.bcel.classfile.LineNumberTable;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.PUTFIELD;

/** This class represents an analysis of a spawnable call.
 */
public class SpawnableCallAnalysis {


    private Debug d;


    private ArrayList<Path> endingPaths;
    private ArrayList<StoreLoadPath> storeLoadPaths;
    private Path latestCommonSubPath;

    private boolean resultMayHaveAliases = false;    

    /* public methods */

    /** Instantiates a spawnable call analysys from a spawnable call and a
     * graph of basic blocks.
     *
     * @param spawnableCall The spawnable call to be analyzed.
     * @param basicBlockGraph The basic block graph that helps analyzing.
     * @param d A debug instance to write debug information to.
     */
    public SpawnableCallAnalysis(SpawnableCall spawnableCall, BasicBlockGraph basicBlockGraph, Debug d) {
	this.d = d;

	int idBasicBlock = basicBlockGraph.getIdBasicBlock(spawnableCall.getInvokeInstruction());
	BasicBlock callerBlock = basicBlockGraph.getBasicBlock(idBasicBlock);
	
	this.d.log(1, "analyzing spawnable call: %s at basic block %d\n", spawnableCall, idBasicBlock);
	
	    InstructionHandle invoker = spawnableCall.getInvokeInstruction();
	
        MethodGen mg = callerBlock.getMethodGen();
        InstructionHandle[] stackConsumers = mg.findInstructionConsumers(invoker);
        
        LineNumberTable t = mg.getLineNumberTable(mg.getConstantPool());
        
        int spawnLineno = t.getSourceLine(invoker.getPosition());
        
        String className = mg.getClassName();
        
        for (InstructionHandle consumer : stackConsumers) {
            int indexToCheck = indexToCheckForAlias(consumer, mg);
            // if the store is an array store or object field store,
            // we need to make sure that it is not aliased. --Ceriel
            // Fairly cheap check could be: the object is local, allocated in the
            // spawning method, and no alias is done. This may, however, be a bit
            // too restrictive. On the other hand, allowing for more makes the check
            // much more difficult. --Ceriel
            if (indexToCheck >= 0) {
                // So, here we have a spawn that stores into an object field or an
                // array element.
                d.log(0, "spawn stores in array element or object field, index " + indexToCheck);
                if (mg.isParameter(indexToCheck)) {
                    // The object is indicated by a parameter. We give up.
                    d.warning("The result of the spawn at line " + spawnLineno + " of class " + className 
                            + " is stored in an object given as parameter to "
                            + "the spawning method. This case is not handled by the sync inserter/adviser. "
                            + "The resulting sync placement is likely not optimal (to put it lightly).");
                    resultMayHaveAliases = true;
                    break;
                }
            
                // Here, the object is indicated by a local variable. This is OK if
                // 1. There is no load of this local variable present in front of the spawn.
                // 2. The object is allocated in the spawning method, and the result of the allocation
                //    is stored in this local variable.
                Set<BasicBlock> predecessors = callerBlock.getAllPredecessors();
                for (BasicBlock b : predecessors) {
                    LoadAwareBasicBlock lb = new LoadAwareBasicBlock(b);
                    if (lb.containsLoadWithIndex(indexToCheck) || ! lb.noAliasesStoreWithIndex(indexToCheck)) {
                        d.warning("The result of the spawn at line " + spawnLineno + " of class " + className 
                                + " is stored in an object that may have aliases. "
                                + "The resulting sync placement is likely not optimal (to put it lightly).");
                        resultMayHaveAliases = true;
                        break;
                    }
                }
                LoadAwareBasicBlock lb = new LoadAwareBasicBlock(callerBlock);
                if (lb.containsLoadWithIndexBefore(invoker, indexToCheck) || ! lb.noAliasesStoreWithIndexBefore(invoker, indexToCheck)) {
                    d.warning("The result of the spawn at line " + spawnLineno + " of class " + className 
                            + " is stored in an object that may have aliases. "
                            + "The resulting sync placement is likely not optimal (to put it lightly).");
                    resultMayHaveAliases = true;
                    break;
                }
            }
        }

        if (resultMayHaveAliases) {
            spawnableCall.setResultMayHaveAliases(true);
            endingPaths = null;
            storeLoadPaths = new ArrayList<StoreLoadPath>();
            storeLoadPaths.add(new StoreLoadPath(spawnableCall.getInvokeInstruction(), callerBlock, spawnableCall.getIndicesStores()));
        }

        if (! resultMayHaveAliases) {
            endingPaths = basicBlockGraph.getEndingPathsFrom(idBasicBlock);
            this.d.log(2, "ending paths from %d:\n", idBasicBlock);
            this.d.log(3, "%s\n", endingPaths);

            storeLoadPaths = getStoreLoadPaths(endingPaths, spawnableCall);
            this.d.log(2, "paths from store to load:");
            this.d.log(3, "%s\n", storeLoadPaths);
        }
	
        // The spawnable call analysis analyzes a bit more than is necessary
        // for the current syncrewriter. It keeps doing it, but it is not used,
        // so no debug information about this.
        if (storeLoadPaths.size() == 0) {
            /*
	    d.warning("The result of spawnable call %s is never read, %s\n", spawnableCall,
		    "defaulting to the latest common subpath of all ending paths");
             */
            Path[] endingPathsArray = new Path[endingPaths.size()];
            latestCommonSubPath = 
                Path.getLatestCommonSubPath(endingPaths.toArray(endingPathsArray));
        }
        else {
            Path[] storeLoadPathsArray = new Path[storeLoadPaths.size()];
            latestCommonSubPath = 
                Path.getLatestCommonSubPath(storeLoadPaths.toArray(storeLoadPathsArray));
        }
        /*
	d.log(2, "latest common subpath of all store to load paths:\n");
	d.log(3, "%s\n", latestCommonSubPath);
         */
        this.d.log(1, "analyzed spawnable call: %s\n", spawnableCall);
    }

    private int indexToCheckForAlias(InstructionHandle ih, MethodGen mg) {
        if (mg.isArrayStore(ih.getInstruction()) || ih.getInstruction() instanceof PUTFIELD) {
            ih  = mg.getObjectReferenceLoadInstruction(ih);
            ALOAD objectLoadInstruction = (ALOAD) ih.getInstruction();
            // The 'not an ALOAD' case is already dealt with.
            return objectLoadInstruction.getIndex();
        }
        return -1;
    }
    
    /** Returns the latest common subpath for store load paths. 
     *
     * If there are no store-to-load paths, the latest common subpaths of all
     * ending paths is taken.
     *
     * @return The latest common subpath.
     */
    public Path getLatestCommonSubPath() {
	return latestCommonSubPath;
    }


    /** Returns the store-to-load paths for this spawnable call.
     *
     * @return The store to load paths
     */
    public ArrayList<StoreLoadPath> getStoreLoadPaths() {
	return storeLoadPaths;
    }

    /** Returns all the ending paths from the spawnable call.
     *
     * @return the ending paths.
     */
    public ArrayList<Path> getEndingPaths() {
	return endingPaths;
    }



    /* private methods */

    private void fillStoreLoadPaths(ArrayList<StoreLoadPath> storeLoadPaths, Path path, SpawnableCall spawnableCall) {
	Integer[] indicesStores = spawnableCall.getIndicesStores();
	InstructionHandle invokeInstruction = spawnableCall.getInvokeInstruction();

	if (!spawnableCall.exceptionsHandled()) {
	    return;
	}
	else {
	    StoreLoadPath storeLoadPath = null;
	    try {
		storeLoadPath = new StoreLoadPath(invokeInstruction, path, indicesStores);
	    }
	    catch (NeverReadException e) {
	    }
	    if (storeLoadPath != null && !storeLoadPaths.contains(storeLoadPath)) {
		storeLoadPaths.add(storeLoadPath);
	    }
	}
    }



    private ArrayList<StoreLoadPath> getStoreLoadPaths(ArrayList<Path> 
	    endingPaths, SpawnableCall spawnableCall) {

	ArrayList<StoreLoadPath> storeLoadPaths = 
	    new ArrayList<StoreLoadPath>();

	for (Path path : endingPaths) {
	    fillStoreLoadPaths(storeLoadPaths, path, spawnableCall);
	}

	return storeLoadPaths;
	    }
    
    public boolean mayHaveAliases() {
        return resultMayHaveAliases;
    }
}
