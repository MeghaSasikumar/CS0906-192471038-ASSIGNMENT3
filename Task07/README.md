# Task 07 - Concurrent, Fault-Tolerant Processor from First Principles (Semester Result Processing)

## Compile and run
```bash
javac *.java
java ResultProcessor small_index.txt sequential              # hand-checked set (output.txt)
java -Ddetails=1 ResultProcessor small_index.txt executor 4  # per-file lines
java ScoreFileGenerator /tmp/big 10000 20 25                 # 10,000 files: every 20th corrupt, every 25th missing
java ResultProcessor /tmp/big/index.txt executor 8
java -Dlatency=2 ResultProcessor /tmp/big/index.txt executor 8   # simulate 2 ms slow storage per file
java -Dinject=true ResultProcessor /tmp/big/index.txt executor 4 # fault injection
java ResultProcessor /tmp/big/index.txt parallel | threadperfile  # alternatives
```
The 10,000 generated files are not committed (regenerate with the generator, seed fixed so results are repeatable). Machine: 2 CPU cores, OpenJDK 21. Raw outputs are in `evidence/`.

## Stage 1 - Interpret the problem (joint, 2 marks)
**Parallel tasks and shared data**
* Each score file is independent: read -> validate -> compute. These 10,000 tasks can run in parallel (no task needs another's output).
* Shared data: only the **result store** (one entry per file) and the final summary counters. Input files are read-only; each task works on its own local variables.

**Failures and handling**
| Failure | Where | Handling |
|---|---|---|
| File listed but missing | read | `MissingScoreFileException` -> status MISSING |
| Other I/O error (permissions, read error) | read | `ScoreFileException` (cause kept) -> status ERROR |
| Empty / truncated / wrong number of lines, no header or checksum | validate | `CorruptScoreFileException` -> CORRUPT |
| Non-numeric marks, marks outside 0-100 | validate | `CorruptScoreFileException` -> CORRUPT |
| Tampered content (SHA-256 mismatch) | validate | `CorruptScoreFileException` -> CORRUPT |
| Unexpected `RuntimeException` (bug) | any | caught in the task -> ERROR |
| `Error` (e.g. `StackOverflowError`) | any | not caught in the task; surfaces as `ExecutionException` at `Future.get()` -> ERROR |
| Task takes too long | any | `Future.get(60 s)` -> `TimeoutException` -> cancel, status TIMEOUT |

**Measurable requirements**
* R1 (completeness): OK + CORRUPT + MISSING + ERROR + TIMEOUT = number of files, and result-store size = number of files.
* R2 (fault tolerance): with injected failures the run finishes, produces a summary, and exits normally (no uncaught exception).
* R3 (correctness): the result digest (SHA-256 over all sorted result lines) is identical for 1, 2, 4, 8 threads and the sequential baseline; the small hand-checked set matches the hand calculation exactly.
* R4 (speed): with 2 ms storage latency per file, 8 threads finish in at most 1/4 of the 1-thread time.
* R5 (resources): peak thread count is bounded by the pool size (+ constant), not by the number of files.

## Stage 2 - Design (Student A, 3 marks)
**Class diagram**
```
 ResultProcessor (main)
    |-- uses --> ExecutorService (fixed pool of p threads)
    |-- submits --> Callable<Result>  : () -> safeProcess(file, index)
    |-- keeps --> Map<String, Future<Result>>
    '-- writes to --> ConcurrentHashMap<String, Result>   (shared result store)

 Result (immutable)           Status {OK, CORRUPT, MISSING, ERROR, TIMEOUT}
   file, status, student, total, percent, pass, message

 Exception
   '- ScoreFileException (checked)
        |- MissingScoreFileException
        '- CorruptScoreFileException
```
**Pseudocode**
```
files    = read index
store    = new ConcurrentHashMap
pool     = newFixedThreadPool(p)
for each file: futures[file] = pool.submit( () -> safeProcess(file) )
for each (file, future):
    try    store[file] = future.get(60 s)
    catch ExecutionException -> store[file] = ERROR(cause)      // Errors that escaped the task
    catch TimeoutException   -> future.cancel; store[file] = TIMEOUT
pool.shutdown()
print summary from store; assert count(all statuses) == files.size

safeProcess(file):
    try   read -> validate (format, range, checksum) -> compute total, percent, pass -> Result OK
    catch Missing / Corrupt / ScoreFileException / Exception -> Result(failed status, message)
```
**Choice of concurrent collection: `ConcurrentHashMap<String, Result>`.** Safe for many writers without external locking, O(1) put and get, keyed by file name so each file has exactly one entry (completeness is checkable as `size() == files`), and no lost updates. Alternatives rejected: `Collections.synchronizedList` (one lock, writers serialise), `CopyOnWriteArrayList` (copies the whole array on each write, O(n) per add -> O(n^2) overall for 10,000), `ConcurrentLinkedQueue` (no lookup by file, no duplicate check).

**Exception handling so one failure cannot stop the run.** (1) `safeProcess` converts every `Exception` into a `Result` value, so `call()` normally never throws; (2) the collector wraps each `Future.get()` in its own try/catch, so even an `Error` or timeout in one task becomes one ERROR/TIMEOUT entry; (3) failures are data, not control flow, so the loop continues. The storage layer never leaks `IOException`: it is wrapped (cause kept) as in Q2.

## Stage 3 - Implement and measure (Student B, 3 marks)
**Hand-checked set** (`small_set/`, `EXPECTED_BY_HAND.txt`, `output.txt`): 10 entries, expected by hand OK=6 (PASS 4, FAIL 2), CORRUPT=3, MISSING=1, average 64.94%. Program output: identical, including boundary cases (40 marks passes, 39 fails) and per-file totals. Sequential and 4-thread runs give the same digest `696f5739033e`.

**Scale test: 10,000 files** (9,200 valid, 400 corrupt, 400 missing; same digest `4eea78e6b754` for every configuration -> results are identical regardless of threads).

*Measurement A - real local disk, 2 cores* (`evidence/benchmark_local.txt`, 3 runs each):
| Threads | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| sequential (no pool) | 568 ms | | |
| pool = 1 | 673 | 677 | 794 |
| pool = 2 | 803 | 500 | 783 |
| pool = 4 | 871 | 1017 | 942 |
| pool = 8 | 1130 | 1124 | 1081 |
Adding threads made it **slower** (about 0.7 s -> 1.1 s). Each file is ~100 bytes and cached by the OS, so one file takes about 60 us of CPU; there is nothing to wait for, and on 2 cores the pool only adds task submission, queueing, `Future` and context-switch overhead.

*Measurement B - simulated slow storage (2 ms I/O wait per file, `-Dlatency=2`; models a network share)* (`evidence/benchmark_latency2ms.txt`):
| Threads | 1 | 2 | 4 | 8 | 16 | 32 |
|---|---|---|---|---|---|---|
| Time (ms) | 21,796 | 10,838 | 5,502 | 2,828 | 1,562 | 1,110 |
| Speed-up vs 1 | 1.0x | 2.0x | 4.0x | 7.7x | 14.0x | 19.6x |
The latency is *simulated* with `Thread.sleep`, so this shows the behaviour of an I/O-bound workload; it is not a measurement of a real network disk. R4 is met (8 threads: 2.8 s <= 21.8/4 = 5.4 s).

**Fault injection** (`evidence/fault_injection.txt`; every 1000th file throws `RuntimeException`, every 2500th throws `StackOverflowError`): 12 files recorded as ERROR (10 RuntimeException + 2 Error), MISSING dropped from 400 to 388 because 12 injected files were also missing ones, and **10000 of 10000 accounted** in all three designs; the run finished normally (R1, R2).

**Failures not handled correctly / not verified (honest record)**
1. The `TIMEOUT` path was **not exercised** by any test (all tasks finished in time); it is code-reviewed only. Also `cancel(true)` only stops a task that responds to interruption - a task stuck in a non-interruptible native read would keep its pool thread occupied.
2. Parallel-stream and thread-per-file versions had to be given an extra `catch (Throwable)` inside the task: without it an `Error` would escape (see alternatives below). I confirmed the wrapped versions only; I did not run the unwrapped stream version.
3. Out-of-memory with very large individual files was not tested.

## Stage 4 - Defend the design (joint, 2 marks)
**Recommended pool size.** For CPU-bound work use about the number of cores (here 2); for I/O-bound work use roughly `cores x (1 + wait/compute)`. Here wait/compute is about 2000 us / 60 us ~ 35, so the formula allows dozens of threads; measurement B shows speed-up continuing to 8 (7.7x), then flattening (16 -> 14x, 32 -> 19.6x) as the CPU and the shared-map/submit overhead become the limit. Recommendation: **8 to 16 threads for slow shared storage; 1 to 2 threads (or plain sequential) for local SSD/cached files.** More threads did not always help because (a) Measurement A: no waiting to overlap, only overhead, (b) only 2 cores can compute at once, (c) every extra thread costs a stack and scheduling work.

**How the design guarantees a complete and correct summary.** Every submitted file has exactly one `Future` and exactly one map key; the collector visits every future, and each possible outcome (value, task `Error`, timeout) writes exactly one entry. The summary is computed only after all futures are done (so no half-finished data), and the program asserts `count(statuses) == files == map size`. Result objects are immutable and `ConcurrentHashMap` publishes them safely. Correctness is evidenced by identical digests for all pool sizes and the hand-checked set.

**One remaining limitation.** Results live only in memory and a failed file is classified once with no retry: if the JVM crashes at file 9,900 all work is lost, and a transient I/O error (a brief network glitch) is recorded as ERROR instead of being retried. A production version would add checkpointing (write results as they complete) and a limited retry policy for transient errors.

## Alternative concept - parallel streams, or one thread per file
**Parallel stream** (`IntStream.range(...).parallel().forEach(...)`): runs on the shared `ForkJoinPool.commonPool()`, sized cores-1 (= 1 worker + the calling thread = 2 threads here).
* *Time:* with 2 ms latency it took **10,781 ms** - same as a fixed pool of 2 (10,838 ms) because the pool is tied to CPU count, not I/O. With `-Djava.util.concurrent.ForkJoinPool.common.parallelism=8` it took 2,843 ms (fixed pool of 8: 2,828 ms). No-latency run (with fault injection): 744 ms, in line with Measurement A.
* *Memory:* O(cores) threads, nothing to size by default. Blocking I/O in the *common* pool also starves other parallel streams in the JVM.
* *Failure handling:* an exception thrown out of the lambda propagates from the terminal operation and ends the stream, so without the `try/catch (Throwable)` we added one bad file would abort the run (reasoning from the API semantics; not run unwrapped). There is no per-task `Future`, so no per-file timeout.

**One thread per file** (10,000 `Thread` objects):
* *Time:* 2 ms latency, quick tasks: 2,092 ms (fast, because tasks are so short that threads end quickly: peak 94 alive).
* *Memory - the real cost* (`evidence/thread_per_file_slow_io.txt`): with 3 s per file the program created **10,006 threads at once and its peak resident memory was 1,485 MB**; a fixed pool of 64 threads ran the same work with 70 threads and 115 MB. Memory = number of threads x (stack size + kernel structures); stacks are reserved at about 1 MB each by default, and the OS limit on threads (`pids`/`ulimit -u`, 32,003 here) can be hit - then `OutOfMemoryError: unable to create native thread`. Space is O(n) instead of O(p).
* *Failure handling:* each thread needs its own try/catch; an uncaught exception kills only that thread (and goes to the default handler), so the result is simply missing unless the code records it - which is why we added the "never started"/Throwable handling. No `Future`, no timeout, no backpressure.

**Summary:** `ExecutorService` + `Callable` + `Future` gives bounded memory O(p) threads, time about n x t / p when I/O bound, a per-task result/exception/timeout channel, and the pool size is a tunable parameter. Parallel streams are shortest to write but their pool size is fixed by CPU count and their error handling is all-or-nothing; thread-per-file is the simplest but its memory grows with the number of files.
