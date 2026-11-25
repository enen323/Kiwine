# PrepDeck 后端单体化 + MySQL 改造 — 实施计划

日期：2026-11-24
状态：待执行

## Goal

把 prepdeck-backend 从「Nacos + 9 微服务 + 网关 + PostgreSQL」改造为「单模块单体 Spring Boot + 本机 MySQL」：一条命令本地起整个后端，前端零改动，认证能力不退化。

## Architecture

- 单 Maven 模块：根 `pom.xml` 即 Spring Boot 应用（`packaging=jar` + `spring-boot-maven-plugin`）。
- 8 个服务 + common 的源码 `git mv` 进根 `src/main/java`（包名同为 `com.prepdeck.*`，零冲突），删 9 个模块 pom、9 个启动类、整个 gateway 目录、全部测试。
- 唯一启动类 `com.prepdeck.PrepDeckApplication`：`@SpringBootApplication(scanBasePackages = "com.prepdeck")` + `@MapperScan(basePackages = "com.prepdeck", markerInterface = BaseMapper.class)`。
- 网关的认证 filter 与 CORS 移植为 servlet 组件；Feign 调用改进程内 `@Component` 直调；Sentinel/Nacos 全删。
- 数据层换 MySQL：driver、`JsonbTypeHandler`、`QuestionMapper.xml` 搜索、init SQL 全部 MySQL 化。
- AI：删 `@ConditionalOnProperty`，缺 `DEEPSEEK_API_KEY` 启动即失败（fail fast）。

## Tech Stack

Java 21、Spring Boot 3.3.3.5（3.3.5）、Spring MVC、MyBatis-Plus 3.5.7、MySQL Connector/J（版本由 Spring Boot BOM 管理）、Redis（data-redis）、JJWT、Actuator、Hutool、spring-ai-starter-model-deepseek、Lombok。移除：Nacos、Sentinel、OpenFeign、LoadBalancer、Spring Cloud BOM、Spring Cloud Alibaba BOM、spring-boot-starter-test。

## Spec

设计文档（唯一权威，含全部决策与理由）：
`docs/superpowers/specs/2026-11-24-prepdeck-monolith-mysql-design.md`。
本文只做任务分解；冲突时以设计文档为准。

## Global Constraints

- 使用 `git mv` 搬源码，保留 git 历史。
- 不引入任何新依赖；MySQL 驱动是替换 pg 驱动，不算新增。
- 不写测试、不搬测试：67 个测试文件全删；根 pom 删 `spring-boot-starter-test`。唯一构建门禁是编译通过（`mvn package` 无测试阶段）。
- 不动前端（`prepdeck-frontend/` 零改动）。
- 认证不可退化：`AuthFilter` 注入头必须**替换**（`getHeader`/`getHeaders` 同时覆盖），杜绝客户端伪造头绕过。
- 每个任务完成后自行 commit（任务边界即 commit 边界），不跨任务混提交。
- 除已列 Task 4 的 REQUIRES_NEW 清单外，不加新事务/新配置/新抽象。

## Review Focus（每项挂到拥有该代码的任务）

1. **认证安全**：`AuthFilter` 白名单与 `AuthGlobalFilter` 完全一致；四头替换语义（防伪造）；401 行为。→ Task 3
2. **Feign 契约保真**：8 个客户端改本地 `@Component` 后方法签名不变、语义等价、无遗漏调用方。→ Task 4
3. **事务语义**：REQUIRES_NEW 只加在设计点名的 4 处写路径，不加多余处。→ Task 4
4. **MySQL DDL 等价**：表数/索引数/列/约束与原 pg 脚本一致；两个游离 migration 已吸收；两个部分唯一索引不建。→ Task 7
5. **搜索与数据层转换**：`QuestionMapper.xml` 4 处 LIKE 改写、`QuestionEntity` 删 `search_vector`、两份 `JsonbTypeHandler` 改 `Types.VARCHAR`。→ Task 6

---

## Tasks

### Task 1：单模块骨架（结构搬迁）

