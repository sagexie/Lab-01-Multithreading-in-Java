# Lab 01 - Task 2: Lost updates on a shared counter

## How to run

    javac -d out src\edu\itc\se\lab01\task2\*.java
    java -cp out edu.itc.se.lab01.task2.Bench

`Bench.run` starts 8 threads, each calling `increment()` 1,000,000 times, joins them, and prints the final value, whether it equals 8,000,000, and the elapsed time measured with `System.nanoTime()`.

## The race

`count++` on a plain `long` is three steps: read, add 1, write back. Two threads can read the same value and both write value + 1, so one increment is lost:

    count = 0
    T1: read 0
    T2: read 0
    T1: write 1
    T2: write 1     -> count = 1, expected 2

## Results

8 threads x 1,000,000 increments, 3 rounds, a new counter for every run.

| Counter             | Round 1             | Round 2             | Round 3              | Correct?         |
|---------------------|---------------------|---------------------|----------------------|------------------|
| UnsafeCounter       | 3,092,467 / 98 ms   | 2,846,303 / 26 ms   | 3,486,355 / 121 ms   | never            |
| SynchronizedCounter | 759 ms              | 872 ms              | 1233 ms              | always 8,000,000 |
| LockCounter         | 236 ms              | 226 ms              | 216 ms               | always 8,000,000 |
| AtomicCounter       | 149 ms              | 337 ms              | 387 ms               | always 8,000,000 |
| LongAdderCounter    | 20 ms               | 15 ms               | 14 ms                | always 8,000,000 |

### Discussion

- **UnsafeCounter** lost 56-64% of all increments, and a different amount every run. It is fast but wrong, so it is useless.
- **SynchronizedCounter** was the slowest and the least stable (759-1233 ms). With 8 threads competing for one monitor, the JVM inflates it to a heavyweight lock and threads are repeatedly put to sleep and woken up.
- **LockCounter** (`ReentrantLock`) was 3-5x faster than `synchronized` and very consistent (216-236 ms). It spins briefly before parking a thread and keeps waiters in a queue, which handles heavy contention better. `unlock()` is always in a `finally` block so the lock is released even if the code throws.
- **AtomicCounter** (`AtomicLong`) uses no lock: `incrementAndGet()` is a single atomic CPU instruction. It won round 1, but in rounds 2-3 it was slower than `ReentrantLock`. All threads still write the same memory location, so that cache line bounces between cores, and when a lock makes threads take turns, the cores can actually collide less often.
- **LongAdderCounter** was by far the fastest (14-20 ms), even faster than the broken UnsafeCounter. LongAdder splits the count into several cells on separate cache lines, so threads mostly increment different memory and do not collide; `sum()` adds the cells at the end. UnsafeCounter still has every thread fighting over one cache line.

These numbers come from a simple wall-clock benchmark with no JIT warm-up, so individual runs are noisy. The overall ranking is the lesson, not the exact milliseconds.

## Question: why is synchronizing only `increment()` still a bug?

    public synchronized void increment() { count++; }
    public long get() { return count; }   // not synchronized

`synchronized` gives two guarantees: **mutual exclusion** (only one thread runs the code at a time) and **visibility** (when a thread releases a lock, its writes become visible to the next thread that acquires the same lock). The Java Memory Model only promises visibility between threads that lock the **same** monitor.

If only `increment()` is synchronized, no increments are lost, but a thread calling the plain `get()` never acquires the lock, so nothing guarantees it sees the latest value:

1. **Stale reads.** The reader may keep seeing an old value. The JIT is allowed to keep `count` in a register, so a loop like `while (c.get() < 8_000_000) {}` could spin forever.
2. **Torn reads.** A `long` is 64 bits, and the Java spec (JLS 17.7) allows non-volatile `long` reads and writes to happen as two 32-bit halves. A reader could see half of an old value and half of a new one, a number that was never stored.

**Why does Bench still print the right total in that case?** It calls `get()` only after `join()` on every thread, and `join()` itself guarantees that everything the joined thread did is visible. Any thread that reads while the counter is still being updated, such as a progress monitor, has no such protection.

**Fix:** make `get()` synchronized too (as `SynchronizedCounter` does), declare the field `volatile`, or use `AtomicLong` / `LongAdder`.
