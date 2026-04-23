package com.hj.log.source.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hj.log.common.domain.ApiKey;
import com.hj.log.common.domain.AppRegistration;
import com.hj.log.common.enums.AppStatus;
import com.hj.log.common.enums.Environment;
import com.hj.log.common.enums.KeyStatus;
import com.hj.log.common.enums.Scope;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("mapper-test")
class MapperIntegrationTest {

    @Autowired private AppMapper appMapper;
    @Autowired private ApiKeyMapper apiKeyMapper;

    @Test
    void should_insert_and_query_app() {
        AppRegistration app = newApp("svc-mapper-1", Environment.dev);
        appMapper.insert(app);

        assertThat(app.getId()).isNotNull();
        assertThat(appMapper.findByCode("svc-mapper-1")).isNotNull();
        assertThat(appMapper.findById(app.getId())).extracting(AppRegistration::getCode).isEqualTo("svc-mapper-1");
    }

    @Test
    void list_should_filter_by_environment_and_status() {
        appMapper.insert(newApp("svc-mapper-2", Environment.prod));
        appMapper.insert(newApp("svc-mapper-3", Environment.dev));

        List<AppRegistration> prod = appMapper.list(null, Environment.prod, AppStatus.active);
        assertThat(prod).extracting(AppRegistration::getCode).contains("svc-mapper-2");
    }

    @Test
    void revoke_should_set_status_revoked_without_physical_delete() {
        AppRegistration app = newApp("svc-mapper-4", Environment.dev);
        appMapper.insert(app);

        ApiKey k = newKey(app.getId(), "lpk_AB12", "hash-aaa-aaa", Scope.WRITE);
        apiKeyMapper.insert(k);

        int affected = apiKeyMapper.updateStatus(k.getId(), KeyStatus.revoked);
        assertThat(affected).isEqualTo(1);

        ApiKey found = apiKeyMapper.findById(k.getId());
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo(KeyStatus.revoked);
    }

    @Test
    void touchLastUsed_should_update_timestamp() {
        AppRegistration app = newApp("svc-mapper-5", Environment.dev);
        appMapper.insert(app);
        ApiKey k = newKey(app.getId(), "lpk_CD34", "hash-bbb-bbb", Scope.READ);
        apiKeyMapper.insert(k);

        Instant now = Instant.now();
        apiKeyMapper.touchLastUsed(k.getId(), now);

        ApiKey reloaded = apiKeyMapper.findByHash("hash-bbb-bbb");
        assertThat(reloaded.getLastUsedAt()).isNotNull();
    }

    @Test
    void listByAppId_should_return_all_keys_including_revoked() {
        AppRegistration app = newApp("svc-mapper-6", Environment.dev);
        appMapper.insert(app);
        ApiKey active = newKey(app.getId(), "lpk_EE12", "hash-ccc-aaa", Scope.WRITE);
        ApiKey revoked = newKey(app.getId(), "lpk_EE34", "hash-ccc-bbb", Scope.READ);
        apiKeyMapper.insert(active);
        apiKeyMapper.insert(revoked);
        apiKeyMapper.updateStatus(revoked.getId(), KeyStatus.revoked);

        List<ApiKey> all = apiKeyMapper.listByAppId(app.getId());
        assertThat(all).extracting(ApiKey::getKeyPrefix).containsExactlyInAnyOrder("lpk_EE12", "lpk_EE34");
    }

    private AppRegistration newApp(String code, Environment env) {
        AppRegistration a = new AppRegistration();
        a.setCode(code);
        a.setName(code);
        a.setOwner("team");
        a.setEnvironment(env);
        a.setStatus(AppStatus.active);
        return a;
    }

    private ApiKey newKey(Long appId, String prefix, String hash, Scope scope) {
        ApiKey k = new ApiKey();
        k.setAppId(appId);
        k.setKeyPrefix(prefix);
        k.setKeyHash(hash);
        k.setScope(scope);
        k.setStatus(KeyStatus.active);
        return k;
    }
}
