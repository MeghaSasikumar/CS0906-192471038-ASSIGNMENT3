import java.util.*;

/** Alternative concept: no exceptions - validate in advance and return error codes. */
public class ErrorCodeATM {
    static final int OK = 0, BAD_NUMBER = -1, NEGATIVE = -2, NO_FUNDS = -3;

    static int withdraw(double balance, String text) {
        Scanner s = new Scanner(text);
        if (!s.hasNextDouble()) return BAD_NUMBER;      // check 1
        double amt = s.nextDouble();
        if (amt <= 0) return NEGATIVE;                  // check 2
        if (amt > balance) return NO_FUNDS;             // check 3
        return OK;
    }

    public static void main(String[] args) {
        String[] tests = {"500", "abc", "-5", "99999"};
        for (String t : tests) System.out.println(t + " -> code " + withdraw(5000, t));
    }
}
