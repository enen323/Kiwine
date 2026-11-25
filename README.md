# PrepDeck

PrepDeck 是一个 Spring Boot 3.3.5 单体应用（由原 8 个微服务 + gateway 合并而来），Java 21，数据层为 MySQL 8 + Redis。提供 AI 模拟面试、简历、岗位等核心能力，前端 Vite 工程位于 `prepdeck-frontend/`。

## 环境变量

变量名与默认值均来自 `prepdeck-backend/src/main/resources/application.yml`（`${变量名:默认值}` 形式），未列出的变量均无可调默认值：

| 变量 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `DEEPSEEK_API_KEY` | 是 | 无 | DeepSeek API 密钥，缺失时启动即失败（fail fast） |
| `JWT_SECRET` | 是 | 无 | JWT 签名密钥，缺失时启动校验失败，认证全挂 |
| `DB_HOST` | 否 | `localhost` | MySQL 主机 |
| `DB_NAME` | 否 | `prepdeck` | 数据库名 |
| `DB_USERNAME` | 否 | `root` | MySQL 用户名 |
| `DB_PASSWORD` | 否 | `root` | MySQL 密码 |
| `REDIS_HOST` | 否 | `localhost` | Redis 主机 |
| `REDIS_PORT` | 否 | `6379` | Redis 端口 |
| `LOG_LEVEL` | 否 | `DEBUG` | 日志级别（仅作用于 `com.prepdeck` 包） |

## 本地启动

1. 建库并导入 schema：

   ```bash
   mysql -u root -p < prepdeck-backend/sql/init/init-database.sql
   ```

2. 启动 Redis（MySQL 使用宿主机实例，不在此 compose 内）：

   ```bash
   cd prepdeck-backend/docker && docker compose up -d
   ```

3. 构建：

   ```bash
   cd prepdeck-backend && mvn clean package
   ```

4. 运行（两个必填变量缺一不可）：

   ```bash
   DEEPSEEK_API_KEY=... JWT_SECRET=... java -jar target/prepdeck-1.0.0-SNAPSHOT.jar
   ```

服务端口 8080，健康检查：`curl localhost:8080/actuator/health`。

## 注意事项

- 本机 MySQL root 密码若非 `root`，请设置 `DB_PASSWORD` 覆盖默认值。
- 单库 20 连接（Hikari）是单体容量上限，原微服务合计约 96。
- 前端：`cd prepdeck-frontend && npm install && npm run dev`（vite 代理 `/api` → `localhost:8080`）。
- 本项目按用户决策不含自动化测试，验证方式为编译 + 启动 + 手工验收。
