package com.hj.log.mapper;

import com.hj.log.domain.AlertRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertRuleMapper {

    int insert(AlertRule alertRule);

    AlertRule selectById(String id);
}
