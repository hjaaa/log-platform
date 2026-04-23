---
feature_id: F-12-env-example
title: .env.example + .gitignore 增补
status: done
type: chore
module: ops
effort_pd: 0.25
depends_on: []
refs:
  - artifacts/detailed-design.md#8-部署与运行
---

## 实施清单

### `.env.example`（commit 入仓）

```
# 复制为 .env 后填值；.env 已 gitignore
# 生成 ADMIN_TOKEN：openssl rand -hex 32
LOG_PLATFORM_ADMIN_TOKEN=replace-me-with-openssl-rand-hex-32
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/log_platform?useSSL=false&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=replace-me
```

### `.gitignore` 增补

```
.env
log-sdk/fallback/
_archive/**/target/
```

### README 增补

- 在「构建与运行」前补一段：
  ```
  cp .env.example .env
  # 编辑 .env 填入真实值
  source .env
  mvn -pl log-web spring-boot:run
  ```

## 验收标准

- [ ] `.env.example` 存在且**不含**真实 secret（grep `replace-me` 必须命中）
- [ ] `.gitignore` 包含 `.env`、`log-sdk/fallback/`、`_archive/**/target/`
- [ ] README 增加 `cp .env.example .env && source .env` 启动指引
- [ ] git status 不显示 `.env` 文件（如果创建了一个本地副本）
