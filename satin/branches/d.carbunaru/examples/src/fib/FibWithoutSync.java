package fib;

/**
 * Only here for demonstration purposes: demonstrates the sync-rewriter.
 */
public class FibWithoutSync extends ibis.satin.SatinObject implements FibInterface {

    private static final long serialVersionUID = -2084766268168809118L;

    public long fib(int n) {
        if (n < 2)
            return n;

        long x = fib(n - 1);
        long y = fib(n - 2);
        // Sync should be inserted here.
        return x + y;
    }

    public static void main(String[] args) {
        int n = 0;

        if (args.length == 0) {
            n = 30;
        } else if (args.length > 1) {
            System.out.println("Usage: fib <n>");
            System.exit(1);
        } else {
            n = Integer.parseInt(args[0]);
        }

        FibWithoutSync f = new FibWithoutSync();

        System.out.println("Running Fib " + n);

        long start = System.currentTimeMillis();
        long result = f.fib(n);
        // Sync should be inserted here.
        // Note that the order of the statements here is changed with respect
        // to the original Fib, because the sync-rewriter does not understand
        // that the time should be measured after the sync. Therefore, the
        // access to "result" is moved upwards.
        System.out.println("application result fib (" + n + ") = " + result);
        double time = (System.currentTimeMillis() - start) / 1000.0;

        System.out.println("application time fib (" + n + ") took " + time
                + " s");
    }
}
