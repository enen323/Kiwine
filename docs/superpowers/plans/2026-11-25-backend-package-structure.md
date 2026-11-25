# 后端包结构统一 + 结构宪章 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全项目域包命名统一为 `<域>.repository/` + `<域>.domain/`，service 实现平铺、DTO 归位、XML 跟域走，并把「一域一形状」结构宪章写进 AGENTS.md。

**Architecture:** 纯机械迁移：`git mv` 挪包 + 重写 package/import 声明，类名与内容不变。无自动化测试（既定决策），每个任务的验证循环 = 结构断言（grep）+ `mvn compile`，运行时冒烟集中在 Task 6。

**Tech Stack:** Java 21 / Spring Boot 3.3.5 / MyBatis-Plus 3.5.7 / Maven 单模块（prepdeck-backend）

**Spec:** `docs/superpowers/specs/2026-11-25-backend-package-structure-design.md`（方案 B 已批准；本计划是其论证）

## Global Constraints

- **无自动化测试**（用户决策，AGENTS.md 硬约束）：不写、不跑、不补测试。每个任务的验证 = 结构断言（grep 必须为空）+ `mvn compile` 通过；运行时冒烟只在 Task 6 做一次。
- `PrepDeckApplication` 的 `@MapperScan(basePackages = "com.prepdeck", markerInterface = BaseMapper.class)` **保持不动**——markerInterface 与包名无关，是防止 16 个 service 接口与 AiClient 被注册为 mapper 的必要护栏。
- `application.yml` 无 `mapper-locations`，走 MyBatis-Plus 默认 `classpath*:/mapper/**/*.xml`；XML 移入子目录不改配置，Task 6 以启动后实际查询验证通配覆盖。
- 所有移动用 `git mv`（保历史）；类名不变、类内容除 package/import 声明外不变。
- 编译门禁命令：`cd prepdeck-backend && mvn clean package`（任务内用 `mvn compile` 快速迭代，Task 6 跑全量）。
- `UserQuotaMapper.xml` 的 namespace 必须与新 FQN 一致（`com.prepdeck.quota.repository.UserQuotaMapper`）；question 的 4 个 XML namespace 本就是 `com.prepdeck.question.repository.*`，只挪位置不改内容。
- 范围外（不做，见 spec 非目标）：client 层去留、payment 引用 `quota.util.QuotaConstants` 的跨域泄漏、业务逻辑/SQL/接口路径。

## Review Focus

1. **XML namespace 改写漏失或错拼**（`UserQuotaMapper.xml`）→ 编译不报，运行时调 `increment*` 自定义语句时报 `BindingException`。钉法：Task 6 冒烟 POST `/api/v1/quota/consume` 必须非 500。
2. **MyBatis-Plus 默认 XML 通配不覆盖 `mapper/question/` 子目录** → 启动不报，运行时 question 查询 500。钉法：Task 6 冒烟 GET `/api/v1/questions` 必须非 500。
3. **有人把 `@MapperScan` 的 `markerInterface` 当"统一后无用"顺手删** → 启动即 `NoUniqueBeanDefinitionException`。钉法：Task 6 启动成功即为反证；本计划任何任务不改 `PrepDeckApplication.java`。
4. **flatten 后残留空 `service/impl/` 目录** → 结构违宪（git 不管空目录，下一个人建同名包会混）。钉法：Task 1–3 每任务 grep 后 `find` 确认无 `impl` 目录。
5. **AGENTS.md 宪章与实际结构脱节**（写了宪章但漏改「后端结构」旧描述，留下互相矛盾的两份）。钉法：Task 5 通读改后全文，旧「repository 或 mapper」措辞必须清零。

## File Structure

