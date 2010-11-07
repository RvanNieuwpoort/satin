package ibis.satin.impl.syncrewriter.analyzer;

import ibis.satin.impl.syncrewriter.Analyzer;
import ibis.satin.impl.syncrewriter.SpawnableCall;
import ibis.satin.impl.syncrewriter.SpawningMethod;
import ibis.satin.impl.syncrewriter.SyncInsertionProposalFailure;
import ibis.satin.impl.syncrewriter.analyzer.controlflow.SpawnableCallAnalysis;
import ibis.satin.impl.syncrewriter.analyzer.controlflow.StoreLoadPath;
import ibis.satin.impl.syncrewriter.controlflow.BasicBlock;
import ibis.satin.impl.syncrewriter.controlflow.BasicBlockGraph;
import ibis.satin.impl.syncrewriter.controlflow.Path;
import ibis.satin.impl.syncrewriter.util.Debug;

import java.util.ArrayList;

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.INVOKESTATIC;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.verifier.structurals.InstructionContext;




/** An implementation of an Analyzer analyzing with control flow awareness.
 */
public class ControlFlow implements Analyzer {



    /* private classes */

    private class PathPartOfLoopException extends Exception { 
        private static final long serialVersionUID = 1L;
        private static final String MESSAGE = "Sync insertion is part of a loop"; 
	private PathPartOfLoopException() { 
	    super(MESSAGE); 
	} 
    }


    private class ResultNotLoadedException extends Exception { 
        private static final long serialVersionUID = 1L;
	private static final String MESSAGE = "The result is not loaded"; 
	private ResultNotLoadedException() { 
	    super(MESSAGE); 
	} 
    }



    private Debug d;



    /* public methods */

    /** Proposes instructionHandles before which a sync should be inserted.
     *
     * If the result of a spawnable method is not stored, but given to a method
     * for example, this analyzer can't do its job and it will propose syncs
     * right after the spawnable calls.
     *
     * It tries to find all store-to-load paths for every spawnable call and
     * will then try to find the earliest load in the last basic blocks of
     * those store-to-load paths. 
     *
     * If there are other spawnable calls after the earliest load but within
     * the same basic blocks, it will try to find again the earliest loads for
     * these spawnable calls.
     *
     * @param spawningMethod The spawnable method in which syncs should be
     * inserted.
     * @param d A debug instance for logging error, warning and debug messages.
     */
    public InstructionHandle[] proposeSyncInsertion(SpawningMethod
	    spawningMethod, Debug d) throws SyncInsertionProposalFailure {

	this.d = d;

	if (needsImmediateSyncInsertion(spawningMethod)) { 
	    d.warning("Some results are not stored, defaulting to no parallelism\n");
	    return getImmediateSyncs(spawningMethod); 
	}

	d.log(0, "proposing basic blocks\n"); 
	
	BasicBlock[] proposedBasicBlocks;
    proposedBasicBlocks = proposeBasicBlocks(spawningMethod);
 
	d.log(0, "proposed basic blocks:\n"); d.log(1, "%s\n", toString(proposedBasicBlocks));

	return getEarliestLoadInstructions(proposedBasicBlocks,
		spawningMethod);

	    }




    /* private methods */

    /* remove spawnable calls before instruction handle ih
     */
    private void removeSpawnableCallsBefore(InstructionHandle ih,
	    ArrayList<SpawnableCall> spawnableCalls, 
	    BasicBlock basicBlock) {

	ArrayList<SpawnableCall> spawnableCallsClone = new
	    ArrayList<SpawnableCall>(spawnableCalls); 
	for (SpawnableCall spawnableCall : spawnableCallsClone) { 
	    InstructionHandle spawnInstructionHandle = 
		spawnableCall.getInvokeInstruction();
	    if (basicBlock.contains(spawnInstructionHandle)) { 
		if (spawnInstructionHandle.getPosition() < ih.getPosition()) {
		    spawnableCalls.remove(spawnableCall); 
		} 
	    } else {
		spawnableCalls.remove(spawnableCall); 
	    } 
	} 
    }


