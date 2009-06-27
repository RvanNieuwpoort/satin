package ibis.satin.impl.syncrewriter;


import org.apache.bcel.classfile.Method;


class SpawnSignature {
    

    private Method method;
    private String className;


    SpawnSignature(Method method, String className) {
	this.method = method;
	this.className = className;
    }


    Method getMethod() {
	return method;
    }


    String getClassName() {
	return className;
    }


    public boolean equals(Object object) {
	if (!(object instanceof SpawnSignature)) return false;
	SpawnSignature ss = (SpawnSignature) object;

	return (this.method.equals(ss.method) && className.equals(ss.className));
    }


    public String toString() {
	return "spawnSignature: method: " + method.getName() + ", class: " + className;
    }
}
