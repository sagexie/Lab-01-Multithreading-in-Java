# Lab 01 – Task 3: Bounded Buffer

## How to run
Open `Main.java` (hand-written `BoundedBuffer`) or `Main2.java` (`ArrayBlockingQueue`) in VS Code and click **Run**.
Both print each processed order, three "received poison pill" lines, and
`Total processed: 100 (expected 100)`.

## Design
- `Producer` and `Consumer` depend only on the `OrderQueue` interface, so either buffer plugs in
  without changing them.
- Only the **last** producer to finish sends the poison pills (tracked with a shared `AtomicInteger`).
  If each producer sent its own pills, the faster producer could stop all consumers while the other
  producer still had orders. That producer would then block forever on a full buffer.

## BoundedBuffer vs BlockingQueue
`BoundedBuffer` needs `synchronized`, two `while` guards, `wait()`, `notifyAll()` and a capacity check.
With `ArrayBlockingQueue`, all of that becomes `q.put(o)` and `q.take()`, one line each.
`Producer`, `Consumer` and `Runner` did not change at all.

## Experiment: `notifyAll()` → `notify()`, `while` → `if`
Each variant was run 20 times with a PowerShell loop (10-second timeout = hang).

| Variant | Normal setup (2P×50, 3C, cap 5, sleeps) | Stress (4P×5000, 6C, cap 1, no sleeps) |
|---|---|---|
| `while` + `notifyAll()` (correct) | 20/20 OK | 20/20 OK |
| `if` + `notify()` | 20/20 CRASH | – |
| `if` + `notifyAll()` | 20/20 CRASH | – |
| `while` + `notify()` | 20/20 OK | 20/20 HANG |

### `if` → crash (`NoSuchElementException`)
Waking up from `wait()` does not mean the condition is true now. A woken consumer must get the lock
back first, and another consumer may take the only item before it does. With `if`, the woken consumer
does not check again and calls `removeFirst()` on an empty deque. Spurious wake-ups cause the same
problem. `while` re-checks the condition after every wake-up.

Note: the final total still said 100, because the consumer crashed before processing anything and
the other two consumers did all the work. The count alone did not catch the bug; the exception in the
error output did.

### `notify()` → deadlock (program hangs)
Producers and consumers wait on the same lock, and `notify()` wakes only one random thread. If a
consumer's `notify()` wakes another consumer instead of a producer, that consumer sees an empty
buffer, goes back to `wait()` and notifies nobody. The wake-up is lost. Eventually all threads are
waiting and the program hangs forever. `notifyAll()` wakes everyone, so the right thread always gets
a chance. (`ArrayBlockingQueue` avoids this with two separate conditions, `notFull` and `notEmpty`.)

### Why the normal setup hid the deadlock
With the random sleeps, producers and consumers rarely wait at the same time, so `notify()` passed
all 20 runs. Without sleeps and with capacity 1, it hung every time. Concurrency bugs depend on
timing, so passing tests does not prove the code is correct.

# Lab 01 · Task 4: Thread pools, `Future` and virtual threads

## Build & run (JDK 21+, Maven)

```bash
mvn compile
java -cp target/classes edu.itc.se.lab01.task4.Main
```

## Files

All in package `edu.itc.se.lab01.task4`.

| File | Role |
|---|---|
| `PrimeCounter.java` | `Callable<Long>`: counts primes in `[from, to)` by trial division (CPU-bound) |
| `FakeDownload.java` | `Callable<String>`: sleeps 200 ms, returns `url -> length`. Every 50th task throws `IOException("boom")`, task #7 sleeps 5 s |
| `Main.java` | Runs all experiments: CPU pool, platform vs virtual, failures, timeout, `CompletableFuture` bonus |

## Sample output (JDK 25, 12 threads)

```
single-threaded: 283146 primes in 3516 ms
pool (12 threads): 283146 primes in 395 ms

  file-7: timed out after 1 s, cancelled
downloads on fixed(20) platform threads: ok=489 failed=10 timedOut=1 in 5240 ms
  file-7: timed out after 1 s, cancelled
downloads on virtual threads           : ok=489 failed=10 timedOut=1 in 1283 ms

downloads on CompletableFuture+virtual : ok=489 failed=10 timedOut=1 in 1032 ms
  sample fallback: FALLBACK https://example.com/file-49 -> cached copy (boom)

main() finished - JVM should exit by itself now.
```

The process exits with code 0. No `System.exit` is used.

