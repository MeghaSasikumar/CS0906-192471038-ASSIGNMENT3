/** Alternative concept: the same exceptions made UNCHECKED (extend RuntimeException). */
public class UncheckedPortal {
    static class ExamException extends RuntimeException {
        ExamException(String m) { super(m); }
        ExamException(String m, Throwable c) { super(m, c); }
    }
    static class StorageEx extends ExamException { StorageEx(String m, Throwable c) { super(m, c); } }
    // No 'throws' clauses anywhere - signatures are shorter, but the compiler no longer forces callers to handle anything.
    static void save(String f)   { try { throw new java.io.IOException("disk full"); } catch (java.io.IOException e) { throw new StorageEx("Could not store " + f, e); } }
    static void service(String f) { save(f); }
    public static void main(String[] a) {
        service("ok.pdf");                       // compiles even with NO try/catch; fails at run time if nobody catches
    }
}
