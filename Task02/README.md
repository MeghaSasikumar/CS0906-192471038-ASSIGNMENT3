# Task 02 - User-Defined Exceptions and Propagation in an Online Examination Portal

## Compile and run
```bash
javac *.java
java StorageDemo          # "Complete the Code" program (TODO 1 done); compare with output.txt
java Portal               # 3-layer portal, 6 scenarios       (evidence/portal_output.txt)
java -Dtrace=1 Portal     # also prints the full chained stack trace
java LostCauseDemo        # cause kept vs cause lost
java UncheckedPortal      # alternative concept: unchecked exceptions
java ChainBenchmark       # cost of stack trace / chaining
```

## Complete the Code - TODO 1
```java
throw new SubmissionStorageException("Could not store " + fileName, e);
```
Output (`output.txt`) matches the expected output:
```
Saved answer01.pdf
Error: Could not store answer02.pdf
Cause: Disk quota exceeded
```
**What if the IOException were not passed to the constructor?** `getCause()` returns `null`, so `e.getCause().getMessage()` throws `NullPointerException` and the `Cause:` line is never printed (`evidence/no_cause_output.txt`):
`Exception in thread "main" java.lang.NullPointerException: Cannot invoke "java.lang.Throwable.getMessage()" because the return value of "ExamException.getCause()" is null`

## Shared work - design
Hierarchy (in `Portal.java`):
```
Exception
 |- ExamException                      (checked base)
 |   |- ExamClosedException            (checked)
 |   |- NotRegisteredException         (checked)
 |   |- AnswerTooLargeException        (checked)
 |   '- SubmissionStorageException     (checked, wraps IOException)
 '- RuntimeException
     '- InvalidSubmissionException     (unchecked)
```
Layers: `Controller` (catches and talks to the student) -> `ExamService` (business rules, `throws ExamException`) -> `StorageLayer` (`throws SubmissionStorageException`, wraps `IOException`).

## Student A - Hierarchy and propagation
**Checked vs unchecked - reasons**
| Exception | Type | Reason |
|---|---|---|
| `ExamClosedException`, `NotRegisteredException`, `AnswerTooLargeException` | checked | Expected business failures the caller can react to (show a message). The compiler forces every layer to deal with them. |
| `SubmissionStorageException` | checked | Recoverable (retry), and a meaningful wrapper for a low-level `IOException`. |
| `InvalidSubmissionException` | unchecked | A null/blank argument is a programming error; the student cannot fix it and forcing `throws` everywhere adds noise. |

**Propagation path of one exception (storage failure, case 5):**
`StorageLayer.write` creates `IOException("No space left on device")` -> caught and wrapped in `SubmissionStorageException` (cause kept) -> propagates through `ExamService.submit` (declared with `throws ExamException`, not caught) -> reaches `Controller.submit`, where `catch (SubmissionStorageException e)` finally handles it.
**Message the student sees:** "We could not save your answer, please retry. (Could not store FULL_ans5.pdf)". The raw IOException text goes only to the log line, not to the student.

## Student B - Scenarios and chaining evidence
| Case | Input | Exception | Result seen by student |
|---|---|---|---|
| 1 | valid | none | Submission accepted |
| 2 | after 17:00 | `ExamClosedException` | rejected - window closed |
| 3 | unregistered student | `NotRegisteredException` | rejected - not registered |
| 4 | 800 KB file | `AnswerTooLargeException` | rejected - limit 500 KB |
| 5 | storage full | `SubmissionStorageException` (cause `IOException`) | "could not save, please retry" |
| 6 | blank file name | `InvalidSubmissionException` (unchecked) | internal error |

**Chained stack trace** (`evidence/chained_stacktrace.txt`): `Portal$SubmissionStorageException: Could not store FULL_ans5.pdf` ... `Caused by: java.io.IOException: No space left on device ... 3 more`. "... 3 more" means the remaining frames are identical to the enclosing exception's frames.
**Without cause** (`evidence/lost_cause.txt`): the `Caused by:` section disappears and `getCause()` is `null`. We lose the original error text (`Disk quota exceeded`) and the exact line where it happened - debugging has to start from the wrapper only.

## Joint conclusion - why wrap `IOException`?
A `SubmissionStorageException` speaks the portal's language (what failed, in terms the layers above understand) and hides storage implementation details. The UI does not depend on `java.io` (we can switch to a database or cloud storage without touching the controller), the student gets a safe message instead of internal disk details, and the original `IOException` is still available through `getCause()` for logs.

## Alternative concept - unchecked exceptions
`UncheckedPortal.java` extends `RuntimeException`.
* **Signatures:** no `throws` clauses anywhere; `StorageLayer.write`, `ExamService.submit` become shorter.
* **Calling code:** no mandatory `try/catch`. It compiles even if nobody handles the error (`evidence/unchecked_uncaught.txt` - the program just dies). Handling becomes a convention instead of a compiler guarantee, so forgotten handlers show up only at run time.
* **Time cost** (`evidence/chain_benchmark.txt`, ns per exception): depth 5 ~400, depth 50 ~1300-1500, depth 200 ~4400; with stack trace disabled (`writableStackTrace=false`) ~30-150. Capturing the stack trace costs O(d) time in stack depth d and O(d) space (one stack-frame record per frame). The small difference between "with cause" and "no cause" is within measurement noise.
* **Chaining and memory:** chaining adds only one reference field (O(1)) per exception, but the cause keeps its own stack trace alive, so one wrapped exception holds roughly the sum of its chain's traces (O(d1 + d2 + ...)) until it is garbage collected.
