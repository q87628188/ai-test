package com.demo.ragingest.domain;

/** 指数退避：delay = min(base * 2^attempt, max)。 */
public class RetryPolicy {

    /**
     * @param attempt 从 0 起的退避次数（第 1 次重试用 attempt=0）
     */
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
