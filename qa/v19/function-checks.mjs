/** Real Vue script-setup execution, isolated API spies. No network or business writes. */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { mountSource, vue, flush, deferred } from '../vue-harness.mjs'

const definitions = []
const add = (id, stage, title, source, expected, run) => definitions.push({ id, stage, title, source, expected, run })
const sources = {
  acceptance: 'frontend/src/views/acceptance/Acceptance.vue',
  transform: 'frontend/src/views/transform/Transform.vue',
  partner: 'frontend/src/views/acceptance/PartnerEval.vue',
  evaluation: 'frontend/src/views/implement/Evaluation.vue',
  plan: 'frontend/src/views/implement/Plan.vue',
}
function setup(kind, permissions = {}) {
  const rights = { fill: true, submit: true, audit: true, edit: true, ...permissions }
  const writes = [], notices = [], dialogs = []
  const record = key => async (...args) => { writes.push({ key, args }); return { data: 501 } }
  const projectApi = {
    page: async () => ({ data: { records: [{ id: 101, name: '隔离项目A' }] } }),
    overview: async id => ({ data: { project: { id, name: '隔离项目' + id } } }),
    detail: async id => ({ data: { id, name: '隔离项目' + id } }),
  }
  const api = {
    projectApi,
    acceptanceApi: {
      detail: async id => ({ data: { projectId: id, status: 'APPLYING', items: [] } }),
      check: async () => ({ data: [{ key: 'milestone', passed: true }] }),
      submit: record('accept.submit'), materials: record('accept.materials'), audit: record('accept.audit'),
    },
    transformApi: { page: async () => ({ data: { records: [] } }), create: record('transform.create'), bind: record('transform.bind') },
    deliverableApi: { list: async () => ({ data: [] }) },
    partnerApi: { list: async () => ({ data: [] }), blacklist: async () => ({ data: [] }), create: record('partner.create'), submit: record('partner.submit') },
    evalApi: { list: async () => ({ data: [] }), create: record('evaluation.create'), remove: record('evaluation.remove') },
    planApi: { list: async () => ({ data: [] }), finishApply: record('plan.apply'), finishAudit: record('plan.audit'), sync: record('plan.sync') },
  }
  const dependencies = {
    '@/api/modules': api,
    'vue-router': { useRoute: () => ({ query: { projectId: '101' } }) },
    'ant-design-vue': {
      message: Object.fromEntries(['success', 'warning', 'error', 'info'].map(key => [key, text => notices.push({ key, text })])),
      Modal: { confirm: options => dialogs.push(options) },
    },
    '@ant-design/icons-vue': {},
    '@/utils/format': { fmtDate: String, dueText: String },
    '@/utils/acceptFlow': { buildAcceptFlowOptsFromOverview: () => ({}) },
    '@/api/request': { isSilentAuthError: () => false },
    '@/stores/dict': { useDictStore: () => ({ load: async () => {}, options: () => [], label: (_, value) => value }) },
    '@/composables/useWorkDuty': { useWorkDuty: () => ({ can: vue.ref(rights), guard: action => Boolean(rights[action]) }) },
  }
  return { ...mountSource(sources[kind].replace('frontend/src/', ''), dependencies), api, writes, notices, dialogs }
}
async function withFixture(kind, body, permissions) {
  const f = setup(kind, permissions)
  try { await flush(); await body(f); await flush() } finally { f.unmount() }
}
const ac = (id, title, expected, run) => add(id, '项目验收', title, sources.acceptance, expected, run)
const tr = (id, title, expected, run) => add(id, '成果转化', title, sources.transform, expected, run)
const imp = (id, kind, title, expected, run) => add(id, '实施阶段', title, sources[kind], expected, run)

