package com.hj.log.query.mapper;

import com.hj.log.query.dto.LogSearchCriteria;
import com.hj.log.query.dto.LogView;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code logs} 表的读端 + appCode 关联（LEFT JOIN {@code app_registrations}）。 */
@Mapper
public interface LogQueryMapper {

    /**
     * 复合条件 + 游标分页查询。{@code cursorTs} / {@code cursorId} 可同时为 {@code null}（首页）。
     * 由 Service 控制 {@code limit}（已校验 ≤ max-page-size）。
     */
    List<LogView> search(@Param("criteria") LogSearchCriteria criteria,
                         @Param("cursorTs") Instant cursorTs,
                         @Param("cursorId") Long cursorId,
                         @Param("limit") int limit);

    /** 单条查找；返回 {@code null} 时由 Service 抛 {@code QUERY_LOG_NOT_FOUND}。 */
    LogView findById(@Param("id") long id);

    /**
     * 按 traceId 查询。Service 调用时传 {@code limit + 1} 以探测 {@code truncated}。
     * 排序按 {@code server_ts ASC, id ASC} 便于按 trace 时间顺序展示。
     */
    List<LogView> searchByTrace(@Param("traceId") String traceId,
                                @Param("limit") int limit);
}
