package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Consumer implements Runnable {
    private final OrderQueue queue;
    private final String name;
    private final AtomicInteger processed;   // shared by all consumers

    public Consumer(OrderQueue queue, String name, AtomicInteger processed) {
        this.queue = queue;
        this.name = name;
        this.processed = processed;
    }

    @Override
    public void run() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        try {
            while (true) {
                Order order = queue.take();
                if (order.isPoisonPill()) {
                    System.out.println(name + " received poison pill, stopping");
                    return;
                }
                Thread.sleep(rnd.nextInt(5, 31));          // "process" 5..30 ms
                processed.incrementAndGet();
                System.out.println(name + " processed " + order);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}