
package edu.itc.se.lab01.task5;
 
import java.util.concurrent.ThreadLocalRandom;
 
/** Fix A: always lock the account with the smaller id first, so a circular wait is impossible. */
public class OrderedBank {
    static final int ACCOUNTS = 4, THREADS = 8, TRANSFERS_PER_THREAD = 100_000;
 
    static final class Account {
        final int id; private long balance;
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }
 
    static void transfer(Account from, Account to, long amount) {
        Account first  = from.id < to.id ? from : to;   // same order for every thread
        Account second = from.id < to.id ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
            }
        }
    }
 
    public static void main(String[] args) throws InterruptedException {
        Deadlockwatchdog.start();
 
        Account[] accounts = new Account[ACCOUNTS];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);
 
        long t0 = System.nanoTime();
        Thread[] workers = new Thread[THREADS];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
                    int a = rnd.nextInt(ACCOUNTS);
                    int b = rnd.nextInt(ACCOUNTS - 1);
                    if (b >= a) b++;                                // b is always different from a
                    transfer(accounts[a], accounts[b], rnd.nextInt(1, 50));
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
 
        long total = 0; for (Account a : accounts) total += a.balance();
        System.out.printf("transfers=%d total=%d (expected %d) in %d ms%n",
                (long) THREADS * TRANSFERS_PER_THREAD, total, ACCOUNTS * 1_000L,
                (System.nanoTime() - t0) / 1_000_000);
    }
}
 