package edu.itc.se.lab01.task2;

/**
 * NOT thread-safe. count++ is read -> add 1 -> write back, so two threads
 * can read the same value and one update is lost.
 *
 * Recorded results (8 threads x 1,000,000, expected 8,000,000):
 *   run 1: 3,092,467  (lost 4,907,533)
 *   run 2: 2,846,303  (lost 5,153,697)
 *   run 3: 3,486,355  (lost 4,513,645)
 * Wrong every time, and by a different amount each time, because the
 * result depends on how the threads happened to interleave.
 */
public class UnsafeCounter implements Counter {
    private long count;

    @Override public void increment() { count++; }
    @Override public long get() { return count; }
}
