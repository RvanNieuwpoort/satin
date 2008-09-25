package search;

import ibis.satin.SatinObject;

class SearchImpl2 extends SatinObject implements Searcher {

    private static final long serialVersionUID = 1L;

    public int search(int a[], int from, int to, int val) {
        if (from == to) { // The complete array has been searched.
            return -1; // The element was not found.
        }
        if (to - from == 1) { // Only one element left.
            return (a[from] == val) ? from : -1; // It might be the one.
        }

        // Now, split the array in two parts and search them in parallel.
        int mid = (from + to) / 2;
        int res1 = search(a, from, mid, val);
        int res2 = search(a, mid, to, val);
        sync();
        return (res1 >= 0) ? res1 : res2;
    }

    public static void main(String[] args) {
        SearchImpl1 s = new SearchImpl1();
        int a[] = new int[2000000];

        // Fill the array with random values between 0 and 1000000.
        for (int i = 0; i < 2000000; i++) {
            a[i] = (int) (Math.random() * 1000000);
        }

        // Search for 42 in two sub-domains of the array.
        // Because the search method is marked as spawnable,
        // Satin can run these methods in parallel.
        int res1 = s.search(a, 0, 1000000, 42);
        int res2 = s.search(a, 1000000, 2000000, 42);

        // Wait for results of the two invocations above.
        s.sync();

        // Now compute an overall result.
        int res = (res1 >= 0) ? res1 : res2;

        if (res >= 0) {
            System.out.println("found at pos: " + res);
        } else {
            System.out.println("element not found");
        }
    }
}
