package com.hj.log.web.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hj.log.common.enums.LogLevel;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new TestController())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

    @Test
    void should_return_business_status_and_code_when_business_exception() throws Exception {
        MvcResult result =
                mvc.perform(get("/__test__/business"))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("APP_CODE_DUPLICATE"))
                        .andExpect(jsonPath("$.message").value("重复应用编码"))
                        .andExpect(jsonPath("$.data").doesNotExist())
                        .andReturn();
        assertResponseHasNoStackTrace(result);
    }

    @Test
    void should_return_400_with_generic_body_when_validation_fails() throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/__test__/validate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST))
                        .andExpect(jsonPath("$.message").value("请求参数不合法"))
                        .andReturn();
        assertResponseHasNoStackTrace(result);
    }

    @Test
    void should_return_400_with_generic_body_when_request_param_type_mismatch() throws Exception {
        MvcResult result =
                mvc.perform(get("/__test__/mismatch").param("level", "not-a-level"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST))
                        .andExpect(jsonPath("$.message").value("请求参数不合法"))
                        .andReturn();
        assertResponseHasNoStackTrace(result);
    }

    @Test
    void should_return_500_generic_body_when_unknown_exception() throws Exception {
        MvcResult result =
                mvc.perform(get("/__test__/boom"))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR))
                        .andExpect(jsonPath("$.message").value("服务暂时不可用"))
                        .andReturn();
        assertResponseHasNoStackTrace(result);
    }

    @Test
    void should_mask_lpk_prefix_in_internal_messages() {
        assertThat(GlobalExceptionHandler.maskSensitive("leak lpk_AB12CD34 tail"))
                .isEqualTo("leak lpk_*** tail");
        assertThat(GlobalExceptionHandler.maskSensitive("plain"))
                .isEqualTo("plain");
        assertThat(GlobalExceptionHandler.maskSensitive(null)).isNull();
        assertThat(
                        GlobalExceptionHandler.maskSensitive(
                                "lpk_aaa and lpk_bbb both hit"))
                .isEqualTo("lpk_*** and lpk_*** both hit");
    }

    private static void assertResponseHasNoStackTrace(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .as("response body must not expose stack frames")
                .doesNotContain("at com.hj.log")
                .doesNotContain(".java:");
    }

    @RestController
    @RequestMapping("/__test__")
    static class TestController {

        @GetMapping("/business")
        public void throwBusiness() {
            throw new BusinessException("APP_CODE_DUPLICATE", "重复应用编码", 409);
        }

        @GetMapping("/boom")
        public void throwUnknown() {
            throw new RuntimeException("boom lpk_SECRET123");
        }

        @GetMapping("/mismatch")
        public String mismatch(@RequestParam LogLevel level) {
            return level.name();
        }

        @PostMapping("/validate")
        public String validate(@Valid @RequestBody ValidateRequest req) {
            return req.name;
        }
    }

    static class ValidateRequest {
        @NotBlank(message = "must not be blank")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
