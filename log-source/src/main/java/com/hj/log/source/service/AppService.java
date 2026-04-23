package com.hj.log.source.service;

import com.hj.log.common.domain.AppRegistration;
import com.hj.log.common.enums.AppStatus;
import com.hj.log.common.enums.Environment;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import com.hj.log.source.dto.CreateAppRequest;
import com.hj.log.source.mapper.AppMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AppService {

    private static final Logger log = LoggerFactory.getLogger(AppService.class);

    private final AppMapper appMapper;

    public AppService(AppMapper appMapper) {
        this.appMapper = appMapper;
    }

    public AppRegistration create(CreateAppRequest req) {
        Environment env = Environment.fromCode(req.getEnvironment());
        if (env == null) {
            throw new BusinessException(ErrorCode.APP_INVALID_ENV, "environment 不合法");
        }
        AppRegistration app = new AppRegistration();
        app.setCode(req.getCode());
        app.setName(req.getName());
        app.setOwner(req.getOwner());
        app.setEnvironment(env);
        app.setStatus(AppStatus.active);
        try {
            appMapper.insert(app);
        } catch (DuplicateKeyException dup) {
            throw new BusinessException(ErrorCode.APP_CODE_DUPLICATE, "应用 code 已存在", 409, dup);
        }
        log.info("[app] 注册成功 appId={} code={} environment={}", app.getId(), app.getCode(), env);
        return app;
    }

    public List<AppRegistration> list(String code, String environment, String status) {
        Environment env = environment == null ? null : Environment.fromCode(environment);
        AppStatus st = status == null ? null : AppStatus.fromCode(status);
        return appMapper.list(code, env, st);
    }

    public AppRegistration mustFindById(Long id) {
        AppRegistration app = appMapper.findById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND, "应用不存在", 404);
        }
        return app;
    }
}
