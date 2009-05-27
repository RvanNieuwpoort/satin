package search;

interface Searcher extends ibis.satin.Spawnable {
    public int search(int a[], int from, int to, int val);
}
