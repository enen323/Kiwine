# PrepDeck 后端单体化 + MySQL 改造 — 设计文档

日期：2026-11-24
状态：待批准

## 意图与为什么

用户在新电脑上开发 PrepDeck：不再部署 Nacos，数据库从 PostgreSQL 换成本机已安装的 MySQL，并把 8 个微服务 + 网关合并为一个单体 Spring Boot 应用。目标是一条命令在本地跑起整个后端，前端零改动。

## 目标

- **单体**：一个 Maven 模块、一个 `application.yml`、一个启动类、一个 jar，端口 8080（前端 vite proxy 已指向 8080）。
- **去 Nacos**：服务发现与配置中心全部移除，服务间调用改为进程内直接调用。
- **MySQL**：连本机 MySQL，库名 `prepdeck`，表结构由 `sql/init/init-database.sql`（转换为 MySQL 方言）初始化；驱动 `com.mysql:mysql-connector-j`（版本由 Spring Boot BOM 管理）。
- **认证不退化**：网关的 JWT 校验 + Redis 黑名单 + 请求头注入能力完整移植进单体。
- **无 AI key 也能启动**：未配置 `spring.ai.deepseek.api-key` 时服务正常启动，AI 接口返回明确业务错误。

## 非目标

- 不做旧 PostgreSQL 数据迁移（用户确认：全新建库，不要旧数据）。
- 不配 DeepSeek api-key（用户确认：先留空）。
- 不动前端。
- 不引入任何新依赖（MySQL 驱动是替换 pg 驱动，不是新增）。
- 不做职责之外的重构清理（Feign 本地客户端作为可后续清理项保留）。

## 反目标（出现即失败）

- 单体启动后登录态失效、需登录接口全部 401（认证移植失败）。
- 启动即失败（如 `AiClient` 无 bean）。
- 残留 nacos / sentinel / feign / gateway 依赖或配置导致启动报错、告警或行为变化。
- 数据库方言转换丢表、丢索引、丢注释。

## 约束

- Java 21、Spring Boot 3.3.5、Spring Cloud 2023.0.3、Spring Cloud Alibaba 2023.0.1.3（后者随 nacos/sentinel 一并移除）。
- 本机已装 MySQL（8.x；JSON 类型需 5.7+）。
- Redis 保留（docker-compose 的 redis 服务继续用，令牌黑名单依赖）。
- `GATEWAY_SECRET` 环境变量约定保留（`SecurityContext` 依赖）。
- git 管理，源码搬迁用 `git mv` 保留历史。

## 方案对比（已与用户讨论）

1. **保留微服务 + 静态实例清单**（`spring.cloud.discovery.client.simple.instances`，Feign 名与网关 `lb://` 不动）——用户否决：要的是单体。
2. **保留多模块 + 新增 app 模块聚合**（目录和 9 个 pom 不动，只加一个启动模块）——否决：留下 9 个模块外壳，依赖声明仍是 9 份。
3. **单模块（选定）**：根 pom 直接变成 Spring Boot 应用，8 个服务 + common 的源码全部并进根 `src/main/java`，删 9 个模块 pom、9 个启动类留 1 个，删 gateway 模块。

## 具体设计

### 1. 结构（单模块）

- 根 `pom.xml`：`packaging` 改 `jar`，挂 `spring-boot-maven-plugin`；依赖取各模块并集：web、validation、mybatis-plus-boot3-starter、mysql-connector-j(runtime)、jjwt 三件套、actuator、data-redis、security-crypto、hutool-all、spring-ai-starter-model-deepseek、lombok、test。
- 删除：spring-cloud-alibaba BOM、nacos/sentinel/openfeign/loadbalancer 相关依赖；若移除后无任何 spring-cloud 使用，一并删除 Spring Cloud BOM（builder 核实，见"留给 builder"）。
- 源码搬迁（`git mv`）：`{user,resume,interview,report,question,quota,payment,job}-service/src/main/java/com/prepdeck/<svc>/**` → `src/main/java/com/prepdeck/<svc>/**`；`common` 同样处理；`src/test` 随主源码一并搬迁。包名本来都是 `com.prepdeck.*`，零冲突。
- 删除：9 个模块目录下的 pom、9 个 `*Application.java`、整个 `gateway/` 目录。
- 新增 `com.prepdeck.PrepDeckApplication`：`@SpringBootApplication(scanBasePackages = "com.prepdeck")` + `@MapperScan("com.prepdeck")`（覆盖各服务原来的 `.mapper` / `.repository` 两种包名）。
- 现有行为保持不变的部分：`MyBatisPlusConfig` / `JacksonConfig` 今天就被各服务显式扫描（`scanBasePackages` 含 `com.prepdeck.common`），单体后同样激活；`ThreadPoolConfig` 今天就是死的（`@AutoConfiguration` 无 `AutoConfiguration.imports` 文件，`@Async` 未生效），单体后**保持死**——不在本次范围内"顺手修复"。

