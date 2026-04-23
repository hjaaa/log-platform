package com.hj.log.source.mapper;

import com.hj.log.common.domain.ApiKey;
import com.hj.log.common.enums.KeyStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** API Key 持久化操作。 */
@Mapper
public interface ApiKeyMapper {

    /** 鉴权热路径：按 SHA-256 hash 查找；找不到返回 {@code null}。 */
    ApiKey findByHash(@Param("hash") String hash);

    /** 异步更新 last_used_at。返回受影响行数（1 = 命中、0 = key 已不存在）。 */
    int touchLastUsed(@Param("id") Long id, @Param("lastUsedAt") Instant lastUsedAt);

    /** 签发 key；自增主键回填到 {@code key.id}。 */
    int insert(ApiKey key);

    ApiKey findById(@Param("id") Long id);

    /** 控制面：列出某 app 的所有 key（含 revoked，便于审计）。按 id 升序。 */
    List<ApiKey> listByAppId(@Param("appId") Long appId);

    /** 撤销：状态置为 {@link KeyStatus#revoked}。返回 1 = 命中，0 = 不存在或已撤销。 */
    int updateStatus(@Param("id") Long id, @Param("status") KeyStatus status);
}
