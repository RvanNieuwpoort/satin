package search;

import ibis.satin.SatinObject;

class SearchImpl3 extends SatinObject implements Searcher3 {

    private static final long serialVersionUID = 1L;

    public void search(int a[], int from, int to, int val)
            throws SearchResultFound {

        if (from == to) { // The complete array has been searched.
            return; // The element was not found.
        }
        if (to - from == 1) { // Only one element left.
            if (a[from] == val) { // Found it!
                throw new SearchResultFound(from);
            } else {
                return; // The element was not found.
            }
        }

        // Now, split the array in two parts and search them in parallel.
        int mid = (from + to) / 2;
        search(a, from, mid, val);
        search(a, mid, to, val);
        sync();
    }

    public static void main(String[] args) {
        SearchImpl3 s = new SearchImpl3();
        int a[] = new int[2000000];
        int res = -1;

        // Fill the array with random values between 0 and 1000000.
        for (int i = 0; i < 2000000; i++) {
            a[i] = (int) (Math.random() * 1000000);
        }

        // Search for 42 in two sub-domains of the array.
        // Because the search method is marked as spawnable,
        // Satin can run these methods in parallel.
        try {
            s.search(a, 0, 1000000, 42);
            s.search(a, 1000000, 2000000, 42);

            // Wait for results of the invocations above.
            s.sync();
        } catch (SearchResultFound x) {
            // We come here only if one of the two jobs found a result.
            s.abort(); // kill the other job that might still be running.
            res = x.pos;
            return; // return needed because inlet is handled in separate
                    // thread.
        }

        if (res >= 0) {
            System.out.println("found at pos: " + res);
        } else {
            System.out.println("element not found");
        }
    }
}