### 2. 配置合并（根 `src/main/resources/application.yml`）

- `server.port: 8080`。
- datasource：`jdbc:mysql://${DB_HOST:localhost}:3306/${DB_NAME:prepdeck}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true`，driver `com.mysql.cj.jdbc.Driver`，`${DB_USERNAME:root}` / `${DB_PASSWORD:root}`；Hikari 参数取现有最大值（maximum-pool-size 20 / minimum-idle 5 / idle-timeout 300000 / connection-timeout 10000 / max-lifetime 1200000）。
- redis：host/port 默认 `localhost:6379`（取代各服务不一的默认值）。
- mybatis-plus 段：各服务原一致，取一份（ASSIGN_ID、logic-delete、下划线转驼峰、StdOut 日志）。
- 保留：`interview.prompt` 整段（来自 interview-service）、`spring.servlet.multipart` 10MB/20MB、`logging.level.com.prepdeck`。
- 删除：`spring.cloud.*` 全部（nacos/gateway/loadbalancer/sentinel）、`feign.*`、所有 `spring.config.import: optional:nacos:...` 行。
- 无需配置：`resume.upload.dir`、`wechatpay.app-id` 均有默认值。

### 3. 认证移植（安全关键，不可省）

- 新增 `com.prepdeck.common.filter.AuthFilter`（`OncePerRequestFilter`）：
  - 白名单与 `AuthGlobalFilter` 完全一致：`/api/v1/auth/login`、`/api/v1/auth/register`、`/api/v1/auth/refresh`、`/api/system/login`、`/api/v1/payment/callback/`、`/actuator/health`、`/health`（前缀匹配）。
  - 流程：提取 `Authorization: Bearer` → 取 jti 用 `StringRedisTemplate`（阻塞 API）查 `TokenBlacklist.blacklistKey(jti)` → `JwtProvider.validateToken` → 注入 `X-User-Id`（subject）、`X-User-Role`、`X-Gateway-Source: prepdeck-gateway`、`X-Gateway-Secret` 请求头（`HttpServletRequestWrapper`）→ 失败 401。
  - `GATEWAY_SECRET` 取环境变量、默认值与现状一致。
  - **必须替换而非追加**：wrapper 对 `X-User-Id` / `X-User-Role` / `X-Gateway-Source` / `X-Gateway-Secret` 四个头同时覆盖 `getHeader` 和 `getHeaders`，杜绝客户端伪造头绕过（网关的 `mutate().header()` 是替换语义，servlet 侧必须一致）。
- `SecurityContext` 不改：仍校验上述请求头，行为与网关时代一致（header 由自家 filter 注入）。
- CORS 移植：`gateway/config/CorsConfig.java` 的 reactive `CorsWebFilter` → servlet 版（`CorsFilter` 或 `WebMvcConfigurer#addCorsMappings`），保留 `ALLOWED_ORIGINS` 环境变量、默认 `http://127.0.0.1:*`、`setAllowedOriginPatterns`、allowCredentials。
- 测试：原 gateway 的 `AuthGlobalFilterTest`/`CorsConfigTest` 随 gateway 删除；为新 `AuthFilter` 补单元测试：白名单放行、无 token 401、有效 token 注入 header、黑名单 token 401。

### 4. Feign → 本地客户端

- 8 个 `@FeignClient` 接口逐一转为同名 `@Component` 类（接口改 class，方法签名不变），方法体直接调用目标服务的 `@Service` bean；契约以目标 controller 的方法为准（builder 逐个对照）。
- 涉及的调用关系：payment→quota；interview→resume/quota/question；resume→quota；job→resume/quota；question→interview。
- 删除 8 个 `*FallbackFactory`、`@EnableFeignClients`、`feign.*` 配置、openfeign/loadbalancer 依赖。
- **事务语义保持现状**：今天跨服务调用是独立事务（HTTP 边界即提交）。合并后默认会加入调用方事务，因此对原跨服务边界上的**写操作**显式加 `@Transactional(propagation = Propagation.REQUIRES_NEW)`（至少覆盖：payment→quota 发放、interview→quota 扣减、resume/job→quota 扣减、quota 月度重置），保持"先提交后返回"的现有语义；只读调用不加。
- **已知行为差异（用户已认可方向）**：本地调用失败不再降级为"服务不可用"，真实异常直接冒泡到 `GlobalExceptionHandler`。语义更正确，但错误码会变。

