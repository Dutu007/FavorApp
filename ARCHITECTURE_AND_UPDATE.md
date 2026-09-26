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
| `api` | `ghcr.io/dutu007/favorapp-api:latest` | 注册、登录、匹配、评分和记录 API |
| `postgres` | `ghcr.io/dutu007/favorapp-postgres:16-alpine` | 应用数据库 |

资源限制：PostgreSQL 256 MB，API 128 MB，合计最多 384 MB。

- API 只绑定宿主机 `127.0.0.1:8080`。
- PostgreSQL 不映射宿主机端口。
- API 通过外部 Docker 网络 `zengdeming-edge` 的别名 `favorapp-api` 被网关访问。
- 数据库数据保存在 Docker volume `favorapp-postgres`。
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

数据库镜像通常不需要更新。确实需要更新时：

```bash
cd /home/dutu/workspace/favor-app
docker compose pull postgres
docker compose up -d postgres
```

不要使用 `docker compose down -v`，否则会删除数据库数据卷。

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
docker compose logs --tail=100 postgres
docker stats --no-stream
```

不要日常使用 `docker image prune -a -f` 或 `docker system prune -a --volumes`。
