package kmeans;

public class Points extends ibis.satin.SharedObject {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private double[][] points;

    public double[] getPoint(int index) {
	return points[index];
    }

    public void initializePoints(double[][] points) {
	this.points = points;
    }

    public int size() {
	return points.length;
    }
}
