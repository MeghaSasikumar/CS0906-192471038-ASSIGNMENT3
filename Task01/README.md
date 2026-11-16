# Task 01 - Handling Failure in a Bank ATM Simulation

**Java:** OpenJDK 21 (source is plain Java 8+ compatible).

## Compile and run
```bash
javac *.java
java ATM < input.txt            # main program; compare with output.txt
java UncaughtDemo               # Student B: uncaught stack trace
java ErrorCodeATM               # alternative concept (no exceptions)
java CostBenchmark              # exception vs error-code cost
```
`BrokenCatchOrder.java.txt` is kept as `.txt` because it intentionally does not compile (see `evidence/`).

## Result
All 8 test inputs reach the intended `catch` block and the `finally` block runs every time (see `output.txt`).

## Shared work - failure points and classification
| # | Failure point | Exception | Category | Caught in |
|---|---|---|---|---|
| 1 | Card-reader file missing | `FileNotFoundException` | checked | catch 5 |
| 2 | Card unreadable / card does not match account | `IOException` | checked | catch 6 |
| 3 | Letters typed instead of a number | `NumberFormatException` | unchecked | catch 1 (multi-catch) |
| 4 | Negative / zero amount | `IllegalArgumentException` | unchecked | catch 2 |
| 5 | Unknown account | `IllegalStateException` | unchecked | catch 3 |
| 6 | Amount greater than balance | `InsufficientFundsException` (user-defined) | checked | catch 4 |
| 7 | Input line with missing field | `ArrayIndexOutOfBoundsException` | unchecked | catch 1 (multi-catch) |
| - | JVM running out of memory etc. | `OutOfMemoryError` | **Error** - not caught, cannot be recovered by the ATM | - |

## Student A - Exception design
**Relevant part of the Throwable hierarchy**
```
Throwable
 |- Error                         (OutOfMemoryError, StackOverflowError - not handled)
 '- Exception
     |- IOException               (checked)
     |   '- FileNotFoundException
     |- InsufficientFundsException (user-defined, checked)
     '- RuntimeException          (unchecked)
         |- IllegalArgumentException
         |   '- NumberFormatException
         |- IllegalStateException
         '- IndexOutOfBoundsException
             '- ArrayIndexOutOfBoundsException
```
**Order of catch blocks - justification.** Java checks catch blocks top to bottom and uses the first that matches, so a subclass must appear before its superclass:
* `NumberFormatException` (in the multi-catch) comes before `IllegalArgumentException`, its parent. A multi-catch cannot list a class and its own subclass together, so they are separate blocks.
* `FileNotFoundException` comes before `IOException`.
* The remaining blocks are unrelated, so their order only affects readability (most specific business error first).

**What `finally` guarantees.** The "card returned, session closed" line runs after success, after every handled failure, and even if a catch block itself throws. In a real ATM this is where the card is ejected and the session released, so the customer is never left with a stuck card. (The only things that skip it are `System.exit()` or the JVM dying.)

## Student B - Testing and tracing
| Test | Input | Path triggered | Catch block | `finally` ran? |
|---|---|---|---|---|
| 1 | `1001 500 card_1001.txt` | success | none | yes |
| 2 | `1001 abc ...` | letters instead of number | catch 1 | yes |
| 3 | `1001 -200 ...` | negative amount | catch 2 | yes |
| 4 | `1001 99999 ...` | more than balance | catch 4 | yes |
| 5 | `1001 100 missing_card.txt` | missing card file | catch 5 | yes |
| 6 | `1002 100 card_1001.txt` | card/account mismatch | catch 6 | yes |
| 7 | `9999 100 card_9999.txt` | unknown account | catch 3 | yes |
| 8 | `1001` | missing fields | catch 1 | yes |

**Stack trace of an uncaught exception** (`evidence/uncaught_stacktrace.txt`), read bottom-up:
* `at UncaughtDemo.main(UncaughtDemo.java:7)` - where the call chain started (`withdraw("abc")`).
* `at UncaughtDemo.withdraw(UncaughtDemo.java:4)` - called `parse`.
* `at UncaughtDemo.parse(UncaughtDemo.java:3)` - called `Double.parseDouble`.
* `Double.parseDouble` -> `FloatingDecimal...` - JDK internals where the text failed to parse; the top line is where the exception object was created.
* The first line `Exception in thread "main" java.lang.NumberFormatException: For input string: "abc"` shows the thread, exception type and message. Nobody caught it, so the thread died and `"never printed"` was not printed.

**Broader catch before narrower** (`evidence/broken_catch_order_compile_error.txt`): the compiler rejects it - `exception FileNotFoundException has already been caught`. The narrower block would be unreachable.

## Joint design question - should we use one `catch (Exception e)`?
Acceptable only at the outermost boundary (e.g. the top of the ATM loop or a thread's `run`) to log the failure and fail safely so the machine does not crash. What is lost: the program can no longer react differently (retry the card read, ask for a new amount, or stop), real bugs such as `NullPointerException` get silently treated like user mistakes, unchecked exceptions are swallowed unintentionally, and the compiler's help with checked exceptions is thrown away.

## Alternative concept - error codes instead of exceptions
`ErrorCodeATM.java` validates first (`Scanner.hasNextDouble()`) and returns `-1/-2/-3`.
* **Readability:** exception version keeps the happy path clean and the failure handling separate; the error-code version needs an `if` after every call, and callers can forget to check the code.
* **Number of checks:** error codes need one explicit check per failure per call (3 here, and the caller must test the result again); exceptions need none on the happy path.
* **Time:** `evidence/benchmark.txt` measured ~1.3 ns per error-code call versus ~163 ns per call when half the calls throw (~325 ns per actual throw) at depth 1, and ~1240 ns per call (~2500 ns per throw) at stack depth 50. Creating a `Throwable` calls `fillInStackTrace()`, which walks the stack, so cost is O(d) in stack depth d; returning a code is O(1).
* **Space:** an exception object holds the stack trace (O(d) memory); an error code is one int (O(1)).
* **Conclusion:** use exceptions for rare, exceptional failures; error codes only for hot paths where "failure" is frequent and expected.
