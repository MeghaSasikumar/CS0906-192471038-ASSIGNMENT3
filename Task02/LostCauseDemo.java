import java.io.IOException;
/** Student B: what is lost if the exception is re-thrown WITHOUT its cause. */
public class LostCauseDemo {
    static class StoreEx extends Exception {
        StoreEx(String m) { super(m); }
        StoreEx(String m, Throwable c) { super(m, c); }
    }
    static void lowLevel() throws IOException { throw new IOException("Disk quota exceeded"); }
    static void withCause() throws StoreEx    { try { lowLevel(); } catch (IOException e) { throw new StoreEx("Could not store file", e); } }
    static void withoutCause() throws StoreEx { try { lowLevel(); } catch (IOException e) { throw new StoreEx("Could not store file"); } }
    public static void main(String[] args) {
        System.out.println("=== WITH cause ===");
        try { withCause(); } catch (StoreEx e) { e.printStackTrace(System.out); System.out.println("getCause() = " + e.getCause()); }
        System.out.println("\n=== WITHOUT cause ===");
        try { withoutCause(); } catch (StoreEx e) { e.printStackTrace(System.out); System.out.println("getCause() = " + e.getCause()); }
    }
}