- 移动（不改类名）：quota `mapper/UserQuotaMapper.java`→`repository/`、`entity/UserQuotaEntity.java`→`domain/`、`service/impl/QuotaServiceImpl.java`→`service/` 平铺；payment 同构 3 个；job `client/ResumeVO.java`→`dto/response/`、2 个 impl→`service/` 平铺；resume 1 个 impl→`service/` 平铺。
- 移动（XML）：`resources/mapper/UserQuotaMapper.xml`→`resources/mapper/quota/`；4 个 question XML→`resources/mapper/question/`。
- 重写声明的文件：上述 8 个 java（package + 少量 import）。
- 修改文档：`AGENTS.md`（后端结构节换宪章）。
- 不碰：`PrepDeckApplication.java`、任何业务逻辑、`application.yml`。

---

### Task 1: quota + payment 域迁移（mapper/entity → repository/domain，impl 平铺）

**Files:**
- Move: `prepdeck-backend/src/main/java/com/prepdeck/quota/mapper/UserQuotaMapper.java` → `.../quota/repository/UserQuotaMapper.java`
- Move: `.../quota/entity/UserQuotaEntity.java` → `.../quota/domain/UserQuotaEntity.java`
- Move: `.../quota/service/impl/QuotaServiceImpl.java` → `.../quota/service/QuotaServiceImpl.java`
- Move: `.../payment/mapper/PaymentRecordMapper.java` → `.../payment/repository/PaymentRecordMapper.java`
- Move: `.../payment/entity/PaymentRecordEntity.java` → `.../payment/domain/PaymentRecordEntity.java`
- Move: `.../payment/service/impl/PaymentServiceImpl.java` → `.../payment/service/PaymentServiceImpl.java`
- Move: `prepdeck-backend/src/main/resources/mapper/UserQuotaMapper.xml` → `prepdeck-backend/src/main/resources/mapper/quota/UserQuotaMapper.xml`
- Modify: 上述 6 个 java 的 package 声明 + 3 处 import + XML namespace 1 处

**Interfaces:**
- Produces: 迁移后的包路径——`com.prepdeck.quota.repository.UserQuotaMapper`、`com.prepdeck.quota.domain.UserQuotaEntity`、`com.prepdeck.quota.service.QuotaServiceImpl`、`com.prepdeck.payment.repository.PaymentRecordMapper`、`com.prepdeck.payment.domain.PaymentRecordEntity`、`com.prepdeck.payment.service.PaymentServiceImpl`（后续任务与新代码按此引用）。

- [ ] **Step 1: git mv 全部 7 个文件**

```bash
cd prepdeck-backend/src/main
git mv java/com/prepdeck/quota/mapper/UserQuotaMapper.java java/com/prepdeck/quota/repository/UserQuotaMapper.java
git mv java/com/prepdeck/quota/entity/UserQuotaEntity.java java/com/prepdeck/quota/domain/UserQuotaEntity.java
git mv java/com/prepdeck/quota/service/impl/QuotaServiceImpl.java java/com/prepdeck/quota/service/QuotaServiceImpl.java
git mv java/com/prepdeck/payment/mapper/PaymentRecordMapper.java java/com/prepdeck/payment/repository/PaymentRecordMapper.java
git mv java/com/prepdeck/payment/entity/PaymentRecordEntity.java java/com/prepdeck/payment/domain/PaymentRecordEntity.java
git mv java/com/prepdeck/payment/service/impl/PaymentServiceImpl.java java/com/prepdeck/payment/service/PaymentServiceImpl.java
git mv resources/mapper/UserQuotaMapper.xml resources/mapper/quota/UserQuotaMapper.xml
```

Expected: 7 条 `Renaming ...`；`java/com/prepdeck/quota/mapper/`、`quota/entity/`、`payment/mapper/`、`payment/entity/`、两处 `service/impl/` 均不存在。

- [ ] **Step 2: 改 4 个 package 声明 + 1 个 XML namespace**