**Files**:
- Create: `prepdeck-backend/src/main/java/com/prepdeck/PrepDeckApplication.java`
- Modify: `prepdeck-backend/pom.xml`（整体重写）
- Move（`git mv`）: `prepdeck-backend/{user,resume,interview,report,question,quota,payment,job}-service/src/main/java/com/prepdeck/<svc>/**` 与 `prepdeck-backend/common/src/main/java/com/prepdeck/common/**` → `prepdeck-backend/src/main/java/com/prepdeck/<svc>|common/**`；resources 一并合并：5 个 mapper XML（`question-service/.../mapper/` 4 个 + `quota-service/.../mapper/UserQuotaMapper.xml`）→ `prepdeck-backend/src/main/resources/mapper/`；2 个 migration SQL → `prepdeck-backend/src/main/resources/db/migration/`（Task 7 吸收后删）
- Delete: 9 个模块 `pom.xml`、9 个 `*Application.java`、9 个模块 `src/main/resources/application*.yml`、整个 `prepdeck-backend/gateway/` 目录、所有模块的 `src/test/` 目录（含 common 的）

**Interfaces**:
- `com.prepdeck.PrepDeckApplication`：`@SpringBootApplication(scanBasePackages = "com.prepdeck")`、`@MapperScan(basePackages = "com.prepdeck", markerInterface = BaseMapper.class)`（markerInterface 必须有，否则全部接口被注册为 mapper，见设计文档 §1）、标准 `main`。

**Steps**:
- [ ] 读设计文档"具体设计 §1"，按 Files 列执行 `git mv`（主源码 + mapper XML + migration SQL；`src/test` 不搬、直接删）
- [ ] 删除 9 个 `*Application.java`、9 个模块 pom、9 个模块 `src/main/resources/application*.yml`、整个 gateway 目录
- [ ] 重写根 `pom.xml`：`packaging=jar`；挂 `spring-boot-maven-plugin`；依赖按设计文档 §1 并集清单；删 spring-cloud BOM、alibaba BOM、nacos/sentinel/openfeign/loadbalancer、`spring-boot-starter-test`
- [ ] 新建 `PrepDeckApplication.java`（见 Interfaces；`@MapperScan` 必须带 `markerInterface = BaseMapper.class`）
- [ ] 验证：`grep -rn "spring-cloud\|nacos\|sentinel\|openfeign" pom.xml` 无输出；`find . -name "*Application.java" | grep -v PrepDeckApplication` 为空；`find . -path "*/src/test" -type d` 为空；`find . -name "application*.yml" -not -path "./src/*"` 为空；`ls src/main/resources/mapper/` 有 5 个 XML；`mvn -q validate` 通过（pom 可解析）
- [ ] Commit

**注意**：本任务完成后 `mvn compile` **预期失败**——feign 接口与 `@SentinelResource` 仍引用已删依赖，Task 4/5 完成后恢复；首次全量编译在 Task 9。验证只用 `mvn validate`。

**依赖**：Task 2–8 的路径都以本任务的搬迁结果为前提（如 Task 6 的 `src/main/resources/mapper/QuestionMapper.xml`）。

**依赖**：无（阻塞其余全部任务）。

---

### Task 2：合并 application.yml

**Files**:
- Create: `prepdeck-backend/src/main/resources/application.yml`（单体版，源自 9 份合并）
- Delete: 9 个模块的 `src/main/resources/application*.yml`（随模块目录删除时核对无遗漏）

**Steps**:
- [ ] 按设计文档 §2 写 `application.yml`：port 8081、MySQL 连接串（`jdbc:mysql://${DB_HOST:localhost}:3306/${DB_NAME:prepdeck}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true`）、driver `com.mysql.cj.jdbc.Driver`、`${DB_USERNAME:root}`/`${DB_PASSWORD:root}`、Hikari 20/5/300000/10000/1200000、redis localhost:6379、mybatis-plus 一段、`interview.prompt` 整段、multipart 10MB/20MB、`logging.level.com.prepdeck`
- [ ] AI 段：`spring.ai.deepseek.api-key: ${DEEPSEEK_API_KEY}`（无默认值）+ `spring.ai.deepseek.base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}`
- [ ] 确认删除：`spring.cloud.*`、`feign.*`、所有 `spring.config.import: optional:nacos:...`
- [ ] 验证：`grep -n "nacos\|feign\|spring.cloud" src/main/resources/application.yml` 无输出；`grep -c "" application.yml` 行数合理；保留段核对（`interview.prompt` 键与 interview-service 原文件一致）
- [ ] Commit

**依赖**：Task 1。

---

### Task 3：认证与 CORS 移植（安全关键）

