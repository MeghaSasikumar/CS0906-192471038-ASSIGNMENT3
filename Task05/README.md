# Task 05 - Race Conditions in a Cinema Seat-Booking System

## Compile and run
```bash
javac *.java
java SeatBooking            # Complete-the-Code program, TODO 1 done (synchronized)  -> output.txt
java SeatBookingUnsafe      # same program WITHOUT synchronized
java RaceLab race           # reliable reproduction of double booking
java RaceLab bench          # compare synchronization strategies
```
Test machine: 2 CPU cores, OpenJDK 21.

## Complete the Code - TODO 1
```java
public synchronized void book(int seat) {
    if (!booked[seat]) { booked[seat] = true; bookingsMade++; }
}
```
Output (`output.txt`): `Seats sold: 10` (matches expected). `evidence/synchronized_10_runs.txt`: 10 of 10 runs print `Seats sold: 10`.

**Unsynchronized, 10 runs** (`evidence/unsafe_given_program_10_runs.txt`): all 10 runs also printed `Seats sold: 10`. The given test is too gentle: `Thread.start()` is slow compared with the tiny `book()` body, so the 100 threads effectively run one after another and the race window is never hit. The bug is still there - the next section proves it. A passing test does not prove thread-safe code.

**Why can the unsynchronized version sell more than 10?** `book` is *check-then-act* (`if (!booked[seat])` then `booked[seat] = true`), plus `bookingsMade++`, which is read-modify-write. These are not atomic. Two threads can both read `booked[seat] == false` before either writes `true`, so both book the same seat and `bookingsMade` is incremented twice. The `++` itself can also lose updates.

## Student A - Producing and explaining the race
**Reliable test** (`RaceLab race`): 8 threads each try seats 0..1999 in the same order at the same moment (released by a `CountDownLatch`), and `Thread.yield()` is placed between the check and the act to widen the window (`evidence/race_reproduction.txt`). Expected sales 2000:
| Run | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Seats sold | 5922 | 15355 | 10951 | 7815 | 11844 | 7497 | 7105 | 14560 | 15324 | 2649 |
| Double bookings | 3922 | 13355 | 8951 | 5815 | 9844 | 5497 | 5105 | 12560 | 13324 | 649 |

Total 79,022 double bookings in 10 runs; every run oversold. The synchronized version sold exactly 2000 in every run.

**Interleaving of two threads (same seat 3):**
| Step | Thread T1 | Thread T2 | `booked[3]` |
|---|---|---|---|
| 1 | reads `booked[3]` -> false | | false |
| 2 | | reads `booked[3]` -> false | false |
| 3 | sets `booked[3] = true`, `bookingsMade++` | | true |
| 4 | | sets `booked[3] = true`, `bookingsMade++` | true |
Both believed the seat was free -> the seat is sold twice.

**Critical section:** exactly the check `if (!booked[seat])`, the write `booked[seat] = true` and `bookingsMade++` - they must execute as one indivisible unit.

## Student B - Comparing synchronization strategies (`evidence/benchmark.txt`)
10,000 booking attempts over 1000 seats, 8 threads, median of 15 runs, each attempt also does ~200 `sqrt` operations of unshared work (price calculation stand-in):
| Strategy | Median time | Double bookings |
|---|---|---|
| synchronized method | 4.33 ms | 0 |
| synchronized block (critical section only) | 2.44 ms | 0 |
| AtomicBoolean + `compareAndSet` | 2.15 ms | 0 |
| ReentrantLock | 2.78 ms | 0 |
| ConcurrentHashMap `putIfAbsent` | 4.04 ms | 0 |
Caveat: very short runs on a 2-core machine; treat as relative, not absolute (re-run values vary by about +/-30%).

**Whole method vs critical section only.** Locking the whole method also locks the non-shared work, so the 8 threads queue behind each other for the whole `busyWork()` (4.33 ms). Locking only the critical section lets that work run in parallel (2.44 ms). Rule: hold a lock for as little time as possible, but cover all shared-state reads and writes.

**Static synchronization.** A `static synchronized` method locks the `Class` object, one lock for the whole JVM. It would be needed only if the booking state were `static` (shared by all instances). Here the state belongs to each screen instance, so an instance lock is correct, and a static lock would needlessly block the 20 screens from each other.

**Intrinsic lock used.** Both the synchronized method and `synchronized (this)` use the same intrinsic lock (monitor) of the `SeatBooking` object (`this`). A synchronized method is simply a synchronized block around the whole body.

## Joint decision - strategy for 20 screens
One `SeatBooking` object per screen, each protecting its own seats with a **synchronized block (or `ReentrantLock`) around just the check-and-book**. Reasons: each screen has its own lock, so screens never contend with each other; contention within a screen is short because the critical section is tiny; it is simple, correct and handles *group bookings* (several seats booked all-or-nothing under one lock), which per-seat CAS cannot do atomically. Use `AtomicBoolean` CAS per seat only if every booking is a single seat and contention is very high.

## Alternative concept - AtomicBoolean[] / ReentrantLock / ConcurrentHashMap
```java
AtomicBoolean[] booked;                       // one flag per seat
boolean book(int s) { return booked[s].compareAndSet(false, true); }   // atomic check-then-act
// ReentrantLock: lock.lock(); try { check+book } finally { lock.unlock(); }
// ConcurrentHashMap: map.putIfAbsent(seat, TRUE) == null  -> booked by me
```
| Approach | Time per booking under contention | Extra space for s seats |
|---|---|---|
| synchronized / ReentrantLock | O(1) work but threads queue on one lock: with k contending threads about O(k) waiting | O(1) (one lock) |
| `AtomicBoolean[]` CAS | O(1), lock-free; failed CAS just means "already booked" (no retry needed here) | O(s) objects (about 16 bytes per `AtomicBoolean`, plus array) |
| `ConcurrentHashMap.putIfAbsent` | O(1) average, per-bucket synchronization | O(s) entries (about 32-48 bytes each) |
Measured (table above): CAS 2.15 ms < block 2.44 ms < ReentrantLock 2.78 ms < putIfAbsent 4.04 ms < synchronized method 4.33 ms.