逐文件精确替换（每文件只改声明行，其余不动）：
- `quota/repository/UserQuotaMapper.java:1` `package com.prepdeck.quota.mapper;` → `package com.prepdeck.quota.repository;`
- `quota/domain/UserQuotaEntity.java:1` `package com.prepdeck.quota.entity;` → `package com.prepdeck.quota.domain;`
- `quota/service/QuotaServiceImpl.java:1` `package com.prepdeck.quota.service.impl;` → `package com.prepdeck.quota.service;`
- `payment/repository/PaymentRecordMapper.java:1` `package com.prepdeck.payment.mapper;` → `package com.prepdeck.payment.repository;`
- `payment/domain/PaymentRecordEntity.java:1` `package com.prepdeck.payment.entity;` → `package com.prepdeck.payment.domain;`
- `payment/service/PaymentServiceImpl.java:1` `package com.prepdeck.payment.service.impl;` → `package com.prepdeck.payment.service;`
- `resources/mapper/quota/UserQuotaMapper.xml:4` `com.prepdeck.quota.mapper.UserQuotaMapper` → `com.prepdeck.quota.repository.UserQuotaMapper`

- [ ] **Step 3: 改 3 处 import**

- `quota/service/QuotaServiceImpl.java:9` `import com.prepdeck.quota.mapper.UserQuotaMapper;` → `import com.prepdeck.quota.repository.UserQuotaMapper;`
- `payment/service/PaymentServiceImpl.java:8` `import com.prepdeck.payment.entity.PaymentRecordEntity;` → `import com.prepdeck.payment.domain.PaymentRecordEntity;`

Expected: `grep -rn "com.prepdeck.quota.mapper\|com.prepdeck.quota.entity\|com.prepdeck.payment.mapper\|com.prepdeck.payment.entity" prepdeck-backend/src/main/` 无输出。

- [ ] **Step 4: 编译**

Run: `cd prepdeck-backend && mvn compile`
Expected: BUILD SUCCESS（引用 quota/payment 旧包的 interview/resume/job 域若有漏改会在此暴露）。

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: quota/payment 统一 repository/domain 命名，service 实现平铺"
```

### Task 2: job 域迁移（ResumeVO 归位 + impl 平铺）

**Files:**
- Move: `prepdeck-backend/src/main/java/com/prepdeck/job/client/ResumeVO.java` → `.../job/dto/response/ResumeVO.java`
- Move: `.../job/service/impl/JobApplicationServiceImpl.java` → `.../job/service/JobApplicationServiceImpl.java`
- Move: `.../job/service/impl/AiCoverLetterServiceImpl.java` → `.../job/service/AiCoverLetterServiceImpl.java`
- Modify: 上述 3 个 java 的 package 声明 + 2 处 import

**Interfaces:**
- Produces: `com.prepdeck.job.dto.response.ResumeVO`（替代原 `job.client.ResumeVO`；`resume.dto.response.ResumeVO` 是另一个已存在的类，勿混）；`com.prepdeck.job.service.JobApplicationServiceImpl`、`com.prepdeck.job.service.AiCoverLetterServiceImpl`。

- [ ] **Step 1: git mv 3 个文件**

```bash
cd prepdeck-backend/src/main
git mv java/com/prepdeck/job/client/ResumeVO.java java/com/prepdeck/job/dto/response/ResumeVO.java
git mv java/com/prepdeck/job/service/impl/JobApplicationServiceImpl.java java/com/prepdeck/job/service/JobApplicationServiceImpl.java
git mv java/com/prepdeck/job/service/impl/AiCoverLetterServiceImpl.java java/com/prepdeck/job/service/AiCoverLetterServiceImpl.java
```

Expected: 3 条 `Renaming`；`job/service/impl/` 不存在。

- [ ] **Step 2: 改 3 个 package 声明 + 2 处 import**

- `job/dto/response/ResumeVO.java:1` `package com.prepdeck.job.client;` → `package com.prepdeck.job.dto.response;`
- `job/service/JobApplicationServiceImpl.java:1` `package com.prepdeck.job.service.impl;` → `package com.prepdeck.job.service;`；同文件 `import com.prepdeck.job.client.ResumeVO;` → `import com.prepdeck.job.dto.response.ResumeVO;`
- `job/service/AiCoverLetterServiceImpl.java:1` 同样平铺 package；同文件 `import com.prepdeck.job.client.ResumeVO;` → `import com.prepdeck.job.dto.response.ResumeVO;`

- [ ] **Step 3: 结构断言**

Run: `grep -rn "job\.client\.ResumeVO\|job\.service\.impl" prepdeck-backend/src/main/ && find prepdeck-backend/src/main/java -type d -name impl`
Expected: 均无输出。

- [ ] **Step 4: 编译**

Run: `cd prepdeck-backend && mvn compile`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: job 域 ResumeVO 归位 dto/response，service 实现平铺"
```

