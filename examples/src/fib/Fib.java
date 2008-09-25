package fib;

/*
 * This is one of the most simple Satin programs possible. It calculates
 * Fibonacci numbers. It calculates them in a very inefficient way, but this
 * program is used as an example. It is not trying to be a
 * fast Fibonacci implementation.
 */
final class Fib extends ibis.satin.SatinObject implements FibInterface {

    private static final long serialVersionUID = -2084766268168809118L;

    public long fib(int n) {
        if (n < 2)
            return n;

        long x = fib(n - 1);
        long y = fib(n - 2);
        sync();

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

        Fib f = new Fib();

        System.out.println("Running Fib " + n);

        long start = System.currentTimeMillis();
        long result = f.fib(n);
        f.sync();
        double time = (double) (System.currentTimeMillis() - start) / 1000.0;

        System.out.println("application time fib (" + n + ") took " + time
                + " s");
        System.out.println("application result fib (" + n + ") = " + result);
    }
}
