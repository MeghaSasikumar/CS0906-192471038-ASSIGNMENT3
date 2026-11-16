# Task 03 - Resource Safety, Debugging and Robust Code (Daily Sales Report Generator)

## Compile and run
```bash
javac *.java
java SalesReport leaky  input.txt report_leaky.txt    # manual close() AFTER processing (buggy)
java SalesReport manual input.txt report_manual.txt   # try-catch-finally, close() in finally
java SalesReport twr    input.txt report_twr.txt      # try-with-resources
java SuppressedDemo                                    # closing order + suppressed exception
java MakeBigData /tmp/big 400                          # then: (ulimit -n 150; java SalesReport leaky /tmp/big/index.txt out.txt)
```
`input_valid.txt` = 3 good store files, `input.txt` = 5 files of which 2 are corrupt (`Pen,abc`, `Pen,x1`). `output.txt` = result of all three versions on `input.txt`.

## Results (all captured in `evidence/`)
| Check | Result |
|---|---|
| Valid input, all 3 versions | Identical reports (`diff` empty): total **2271.49**, `FAILED []` |
| Failing input, manual vs try-with-resources | Identical reports: total 2271.49, `FAILED [store3_corrupt, store5_corrupt]` |
| Files left open after failing input (2 corrupt files) | leaky **2**, manual **0**, twr **0** |
| 400 corrupt files with `ulimit -n 150` | leaky: leak -> "Too many open files" on 256 files -> crash, incomplete report. manual/twr: all 400 handled, 0 left open |

## Shared work
* Version 1 (`leaky`) and Version 2 (`manual`) are the two manual versions; Version 3 (`twr`) uses try-with-resources. The reports are verified identical with `diff`.
* The first manual attempt closes the reader on the line after processing - natural to write, and wrong, as the debugging below shows.

## Student A - Debugging and experimental analysis
* **Where the leak happens** (step-through / breakpoint in `leaky`): set a breakpoint on `double t = sumStore(r);` for `store3_corrupt.txt`. `TrackedReader` has just incremented `openHandles` to 1. Stepping over `sumStore` jumps straight to the `catch` block because `Double.parseDouble("abc")` throws; the line `r.close()` is never executed, and `r` is out of scope in the catch block, so nothing can close it later.
* **Input that makes the manual version fail part-way:** `data/store3_corrupt.txt` (non-numeric amount) and `data/store5_corrupt.txt`.
* **Files left open for the same failing input:** leaky 2, manual 0, twr 0 (`evidence/failing_runs.txt`).
* **Logging:** every failure is logged with the store that caused it, e.g. `[LOG] FAILED store data/store3_corrupt.txt : java.lang.NumberFormatException: For input string: "abc"`.

## Student B - Design and correctness
* **Resources and where they must be closed:** (1) the summary `PrintWriter`/`FileWriter` - opened once, closed at the very end; (2) one `BufferedReader`/`FileReader` per store file - closed after that store, whether it succeeded or failed.
* **Why the manual version leaks:** `close()` is ordinary code in the `try` body, so any exception skips it. Even in a `finally`, a manual version leaks if `close()` of one resource throws before the next resource's `close()`, or if the second resource's constructor throws after the first was opened.
* **How try-with-resources guarantees closing:** the compiler generates a hidden `finally` that calls `close()` on every resource that was successfully opened, even on `return`/`break`/exception. Resources are closed in **reverse order of opening** (`evidence/suppressed_demo.txt`: B closed first, then A).
* **Suppressed exception:** if the body throws (primary) and `close()` also throws, the `close()` exception is attached to the primary through `addSuppressed` instead of replacing it; read it with `getSuppressed()`. In `SuppressedDemo` the caught exception is "PRIMARY failure in body" and "close() of B failed" is listed as suppressed - the root cause is not lost.

## Joint engineering decision - when is try-with-resources not enough?
* When the report file must be **all-or-nothing**: closing a half-written file still leaves a corrupt report; write to a temp file and rename atomically on success.
* When the failure of one store should not stop the others - needs an inner try/catch per store (as here) plus logging/retry.
* When resources are not `AutoCloseable`, are opened outside the `try (...)` header, or are held by other threads/pools.
* When `close()`/flush itself can fail and the failure must be acted on (suppressed exceptions are easy to ignore), when a timeout/retry policy is needed, or when the limit is not files but memory or time.

## Alternative concept - `close()` in `finally`, or no closing
For f store files with n total lines:
| Version | Time | Max file handles held open when a failure occurs |
|---|---|---|
| try-with-resources | O(n) | **1** (plus the writer) - the failing file is closed at once |
| explicit `close()` in `finally` | O(n) | **1** (plus the writer) - same safety, more code and a chance of mistakes |
| no closing at all (or `close()` skipped on failure) | O(n) | up to **f** - each failing file stays open until GC (the leaky version holds one handle per failed file; 400 failures -> 400 handles -> "Too many open files") |

Processing time is the same, O(n), in every version; only space (open handles, plus the buffer each handle holds) differs: O(1) versus O(f). Relying on the garbage collector to close files is unreliable because finalization timing is not guaranteed.