ac('FN-AC01', '未执行前置校验不得提交验收', 'allPassed=false 且提交请求数=0', () => withFixture('acceptance', async f => {
  assert.equal(f.state.allPassed, false)
  await f.state.submit()
  assert.equal(f.writes.length, 0)
}))
ac('FN-AC02', '任一前置项未通过阻断验收提交', '失败项保留且提交请求数=0', () => withFixture('acceptance', async f => {
  f.api.acceptanceApi.check = async () => ({ data: [{ key: 'milestone', passed: true }, { key: 'fund', passed: false }] })
  await f.state.runCheck(); await f.state.submit()
  assert.equal(f.state.allPassed, false); assert.equal(f.writes.length, 0)
}))
ac('FN-AC03', '全部校验通过后向当前项目提交一次', '只提交 projectId=101', () => withFixture('acceptance', async f => {
  await f.state.runCheck(); await f.state.submit()
  assert.equal(f.writes.length, 1); assert.equal(f.writes[0].args[0], 101)
}))
ac('FN-AC04', '空校验结果不得提示校验通过', 'allPassed=false 且不出现成功提示', () => withFixture('acceptance', async f => {
  f.api.acceptanceApi.check = async () => ({ data: [] })
  await f.state.runCheck()
  assert.equal(f.state.allPassed, false)
  assert.equal(f.notices.some(n => n.key === 'success'), false, '空数组校验返回了成功提示，和不可提交状态矛盾')
}))
ac('FN-AC05', '项目切换加载期间旧校验立即失效', '新项目加载未完成时不得发出验收提交', () => withFixture('acceptance', async f => {
  await f.state.runCheck()
  const held = deferred(); f.api.acceptanceApi.detail = () => held.promise
  f.state.projectId = 202
  const loading = f.state.load()
  const submitted = f.state.submit()
  held.resolve({ data: { projectId: 202, items: [] } })
  await Promise.all([loading, submitted])
  assert.equal(f.writes.length, 0, '新项目使用旧项目的前置校验结果发出了提交请求')
}))
ac('FN-AC06', '快速切换项目晚返回结果不得覆盖新项目', 'projectId=202 时 detail.projectId 仍为202', () => withFixture('acceptance', async f => {
  const held = deferred()
  f.api.acceptanceApi.detail = id => id === 101 ? held.promise : Promise.resolve({ data: { projectId: id, items: [] } })
  const old = f.state.load()
  f.state.projectId = 202; await f.state.load()
  held.resolve({ data: { projectId: 101, items: [] } }); await old
  assert.equal(f.state.detail.projectId, 202, '旧项目异步响应覆盖了当前验收信息')
}))
ac('FN-AC07', '连续点击验收提交防止重复请求', '前次请求未完成时只发出一次提交', () => withFixture('acceptance', async f => {
  await f.state.runCheck()
  const held = deferred()
  f.api.acceptanceApi.submit = async id => { f.writes.push({ key: 'accept.submit', args: [id] }); return held.promise }
  const first = f.state.submit(); const second = f.state.submit()
  held.resolve({ data: true }); await Promise.all([first, second])
  assert.equal(f.writes.length, 1, '验收提交缺少处理中保护，连续点击发送两次请求')
}))
ac('FN-AC08', '未选择真实文件不能标记材料已上传', '缺少文件、文件ID或文件地址时不得登记上传成功', () => withFixture('acceptance', async f => {
  await f.state.upload({ fieldCode: 'accept_report', materialName: '验收报告', locked: false })
  assert.equal(f.writes.length, 0, '只携带 fieldCode 就登记材料上传，未选择和传输文件')
}))
ac('FN-AC09', '无填报权限人员不可执行上传动作', 'fill=false 时材料写入请求数=0', () => withFixture('acceptance', async f => {
  await f.state.upload({ fieldCode: 'report', materialName: '报告', locked: false })
  assert.equal(f.writes.length, 0, '上传处理函数未应用页面岗位授权；后端是否拒绝需集成验证')
}, { fill: false, edit: false, submit: false, audit: false }))
ac('FN-AC10', '不适用层级材料保持锁定', 'locked=true 时不调用上传接口', () => withFixture('acceptance', async f => {
  await f.state.upload({ fieldCode: 'national', materialName: '国家级材料', locked: true })
  assert.equal(f.writes.length, 0)
}))
ac('FN-AC11', '普通填报人不能执行验收办结', 'audit=false 时不打开审核确认且无审核请求', () => withFixture('acceptance', async f => {
  await f.state.finish(); assert.equal(f.dialogs.length, 0); assert.equal(f.writes.length, 0)
}, { audit: false }))