### 5. AI 未配置可启动

- 新增 `com.prepdeck.common.ai.NoOpAiClient`：`@Component @ConditionalOnMissingBean(AiClient.class)`，三个方法抛 `BusinessException`（优先复用 `ErrorCode.LLM_SERVICE_ERROR`，builder 核对；确无合适码才新增 `AI_NOT_CONFIGURED`）。
- `DeepSeekClient`：删 `@SentinelResource` 注解（sentinel 移除后编译不过）；`@ConditionalOnProperty("spring.ai.deepseek.api-key")` 保留。
- 删除 `AiClientFallback`（仅 sentinel fallbackClass 使用）。
- 四个注入点（`InterviewEngine`、`ResumeServiceImpl`、`JobApplicationServiceImpl`、`AiCoverLetterServiceImpl`）零改动。

### 6. Sentinel 移除

- 删 common/payment/interview/resume/job 五个 pom 的 `spring-cloud-starter-alibaba-sentinel`；删 yml 中 `spring.cloud.sentinel`、`feign.sentinel.enabled`；删 `@SentinelResource` 与 `AiClientFallback`；根 pom 删 Spring Cloud Alibaba BOM。
- 理由：单体里 Sentinel 只能靠 nacos/dashboard 推送规则，已无意义。

### 7. MySQL 化

- 依赖：`org.postgresql:postgresql` → `com.mysql:mysql-connector-j`（不写死版本，走 Spring Boot BOM）。
- 连接串：9 个 `application.yml` + 5 个测试类的 `@TestPropertySource` 全部改 mysql（pg 的 `currentSchema=interview` 参数删除，MySQL 库名即 `prepdeck`）。
- `JsonbTypeHandler`（common 与 resume-service 两份）：`ps.setObject(i, json, Types.OTHER)` → `Types.VARCHAR`（MySQL Connector/J 不支持 OTHER）。类名保留，避免牵连 `@TableField(typeHandler=...)` 引用。
- `sql/init/init-database.sql`（596 行）转 MySQL 方言：
  - 头部 `CREATE DATABASE IF NOT EXISTS prepdeck ... ; USE prepdeck;`（本机手动执行场景）。
  - 删 `CREATE SCHEMA`、全部 `CREATE SEQUENCE`（MyBatis-Plus `ASSIGN_ID` 雪花 ID，不依赖序列）；表名去 `interview.` 前缀。
  - `JSONB` → `JSON`；`TIMESTAMP` → `DATETIME`（`DEFAULT CURRENT_TIMESTAMP` 保留；`update_time` 不加 `ON UPDATE`，与应用层 `updateFill` 职责保持一致）。
  - `BOOLEAN` 保留（MySQL 别名）；`COMMENT ON TABLE/COLUMN` → 表/列 `COMMENT '...'`；索引与唯一约束语法转换；若有外键一并转换（builder 核对）。
  - **必须一并吸收两个游离的 migration 文件**（不在 init-database.sql 里，漏掉则 job 服务每张表都报不存在、"每人一个进行中面试"约束丢失）：
    - `job-service/src/main/resources/db/migration/V1__create_job_tables.sql`：`job_applications`、`ai_cover_letters` 两表 + 3 个索引。
    - `interview-service/src/main/resources/db/migration/V1.0.1__add_active_interview_unique_index.sql`：`uk_user_active_interview` 是 pg 部分唯一索引，MySQL 无法表达，按"部分唯一索引"决策处理。
- 初始化方式：`mysql -u root -p < sql/init/init-database.sql`（写进设计文档与仓库 README 若有）。
- `docker/docker-compose.yml`：删 `postgres` service 与 `postgres-data` volume、删 `nacos` service；`redis` 保留；头注释更新（不再挂载 pg init 脚本）。

### 7b. 全文检索（search_vector）— 已决策：方案 A（LIKE）

`init-database.sql` 有 `search_vector TSVECTOR` + GIN 索引 + `setweight/to_tsvector` 触发器；`question-service/src/main/resources/mapper/QuestionMapper.xml` 4 处用 `search_vector @@ websearch_to_tsquery('simple', #{keyword})`；`QuestionEntity` 把 `search_vector` 映射为 String。MySQL 无这套机制。

