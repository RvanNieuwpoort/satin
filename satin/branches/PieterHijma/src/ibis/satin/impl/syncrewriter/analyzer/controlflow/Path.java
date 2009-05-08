package ibis.satin.impl.syncrewriter.analyzer.controlflow;

import java.util.ArrayList;

import org.apache.bcel.generic.InstructionHandle;


public class Path extends ArrayList<CodeBlock> {


    public Path() {
    }


    Path(Path path) {
	super((ArrayList<CodeBlock>) path);
    }


    public CodeBlock getLastCodeBlock() {
	return get(size() - 1);
    }


    public Path getSubPathIncluding(int indexCodeBlock) {
	for (int i = 0; i < size(); i++) {
	    if (get(i).getIndex() == indexCodeBlock) {
		return (Path) subList(0, i + 1);
	    }
	}
	throw new Error("Codeblock " + indexCodeBlock + " not in this path");
    }


    public Path getCommonSubPathFromEnd(Path rhs) {
	Path path = new Path();

	if (rhs.size() == 0 || size() == 0) {
	    return path;
	}
	int i = size() - 1;
	int j = rhs.size() - 1;
	while (i >= 0 && j >= 0 && get(i).equals(rhs.get(j))) {
	    path.add(0, get(i));
	    i--; 
	    j--;
	}
	return path;
    }



    public Path getCommonSubPathFromStart(Path rhs) {
	Path path = new Path();

	/*
	System.out.printf("0 < size(): %b\n", 0 < size());
	System.out.printf("0 < path.size(): %b\n", 0 < size());
	System.out.printf("0 < size(): %b\n", 0 < size());
	System.out.printf("0 < size(): %b\n", 0 < size());
	*/
	for (int i = 0; i < size() && i < rhs.size() && get(i).equals(rhs.get(i)); i++) {
	    path.add(get(0));
	}
	return path;
    }



    public String toString() {
	StringBuilder sb = new StringBuilder();
	for (int i = 0; i < size() - 1; i++) {
	    sb.append(get(i).getIndex());
	    sb.append(" ");
	}
	sb.append(get(size() - 1).getIndex());
	return sb.toString();
    }


    public void removeLast(CodeBlock codeBlock) {
	remove(lastIndexOf(codeBlock));
    }


    int nrOfOccurences(CodeBlock codeBlock) {
	int nrOfOccurences = 0;
	for (CodeBlock i : this) {
	    if (i.equals(codeBlock)) {
		nrOfOccurences++;
	    }
	}
	
	return nrOfOccurences;
    }
}
