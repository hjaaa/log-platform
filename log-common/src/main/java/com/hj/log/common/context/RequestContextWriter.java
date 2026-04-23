package com.hj.log.common.context;

import com.hj.log.common.enums.Scope;

/**
 * RequestContext 的「友元写入器」。供 log-source 模块下的 Filter 注入上下文使用。
 *
 * <p>该类不是公开 API：业务代码请勿调用 {@link #set} / {@link #clear}，否则会污染
 * 请求隔离语义。Filter 之外的位置应通过 {@link RequestContext#current()} 只读访问。
 *
 * <p>包私有的 {@link RequestContext#doSet} / {@link RequestContext#doClear} 限制了
 * 只有同包内（即本类）可调用，从而把"写权"集中收口到本文件，便于审计。
 */
public final class RequestContextWriter {

    private RequestContextWriter() {
    }

    public static void set(Long appId, Scope scope, Long keyId, String requestId) {
        RequestContext.doSet(new RequestContext(appId, scope, keyId, requestId));
    }

    public static void clear() {
        RequestContext.doClear();
    }
}
