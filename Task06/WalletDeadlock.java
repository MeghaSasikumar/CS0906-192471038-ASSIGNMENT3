import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Q6 Part A - hostel wallet transfers.   java WalletDeadlock deadlock | ordered | trylock | stress
 */
public class WalletDeadlock {
    static class Account {
        final int id; int balance; final ReentrantLock lock = new ReentrantLock();
        Account(int id, int balance) { this.id = id; this.balance = balance; }
    }

    /** Locks FROM then TO. Two opposite transfers can deadlock. */
    static void unsafeTransfer(Account from, Account to, int amt, int pauseMs) throws InterruptedException {
        synchronized (from) {
            if (pauseMs > 0) Thread.sleep(pauseMs);            // widens the window: other thread grabs its first lock now
            synchronized (to) { from.balance -= amt; to.balance += amt; }
        }
    }

    /** Prevention 1: always lock the account with the SMALLER id first (global lock ordering). */
    static void orderedTransfer(Account from, Account to, int amt, int pauseMs) throws InterruptedException {
        Account first = from.id < to.id ? from : to, second = first == from ? to : from;
        synchronized (first) {
            if (pauseMs > 0) Thread.sleep(pauseMs);
            synchronized (second) { from.balance -= amt; to.balance += amt; }
        }
    }

    /** Prevention 2: tryLock with timeout, release and retry with random back-off. */
    static boolean tryTransfer(Account from, Account to, int amt, int pauseMs) throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (from.lock.tryLock(20, TimeUnit.MILLISECONDS)) {
                try {
                    if (pauseMs > 0) Thread.sleep(pauseMs);
                    if (to.lock.tryLock(20, TimeUnit.MILLISECONDS)) {
                        try { from.balance -= amt; to.balance += amt; return true; } finally { to.lock.unlock(); }
                    }
                } finally { from.lock.unlock(); }
            }
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 15));   // random back-off avoids livelock
        }
        return false;
    }

    interface Transfer { void go(Account f, Account t, int amt, int pause) throws Exception; }

    static void pair(String label, Transfer tr) throws Exception {
        Account a = new Account(1, 1000), b = new Account(2, 1000);
        Thread t1 = new Thread(() -> { try { tr.go(a, b, 100, 100); } catch (Exception e) { } }, "T1(A->B)");
        Thread t2 = new Thread(() -> { try { tr.go(b, a, 50, 100); } catch (Exception e) { } }, "T2(B->A)");
        t1.setDaemon(true); t2.setDaemon(true);
        t1.start(); t2.start(); t1.join(1500); t2.join(1500);
        if (t1.isAlive() || t2.isAlive()) {
            System.out.println(label + ": DEADLOCK - threads did not finish within 1.5 s");
            ThreadMXBean mx = ManagementFactory.getThreadMXBean();
            long[] ids = mx.findDeadlockedThreads();
            if (ids != null) for (ThreadInfo ti : mx.getThreadInfo(ids)) System.out.println("   " + ti.getThreadName() + " is " + ti.getThreadState() + " waiting for lock " + ti.getLockName() + " held by " + ti.getLockOwnerName());
        } else {
            System.out.println(label + ": completed. A=" + a.balance + " B=" + b.balance + " total=" + (a.balance + b.balance) + " (must be 2000)");
        }
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "deadlock": pair("unsafe  (lock from, then to)", WalletDeadlock::unsafeTransfer); break;
            case "ordered":  pair("ordered (lock smaller id first)", WalletDeadlock::orderedTransfer); break;
            case "trylock":  pair("tryLock + timeout + back-off", (f, t, m, p) -> { if (!tryTransfer(f, t, m, p)) System.out.println("   gave up"); }); break;
            default: {   // stress: 4 threads x 50,000 random transfers between 5 accounts, with real contention
                for (String mode : new String[]{"ordered", "trylock"}) {
                    Account[] acc = new Account[5]; for (int i = 0; i < 5; i++) acc[i] = new Account(i, 10_000);
                    int threads = 4, per = 50_000; int[] failed = new int[threads];
                    ExecutorService ex = Executors.newFixedThreadPool(threads);
                    long t0 = System.nanoTime(); List<Future<?>> fs = new ArrayList<>();
                    for (int t = 0; t < threads; t++) { final int id = t; fs.add(ex.submit(() -> {
                        Random r = new Random(id);
                        for (int i = 0; i < per; i++) {
                            Account f = acc[r.nextInt(5)], to = acc[r.nextInt(5)]; if (f == to) continue;
                            try { if (mode.equals("ordered")) orderedTransfer(f, to, 1, 0); else if (!tryTransfer(f, to, 1, 0)) failed[id]++; }
                            catch (InterruptedException e) { return null; }
                        }
                        return null; })); }
                    boolean done = true;
                    try { for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS); } catch (TimeoutException e) { done = false; }
                    long ms = (System.nanoTime() - t0) / 1_000_000; int total = 0; for (Account x : acc) total += x.balance;
                    int fail = Arrays.stream(failed).sum();
                    System.out.printf("%-8s: %d transfers finished=%b in %d ms (%.2f us/transfer), total money=%d (must be 50000), gave-up transfers=%d%n",
                        mode, threads * per, done, ms, ms * 1000.0 / (threads * per), total, fail);
                    ex.shutdownNow();
                }
            }
        }
        System.exit(0);
    }
}
