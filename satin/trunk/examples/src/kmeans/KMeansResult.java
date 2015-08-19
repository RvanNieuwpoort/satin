package kmeans;

public class KMeansResult implements java.io.Serializable {
    double[][] centers;
    int[] counts;

    public KMeansResult(double[][] newCenters, int[] counts) {
	this.centers = newCenters;
	this.counts = counts;
    }

    public void add(KMeansResult other) {
	for (int i = 0; i < centers.length; i++) {
	    KMeans.sum(centers[i], other.centers[i]);

	    counts[i] += other.counts[i];
	}
    }

    public void avg() {
	for (int i = 0; i < centers.length; i++) {
	    if (counts[i] != 0) {
		for (int j = 0; j < centers[i].length; j++) {
		    double[] center = centers[i];
		    center[j] /= counts[i];
		    center[j] = (double) Math.round(center[j] * 100) / 100;
		}
	    }
	}
    }
}