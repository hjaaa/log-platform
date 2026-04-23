package com.hj.log.ingestion.service;

import com.hj.log.common.context.RequestContext;
import com.hj.log.common.domain.LogEntry;
import com.hj.log.common.enums.LogKind;
import com.hj.log.common.enums.LogLevel;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import com.hj.log.ingestion.config.IngestionProperties;
import com.hj.log.ingestion.dto.IngestRequest;
import com.hj.log.ingestion.dto.IngestResponse;
import com.hj.log.ingestion.dto.LogEventDto;
import com.hj.log.ingestion.dto.RejectItem;
import com.hj.log.ingestion.mapper.LogEntryMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 写入服务：DTO → DO，校验，多值 INSERT。
 *
 * <p>语义约定（detailed-design §2.3）：
 * <ul>
 *   <li>整批超限 → 抛 {@link BusinessException}({@code INGEST_BATCH_TOO_LARGE}) 走 4xx</li>
 *   <li>单条字段不合法 → 入 {@code rejected[]}，整体仍 200 返回</li>
 * </ul>
 */
@Service
public class LogIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LogIngestionService.class);

    private final LogEntryMapper mapper;
    private final IngestionProperties props;

    public LogIngestionService(LogEntryMapper mapper, IngestionProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    public IngestResponse ingest(IngestRequest req) {
        List<LogEventDto> events = req.getEvents();
        if (events.size() > props.getMaxBatchSize()) {
            throw new BusinessException(ErrorCode.INGEST_BATCH_TOO_LARGE, "单批日志条数超限");
        }
        Long appId = currentAppIdOrThrow();
        Instant serverTs = Instant.now();

        List<LogEntry> accepted = new ArrayList<>(events.size());
        List<RejectItem> rejected = new ArrayList<>(0);
        long start = System.currentTimeMillis();
        for (int i = 0; i < events.size(); i++) {
            String reason = validate(events.get(i));
            if (reason != null) {
                rejected.add(new RejectItem(i, reason));
                continue;
            }
            accepted.add(toEntry(events.get(i), appId, serverTs));
        }

        if (!accepted.isEmpty()) {
            mapper.batchInsert(accepted);
        }
        long cost = System.currentTimeMillis() - start;
        log.info(
                "[ingest] appId={} accepted={} rejected={} costMs={}",
                appId,
                accepted.size(),
                rejected.size(),
                cost);
        return new IngestResponse(accepted.size(), rejected);
    }

    private Long currentAppIdOrThrow() {
        RequestContext ctx = RequestContext.current();
        if (ctx == null || ctx.getAppId() == null) {
            // AuthFilter 应已注入；缺失意味着配置错误（如直接绕过 Filter 调用），按内部错误处理
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "RequestContext 未注入 appId", 500);
        }
        return ctx.getAppId();
    }

    /** 返回 {@code null} 表示通过；否则返回 ErrorCode 字符串。 */
    private String validate(LogEventDto e) {
        if (e == null) {
            return ErrorCode.INGEST_MESSAGE_BLANK;
        }
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return ErrorCode.INGEST_MESSAGE_BLANK;
        }
        if (e.getMessage().length() > props.getMaxMessageLength()) {
            return ErrorCode.INGEST_MESSAGE_TOO_LONG;
        }
        if (e.getStackTrace() != null && e.getStackTrace().length() > props.getMaxStackTraceLength()) {
            return ErrorCode.INGEST_MESSAGE_TOO_LONG;
        }
        if (LogLevel.fromCode(e.getLevel()) == null) {
            return ErrorCode.INGEST_INVALID_LEVEL;
        }
        if (LogKind.fromCode(e.getLogKind()) == null) {
            return ErrorCode.INGEST_INVALID_KIND;
        }
        return null;
    }

    private LogEntry toEntry(LogEventDto e, Long appId, Instant serverTs) {
        LogEntry o = new LogEntry();
        o.setAppId(appId);
        o.setLogKind(LogKind.fromCode(e.getLogKind()));
        o.setLevel(LogLevel.fromCode(e.getLevel()));
        o.setMessage(e.getMessage());
        o.setStackTrace(e.getStackTrace());
        o.setContextData(e.getContextData());
        o.setTraceId(e.getTraceId());
        o.setSpanId(e.getSpanId());
        o.setRequestId(e.getRequestId());
        o.setLoggerName(e.getLoggerName());
        o.setThreadName(e.getThreadName());
        o.setSourceHost(e.getSourceHost());
        o.setClientTs(e.getClientTs() != null ? e.getClientTs() : serverTs);
        o.setServerTs(serverTs);
        return o;
    }
}
