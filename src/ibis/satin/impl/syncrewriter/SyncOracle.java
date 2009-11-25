package ibis.satin.impl.syncrewriter;

import ibis.satin.impl.syncrewriter.util.Debug;

import java.util.ArrayList;

import org.apache.bcel.classfile.JavaClass;

public class SyncOracle extends SyncRewriter {


    void advise(String className, SpawnSignature[] spawnSignatures) 
            throws NoSpawningClassException {

        JavaClass javaClass = getClassFromName(className);
        SpawningClass spawnableClass = 
            new SpawningClass(javaClass, spawnSignatures, new Debug(d.turnedOn(), 2));
        d.log(0, "%s is a spawning class\n", className);
        d.log(1, "it contains calls with spawn signatures:\n");
        print(spawnableClass.getSpawnSignatures(), 2);
        spawnableClass.advise(analyzer);
    }


    void start(String[] argv) {

        ArrayList<String> classFileNames = processArguments(argv);
        if (analyzer == null) setAnalyzer("ControlFlow");

        d.log(0, "analyzing for following spawnsignatures:\n");
        SpawnSignature[] spawnSignatures = getSpawnSignatures(classFileNames);
        print(spawnSignatures, 1);

        for (String classFileName : classFileNames) {
            String className = getClassName(classFileName);
            try {
                advise(className, spawnSignatures);
            }
            catch (NoSpawningClassException e) {
                d.log(0, "%s is not a spawning class\n", className);
            }
        }
    }
    
    /**
     * @param args
     */
    public static void main(String[] args) {
        
        new SyncOracle().start(args);
    }

}
