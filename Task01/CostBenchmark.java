/** Measures cost of creating+throwing+catching an exception vs returning an error code. */
public class CostBenchmark {
    static int codeVersion(int i) { return (i % 2 == 0) ? -1 : 0; }
    static void throwVersion(int i) { if (i % 2 == 0) throw new IllegalArgumentException("bad"); }
    static int depth(int d, int i) { if (d == 0) { throwVersion(i); return 0; } return depth(d - 1, i); }

    public static void main(String[] args) {
        int N = 2_000_000, sink = 0;
        for (int w = 0; w < 3; w++) {                  // warm-up
            for (int i = 0; i < N; i++) { sink += codeVersion(i); try { throwVersion(i); } catch (RuntimeException e) { sink++; } }
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++) sink += codeVersion(i);
        long t1 = System.nanoTime();
        for (int i = 0; i < N; i++) { try { throwVersion(i); } catch (RuntimeException e) { sink++; } }
        long t2 = System.nanoTime();
        for (int i = 0; i < N; i++) { try { depth(50, i); } catch (RuntimeException e) { sink++; } }
        long t3 = System.nanoTime();
        System.out.printf("Error code     : %8.1f ns/op%n", (t1 - t0) / (double) N);
        System.out.printf("Exception (d=1): %8.1f ns/op (half of calls throw)%n", (t2 - t1) / (double) N);
        System.out.printf("Exception (d=50): %7.1f ns/op (stack trace is deeper)%n", (t3 - t2) / (double) N);
        System.out.println("(sink=" + sink + ")");
    }
}
