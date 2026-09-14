# 成果转化分支集成实施方案

> 执行时使用 executing-plans 技能，按任务逐项验证。用户已授权执行线上更新；代码在独立集成工作区实现。

**目标：** 将 `origin/feat/achievement-transformation` 的成果转化功能集成到当前平台，保留导入项目补录及既有入口、权限约束。

**架构：** 沿用现有 Vue / Spring Boot / MySQL 架构，成果转化通过成果包关联正式交付物。历史导入项目补录继续独立保存审核快照；不自动生成正式成果包或重复交付物。

## 1. 已核对来源

- 来源仓库：`https://github.com/Rabit11/ZGSFkeyan-yuyanplatform.git`。
- 分支头：`1b9e664`；功能提交：`05c926f feat(transform): complete achievement conversion workflow`。
- 与当前平台共同基点：`48018fd0c5160cb697c80a63068fc0bc50e0300e`。
- 共同基点至来源分支共 13 个文件变化，约 930 行新增、564 行删除。
- 当前分支 `feature/import-project-supplement` 有未提交的补录代码及其他用户文件，必须先隔离保存。成果转化改动文件与当前已跟踪修改文件没有直接路径重叠，但实体、台账统计及附件服务存在运行时依赖。

## 2. 引入内容与文件

| 内容 | 文件 |
|---|---|
| 成果包、交付物绑定、提交审核备案、版本控制 | `backend/src/main/java/com/comac/rpm/modules/transform/controller/TransformController.java` |
| 新字段及数据库升级 | `backend/src/main/java/com/comac/rpm/modules/transform/entity/AchvTransform.java`、`backend/src/main/resources/db/migration_transform_v1.sql`、`backend/src/main/resources/db/schema.sql` |
| 成果转化页面、接口及状态工具 | `frontend/src/views/transform/Transform.vue`、`frontend/src/api/transform.ts`、`frontend/src/utils/transformPackage.ts`、`frontend/src/utils/transformFlow.ts` |
| Mock 与回归 | `frontend/src/mock/data.ts`、`frontend/src/mock/index.ts`、`frontend/src/mock/transform.ts`、`qa/transform-regression.mjs` |
| 来源设计说明 | `docs/成果转化模块设计与合并说明.md` |

功能包括型号/市场两条转化路径、已交付交付物组包、交付物排他绑定、转化方式及形式级联、完成日期与成效佐证校验、单位审核和总部备案、退回及备案后更新、revision 并发控制。

## 3. 必须适配的差异

1. **历史统计保护。** 来源迁移脚本把历史 DONE 改成 SIGNED，并清空正式实际日期。执行前先统计受影响记录；集成迁移优先只增字段并保留原值，将历史来源及待核对状态单独标识，不擅自将历史数据认定为新流程已备案，也不直接降低既有完成统计。读接口、台账、看板和再次更新必须共同验证此兼容口径。
2. **去除归档入口及提示。** 来源 `transformFlow.ts` 仍有“可归档”“发起项目完成归档”表述。按已确认要求移除该流程提示，保持四周期；所有上传仅出现在左侧功能页，节点只读。
3. **写入权限收口。** 来源允许联系人填报，且调用的公共 `FlowAuditGuard.requireActors` 允许管理员直接通过。成果转化模块增加本模块权限校验：负责人写入，审核者只读材料并办理审核/备案；校验单位、项目归属及禁止自审。避免为修复该模块而修改其他模块的公共授权行为。
4. **附件验证。** 来源后端仅检查 evidence 文件 URL 前缀，不能证明文件存在或属于当前项目。接入真实上传记录及授权下载校验，避免误用或绕过已有 supplement-private 文件保护。
5. **新一轮更新保护。** 来源 prepareProgress 会在更新中清空正式实际日期并调整 DONE。采用草稿与已备案快照区分，更新审核期间保留上轮正式展示结果。
6. **补录边界。** 导入补录中的成果、交付物记录与正式业务表不自动互写。只读展示标清来源；若以后需要转成正式成果包，再设计经审核的映射机制。

## 4. 执行任务

- [x] 保存当前补录开发状态并清点用户其他修改；建立独立集成分支。不要使用硬重置或整库覆盖。
- [x] 按上述 13 文件的基点差异引入成果转化功能，检查功能提交的父提交关系后选择可审查的 cherry-pick 或三方补丁方式。
- [x] 先增加历史 DONE、已备案再次更新、跨单位审核、管理员写入及附件跨项目引用的失败用例，再实现第 3 节适配。
- [x] 在测试数据库运行增量迁移两次，验证幂等、记录数、旧状态及日期不丢失；验证新建数据库 schema 与升级后结构一致。
- [x] 运行来源回归 `node --test qa/transform-regression.mjs`，确认已有 `qa/vue-harness.mjs` 及前端依赖可用；此套 Mock/UI 测试不能代替真实后端验证。
- [x] 运行前端类型检查、生产构建，运行补录 JUnit 及成果转化后端测试。使用隔离项目验证创建、绑定、提交、退回、审核、备案、重新更新与 revision 冲突。
- [ ] 回归左侧补录入口、台账只读汇总、四周期、正常新建申报、文件授权；验证 1366 与 1920 宽度及窄屏抽屉。
- [ ] 确認服务器连接与实际运行版本；备份应用及受影响数据库数据，再执行迁移和部署。上轮 SSH 握手曾失败，不能沿用“已恢复”的假设。
- [ ] 验证线上正式接口与页面、清理仅本次创建的测试夹具，记录发布提交及回滚路径后交付。

## 5. 验收结果要求

成果转化全流程可操作，交付物不能跨项目或重复占用；审核人不可修改/上传，负责人不能自审。未备案的新完成声明不提前计入正式统计，历史数据及上轮备案结果不被迁移或新草稿静默改写。导入补录功能和既有平台入口保持可用，流程节点不出现上传入口。
