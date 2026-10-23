package edu.itc.se.lab01.task4;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class Main {
    static final int DOWNLOADS = 500;    // how many fake downloads
    static final long TIMEOUT_SEC = 1;   // wait at most 1 second per download

    // ============ START HERE ============
    public static void main(String[] args) throws Exception {
        cpuBound();                       // Step 1: primes
        System.out.println();

        // Step 2-4: downloads on 20 normal threads
        try (ExecutorService platform = Executors.newFixedThreadPool(20)) {
            runDownloads("fixed(20) platform threads", platform);
        }
        // Step 2-4: same downloads on virtual threads
        try (ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            runDownloads("virtual threads           ", virtual);
        }
        System.out.println();

        // Step 6 (bonus): same downloads with CompletableFuture
        try (ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            runDownloadsCompletable("CompletableFuture+virtual ", virtual);
        }

        // Step 5: no System.exit needed. try (...) closed every pool,
        // so no threads are left and the program ends by itself.
        System.out.println("\nmain() finished - JVM should exit by itself now.");
    }

    // ============ STEP 1: count primes ============
    static void cpuBound() throws InterruptedException, ExecutionException {
        final long LIMIT = 4_000_000, CHUNKS = 16;
        int threads = Runtime.getRuntime().availableProcessors();   // number of CPU cores

        // (a) one thread does everything
        long t0 = System.nanoTime();
        long single = PrimeCounter.countPrimes(0, LIMIT);
        System.out.printf("single-threaded: %d primes in %d ms%n", single, ms(t0));

        // (b) a pool shares the work
        t0 = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Long>> futures = new ArrayList<>();
            long step = LIMIT / CHUNKS;                      // 250,000 numbers per piece

            // give the pool 16 jobs
            for (int i = 0; i < CHUNKS; i++) {
                long from = i * step;
                long to = (i == CHUNKS - 1) ? LIMIT : from + step;
                futures.add(pool.submit(new PrimeCounter(from, to)));
            }

            // collect the 16 answers and add them up
            long total = 0;
            for (Future<Long> f : futures) total += f.get();

            System.out.printf("pool (%d threads): %d primes in %d ms%n", threads, total, ms(t0));
            if (total != single) throw new IllegalStateException("mismatch: " + total + " vs " + single);
        }
    }

    // ============ STEPS 2, 3, 4: downloads ============
    static void runDownloads(String label, ExecutorService executor) throws InterruptedException {
        long t0 = System.nanoTime();

        // give the pool 500 download jobs
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < DOWNLOADS; i++) {
            futures.add(executor.submit(FakeDownload.forIndex(i)));
        }

        // collect the 500 results and count what happened
        int ok = 0, failed = 0, timedOut = 0;
        for (int i = 0; i < futures.size(); i++) {
            Future<String> f = futures.get(i);
            try {
                f.get(TIMEOUT_SEC, TimeUnit.SECONDS);   // wait max 1 second
                ok++;                                   // it worked
            } catch (ExecutionException e) {            // Step 3: the job threw "boom"
                failed++;
            } catch (TimeoutException e) {              // Step 4: the job was too slow
                f.cancel(true);                         // stop it
                timedOut++;
                System.out.printf("  file-%d: timed out after %d s, cancelled%n", i, TIMEOUT_SEC);
            }
        }
        System.out.printf("downloads on %s: ok=%d failed=%d timedOut=%d in %d ms%n",
                label, ok, failed, timedOut, ms(t0));
    }

    // ============ STEP 6 (BONUS): CompletableFuture ============
    static void runDownloadsCompletable(String label, ExecutorService executor) {
        long t0 = System.nanoTime();
        List<CompletableFuture<String>> cfs = new ArrayList<>();

        for (int i = 0; i < DOWNLOADS; i++) {
            FakeDownload task = FakeDownload.forIndex(i);
            String url = "https://example.com/file-" + i;
            cfs.add(CompletableFuture.supplyAsync(unchecked(task), executor)   // run the job
                    .orTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)                 // max 1 second
                    .handle((result, ex) -> {                                 // turn errors into text
                        if (ex == null) return result;
                        Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                                ? ex.getCause() : ex;
                        if (cause instanceof TimeoutException) return "TIMEOUT " + url + " -> timed out";
                        return "FALLBACK " + url + " -> cached copy (" + cause.getMessage() + ")";
                    }));
        }

        // wait until all 500 are finished
        CompletableFuture.allOf(cfs.toArray(CompletableFuture[]::new)).join();

        // stop the slow job that is still sleeping
        executor.shutdownNow();

        // count the results by looking at the text
        int ok = 0, failed = 0, timedOut = 0;
        for (CompletableFuture<String> cf : cfs) {
            String s = cf.join();
            if (s.startsWith("TIMEOUT")) timedOut++;
            else if (s.startsWith("FALLBACK")) failed++;
            else ok++;
        }
        System.out.printf("downloads on %s: ok=%d failed=%d timedOut=%d in %d ms%n",
                label, ok, failed, timedOut, ms(t0));
        System.out.println("  sample fallback: " + cfs.get(49).join());
    }

    // ============ small helpers ============

    // lets a Callable (which can throw IOException) be used with supplyAsync
    static <T> Supplier<T> unchecked(Callable<T> c) {
        return () -> {
            try {
                return c.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        };
    }

    // milliseconds since t0
    static long ms(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }
}