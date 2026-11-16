/** Student B: stack trace of one UNCAUGHT exception (main does not catch it). */
public class UncaughtDemo {
    static double parse(String s) { return Double.parseDouble(s); }
    static double withdraw(String s) { return parse(s) * 2; }
    public static void main(String[] args) {
        System.out.println("About to withdraw 'abc'");
        withdraw("abc");
        System.out.println("never printed");
    }
}
