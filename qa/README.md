# 6006平台任务流巡检

`task-flow-smoke.mjs` 是无第三方测试框架依赖的只读巡检脚本，默认不会提交、审批、删除或修改业务数据。

覆盖范围：

- 6006网关与前端主要路由
- 登录鉴权及人员花名册
- 立项渠道、项目申报和负责人待审节点
- 两个项目样本及全生命周期聚合接口
- 历史/未来里程碑及里程碑材料
- 计划、经费、评估、交付物、参研评价、验收、成果转化和后评价
- 各演示身份的数据访问

PowerShell运行命令：

```powershell
$env:RPM_INSECURE_TLS='1'
$env:RPM_TIMEOUT_MS='30000'
node qa/task-flow-smoke.mjs
```

也可通过 `RPM_BASE_URL` 指定其他环境。运行结果写入 `qa/output/`：

- `task-flow-test-latest.log`：最新完整日志
- `task-flow-problems-latest.md`：最新问题节点报告
- 同时保留带时间戳的历史日志与报告

脚本发现 FAIL 时退出码为1，仅有 PASS/WARN 时退出码为0，便于接入定时任务或CI。

## 经费核销流转巡检

`fund-writeoff-sync-smoke.mjs` 用于检查 V6 规则：里程碑闭环后，二级单位财务上传付款凭证并完成本级核销，数据自动同步至总部经费看板；核销待办不再推送到项目负责人或总部财务备案页面。

PowerShell运行命令：

```powershell
$env:RPM_INSECURE_TLS='1'
$env:RPM_TIMEOUT_MS='30000'
node qa/fund-writeoff-sync-smoke.mjs
```

如只想检查本地源码和 `dist` 构建产物，可跳过线上接口：

```powershell
$env:RPM_SKIP_LIVE='1'
node qa/fund-writeoff-sync-smoke.mjs
```

运行结果写入 `qa/output/`：

- `fund-writeoff-sync-latest.log`：最新完整日志
- `fund-writeoff-sync-problems-latest.md`：最新问题报告