tr('FN-TR01', '转化方式对应正确的形式选项', '型号=装机/未装机；市场=转让/许可/联合/投资/其他', () => withFixture('transform', async f => {
  assert.equal(f.state.formOptions.map(x => x.value).join(','), 'INSTALLED,UNINSTALLED')
  f.state.form.transformWay = 'MARKET'
  assert.equal(f.state.formOptions.map(x => x.value).join(','), 'TRANSFER,LICENSE,JOINT,INVEST,OTHER')
}))
tr('FN-TR02', '成果名称与所属项目缺失阻止保存', '空白新建表单不调用创建接口', () => withFixture('transform', async f => {
  await f.state.save()
  assert.equal(f.writes.length, 0, '必填名称和项目未填写仍发送创建请求')
}))
tr('FN-TR03', '未选择项目不能借用首个项目计算权限', 'selectedProject=null，不能以其他项目权限代替', () => withFixture('transform', async f => {
  f.state.form.projectId = undefined
  assert.equal(f.state.selectedProject, null, '未选择项目时默认套用了列表第一个项目的权限上下文')
}))
tr('FN-TR04', '无填报权限阻止成果创建', 'fill=false 时创建请求数=0', () => withFixture('transform', async f => {
  Object.assign(f.state.form, { name: '成果A', projectId: 101 }); await f.state.save()
  assert.equal(f.writes.length, 0)
}, { fill: false }))
tr('FN-TR05', '已绑定但未交付成果不能自动进入已选清单', 'bindIds 只含已交付记录', () => withFixture('transform', async f => {
  f.api.deliverableApi.list = async () => ({ data: [
    { id: 1, status: 'PENDING', achievementNo: 'CG-01' }, { id: 2, status: 'DELIVERED', achievementNo: 'CG-01' },
  ] })
  await f.state.openBind({ id: 9, projectId: 101, achievementNo: 'CG-01' })
  assert.equal(f.state.bindIds.join(','), '2', '仅禁止点击未交付行，但把既有未交付关联预选进提交清单')
}))
tr('FN-TR06', '无写权限人员不能确认成果关联', 'fill/edit/submit=false 时绑定请求数=0', () => withFixture('transform', async f => {
  await f.state.openBind({ id: 9, projectId: 101, achievementNo: 'CG-01' }); await f.state.doBind()
  assert.equal(f.writes.length, 0, '确认绑定处理函数没有岗位权限拦截；不等同于证明后端可越权')
}, { fill: false, edit: false, submit: false, audit: false }))
tr('FN-TR07', '成果保存成功关闭填写页', '成功回调后 open=false 且请求绑定所选项目编号', () => withFixture('transform', async f => {
  f.state.projects = [{ id: 101, name: '项目A', projectNo: 'XM-101' }]
  Object.assign(f.state.form, { name: '成果A', projectId: 101 }); f.state.open = true
  await f.state.save()
  assert.equal(f.state.open, false); assert.equal(f.writes[0].args[0].projectNo, 'XM-101')
}))
tr('FN-TR08', '成果保存失败保留表单', '请求失败后 open=true 且名称不丢失', () => withFixture('transform', async f => {
  Object.assign(f.state.form, { name: '待重试成果', projectId: 101 }); f.state.open = true
  f.api.transformApi.create = async () => { throw new Error('模拟服务拒绝') }
  await assert.rejects(() => f.state.save(), /模拟服务拒绝/)
  assert.equal(f.state.open, true); assert.equal(f.state.form.name, '待重试成果')
}))
tr('FN-TR09', '已有大于200条成果时不丢失后续页', '数据总量201时仍能翻页查看第201条', () => withFixture('transform', async f => {
  f.api.transformApi.page = async ({ page = 1, size }) => ({ data: {
    total: 201, records: Array.from({ length: Math.min(size, Math.max(0, 201 - (page - 1) * size)) }, (_, i) => ({ id: (page - 1) * size + i + 1 })),
  } })
  await f.state.load(); f.state.query.page = 26
  assert.equal(f.state.pageRows.some(x => x.id === 201), true, '页面强制只取前200条后本地分页，漏掉第201条')
}))

