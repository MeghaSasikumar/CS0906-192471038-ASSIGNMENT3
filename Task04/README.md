# Task 04 - Thread Creation and the Thread Life Cycle (Parallel Answer-Script Scanning)

## Compile and run
```bash
javac *.java
java ScanStations thread   < input.txt     # stations by extending Thread  (output.txt)
java ScanStations runnable < input.txt     # stations by implementing Runnable
java RunVsStart                             # run() vs start()
java FinishOrder                            # 10 runs, finish order, priorities
java DaemonDemo user ; java DaemonDemo daemon
java ExecutorStations                       # alternative: fixed thread pool
```
`input.txt` = scripts per station (`A 12`, `B 7`, `C 20`, `D 9`). Test machine: 2 CPU cores, OpenJDK 21.

## Result
Both versions give identical output: 4 stations start (`NEW` -> `TIMED_WAITING` while sleeping), finish, `join()` returns, all become `TERMINATED/alive=false`, and the total printed after `join()` is **48** (12+7+20+9) every time. Stations finish in the order B, D, A, C because each script takes 10 ms.

## Shared work
* `StationThread extends Thread` and `StationTask implements Runnable` both use `start()`, `sleep()`, `join()`, `isAlive()` (see `ScanStations.java`).
* The total (`AtomicInteger`) is safe to update from 4 threads; it is read only after `join()`.

## Student A - Thread design
**Thread vs Runnable.** Extending `Thread` ties the task to the thread and uses up the single superclass slot. Implementing `Runnable` separates *what to run* from *how it runs*: the task can extend another class, be reused with several threads, a lambda, or an `ExecutorService`. Prefer `Runnable` (or `Callable`) almost always; extend `Thread` only to change thread behaviour itself.

**Life-cycle diagram** (each station passes the bold states):
```
 NEW --start()--> RUNNABLE <--------------------------+
                   |  ^  \                             |
        sleep(ms)  |  |   '-- blocked on lock -> BLOCKED
                   v  |
              TIMED_WAITING     wait()/join() -> WAITING
                   |
                   v  (run() returns)
              TERMINATED
```
A station goes **NEW -> RUNNABLE -> TIMED_WAITING (each `sleep`) -> RUNNABLE -> TERMINATED**. Printed evidence: NEW before `start()`, `TIMED_WAITING` after, `TERMINATED` after `join()`. `main` itself is in `WAITING` while it is in `join()`.

**`run()` instead of `start()`** (`evidence/run_vs_start.txt`): with `t1.run()` the body printed `executed by thread: main` and `t1.getState()` stayed `NEW` - an ordinary method call, no new thread. With `t2.start()` the body printed `executed by thread: Station-Y`.

**How `join()` guarantees the final total.** `t.join()` blocks the calling thread until `t` is TERMINATED. Joining all four stations before printing means every `addAndGet` has completed (and, by the Java memory model, is visible to `main`) when `TOTAL` is read.

## Student B - Behavioural experiments (`evidence/finish_order.txt`)
**10 runs, four stations with identical CPU work** (start order A,B,C,D):
`CABD, ABCD, ACBD, ACBD, ABCD, ACBD, ABCD, BCAD, ABCD, BACD` -> 5 different orders in 10 runs (ABCD 4, ACBD 3, others 1).

**Priorities** (A = MIN 1, B = C = NORM 5, D = MAX 10): `ABCD x5, ACBD x3, BACD, ABDC` - the order still varies and the MAX-priority station D finished **last** in 9 of 10 runs. On this Linux JVM thread priorities are essentially ignored (the OS scheduler is not given the hint by default), so they are only a hint, never a guarantee.

**Daemon thread** (`evidence/daemon_*.txt`): with a normal (user) thread the JVM stayed alive and printed `Station D finished its 500 ms batch` after `main()` ended. With `setDaemon(true)` the JVM exited as soon as `main()` ended and the message never appeared - daemon threads are killed abruptly, so they must not do work that has to finish (such as scanning scripts).

**Explanation.** The scheduler (OS plus JVM) decides when each runnable thread gets a CPU core; with 4 threads on 2 cores, which one is preempted or runs first depends on timing, load and cache state, none of which the program controls.

## Joint conclusion - why must a correct program not depend on the order?
Because the order is decided by the scheduler, it changes between runs, machines, load and JVM versions (proved above: 5 different orders from identical code). A program that happens to work for one order will fail intermittently under another. Correctness must come from explicit coordination (`join`, locks, queues), not from timing.

## Alternative concept - `ExecutorService` with a fixed pool (`ExecutorStations.java`, `evidence/executor_benchmark.txt`)
```java
ExecutorService pool = Executors.newFixedThreadPool(p);
List<Future<Integer>> f = new ArrayList<>();
for (int i = 0; i < n; i++) f.add(pool.submit(() -> { scan(); return 1; }));
for (Future<Integer> x : f) total += x.get();   // replaces join()
pool.shutdown();
```
* **Time:** n scripts of t ms on p threads take about ceil(n/p)*t -> O(n/p). Measured (n=64, 20 ms): p=1 1297 ms, p=2 645, p=4 322, p=8 164, p=16 82 ms (ideal 1280/640/320/160/80) - almost perfect scaling. The same ceil(n/p) bound applies when you create p Thread objects yourself.
* **Thread creation cost:** 5000 trivial tasks took 446 ms with one new `Thread` each, but 14 ms through a pool of 8 - the pool reuses threads.
* **Memory:** each thread needs its own stack (typically up to 1 MB reserved by default on 64-bit Linux, set by `-Xss`, committed lazily). Hand-made threads: one stack per task -> O(n) stacks if one thread per script. Pool: O(p) stacks plus a queue of waiting tasks O(n) small objects.
* **Other benefits:** results through `Future`, exceptions surface in `get()`, bounded concurrency, shutdown control.
