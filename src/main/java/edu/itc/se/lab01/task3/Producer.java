package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Producer implements Runnable {
    private final OrderQueue queue;
    private final int firstId;
    private final int count;
    private final int consumerCount;
    private final AtomicInteger producersStillRunning; // shared by all producers

    public Producer(OrderQueue queue, int firstId, int count,
                    int consumerCount, AtomicInteger producersStillRunning) {
        this.queue = queue;
        this.firstId = firstId;
        this.count = count;
        this.consumerCount = consumerCount;
        this.producersStillRunning = producersStillRunning;
    }

    @Override
    public void run() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        try {
            for (int i = firstId; i < firstId + count; i++) {
                queue.put(new Order(i, "item-" + i, rnd.nextInt(1, 6)));   // qty 1..5
                Thread.sleep(rnd.nextInt(0, 21));                          // 0..20 ms
            }
            // Only the LAST producer to finish sends the poison pills
            if (producersStillRunning.decrementAndGet() == 0) {
                for (int c = 0; c < consumerCount; c++) {
                    queue.put(Order.poisonPill());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}