**Files**:
- Create: `prepdeck-backend/src/main/java/com/prepdeck/common/filter/AuthFilter.java`
- Create: servlet 版 CORS 配置（`com.prepdeck.common.config` 下 `WebMvcConfigurer` 或 `CorsFilter` bean，二选一，与现有 config 包风格一致）
- Reference（只读不改）: `prepdeck-backend/common/src/main/java/com/prepdeck/common/util/SecurityContext.java`、`TokenBlacklist`
- **例外（Task 3 review 裁决，见设计文档 §3 末条）**: `JwtProvider.createToken` 补 `.id(UUID.randomUUID().toString())` 一行 + import——不设 jti 则黑名单分支永不执行、登出无法吊销，设计验收标准必败。仅此一行，JwtProvider 其余零改动。

**Interfaces**:
- `AuthFilter extends OncePerRequestFilter`：白名单前缀匹配（`/api/v1/auth/login`、`/api/v1/auth/register`、`/api/v1/auth/refresh`、`/api/system/login`、`/api/v1/payment/callback/`、`/actuator/health`、`/health`）；流程：取 `Authorization: Bearer` → jti → `StringRedisTemplate` 查 `TokenBlacklist.blacklistKey(jti)` → `JwtProvider.validateToken` → wrapper 注入 `X-User-Id`（subject）/`X-User-Role`/`X-Gateway-Source: prepdeck-gateway`/`X-Gateway-Secret`（env `GATEWAY_SECRET`，默认值 `10b8e581a75345c78bdc195188a74e0f3dccf51ef33d4452b34c6bed67cd7422`）；失败 401。
- wrapper 必须对四头同时覆盖 `getHeader` 与 `getHeaders`（替换语义，防客户端伪造）。

**Steps**:
- [ ] 读 `AuthGlobalFilter`（原 gateway，Task 1 已删——从 git 历史 `git show HEAD~N:<path>` 或 design 文档取白名单/流程）与 `SecurityContext`、`TokenBlacklist`、`JwtProvider`
- [ ] 实现 `AuthFilter`（Interfaces 如上）
- [ ] 移植 CORS：`ALLOWED_ORIGINS` env、默认 `http://127.0.0.1:*`、`setAllowedOriginPatterns`、allowCredentials
- [ ] 验证（编译在 Task 9，此处静态核对）：白名单 7 条与原文件逐字一致；wrapper 四头均覆盖 `getHeader`/`getHeaders`；`SecurityContext` 零改动（`git diff --stat` 无该文件）
- [ ] Commit

**Review Focus #1 落在本任务。**

**依赖**：Task 1。

---

### Task 4：Feign → 本地客户端

**Files**（8 个接口改 class + 删 8 个 FallbackFactory）:
- `payment-service/.../payment/client/QuotaClient.java` + 删 `QuotaClientFallbackFactory.java`
- `interview-service/.../interview/client/ResumeClient.java`、`QuotaClient.java`、`QuestionClient.java` + 删对应 3 个 `*FallbackFactory.java`
- `resume-service/.../resume/client/QuotaClient.java` + 删 `QuotaClientFallbackFactory.java`
- `job-service/.../job/client/ResumeClient.java`、`QuotaClient.java` + 删对应 2 个 `*FallbackFactory.java`
- `question-service/.../question/client/InterviewClient.java` + 删 `InterviewClientFallbackFactory.java`
- Modify（REQUIRES_NEW 四处写路径）: 调用方 service——见 Steps 第 3 步清单

**Interfaces**:
- 接口改同名 `@Component` class，**方法签名逐一不变**；方法体改为 `@Resource` 注入目标服务 `@Service` bean 后直调（契约以目标 controller 方法为准，逐个对照签名与返回类型）。
- 调用方注入方式不变（原来是 `@Resource` Feign 代理的照旧）。

**Steps**:
- [ ] 逐个转换 8 个 client（interview 3、job 2、payment 1、question 1、resume 1）；每转一个核对方法签名与原接口一致；删除 8 个 `*FallbackFactory` 及其 import
- [ ] 删除各处 `@EnableFeignClients`（原在已删的 `*Application` 上，确认无残留）
- [ ] 在设计点名的跨边界**写**路径加 `@Transactional(propagation = Propagation.REQUIRES_NEW)`：payment→quota 发放、interview→quota 扣减、resume→quota 扣减、job→quota 扣减、quota 月度重置（只读调用不加）
- [ ] 验证：`grep -rn "FeignClient\|FallbackFactory\|EnableFeignClients" --include="*.java" src/` 无输出；8 个 client 的方法数与原文件一致（`git diff` 逐个人工核对）
- [ ] Commit

**Review Focus #2、#3 落在本任务。**

**依赖**：Task 1。

---

### Task 5：AI fail-fast + Sentinel 移除

