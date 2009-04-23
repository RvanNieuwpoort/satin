package ibis.satin.impl.syncrewriter.analyzer.controlflow;

import java.util.ArrayList;

import org.apache.bcel.generic.InstructionHandle;


public class Path extends ArrayList<CodeBlock> {


    Path() {
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
	for (CodeBlock codeBlock: this) {
	    sb.append(codeBlock.getIndex());
	    sb.append(" ");
	}
	return sb.toString();
    }
}
