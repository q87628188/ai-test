package com.demo.ragingest.domain;

public class RetryPolicy {

    public long nextDelayMs(int attempt, long baseMs, long maxMs) {
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0");
        }
        long delay = baseMs;
        for (int i = 0; i < attempt; i++) {
            if (delay > maxMs / 2) {
                return maxMs;
            }
            delay = delay * 2;
        }
        return Math.min(delay, maxMs);
    }
}
