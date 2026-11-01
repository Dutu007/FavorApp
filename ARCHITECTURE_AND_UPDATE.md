# FavorApp 当前架构与更新方法

## 当前架构

```text
Android App
    | HTTPS 443
    v
api.zengdeming.cn -> zengdeming-gateway -> favorapp-api:8080
                                      |
                                      v
                              PostgreSQL:5432
```

服务器目录：`/home/dutu/workspace/favor-app`

| 服务 | 镜像 | 作用 |
| --- | --- | --- |
| `api` | `ghcr.io/dutu007/favorapp-api:latest` | 注册、登录、匹配、评分、记录和礼物奖励 API |
| 独立基础设施 `shared-postgres` | `shared-postgres:16.15` | Favor 与网站共用的 PostgreSQL 实例 |

资源限制：API 128 MB；共享 PostgreSQL 256 MB，由 `/home/dutu/workspace/infra/postgres` 单独管理。

- API 只绑定宿主机 `127.0.0.1:8080`。
- PostgreSQL 当前仅在内部网络 `shared-db` 上提供连接，没有发布宿主机端口。
- API 通过外部 Docker 网络 `zengdeming-edge` 的别名 `favorapp-api` 被网关访问。
- API 通过 `shared-db` 连接 `shared-postgres:5432/favorapp`，使用普通账号 `favorapp_app`。
- 数据库数据保存在独立的 Docker volume `shared-postgres-data`；网站使用另一数据库 `mywebsites`，两个应用账号不能访问对方数据库。
- `.env` 和 `certs/` 只保存在服务器，不提交到 GitHub。

## 更新 API

本地修改后推送：

```powershell
cd D:\MyWorkSpace\Codex\FavorApp
git add backend .github
git commit -m "描述修改内容"
git push origin main
```

确认 GitHub Actions 成功后，在服务器执行：

```bash
cd /home/dutu/workspace/favor-app
docker compose pull api
docker compose up -d --force-recreate api
```

检查：

```bash
docker compose ps
curl http://127.0.0.1:8080/api/v1/health
curl https://api.zengdeming.cn/api/v1/health
```

## 更新 APP

推送包含 `app/**` 改动的提交后会自动完成发布，不需要手动传 APK：

1. GitHub Actions 构建签名的 release APK（版本号 = `1.0.<构建编号>`）
2. 工作流把 `favorapp.apk` 和 `latest.json` 上传到服务器 `favor-app/apks/`
3. API 通过 `/api/v1/app/latest` 对外公布版本，`/api/v1/app/apk` 提供下载
4. 两台手机打开 App 时会自动检查更新，弹窗提示后可直接在 App 内下载并安装

GitHub 仓库需要配置以下 Actions secrets（Settings → Secrets and variables → Actions）：

| Secret | 说明 |
| --- | --- |
| `FAVORAPP_KEYSTORE_BASE64` | release 密钥库的 base64 |
| `FAVORAPP_STORE_PASSWORD` | 密钥库口令 |
| `FAVORAPP_KEY_ALIAS` | 密钥别名（`favorapp`） |
| `FAVORAPP_KEY_PASSWORD` | 密钥口令 |
| `SERVER_HOST` | 服务器地址 |
| `SERVER_USER` | SSH 用户 |
| `SERVER_SSH_KEY` | 部署用 SSH 私钥（对应公钥写入服务器 `authorized_keys`） |

`keystore/` 目录和口令只保存在本地，不要提交。密钥库丢失后已安装的 App 将无法覆盖更新。

服务器上的 `compose.yaml` 需要把 `favor-app/apks` 挂载给 API 容器（只读）：

```yaml
    volumes:
      - ./apks:/data/apks:ro
```

## 更新 Compose 配置

```powershell
scp "D:\MyWorkSpace\Codex\FavorApp\compose.yaml" dutu@zengdeming.cn:/home/dutu/workspace/favor-app/compose.yaml
```

服务器执行：

```bash
cd /home/dutu/workspace/favor-app
docker compose up -d
```

## 更新数据库镜像

Favor 的 Compose 只管理 API，没有 `postgres` 服务。应用更新时不需要更新数据库镜像。
数据库状态与日志应在独立基础设施目录查看：

```bash
cd /home/dutu/workspace/infra/postgres
docker compose ps
docker compose logs --tail=100 postgres
```

当前数据库使用本地标签 `shared-postgres:16.15` 和 `pull_policy: never`，不能靠 `pull postgres`
更新。升级需先备份并验证兼容性，再准备新镜像、修改独立基础设施的配置；它会同时影响两个应用。

## 使用 Navicat 远程连接数据库

当前没有可转发的宿主机 `127.0.0.1:5432` 端口。先在服务器获取数据库容器在共享网络上的 IP：

```bash
docker inspect shared-postgres \
  --format '{{with index .NetworkSettings.Networks "shared-db"}}{{.IPAddress}}{{end}}'
```

将下面的 `数据库容器IP` 替换为上一步结果，在本地 PowerShell 建立 SSH 隧道：

```powershell
ssh -N -L 127.0.0.1:15432:数据库容器IP:5432 dutu@zengdeming.cn
```

这个窗口需要保持打开。数据库容器重建后 IP 可能变化，需要重新获取并建立隧道。
Navicat 新建 PostgreSQL 连接时填写：

```text
主机：127.0.0.1
端口：15432
数据库：服务器 .env 中的 POSTGRES_DB
用户名：服务器 .env 中的 POSTGRES_USER
密码：服务器 .env 中的 POSTGRES_PASSWORD
```

Navicat 连接成功后，SSH 窗口不能关闭。完成管理后按 `Ctrl+C` 关闭隧道即可。

## 回滚 API

将服务器 `.env` 中的 `IMAGE_TAG` 改为 GitHub Actions 发布的提交 SHA，然后执行：

```bash
cd /home/dutu/workspace/favor-app
docker compose pull api
docker compose up -d --force-recreate api
```

## 清理和排查

```bash
docker system df
docker image prune -f
docker compose ps
docker compose logs --tail=100 api
docker logs --tail=100 shared-postgres
docker stats --no-stream
```

不要日常使用 `docker image prune -a -f` 或 `docker system prune -a --volumes`。
