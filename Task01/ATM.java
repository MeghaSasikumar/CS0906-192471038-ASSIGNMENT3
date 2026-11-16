import java.io.*;
import java.util.*;

/** Q1 - ATM withdrawal with try / multi-catch / finally. Reads scenarios from stdin (input.txt). */
public class ATM {
    /** Checked, user-defined: caller must handle or declare it. */
    static class InsufficientFundsException extends Exception {
        InsufficientFundsException(String msg) { super(msg); }
    }

    static final Map<String, Double> BALANCES = new HashMap<>();
    static { BALANCES.put("1001", 5000.0); BALANCES.put("1002", 750.0); }

    /** Every failure point is marked with a FAIL-n comment (used in the analysis table). */
    static double withdraw(String account, String amountText, String cardFile)
            throws IOException, InsufficientFundsException {
        // FAIL-1: card-reader file missing -> FileNotFoundException (checked, subclass of IOException)
        try (BufferedReader card = new BufferedReader(new FileReader(cardFile))) {
            // FAIL-2: unreadable card -> IOException (checked)
            String cardAccount = card.readLine();
            if (cardAccount == null || !cardAccount.trim().equals(account)) {
                throw new IOException("Card does not match account " + account);
            }
        }
        // FAIL-3: letters instead of numbers -> NumberFormatException (unchecked)
        double amount = Double.parseDouble(amountText);
        // FAIL-4: negative or zero amount -> IllegalArgumentException (unchecked)
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive: " + amount);
        Double balance = BALANCES.get(account);
        // FAIL-5: unknown account -> IllegalStateException (unchecked)
        if (balance == null) throw new IllegalStateException("Unknown account " + account);
        // FAIL-6: amount above balance -> InsufficientFundsException (checked, user-defined)
        if (amount > balance) throw new InsufficientFundsException("Requested " + amount + " but balance is " + balance);
        BALANCES.put(account, balance - amount);
        return amount;
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int n = 0;
        while (in.hasNextLine()) {
            String line = in.nextLine().trim();
            if (line.isEmpty()) continue;
            n++;
            System.out.println("Test " + n + ": " + line);
            try {
                String[] p = line.split("\\s+");
                // FAIL-7: line with a missing field -> ArrayIndexOutOfBoundsException (unchecked)
                double cash = withdraw(p[0], p[1], p[2]);
                System.out.println("  Dispensed Rs. " + cash + " (balance " + BALANCES.get(p[0]) + ")");
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {  // multi-catch: unrelated types
                System.out.println("  [catch 1: bad input] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (IllegalArgumentException e) {      // parent of NumberFormatException -> must come AFTER it
                System.out.println("  [catch 2: invalid amount] " + e.getMessage());
            } catch (IllegalStateException e) {
                System.out.println("  [catch 3: account problem] " + e.getMessage());
            } catch (InsufficientFundsException e) {
                System.out.println("  [catch 4: insufficient funds] " + e.getMessage());
            } catch (FileNotFoundException e) {         // subclass of IOException -> must come BEFORE it
                System.out.println("  [catch 5: card reader file missing] " + e.getMessage());
            } catch (IOException e) {
                System.out.println("  [catch 6: card read error] " + e.getMessage());
            } finally {
                System.out.println("  [finally] card returned, session closed");
            }
        }
    }
}
