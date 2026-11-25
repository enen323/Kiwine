# 后端包结构统一 + 结构宪章 — 设计文档

日期：2026-11-25
状态：待批准
前置：2026-11-24-prepdeck-monolith-mysql-design.md（单体化设计，端口已统一为 8081）

## 意图与为什么

单体化合并 8 个服务后，包结构沿袭了各旧服务的两种习惯：6 个域用 `<域>.repository/` + `<域>.domain/`，quota、payment 用 `mapper/` + `entity/`；service 实现分平铺与 `impl/` 两种放法；`job/client/ResumeVO.java` 是错位的 DTO（与 `resume/dto/response/ResumeVO.java` 重复）。目标是：命名零不一致，并定下一条能防止再次漂移的约束。

## 目标

- 统一为**方案 B**：所有域 `<域>.repository/` + `<域>.domain/`。类名保持 `*Mapper` / `*Entity` 不变——只挪包，不动类名（最小 diff）。
- service 实现统一平铺 `service/XxxServiceImpl.java`，取消 `impl/` 子包。
- DTO/VO 全部归 `dto/request`、`dto/response`；`client/` 只放跨域调用类。
- mapper XML 跟域走：`resources/mapper/<域>/`。
- 约束成文：AGENTS.md 增加「结构宪章」一节。

## 非目标 / 反目标

- 不动 client 层去留（6 个前 Feign 客户端的存废另案）。
- 不改业务逻辑、SQL、接口路径、事务语义、类名。
- 不引入 Repository 包装抽象、不引入 ArchUnit、不写测试（无测试是既定决策，不变）。
- 反目标：改名后编译通过但某个 XML namespace 漏改，运行时首次查询才炸；AGENTS.md 与实际结构再次脱节；顺手把 `payment` 引用 `quota.util.QuotaConstants` 这类跨域泄漏也「修」了（范围外，留待另案）。

## 约束

- Java 21 / Spring Boot 3.3.5 / MyBatis-Plus 3.5.7 单 Maven 模块；无自动化测试，验证 = `mvn clean package` + 启动 + 手工抽验。
- `PrepDeckApplication` 的 `@MapperScan(basePackages = "com.prepdeck", markerInterface = BaseMapper.class)` **保持不动**。markerInterface 与包名无关，是防止 16 个 service 接口与 AiClient 被注册为 mapper 的必要护栏（去掉它启动即 `NoUniqueBeanDefinitionException`）。
- `application.yml` 无 `mapper-locations` 配置，走 MyBatis-Plus 默认 `classpath*:/mapper/**/*.xml`——XML 移入子目录无需改配置（实施时以启动后首条查询验证）。

## 方案对比（已与用户讨论并选定 B）

| 方案 | 做法 | 成本 |
| --- | --- | --- |
| A | 全面 `<域>.mapper/` + `<域>.entity/`（MyBatis-Plus 生态默认语汇，名实最相符） | 搬 34 文件、改 53 处 import、4 个 XML namespace |
| **B（选定）** | 全面 `<域>.repository/` + `<域>.domain/`（quota/payment 跟 6 域现状） | 搬 4 文件、改 6 处 import、1 个 XML namespace |
| — | 维持两套 | 即现状，排除 |

选 B：以约 1/10 成本达到同等确定性；`repository` 命名有 Spring Data 先例（DAO 层叫 repository 是通行叫法），不属虚名；若选 A，永久成本是向每个新人解释「为什么叫 repository 里面却是 BaseMapper」。

## 具体设计

### 1. 包迁移（`git mv`，仅挪位置，类名与内容不变）

| 从 | 到 |
| --- | --- |
| `quota/mapper/UserQuotaMapper.java` | `quota/repository/UserQuotaMapper.java` |
| `payment/mapper/PaymentRecordMapper.java` | `payment/repository/PaymentRecordMapper.java` |
| `quota/entity/UserQuotaEntity.java` | `quota/domain/UserQuotaEntity.java` |
| `payment/entity/PaymentRecordEntity.java` | `payment/domain/PaymentRecordEntity.java` |
| `job/client/ResumeVO.java` | `job/dto/response/ResumeVO.java` |
| `{job,payment,quota,resume}/service/impl/*.java`（4 个） | 对应 `service/` 平铺 |

连带更新：上述域 import（quota/mapper 2 处 + payment/entity 1 处 + job ResumeVO 3 处）、`UserQuotaMapper.xml` namespace 改 `com.prepdeck.quota.repository.UserQuotaMapper`、4 个 impl 包声明。

### 2. mapper XML 跟域走

`resources/mapper/{DailyChallenge,Question,QuestionTag,UserFavorite}Mapper.xml` → `resources/mapper/question/`；`UserQuotaMapper.xml` → `resources/mapper/quota/`。

### 3. 结构宪章（写进 AGENTS.md，替换现有「后端结构」节）

```
com.prepdeck.<域>/
  controller/      # @RestController，只做参数校验与编排，不写业务
  service/         # XxxService 接口 + XxxServiceImpl，平铺（无 impl/ 子包）
  repository/      # 数据访问接口（BaseMapper 子类）——全项目唯一碰数据库的地方
  domain/          # 实体
  dto/request/     # 入参
  dto/response/    # 出参，VO 也住这里（禁止 DTO 漂进 client/ 等其他包）
  client/          # 调用别域的唯一入口，只准本域 service 注入
  config/ util/ engine/ handler/ enums/   # 域内辅助，按需
```

三条硬规则：① 一个域一套形状，新域照此复制，形状不齐即缺陷；② 跨域数据访问禁止——service 只能调对方 `client`，永不注入别域的 repository/mapper；③ DTO 不准出 `dto/` 包。执行机制：无测试（既定决策）→ 约束靠结构本身 + AGENTS.md 宪章 + 改域结构时 grep 自检，不引入 ArchUnit。

### 4. 验证

1. `mvn clean package` 编译通过。
2. 启动后逐域抽验查询路径；quota、payment 是本次唯一改 namespace 的两域，**必须**各验一条读写；其余域回归登录/简历/面试主链路。
3. `grep -rn "com.prepdeck.<域>.mapper\|com.prepdeck.<域>.entity\|/service/impl/"` 结果为空。

## 留给 builder 的事项

- 确认 MyBatis-Plus 默认 XML 通配路径确实覆盖子目录（启动日志 + 首条查询验证）。
- AGENTS.md 宪章与现有「后端结构」描述合并，不留互相矛盾的两份说明。
