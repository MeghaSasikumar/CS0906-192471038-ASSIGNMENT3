import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * Q7 - Concurrent, fault-tolerant semester result processor.
 * Usage: java [-Dlatency=ms] [-Dinject=true] ResultProcessor <index.txt> <mode> [poolSize]
 *   mode = sequential | executor | parallel | threadperfile
 */
public class ResultProcessor {
    // ---------- exceptions: checked, meaningful, never leak IOException to the summary ----------
    static class ScoreFileException extends Exception {
        ScoreFileException(String m) { super(m); }
        ScoreFileException(String m, Throwable c) { super(m, c); }
    }
    static class MissingScoreFileException extends ScoreFileException { MissingScoreFileException(String f, Throwable c) { super("missing: " + f, c); } }
    static class CorruptScoreFileException extends ScoreFileException { CorruptScoreFileException(String m) { super(m); } }

    enum Status { OK, CORRUPT, MISSING, ERROR, TIMEOUT }

    /** Immutable result of processing one file (safe to publish across threads). */
    static final class Result {
        final String file; final Status status; final String student; final int total; final double percent; final boolean pass; final String message;
        Result(String file, Status status, String student, int total, double percent, boolean pass, String message) {
            this.file = file; this.status = status; this.student = student; this.total = total; this.percent = percent; this.pass = pass; this.message = message;
        }
        static Result failed(String file, Status s, String msg) { return new Result(file, s, null, 0, 0, false, msg); }
        String line() { return file + " | " + status + " | " + (status == Status.OK ? student + " total=" + total + String.format(" pct=%.2f", percent) + (pass ? " PASS" : " FAIL") : message); }
    }

    static final int LATENCY_MS = Integer.getInteger("latency", 0);   // simulated slow storage (I/O wait) per file
    static final boolean INJECT = Boolean.getBoolean("inject");        // fault injection: unexpected RuntimeException / Error

