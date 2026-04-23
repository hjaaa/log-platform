package com.hj.log.ingestion.mapper;

import com.hj.log.common.domain.LogEntry;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code logs} 表的写入操作。 */
@Mapper
public interface LogEntryMapper {

    /** 多值 INSERT。返回插入行数。 */
    int batchInsert(@Param("entries") List<LogEntry> entries);

    /** 删除 {@code server_ts < cutoff} 的最旧若干行；用于保留期清理。 */
    int deleteOldest(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
