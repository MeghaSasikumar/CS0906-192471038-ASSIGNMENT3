/** Cost of creating exceptions: stack trace capture and chaining, at different stack depths. */
public class ChainBenchmark {
    static class NoTrace extends Exception {
        NoTrace(String m, Throwable c) { super(m, c, false, false); }   // writableStackTrace = false
    }
    static Throwable make(int depth, int mode, Throwable cause) {
        if (depth > 0) return make(depth - 1, mode, cause);
        switch (mode) {
            case 0:  return new Exception("x");                 // stack trace, no cause
            case 1:  return new Exception("x", cause);          // stack trace + cause
            default: return new NoTrace("x", cause);            // no stack trace + cause
        }
    }
    public static void main(String[] args) {
        int N = 300_000; long sink = 0;
        Throwable cause = new java.io.IOException("io");
        String[] names = {"stack trace, no cause", "stack trace + cause", "no stack trace + cause"};
        for (int depth : new int[]{5, 50, 200}) {
            for (int mode = 0; mode < 3; mode++) {
                for (int i = 0; i < N; i++) sink += make(depth, mode, cause).hashCode() & 1;   // warm-up
                long t0 = System.nanoTime();
                for (int i = 0; i < N; i++) sink += make(depth, mode, cause).hashCode() & 1;
                long t1 = System.nanoTime();
                System.out.printf("depth %3d | %-24s | %8.0f ns/exception%n", depth, names[mode], (t1 - t0) / (double) N);
            }
        }
        System.out.println("(sink=" + sink + ")");
    }
}
