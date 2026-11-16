import java.util.concurrent.*;

/** Alternative for Part B: ArrayBlockingQueue(5) instead of wait()/notifyAll(); 1 producer, 1 consumer, N tokens. */
public class BoundedQueueAlt {
    static class WaitNotify {
        final int[] buf = new int[5]; int count, in, out;
        synchronized void put(int t) throws InterruptedException { while (count == 5) wait(); buf[in] = t; in = (in + 1) % 5; count++; notifyAll(); }
        synchronized int take() throws InterruptedException { while (count == 0) wait(); int t = buf[out]; out = (out + 1) % 5; count--; notifyAll(); return t; }
    }
    public static void main(String[] a) throws Exception {
        int N = 1_000_000;
        for (int rep = 0; rep < 4; rep++) {
            WaitNotify w = new WaitNotify(); long[] s = new long[1];
            long t0 = System.nanoTime();
            Thread p = new Thread(() -> { try { for (int i = 1; i <= N; i++) w.put(i); } catch (InterruptedException e) { } });
            Thread c = new Thread(() -> { try { for (int i = 1; i <= N; i++) s[0] += w.take(); } catch (InterruptedException e) { } });
            p.start(); c.start(); p.join(); c.join();
            long t1 = System.nanoTime();
            ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<>(5); long[] s2 = new long[1];
            Thread p2 = new Thread(() -> { try { for (int i = 1; i <= N; i++) q.put(i); } catch (InterruptedException e) { } });
            Thread c2 = new Thread(() -> { try { for (int i = 1; i <= N; i++) s2[0] += q.take(); } catch (InterruptedException e) { } });
            long t2 = System.nanoTime();
            p2.start(); c2.start(); p2.join(); c2.join();
            long t3 = System.nanoTime();
            if (rep >= 1)   // rep 0 = warm-up
                System.out.printf("run %d: wait/notifyAll %6.0f ns/token | ArrayBlockingQueue %6.0f ns/token | sums equal=%b (%d)%n",
                    rep, (t1 - t0) / (double) N, (t3 - t2) / (double) N, s[0] == s2[0], s[0]);
        }
    }
}
