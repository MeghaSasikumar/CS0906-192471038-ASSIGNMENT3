import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Q4 - Four scanner stations scan answer scripts in parallel.
 * Usage: java ScanStations <thread|runnable> < input.txt      (input lines: "<station> <scripts>")
 */
public class ScanStations {
    static final AtomicInteger TOTAL = new AtomicInteger();

    /** Way 1: extend Thread. */
    static class StationThread extends Thread {
        final int scripts;
        StationThread(String name, int scripts) { super("Station-" + name); this.scripts = scripts; }
        @Override public void run() { scan(getName(), scripts); }
    }

    /** Way 2: implement Runnable (task) and hand it to a Thread. */
    static class StationTask implements Runnable {
        final String name; final int scripts;
        StationTask(String name, int scripts) { this.name = "Station-" + name; this.scripts = scripts; }
        @Override public void run() { scan(name, scripts); }
    }

    static void scan(String station, int scripts) {
        for (int i = 1; i <= scripts; i++) {
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        TOTAL.addAndGet(scripts);
        System.out.println("  " + station + " finished: scanned " + scripts + " scripts");
    }

    public static void main(String[] args) throws InterruptedException {
        String mode = args.length > 0 ? args[0] : "thread";
        Scanner in = new Scanner(System.in);
        List<Thread> stations = new ArrayList<>();
        while (in.hasNext()) {
            String name = in.next(); int n = in.nextInt();
            stations.add(mode.equals("thread") ? new StationThread(name, n) : new Thread(new StationTask(name, n), "Station-" + name));
        }
        System.out.println("Mode: " + mode);
        for (Thread t : stations) System.out.println(t.getName() + " before start: state=" + t.getState() + " alive=" + t.isAlive());
        for (Thread t : stations) t.start();
        Thread.sleep(30);
        for (Thread t : stations) System.out.println(t.getName() + " after start : state=" + t.getState() + " alive=" + t.isAlive());
        for (Thread t : stations) t.join();               // main waits (WAITING state) for each station
        for (Thread t : stations) System.out.println(t.getName() + " after join  : state=" + t.getState() + " alive=" + t.isAlive());
        System.out.println("TOTAL scripts scanned: " + TOTAL.get());
    }
}
