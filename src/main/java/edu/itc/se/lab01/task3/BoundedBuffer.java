package edu.itc.se.lab01.task3;

import java.util.ArrayDeque;

/**
 * Fixed-capacity buffer built with guarded blocks.
 *
 *  * put() and take() are synchronized on "this", so the deque is only touched by
 * one thread at a time. A thread that cannot proceed calls wait(), which
 * releases the lock and sleeps until another thread calls notifyAll().
 */
public class BoundedBuffer<T> {
    private final ArrayDeque<T> items = new ArrayDeque<>();
    private final int capacity;

    public BoundedBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    public synchronized void put(T item) throws InterruptedException {
        // while, not if: when we wake up, re-check. Another producer may have
        // filled the slot first, or the wakeup may be spurious.
        while (items.size() == capacity) {
            wait();
        }
        items.addLast(item);
        // notifyAll, not notify: wake every waiter so a waiting consumer
        // is guaranteed to be among them.
        notifyAll();
    }

    public synchronized T take() throws InterruptedException {
        while (items.isEmpty()) {
            wait();
        }
        T item = items.removeFirst();
        notifyAll();
        return item;
    }
}