    /* Add the load instructions of spawnable calls that are invoked AFTER the
     * already found earliest load. 
     *
     * If there are spawnable calls within a basic block right after a load for
     * another spawnable call in the basic block, then there should be a sync
     * statement for these spawnable calls as well. 
     * So add those earliest loads as well.
     *
     * result1 = spawningMethod();
     * result2 = spawningMethod();
     *
     * read(result2);			// this is the earliest load
     * result3 = spawningMethod();
     *
     * read(result3);			// this is also an earliest load,
     *					// this is the one that is returned
     */
    private void addLoadsWithSpawnableCallsAfter(InstructionHandle earliestLoad, 
	    BasicBlock basicBlock, SpawningMethod spawningMethod,
	    ArrayList<SpawnableCall> spawnableCalls, 
	    ArrayList<InstructionHandle> earliestLoads) {

	ArrayList<SpawnableCall> spawnableCallsCopy = new ArrayList<SpawnableCall>(spawnableCalls);
	removeSpawnableCallsBefore(earliestLoad, spawnableCallsCopy, basicBlock);

	while (spawnableCallsCopy.size() > 0) {
	    d.log(1, "there are spawnable calls after the earliest load, trying to get the earliest load for these spawnable calls\n");
	    try {
		earliestLoad = getEarliestLoadInstruction(basicBlock, 
			spawnableCallsCopy, spawningMethod);
		earliestLoads.add(earliestLoad);
		removeSpawnableCallsBefore(earliestLoad, spawnableCallsCopy, basicBlock);
	    }
	    catch (ResultNotLoadedException e) {
		d.log(1, "there are no loads associated with these last spawnable calls\n");
		spawnableCallsCopy.removeAll(spawnableCallsCopy);
	    }
	}
    }


    /* Get the index of the instruction after the invoke instruction of the
     * spawnable call. 
     *
     * if the spawnable call isn't invoked, 0, the first instruction is
     * returned.
     */
    private int getIndexAfterSpawnableCall(ArrayList<InstructionContext> instructions, SpawnableCall spawnableCall) {
	for (int i = 0; i < instructions.size(); i++) {
	    InstructionHandle ih = instructions.get(i).getInstruction();
	    if (ih.equals(spawnableCall.getInvokeInstruction())) {
		return i;
	    }
	}
	return 0;// the invoke is not found, so the first instruction
    }


