import java.util.*;
import java.util.concurrent.*;

/** Student B: run the 4 equal stations many times and record the finish order; try priorities; try a daemon. */
public class FinishOrder {
    static String run(boolean priorities) throws InterruptedException {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        List<Thread> ts = new ArrayList<>();
        for (char c = 'A'; c <= 'D'; c++) {
            final String name = String.valueOf(c);
            Thread t = new Thread(() -> {
                double x = 0; for (int i = 0; i < 3_000_000; i++) x += Math.sqrt(i);   // equal CPU work, no sleep
                if (x < 0) System.out.println(x);
                order.add(name);
            });
            if (priorities) t.setPriority(name.equals("A") ? Thread.MIN_PRIORITY : name.equals("D") ? Thread.MAX_PRIORITY : Thread.NORM_PRIORITY);
            ts.add(t);
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        return String.join("", order);
    }
    public static void main(String[] args) throws Exception {
        System.out.println("10 runs, equal work, default priority (start order is A,B,C,D):");
        Map<String, Integer> freq = new TreeMap<>();
        for (int i = 1; i <= 10; i++) { String o = run(false); freq.merge(o, 1, Integer::sum); System.out.println("  run " + i + ": finish order " + o); }
        System.out.println("distinct orders seen: " + freq);
        System.out.println("10 runs with priorities A=MIN(1), B=C=NORM(5), D=MAX(10):");
        freq.clear();
        for (int i = 1; i <= 10; i++) { String o = run(true); freq.merge(o, 1, Integer::sum); System.out.println("  run " + i + ": finish order " + o); }
        System.out.println("distinct orders seen: " + freq);
    }
}
