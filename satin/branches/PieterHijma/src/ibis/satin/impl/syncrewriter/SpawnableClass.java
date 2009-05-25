package ibis.satin.impl.syncrewriter;

import ibis.satin.impl.syncrewriter.util.Debug;

import java.io.PrintStream;

import java.util.ArrayList;

import java.lang.annotation.Annotation;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.Repository;

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.StackConsumer;
import org.apache.bcel.generic.StackProducer;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.DASTORE;
import org.apache.bcel.generic.ALOAD;


class SpawnableClass extends ClassGen {


    private int indexSync;
    private Method[] spawnSignatures;
    private Debug d;


    SpawnableClass (JavaClass javaClass, Debug d) throws NoSpawnableClassException {
	super(javaClass);

	if (!(javaClass.isClass() && isSpawnable(javaClass))) {
	    throw new NoSpawnableClassException();
	}

	this.indexSync = getConstantPool().addMethodref(javaClass.getClassName(), 
		"sync", "()V");
	this.spawnSignatures = getSpawnSignatures(javaClass);
	this.d = d;
    }


    void rewrite(Analyzer analyzer) throws ClassRewriteFailure {
	boolean throwRewriteFailure = false;

	for (Method spawnSignature : spawnSignatures) {
	    try {
	        rewriteForSpawnSignature(spawnSignature, analyzer);
	    }
	    catch (MethodRewriteFailure e) {
		throwRewriteFailure = true;
	    }
	}
	if (throwRewriteFailure) throw new ClassRewriteFailure();
    }


    private void rewriteForSpawnSignature(Method spawnSignature, Analyzer analyzer) 
	throws MethodRewriteFailure {
	boolean throwRewriteFailure = false;

	d.log(0, "rewriting for spawn signature: %s\n", spawnSignature.getName());

	Method[] methods = getMethods();

	for (Method method : methods) {
	    try {
		SpawnableMethod spawnableMethod = new 
		    SpawnableMethod(method, getClassName(), getConstantPool(),
			    spawnSignature, indexSync, 
			    new Debug(d.turnedOn(), d.getStartLevel()+2));
		d.log(1, "%s calls %s\n", method.getName(), spawnSignature.getName());
		spawnableMethod.rewrite(analyzer);
		removeMethod(method);
		addMethod(spawnableMethod.getMethod());
	    }
	    catch (NoSpawnableMethodException e) {
		d.log(1, "%s doesn't call %s\n", method.getName(), spawnSignature.getName());
		// spawnSignature is not called
	    }
	    catch (MethodRewriteFailure e) {
		d.error("Failed to rewrite %s for spawn signature: %s\n",
			method.getName(), spawnSignature.getName());
		throwRewriteFailure = true;
	    }
	    catch (AssumptionFailure e) {
		d.error("Failed to rewrite %s for spawn signature: %s\n",
			method.getName(), spawnSignature.getName());
		d.error(e.getMessage());
		throwRewriteFailure = true;
	    }
	}

	if (throwRewriteFailure) throw new MethodRewriteFailure();
	d.log(0, "rewritten for spawn signature: %s\n", spawnSignature.getName());
    }


    private boolean isSpawnable(JavaClass javaClass) {
	JavaClass[] interfaces = javaClass.getAllInterfaces();

	for (JavaClass javaInterface : interfaces) {
	    if (javaInterface.getClassName().equals("ibis.satin.Spawnable")) {
		return true;
	    }
	}

	return false;
    }


    private Method[] getSpawnSignatures(JavaClass spawnableClass) {
	Method[] spawnableMethods = null;

	JavaClass[] interfaces = spawnableClass.getInterfaces();
	for (JavaClass javaInterface : interfaces) {
	    if (isSpawnable(javaInterface)) {
		spawnableMethods = javaInterface.getMethods();
	    }
	}

	if (spawnableMethods == null) {
	    throw new Error("no methods specified in " +
		    spawnableClass.getClassName());
	}

	return spawnableMethods;
    }
}
