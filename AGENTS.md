# AGENTS.md

PrepDeck：AI 模拟面试平台。后端 `prepdeck-backend/` 是 Spring Boot 3.3.5 单体（Java 21、MySQL 8 + Redis），前端 `prepdeck-frontend/` 是 Vite + React 19。由 8 个微服务 + 网关合并而来，背景与已知行为差异见 `docs/superpowers/specs/2026-11-24-prepdeck-monolith-mysql-design.md`。

## 硬约束

- **无自动化测试**（用户决策）：不写、不跑、不补测试。验证 = `cd prepdeck-backend && mvn clean package` 编译通过 + 启动 + 手工验收。
- **AI fail fast**（用户决策）：未配 `DEEPSEEK_API_KEY` 时启动必须失败；保持现状，禁止 NoOp/降级 bean。
- 前后端契约固定：控制器路径 `/api/v1/**` + `Result<T>` 响应体，后端改动保持不动。
- 不引入新依赖（单体化既定边界）。
- 改动落在既有分层与命名内（见下）；职责外的重构清理不做。

## 命令

启动、环境变量、建库导入的单一事实来源是 `README.md`；前端脚本（dev/build/lint）在 `prepdeck-frontend/package.json`。后端构建门禁仅编译：`mvn clean package`。

## 后端结构（prepdeck-backend）

- 包布局：`com.prepdeck.<domain>`（user/resume/interview/report/question/quota/payment/job）+ `com.prepdeck.common`（config/filter/handler/util/constant/model/enums/exception/dto/ai）。
- 单域分层：`controller` → `service`（`/impl`）→ `repository` 或 `mapper`（payment/quota 用 `entity`/`mapper`）→ `domain` 或 `entity`；DTO 在 `dto/request`、`dto/response`。
- 统一出口：接口一律返回 `common/model/Result<T>`（code=0 为成功）；失败抛 `common/exception` 下的业务异常，由 `GlobalExceptionHandler` 兜底。
- 认证链：`common/filter/AuthFilter` 校验 JWT + Redis 黑名单后，注入 `X-User-Id`/`X-User-Role`/`X-Gateway-Source`/`X-Gateway-Secret` 头；`common/util/SecurityContext` 从这些头取当前用户。新接口默认走该链路；放行路径只改 `AuthFilter` 白名单。
- 原 Feign 客户端已改为各域 `client` 包下的 `@Component` 本地类。payment/interview/resume/job 下有 4 个 `QuotaClient`、2 个 `ResumeClient` 同名类，靠显式 bean 名区分（如 `@Component("interviewQuotaClient")`）；新增客户端保持 bean 名唯一。
- 跨原服务边界的写操作保留 `@Transactional(REQUIRES_NEW)`（维持"先提交后返回"语义）；只读调用不加。
- 面试 Prompt（阶段配置、规则片段、全局边界）全部在 `src/main/resources/application.yml` 的 `interview.prompt` 段；改 Prompt 改这里，不改 Java。
- ID 为 MyBatis-Plus 雪花 ID（ASSIGN_ID），跨层传递按 64 位长整数对待。

## 前端结构（prepdeck-frontend）

- API 层 `src/api/*.ts` 统一基于 `src/api/request.ts`（axios，baseURL `/api/v1`，vite proxy 转发 8081）；新接口加一个模块文件，不散落裸请求。
- 雪花 ID 经 `json-bigint` 以字符串返回（`request.ts` 的 transformResponse）；新解析路径沿用，防止精度丢失。
- 状态用 zustand（`src/stores`）；页面在 `src/pages`；简历编辑器在 `src/components/editor`（dnd-kit 拖拽 + `fields/` 可编辑字段组件）。
- UI 改动的设计事实来源是 `prepdeck-frontend/DESIGN.md`（配色/字体/圆角/间距 token）；先查它再动手。

## 已知限制（单体化遗留，别顺手"修复"）

- 关键词搜索是 LIKE（`QuestionMapper.xml`），大数据量全表扫描；升级路径为 FULLTEXT ngram。
- 两处 PG 部分唯一索引未建（账号指纹去重、"每人一个进行中面试"），仅应用层检查，存在并发竞态窗口。
- `update_time` 纯应用层维护（PG 触发器已删）；新增写路径显式赋值。
- `common/config/ThreadPoolConfig` 是死配置（无 `AutoConfiguration.imports`，`@Async` 不生效）；保持死，除非任务就是修它。

## 文档

- 启动/环境变量：`README.md`
- 单体化设计与已知行为差异：`docs/superpowers/specs/2026-11-24-prepdeck-monolith-mysql-design.md`
- 实施计划：`docs/superpowers/plans/2026-11-24-prepdeck-monolith-mysql.md`