    /* Get the load instruction from a basic block associated with a spawnable
     * call. 
     *
     * If the spawnable call is invoked in this basic block, search after this
     * spawnable call. 
     * Array stores and putfield methods are not seen as load instructions.
     *
     * An ibis.satin.SatinObject.pause() instruction is regarded as a load.
     */
    private InstructionHandle getLoadInstruction(BasicBlock basicBlock, SpawnableCall spawnableCall, 
	    SpawningMethod spawningMethod) throws ResultNotLoadedException {
	if (!spawnableCall.exceptionsHandled()) {
	    throw new ResultNotLoadedException();
	}
	ConstantPoolGen cp = spawningMethod.getConstantPool();
	ArrayList<InstructionContext> instructions = basicBlock.getInstructions();
	int indexAfterSpawnableCall = getIndexAfterSpawnableCall(instructions, spawnableCall);
    if (spawnableCall.resultMayHaveAliases()) {
        InstructionHandle[] consumers = spawningMethod.findInstructionConsumers(spawnableCall.getInvokeInstruction());
        for (InstructionHandle h : consumers) {
            for (InstructionContext c : instructions) {
                if (c.getInstruction() == h) {
                    return h;
                }
            }
        }
        return null;
    }
	
	for (int i = indexAfterSpawnableCall; i < instructions.size(); i++) {
	    InstructionHandle ih = instructions.get(i).getInstruction();
	    try {
		INVOKESTATIC pauseInstruction = 
		    (INVOKESTATIC) (ih.getInstruction());
		if (pauseInstruction.getMethodName(cp).equals("pause") &&
			pauseInstruction.getType(cp).getSignature().equals("V") &&
			pauseInstruction.getClassName(cp).equals("ibis.satin.SatinObject")) {
		    return ih;
		}
	    }
	    catch (ClassCastException e) {
	    }
	    try {
		LoadInstruction loadInstruction = 
		    (LoadInstruction) (ih.getInstruction());
		if (spawnableCall.storesIn(loadInstruction.getIndex()) && 
			!spawningMethod.isUsedForArrayStore(ih) &&
			!spawningMethod.isUsedForPutField(ih)) {  
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
     * This is done for a single basic block.
     *
     * result1 = spawnableCall();
     * result2 = spawnableCall();
     *
     * read(result2); <---- this is returned
     * read(result1);
     */
    private InstructionHandle getEarliestLoadInstruction(BasicBlock basicBlock, 
	    ArrayList<SpawnableCall> spawnableCalls, SpawningMethod spawningMethod) throws ResultNotLoadedException {

	InstructionHandle earliestLoadInstruction = null;

	d.log(2, "looking for load instruction for:\n"); 
	for (SpawnableCall spawnableCall : spawnableCalls) {
	    d.log(3, "spawnable call: %s\n", spawnableCall);
	    try {
		InstructionHandle loadInstruction = getLoadInstruction(basicBlock, spawnableCall, spawningMethod);
		d.log(4, "found load or pause() instruction %s on position %d\n", loadInstruction, loadInstruction.getPosition());
		if (earliestLoadInstruction == null
			|| loadInstruction.getPosition() < earliestLoadInstruction.getPosition()) {
		    earliestLoadInstruction = loadInstruction;
			}
	    }
	    catch (ResultNotLoadedException e) {
		d.log(4, "result of spawnable call not loaded and no pause() found\n");
	    }
	}
	if (earliestLoadInstruction == null) {
	    d.log(2, "didn't find loadinstructions\n");
	    throw new ResultNotLoadedException();
	}
	d.log(2, "earliest load instruction: %s\n", earliestLoadInstruction);
	return earliestLoadInstruction;
    }


    /* Try to get the earliest load in a basic block.
     *
     * If no earliest load is found, default to the last instruction of the
     * basic block.
     */
    private ArrayList<InstructionHandle> tryEarliestLoadInstructions(BasicBlock basicBlock, SpawningMethod spawningMethod) {
	ArrayList<SpawnableCall> spawnableCalls = spawningMethod.getSpawnableCalls();
	ArrayList<InstructionHandle> earliestLoads = new ArrayList<InstructionHandle>();

	try {
	    d.log(1, "trying to get the earliest load associated with spawnable calls in basic block %d\n", basicBlock.getId());
	    InstructionHandle earliestLoad =  getEarliestLoadInstruction(basicBlock, 
		    spawnableCalls, spawningMethod);
	    earliestLoads.add(earliestLoad);
	    d.log(1, "succeeded to get the earliest load\n");

	    // it is not over yet, there could be spawnable calls after the
	    // earliest load.
	    addLoadsWithSpawnableCallsAfter(earliestLoad, basicBlock, spawningMethod, spawnableCalls, earliestLoads);
	}
	catch (ResultNotLoadedException e) {
	    InstructionHandle lastInstruction = basicBlock.getEnd().getInstruction();
	    d.log(1, "result is not loaded in this basic block, defaulting to last instruction of the basic block\n");
	    earliestLoads.add(lastInstruction);
	    return earliestLoads;
	}

	return earliestLoads;
    }


    /* Get earliest load instructions in basic blocks 
     */
    private InstructionHandle[] getEarliestLoadInstructions(BasicBlock[] basicBlocks, SpawningMethod spawningMethod) {
	ArrayList<InstructionHandle> bestInstructionHandles = new ArrayList<InstructionHandle>();

	d.log(0, "trying to get the earliest load instructions\n"); 
	for (int i = 0; i < basicBlocks.length; i++) {
	    bestInstructionHandles.addAll(tryEarliestLoadInstructions(basicBlocks[i], spawningMethod));
	}

	InstructionHandle[] array = new InstructionHandle[bestInstructionHandles.size()];
	return bestInstructionHandles.toArray(array);
    }





    /* printing an array of basic blocks */

    private String toString(BasicBlock[] basicBlocks) {
	if (basicBlocks.length == 0) return "";
	StringBuilder sb = new StringBuilder();
	for (int i = 0; i < basicBlocks.length - 1; i++) {
	    sb.append(basicBlocks[i].getId());
	    sb.append(", ");
	}
	sb.append(basicBlocks[basicBlocks.length - 1].getId());
	return sb.toString();
    }





    /* proposing a basic block */

    private ArrayList<BasicBlock> removeDuplicates(ArrayList<BasicBlock> basicBlocks) {
	ArrayList<BasicBlock> withoutDuplicates = new ArrayList<BasicBlock>();
	for (BasicBlock basicBlock : basicBlocks) {
	    if (!withoutDuplicates.contains(basicBlock)) {
		withoutDuplicates.add(basicBlock);
	    }
	}
	return withoutDuplicates;
    }


    private boolean hasBasicBlockBefore(BasicBlock endPointPath, Path storeLoadPath, ArrayList<BasicBlock> basicBlocks) {
	for (BasicBlock basicBlock : basicBlocks) {
	    if (storeLoadPath.containsBefore(basicBlock, endPointPath)) {
		return true;
	    }
	}
	return false;
    }


    /* filters away the basic blocks that can be reached by other basic blocks
     * in any case */
    private ArrayList<BasicBlock> filter(ArrayList<BasicBlock> basicBlocks, ArrayList<Path> storeLoadPaths) {
	ArrayList<BasicBlock> filteredBasicBlocks = new ArrayList<BasicBlock>();
	BasicBlock[] basicBlocksArray = new BasicBlock[basicBlocks.size()];
	basicBlocksArray = basicBlocks.toArray(basicBlocksArray);

	for (int i = 0; i < basicBlocks.size(); i++) {
	    if (hasBasicBlockBefore(basicBlocks.get(i), storeLoadPaths.get(i), basicBlocks)) {
		basicBlocksArray[i] = null;
	    }
	}
	for (int i = 0; i < basicBlocksArray.length; i++) {
	    if (basicBlocksArray[i] != null) {
		filteredBasicBlocks.add(basicBlocksArray[i]);
	    }
	}

	return filteredBasicBlocks;
    }


    /* Get the best basic blocks from paths.
     *
     * The best basic block can be the last basic block that is not part of a
     * loop, otherwise the last basic block of the path.
     */
    private ArrayList<BasicBlock> getBestBasicBlocks(ArrayList<Path> storeLoadPaths, BasicBlockGraph basicBlockGraph) {
	ArrayList<BasicBlock> basicBlocks = new ArrayList<BasicBlock>();
	for (int i = 0; i < storeLoadPaths.size(); i++) {
	    basicBlocks.add(getBestBasicBlock(basicBlockGraph, storeLoadPaths.get(i)));
	}

	return basicBlocks;
    }


    private BasicBlock getLastBasicBlockNotPartOfLoop(BasicBlockGraph basicBlockGraph, Path path) throws PathPartOfLoopException {
	for (int i = path.size() - 1; i >= 0; i--) {
	    BasicBlock basicBlock = path.get(i);
	    if (!basicBlockGraph.isPartOfLoop(basicBlock.getId())) {
		return basicBlock;
	    }
	}
	throw new PathPartOfLoopException();
    }


    /* Get the best basic block from a path.
     *
     * This can be the last basic block that is not part of a loop, otherwise
     * the last basic block of the path.
     */
    private BasicBlock getBestBasicBlock(BasicBlockGraph basicBlockGraph, Path path) {
	try {
	    BasicBlock lastBasicBlockNotPartOfLoop = 
		getLastBasicBlockNotPartOfLoop(basicBlockGraph, path);
	    d.log(1, "the last basic block not part of a loop: %d\n", lastBasicBlockNotPartOfLoop.getId());
	    return lastBasicBlockNotPartOfLoop;
	}
	catch (PathPartOfLoopException e) {
	    BasicBlock lastBasicBlock = path.getLastBasicBlock(); 
	    d.warning("taking last basic block %d, but it is part of a loop\n", lastBasicBlock.getId());
	    return lastBasicBlock;
	}
    }


    private void addStoreLoadPaths(ArrayList<StoreLoadPath> storeLoadPaths, ArrayList<Path> allStoreLoadPaths) {
	for (StoreLoadPath storeLoadPath : storeLoadPaths) {
	    if (!allStoreLoadPaths.contains(storeLoadPath)) {
		allStoreLoadPaths.add(storeLoadPath);
	    }
	}
    }


    private void addPaths(ArrayList<Path> storeLoadPaths, ArrayList<Path> allStoreLoadPaths) {
	for (Path storeLoadPath : storeLoadPaths) {
	    if (!allStoreLoadPaths.contains(storeLoadPath)) {
		allStoreLoadPaths.add(storeLoadPath);
	    }
	}
    }


    private ArrayList<Path> getStoreLoadPaths(SpawnableCallAnalysis[] spawnableCallAnalyses) {
	ArrayList<Path> allStoreLoadPaths = new ArrayList<Path>();
	for (SpawnableCallAnalysis spawnableCallAnalysis : spawnableCallAnalyses) {
	    ArrayList<StoreLoadPath> storeLoadPaths = spawnableCallAnalysis.getStoreLoadPaths();
	    if (storeLoadPaths.size() == 0) {
		addPaths(spawnableCallAnalysis.getEndingPaths(), allStoreLoadPaths);
	    }
	    else {
		addStoreLoadPaths(storeLoadPaths, allStoreLoadPaths);
	    }
	}

	return allStoreLoadPaths;
    }


    private SpawnableCallAnalysis[] getSpawnableCallAnalyses(ArrayList<SpawnableCall> spawnableCalls,
	    BasicBlockGraph basicBlockGraph) {
	SpawnableCallAnalysis[] spawnableCallAnalyses = new SpawnableCallAnalysis[spawnableCalls.size()];

	for (int i = 0; i < spawnableCalls.size(); i++) {
	    spawnableCallAnalyses[i] = new SpawnableCallAnalysis(spawnableCalls.get(i), basicBlockGraph, new Debug(d.turnedOn(), d.getStartLevel() + 1));
	}

	return spawnableCallAnalyses;
    }


    /* proposes basic blocks to put syncs into
     *
     * create spawnable call analyses
     *	    will create store-to-load paths
     * from all analyses all store to load paths, duplicates removed
     *
     * Try to get the best basic block, block not within a loop
     * Filter out the basic blocks that always can be reached by a previous
     *	    block
     * Again remove duplicates
     */
    private BasicBlock[] proposeBasicBlocks(SpawningMethod spawningMethod) 
	throws SyncInsertionProposalFailure {

	ArrayList<SpawnableCall> spawnableCalls = 
	    spawningMethod.getSpawnableCalls();
	BasicBlockGraph basicBlockGraph = new BasicBlockGraph(spawningMethod);

	d.log(1, "analyzing spawnable calls\n");
	SpawnableCallAnalysis[] spawnableCallAnalyses = getSpawnableCallAnalyses(spawnableCalls, basicBlockGraph);

	d.log(1, "all store load paths:\n");
	ArrayList<Path> allStoreLoadPaths = getStoreLoadPaths(spawnableCallAnalyses);
	d.log(2, "%s\n", allStoreLoadPaths);

	if (allStoreLoadPaths.size() == 1) {
	    BasicBlock[] basicBlocks = new BasicBlock[1];
	    basicBlocks[0] =  getBestBasicBlock(basicBlockGraph, allStoreLoadPaths.get(0));
	    return basicBlocks;
	}
	else {
	    ArrayList<BasicBlock> allBasicBlocks = getBestBasicBlocks(allStoreLoadPaths, basicBlockGraph);
	    ArrayList<BasicBlock> filteredBasicBlocks = filter(allBasicBlocks, allStoreLoadPaths);
	    ArrayList<BasicBlock> duplicatesRemoved = removeDuplicates(filteredBasicBlocks);
	    BasicBlock[] result = new BasicBlock[duplicatesRemoved.size()];
	    return duplicatesRemoved.toArray(result);
	}
    }







    /* handle immediate syncs for when the result of a spawnable call is not stored */

    private InstructionHandle[] getImmediateSyncs(SpawningMethod spawningMethod) {
	ArrayList<SpawnableCall> spawnableCalls = spawningMethod.getSpawnableCalls();
	InstructionHandle[] immediateSyncs = new InstructionHandle[spawnableCalls.size()];
	for (int i = 0; i < immediateSyncs.length; i++) {
	    SpawnableCall spawnableCall = spawnableCalls.get(i);
	    immediateSyncs[i] = spawnableCall.getInvokeInstruction().getNext();
	}
	return immediateSyncs;
    }


    private boolean needsImmediateSyncInsertion(SpawningMethod spawningMethod) {
	ArrayList<SpawnableCall> spawnableCalls = spawningMethod.getSpawnableCalls();
	for (SpawnableCall spawnableCall : spawnableCalls) {
	    if (!spawnableCall.resultIsStored()) {
		return true;
	    }
	}
	return false;
    }










    /* unused code.
     * Mainly used for finding overlapping paths. This does lead to less sync
     * insertions, but has worse performance than everything above.
     */

    /*
    private BasicBlock[] getBestBasicBlocks(SpawnableCallAnalysis[] spawnableCallAnalyses, BasicBlockGraph basicBlockGraph) {
	BasicBlock[] basicBlocks = new BasicBlock[spawnableCallAnalyses.length];
	for (int i = 0; i < basicBlocks.length; i++) {
	    basicBlocks[i] = getBestBasicBlock(basicBlockGraph, spawnableCallAnalyses[i].getLatestCommonSubPath());
	}

	return basicBlocks;
    }


    private String toString(Path[] paths) {
	if (paths.length == 0) return "[]";
	StringBuilder sb = new StringBuilder("[");
	for (int i = 0; i < paths.length - 1; i++) {
	    sb.append(paths[i]);
	    sb.append(", ");
	}
	sb.append(paths[paths.length - 1]);
	sb.append("]");
	return sb.toString();
    }


    private Path getLatestCommonSubPath(SpawnableCallAnalysis[] spawnableCallAnalyses) 
	throws SyncInsertionProposalFailure {
	Path[] latestCommonSubPaths = new Path[spawnableCallAnalyses.length];
	for (int i = 0; i < spawnableCallAnalyses.length; i++) {
	    latestCommonSubPaths[i] = spawnableCallAnalyses[i].getLatestCommonSubPath();
	}

	d.log(1, "the latest common subpaths for all spawnable calls combined:\n");
	d.log(2, toString(latestCommonSubPaths));

	Path latestCommonSubPath = Path.getLatestCommonSubPath(latestCommonSubPaths);

	d.log(1, "the final latest subpath from all spawnable calls combined:\n");
	d.log(2, "%s\n", latestCommonSubPath);

	return latestCommonSubPath;
    }


    private BasicBlock[] getLastBasicBlocks(ArrayList<StoreLoadPath> paths) {
	BasicBlock[] basicBlocks = new BasicBlock[paths.size()];
	for (int i = 0; i < paths.size(); i++) {
	    basicBlocks[i] = paths.get(i).getLastBasicBlock();
	}

	return basicBlocks;
    }
    */
}
