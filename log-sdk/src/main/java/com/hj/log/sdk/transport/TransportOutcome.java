package com.hj.log.sdk.transport;

/** 单次 HTTP POST 的三态结果，用于驱动上层重试 / 丢弃 / 降级决策。 */
public enum TransportOutcome {

    /** HTTP 2xx：批已落服务端。 */
    SUCCESS,

    /** HTTP 4xx：服务端明确拒收；重试无益，应丢弃并 WARN。 */
    CLIENT_ERROR,

    /** HTTP 5xx、网络超时、IO 异常：属瞬时问题，上层决定重试或降级。 */
    TRANSIENT_ERROR
}
