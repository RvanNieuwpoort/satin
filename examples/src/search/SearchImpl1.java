package search;

import ibis.satin.SatinObject;

class SearchImpl1 extends SatinObject implements Searcher {
   public int search(int a[], int from, int to, int val) {
      for(int i = from; i < to; i++) {
         if (a[i] == val) return i;
      }
      return -1;
   }

   public static void main(String[] args) {
      SearchImpl1 s = new SearchImpl1();
      int a[] = new int[200];

      // Fill the array with random values between 0 and 100.
      for(int i=0; i<200; i++) {
         a[i] = (int) (Math.random() * 100);
      }

      // Search for 42 in two sub-domains of the array.
      // Because the search method is marked as spawnable,
      // Satin can run these methods in parallel.
      int res1 = s.search(a, 0, 100, 42);
      int res2 = s.search(a, 100, 200, 42);

      // Wait for results of the two invocations above.
      s.sync();

      // Now compute an overall result.
      int res = (res1 >= 0) ? res1 : res2;

      if(res >= 0) {
         System.out.println("found at pos: " + res);
      } else {
         System.out.println("element not found");
      }
   }
}