**Files**:
- Modify: `prepdeck-backend/src/main/java/com/prepdeck/common/ai/DeepSeekClient.java`（删 `@ConditionalOnProperty` 与 import、删 `@SentinelResource`）
- Delete: `prepdeck-backend/src/main/java/com/prepdeck/common/ai/AiClientFallback.java`
- Verify: 四个注入点（`interview/.../InterviewEngine`、`resume/.../service/impl/ResumeServiceImpl`、`job/.../JobApplicationServiceImpl`、`job/.../AiCoverLetterServiceImpl`）零改动

**Steps**:
- [ ] `DeepSeekClient` 删 `@ConditionalOnProperty("spring.ai.deepseek.api-key")`（保留无默认值的 `@Value` 注入）、删 `@SentinelResource`
- [ ] 删 `AiClientFallback.java`
- [ ] 验证：`grep -rn "SentinelResource\|AiClientFallback\|NoOpAi" --include="*.java" src/` 无输出；`@Value("${spring.ai.deepseek.api-key}")` 保留；四个注入点文件在 `git diff --stat` 中不出现
- [ ] Commit

**依赖**：Task 1。

---

### Task 5.5：修复客户端 bean 名冲突（Task 5 实测发现，插入任务）

**Files**:
- Modify: 6 个客户端类加显式 bean 名——`prepdeck-backend/src/main/java/com/prepdeck/payment/client/QuotaClient.java`、`interview/client/QuotaClient.java`、`resume/client/QuotaClient.java`、`job/client/QuotaClient.java`、`interview/client/ResumeClient.java`、`job/client/ResumeClient.java`
- Verify: 其余文件在 `git diff --stat` 中不出现

**Steps**:
- [ ] 上述 6 个 `@Component` 类加显式且互不重复的 bean 名（如 `paymentQuotaClient`、`interviewQuotaClient`、`resumeQuotaClient`、`jobQuotaClient`、`interviewResumeClient`、`jobResumeClient`）
- [ ] 验证：`mvn -q clean compile` exit 0；`grep -rn '@Component("' --include="*.java" src/main/java` 中 6 个客户端 bean 名无重名
- [ ] 运行时验证：先 `mvn clean compile`（清掉 `target/classes` 里 31 个陈旧 class，否则运行期组件扫描会撞幽灵冲突），不设 `DEEPSEEK_API_KEY` 启动 → 报 `Could not resolve placeholder 'DEEPSEEK_API_KEY'`（而非 bean 冲突）；设 `DEEPSEEK_API_KEY=dummy` 启动 → `Started PrepDeckApplication`
- [ ] Commit

**依赖**：Task 4、Task 5。

---

### Task 6：MySQL 数据层代码

**Files**:
- Modify: `prepdeck-backend/src/main/java/com/prepdeck/common/.../JsonbTypeHandler.java` 与 `prepdeck-backend/src/main/java/com/prepdeck/resume/.../JsonbTypeHandler.java`（两份同名，都在单体内）
- Modify: `prepdeck-backend/src/main/java/com/prepdeck/question/.../entity/QuestionEntity.java`（删 `search_vector` 字段）
- Modify: `prepdeck-backend/src/main/resources/mapper/QuestionMapper.xml`（4 处 LIKE 改写）
- Modify: `pom.xml` 已在 Task 1 换驱动——本任务只核对

**Steps**:
- [ ] 两份 `JsonbTypeHandler`：`ps.setObject(i, json, Types.OTHER)` → `Types.VARCHAR`；类名不变（避免牵连 `@TableField(typeHandler=...)`）
- [ ] `QuestionEntity` 删 `search_vector` 字段
- [ ] `QuestionMapper.xml`：4 处 `search_vector @@ websearch_to_tsquery('simple', #{keyword})` → `AND (q.title LIKE ... OR q.content LIKE ...)`（设计 §7b）
- [ ] 验证：`grep -rn "Types.OTHER\|search_vector\|websearch_to_tsquery" src/` 无输出；mapper XML 改动数与设计一致（4 处）
- [ ] Commit

**Review Focus #5 前半落在本任务。**

**依赖**：Task 1。

---

### Task 7：init SQL MySQL 化

**Files**:
- Rewrite: `prepdeck-backend/sql/init/init-database.sql`（596 行 pg → MySQL）
- Absorb（内容并入 init SQL 后删源文件）: `prepdeck-backend/src/main/resources/db/migration/V1__create_job_tables.sql`、`prepdeck-backend/src/main/resources/db/migration/V1.0.1__add_active_interview_unique_index.sql`（Task 1 已搬到根 resources 下）

