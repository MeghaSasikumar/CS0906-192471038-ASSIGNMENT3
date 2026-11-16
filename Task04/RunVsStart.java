/** Student A: calling run() does NOT create a new thread - only start() does. */
public class RunVsStart {
    public static void main(String[] args) throws InterruptedException {
        Runnable r = () -> System.out.println("  body executed by thread: " + Thread.currentThread().getName());
        System.out.println("main thread is: " + Thread.currentThread().getName());
        Thread t1 = new Thread(r, "Station-X");
        System.out.println("calling t1.run():");
        t1.run();                       // ordinary method call, runs in main
        System.out.println("  t1.getState() after run()  = " + t1.getState() + "  (never started)");
        Thread t2 = new Thread(r, "Station-Y");
        System.out.println("calling t2.start():");
        t2.start(); t2.join();          // new thread executes run()
        System.out.println("  t2.getState() after join() = " + t2.getState());
    }
}
