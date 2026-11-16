import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Q5 lab: reproduce the double booking reliably, and compare strategies.
 * Usage: java RaceLab race | bench
 */
public class RaceLab {
    interface Booker { boolean book(int seat); }    // true if THIS call sold the seat

    static void busyWork() { double x = 0; for (int i = 0; i < 200; i++) x += Math.sqrt(i); if (x < 0) System.out.println(x); }

    // ---- strategies ----
    static class Unsafe implements Booker {
        final boolean[] booked; final boolean widen;
        Unsafe(int n, boolean widen) { booked = new boolean[n]; this.widen = widen; }
        public boolean book(int s) {
            if (!booked[s]) {                       // CHECK
                if (widen) Thread.yield();          // makes the race window bigger (for a reliable demo)
                booked[s] = true;                   // ACT   <- critical section is check + act
                return true;
            }
            return false;
        }
    }
    static class SyncMethod implements Booker {
        final boolean[] booked;
        SyncMethod(int n) { booked = new boolean[n]; }
        public synchronized boolean book(int s) {   // lock = this, held for the WHOLE method incl. busyWork
            busyWork();
            if (!booked[s]) { booked[s] = true; return true; }
            return false;
        }
    }
    static class SyncBlock implements Booker {
        final boolean[] booked;
        SyncBlock(int n) { booked = new boolean[n]; }
        public boolean book(int s) {
            busyWork();                             // not shared state -> outside the lock
            synchronized (this) {                   // lock only the critical section
                if (!booked[s]) { booked[s] = true; return true; }
                return false;
            }
        }
    }
    static class Cas implements Booker {
        final AtomicBoolean[] booked;
        Cas(int n) { booked = new AtomicBoolean[n]; for (int i = 0; i < n; i++) booked[i] = new AtomicBoolean(); }
        public boolean book(int s) { busyWork(); return booked[s].compareAndSet(false, true); }
    }
    static class Lock implements Booker {
        final boolean[] booked; final ReentrantLock lock = new ReentrantLock();
        Lock(int n) { booked = new boolean[n]; }
        public boolean book(int s) {
            busyWork();
            lock.lock();
            try { if (!booked[s]) { booked[s] = true; return true; } return false; } finally { lock.unlock(); }
        }
    }
    static class Chm implements Booker {
        final ConcurrentHashMap<Integer, Boolean> map = new ConcurrentHashMap<>();
        public boolean book(int s) { busyWork(); return map.putIfAbsent(s, Boolean.TRUE) == null; }
    }

    /** Runs `attempts` booking attempts over `seats` seats on `threads` threads; returns {sales, doubleBookings, millis}. */
    static long[] run(Booker b, int seats, int attempts, int threads) throws Exception { return run(b, seats, attempts, threads, false); }
    static long[] run(Booker b, int seats, int attempts, int threads, boolean sameOrder) throws Exception {
        AtomicInteger sales = new AtomicInteger();
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        int per = attempts / threads;
        for (int t = 0; t < threads; t++) {
            final int off = t;
            fs.add(ex.submit(() -> { go.await(); for (int i = 0; i < per; i++) if (b.book((sameOrder ? i : off * per + i) % seats)) sales.incrementAndGet(); return null; }));
        }
        long t0 = System.nanoTime(); go.countDown();
        for (Future<?> f : fs) f.get();
        long us = (System.nanoTime() - t0) / 1_000;
        ex.shutdown();
        return new long[]{sales.get(), Math.max(0, sales.get() - seats), us};
    }

    public static void main(String[] a) throws Exception {
        if (a[0].equals("race")) {
            System.out.println("Race test: 2000 seats, 8 threads, EVERY thread tries seats 0..1999 in the same order, widened window. Expected sales = 2000");
            long total = 0;
            for (int r = 1; r <= 10; r++) {
                long[] res = run(new Unsafe(2000, true), 2000, 2000 * 8, 8, true);
                total += res[1];
                System.out.println("  run " + r + ": seats sold = " + res[0] + "  -> double bookings = " + res[1]);
            }
            System.out.println("total double bookings over 10 runs: " + total);
            System.out.println("Same test with synchronized method (should always be 2000):");
            for (int r = 1; r <= 3; r++) System.out.println("  run " + r + ": seats sold = " + run(new SyncMethod(2000), 2000, 2000 * 8, 8, true)[0]);
        } else {
            int seats = 1000, attempts = 10_000, threads = 8, reps = 15;
            String[] names = {"synchronized method", "synchronized block", "AtomicBoolean CAS", "ReentrantLock", "ConcurrentHashMap.putIfAbsent"};
            System.out.println("10,000 booking attempts, 1000 seats, 8 threads, median of " + reps + " runs (each attempt has ~200 sqrt of unshared work)");
            for (int k = 0; k < names.length; k++) {
                long[] times = new long[reps]; long dbl = 0;
                for (int r = 0; r < reps + 2; r++) {
                    Booker b = new Booker[]{new SyncMethod(seats), new SyncBlock(seats), new Cas(seats), new Lock(seats), new Chm()}[k];
                    long[] res = run(b, seats, attempts, threads);
                    if (r >= 2) { times[r - 2] = res[2]; dbl += res[1]; } // first 2 = warm-up
                    if (res[0] != seats) throw new AssertionError(names[k] + " sold " + res[0]);
                }
                Arrays.sort(times);
                System.out.printf("  %-30s median %6.2f ms   double bookings: %d%n", names[k], times[reps / 2] / 1000.0, dbl);
            }
        }
    }
}