add('FN-PA01', '项目验收', '协作评价等级边界正确', sources.partner, '59不合格、60合格、80良好、90优秀、100优秀', () => withFixture('partner', async f => {
  for (const [total, grade] of [[59, 'FAIL'], [60, 'PASS'], [79, 'PASS'], [80, 'GOOD'], [89, 'GOOD'], [90, 'EXCELLENT'], [100, 'EXCELLENT']]) {
    let remaining = total
    for (const dim of ['techScore', 'qualityScore', 'progressScore', 'serviceScore', 'complianceScore']) {
      f.state.form[dim] = Math.min(20, remaining); remaining -= f.state.form[dim]
    }
    assert.equal(f.state.total, total); assert.equal(f.state.previewGrade, grade)
  }
}))
add('FN-PA02', '项目验收', '无授权时不能提交协作单位评价', sources.partner, '不弹出提交确认、不发送提交', () => withFixture('partner', async f => {
  await f.state.submit({ id: 7, score: 90 }); assert.equal(f.dialogs.length, 0); assert.equal(f.writes.length, 0)
}, { submit: false }))
imp('FN-IM01', 'plan', '计划办结申请经过提交权限校验', '无 submit 权限时不调用办结接口', () => withFixture('plan', async f => {
  await f.state.apply({ id: 7 }); assert.equal(f.writes.length, 0)
}, { submit: false }))
imp('FN-IM02', 'plan', '计划终审经过审核权限校验', '无 audit 权限时不调用终审接口', () => withFixture('plan', async f => {
  await f.state.audit({ id: 7 }); assert.equal(f.writes.length, 0)
}, { audit: false }))
imp('FN-IM03', 'evaluation', '评估材料保存不能只登记名称就声称已上传', '未选择文件不报告材料上传成功', () => withFixture('evaluation', async f => {
  f.state.form.name = '中期评估'
  await f.state.save()
  assert.equal(f.notices.some(n => n.key === 'success' && n.text.includes('已上传')), false, '仅提交名称、类型、日期和结果，未携带文件却提示材料已上传')
}))
imp('FN-IM04', 'evaluation', '未授权人员不能删除评估记录', 'fill/edit/submit/audit=false 时不发出删除', () => withFixture('evaluation', async f => {
  await f.state.remove({ id: 7 }); assert.equal(f.writes.length, 0, '评估删除处理函数没有权限拦截；后端数据授权另验')
}, { fill: false, edit: false, submit: false, audit: false }))

export async function runChecks() {
  const cases = []
  for (const { run, ...definition } of definitions) {
    const started = performance.now()
    try {
      await run()
      cases.push({ ...definition, status: 'PASS', evidence: definition.expected, durationMs: Math.round(performance.now() - started) })
    } catch (error) {
      // Setup/runtime errors are infrastructure blocks, never product assertion failures.
      cases.push({ ...definition, status: error?.code === 'ERR_ASSERTION' ? 'FAIL' : 'BLOCKED', evidence: String(error?.message || error), durationMs: Math.round(performance.now() - started) })
    }
    console.log(`${cases.at(-1).status} ${definition.id} ${definition.title}`)
  }
  return { suite: 'functions', layer: 'Vue组件逻辑（API与岗位策略为测试替身；非浏览器、非后端权限证明）', cases: cases.map(c => ({ ...c, requirementIds: [] })) }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = await runChecks()
  const outputIndex = process.argv.indexOf('--output')
  const output = outputIndex >= 0 ? process.argv[outputIndex + 1] : fileURLToPath(new URL('./output/function-results.json', import.meta.url))
  if (!output) throw new Error('--output requires a filename')
  await mkdir(path.dirname(path.resolve(output)), { recursive: true })
  await writeFile(output, JSON.stringify(result, null, 2) + '\n')
  process.exitCode = result.cases.some(c => c.status === 'FAIL') ? 1 : result.cases.some(c => c.status === 'BLOCKED') ? 2 : 0
}
