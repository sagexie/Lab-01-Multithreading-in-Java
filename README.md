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