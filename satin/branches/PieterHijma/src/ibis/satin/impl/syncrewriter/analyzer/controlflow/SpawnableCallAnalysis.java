package ibis.satin.impl.syncrewriter.analyzer.controlflow;

import java.util.ArrayList;
import ibis.satin.impl.syncrewriter.util.Debug;
import ibis.satin.impl.syncrewriter.SpawnableCall;
import ibis.satin.impl.syncrewriter.controlflow.*;

import org.apache.bcel.generic.InstructionHandle;

/** This class represents an analysis of a spawnable call.
 */
public class SpawnableCallAnalysis {


    private Debug d;


    private ArrayList<Path> endingPaths;
    private ArrayList<StoreLoadPath> storeLoadPaths;
    private Path latestCommonSubPath;


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
	d.log(1, "analyzing spawnable call: %s at basic block %d\n", spawnableCall, idBasicBlock);

	endingPaths = basicBlockGraph.getEndingPathsFrom(idBasicBlock);
	d.log(2, "ending paths from %d:\n", idBasicBlock);
	d.log(3, "%s\n", endingPaths);

	storeLoadPaths = getStoreLoadPaths(endingPaths, spawnableCall);
	d.log(2, "paths from store to load:");
	d.log(3, "%s\n", storeLoadPaths);


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
	d.log(1, "analyzed spawnable call: %s\n", spawnableCall);
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
}
