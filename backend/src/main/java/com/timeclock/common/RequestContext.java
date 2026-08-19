package com.timeclock.common;

/**
 * 请求上下文：贯穿请求的 requestId 与客户端 IP，用于日志、错误响应与认证限流。
 *
 * <p>使用 {@link ThreadLocal} 按请求线程保存，由 {@code RequestContextFilter} 在请求进入时
 * 初始化、请求结束后清理，避免跨请求串扰。单实例内存限流计数不跨线程共享密钥。
 */
public final class RequestContext {

    private static final ThreadLocal<Context> HOLDER = ThreadLocal.withInitial(Context::new);

    private RequestContext() {
    }

    /** 设置当前线程请求上下文（由过滤器在请求开始时调用）。 */
    public static void set(String requestId, String clientIp) {
        Context c = new Context();
        c.requestId = requestId;
        c.clientIp = clientIp;
        HOLDER.set(c);
    }

    public static String requestId() {
        return HOLDER.get().requestId;
    }

    public static String clientIp() {
        return HOLDER.get().clientIp;
    }

    /** 请求结束时清理，避免线程复用导致的串扰。 */
    public static void clear() {
        HOLDER.remove();
    }

    private static final class Context {
        String requestId;
        String clientIp;
    }
}
