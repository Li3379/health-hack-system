<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# controller/

**Purpose**: REST API 端点层，处理 HTTP 请求并委托给 service 层。所有返回 `Result<T>`。

**For AI Agents**:
- 使用 `@RestController` + `@RequestMapping("/api/xxx")`
- 注入 service 接口，不直接注入 mapper
- 参数校验: `@Valid` + `@Validated`
- 响应: `Result.success(data)` 或 `Result.error(ErrorCode.XXX)`
- 分页: 使用 `PageQuery` DTO，返回 `PageResult`
