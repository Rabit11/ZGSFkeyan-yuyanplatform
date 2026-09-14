# 项目申报渠道化审签流程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让项目申报按 V19.1 渠道配置执行不同的提交、审批、待办和流转图节点，保持立项备案准入规则不变。

**Architecture:** 在 `frontend/src/utils` 建立纯函数渠道流程模板，使用渠道编码生成节点序列和角色键；后端维护同等模板映射并将申报的节点模板快照存入岗位/备注兼容字段。前端流转图、Mock 接口和自动化测试均按渠道模板读取，后端负责最终状态与权限校验。

**Tech Stack:** Spring Boot/Java、Vue 3、TypeScript、Vite Mock、Node `node:test`。

## Global Constraints

- 只修改项目申报、立项备案衔接和申报待办相关代码。
- 实施、验收、经费、成果转化模块不得改变业务规则。
- 普通通用渠道沿用 V19.1 规定的 9 个申报节点（含项目联系人填写节点）。
- 在途旧数据没有模板快照时继续使用现有固定链，不能破坏历史记录。
- 所有渠道流程完成后仍通过现有 `filing-submit` 进入立项备案。

---

### Task 1: 建立前端渠道流程模板

**Files:**
- Modify: `frontend/src/utils/declareFlow.ts`
- Test: `qa/declaration-channel-flow.test.mjs`

- [ ] 增加 `channelFlowTemplate(channelCode, needApproval)`，返回节点标题、角色键、节点类型和是否结束。
- [ ] 覆盖 XX25、科技周、大飞机研究院、先进材料联盟、商飞-波音、无需审批及通用渠道。
- [ ] 让 `firstDeclareAuditNode`、`nextDeclareAuditNode`、`buildDeclareSteps` 使用模板。
- [ ] 为同名的两次学术委员会节点使用不同稳定编码。
- [ ] 先写纯函数测试，断言 XX25 无财务节点及特殊渠道节点顺序。

### Task 2: 接入后端渠道状态机和权限

**Files:**
- Modify: `backend/src/main/java/com/comac/rpm/modules/declaration/controller/DeclarationController.java`
- Modify: `backend/src/main/java/com/comac/rpm/common/permission/FlowAuditGuard.java`

- [ ] 增加按渠道编码返回节点链的后端方法，并保留旧数据固定链兜底。
- [ ] 提交时保存流程模板标识并进入模板首个审核节点。
- [ ] 审批时按当前模板推进，不再直接对固定数组取下一个节点。
- [ ] 无需审批项目走单位科技部审核后直接归档；商飞-波音不得因“报备”字样跳过审批。
- [ ] 每个模板节点映射到岗位键；缺少指定办理人时拒绝提交并返回节点名称。
- [ ] 备案准入继续只接受 `APPROVED` 或 `REPORTED`。

### Task 3: 同步 Mock 登录、待办和审批

**Files:**
- Modify: `frontend/src/mock/index.ts`
- Modify: `frontend/src/mock/data.ts`
- Modify: `frontend/src/utils/flowActor.ts`

- [ ] Mock 使用与前端相同的渠道节点序列。
- [ ] 新建、提交、审批、待办均按渠道模板运行并校验当前登录人。
- [ ] 给特殊节点提供可登录的 Mock 岗位账号，保留现有账号兼容映射。
- [ ] 新增覆盖所有渠道的 Mock 申报测试样本，测试结束恢复数据。

### Task 4: 更新申报页面和项目生命周期流转图

**Files:**
- Modify: `frontend/src/views/initiation/Declaration.vue`
- Modify: `frontend/src/views/overview/ProjectDetail.vue`
- Modify: `frontend/src/components/declare/DeclareFlowDialog.vue`

- [ ] 新建申报选择渠道后显示该渠道实际审签节点。
- [ ] 当前节点、待办文案、审批成功后的下一节点从模板生成。
- [ ] 项目详情生命周期的“项目申报”流转图使用申报保存的渠道节点，不再显示固定链。
- [ ] 保持立项备案节点和入口条件不变。

### Task 5: 回归验证和报告

**Files:**
- Create: `qa/declaration-channel-flow.test.mjs`
- Modify: `qa/mock-declaration-approval-flow.mjs`
- Modify: `docs/项目申报与立项备案V19.1符合性及自动测试报告.md`

- [ ] 自动验证全部渠道的节点顺序、指定账号待办、无关账号拒绝、驳回重提和办结归档。
- [ ] 明确 XX25 无财务节点，科技周、研究院、联盟、波音和无需审批流程均有专属断言。
- [ ] 运行 Node 测试、前端类型检查；若后端构建环境可用再运行后端测试。
- [ ] 报告记录真实覆盖范围和仍未实现的线下业务动作。
