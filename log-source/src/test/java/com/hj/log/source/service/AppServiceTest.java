package com.hj.log.source.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hj.log.common.domain.AppRegistration;
import com.hj.log.common.enums.AppStatus;
import com.hj.log.common.enums.Environment;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import com.hj.log.source.dto.CreateAppRequest;
import com.hj.log.source.mapper.AppMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class AppServiceTest {

    @Mock private AppMapper appMapper;
    private AppService service;

    @BeforeEach
    void setUp() {
        service = new AppService(appMapper);
    }

    @Test
    void should_create_app_with_default_active_status() {
        when(appMapper.insert(any())).thenAnswer(inv -> {
            AppRegistration row = inv.getArgument(0);
            row.setId(42L);
            return 1;
        });

        AppRegistration app = service.create(buildRequest("order-service", "prod"));

        assertThat(app.getId()).isEqualTo(42L);
        assertThat(app.getStatus()).isEqualTo(AppStatus.active);
        assertThat(app.getEnvironment()).isEqualTo(Environment.prod);
    }

    @Test
    void should_throw_APP_INVALID_ENV_when_env_unknown() {
        CreateAppRequest req = buildRequest("svc", "qa");
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APP_INVALID_ENV);
    }

    @Test
    void should_translate_duplicate_key_to_APP_CODE_DUPLICATE() {
        when(appMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_app_code"));

        assertThatThrownBy(() -> service.create(buildRequest("dup", "dev")))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> {
                    BusinessException be = (BusinessException) t;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.APP_CODE_DUPLICATE);
                    assertThat(be.getHttpStatus()).isEqualTo(409);
                });
    }

    @Test
    void must_find_should_throw_404_when_missing() {
        when(appMapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.mustFindById(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> {
                    BusinessException be = (BusinessException) t;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.APP_NOT_FOUND);
                    assertThat(be.getHttpStatus()).isEqualTo(404);
                });
    }

    private CreateAppRequest buildRequest(String code, String env) {
        CreateAppRequest r = new CreateAppRequest();
        r.setCode(code);
        r.setName("Order Service");
        r.setOwner("trade-team");
        r.setEnvironment(env);
        return r;
    }
}
