import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/** Q2 - Online examination portal: exception hierarchy, throw/throws across 3 layers, chaining. */
public class Portal {
    // ---------- exception hierarchy ----------
    /** Checked base: every business failure the student can act on. */
    static class ExamException extends Exception {
        ExamException(String m) { super(m); }
        ExamException(String m, Throwable c) { super(m, c); }
    }
    static class ExamClosedException extends ExamException {            // checked
        ExamClosedException(String m) { super(m); }
    }
    static class NotRegisteredException extends ExamException {         // checked
        NotRegisteredException(String m) { super(m); }
    }
    static class AnswerTooLargeException extends ExamException {        // checked
        AnswerTooLargeException(String m) { super(m); }
    }
    static class SubmissionStorageException extends ExamException {     // checked, wraps IOException
        SubmissionStorageException(String m, Throwable c) { super(m, c); }
    }
    /** Unchecked: programming error / bad call (null, blank) - the student cannot fix it. */
    static class InvalidSubmissionException extends RuntimeException {
        InvalidSubmissionException(String m) { super(m); }
    }

    // ---------- storage layer ----------
    static class StorageLayer {
        void write(String file, long sizeKb) throws SubmissionStorageException {
            try {
                if (file.startsWith("FULL_")) throw new IOException("No space left on device");
                System.out.println("    [storage] wrote " + file + " (" + sizeKb + " KB)");
            } catch (IOException e) {
                throw new SubmissionStorageException("Could not store " + file, e);   // chaining
            }
        }
    }

    // ---------- service layer ----------
    static class ExamService {
        static final long LIMIT_KB = 500;
        final Set<String> registered = new HashSet<>(List.of("S1:CS101", "S2:CS101"));
        final LocalDateTime closes = LocalDateTime.of(2026, 11, 20, 17, 0);
        final StorageLayer storage = new StorageLayer();

        void submit(String student, String course, String file, long sizeKb, LocalDateTime now) throws ExamException {
            if (student == null || file == null || file.isBlank()) throw new InvalidSubmissionException("student and file are required");
            if (now.isAfter(closes)) throw new ExamClosedException("Exam window closed at " + closes);
            if (!registered.contains(student + ":" + course)) throw new NotRegisteredException(student + " is not registered for " + course);
            if (sizeKb > LIMIT_KB) throw new AnswerTooLargeException(file + " is " + sizeKb + " KB; limit is " + LIMIT_KB + " KB");
            storage.write(file, sizeKb);                       // propagates SubmissionStorageException upward
        }
    }

    // ---------- controller layer (final handler) ----------
    static class Controller {
        final ExamService service = new ExamService();
        void submit(String student, String course, String file, long sizeKb, LocalDateTime now) {
            try {
                service.submit(student, course, file, sizeKb, now);
                System.out.println("    Student sees: Submission accepted.");
            } catch (ExamClosedException | NotRegisteredException | AnswerTooLargeException e) {
                System.out.println("    Student sees: Submission rejected - " + e.getMessage());
            } catch (SubmissionStorageException e) {
                System.out.println("    Student sees: We could not save your answer, please retry. (" + e.getMessage() + ")");
                System.out.println("    Log: cause = " + e.getCause());            // original IOException kept
                if (System.getProperty("trace") != null) e.printStackTrace(System.out);
            } catch (ExamException e) {                        // any other business failure (base type, last of the checked)
                System.out.println("    Student sees: Submission failed - " + e.getMessage());
            } catch (InvalidSubmissionException e) {
                System.out.println("    Student sees: Internal error - " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        Controller c = new Controller();
        LocalDateTime open = LocalDateTime.of(2026, 11, 20, 10, 0), late = LocalDateTime.of(2026, 11, 20, 18, 30);
        Object[][] cases = {
            {"S1", "CS101", "ans1.pdf", 120L, open, "valid submission"},
            {"S1", "CS101", "ans2.pdf", 120L, late, "window closed"},
            {"S9", "CS101", "ans3.pdf", 120L, open, "not registered"},
            {"S2", "CS101", "ans4.pdf", 800L, open, "file too large"},
            {"S2", "CS101", "FULL_ans5.pdf", 100L, open, "storage failure (IOException chained)"},
            {"S2", "CS101", " ", 100L, open, "blank file name (unchecked)"},
        };
        int n = 0;
        for (Object[] k : cases) {
            System.out.println("Case " + (++n) + ": " + k[5]);
            c.submit((String) k[0], (String) k[1], (String) k[2], (Long) k[3], (LocalDateTime) k[4]);
        }
    }
}
