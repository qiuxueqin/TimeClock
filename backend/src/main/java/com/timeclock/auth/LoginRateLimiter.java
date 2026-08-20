package com.timeclock.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例内存限流器（§3.4 登录/注册限流默认 5 次失败 / 15 分钟 / IP+邮箱）。
 *
 * <p>以 {@code ip|email} 为键，统计最近失败次数；应用重启后计数清零可接受。
 * 计数随滑动窗口滚动：键未在窗口内访问即被惰性移除。
 */
public final class LoginRateLimiter {

    private final int maxFailures;
    private final Duration window;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimiter(int maxFailures, Duration window) {
        this.maxFailures = maxFailures;
        this.window = window;
    }

    /** Returns whether a key may attempt an operation without changing its failure count. */
    public boolean isAllowed(String key) {
        Instant now = Instant.now();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(now));
        synchronized (b) {
            resetIfExpired(b, now);
            return b.count < maxFailures;
        }
    }

    /** Records one failed attempt. Successful attempts never consume the quota. */
    public void recordFailure(String key) {
        Instant now = Instant.now();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(now));
        synchronized (b) {
            resetIfExpired(b, now);
            if (b.count < maxFailures) {
                b.count++;
            }
        }
    }

    /** Clears failures after a successful authentication operation. */
    public void clear(String key) {
        buckets.remove(key);
    }

    private void resetIfExpired(Bucket bucket, Instant now) {
        if (!now.isBefore(bucket.windowStart.plus(window))) {
            bucket.windowStart = now;
            bucket.count = 0;
        }
    }

    /** 清理长期不活跃的键，避免内存无限增长（惰性维护）。 */
    public void prune() {
        Instant cutoff = Instant.now().minus(window);
        buckets.entrySet().removeIf(e -> e.getValue().windowStart.isBefore(cutoff));
    }

    private static final class Bucket {
        Instant windowStart;
        int count;

        Bucket(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
