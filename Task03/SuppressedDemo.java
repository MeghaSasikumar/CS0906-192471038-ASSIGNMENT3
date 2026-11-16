/** Student B: closing order and suppressed exceptions in try-with-resources. */
public class SuppressedDemo {
    static class Res implements AutoCloseable {
        final String name; final boolean failOnClose;
        Res(String name, boolean failOnClose) { this.name = name; this.failOnClose = failOnClose; System.out.println("open  " + name); }
        @Override public void close() {
            System.out.println("close " + name);
            if (failOnClose) throw new IllegalStateException("close() of " + name + " failed");
        }
    }
    public static void main(String[] args) {
        try (Res a = new Res("A (opened first)", false); Res b = new Res("B (opened second)", true)) {
            System.out.println("body running");
            throw new RuntimeException("PRIMARY failure in body");
        } catch (RuntimeException e) {
            System.out.println("caught   : " + e.getMessage());
            for (Throwable s : e.getSuppressed()) System.out.println("suppressed: " + s.getMessage());
        }
    }
}
