package com.hj.log.source.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.common.domain.ApiKey;
import com.hj.log.common.enums.KeyStatus;
import com.hj.log.common.enums.Scope;
import com.hj.log.source.dto.ApiKeyIssuedView;
import com.hj.log.source.dto.IssueKeyRequest;
import com.hj.log.source.service.ApiKeyService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerMvcTest {

    @Mock private ApiKeyService apiKeyService;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ApiKeyController(apiKeyService)).build();
    }

    @Test
    void issue_response_should_not_contain_keyHash() throws Exception {
        ApiKeyIssuedView v = new ApiKeyIssuedView();
        v.setId(33L);
        v.setAppId(12L);
        v.setScope("WRITE");
        v.setLabel("order-write");
        v.setKeyPrefix("lpk_AB12");
        v.setPlaintext("lpk_AB12CD34EF56GH78IJ90KL12MN34OP56QRST"); // 40 chars
        v.setCreatedAt(Instant.parse("2026-04-23T11:15:00Z"));
        when(apiKeyService.issue(eq(12L), any(IssueKeyRequest.class))).thenReturn(v);

        IssueKeyRequest req = new IssueKeyRequest();
        req.setScope("write");
        req.setLabel("order-write");

        mvc.perform(post("/api/v1/apps/12/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(33))
                .andExpect(jsonPath("$.data.plaintext").value("lpk_AB12CD34EF56GH78IJ90KL12MN34OP56QRST"))
                .andExpect(jsonPath("$.data.keyPrefix").value("lpk_AB12"))
                .andExpect(jsonPath("$.data.keyHash").doesNotExist());
    }

    @Test
    void issue_should_400_on_invalid_scope_pattern() throws Exception {
        IssueKeyRequest req = new IssueKeyRequest();
        req.setScope("admin"); // 控制层 Pattern 已拒绝（write|read）

        mvc.perform(post("/api/v1/apps/12/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revoke_should_return_revoked_view() throws Exception {
        ApiKey revoked = new ApiKey();
        revoked.setId(33L);
        revoked.setAppId(12L);
        revoked.setKeyPrefix("lpk_AB12");
        revoked.setScope(Scope.WRITE);
        revoked.setStatus(KeyStatus.revoked);
        when(apiKeyService.revoke(12L, 33L)).thenReturn(revoked);

        mvc.perform(delete("/api/v1/apps/12/keys/33"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(33))
                .andExpect(jsonPath("$.data.status").value("revoked"))
                // 不应泄露 hash
                .andExpect(jsonPath("$.data.keyHash").doesNotExist());
    }
}