**Steps**:
- [ ] 头部 `CREATE DATABASE IF NOT EXISTS prepdeck ...; USE prepdeck;`；删 `CREATE SCHEMA`、13 个 `CREATE SEQUENCE`；表名去 `interview.` 前缀
- [ ] `JSONB`→`JSON`；`TIMESTAMP`→`DATETIME`（`DEFAULT CURRENT_TIMESTAMP` 保留；`update_time` 不加 `ON UPDATE`）；`BOOLEAN` 保留；`COMMENT ON TABLE/COLUMN`→`COMMENT '...'`；索引/唯一约束语法转换
- [ ] 吸收两个游离 migration（job 两表 + 3 索引；active-interview 部分唯一索引**不建**，按设计 §7c）
- [ ] 两个 pg 部分唯一索引均不建：`uk_user_account_fingerprint`、`uk_user_active_interview`（设计 §7c，应用层检查兜底）
- [ ] 删 `search_vector` 列、GIN 索引、触发器（设计 §7b）
- [ ] 验证：本机 `mysql -u root -p < sql/init/init-database.sql` 成功；`SHOW TABLES` 表数与原脚本一致（原 13 表 + job 2 表 = 15）；`SHOW INDEX FROM <每个有索引的表>` 索引集合与原脚本一致（减去两个部分唯一索引）
- [ ] Commit

**Review Focus #4 落在本任务。**

**依赖**：Task 1（文件位置）；SQL 内容转换独立于编译。

---

### Task 8：docker-compose 清理

**Files**:
- Modify: `prepdeck-backend/docker/docker-compose.yml`

**Steps**:
- [ ] 删 `postgres` service 与 `postgres-data` volume、删 `nacos` service；`redis` 保留；头注释更新（不再挂载 pg init 脚本）
- [ ] 验证：`grep -n "postgres\|nacos" docker/docker-compose.yml` 无输出；`docker compose config` 语法合法（docker 可用时）
- [ ] Commit

**依赖**：Task 1。

---

### Task 9：首次全量构建 + 全量验收

**Files**: 无新增；只修编译/启动期错误（预期小修：包名扫描、重复 bean、缺失配置）

**Steps**:
- [ ] `cd prepdeck-backend && mvn -q package`（无测试阶段）通过
- [ ] 修编译/启动期问题直至 `java -jar target/*.jar` 启动成功（MySQL 已按 Task 7 建库；Redis 容器在跑）
- [ ] 验收逐条跑设计文档"验收标准"：
  1. 未配 `DEEPSEEK_API_KEY` 启动失败并给出明确错误；配后启动成功
  2. `/actuator/health` 200；无 token 访问 `/api/v1/users/**` 401；登录拿 token 后 200
  3. AuthFilter 手工四项：白名单路径放行、其余 401、伪造 `X-User-Id` 无合法 token 401、登出后旧 token 立即失效
  4. 前端 `npm run dev`：登录 → 创建简历 → 开始面试主链路可用（前端零改动）
- [ ] Commit（如有修复）

**依赖**：Task 2–8 全部完成。

---

## 自审记录

- **Spec 覆盖**：设计文档 §1→Task 1；§2→Task 2；§3→Task 3；§4→Task 4；§5/§6→Task 5；§7 代码部分→Task 6、SQL 部分→Task 7；§7 前端→验收（Task 9）；§8 测试→Global Constraints + Task 1 删除。已知行为差异/留给 builder 事项随各任务处置。
- **接口与类型**：`AuthFilter` 签名、8 个 client 的"签名不变"约束、`PrepDeckApplication` 注解、4 处 REQUIRES_NEW 均已写明；Feign 契约以目标 controller 为准的对照方法已写进 Task 4 steps。
- **数据模型**：DDL 转换规则 + 两 migration 吸收 + 两索引不建 + search_vector 删除，集中在 Task 7 并有 mysql 验证命令。
- **错误处理**：401（AuthFilter）、fail fast（缺 key）、异常直冒 `GlobalExceptionHandler`（设计已知差异）均有归属。
- **验证**：每任务有可执行验证命令/静态核对；全量编译只在 Task 9（Task 1 明确说明预期编译失败）。
- **比例**：9 个任务对应设计 9 节；无新增范围。

## 执行方式（待用户选择）

- **A. subagent-driven**：每任务派 fresh implementer subagent 实现 + reviewer 审查后进入下一任务（隔离上下文，适合长任务）。
- **B. native（本会话内联）**：我在本会话逐任务实现，末尾统一 review。
