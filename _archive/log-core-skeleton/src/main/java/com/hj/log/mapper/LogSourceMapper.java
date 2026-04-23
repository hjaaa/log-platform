package com.hj.log.mapper;

import com.hj.log.domain.LogSource;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LogSourceMapper {

    int insert(LogSource logSource);

    LogSource selectById(String id);
}
