package ibis.satin.impl.syncrewriter;

import java.util.ArrayList;

import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.MethodGen;

public interface Analyzer {


    InstructionHandle[] proposeSyncInsertion(MethodGen methodGen, 
	    ArrayList<SpawnableMethodCall> spawnableCalls);

}
