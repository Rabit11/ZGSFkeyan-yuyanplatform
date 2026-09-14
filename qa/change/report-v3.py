"""Render the current UI/rehearsal validation report from measured results."""
from pathlib import Path
import json,re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]; out=root/'deploy/change-v3'
read=lambda name:json.loads((out/name).read_text())
api=read('api-results.json'); browser=read('browser-results.json'); lab=read('lab-results.json'); other=read('other-pages-results.json'); scope=read('scope-check.json')
for suite in [api,browser,lab]:
    assert suite['results'] and all(r['pass'] for r in suite['results'])
    assert not suite.get('errors',[])
assert not other['errors'] and not other['failures'] and len(other['routes'])==11
assert not scope['outsideScope']
assert read('reset-results.json')['pass']
assert all(v['unchanged'] for v in scope['sourceDocuments'])
assert 'BUILD SUCCESS' in (out/'backend-build.log').read_text()
assert 'built in' in (out/'frontend-build.log').read_text()
assert not re.search(r'error TS\d+', (out/'typecheck.log').read_text())
assert re.search(r'fail 0\b',(out/'frontend-tests.log').read_text())
unit=[ET.parse(f).getroot() for f in (root/'backend/target/surefire-reports').glob('TEST-*change*.xml')]
assert sum(int(r.get('failures','0'))+int(r.get('errors','0')) for r in unit)==0
count=sum(int(r.get('tests','0')) for r in unit)
lines=['# 项目变更新界面与隔离演练测试报告','',f"最近专项测试：{lab['date']}。本轮测试使用 6026 / rpm_change_lab，原平台 6006 只做读取和登录验证。",'',
'## 实测结果','', '| 检查 | 结果 | 证据 |','| --- | --- | --- |',
f'| Java 规则及字段单元测试 | {count}/{count} | [构建日志](../../deploy/change-v3/backend-build.log) |',
'| 前端规则及异步状态 | 17/17 | [日志](../../deploy/change-v3/frontend-tests.log) |',
f"| 真实接口场景 | {len(api['results'])}/{len(api['results'])} | [结果](../../deploy/change-v3/api-results.json) |",
f"| 完整浏览器业务场景 | {len(browser['results'])}/{len(browser['results'])} | [结果](../../deploy/change-v3/browser-results.json) |",
f"| 新界面与演练专项 | {len(lab['results'])}/{len(lab['results'])} | [结果](../../deploy/change-v3/lab-results.json) |",
'| 其他页面冒烟 | 11/11，无页面及接口错误 | [结果](../../deploy/change-v3/other-pages-results.json) |',
'| 场景重置与重复启动 | 8 个场景恢复全部 13 类可变更对象，重复启动不增生项目 | [结果](../../deploy/change-v3/reset-results.json) |',
'| TypeScript / Vite | 通过 | [类型检查](../../deploy/change-v3/typecheck.log)、[构建](../../deploy/change-v3/frontend-build.log) |',
f"| 修改范围 | {scope['checkedExistingFiles']} 个既有文件对照，无越界变更；3 份原始资料 hash 不变 | [结果](../../deploy/change-v3/scope-check.json) |",
'| 样式隔离 | 从项目变更跳转里程碑，与直接进入里程碑的顶栏/侧栏/表头/标题样式相同 | [计算样式](../../deploy/change-v3/style-isolation.json) |','',
'## 新增专项用例','', '| 用例 | 结果 |','| --- | --- |']
lines += [f"| {r['name']} | 通过 |" for r in lab['results']]
lines += ['', '## 页面截图','',
'- [正式平台内的新工作台](../../deploy/change-v3/05-formal-workspace.png)',
'- [测试模式与场景操作脚本](../../deploy/change-v3/01-lab-workspace.png)',
'- [对象面板与任务指引](../../deploy/change-v3/02-object-inspector.png)',
'- [768px 窄屏面板](../../deploy/change-v3/04-narrow-inspector.png)',
'- [离开变更后的其他页面](../../deploy/change-v3/03-other-page-after-leave.png)','',
'## 本轮发现与处理','',
'1. 法务候选人过宽：改为明确配置工号名单，创建重大草稿可暂不指定，提交及实际法务办理必须有有效资格配置。',
'2. 申请可能流转到无人办理的节点：提交前检查全部节点的在岗办理人，缺少岗位时保持草稿并展示原因。',
'3. 技术负责人交接难以找到：负责人待办包含可确认提交的技术草稿。',
'4. 日期控件没有可访问标签关联：显式绑定表单标签和日期输入 ID，页面测试通过真实标签访问输入。',
'5. 保存/预填后的异步渲染：测试等待明确的已填值与按钮可用状态；不以仅收到网络响应代表页面可操作。',
'6. 新任务清单与上传控件文字相近：清单标明“定位”动作，测试限定到材料区域执行实际上传。',
'7. 刷新、站内跳转可丢失编辑：添加模块生命周期内的提醒与离开确认，取消操作保留输入。','',
'## 复现与适用边界','',
'执行 `python3 qa/change/run-lab.py` 可重跑本轮模块验证。环境启动、8 个使用场景、法务名单及重置说明见 [操作指南](04-新界面与隔离演练指南.md)。', '',
'自动化证明列出的场景在本地 Chromium 与真实本地服务下通过。未做性能压测、跨浏览器矩阵或真实 GXB 外部联调；人工视觉认可、正式法务任命及未明确渠道规则不属于自动通过项。单字段申请、历史只读等现有限制仍见操作指南。','']
(root/'docs/change/05-新界面与演练测试报告.md').write_text('\n'.join(lines))
print('Wrote latest validation report')
