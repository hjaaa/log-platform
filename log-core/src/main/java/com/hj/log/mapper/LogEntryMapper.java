package com.hj.log.mapper;

import com.hj.log.domain.LogEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LogEntryMapper {

    int insert(LogEntry logEntry);

    LogEntry selectById(String id);
}