### Task 3: resume 域 impl 平铺

**Files:**
- Move: `prepdeck-backend/src/main/java/com/prepdeck/resume/service/impl/ResumeServiceImpl.java` → `.../resume/service/ResumeServiceImpl.java`
- Modify: package 声明 1 处

- [ ] **Step 1: git mv + 改 package 声明**

```bash
cd prepdeck-backend/src/main
git mv java/com/prepdeck/resume/service/impl/ResumeServiceImpl.java java/com/prepdeck/resume/service/ResumeServiceImpl.java
```

`resume/service/ResumeServiceImpl.java:1`：`package com.prepdeck.resume.service.impl;` → `package com.prepdeck.resume.service;`

- [ ] **Step 2: 结构断言**

Run: `grep -rn "resume\.service\.impl" prepdeck-backend/src/main/ && find prepdeck-backend/src/main/java -type d -name impl`
Expected: 均无输出（全项目 impl 目录清零）。

- [ ] **Step 3: 编译 + Commit**

Run: `cd prepdeck-backend && mvn compile` → Expected: BUILD SUCCESS。

```bash
git add -A && git commit -m "refactor: resume 域 service 实现平铺，取消 impl 子包"
```

### Task 4: question 域 mapper XML 跟域走

**Files:**
- Move: `prepdeck-backend/src/main/resources/mapper/{DailyChallenge,Question,QuestionTag,UserFavorite}Mapper.xml` → `prepdeck-backend/src/main/resources/mapper/question/`（4 个）

- [ ] **Step 1: git mv 4 个 XML（内容不动）**

```bash
cd prepdeck-backend/src/main
mkdir -p resources/mapper/question
git mv resources/mapper/DailyChallengeMapper.xml resources/mapper/question/
git mv resources/mapper/QuestionMapper.xml resources/mapper/question/
git mv resources/mapper/QuestionTagMapper.xml resources/mapper/question/
git mv resources/mapper/UserFavoriteMapper.xml resources/mapper/question/
```

Expected: 4 条 `Renaming`；`grep -c "com.prepdeck.question.repository" resources/mapper/question/*.xml` 每个文件 ≥1（namespace 原样未动）。

- [ ] **Step 2: 结构断言 + 编译 + Commit**

Run: `ls prepdeck-backend/src/main/resources/mapper/` → Expected: 只含 `question/` 与 `quota/` 两个目录。

Run: `cd prepdeck-backend && mvn compile` → Expected: BUILD SUCCESS。

```bash
git add -A && git commit -m "refactor: question 域 mapper XML 移入 resources/mapper/question/"
```

### Task 5: AGENTS.md 结构宪章

**Files:**
- Modify: `AGENTS.md`（「后端结构」节整节替换）

- [ ] **Step 1: 替换「后端结构」节**

将 `## 后端结构（prepdeck-backend）` 下的「包布局」「单域分层」两条替换为以下宪章（认证链、client bean 名、事务、Prompt、雪花 ID 等其余条目原样保留）：

