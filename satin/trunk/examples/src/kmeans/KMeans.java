package kmeans;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

public class KMeans extends ibis.satin.SatinObject implements KMeansInterface {
    public static final double THRESHOLD = 0.01;
    public static final int MAXITER = 500;

    private static double centers[][];

    private static final double[] parseVector(String line) {
	String[] numbers = line.split(" ");
	double[] output = new double[numbers.length];
	for (int i = 0; i < output.length; ++i) {
	    output[i] = Double.valueOf(numbers[i]);
	}
	return output;
    }

    // Read initial "centers" file.
    private static final double[][] readCenters(String file) throws Exception {
	BufferedReader buffer = new BufferedReader(new FileReader(file));
	ArrayList<double[]> c = new ArrayList<double[]>();
	String line;
	while ((line = buffer.readLine()) != null) {
	    c.add(parseVector(line));
	}
	buffer.close();
	return c.toArray(new double[c.size()][]);
    }

    // Read "points" directory.
    private static final double[][] readPoints(String dir) throws Exception {
	File d = new File(dir);
	File[] files = d.listFiles();
	if (files != null) {
	    ArrayList<double[]> c = new ArrayList<double[]>();
	    for (File f : files) {
		BufferedReader buffer = new BufferedReader(new FileReader(f));
		String line;
		while ((line = buffer.readLine()) != null) {
		    c.add(parseVector(line));
		}
		buffer.close();
	    }
	    return c.toArray(new double[c.size()][]);
	} else {
	    throw new Exception("Could not open directory " + dir);
	}
    }

    // A single iteration of the clustering algorithm.
    @Override
    public KMeansResult kMeanCluster(double[][] centers, Points points, int lo,
	    int high) {
	int[] counts = new int[centers.length];
	double[][] newCenters = new double[centers.length][];
	KMeansResult result = new KMeansResult(newCenters, counts);

	// Initialize new centers
	for (int i = 0; i < newCenters.length; i++) {
	    newCenters[i] = new double[centers[i].length];
	}

	double totalMinimum = 0.0;

	// For each point, determine the closest center
	for (int pIndex = lo; pIndex < high; pIndex++) {
	    double[] point = points.getPoint(pIndex);
	    double minimum = Double.MAX_VALUE;
	    int cluster = -1;
	    for (int i = 0; i < centers.length; i++) {
		double distance = eucledianDistance(point, centers[i]);
		if (distance < minimum) {
		    minimum = distance;
		    cluster = i;
		}
	    }
	    totalMinimum += minimum;

	    // Update new center sum
	    sum(newCenters[cluster], point);
	    counts[cluster]++;
	}

	System.out.println("Sum distance = " + totalMinimum);
	return result;
    }

    /***** SUPPORT METHODS *****/
    private static final double eucledianDistance(double[] set1, double[] set2) {
	double sum = 0;
	int length = set1.length;
	for (int i = 0; i < length; i++) {
	    double diff = set2[i] - set1[i];
	    sum += (diff * diff);
	}
	return Math.sqrt(sum);
    }

    static final void sum(double[] s1, double[] s2) {
	for (int i = 0; i < s1.length; ++i) {
	    long el1 = Math.round(s1[i] * 100);
	    long el2 = Math.round(s2[i] * 100);
	    s1[i] = (el1 + el2) / 100.0;
	}
    }

    public static void main(String[] args) throws Exception {
	int numTasks = 1;
	centers = readCenters(args[0]);
	Points points = new Points();
	points.initializePoints(readPoints(args[1]));
	if (args.length > 2) {
	    numTasks = Integer.parseInt(args[2]);
	}
	points.exportObject();

	KMeans m = new KMeans();

	int iteration = 0;
	double total;
	KMeansResult[] results = new KMeansResult[numTasks];
	int pointsPerTask = points.size() / numTasks;
	int p1 = points.size() - numTasks * pointsPerTask;
	// Spawn tasks in Master-worker fashion.
	do {
	    int begin = 0;
	    for (int i = 0; i < p1; i++) {
		int end = begin + pointsPerTask + 1;
		results[i] = m.kMeanCluster(centers, points, begin, end);
		begin = end;
	    }
	    for (int i = p1; i < numTasks; i++) {
		int end = begin + pointsPerTask;
		results[i] = m.kMeanCluster(centers, points, begin, end);
		begin = end;
	    }
	    m.sync();
	    iteration++;

	    for (int i = 1; i < numTasks; i++) {
		results[0].add(results[i]);
	    }
	    results[0].avg();

	    total = 0.0;
	    // Compute new centers
	    for (int i = 0; i < centers.length; i++) {
		if (results[0].counts[i] > 0) {
		    total += eucledianDistance(centers[i],
			    results[0].centers[i]);
		    centers[i] = results[0].centers[i];
		}
	    }
	    System.out.println("Iteration " + iteration + ", total diff = "
		    + total);
	} while (total >= THRESHOLD);

	// Print out centroid results.
	System.out.println("Centers:");
	for (int i = 0; i < centers.length; i++) {
	    System.out.print("(");
	    for (int j = 0; j < centers[i].length; j++) {
		System.out.print(centers[i][j]);
		if (j < centers[i].length - 1) {
		    System.out.print(", ");
		}
	    }
	    System.out.println(")");
	}
    }
}
