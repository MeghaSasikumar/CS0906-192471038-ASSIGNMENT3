/** Student B: a daemon station does not keep the JVM alive when main() ends. Arg "daemon" or "user". */
public class DaemonDemo {
    public static void main(String[] args) throws InterruptedException {
        boolean daemon = args.length > 0 && args[0].equals("daemon");
        Thread station = new Thread(() -> {
            try { Thread.sleep(500); System.out.println("  Station D finished its 500 ms batch"); }
            catch (InterruptedException e) { }
        }, "Station-D");
        station.setDaemon(daemon);
        station.start();
        System.out.println("Station-D daemon=" + station.isDaemon() + "; main() is ending now");
    }
}
