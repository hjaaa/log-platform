package com.hj.log;

import com.hj.log.common.base.BaseController;
import com.hj.log.common.base.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController extends BaseController {

    @GetMapping("/health")
    public ResponseResult<String> health() {
        return success("ok");
    }
}
