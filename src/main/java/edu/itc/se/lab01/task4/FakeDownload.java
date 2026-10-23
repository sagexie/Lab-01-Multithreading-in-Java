package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.concurrent.Callable;

public class FakeDownload implements Callable<String> {
    static final long NORMAL_DELAY_MS = 200;
    static final long SLOW_DELAY_MS = 5_000;

    private final String url;
    private final long delayMs;
    private final boolean fail;

    public FakeDownload(String url) {
        this(url, NORMAL_DELAY_MS, false);
    }

    public FakeDownload(String url, long delayMs, boolean fail) {
        this.url = url;
        this.delayMs = delayMs;
        this.fail = fail;
    }

    public static FakeDownload forIndex(int i) {
        String url = "https://example.com/file-" + i;
        boolean fail = (i + 1) % 50 == 0;
        long delay = (i == 7) ? SLOW_DELAY_MS : NORMAL_DELAY_MS;
        return new FakeDownload(url, delay, fail);
    }

    @Override
    public String call() throws Exception {
        Thread.sleep(delayMs);
        if (fail) throw new IOException("boom");
        int length = 1_000 + Math.floorMod(url.hashCode(), 9_000);
        return url + " -> " + length;
    }
}