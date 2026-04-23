package com.hj.log.source.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hj.log.common.domain.ApiKey;
import com.hj.log.common.domain.AppRegistration;
import com.hj.log.common.enums.KeyStatus;
import com.hj.log.common.enums.Scope;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import com.hj.log.source.dto.ApiKeyIssuedView;
import com.hj.log.source.dto.IssueKeyRequest;
import com.hj.log.source.mapper.ApiKeyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock private ApiKeyMapper apiKeyMapper;
    @Mock private AppService appService;
    private ApiKeyService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(apiKeyMapper, appService);
    }

    @Test
    void issue_should_return_plaintext_only_in_response() {
        when(appService.mustFindById(12L)).thenReturn(stubApp(12L));
        when(apiKeyMapper.insert(any())).thenAnswer(inv -> {
            ((ApiKey) inv.getArgument(0)).setId(33L);
            return 1;
        });

        ApiKeyIssuedView view = service.issue(12L, request("write", "order-write"));

        assertThat(view.getId()).isEqualTo(33L);
        assertThat(view.getScope()).isEqualTo("WRITE");
        assertThat(view.getPlaintext()).startsWith("lpk_").hasSize(40);
        assertThat(view.getKeyPrefix()).hasSize(8).isEqualTo(view.getPlaintext().substring(0, 8));
        // 落库的 ApiKey 不应含明文（mapper insert 已捕获）
        verify(apiKeyMapper, times(1)).insert(any());
    }

    @Test
    void issue_should_throw_APP_NOT_FOUND_when_app_missing() {
        when(appService.mustFindById(99L))
                .thenThrow(new BusinessException(ErrorCode.APP_NOT_FOUND, "应用不存在", 404));

        assertThatThrownBy(() -> service.issue(99L, request("write", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APP_NOT_FOUND);
        verify(apiKeyMapper, never()).insert(any());
    }

    @Test
    void issue_should_reject_admin_scope() {
        when(appService.mustFindById(12L)).thenReturn(stubApp(12L));
        assertThatThrownBy(() -> service.issue(12L, request("admin", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.KEY_INVALID_SCOPE);
        verify(apiKeyMapper, never()).insert(any());
    }

    @Test
    void issue_should_reject_unknown_scope() {
        when(appService.mustFindById(12L)).thenReturn(stubApp(12L));
        assertThatThrownBy(() -> service.issue(12L, request("WRITE-WRONG", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.KEY_INVALID_SCOPE);
    }

    @Test
    void revoke_should_set_status_to_revoked() {
        ApiKey existing = stubKey(33L, 12L, KeyStatus.active);
        when(apiKeyMapper.findById(33L)).thenReturn(existing);
        when(apiKeyMapper.updateStatus(33L, KeyStatus.revoked)).thenReturn(1);

        ApiKey result = service.revoke(12L, 33L);

        assertThat(result.getStatus()).isEqualTo(KeyStatus.revoked);
        verify(apiKeyMapper, times(1)).updateStatus(33L, KeyStatus.revoked);
    }

    @Test
    void revoke_should_be_idempotent_for_already_revoked() {
        ApiKey existing = stubKey(33L, 12L, KeyStatus.revoked);
        when(apiKeyMapper.findById(33L)).thenReturn(existing);

        ApiKey result = service.revoke(12L, 33L);

        assertThat(result.getStatus()).isEqualTo(KeyStatus.revoked);
        verify(apiKeyMapper, never()).updateStatus(any(), any());
    }

    @Test
    void revoke_should_throw_KEY_NOT_FOUND_when_missing() {
        when(apiKeyMapper.findById(33L)).thenReturn(null);

        assertThatThrownBy(() -> service.revoke(12L, 33L))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> {
                    BusinessException be = (BusinessException) t;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.KEY_NOT_FOUND);
                    assertThat(be.getHttpStatus()).isEqualTo(404);
                });
    }

    @Test
    void revoke_should_throw_KEY_NOT_FOUND_when_appId_mismatch() {
        ApiKey existing = stubKey(33L, 999L /* belongs to other app */, KeyStatus.active);
        when(apiKeyMapper.findById(33L)).thenReturn(existing);

        assertThatThrownBy(() -> service.revoke(12L, 33L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.KEY_NOT_FOUND);
        verify(apiKeyMapper, never()).updateStatus(any(), any());
    }

    private static AppRegistration stubApp(long id) {
        AppRegistration a = new AppRegistration();
        a.setId(id);
        a.setCode("svc-" + id);
        return a;
    }

    private static ApiKey stubKey(long id, long appId, KeyStatus status) {
        ApiKey k = new ApiKey();
        k.setId(id);
        k.setAppId(appId);
        k.setScope(Scope.WRITE);
        k.setStatus(status);
        k.setKeyPrefix("lpk_AB12");
        return k;
    }

    private static IssueKeyRequest request(String scope, String label) {
        IssueKeyRequest r = new IssueKeyRequest();
        r.setScope(scope);
        r.setLabel(label);
        return r;
    }
}
