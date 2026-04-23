package com.hj.log.common.context;

import com.hj.log.common.enums.Scope;

/**
 * 当前请求的上下文（ThreadLocal）。承载鉴权与日志关联所需的最少字段。
 *
 * <p>读：业务代码任意位置 {@link #current()}；
 * 写：仅 {@code log-source} 模块下的 {@code AuthFilter} / {@code RequestIdFilter} 通过
 * {@link RequestContextWriter}（同包友元）调用。其他位置无法构造或写入。
 */
public final class RequestContext {

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    private final Long appId;
    private final Scope scope;
    private final Long keyId;
    private final String requestId;

    RequestContext(Long appId, Scope scope, Long keyId, String requestId) {
        this.appId = appId;
        this.scope = scope;
        this.keyId = keyId;
        this.requestId = requestId;
    }

    /** 取当前线程绑定的上下文；未绑定返回 {@code null}。 */
    public static RequestContext current() {
        return HOLDER.get();
    }

    /** 仅 friend（{@link RequestContextWriter}）可清理。 */
    static void doClear() {
        HOLDER.remove();
    }

    static void doSet(RequestContext ctx) {
        HOLDER.set(ctx);
    }

    public Long getAppId() {
        return appId;
    }

    public Scope getScope() {
        return scope;
    }

    public Long getKeyId() {
        return keyId;
    }

    public String getRequestId() {
        return requestId;
    }
}
