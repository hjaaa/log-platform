package com.hj.log;

import com.hj.log.common.base.ResponseResult;
import com.hj.log.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestExceptionController {

    @PostMapping("/test/validation")
    public ResponseResult<String> validate(@Valid @RequestBody ValidationRequest request) {
        return ResponseResult.success(request.name());
    }

    @GetMapping("/test/business")
    public ResponseResult<String> business() {
        throw new BusinessException(4001, "业务异常");
    }

    @GetMapping("/test/unknown")
    public ResponseResult<String> unknown() {
        throw new RuntimeException("unknown");
    }

    public record ValidationRequest(@NotBlank(message = "name不能为空") String name) {
    }
}
