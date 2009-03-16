package ibis.satin.impl.syncrewriter;

import ibis.satin.impl.syncrewriter.Analyzer;

//import ibis.satin.impl.syncrewriter.analyzer.*;

class AnalyzerFactory {


    static Analyzer createAnalyzer(String analyzerName) {
	try {
	    ClassLoader classLoader = AnalyzerFactory.class.getClassLoader();
	    Class aClass = classLoader.loadClass(
		    "ibis.satin.impl.syncrewriter.analyzer." + analyzerName);
	    /*
	    Class aClass = classLoader.loadClass(
		    "ibis.satin.impl.syncrewriter.analyzer." + analyzerName + ".class");
	    Class aClass = classLoader.loadClass(
		    "analyzer." + analyzerName);
	    Class aClass = classLoader.loadClass(
		    "analyzer." + analyzerName + ".class");
		    */
	    Object object = (Object) aClass.newInstance();
	    return (Analyzer) object;
	} 
	catch (ClassNotFoundException e) {
	    throw new Error(e);
	}
	catch (InstantiationException e) {
	    throw new Error(e);
	}
	catch (IllegalAccessException e) {
	    throw new Error(e);
	}
    }
}