    static String sha256(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); for (byte b : d) sb.append(String.format("%02x", b)); return sb.toString();
    }

    /** Read + validate + compute one file. Throws meaningful checked exceptions. */
    static Result process(String file, int index) throws Exception {
        if (INJECT && index % 1000 == 0) throw new IllegalStateException("injected RuntimeException for " + file);
        if (INJECT && index % 2500 == 0) throw new StackOverflowError("injected Error for " + file);
        List<String> lines;
        try {
            if (LATENCY_MS > 0) Thread.sleep(LATENCY_MS);
            lines = Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new MissingScoreFileException(file, e);
        } catch (IOException e) {
            throw new ScoreFileException("cannot read " + file, e);
        }
        if (lines.size() != 5) throw new CorruptScoreFileException("expected 5 lines but found " + lines.size());
        if (!lines.get(0).startsWith("STUDENT:")) throw new CorruptScoreFileException("missing STUDENT header");
        if (!lines.get(4).startsWith("SHA256:")) throw new CorruptScoreFileException("missing checksum");
        String body = String.join("\n", lines.subList(0, 4)) + "\n";
        if (!sha256(body).equals(lines.get(4).substring(7))) throw new CorruptScoreFileException("checksum mismatch");
        int total = 0; boolean pass = true;
        for (int i = 1; i <= 3; i++) {
            String[] p = lines.get(i).split(",");
            int m;
            try { m = Integer.parseInt(p[1].trim()); } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) { throw new CorruptScoreFileException("bad marks line '" + lines.get(i) + "'"); }
            if (m < 0 || m > 100) throw new CorruptScoreFileException("marks out of range: " + m);
            total += m; if (m < 40) pass = false;
        }
        return new Result(file, Status.OK, lines.get(0).substring(8), total, total / 3.0, pass, "");
    }

    /** The unit of work. call() NEVER throws for expected or unexpected Exceptions: it converts each into a Result. */
    static Result safeProcess(String file, int index) {
        try { return process(file, index); }
        catch (MissingScoreFileException e) { return Result.failed(file, Status.MISSING, e.getMessage()); }
        catch (CorruptScoreFileException e) { return Result.failed(file, Status.CORRUPT, e.getMessage()); }
        catch (ScoreFileException e)        { return Result.failed(file, Status.ERROR, e.getMessage() + " <- " + e.getCause()); }
        catch (InterruptedException e)      { Thread.currentThread().interrupt(); return Result.failed(file, Status.ERROR, "interrupted"); }
        catch (Exception e)                 { return Result.failed(file, Status.ERROR, "unexpected " + e); }
        // java.lang.Error (e.g. StackOverflowError) is NOT caught here: it travels to Future.get() -> ExecutionException (second safety net)
    }

    public static void main(String[] args) throws Exception {
        List<String> files = Files.readAllLines(Paths.get(args[0])).stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
        String mode = args[1]; int pool = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        ConcurrentHashMap<String, Result> results = new ConcurrentHashMap<>();    // shared result store
        long t0 = System.nanoTime();
        switch (mode) {
            case "sequential":
                for (int i = 0; i < files.size(); i++) results.put(files.get(i), safeProcess(files.get(i), i + 1));
                break;
            case "executor": {
                ExecutorService ex = Executors.newFixedThreadPool(pool);
                Map<String, Future<Result>> futures = new LinkedHashMap<>();
                for (int i = 0; i < files.size(); i++) { final String f = files.get(i); final int idx = i + 1; futures.put(f, ex.submit(() -> safeProcess(f, idx))); }
                for (Map.Entry<String, Future<Result>> e : futures.entrySet()) {
                    try { results.put(e.getKey(), e.getValue().get(60, TimeUnit.SECONDS)); }
                    catch (ExecutionException x) { results.put(e.getKey(), Result.failed(e.getKey(), Status.ERROR, "task died: " + x.getCause())); }   // Errors end up here
                    catch (TimeoutException x)   { e.getValue().cancel(true); results.put(e.getKey(), Result.failed(e.getKey(), Status.TIMEOUT, "no result in 60 s")); }
                }
                ex.shutdown();
                break;
            }
            case "parallel": {
                final List<String> fl = files;
                IntStream.range(0, fl.size()).parallel().forEach(i -> {
                    try { results.put(fl.get(i), safeProcess(fl.get(i), i + 1)); }
                    catch (Throwable t) { results.put(fl.get(i), Result.failed(fl.get(i), Status.ERROR, "task died: " + t)); }   // without this an Error would abort the whole stream
                });
                break;
            }
            default: {   // threadperfile
                List<Thread> ts = new ArrayList<>();
                try {
                    for (int i = 0; i < files.size(); i++) {
                        final String f = files.get(i); final int idx = i + 1;
                        Thread t = new Thread(() -> { try { results.put(f, safeProcess(f, idx)); } catch (Throwable x) { results.put(f, Result.failed(f, Status.ERROR, "task died: " + x)); } });
                        t.start(); ts.add(t);
                    }
                } catch (Throwable t) { System.out.println("!! could not create thread #" + (ts.size() + 1) + ": " + t); }
                for (Thread t : ts) t.join();
                for (int i = 0; i < files.size(); i++) results.putIfAbsent(files.get(i), Result.failed(files.get(i), Status.ERROR, "never started (thread creation failed)"));
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;

        // ---------- summary ----------
        EnumMap<Status, Integer> count = new EnumMap<>(Status.class); for (Status s : Status.values()) count.put(s, 0);
        int pass = 0, fail = 0; double sumPct = 0;
        for (Result r : results.values()) { count.merge(r.status, 1, Integer::sum); if (r.status == Status.OK) { if (r.pass) pass++; else fail++; sumPct += r.percent; } }
        TreeMap<String, Result> sorted = new TreeMap<>(results);
        MessageDigestHolder h = new MessageDigestHolder(); for (Result r : sorted.values()) h.add(r.line());
        System.out.println("MODE=" + mode + (mode.equals("executor") ? " pool=" + pool : "") + " latency=" + LATENCY_MS + "ms files=" + files.size());
        System.out.println("  processed OK=" + count.get(Status.OK) + " (PASS " + pass + ", FAIL " + fail + ", avg " + String.format("%.2f", count.get(Status.OK) == 0 ? 0 : sumPct / count.get(Status.OK)) + "%)"
            + "  CORRUPT=" + count.get(Status.CORRUPT) + "  MISSING=" + count.get(Status.MISSING) + "  ERROR=" + count.get(Status.ERROR) + "  TIMEOUT=" + count.get(Status.TIMEOUT));
        int accounted = count.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("  accounted " + accounted + " of " + files.size() + " files, map size " + results.size() + " -> " + (accounted == files.size() && results.size() == files.size() ? "COMPLETE" : "INCOMPLETE !!"));
        System.out.println("  time=" + ms + " ms   peakThreads=" + ManagementFactory.getThreadMXBean().getPeakThreadCount() + "   peakRSS=" + peakRssMb() + " MB   resultDigest=" + h.digest());
        if (System.getProperty("details") != null) sorted.values().forEach(r -> System.out.println("    " + r.line()));
        System.exit(0);
    }

    static final class MessageDigestHolder {
        final java.security.MessageDigest md; MessageDigestHolder() { try { md = java.security.MessageDigest.getInstance("SHA-256"); } catch (Exception e) { throw new RuntimeException(e); } }
        void add(String s) { md.update(s.getBytes(StandardCharsets.UTF_8)); }
        String digest() { StringBuilder sb = new StringBuilder(); byte[] d = md.digest(); for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i])); return sb.toString(); }
    }
    static long peakRssMb() {
        try { for (String l : Files.readAllLines(Paths.get("/proc/self/status"))) if (l.startsWith("VmHWM")) return Long.parseLong(l.replaceAll("\\D+", "")) / 1024; } catch (Exception e) { }
        return -1;
    }
}
