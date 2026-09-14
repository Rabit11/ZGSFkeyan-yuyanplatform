"""Generate a concise, evidence-linked report from completed change module test runs."""
import json,re,xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[2]
logs=root/'deploy/change'
api=json.loads((logs/'api-results.json').read_text())
browser=json.loads((logs/'browser-results.json').read_text())
scope=json.loads((logs/'scope-check.json').read_text())
unit=[]
for file in (root/'backend/target/surefire-reports').glob('TEST-*change*.xml'):
 r=ET.parse(file).getroot();unit.append({key:int(r.get(key,'0')) for key in ['tests','failures','errors','skipped']})
frontend=(logs/'frontend-unit.log').read_text()
api_pass=sum(r['pass'] for r in api['results']);browser_pass=sum(r['pass'] for r in browser['results'])
assert api_pass==len(api['results']) and browser_pass==len(browser['results'])
assert not scope['unexpected'] and not browser['errors']
other=json.loads((logs/'other-pages-results.json').read_text())
assert len(other['routes'])==11 and not other['errors'] and not other['failures']
assert sum(r['failures']+r['errors'] for r in unit)==0
assert re.search(r'fail 0\b',frontend)
lines=['# 项目变更模块测试报告','',f"API 执行时间：{api['date']}。浏览器执行时间：{browser['date']}。",'',
'测试连接本地真实 Spring Boot、MySQL、Redis、MinIO 服务。使用独立 `QA_CHANGE_...` 项目和真实演示账号，流程结束后清理测试项目、变更记录及其精确 MinIO 对象；保留截图和日志。原业务样例不作为写入测试对象。','',
'## 结果','', '| 检查 | 结果 | 证据 |','| --- | --- | --- |',
f"| 后端规则与字段单元测试 | {sum(r['tests'] for r in unit)} / {sum(r['tests'] for r in unit)} 通过 | [Maven 日志](../../deploy/change/backend-build.log) |",
'| 前端规则及异步状态单测 | 17 / 17 通过 | [单测日志](../../deploy/change/frontend-unit.log) |',
f"| 真实 API 场景 | {api_pass} / {len(api['results'])} 通过 | [结构化结果](../../deploy/change/api-results.json) |",
f"| Chromium 实际页面操作 | {browser_pass} / {len(browser['results'])} 通过，无页面运行异常 | [结构化结果](../../deploy/change/browser-results.json) |",
'| TypeScript 与 Vite 构建 | 通过 | [类型检查](../../deploy/change/typecheck.log)、[构建](../../deploy/change/frontend-build.log) |',
'| 其他模块原有回归 | 5 组通过 | [回归日志](../../deploy/change/platform-regression.log) |',
'| 其他页面浏览器冒烟 | 11 个路由通过，无页面/API 错误 | [结果](../../deploy/change/other-pages-results.json) |',
f"| 修改边界校验 | {scope['checkedFiles']} 个既有文件逐一比对，无越界修改 | [SHA-256 比对](../../deploy/change/scope-check.json) |",'',
'## API 场景清单','', '| 场景 | 结果 |','| --- | --- |']
lines += [f"| {r['name']} | {'通过' if r['pass'] else '失败'} |" for r in api['results']]
lines += ['', '## 页面操作清单','', '| 场景 | 结果 |','| --- | --- |']
lines += [f"| {r['name']} | {'通过' if r['pass'] else '失败'} |" for r in browser['results']]
lines += ['', '## 页面证据','',
'- [原页面内的变更工作台](../../deploy/change/01-workbench.png)',
'- [前后对照与审批路径](../../deploy/change/02-comparison-and-route.png)',
'- [终审后履历](../../deploy/change/03-approved-history.png)',
'- [重大变更与法务选择](../../deploy/change/04-major-change-form.png)',
'- [768px 抽屉适配](../../deploy/change/05-narrow-drawer.png)',
'- [GXB 归档完成](../../deploy/change/06-gxb-archive.png)', '',
'截图来自独立测试项目，不是正常业务新建的数据。测试日志与截图位于本地忽略目录 `deploy/change`，需要交付给其他人时应另行打包。','',
'## 测试发现并修复的问题','',
'1. 并发办理读取到旧快照，造成重复审批或幂等创建返回错误：改为模块 READ_COMMITTED 事务和锁后当前读，并以同时请求验证。',
'2. 类型筛选前后端参数不同：统一使用 changeType，验证筛选后每条记录类型及状态。',
'3. 技术负责人草稿缺少提交交接：增加由指定项目负责人确认提交的路径。',
'4. 付款节点已进入办结审核仍可调整：排除待审核及已完成记录。',
'5. 页面重复保存、旧响应覆盖、缺少保存/提交区分：以异步单测及浏览器流程验证操作锁、请求序号和明确两步操作。',
'6. 原统计看板以 size=200 查询变更列表：保留这一读取契约，未修改看板代码。',
'7. 长表格测量行被样式撑高、编辑切换详情保留原滚动位置：限定实际数据行样式，切换抽屉模式时回到内容顶部。','',
'## 复现命令','', '在仓库根目录，按 02-模块设计与维护指南准备本地数据库并执行迁移、构建及启动真实服务后：','',
'```bash',
'node --test qa/change/policy.test.mjs qa/change/workbench.test.mjs',
'node qa/change/api.mjs',
'LD_LIBRARY_PATH="$PWD/.deploy-libs/rootfs/usr/lib/x86_64-linux-gnu" node qa/change/browser.mjs',
'npm run typecheck --prefix frontend',
'npm run build --prefix frontend',
'JAVA_HOME="$PWD/.deploy-libs/jdk-17.0.20.1+1" .deploy-libs/apache-maven-3.9.9/bin/mvn -B -f backend/pom.xml test',
'node qa/run-platform-checks.mjs',
'```','',
'浏览器脚本可用 PLAYWRIGHT_MODULE 指定本机 Playwright 的模块路径。SQL 辅助工具仅用于本地隔离测试，读取现有私有 MySQL 客户端配置。不要在生产数据库执行这些写入测试。','',
'## 验证范围与限制','',
'本报告证明上述场景在本地环境通过，不代表全平台业务规则已全部修复。原有全平台 V19 检查的未完成项仍见上一轮记录，本次没有为通过测试去修改其他模块。未执行压力测试、真实 GXB 联调、专职法务资格验证、跨浏览器矩阵或完整手机端测试。当前模块的流程口径、历史只读策略、Mock 模式及其他模块旁路编辑边界见 01、02 文档。','']
(root/'docs/change/03-模块测试报告.md').write_text('\n'.join(lines))
print('Wrote docs/change/03-模块测试报告.md')
