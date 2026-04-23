package com.hj.log.mapper;

import com.hj.log.domain.NotificationConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationConfigMapper {

    int insert(NotificationConfig notificationConfig);

    NotificationConfig selectById(String id);
}
