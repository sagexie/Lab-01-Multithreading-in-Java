package edu.itc.se.lab01.task1;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo = new Countdown("apollo");
        Thread gemini = new Thread(new CountdownTask(), "gemini");
        Thread mercury = new Thread(() -> new CountdownTask().run(), "mercury");
        Thread artemis = Thread.ofVirtual()
                .name("artemis")
                .unstarted(new CountdownTask());

        Thread[] threads = {apollo, gemini, mercury, artemis};

        // Start or run all four threads
        if (sequential) {
            for (Thread thread : threads) {
                thread.run();
            }
        } else {
            for (Thread thread : threads) {
                thread.start();
            }
        }

        System.out.println("Mission control: all launched");

        // Wait for all threads to finish
        if (!sequential) {
            for (Thread thread : threads) {
                thread.join();
            }
        }

        System.out.println("Mission control: all landed");

        // Print thread information
        for (Thread thread : threads) {
            System.out.printf(
                    "%-8s virtual=%-5s state=%s%n",
                    thread.getName(),
                    thread.isVirtual(),
                    thread.getState()
            );
        }
    }
}

