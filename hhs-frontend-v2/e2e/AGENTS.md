<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# e2e/

**Purpose**: Playwright E2E 端到端测试，测试完整的用户操作流程。

**Key Files**:
- `ai-input.spec.ts` — AI 输入流程测试 (2 symbols)
- `device-sync.spec.ts` — 设备同步流程测试 (2 symbols)
- `ocr-input.spec.ts` — OCR 录入流程测试 (3 symbols)
- `quick-input.spec.ts` — 快速录入流程测试 (2 symbols)
- `wellness.spec.ts` — 健康生活方式测试 (2 symbols)
- `fixtures/` — 测试数据和固定装置

**For AI Agents**:
- 使用 Playwright `@playwright/test` 框架
- 测试文件命名: `*.spec.ts`
- 需要后端服务运行才能执行
- 运行: `npx playwright test` (全部) / `npx playwright test e2e/xxx.spec.ts` (单个)
- 配置: `playwright.config.ts`
