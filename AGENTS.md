# AGENTS.md

PrepDeck：AI 模拟面试平台。后端 `prepdeck-backend/`：Spring Boot 3.3.5 单体（Java 21、MySQL 8 + Redis、MyBatis-Plus）。前端 `prepdeck-frontend/`：Vite + React 19 + TypeScript + Tailwind。

## 硬约束（用户既定决策，覆盖 agent 默认行为）

- 无自动化测试：不写、不跑、不补。验证 = `cd prepdeck-backend && mvn clean package` 编译通过 + 启动 + 手工验收。
- AI fail fast：未配 `DEEPSEEK_API_KEY` 启动必须失败；保持现状，禁止 NoOp/降级 bean。
- 前后端契约固定：控制器路径 `/api/v1/**` + `Result<T>` 响应体，后端改动保持不动。
- 不为既有栈已覆盖的需求新增依赖；确需新依赖是显式决策——先想清楚为什么现有栈不够，再动 pom。微服务时代的 nacos/sentinel/feign/gateway 类不回潮。
- 改动落在既有分层与命名内；职责外的重构清理不做。

## 命令

启动、环境变量、建库的唯一事实来源：`README.md`。前端脚本：`prepdeck-frontend/package.json`。后端构建门禁仅编译：`mvn clean package`。

## 后端结构（prepdeck-backend/src/main/java/com/prepdeck）

- 域：`user/ resume/ interview/ report/ question/ quota/ payment/ job/` × 8，加 `common/`（config/filter/handler/util/constant/model/enums/exception/dto/ai）。
- 结构宪章（一域一形状，硬约束），每个域长成：
  - `controller/`：只做参数校验与编排，不写业务
  - `service/`：`XxxService` 接口 + `XxxServiceImpl` **平铺**（禁止 `impl/` 子包）
  - `repository/`：数据访问接口（`BaseMapper` 子类，类名保持 `*Mapper`）——全项目唯一碰数据库的地方
  - `domain/`：实体（类名保持 `*Entity`）
  - `dto/request`、`dto/response`：入参/出参，VO 也住这里（禁止 DTO/VO 漂进其他包）
  - `client/`：调用别域的唯一入口，只准本域 service 注入
  - `config/ util/ engine/ handler/ enums/`：域内辅助，按需
  - mapper XML 跟域走：`resources/mapper/<domain>/`
- 三条执行规则：① 新域照宪章复制，形状不齐即缺陷；② 跨域数据访问禁止——service 只调对方 `client`，永不注入别域的 repository/mapper；③ DTO 不准出 `dto/` 包。项目无测试，约束执行 = 结构本身 + 本章 + 改域结构时 grep 自检（如 `grep -rn "service/impl/\|<域>\.mapper\|<域>\.entity" src/main/`），不引入 ArchUnit。
- 统一出口：接口一律返回 `common/model/Result<T>`（code=0 为成功）；失败抛 `common/exception` 下的业务异常，由 `GlobalExceptionHandler` 兜底。
- 认证链：`common/filter/AuthFilter` 校验 JWT + Redis 黑名单后，注入 `X-User-Id`/`X-User-Role`/`X-Gateway-Source`/`X-Gateway-Secret` 头；`common/util/SecurityContext` 从这些头取当前用户。新接口默认走该链路；放行路径只改 `AuthFilter` 白名单。
- client 层：原 Feign 客户端已改为各域 `client/` 包下的 `@Component` 本地类。payment/interview/resume/job 下有 4 个 `QuotaClient`、2 个 `ResumeClient` 同名类，靠显式 bean 名区分（如 `@Component("interviewQuotaClient")`）；新增客户端保持 bean 名唯一。
- 跨域写操作保留 `@Transactional(REQUIRES_NEW)`（维持"先提交后返回"语义）；只读调用不加。
- 面试 Prompt（阶段配置、规则片段、全局边界）全部在 `src/main/resources/application.yml` 的 `interview.prompt` 段；改 Prompt 改这里，不改 Java。
- ID 为 MyBatis-Plus 雪花 ID（ASSIGN_ID），跨层传递按 64 位长整数对待。

## 前端结构（prepdeck-frontend/src）

- API 层 `api/*.ts` 统一基于 `api/request.ts`（axios，baseURL `/api/v1`，vite proxy 转发 8081）；新接口加一个模块文件，不散落裸请求。
- 雪花 ID 经 `api/request.ts` 的 `json-bigint`（storeAsString）以字符串返回；新解析路径沿用，防止精度丢失。
- 状态用 zustand（`stores/`）；页面在 `pages/`；简历编辑器在 `components/editor`（dnd-kit 拖拽 + `fields/` 可编辑字段组件）。
- UI 改动的设计事实来源是 `prepdeck-frontend/DESIGN.md`（配色/字体/圆角/间距 token）；先查它再动手。

## 已知限制（代码现状，别顺手"修复"）

- 关键词搜索是 LIKE（`resources/mapper/question/QuestionMapper.xml`），大数据量全表扫描；升级路径为 FULLTEXT ngram。
- 账号指纹去重、"每人一个进行中面试"只有应用层检查，无唯一索引兜底，存在并发竞态窗口。
- `update_time` 纯应用层维护；新增写路径显式赋值。
- `common/config/ThreadPoolConfig` 是死配置（无 `AutoConfiguration.imports`，`@Async` 不生效）；保持死，除非任务就是修它。
