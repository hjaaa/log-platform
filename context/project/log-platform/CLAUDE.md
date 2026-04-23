# log-platform 项目规范

## 构建与运行

```bash
# 编译所有模块
mvn clean compile

# 打包（跳过测试）
mvn clean package -DskipTests

# 启动应用
mvn -pl log-web spring-boot:run

# 运行所有测试
mvn test

# 运行指定模块测试
mvn -pl log-core test
mvn -pl log-web test

# 运行单个测试类
mvn -pl log-core test -Dtest=MapperIntegrationTest
```

## 模块结构

```
log-platform
├── log-core   — 领域模型、Mapper、公共组件（无 Spring Web 依赖）
└── log-web    — 启动入口、Controller、全局异常处理（依赖 log-core）
```

**根包**：`com.hj.log`

## 分层约定

| 层 | 包 | 职责 |
|---|---|---|
| Controller | `com.hj.log.*.controller` | 参数校验（`@Valid`）、鉴权、调用 Service、返回 `ResponseResult<T>` |
| Service | `com.hj.log.*.service` | 业务编排；不直接操作 Mapper XML |
| Mapper | `com.hj.log.mapper` | MyBatis 接口，对应 `resources/mapper/*.xml` |
| Domain | `com.hj.log.domain` | 数据库 DO：`LogEntry` / `AlertRule` / `LogSource` / `NotificationConfig` |

- **禁止**把业务逻辑写入 Mapper XML
- DTO/VO 与 DO 分离，不混用

## 核心公共组件

- `ResponseResult<T>`（`com.hj.log.common.base`）：统一响应包装，Controller 全部走这个
- `BusinessException`（`com.hj.log.common.exception`）：业务异常，`GlobalExceptionHandler` 统一捕获
- `BaseEntity`：含 `id`、`createTime`、`updateTime`；所有 DO 继承
- `GlobalExceptionHandler`（`com.hj.log.common.handler`）：捕获 `MethodArgumentNotValidException`、`BusinessException`、`Exception`，返回标准错误体

## 数据库

- 生产：MySQL（schema 见 `log-core/src/main/resources/db/schema.sql`）
- 测试：H2 内存数据库（`@MybatisTest` + `@AutoConfigureTestDatabase`）
- JSON 字段（`context_data`、`notification_channels`、`match_config`）当前以 `String` 存储，业务层手动序列化

## 测试约定

- Mapper 层：`@MybatisTest` + H2，测试类在 `log-core`
- Web 层：`@SpringBootTest` + MockMvc，测试类在 `log-web`
- 命名：`should_xxx_when_yyy`

## 告警规则核心字段

| 字段 | 说明 |
|---|---|
| `matchPattern` | 正则，匹配日志内容 |
| `timeWindowSeconds` | 滑动窗口（秒） |
| `threshold` | 窗口内命中次数上限 |
| `coolDownPeriod` | 冷却期（秒），防告警风暴 |
| `notificationChannels` | JSON，关联通知渠道 ID 列表 |
