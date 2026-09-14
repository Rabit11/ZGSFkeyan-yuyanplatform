/** Execute the application's real flow display and employee-resolution utilities. */
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const frontend = path.resolve(here, '../../frontend')
const require = createRequire(path.join(frontend, 'package.json'))
const { build } = require('esbuild')
const compiled = await build({
  stdin: { contents: [
    "import * as actor from './src/utils/flowActor'",
    "import * as duty from './src/utils/workDuty'",
    "import * as accept from './src/utils/acceptFlow'",
    "import * as transform from './src/utils/transformFlow'",
    "import * as implement from './src/utils/implementFlow'",
    'export { actor, duty, accept, transform, implement }',
  ].join('\n'), resolveDir: frontend, loader: 'ts' },
  bundle: true, platform: 'node', format: 'esm', write: false,
  alias: { '@': path.join(frontend, 'src') },
})
const { actor, duty, accept, transform, implement } = await import('data:text/javascript;base64,' + Buffer.from(compiled.outputFiles[0].text).toString('base64'))
const definitions = []
const add = (id, stage, title, source, expected, run) => definitions.push({ id, stage, title, source: 'frontend/src/utils/' + source + '.ts', expected, run })

add('FV-01', '跨阶段', '办理人按指定工号准确匹配', 'flowActor', '指定工号可办，不同工号不同姓名不可办', () => {
  const handlers = [{ employeeNo: '901001', name: '审核甲' }]
  assert.equal(actor.canActOnHandlers(handlers, { employeeNo: '901001', realName: '审核甲' }), true)
  assert.equal(actor.canActOnHandlers(handlers, { employeeNo: '901002', realName: '审核乙' }), false)
})
add('FV-02', '跨阶段', '同名但工号不同的人不能代替指定办理人', 'flowActor', '工号明确且不一致时不可因姓名相同放行', () => {
  assert.equal(actor.canActOnHandlers([{ employeeNo: '901001', name: '同名人员' }], { employeeNo: '901002', realName: '同名人员' }), false, '工号不匹配后退回姓名匹配，放行同名不同人')
})
add('FV-03', '跨阶段', '姓名前缀不能冒充完整指定姓名', 'flowActor', '姓名较短不能匹配另一个完整姓名', () => {
  assert.equal(actor.canActOnHandlers([{ label: '测试人员甲（901001）' }], { employeeNo: '901002', realName: '测试人员' }), false, 'startsWith 姓名前缀判定导致错误匹配')
})
add('FV-04', '跨阶段', '岗位缺失时不能只按角色开放审核', 'workDuty', '无项目岗位人员时 audit.can=false 且显示待指定', () => {
  const result = duty.resolveWorkDuty('accept', { id: 101, teamMembers: [] }, { employeeNo: '901003', identityCode: 'unitHead', realName: '单位负责人' })
  assert.equal(result.audit.pending, true)
  assert.equal(result.audit.can, false, '未指定项目审核人时仍按任职身份开放审核')
})
add('FV-05', '项目验收', '未完成节点阻断前置校验展示', 'acceptFlow', '未闭环里程碑 passed=false', () => {
  const check = accept.buildAcceptChecks({ milestones: [{ status: 'CLOSE_UNIT_AUDIT', colorStatus: 'BLUE' }] })
  assert.equal(check.find(c => c.key === 'MILESTONE').passed, false)
})
add('FV-06', '项目验收', '绿色展示不能覆盖待审核业务状态', 'acceptFlow', 'CLOSE_UNIT_AUDIT即使误带绿色也不能显示闭环通过', () => {
  const check = accept.buildAcceptChecks({ milestones: [{ status: 'CLOSE_UNIT_AUDIT', colorStatus: 'GREEN' }] })
  assert.equal(check.find(c => c.key === 'MILESTONE').passed, false, '颜色标签被当作审核通过的替代证据')
})
add('FV-07', '项目验收', '验收流转图保留全部六类前置检查', 'acceptFlow', '里程碑、交付物、外协、经费、整改、材料均有校验展示', () => {
  const checks = accept.buildAcceptChecks({})
  assert.equal(checks.length >= 6, true, '当前仅返回4类校验，遗漏外协事项与验收材料齐套')
})
add('FV-08', '项目验收', '验收完成状态不能凭空生成已上传附件', 'acceptFlow', '没有实际文件信息不应展示已上传、虚构文件名或大小', () => {
  const result = accept.buildAcceptFlowOptsFromOverview({ project: { id: 101, levelCode: 'COMPANY' }, acceptance: { status: 'DONE', items: [] } })
  assert.equal(result.groups.flatMap(g => g.files).some(f => f.uploaded && !f.fileUrl), false, '仅因验收DONE生成了已上传PDF及默认128KB信息')
})
add('FV-09', '项目验收', '缺失验收办理人不得回填固定演示工号', 'acceptFlow', '非系统节点无岗位来源时保持待指定', () => {
  const result = accept.buildAcceptFlowOptsFromOverview({ project: { id: 101, teamMembers: [] }, acceptance: { status: 'APPLYING' } })
  assert.equal(result.nodes.filter(n => ['AUDIT', 'ACTION'].includes(n.nodeType)).flatMap(n => n.handlers).some(h => h.employeeNo), false, '项目团队为空仍显示固定花名册中的办理人')
})
add('FV-10', '项目验收', '不同项目层级开放对应验收材料', 'acceptFlow', '国家三级/地方两级/公司两级', () => {
  for (const [level, expected] of [['NATIONAL', 'COMPANY,NATIONAL,UNIT'], ['LOCAL', 'LOCAL,UNIT'], ['COMPANY', 'COMPANY,UNIT']]) {
    const result = accept.buildAcceptFlowOptsFromOverview({ project: { id: 101, levelCode: level } })
    assert.equal(result.groups.filter(g => !g.locked).map(g => g.code).sort().join(','), expected)
  }
})
add('FV-11', '成果转化', '流程摘要处理人须与当前节点一致', 'transformFlow', '当前二级审核摘要显示审核人甲而非经办人乙', () => {
  const teamMembers = [
    { roleCode: 'UNIT_MINISTER', employeeNo: '901005', userName: '审核人甲' },
    { roleCode: 'UNIT_SUPERVISOR', employeeNo: '901008', userName: '经办人乙' },
  ]
  const result = transform.buildTransformFlowOptsFromOverview({ project: { id: 101, teamMembers }, transforms: [{ id: 1, status: 'NEGOTIATING' }] })
  const current = result.nodes.find(n => n.nodeCode === 'TF_UNIT')
  assert.equal(current.status, 'current')
  assert.equal(result.currentFlow.includes(current.handlers[0].name), true, '当前节点审核人与顶部当前流程摘要使用了不同人员')
})
add('FV-12', '成果转化', '缺失成果办理人不得回填固定演示工号', 'transformFlow', '缺失岗位的业务节点显示待指定', () => {
  const result = transform.buildTransformFlowOptsFromOverview({ project: { id: 101, teamMembers: [] }, transforms: [] })
  assert.equal(result.nodes.filter(n => ['AUDIT', 'ACTION'].includes(n.nodeType)).flatMap(n => n.handlers).some(h => h.employeeNo), false, '无岗位仍按固定演示花名册显示负责人和审核人')
})
add('FV-13', '实施阶段', '待销项审核节点不得计为完成', 'implementFlow', 'CLOSE_DEPT_AUDIT时msDone=0，节点审核保持当前', () => {
  const result = implement.buildImplementFlowOpts({ id: 101, status: 'IMPLEMENTING', milestones: [{ id: 1, name: '节点', planDate: '2026-10-31', status: 'CLOSE_DEPT_AUDIT', colorStatus: 'BLUE' }] })
  assert.equal(result.msDone, 0)
  assert.equal(result.milestoneFlows.length, 1)
  assert.equal(result.milestoneFlows[0].nodes.some(n => n.status === 'current'), true)
})

const cases = []
for (const { run, ...definition } of definitions) {
  try { run(); cases.push({ ...definition, status: 'PASS', evidence: definition.expected, requirementIds: [] }) }
  catch (error) { cases.push({ ...definition, status: error.code === 'ERR_ASSERTION' ? 'FAIL' : 'BLOCKED', evidence: error.message, requirementIds: [] }) }
  console.log(`${cases.at(-1).status} ${definition.id} ${definition.title}`)
}
const outputIndex = process.argv.indexOf('--output')
const output = outputIndex >= 0 ? process.argv[outputIndex + 1] : path.join(here, 'output/flow-view-results.json')
if (!output) throw new Error('--output requires a filename')
await mkdir(path.dirname(path.resolve(output)), { recursive: true })
await writeFile(output, JSON.stringify({ suite: 'flow-view', layer: '真实前端流程图/岗位解析函数；不证明后端API和消息投递', cases }, null, 2) + '\n')
process.exitCode = cases.some(c => c.status === 'FAIL') ? 1 : cases.some(c => c.status === 'BLOCKED') ? 2 : 0
