<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# .github/

GitHub Actions CI/CD 工作流配置。

## Key Files (under `workflows/`)

- `backend-ci.yml` — 后端 CI: 2-job 流水线 (单元测试 + Testcontainers 集成测试)
- `frontend-ci.yml` — 前端 CI: 单 job (build + lint + test)
- `docker-publish.yml` — Docker 镜像构建和推送
- `deploy.yml` — SSH 自动部署到生产服务器

## For AI Agents

- CI 触发条件: 任何分支推送，修改对应目录
- 后端 CI 需要 Docker (Testcontainers)
- 修改 CI 配置后需验证 YAML 语法
