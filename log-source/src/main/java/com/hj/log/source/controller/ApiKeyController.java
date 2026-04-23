package com.hj.log.source.controller;

import com.hj.log.common.base.ResponseResult;
import com.hj.log.source.dto.ApiKeyIssuedView;
import com.hj.log.source.dto.ApiKeyView;
import com.hj.log.source.dto.IssueKeyRequest;
import com.hj.log.source.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/apps/{appId}/keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseResult<ApiKeyIssuedView> issue(
            @PathVariable Long appId, @RequestBody @Valid IssueKeyRequest req) {
        return ResponseResult.ok(apiKeyService.issue(appId, req));
    }

    @GetMapping
    public ResponseResult<java.util.List<ApiKeyView>> list(@PathVariable Long appId) {
        return ResponseResult.ok(apiKeyService.listByAppId(appId).stream().map(ApiKeyView::from).toList());
    }

    @DeleteMapping("/{keyId}")
    public ResponseResult<ApiKeyView> revoke(@PathVariable Long appId, @PathVariable Long keyId) {
        return ResponseResult.ok(ApiKeyView.from(apiKeyService.revoke(appId, keyId)));
    }
}
