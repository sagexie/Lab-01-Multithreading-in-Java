package edu.itc.se.lab01.task2;

import java.util.concurrent.atomic.LongAdder;

/**
 * Why LongAdder is faster under contention:
 * AtomicLong is ONE variable, so all 8 threads fight over the same cache
 * line, which bounces between CPU cores on every increment. LongAdder splits
 * the count into several cells, each on its own cache line. Threads increment
 * different cells in parallel with almost no collisions, and sum() adds the
 * cells up at the end.
 * Trade-off: sum() is not an exact snapshot while writers are still running.
 * That is fine here because we read only after join().
 */
public class LongAdderCounter implements Counter {
    private final LongAdder count = new LongAdder();

    @Override public void increment() { count.increment(); }
    @Override public long get() { return count.sum(); }
}
