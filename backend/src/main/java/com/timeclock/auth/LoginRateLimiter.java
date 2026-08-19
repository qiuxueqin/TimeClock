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

    /**
     * 记录一次失败；若已超过上限则拒绝（返回 false）。
     * 该键在窗口内达到上限时，后续注册请求被拒绝直至窗口滑过。
     */
    public boolean isAllowed(String key) {
        Instant now = Instant.now();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(now));
        synchronized (b) {
            if (now.isAfter(b.windowStart.plus(window))) {
                b.windowStart = now;
                b.count = 0;
            }
            if (b.count >= maxFailures) {
                return false;
            }
            b.count++;
            return true;
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
