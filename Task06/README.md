# Task 06 - Deadlock and Inter-Thread Communication

## Compile and run
```bash
javac *.java
java TokenCounter                 # Complete-the-Code program (TODO 1 + 2 done)  -> output.txt
java TokenCounterIf               # same program with while changed to if (as the assignment asks)
java WaitLab trace|ifwhile|notify # instrumented experiments (Part B)
java WalletDeadlock deadlock|ordered|trylock|stress   # Part A
java BoundedQueueAlt              # alternative: ArrayBlockingQueue
```
Test machine: 2 CPU cores, OpenJDK 21. All raw outputs are in `evidence/`.

## Complete the Code - TODO 1 and TODO 2
```java
public synchronized void put(int token) throws InterruptedException {
    while (count == buffer.length) wait();      // wait while full
    buffer[in] = token; in = (in + 1) % buffer.length; count++;
    notifyAll();                                // wake waiting threads
}
public synchronized int take() throws InterruptedException {
    while (count == 0) wait();                  // wait while empty
    int token = buffer[out]; out = (out + 1) % buffer.length; count--;
    notifyAll();
    return token;
}
```
Output (`output.txt`): `Sum of tokens collected: 210 (expected 210)` - matches.

**`while` changed to `if` in both methods** (`TokenCounterIf.java`, 30 runs of the actual program, `evidence/given_program_if_30_runs.txt`): 29 runs printed 210 but **one run printed `Sum of tokens collected: 145`**. In the instrumented lab (cook delayed 2 ms per token so both students are waiting, `evidence/if_vs_while.txt`): `while` -> 0/30 runs wrong; `if` -> **30/30 runs wrong** (underflow: a student took from an empty counter and read a stale slot).
*Explanation:* after `notifyAll()` both waiting students wake up. The first one takes the token; the second was woken for a token that is already gone, but with `if` it does not re-check `count == 0` and takes anyway, so `count` goes negative and a stale buffer value is returned. With `while` it re-checks and goes back to waiting. The rare failure (1 in 30) is the point: the code *looks* correct most of the time.

## Part B - Producer-consumer
### Student A
* **Why `wait()` in a loop and inside `synchronized`:** `wait()` must be called while holding the object's monitor (otherwise `IllegalMonitorStateException`) because checking the condition and going to sleep must be atomic - otherwise a notification could arrive between the check and the wait and be lost. It must be in a `while` loop because a woken thread must re-test the condition: another thread may have changed it first (shown above), and spurious wakeups are allowed by the JVM spec.
* **`notify()` vs `notifyAll()`** (`evidence/notify_vs_notifyall.txt`; 2 producers, 2 consumers, capacity 1, 40 runs each): `notifyAll()` hung 0/40 runs, **`notify()` hung 39/40 runs**. All threads wait on the same monitor, so `notify()` may wake a thread of the *wrong kind* (e.g. a producer wakes another producer, which finds the counter full and waits again); the single wakeup is consumed and every thread ends up waiting forever. In our `TokenCounter` design (1 producer, 2 consumers of equal kind) `notifyAll()` is used because the two kinds of waiter share one condition.
* **Counter size over time** (`evidence/counter_trace.txt`): `[1,2,3,4,5,4,3,2,1,0,1,0,...,5,4,3,4,3,2,1,0,1,0]` - maximum 5, minimum 0, sum 210, 0 anomalies.
* **`sleep()` vs `wait()`:** `sleep(ms)` is a `Thread` method that pauses the thread for a time, **keeps** every lock it holds, and needs no monitor. `wait()` is an `Object` method, must hold the object's monitor, **releases** it while waiting, and resumes only after `notify/notifyAll` (or timeout/interrupt) and after reacquiring the monitor.

## Part A - Wallet transfers: deadlock
### Student B - Deadlock analysis and prevention
* **Pair of transfers that reliably deadlocks** (`java WalletDeadlock deadlock`): T1 = A->B and T2 = B->A, each locks its `from` account, pauses 100 ms, then requests `to`. JVM deadlock detector output:
  ```
  T1(A->B) is BLOCKED waiting for lock Account@3d075dc0 held by T2(B->A)
  T2(B->A) is BLOCKED waiting for lock Account@7b23ec81 held by T1(A->B)
  ```
* **The four conditions (Coffman) present:** (1) *Mutual exclusion* - a monitor can be held by only one thread; (2) *Hold and wait* - each thread holds one account while waiting for the other; (3) *No preemption* - a monitor cannot be taken away from its owner; (4) *Circular wait* - T1 waits for T2 and T2 waits for T1.
* **Prevention - consistent lock ordering** (`orderedTransfer`): always lock the account with the smaller id first. Result: both transfers complete, A=950, B=1050, total 2000. Stress test: 4 threads x 50,000 random transfers over 5 accounts finished in 37 ms and total money stayed 50,000 (`evidence/wallet_stress.txt`).
* **Why the rule works:** it removes condition 4. If every thread acquires locks in the same global order, a waiting thread always waits for a lock with a *higher* rank than any it holds, so a cycle in the wait-for graph is impossible.

## Joint conclusion - what do deadlock and producer-consumer have in common?
Both are about threads that must wait for each other, and both are solved by a protocol that guarantees someone can always proceed. Deadlock prevention guarantees progress by a global lock order (no circular wait). The bounded counter guarantees progress by `while`-guarded `wait()` plus `notifyAll()` after every state change: a producer waiting on "full" is always released by a consumer's take, and vice versa, with the single monitor serialising the state. The failures also look alike: everyone waiting and nobody able to wake anyone (`notify()` hang, circular lock wait).

## Alternative concept
### 1. `ArrayBlockingQueue<Integer>(5)` instead of `wait()/notifyAll()` (`BoundedQueueAlt.java`)
```java
ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<>(5);
q.put(token);            // blocks while full
int t = q.take();        // blocks while empty
```
Measured, 1 producer + 1 consumer, 1,000,000 tokens (`evidence/queue_benchmark.txt`): wait/notifyAll 4.9-5.8 us per token, ArrayBlockingQueue 5.2-5.4 us per token. They are about equal, because both use one lock and are dominated by thread hand-off when the buffer is only 5 long.
**Complexity:** `put/take` O(1) time in both; space O(capacity) = 5 slots. The queue hides the `while`/`notifyAll` details, so it is **much less error-prone** (no lost wakeups, no `if`-instead-of-`while` bug).

### 2. `tryLock()` with timeout instead of lock ordering (`tryTransfer`)
Try `from.lock`, then `to.lock`; if the second fails within 20 ms, release the first, back off randomly and retry (up to 20 attempts).
Measured stress (200,000 transfers, `evidence/wallet_stress.txt`): ordered **0.19 us/transfer**, tryLock **0.32 us/transfer**, 0 given-up transfers, money conserved.
**Complexity:** ordered: O(1) per transfer (two lock acquisitions), O(1) extra space (compare two ids). tryLock: O(1) expected per transfer but with retries (worst case up to the retry limit, and a transfer can *fail*), and each account needs a `ReentrantLock` object (O(accounts) space).
**Which is less error-prone?** Lock ordering is the simplest and gives a hard guarantee but requires every code path to follow the rule; `tryLock` tolerates mistakes but adds timeouts, retries, possible livelock and failure handling. `ArrayBlockingQueue` is clearly the least error-prone of the producer-consumer designs.
