package com.hj.log.source.service;

import com.hj.log.common.domain.ApiKey;
import com.hj.log.common.enums.KeyStatus;
import com.hj.log.common.enums.Scope;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import com.hj.log.source.dto.ApiKeyIssuedView;
import com.hj.log.source.dto.IssueKeyRequest;
import com.hj.log.source.mapper.ApiKeyMapper;
import com.hj.log.source.util.KeyMaterial;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    /** 控制面只允许签发 write/read；admin scope 不入 api_keys 表（detailed-design §2.2.3）。 */
    private static final EnumSet<Scope> ALLOWED_SCOPES = EnumSet.of(Scope.WRITE, Scope.READ);

    private final ApiKeyMapper apiKeyMapper;
    private final AppService appService;

    public ApiKeyService(ApiKeyMapper apiKeyMapper, AppService appService) {
        this.apiKeyMapper = apiKeyMapper;
        this.appService = appService;
    }

    public ApiKeyIssuedView issue(Long appId, IssueKeyRequest req) {
        appService.mustFindById(appId); // APP_NOT_FOUND on miss
        Scope scope = Scope.fromCode(req.getScope());
        if (scope == null || !ALLOWED_SCOPES.contains(scope)) {
            throw new BusinessException(ErrorCode.KEY_INVALID_SCOPE, "scope 不合法");
        }

        KeyMaterial mat = KeyMaterial.generate();
        ApiKey row = new ApiKey();
        row.setAppId(appId);
        row.setKeyPrefix(mat.getKeyPrefix());
        row.setKeyHash(mat.getKeyHash());
        row.setScope(scope);
        row.setLabel(req.getLabel());
        row.setStatus(KeyStatus.active);
        row.setExpiresAt(req.getExpiresAt());
        apiKeyMapper.insert(row);

        log.info(
                "[apikey] 签发 appId={} keyId={} scope={} prefix={}",
                appId,
                row.getId(),
                scope,
                mat.getKeyPrefix());

        ApiKeyIssuedView view = new ApiKeyIssuedView();
        view.setId(row.getId());
        view.setAppId(appId);
        view.setScope(scope.name());
        view.setLabel(req.getLabel());
        view.setKeyPrefix(mat.getKeyPrefix());
        view.setPlaintext(mat.getPlaintext());
        view.setExpiresAt(req.getExpiresAt());
        view.setCreatedAt(Instant.now());
        return view;
    }

    public ApiKey revoke(Long appId, Long keyId) {
        ApiKey existing = apiKeyMapper.findById(keyId);
        if (existing == null || !existing.getAppId().equals(appId)) {
            throw new BusinessException(ErrorCode.KEY_NOT_FOUND, "key 不存在", 404);
        }
        if (existing.getStatus() == KeyStatus.revoked) {
            // 幂等：已撤销直接返回当前状态
            return existing;
        }
        apiKeyMapper.updateStatus(keyId, KeyStatus.revoked);
        existing.setStatus(KeyStatus.revoked);
        log.info("[apikey] 撤销 appId={} keyId={}", appId, keyId);
        return existing;
    }

    public List<ApiKey> listByAppId(Long appId) {
        appService.mustFindById(appId);
        return apiKeyMapper.listByAppId(appId);
    }
}
