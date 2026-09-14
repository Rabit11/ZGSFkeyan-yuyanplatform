# 项目申报与立项备案 V19.1 对照及 Mock 流转测试 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 形成项目申报、立项备案的 V19.1 符合性结论，并提供覆盖全部 Mock 申报及全部现有审批节点的自动提醒测试。

**Architecture:** 需求检查采用文档条款与前后端、Mock 代码的证据对照；自动测试通过 esbuild 在 Node 中加载真实 Mock 路由，使用内存 localStorage 模拟账号切换，按实际接口完成提交、待办查询和审批流转。

**Tech Stack:** Node.js test、esbuild、TypeScript/Vue Mock、Markdown。

## Global Constraints

- 只处理项目申报、立项备案。
- 不修改其他业务模块。
- 测试结束后恢复 Mock 内存数据。
- 截图文件不可用时，以 V19.1 文档第 762-800 行及已确认的 9 节点示例为准。

---

### Task 1: 全项目申报待办流转脚本

**Files:**
- Create: `qa/mock-declaration-approval-flow.mjs`

**Interfaces:**
- Consumes: `mockRequest`, `declarations`, `materials`, `users`, `channels`
- Produces: 可直接执行的全申报审批提醒测试及控制台逐节点结果

- [ ] 使用 esbuild 加载真实 Mock 路由和数据。
- [ ] 为每条申报补齐必传材料并调用提交接口。
- [ ] 逐节点登录指定账号，验证待办命中和无关账号隔离。
- [ ] 调用审核接口并断言下一节点，最终断言审批归档。
- [ ] 在 `finally` 中恢复申报、材料和登录状态。
- [ ] 执行 `node qa/mock-declaration-approval-flow.mjs`，预期全部通过。

### Task 2: V19.1 符合性报告

**Files:**
- Create: `docs/项目申报与立项备案V19.1符合性及自动测试报告.md`

**Interfaces:**
- Consumes: V19.1 第 279-285、696-800 行及申报/备案实现
- Produces: 逐项结论、风险说明、自动测试覆盖和执行方法

- [ ] 对照渠道材料表与代码渠道配置。
- [ ] 对照各渠道审签链、无需审批报备、转办和通用规则。
- [ ] 对照立项备案准入、材料、总部审核及归档进入实施阶段。
- [ ] 记录 Mock 覆盖边界和测试结果。

### Task 3: 回归验证

**Files:**
- Test: `qa/mock-declaration-approval-flow.mjs`
- Test: `qa/declaration-pending.test.mjs`
- Test: `qa/filing-page-regression.mjs`

**Interfaces:**
- Consumes: 新增脚本和现有申报/备案回归测试
- Produces: 可复现的通过/失败结果

- [ ] 运行 `node qa/mock-declaration-approval-flow.mjs`。
- [ ] 运行 `node --test qa/declaration-pending.test.mjs qa/filing-page-regression.mjs`。
- [ ] 运行 `npm run typecheck --prefix frontend`。
