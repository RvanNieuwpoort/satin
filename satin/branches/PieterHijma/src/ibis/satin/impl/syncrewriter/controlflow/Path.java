package ibis.satin.impl.syncrewriter.controlflow;

import java.util.ArrayList;

import org.apache.bcel.generic.InstructionHandle;


/** A Path is a sequence of {@link BasicBlock}'s. 
 *
 * The basic blocks are in order and each basic block targets the following
 * basic block, except for the last basic block.
 */
public class Path extends ArrayList<BasicBlock> {


    /* public methods */

    /** Constructs an empty path.
     */
    public Path() {
    }


    /** Constructs a path from a path.
     *
     * It creates a shallow copy of the path. 
     */
    public Path(Path path) {
	super((ArrayList<BasicBlock>) path);
    }



    /** Returns the last basic block.
     *
     * @return the last basic block
     */
    public BasicBlock getLastBasicBlock() {
	return get(size() - 1);
    }


    /** Returns a subpath from the beginning to a basic block which has index
     * indexBasicBlock.
     *
     * Note that this is not the index of the basic block in the path (given by
     * get(index), but the index of the basic block in the method.
     * The last basic block will be the basic block with index indexBasicBlock.
     *
     * @param indexBasicBlock the index of the basic block.
     *
     * @return a path from the first basic block until and including the basic
     * block which has an index equal to indexBasicBlock.
     */
    public Path getSubPathIncluding(int indexBasicBlock) {
	for (int i = 0; i < size(); i++) {
	    if (get(i).getIndex() == indexBasicBlock) {
		return (Path) subList(0, i + 1);
	    }
	}
	throw new Error("Codeblock " + indexBasicBlock + " not in this path");
    }


    /** Returns a common subpath between this and another path from the end.
     *
     * The returned path may be empty. Then there is no common subpath.
     *
     * @param path the path with which this path may have common subpath
     * @return the common subpath from the end
     */
    public Path getCommonSubPathFromEnd(Path path) {
	Path commonSubPath = new Path();

	if (path.size() == 0 || size() == 0) {
	    return commonSubPath;
	}
	int i = size() - 1;
	int j = path.size() - 1;
	while (i >= 0 && j >= 0 && get(i).equals(path.get(j))) {
	    commonSubPath.add(0, get(i));
	    i--; 
	    j--;
	}
	return commonSubPath;
    }


    /** Returns a common subpath between this and multiple other paths from the end.
     *
     * The returned path may be empty. Then there is no common subpath.
     *
     * @param paths the paths with which this path may have common subpath
     * @return the common subpath from the end
     */
    public static Path getLatestCommonSubPath(Path[] paths) {
	Path latestCommonSubPath = getCommonSubPathFromEnd(paths);
	if (latestCommonSubPath.size() == 0) {
	    latestCommonSubPath = getCommonSubPathFromStart(paths);
	}

	return latestCommonSubPath;
    }



    /** Returns a common subpath between this and another path from the start.
     *
     * The returned path may be empty. Then there is no common subpath.
     *
     * @param path the path with which this path may have common subpath
     * @return the common subpath from the start
     */
    public Path getCommonSubPathFromStart(Path path) {
	Path commonSubPath = new Path();

	/*
	   System.out.printf("0 < size(): %b\n", 0 < size());
	   System.out.printf("0 < commonSubPath.size(): %b\n", 0 < size());
	   System.out.printf("0 < size(): %b\n", 0 < size());
	   System.out.printf("0 < size(): %b\n", 0 < size());
	   */
	for (int i = 0; i < size() && i < path.size() && get(i).equals(path.get(i)); i++) {
	    commonSubPath.add(get(i));
	}
	return commonSubPath;
    }



    public String toString() {
	StringBuilder sb = new StringBuilder();
	if (size() == 0) return "";
	for (int i = 0; i < size() - 1; i++) {
	    sb.append(get(i).getIndex());
	    sb.append(" ");
	}
	sb.append(get(size() - 1).getIndex());
	return sb.toString();
    }


    /** Removes the last basic block.
     *
     * @param basicBlock the basic block that is being removed. 
     */
    public void removeLast(BasicBlock basicBlock) {
	remove(lastIndexOf(basicBlock));
    }


    public boolean containsBefore(BasicBlock basicBlock, BasicBlock endPoint) {
	int indexBasicBlock = indexOf(basicBlock);
	int indexEndPoint = indexOf(endPoint);
	if (indexEndPoint == -1) throw new Error("endPoint should be part of this path");

	return indexBasicBlock != -1 && indexBasicBlock < indexEndPoint;
    }






    /* package methods */

    int nrOfOccurences(BasicBlock basicBlock) {
	int nrOfOccurences = 0;
	for (BasicBlock i : this) {
	    if (i.equals(basicBlock)) {
		nrOfOccurences++;
	    }
	}

	return nrOfOccurences;
    }




    /* private methods */


    private static Path getCommonSubPathFromEnd(Path[] paths) {
	if (paths.length == 0) {
	    return new Path();
	}
	Path subPath = paths[0];
	for (int i = 1; i < paths.length; i++) {
	    subPath = subPath.getCommonSubPathFromEnd(paths[i]);
	}
	return subPath;
    }


    private static Path getCommonSubPathFromStart(Path[] paths) {
	if (paths.length == 0) {
	    return new Path();
	}
	Path subPath = paths[0];
	for (int i = 1; i < paths.length; i++) {
	    subPath = subPath.getCommonSubPathFromStart(paths[i]);
	}
	return subPath;
    }
}
