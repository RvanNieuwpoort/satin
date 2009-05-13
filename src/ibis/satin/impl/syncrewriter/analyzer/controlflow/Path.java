package ibis.satin.impl.syncrewriter.analyzer.controlflow;

import java.util.ArrayList;

import org.apache.bcel.generic.InstructionHandle;


public class Path extends ArrayList<BasicBlock> {


    /* public methods */

    public Path() {
    }


    public BasicBlock getLastBasicBlock() {
	return get(size() - 1);
    }


    public Path getSubPathIncluding(int indexBasicBlock) {
	for (int i = 0; i < size(); i++) {
	    if (get(i).getIndex() == indexBasicBlock) {
		return (Path) subList(0, i + 1);
	    }
	}
	throw new Error("Codeblock " + indexBasicBlock + " not in this path");
    }


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



    public static Path getLatestCommonSubPath(Path[] paths) {
	Path latestCommonSubPath = getCommonSubPathFromEnd(paths);
	if (latestCommonSubPath.size() == 0) {
	    latestCommonSubPath = getCommonSubPathFromStart(paths);
	}

	return latestCommonSubPath;
    }



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


    public void removeLast(BasicBlock basicBlock) {
	remove(lastIndexOf(basicBlock));
    }






    /* package methods */

    Path(Path path) {
	super((ArrayList<BasicBlock>) path);
    }


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
