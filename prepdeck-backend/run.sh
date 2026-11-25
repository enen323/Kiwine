#!/usr/bin/env bash
# 免系统环境变量启动：把 .env 导出为进程环境变量后启动。
# JWT_SECRET / GATEWAY_SECRET / ALLOWED_ORIGINS 由代码经 System.getenv() 读取，
# 只有导出进真实进程环境才能生效，故用本脚本而非 Spring 的 config.import。
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "未找到 .env：请复制 .env.example 为 .env 并填写必填项" >&2
  exit 1
fi

# 逐行导出 .env；跳过注释与空行；进程已有的环境变量不覆盖（dotenv 惯例）
while IFS= read -r line; do
  case "$line" in ''|\#*) continue ;; esac
  key=${line%%=*}
  [ -n "${!key+x}" ] && continue
  export "$key=${line#*=}"
done < .env

exec java -jar target/prepdeck-1.0.0-SNAPSHOT.jar "$@"
