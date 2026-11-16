import java.util.*;

/**
 * Q6 Part B experiments on an instrumented bounded counter.
 *   java WaitLab trace      : counter size over time (never > 5, never < 0)
 *   java WaitLab ifwhile    : while vs if around wait()
 *   java WaitLab notify     : notify() vs notifyAll() with 2 producers and 2 consumers, capacity 1
 */
public class WaitLab {
    static class Counter {
        final int[] buf; int count, in, out; final boolean useWhile, useNotifyAll;
        int anomalies, minSeen = 0, maxSeen = 0; final List<Integer> sizes = Collections.synchronizedList(new ArrayList<>());
        Counter(int cap, boolean useWhile, boolean useNotifyAll) { buf = new int[cap]; this.useWhile = useWhile; this.useNotifyAll = useNotifyAll; }
        synchronized void put(int t) throws InterruptedException {
            if (useWhile) { while (count == buf.length) wait(); } else { if (count == buf.length) wait(); }
            if (count == buf.length) anomalies++;                  // OVERFLOW: adding to a full counter
            buf[in] = t; in = (in + 1) % buf.length; count++;
            maxSeen = Math.max(maxSeen, count); sizes.add(count);
            if (useNotifyAll) notifyAll(); else notify();
        }
        synchronized int take() throws InterruptedException {
            if (useWhile) { while (count == 0) wait(); } else { if (count == 0) wait(); }
            if (count == 0) anomalies++;                           // UNDERFLOW: taking from an empty counter
            int t = buf[out]; out = (out + 1) % buf.length; count--;
            minSeen = Math.min(minSeen, count); sizes.add(count);
            if (useNotifyAll) notifyAll(); else notify();
            return t;
        }
    }

    /** returns {sum, anomalies, hung(0/1), maxSeen, minSeen} */
    static long[] runOnce(int cap, int producers, int consumers, int tokensEach, boolean useWhile, boolean useNotifyAll, int produceDelayMs, Counter[] out) throws Exception {
        Counter c = new Counter(cap, useWhile, useNotifyAll); out[0] = c;
        long[] sums = new long[consumers];
        List<Thread> ts = new ArrayList<>();
        int total = producers * tokensEach, perConsumer = total / consumers;
        for (int p = 0; p < producers; p++) {
            Thread t = new Thread(() -> { try { for (int i = 1; i <= tokensEach; i++) { c.put(i); if (produceDelayMs > 0) Thread.sleep(produceDelayMs); } } catch (InterruptedException e) { } });
            t.setDaemon(true); ts.add(t);
        }
        for (int k = 0; k < consumers; k++) {
            final int id = k;
            Thread t = new Thread(() -> { try { for (int i = 0; i < perConsumer; i++) sums[id] += c.take(); } catch (InterruptedException e) { } });
            t.setDaemon(true); ts.add(t);
        }
        for (Thread t : ts) t.start();
        boolean hung = false; long deadline = System.currentTimeMillis() + 3000;
        for (Thread t : ts) { t.join(Math.max(1, deadline - System.currentTimeMillis())); if (t.isAlive()) hung = true; }
        long sum = 0; for (long s : sums) sum += s;
        return new long[]{sum, c.anomalies, hung ? 1 : 0, c.maxSeen, c.minSeen};
    }

    public static void main(String[] a) throws Exception {
        Counter[] holder = new Counter[1];
        if (a[0].equals("trace")) {
            long[] r = runOnce(5, 1, 2, 20, true, true, 0, holder);
            System.out.println("counter size after each put/take (40 operations, capacity 5):");
            System.out.println(holder[0].sizes);
            System.out.println("max size seen = " + r[3] + " (limit 5), min size seen = " + r[4] + " (limit 0), sum = " + r[0] + " (expected 210), anomalies = " + r[1]);
        } else if (a[0].equals("ifwhile")) {
            System.out.println("1 cook (2 ms per token), 2 students, capacity 5, 20 tokens, notifyAll; 30 runs each");
            for (boolean w : new boolean[]{true, false}) {
                int wrongSum = 0, anomRuns = 0, hung = 0; long anom = 0;
                for (int r = 0; r < 30; r++) {
                    long[] x = runOnce(5, 1, 2, 20, w, true, 2, holder);
                    if (x[0] != 210) wrongSum++; if (x[1] > 0) anomRuns++; anom += x[1]; if (x[2] == 1) hung++;
                }
                System.out.printf("  wait() inside %-5s : runs with wrong sum = %2d/30, runs with underflow/overflow = %2d/30 (total %d), hung = %d%n", w ? "while" : "if", wrongSum, anomRuns, anom, hung);
            }
        } else {
            System.out.println("2 producers, 2 consumers, capacity 1, 10 tokens each, wait() in while; 40 runs each (hang = not finished in 3 s)");
            for (boolean all : new boolean[]{true, false}) {
                int hung = 0;
                for (int r = 0; r < 40; r++) { long[] x = runOnce(1, 2, 2, 10, true, all, 0, holder); if (x[2] == 1) hung++; }
                System.out.printf("  %-11s : hung runs = %2d/40%n", all ? "notifyAll()" : "notify()", hung);
            }
        }
        System.exit(0);
    }
}
