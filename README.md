# CS0906 - Programming in Java - Assignment 3 (CO3)
**Exception handling and multithreading**

| | |
|---|---|
| Student | Megha Sasikumar (GitHub: MeghaSasikumar) |
| Register number | __192471038_____ (fill in) |
| Group partner (Student B) | ____________ (fill in) |
| Java version | OpenJDK 21 (code uses only Java 8+ features; compiles with `javac`, no Maven needed) |
| Test machine | 2 CPU cores, Linux |

Each task is in its own folder (`Task01` ... `Task07`) containing: the Java source, `input.txt` / test data, `output.txt` (real captured output), `evidence/` (stack traces, run tables, benchmarks) and a `README.md` with the answers to every question of that task.

## How to compile and run
```bash
cd TaskNN
javac *.java            # compiles everything in the task folder
```
| Task | Topic | Run | Result |
|---|---|---|---|
| Task01 | ATM failure handling | `java ATM < input.txt` | All 8 test inputs reach the intended catch block; `finally` always runs. Exception vs error-code cost measured. |
| Task02 | Exam portal exceptions and chaining | `java StorageDemo`, `java Portal` | TODO 1 done; output matches expected. Chained cause preserved; without cause -> `NullPointerException`. |
| Task03 | try-with-resources and resource leaks | `java SalesReport twr input.txt out.txt` (also `leaky`, `manual`) | Reports identical across versions; leaky version leaves 2 files open and crashes with "Too many open files" at scale. |
| Task04 | Threads and life cycle | `java ScanStations thread < input.txt` | Total 48 after `join()`; 10 runs show 5 different finish orders; executor scales ~n/p. |
| Task05 | Race condition in seat booking | `java SeatBooking`, `java RaceLab race` | TODO 1 done: `Seats sold: 10`. Race reproduced (double bookings in 10/10 runs) and fixed. |
| Task06 | Deadlock and producer-consumer | `java TokenCounter`, `java WalletDeadlock deadlock` | TODO 1+2 done: sum 210. `if` instead of `while` gives wrong sums; `notify()` hangs 39/40 runs; deadlock reproduced and prevented. |
| Task07 | Concurrent fault-tolerant processor | `java ResultProcessor small_index.txt executor 4` | Hand-checked set matches exactly; 10,000-file run complete and identical for 1/2/4/8 threads; fault injection survived. |

## Notes on the results
* Every number in the task READMEs was measured by running the code in this repository (see the `evidence/` folders). Timings come from a 2-core machine and short runs, so treat them as relative.
* Some experiments gave results that were different from what theory alone would predict, and these are reported as observed: the unsynchronized given seat-booking program did **not** fail in 10 plain runs (Task05), thread priorities had no visible effect (Task04), and extra threads made the local-disk run slower (Task07).
* Task07 slow-storage measurements use a simulated 2 ms delay per file (stated wherever used).

## Individual Contribution Record (complete before submission)
| Question | Student A - primary responsibility | Student B - primary responsibility | Joint |
|---|---|---|---|
| Q1 | Exception design, hierarchy, catch order, `finally` | Test inputs, stack trace, catch-order experiment | Implementation, joint design question |
| Q2 | Hierarchy, checked/unchecked reasons, propagation path | Scenarios, chaining evidence, lost-cause demo | Implementation, joint conclusion |
| Q3 | (reversed) debugging, leak counts, logging | (reversed) design/correctness, suppressed exceptions | Both versions, engineering decision |
| Q4 | Thread design, life-cycle diagram, `run()` vs `start()` | 10 runs, priorities, daemon | Implementation, joint conclusion |
| Q5 | Reproduce and explain the race, critical section | Timing comparison, static sync, intrinsic locks | Implementations, deployment decision |
| Q6 | Producer-consumer design, `wait`/`notify` | Deadlock analysis and prevention | Both parts, joint conclusion |
| Q7 | Stage 2 design | Stage 3 implementation and measurement | Stage 1 and Stage 4 |

(The "primary responsibility" columns restate the assignment's own split. Please edit this table so it reflects who actually did what in your pair, because each student will be verified individually.)
