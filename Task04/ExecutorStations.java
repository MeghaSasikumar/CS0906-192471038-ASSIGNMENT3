import java.util.*;
import java.util.concurrent.*;

/** Alternative concept: fixed thread pool instead of creating Thread objects yourself. */
public class ExecutorStations {
    static long scanAll(int n, int p, int ms) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(p);
        long t0 = System.nanoTime();
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) fs.add(pool.submit(() -> { Thread.sleep(ms); return 1; }));
        int total = 0; for (Future<Integer> f : fs) total += f.get();
        long t = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdown();
        if (total != n) throw new AssertionError();
        return t;
    }
    public static void main(String[] args) throws Exception {
        int n = 64, ms = 20;
        System.out.println("n=" + n + " scripts (20 ms each), fixed pool of p threads; ideal time = ceil(n/p)*20 ms");
        for (int p : new int[]{1, 2, 4, 8, 16, 64}) {
            long t = scanAll(n, p, ms);
            System.out.printf("  p=%2d  measured %5d ms   ideal %5d ms%n", p, t, (long) Math.ceil(n / (double) p) * ms);
        }
        // Thread-creation overhead: 5000 tiny tasks, one new Thread each vs a pool of 8
        int m = 5000;
        long t0 = System.nanoTime();
        Thread[] ts = new Thread[m];
        for (int i = 0; i < m; i++) { ts[i] = new Thread(() -> { }); ts[i].start(); }
        for (Thread t : ts) t.join();
        long raw = (System.nanoTime() - t0) / 1_000_000;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        t0 = System.nanoTime();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < m; i++) fs.add(pool.submit(() -> { }));
        for (Future<?> f : fs) f.get();
        long pooled = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdown();
        System.out.println(m + " trivial tasks: one new Thread each = " + raw + " ms, pool of 8 = " + pooled + " ms");
    }
}
