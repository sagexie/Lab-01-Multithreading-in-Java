
package edu.itc.se.lab01.task5;
 
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
 
/**
 * Fix B: each account has a ReentrantLock. A transfer tries to take both locks with a
 * 10 ms timeout; if it can't get both, it lets go of everything, waits a random moment, and retries.
 */
public class TryLockbank {
    static final int ACCOUNTS = 4, THREADS = 8, TRANSFERS_PER_THREAD = 100_000;
    static final LongAdder retries = new LongAdder();
 
    static final class Account {
        final int id; private long balance;
        final ReentrantLock lock = new ReentrantLock();
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }
 
    /** Throws InterruptedException if the thread is interrupted while waiting for a lock. */
    static void transfer(Account from, Account to, long amount) throws InterruptedException {
        var rnd = ThreadLocalRandom.current();
        while (true) {
            if (from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                        try {
                            if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
                            return;                                    // success
                        } finally {
                            to.lock.unlock();
                        }
                    }
                } finally {
                    from.lock.unlock();                                // always let go of the first lock
                }
            }
            retries.increment();                                       // didn't get both locks
            TimeUnit.MICROSECONDS.sleep(rnd.nextInt(1, 1_000));        // random back-off, then retry
        }
    }
 
    static long total(Account[] accounts) {
        long total = 0; for (Account a : accounts) total += a.balance();
        return total;
    }
 
    static Account[] newAccounts() {
        Account[] accounts = new Account[ACCOUNTS];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);
        return accounts;
    }
 
    public static void main(String[] args) throws InterruptedException {
        Account[] accounts = newAccounts();
 
        long t0 = System.nanoTime();
        Thread[] workers = new Thread[THREADS];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                try {
                    for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
                        int a = rnd.nextInt(ACCOUNTS);
                        int b = rnd.nextInt(ACCOUNTS - 1);
                        if (b >= a) b++;
                        transfer(accounts[a], accounts[b], rnd.nextInt(1, 50));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();                // restore the flag
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
 
        System.out.printf("transfers=%d retries=%d total=%d (expected %d) in %d ms%n",
                (long) THREADS * TRANSFERS_PER_THREAD, retries.sum(), total(accounts),
                ACCOUNTS * 1_000L, (System.nanoTime() - t0) / 1_000_000);
    }
}
