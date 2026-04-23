package com.hj.log.source.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.hj.log.common.enums.Scope;
import org.junit.jupiter.api.Test;

class EndpointResolverTest {

    @Test
    void should_resolve_admin_for_apps() {
        assertThat(EndpointResolver.resolve("POST", "/api/v1/apps")).isEqualTo(Scope.ADMIN);
        assertThat(EndpointResolver.resolve("GET", "/api/v1/apps")).isEqualTo(Scope.ADMIN);
        assertThat(EndpointResolver.resolve("DELETE", "/api/v1/apps/12/keys/33")).isEqualTo(Scope.ADMIN);
    }

    @Test
    void should_resolve_write_for_post_logs() {
        assertThat(EndpointResolver.resolve("POST", "/api/v1/logs")).isEqualTo(Scope.WRITE);
        assertThat(EndpointResolver.resolve("post", "/api/v1/logs")).isEqualTo(Scope.WRITE);
    }

    @Test
    void should_resolve_read_for_get_logs() {
        assertThat(EndpointResolver.resolve("GET", "/api/v1/logs")).isEqualTo(Scope.READ);
        assertThat(EndpointResolver.resolve("GET", "/api/v1/logs/123")).isEqualTo(Scope.READ);
        assertThat(EndpointResolver.resolve("GET", "/api/v1/logs/trace/abc")).isEqualTo(Scope.READ);
        assertThat(EndpointResolver.resolve("GET", "/api/v1/logs/search")).isEqualTo(Scope.READ);
    }

    @Test
    void should_return_null_for_unmanaged_paths() {
        assertThat(EndpointResolver.resolve("GET", "/api/v2/logs")).isNull();
        assertThat(EndpointResolver.resolve("GET", "/health")).isNull();
        assertThat(EndpointResolver.resolve(null, "/api/v1/logs")).isNull();
    }

    @Test
    void should_not_treat_post_logs_path_as_read() {
        // POST /logs/anything 不被视为 write（write 只接受精确 /logs）
        assertThat(EndpointResolver.resolve("POST", "/api/v1/logs/123")).isNull();
    }
}
