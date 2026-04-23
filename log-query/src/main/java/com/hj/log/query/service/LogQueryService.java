package com.hj.log.query.service;

import com.hj.log.common.context.RequestContext;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import com.hj.log.query.config.QueryProperties;
import com.hj.log.query.cursor.CursorCodec;
import com.hj.log.query.dto.LogSearchCriteria;
import com.hj.log.query.dto.LogView;
import com.hj.log.query.dto.Page;
import com.hj.log.query.dto.TracePage;
import com.hj.log.query.mapper.LogQueryMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 查询服务：参数校验 → 默认值补齐 → 解游标 → 调 Mapper → 编游标。
 *
 * <p>校验规则与错误码见 detailed-design §2.4 & §3。
 */
@Service
public class LogQueryService {

    private static final Logger log = LoggerFactory.getLogger(LogQueryService.class);

    private final LogQueryMapper mapper;
    private final QueryProperties props;

    public LogQueryService(LogQueryMapper mapper, QueryProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    /** {@code GET /api/v1/logs/search} 主流程。 */
    public Page<LogView> search(LogSearchCriteria criteria) {
        normalizeAndValidate(criteria);

        Instant cursorTs = null;
        Long cursorId = null;
        if (criteria.getCursor() != null && !criteria.getCursor().isEmpty()) {
            CursorCodec.Cursor c = CursorCodec.decode(criteria.getCursor());
            cursorTs = Instant.ofEpochMilli(c.getServerTsMillis());
            cursorId = c.getId();
        }

        int pageSize = criteria.getPageSize();
        // 多取一条用来判断是否还有下一页，避免出现末页 nextCursor 不为空但下一页为空的情况
        int fetchSize = pageSize + 1;

        long start = System.currentTimeMillis();
        List<LogView> rows = mapper.search(criteria, cursorTs, cursorId, fetchSize);
        long cost = System.currentTimeMillis() - start;

        boolean hasMore = rows.size() > pageSize;
        List<LogView> items = hasMore ? rows.subList(0, pageSize) : rows;

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            LogView last = items.get(items.size() - 1);
            nextCursor = CursorCodec.encode(last.getServerTs().toEpochMilli(), last.getId());
        }

        Long appId = currentAppIdOrNull();
        log.info(
                "[query.search] appId={} hitCount={} costMs={} cursorIn={} cursorOut={}",
                appId,
                items.size(),
                cost,
                criteria.getCursor(),
                nextCursor);
        return new Page<>(items, nextCursor, pageSize);
    }

    /** {@code GET /api/v1/logs/{id}}。 */
    public LogView getById(long id) {
        LogView v = mapper.findById(id);
        if (v == null) {
            throw new BusinessException(ErrorCode.QUERY_LOG_NOT_FOUND, "日志不存在或已过保留期", 404);
        }
        return v;
    }

    /** {@code GET /api/v1/logs/trace/{traceId}}。 */
    public TracePage<LogView> searchByTrace(String traceId, Integer limit) {
        int effectiveLimit = limit != null ? limit : props.getTraceDefaultLimit();
        if (effectiveLimit > props.getTraceMaxLimit()) {
            throw new BusinessException(
                    ErrorCode.QUERY_LIMIT_TOO_LARGE, "limit 超限", 400);
        }
        if (effectiveLimit <= 0) {
            effectiveLimit = props.getTraceDefaultLimit();
        }

        long start = System.currentTimeMillis();
        // 多取一条用来判断 truncated
        List<LogView> rows = mapper.searchByTrace(traceId, effectiveLimit + 1);
        long cost = System.currentTimeMillis() - start;

        boolean truncated = rows.size() > effectiveLimit;
        List<LogView> items = truncated ? rows.subList(0, effectiveLimit) : rows;

        log.info(
                "[query.trace] traceId={} hitCount={} truncated={} costMs={}",
                traceId,
                items.size(),
                truncated,
                cost);
        return new TracePage<>(items, truncated);
    }

    private void normalizeAndValidate(LogSearchCriteria c) {
        boolean noAppCode = c.getAppCode() == null || c.getAppCode().isEmpty();
        boolean noTraceId = c.getTraceId() == null || c.getTraceId().isEmpty();
        if (noAppCode && noTraceId) {
            throw new BusinessException(
                    ErrorCode.QUERY_MISSING_APP_FILTER, "必须传 appCode 或 traceId", 400);
        }
        if (c.getKeyword() != null && c.getKeyword().startsWith("%")) {
            throw new BusinessException(
                    ErrorCode.QUERY_KEYWORD_PATTERN_INVALID, "keyword 不允许以 % 开头", 400);
        }
        if (c.getPageSize() == null || c.getPageSize() <= 0) {
            c.setPageSize(props.getDefaultPageSize());
        } else if (c.getPageSize() > props.getMaxPageSize()) {
            throw new BusinessException(
                    ErrorCode.QUERY_PAGE_SIZE_TOO_LARGE, "pageSize 超限", 400);
        }
        Instant now = Instant.now();
        if (c.getTo() == null) {
            c.setTo(now);
        }
        if (c.getFrom() == null) {
            c.setFrom(now.minus(1, ChronoUnit.HOURS));
        }
    }

    private Long currentAppIdOrNull() {
        RequestContext ctx = RequestContext.current();
        return ctx == null ? null : ctx.getAppId();
    }
}
