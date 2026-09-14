# 项目申报审批待办提醒可靠性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让项目申报审批待办按人准确查询、自动刷新，并在历史数据和其他模块异常时保持可靠。

**Architecture:** 后端新增当前用户申报待办查询接口，集中处理节点与岗位快照匹配；前端改用该接口并定时刷新，其他待办模块独立容错；Mock 和回归测试复现跨用户、历史快照及异常场景。

**Tech Stack:** Spring Boot/MyBatis-Plus、Vue 3、Pinia、TypeScript、Node test。

## Global Constraints

- 不改变现有申报审批状态机和权限校验语义。
- 保留旧接口兼容已有页面。
- 历史岗位快照从 `remark` 读取，不强制在线迁移数据。

### Task 1: 后端申报待办接口与岗位兼容

**Files:**
- Modify: `backend/src/main/java/com/comac/rpm/modules/declaration/controller/DeclarationController.java`
- Modify: `backend/src/main/java/com/comac/rpm/common/permission/FlowAuditGuard.java`
- Modify: `frontend/src/api/modules.ts`

- [ ] 增加 `GET /api/declarations/pending`，仅查询 `SUBMITTED/APPROVING`，按当前用户匹配岗位表、remark 快照、申请人和单位范围，返回完整列表。
- [ ] 统一“单位分管领导”匹配 `unitTechDirector`、`unitTechSupervisor`。
- [ ] 保持详情和原分页接口不变。

### Task 2: 前端待办刷新与模块独立容错

**Files:**
- Modify: `frontend/src/stores/pending.ts`
- Modify: `frontend/src/layouts/BasicLayout.vue`
- Modify: `frontend/src/views/initiation/Declaration.vue`

- [ ] 使用 pending 接口替换 `size: 200` 全量拉取和本地过滤。
- [ ] 将财务待办加载包裹在独立 try/catch，失败只清空财务状态。
- [ ] BasicLayout 在登录后启动 30 秒定时刷新，身份变化时重建，卸载时清理。
- [ ] 申报页待我审核列表复用 pending 接口。

### Task 3: Mock 与回归测试

**Files:**
- Modify: `frontend/src/mock/index.ts`
- Create/Modify: `qa/declaration-pending.test.mjs`

- [ ] Mock 增加 `/declarations/pending`，按当前登录用户、节点和岗位返回待办。
- [ ] 覆盖历史 remark、分管领导双岗位、超过 200 条和财务接口失败场景。
- [ ] 运行现有回归测试和新增测试。

### Task 4: 修改报告

**Files:**
- Create: `docs/项目申报审批待办提醒修复报告.md`

- [ ] 记录问题、修改文件、修复方式、测试命令和结果。
- [ ] 复制报告到 `/Users/yangzihao/Desktop/项目申报审批待办提醒修复报告.md`。
