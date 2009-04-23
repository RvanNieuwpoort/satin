package ibis.satin.impl.syncrewriter;

import ibis.satin.impl.syncrewriter.Analyzer;

class AnalyzerFactory {


    static Analyzer createAnalyzer (String analyzerName) throws 
	ClassNotFoundException, IllegalAccessException, InstantiationException {

	    ClassLoader classLoader = AnalyzerFactory.class.getClassLoader();
	    Class analyzerClass = classLoader.loadClass(
		    "ibis.satin.impl.syncrewriter.analyzer." + analyzerName);

	    Object analyzerInstance = (Object) analyzerClass.newInstance();
	    return (Analyzer) analyzerInstance;
	} 
}
