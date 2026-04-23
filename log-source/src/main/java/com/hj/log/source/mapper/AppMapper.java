package com.hj.log.source.mapper;

import com.hj.log.common.domain.AppRegistration;
import com.hj.log.common.enums.AppStatus;
import com.hj.log.common.enums.Environment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code app_registrations} 表 CRUD。 */
@Mapper
public interface AppMapper {

    /** 插入；自增主键回填到 {@code app.id}。 */
    int insert(AppRegistration app);

    AppRegistration findById(@Param("id") Long id);

    AppRegistration findByCode(@Param("code") String code);

    /** 多条件可选过滤；任一参数为 {@code null} 视为不过滤。 */
    List<AppRegistration> list(
            @Param("code") String code,
            @Param("environment") Environment environment,
            @Param("status") AppStatus status);
}
