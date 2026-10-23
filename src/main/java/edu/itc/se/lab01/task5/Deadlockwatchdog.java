package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Daemon thread that checks for deadlocks once per second and prints the thread names. */
public final class Deadlockwatchdog {
    private Deadlockwatchdog() {}

    public static Thread start() {
        Thread t = new Thread(() -> {
            ThreadMXBean mx = ManagementFactory.getThreadMXBean();
            String lastReport = "";
            while (true) {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    return;                                   // asked to stop
                }
                long[] ids = mx.findDeadlockedThreads();      // null when there is no deadlock
                if (ids == null) continue;
                String names = Arrays.stream(mx.getThreadInfo(ids))
                        .filter(info -> info != null)
                        .map(ThreadInfo::getThreadName)
                        .sorted()
                        .collect(Collectors.joining(", "));
                if (!names.equals(lastReport)) {              // print each new deadlock once
                    System.out.println("[watchdog] DEADLOCK detected between: " + names);
                    lastReport = names;
                }
            }
        }, "deadlock-watchdog");
        t.setDaemon(true);   // daemon: never keeps the JVM alive by itself
        t.start();
        return t;
    }
}