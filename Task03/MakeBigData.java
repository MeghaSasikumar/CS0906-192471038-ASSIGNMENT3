import java.io.*;
/** Creates N corrupt store files in a directory plus an index file, to reproduce "Too many open files". */
public class MakeBigData {
    public static void main(String[] a) throws IOException {
        int n = Integer.parseInt(a[1]); new File(a[0]).mkdirs();
        try (PrintWriter idx = new PrintWriter(a[0] + "/index.txt")) {
            for (int i = 0; i < n; i++) {
                String f = a[0] + "/s" + i + ".txt";
                try (PrintWriter w = new PrintWriter(f)) { w.println("Pen,bad" + i); }
                idx.println(f);
            }
        }
    }
}