```markdown
- 包布局：`com.prepdeck.<domain>`（user/resume/interview/report/question/quota/payment/job）+ `com.prepdeck.common`（config/filter/handler/util/constant/model/enums/exception/dto/ai）。
- **结构宪章（一域一形状，硬约束）**：每个域必须长成——
  - `controller/`：只做参数校验与编排，不写业务
  - `service/`：`XxxService` 接口 + `XxxServiceImpl` **平铺**（禁止 `impl/` 子包）
  - `repository/`：数据访问接口（`BaseMapper` 子类，类名保持 `*Mapper`）——全项目唯一碰数据库的地方
  - `domain/`：实体（类名保持 `*Entity`）
  - `dto/request`、`dto/response`：入参/出参，VO 也住这里（禁止 DTO/VO 漂进 `client/` 等其他包）
  - `client/`：调用别域的唯一入口，只准本域 service 注入
  - `config/ util/ engine/ handler/ enums/`：域内辅助，按需
  - mapper XML 跟域走：`resources/mapper/<domain>/`
- 三条执行规则：① 一个域一套形状，新域照此复制，形状不齐即缺陷；② 跨域数据访问禁止——service 只能调对方 `client`，永不注入别域的 repository/mapper；③ DTO 不准出 `dto/` 包。项目无测试（既定决策），约束执行 = 结构本身 + 本章 + 改域结构时 grep 自检（如 `grep -rn "service/impl/\|<域>.mapper\|<域>.entity" src/main/`），不引入 ArchUnit。
```

- [ ] **Step 2: 断言 + Commit**

Run: `grep -n "repository 或 mapper\|service（\`/impl\`）" AGENTS.md` → Expected: 无输出（旧措辞清零）。

```bash
git add AGENTS.md && git commit -m "docs: AGENTS.md 写入一域一形状结构宪章"
```

### Task 6: 全量验证（编译门禁 + 结构断言 + 启动冒烟）

**Files:** 无新增/修改（只验证）

- [ ] **Step 1: 编译门禁**

Run: `cd prepdeck-backend && mvn clean package`
Expected: BUILD SUCCESS。

- [ ] **Step 2: 全项目结构断言**

Run:
```bash
cd prepdeck-backend
grep -rn "com.prepdeck.\(quota\|payment\)\.\(mapper\|entity\)\|\.service\.impl;\|service/impl/" src/main/ ; find src/main/java -type d -name impl ; ls src/main/resources/mapper/
```
Expected: 前两条无输出；第三条只含 `question/`、`quota/`。

- [ ] **Step 3: 启动 + 运行时冒烟（钉 Review Focus 1–3）**

Run: 按 `README.md` 启动（MySQL/Redis 就绪，`DEEPSEEK_API_KEY` 已配），然后：
```bash
curl -s -X POST localhost:8081/api/v1/auth/login -H 'Content-Type: application/json' -d '<按 README 的登录请求体>'   # 拿 token
curl -s localhost:8081/api/v1/quota -H "Authorization: Bearer <token>"
curl -s -X POST "localhost:8081/api/v1/quota/consume?serviceType=interview" -H "Authorization: Bearer <token>"
curl -s localhost:8081/api/v1/questions -H "Authorization: Bearer <token>"
```
Expected: 启动日志有 `Started PrepDeckApplication`；GET `/api/v1/quota` 返回 quota 数据（非 500）→ 证明 quota repository/domain 包迁移无误；POST `/consume` 非 500 → 证明 `UserQuotaMapper.xml` namespace 改写正确、`incrementInterviewFreeUsed` 自定义语句绑定成功；GET `/api/v1/questions` 非 500 → 证明 `resources/mapper/question/` 子目录被默认 `classpath*:/mapper/**/*.xml` 覆盖。

- [ ] **Step 4: Commit（若有文档/收尾改动）**

若 Task 6 无代码改动则跳过；否则 `git add -A && git commit -m "chore: 包结构统一收尾"`。
