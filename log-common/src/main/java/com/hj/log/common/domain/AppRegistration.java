package com.hj.log.common.domain;

import com.hj.log.common.base.BaseEntity;
import com.hj.log.common.enums.AppStatus;
import com.hj.log.common.enums.Environment;

/** 对应表 {@code app_registrations}。 */
public class AppRegistration extends BaseEntity {

    private String code;
    private String name;
    private String owner;
    private Environment environment;
    private AppStatus status;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public AppStatus getStatus() {
        return status;
    }

    public void setStatus(AppStatus status) {
        this.status = status;
    }
}
