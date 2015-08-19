package kmeans;

public interface KMeansInterface {
    KMeansResult kMeanCluster(double[][] centers, Points points, int lo,
	    int high);
}
