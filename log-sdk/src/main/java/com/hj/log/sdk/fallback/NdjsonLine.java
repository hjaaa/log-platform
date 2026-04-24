package com.hj.log.sdk.fallback;

import com.hj.log.sdk.model.LogEvent;

/** Fallback 文件一行的内存模型；{@code ts} 暂不消费，保留便于排查。 */
public record NdjsonLine(LogEvent event, String ts) {}
