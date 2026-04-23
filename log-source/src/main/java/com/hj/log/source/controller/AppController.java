package com.hj.log.source.controller;

import com.hj.log.common.base.ResponseResult;
import com.hj.log.source.dto.AppView;
import com.hj.log.source.dto.CreateAppRequest;
import com.hj.log.source.service.AppService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/apps")
public class AppController {

    private final AppService appService;

    public AppController(AppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public ResponseResult<AppView> create(@RequestBody @Valid CreateAppRequest req) {
        return ResponseResult.ok(AppView.from(appService.create(req)));
    }

    @GetMapping
    public ResponseResult<List<AppView>> list(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false, defaultValue = "active") String status) {
        List<AppView> items = appService.list(code, environment, status).stream().map(AppView::from).toList();
        return ResponseResult.ok(items);
    }
}