## 1. CPU-bound: why the pool helps (but only up to the core count)

Counting primes keeps a core 100% busy, so the useful parallelism is
`availableProcessors()`. On this 12-thread machine the speed-up is about 8.9× (3516 ms → 395 ms).
It is less than a perfect 12× because the chunks take different amounts of time and sharing out the work has a small cost.
More threads than cores would not help. They would only time-slice the same cores.

The range is split into **16 chunks rather than N chunks** because the work is uneven.
Trial division up to √n makes higher numbers more expensive, so the last chunk costs more than the first.
Many small chunks let a free thread grab the next chunk. This balances the load.

## 2. I/O-bound: platform pool vs virtual threads

Each download spends almost all its time **waiting** (`Thread.sleep` stands in for network I/O). It uses almost no CPU.

**Fixed pool of 20 platform threads.**
At most 20 downloads can be "in flight" at once, because each one holds an OS thread while it sleeps.
500 tasks / 20 threads = 25 waves × 200 ms ≈ **5 000 ms**.
The extra ≈240 ms (5240 ms measured) comes from the slow task, which holds one pool thread for 1 s before it is cancelled.
To go faster you would need a bigger pool. But platform threads are expensive OS threads, with about 1 MB of stack reserved each, so you cannot simply create 10 000 of them.

**`newVirtualThreadPerTaskExecutor()`.**
Every task gets its own virtual thread. A virtual thread is a lightweight JVM-managed thread.
When it blocks in `sleep` or on I/O, it *unmounts* from its carrier (a small ForkJoinPool of platform threads, about one per core).
The carrier is then free to run other virtual threads.
So all 500 downloads wait **at the same time**, and they finish in about 200 ms.
The total of ≈1.3 s (1283 ms measured) comes almost entirely from the deliberate 1 s timeout on `file-7`.
Without the slow task, the whole batch takes about 200 ms.

**Takeaway.**
- For **blocking I/O**, the bottleneck is "how many things can wait at once". Virtual threads make that number cheap (thousands or millions).
- For **CPU-bound** work, virtual threads give no benefit. You still have only N cores, so a fixed pool sized to the core count is the right tool.

## 3. Failure handling

When a task throws, the exception is **captured inside the `Future`**.
It is not lost, and it does not kill the pool thread.
`future.get()` rethrows it wrapped in `ExecutionException`, and `getCause()` gives the original `IOException`.
The program catches it, counts it, and continues, which produces `failed=10`.

## 4. Timeouts and cancellation

`future.get(1, TimeUnit.SECONDS)` throws `TimeoutException` if the result is not ready in time.
A timeout only stops *waiting*. The task itself keeps running.
That is why the program calls `future.cancel(true)`.
`true` means "interrupt the running thread". `Thread.sleep` responds by throwing `InterruptedException`, so the task ends early.
If the task were not cancelled, the executor's `close()` would wait for the full 5 s.

## 5. Shutdown

All executors are opened with **try-with-resources** (`ExecutorService` is `AutoCloseable` since JDK 19).
`close()` calls `shutdown()` and waits for submitted tasks to finish.
Once every pool is closed, no non-daemon threads remain, so the JVM exits normally without `System.exit`.

On JDK < 19, use the classic pattern:

```java
pool.shutdown();
if (!pool.awaitTermination(10, TimeUnit.SECONDS)) pool.shutdownNow();
```

## 6. Bonus: `CompletableFuture`

```java
CompletableFuture.supplyAsync(unchecked(task), executor)
        .orTimeout(1, TimeUnit.SECONDS)
        .handle((result, ex) -> ex == null ? result : fallback(ex));
...
CompletableFuture.allOf(cfs.toArray(CompletableFuture[]::new)).join();
```

- `supplyAsync` takes a `Supplier`, which cannot throw checked exceptions. The `unchecked()` helper wraps the `IOException` in a `CompletionException`.
- `handle()` sees either the result or the exception, and turns failures into a fallback string. Because of this, `allOf(...).join()` never throws.
- `orTimeout` completes the future exceptionally after 1 s, but it **does not interrupt the underlying task**. Cancelling a `CompletableFuture` never interrupts its thread either. That is why the code calls `executor.shutdownNow()` after `allOf`, which interrupts the still-sleeping 5 s task so `close()` does not block.
- This version is non-blocking: the results are composed instead of being collected one by one with `get()`. So total time is roughly *max*(task times, timeout), about 1 s.
