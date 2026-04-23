package com.hj.log.source.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.common.domain.AppRegistration;
import com.hj.log.common.enums.AppStatus;
import com.hj.log.common.enums.Environment;
import com.hj.log.source.dto.CreateAppRequest;
import com.hj.log.source.service.AppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AppControllerMvcTest {

    @Mock private AppService appService;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AppController(appService)).build();
    }

    @Test
    void should_create_app_and_return_view() throws Exception {
        AppRegistration created = new AppRegistration();
        created.setId(1L);
        created.setCode("order-service");
        created.setName("Order");
        created.setOwner("trade");
        created.setEnvironment(Environment.prod);
        created.setStatus(AppStatus.active);
        when(appService.create(any())).thenReturn(created);

        CreateAppRequest req = new CreateAppRequest();
        req.setCode("order-service");
        req.setName("Order");
        req.setOwner("trade");
        req.setEnvironment("prod");

        mvc.perform(post("/api/v1/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("order-service"))
                .andExpect(jsonPath("$.data.environment").value("prod"))
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    void should_400_on_invalid_environment() throws Exception {
        CreateAppRequest req = new CreateAppRequest();
        req.setCode("svc");
        req.setName("Svc");
        req.setOwner("team");
        req.setEnvironment("qa"); // 不在 dev|staging|prod 中

        mvc.perform(post("/api/v1/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_400_on_invalid_code_pattern() throws Exception {
        CreateAppRequest req = new CreateAppRequest();
        req.setCode("Order_Service"); // 含大写 + 下划线，违反 [a-z0-9-]+
        req.setName("Order");
        req.setOwner("trade");
        req.setEnvironment("dev");

        mvc.perform(post("/api/v1/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_should_return_array() throws Exception {
        AppRegistration a = new AppRegistration();
        a.setId(1L);
        a.setCode("a");
        a.setName("A");
        a.setOwner("o");
        a.setEnvironment(Environment.dev);
        a.setStatus(AppStatus.active);
        when(appService.list(any(), any(), any())).thenReturn(java.util.List.of(a));

        mvc.perform(get("/api/v1/apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }
}
