
package edu.itc.se.lab01.task1;

public class Countdown extends Thread {

    public Countdown(String name) {
        super(name);
    }

    @Override
    public void run() {
        for (int i = 3; i >= 1; i--) {
            System.out.println(getName() + ": " + i);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        System.out.println(getName() + ": liftoff!");
    }
}