**选定方案 A**：mapper 4 处改为 `AND (q.title LIKE ... OR q.content LIKE ...)`；`QuestionEntity` 删 `search_vector` 字段；init SQL 删该列、GIN 索引与触发器。中文可用、改动最小；失去相关性排序，数据量大后全表扫描。`shortcut: 数据量增长后用 FULLTEXT ngram 替代 LIKE 方案`。

### 7c. 部分唯一索引 — 已决策：方案 A（删索引，应用层检查）

两处 pg 部分唯一索引 MySQL 无法表达：
- `uk_user_account_fingerprint ... WHERE fingerprint IS NOT NULL`（init-database.sql ~230 行）——指纹去重，装饰性。
- `uk_user_active_interview ON interview_record(user_id) WHERE deleted=false AND status IN ('CREATED','IN_PROGRESS')`——"每人一个进行中面试"硬业务规则，`InterviewServiceImpl.checkNoActiveInterview` 应用层也查。

**选定方案 A**：DDL 均不建这两个索引，依赖应用层检查。`shortcut: 并发双开面试的竞态窗口存在（应用检查非原子），生产化需生成列唯一索引`。

### 8. 测试

- 67 个测试随源码搬迁；含 nacos/pg 属性的改为 mysql 属性（nacos 属性直接删）。
- **删除测试被删类的测试**：6 个 `*FallbackFactoryTest`（interview×2、job×2、payment、resume）+ `common/src/test/java/com/prepdeck/common/ai/AiClientFallbackTest.java`（对应类已删，否则编译不过）。
- DB 集成测试（mapper 测试）要求本机 MySQL 已按新 init SQL 建库建表；纯单元测试不受影响。
- `@SpringBootTest` 集成测试合并后会启动整个单体上下文（8 个服务全量 bean），需要 MySQL + Redis 就绪——验收条件已涵盖。
- 验收底线：`mvn -DskipTests package` 必须通过；全量 `mvn test` 在 MySQL 就绪后通过。

### 9. 前端

- 零改动。vite proxy 仍指向 8080；控制器路径本就是 `/api/v1/**`（已核对 11 个控制器无路径冲突）。

## 已知行为差异（需用户知晓）

1. 本地调用失败不再降级为"服务不可用"（SERVICE_UNAVAILABLE），直接抛真实业务异常。
2. 提供 deepseek key 之前，AI 相关接口返回 `LLM_SERVICE_ERROR`。
3. 端口从"9 服务 + 网关"收敛为单一 8080。
4. 旧 Nacos 上可能还有仓库里没有的 per-service `-dev.yml` 覆盖（日志级别等）——无法找回，影响仅限默认值差异；AI 相关（deepseek key/base-url）按用户决定留空。
5. 关键词搜索改用 LIKE：失去 pg 的相关性排序，大数据量下全表扫描（已决策，见 7b）。
6. 两个部分唯一索引不再有 DB 层保证，仅应用层检查，存在并发竞态窗口（已决策，见 7c）。

## 留给 builder 的事项

- 是否存在 MyBatis mapper XML 及位置（有则搬到 `src/main/resources`）。
- `ErrorCode` 核对：有无现成"AI 未配置"码，没有则复用 `LLM_SERVICE_ERROR`。
- 移除 Spring Cloud BOM 前确认无残留 spring-cloud 引用（重点看 `common/config/ThreadPoolConfig` 等）。
- 复核 `QuestionController`（`/api/v1`）与其它控制器无路径冲突。
- 确认 MyBatis-Plus 自动扫描覆盖 `com.prepdeck.*`（是否需要显式 `@MapperScan`）。
- 596 行 SQL 逐表转换后的自检（表数、索引数与原脚本一致）。

## 验收标准

1. `mvn -DskipTests package` 成功。
2. 本机 MySQL 执行新 `sql/init/init-database.sql` 成功，表/索引数量与原 pg 脚本一致。
3. 启动单体：`/actuator/health` 200；无 token 访问 `/api/v1/users/**` 返回 401；登录拿 token 后 200。
4. Redis 容器在跑时，登出后旧 token 立即失效（黑名单生效）。
5. 无 deepseek key 启动成功，AI 接口返回明确业务错误而非 500 堆栈。
6. 前端 `npm run dev`：登录 → 创建简历 → 开始面试主链路可用。
