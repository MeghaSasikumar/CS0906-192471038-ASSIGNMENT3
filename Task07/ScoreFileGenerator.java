import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Generates score files + an index file.   java ScoreFileGenerator <dir> <count> <corruptEvery> <missingEvery>
 * File format:   STUDENT:<id> / <SUBJECT>,<marks 0-100> x3 / SHA256:<hex of everything above, incl. final newline>
 */
public class ScoreFileGenerator {
    static String sha256(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); for (byte b : d) sb.append(String.format("%02x", b)); return sb.toString();
    }
    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]); dir.mkdirs();
        int n = Integer.parseInt(a[1]), corruptEvery = Integer.parseInt(a[2]), missingEvery = Integer.parseInt(a[3]);
        Random r = new Random(42);
        try (PrintWriter idx = new PrintWriter(new File(dir, "index.txt"))) {
            for (int i = 1; i <= n; i++) {
                String name = String.format("S%05d.txt", i);
                idx.println(new File(dir, name).getPath());
                if (i % missingEvery == 0) continue;                                    // listed but never created -> MISSING
                String body = String.format("STUDENT:S%05d%nMATH,%d%nPHYSICS,%d%nCHEMISTRY,%d%n", i, 20 + r.nextInt(81), 20 + r.nextInt(81), 20 + r.nextInt(81));
                String content = body + "SHA256:" + sha256(body) + "\n";
                if (i % corruptEvery == 0) {
                    switch ((i / corruptEvery) % 4) {
                        case 0: content = body.replace("MATH,", "MATH,x") + "SHA256:" + sha256(body) + "\n"; break;   // non-numeric marks
                        case 1: content = body.replace("PHYSICS,", "PHYSICS,1") + "SHA256:" + sha256(body) + "\n"; break; // tampered (checksum mismatch / >100)
                        case 2: content = ""; break;                                                                    // empty file
                        default: content = body;                                                                        // truncated: no checksum
                    }
                }
                try (PrintWriter w = new PrintWriter(new File(dir, name))) { w.print(content); }
            }
        }
        System.out.println("generated " + n + " index entries in " + dir);
    }
}
