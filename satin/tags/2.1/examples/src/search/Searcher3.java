package search;

interface Searcher3 extends ibis.satin.Spawnable {
   public void search(int a[], int from, int to, int val)
      throws SearchResultFound;
}
