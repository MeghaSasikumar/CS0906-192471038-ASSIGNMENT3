import java.io.*;
import java.util.*;

/**
 * Q3 - Daily sales report generator, three versions:
 *   leaky  : manual close() placed after processing (the bug: skipped when an exception occurs)
 *   manual : try-catch-finally, every close() in finally (correct but verbose)
 *   twr    : try-with-resources
 * Usage: java SalesReport <leaky|manual|twr> <file-with-store-paths> <summary-output>
 */
public class SalesReport {
    static int openHandles = 0;      // files currently open (counted by TrackedReader)
    static int maxOpen = 0;

    /** Reader that counts how many files are open and which were opened/closed. */
    static class TrackedReader extends BufferedReader {
        final String name; boolean closed = false;
        TrackedReader(String name) throws FileNotFoundException {
            super(new FileReader(name));
            this.name = name; openHandles++; maxOpen = Math.max(maxOpen, openHandles);
        }
        @Override public void close() throws IOException {
            if (!closed) { closed = true; openHandles--; }
            super.close();
        }
    }

    static final List<String> LOG = new ArrayList<>();
    static void log(String msg) { LOG.add(msg); System.out.println("[LOG] " + msg); }

    static double sumStore(BufferedReader r) throws IOException {
        double total = 0; String line;
        while ((line = r.readLine()) != null) {
            String[] p = line.split(",");
            total += Double.parseDouble(p[1].trim());     // NumberFormatException on corrupt data
        }
        return total;
    }

    static void leaky(List<String> stores, String out) throws IOException {
        PrintWriter w = null;
        try {
            w = new PrintWriter(new FileWriter(out));
            double grand = 0; List<String> failed = new ArrayList<>();
            for (String s : stores) {
                try {
                    BufferedReader r = new TrackedReader(s);
                    double t = sumStore(r);
                    r.close();                              // BUG: never reached if sumStore throws
                    w.printf("%s %.2f%n", s, t); grand += t;
                } catch (IOException | RuntimeException e) {
                    log("FAILED store " + s + " : " + e); failed.add(s);
                }
            }
            w.printf("TOTAL %.2f%nFAILED %s%n", grand, failed);
        } finally {
            if (w != null) w.close();
        }
    }

    static void manual(List<String> stores, String out) throws IOException {
        PrintWriter w = null;
        try {
            w = new PrintWriter(new FileWriter(out));
            double grand = 0; List<String> failed = new ArrayList<>();
            for (String s : stores) {
                BufferedReader r = null;
                try {
                    r = new TrackedReader(s);
                    double t = sumStore(r);
                    w.printf("%s %.2f%n", s, t); grand += t;
                } catch (IOException | RuntimeException e) {
                    log("FAILED store " + s + " : " + e); failed.add(s);
                } finally {
                    if (r != null) { try { r.close(); } catch (IOException e) { log("close failed " + s); } }
                }
            }
            w.printf("TOTAL %.2f%nFAILED %s%n", grand, failed);
        } finally {
            if (w != null) w.close();
        }
    }

    static void twr(List<String> stores, String out) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(out))) {
            double grand = 0; List<String> failed = new ArrayList<>();
            for (String s : stores) {
                try (BufferedReader r = new TrackedReader(s)) {
                    double t = sumStore(r);
                    w.printf("%s %.2f%n", s, t); grand += t;
                } catch (IOException | RuntimeException e) {
                    log("FAILED store " + s + " : " + e); failed.add(s);
                }
            }
            w.printf("TOTAL %.2f%nFAILED %s%n", grand, failed);
        }
    }

    public static void main(String[] args) throws IOException {
        String mode = args[0];
        List<String> stores = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new FileReader(args[1]))) {
            String l; while ((l = in.readLine()) != null) if (!l.isBlank()) stores.add(l.trim());
        }
        switch (mode) {
            case "leaky":  leaky(stores, args[2]);  break;
            case "manual": manual(stores, args[2]); break;
            case "twr":    twr(stores, args[2]);    break;
            default: throw new IllegalArgumentException("mode must be leaky|manual|twr");
        }
        System.out.println("MODE=" + mode + " stores=" + stores.size() + " maxOpenDuringRun=" + maxOpen
                + " FILES LEFT OPEN AT END=" + openHandles);
    }
}
