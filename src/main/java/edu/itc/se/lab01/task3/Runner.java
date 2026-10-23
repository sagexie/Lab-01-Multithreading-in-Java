package edu.itc.se.lab01.task3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class Runner {
static final int PRODUCERS = 2;
static final int ORDERS_PER_PRODUCER = 50;
static final int CONSUMERS = 3;         

    private Runner() {}

    static void run(OrderQueue queue) throws InterruptedException {
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger producersRunning = new AtomicInteger(PRODUCERS);
        List<Thread> threads = new ArrayList<>();

        long start = System.nanoTime();

        for (int p = 0; p < PRODUCERS; p++) {
            int firstId = p * ORDERS_PER_PRODUCER + 1;          // 1, then 51
            threads.add(new Thread(
                new Producer(queue, firstId, ORDERS_PER_PRODUCER, CONSUMERS, producersRunning),
                "producer-" + (p + 1)));
        }
        for (int c = 1; c <= CONSUMERS; c++) {
            threads.add(new Thread(
                new Consumer(queue, "consumer-" + c, processed),
                "consumer-" + c));
        }

        threads.forEach(Thread::start);
        for (Thread t : threads) t.join();

        long ms = (System.nanoTime() - start) / 1_000_000;
        int expected = PRODUCERS * ORDERS_PER_PRODUCER;
        int total = processed.get();

        System.out.println("Total processed: " + total + " (expected " + expected + ") in " + ms + " ms");
        if (total != expected) {
            throw new AssertionError("Expected " + expected + " but processed " + total);
        }
    }
}