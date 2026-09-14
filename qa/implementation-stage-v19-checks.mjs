#!/usr/bin/env node
/**
 * V19.1 实施阶段模块测试脚本。
 *
 * 默认执行本地静态检查；传入 --live 后增加只读接口巡检。
 * 脚本不提交、不审批、不删除、不修改业务数据。
 *
 * 用法：
 *   node qa/implementation-stage-v19-checks.mjs --static
 *   RPM_BASE_URL=https://host:port RPM_INSECURE_TLS=1 node qa/implementation-stage-v19-checks.mjs --live
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

if (process.env.RPM_INSECURE_TLS === '1') process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'

const args = new Set(process.argv.slice(2))
const RUN_LIVE = args.has('--live')
const BASE = String(
  process.env.RPM_BASE_URL ||
    'https://u476948-r3ys-6923790a.westd.seetacloud.com:8443',
).replace(/\/$/, '')
const TIMEOUT_MS = Number(process.env.RPM_TIMEOUT_MS || 20000)

const qaDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(process.argv.find((x) => x.startsWith('--root='))?.slice(7) || path.join(qaDir, '..'))
const outputDir = path.join(qaDir, 'output')
fs.mkdirSync(outputDir, { recursive: true })

const stamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').replace('Z', '')
const logFile = path.join(outputDir, `implementation-stage-v19-${stamp}.log`)
const reportFile = path.join(outputDir, `implementation-stage-v19-report-${stamp}.md`)
const latestLog = path.join(outputDir, 'implementation-stage-v19-latest.log')
const latestReport = path.join(outputDir, 'implementation-stage-v19-report-latest.md')

const lines = []
const issues = []
const stats = { PASS: 0, WARN: 0, FAIL: 0, INFO: 0 }

function compact(value, max = 700) {
  const text = typeof value === 'string' ? value : JSON.stringify(value)
  return text.length > max ? `${text.slice(0, max)}...` : text
}

function log(level, node, message, detail = '') {
  stats[level] = (stats[level] || 0) + 1
  const row = `${new Date().toISOString()} [${level}] [${node}] ${message}${detail ? ` | ${compact(detail)}` : ''}`
  lines.push(row)
  console.log(row)
}

function issue(level, node, message, evidence, recommendation) {
  issues.push({
    id: `IMPL-${String(issues.length + 1).padStart(3, '0')}`,
    level,
    node,
    message,
    evidence,
    recommendation,
  })
  log(level, node, message, evidence)
}

function read(relPath) {
  const abs = path.join(repoRoot, relPath)
  if (!fs.existsSync(abs)) {
    issue('FAIL', '本地文件', `缺少文件：${relPath}`, abs, '确认工程目录完整，或用 --root 指向“给会冉”项目根目录。')
    return ''
  }
  return fs.readFileSync(abs, 'utf8')
}

function fileExists(relPath, label = relPath) {
  const abs = path.join(repoRoot, relPath)
  if (fs.existsSync(abs)) log('PASS', '文件存在', label, relPath)
  else issue('FAIL', '文件存在', `缺少 ${label}`, relPath, '补齐实施阶段页面、接口或测试依赖文件。')
}

function includes(node, relPath, needle, label = needle, level = 'FAIL') {
  const body = read(relPath)
  if (body.includes(needle)) log('PASS', node, `包含：${label}`, relPath)
  else issue(level, node, `缺少：${label}`, relPath, '按 V19.1 需求补齐实现，或在差异表中说明产品确认口径。')
}

function notIncludes(node, relPath, needle, label = needle, level = 'FAIL') {
  const body = read(relPath)
  if (!body.includes(needle)) log('PASS', node, `未发现废弃/冲突内容：${label}`, relPath)
  else issue(level, node, `发现废弃/冲突内容：${label}`, relPath, '确认是否为历史兼容；若不是，应改为 V19.1 口径。')
}

function regexIncludes(node, relPath, regex, label, level = 'FAIL') {
  const body = read(relPath)
  if (regex.test(body)) log('PASS', node, `匹配：${label}`, relPath)
  else issue(level, node, `未匹配：${label}`, relPath, '检查字段、状态、接口和流程节点是否按 V19.1 落地。')
}

function checkRoutesAndPages() {
  log('INFO', '静态检查', '检查实施阶段页面、路由和客户端接口', repoRoot)
  for (const rel of [
    'frontend/src/views/implement/BasicInfo.vue',
    'frontend/src/views/implement/ImplementReview.vue',
    'frontend/src/views/implement/Milestone.vue',
    'frontend/src/views/implement/MilestoneClose.vue',
    'frontend/src/views/implement/Plan.vue',
    'frontend/src/views/implement/Fund.vue',
    'frontend/src/views/implement/Evaluation.vue',
    'frontend/src/views/implement/Change.vue',
    'frontend/src/utils/implementFlow.ts',
    'frontend/src/api/modules.ts',
  ]) fileExists(rel)

  const router = 'frontend/src/router/index.ts'
  for (const route of [
    '/implement/basic',
    '/implement/review',
    '/implement/milestone',
    '/implement/milestone-close',
    '/implement/plan',
    '/implement/fund',
    '/implement/evaluation',
    '/implement/change',
  ]) includes('实施阶段路由', router, route.replace('/implement/', ''), route)

  const api = 'frontend/src/api/modules.ts'
  for (const endpoint of [
    '/api/projects/${id}/maintenance/submit',
    '/api/projects/${id}/maintenance/unit-audit',
    '/api/projects/${id}/maintenance/hq-audit',
    '/api/milestones/annual-plan/audit',
    '/api/milestones/${id}/close',
    '/api/milestones/${id}/close-audit',
    '/api/plans/${id}/finish-apply',
    '/api/plans/${id}/finish-audit',
    '/api/fund/budgets',
    '/api/fund/payments',
    '/api/fund/payments/${id}/writeoff',
    '/api/evaluations',
    '/api/changes/${id}/submit',
    '/api/changes/${id}/audit',
    '/api/audit-logs/mine/done',
  ]) includes('前端 API', api, endpoint, endpoint)
}

function checkBasicInfo() {
  const flow = 'frontend/src/utils/implementFlow.ts'
  const project = 'backend/src/main/java/com/comac/rpm/modules/project/controller/ProjectController.java'
  includes('节点十四-基本信息', flow, '项目团队补充缺失字段', '项目团队补充缺失字段')
  includes('节点十四-基本信息', flow, '项目负责人', '项目负责人参与基本信息岗位')
  includes('节点十四-基本信息', flow, '总部科研', '总部科研确认')
  includes('节点十四-基本信息', project, '@PostMapping("/{id}/maintenance/submit")', '基本信息提交接口')
  includes('节点十四-基本信息', project, '@PostMapping("/{id}/maintenance/unit-audit")', '单位审核接口')
  includes('节点十四-基本信息', project, '@PostMapping("/{id}/maintenance/hq-audit")', '总部确认接口')
  regexIncludes('节点十四-基本信息', project, /maintenance\/materials|saveMaintenanceMaterial|ProjMaterial/i, '基本信息补充材料保存')
  regexIncludes('需求差异-基本信息审批链', flow, /单位分管领导|分管领导/, '单位分管领导复核节点', 'WARN')
}

function checkMilestones() {
  const flow = 'frontend/src/utils/implementFlow.ts'
  const controller = 'backend/src/main/java/com/comac/rpm/modules/milestone/controller/MilestoneController.java'
  const msPage = 'frontend/src/views/implement/Milestone.vue'
  const closePage = 'frontend/src/views/implement/MilestoneClose.vue'

  includes('节点十五-年度清单', flow, '编制里程碑节点', '编制里程碑节点')
  includes('节点十五-年度清单', flow, '二级单位科技部门审核存档', '二级单位科技部门审核存档')
  includes('节点十五-年度清单', controller, '@PostMapping("/milestones/annual-plan/audit")', '年度清单审核接口')
  regexIncludes('节点十五-年度清单', controller, /PENDING_AUDIT|RETURN|DONE/, '年度清单审核状态')

  for (const color of ['BLUE', 'YELLOW', 'RED', 'GREEN']) {
    regexIncludes('节点十六-四色预警', `${color === 'BLUE' ? controller : flow}`, new RegExp(color), `存在 ${color} 状态`, 'WARN')
  }
  regexIncludes('节点十六-四色预警', flow, /到期前 30 天|超期转红|临期|逾期/, '30 天内黄色、超期红色')
  includes('节点十六-延期限制', flow, '/implement/change', '延期走项目变更入口')
  includes('节点十六-延期限制', controller, '@PostMapping("/milestones/{id}/delay")', '延期标记接口')

  includes('节点十七-完成审核', controller, '@PostMapping("/milestones/{id}/materials")', '材料上传接口')
  includes('节点十七-完成审核', controller, '@PostMapping("/milestones/{id}/close")', '完成提交接口')
  includes('节点十七-完成审核', controller, '@PostMapping("/milestones/{id}/close-audit")', '完成审核接口')
  includes('节点十七-完成审核', flow, '按清单上传交付物证明', '交付物证明上传')
  includes('节点十七-完成审核', flow, '审核销项', '销项审核节点')
  regexIncludes('需求差异-里程碑完成审核', flow, /项目承担部门负责人审核销项|单位科研管理部门负责人审核销项/, '现实现为承担部门负责人和单位科研管理部门负责人审核', 'WARN')
  regexIncludes('节点十七-材料页', closePage, /actualDate|fileName|fileUrl|evidence|材料|上传/, '完成日期与材料字段')
  regexIncludes('节点十五-页面', msPage, /annual|年度|里程碑|交付物/, '年度目标、里程碑、交付物页面字段')
}

function checkPlansAndFunds() {
  const plan = 'backend/src/main/java/com/comac/rpm/modules/plan/controller/PlanController.java'
  const fund = 'backend/src/main/java/com/comac/rpm/modules/fund/controller/FundController.java'
  const fundPage = 'frontend/src/views/implement/Fund.vue'
  const flow = 'frontend/src/utils/implementFlow.ts'

  includes('节点十八-计划同步', plan, '@PostMapping("/plans/sync")', 'CMOS/计划同步接口')
  includes('节点十八-计划办结', plan, '@PostMapping("/plans/{id}/finish-apply")', '办结申请接口')
  includes('节点十八-计划办结', plan, '@PostMapping("/plans/{id}/finish-audit")', '办结审核接口')
  regexIncludes('节点十八-计划四色', 'frontend/src/views/implement/Plan.vue', /绿色|蓝色|黄色|红色|colorStatus|finishStatus/, '计划状态展示')

  includes('节点十九-预算填报', fund, '@PostMapping("/fund/budgets")', '预算创建接口')
  includes('节点十九-预算审核', fund, '@PutMapping("/fund/budgets/{id}")', '预算审核/更新接口')
  includes('节点十九-预算审核', fund, '总部财务复核备案', '总部财务复核备案待办')
  includes('节点十九-预算绑定', fundPage, 'milestoneId', '预算绑定里程碑')
  regexIncludes('节点十九-预算状态', fundPage, /PENDING|UNIT_OK|APPROVED/, '预算审签状态')
  regexIncludes('需求差异-预算前置', fundPage, /全部里程碑节点闭环|allClosed|requireFundReady/, '当前实现要求全部里程碑闭环后才执行经费流程', 'WARN')

  includes('节点二十-核销填报', fund, '@PostMapping("/fund/payments")', '核销/付款创建接口')
  includes('节点二十-核销审核', fund, '@PostMapping("/fund/payments/{id}/writeoff")', '核销办理接口')
  includes('节点二十-核销责任人', fund, '二级单位财务上传付款凭证并完成本级核销', '二级单位财务上传付款凭证')
  includes('节点二十-核销同步', fundPage, '完成本级核销并同步总部看板', '本级核销后同步总部看板')
  includes('节点二十-核销材料', fundPage, '核销材料（必传）', '核销材料必传提示')
  regexIncludes('节点二十-核销状态', fund, /WRITTEN|DRAFT|PENDING/, '核销状态枚举')
  regexIncludes('节点二十-核销前置', fund, /needsWriteoffUpload|milestoneId|DONE|GREEN/, '对应里程碑闭环后核销判断')
  regexIncludes('需求差异-核销口径', flow, /全部里程碑.*核销|allMsDone|全部节点闭环/, '当前实现存在全部里程碑闭环后核销/总核口径', 'WARN')
}

function checkEvaluationChangeAudit() {
  const evalController = 'backend/src/main/java/com/comac/rpm/modules/evaluation/controller/EvaluationController.java'
  const change = 'backend/src/main/java/com/comac/rpm/modules/change/controller/ChangeController.java'
  const audit = 'backend/src/main/java/com/comac/rpm/modules/system/controller/AuditController.java'
  const auditAspect = 'backend/src/main/java/com/comac/rpm/common/audit/OperationAuditAspect.java'
  const flow = 'frontend/src/utils/implementFlow.ts'

  includes('节点二十一-评估检查', evalController, '@GetMapping("/projects/{projectId}/evaluations")', '评估列表接口')
  includes('节点二十一-评估检查', evalController, '@PostMapping("/evaluations")', '评估创建接口')
  regexIncludes('节点二十一-评估检查', flow, /二级单位|总部|评估|归档/, '评估初审、终审、归档流程提示')
  regexIncludes('节点二十一-渠道差异', 'frontend/src/views/implement/Evaluation.vue', /渠道|MJKY|专项|学术委员会|联盟|波音|中期|阶段/, '渠道差异材料或流程提示', 'WARN')

  includes('节点二十二-项目变更', change, '@PostMapping("/{id}/submit")', '变更提交接口')
  includes('节点二十二-项目变更', change, '@PostMapping("/{id}/audit")', '变更审核接口')
  regexIncludes('节点二十二-项目变更', 'frontend/src/views/implement/Change.vue', /变更前|变更后|原因|影响|材料|changeType/, '变更前后值、原因、影响和材料字段')
  regexIncludes('节点二十二-审批链', flow, /二级单位主管部门初审|总部管理部门终审|总部/, '变更二级单位初审与总部终审')
  regexIncludes('节点二十二-重大变更', 'frontend/src/views/implement/Change.vue', /法务|重大|延期|总经费|外协|付款节点|考核指标|项目周期|交付物/, '重大变更类型和法务关注', 'WARN')

  includes('待办-经费', 'backend/src/main/java/com/comac/rpm/modules/fund/controller/FundController.java', '@GetMapping("/fund/reviews/pending")', '经费待办接口')
  includes('已办-审计', audit, '@GetMapping("/mine/done")', '我的已办接口')
  includes('审计日志', auditAspect, 'POST/PUT/PATCH/DELETE', '写操作审计切面')
  regexIncludes('审计日志', auditAspect, /sys_audit_log|auditLogMapper\.write/, '审计日志写入')
}

function checkStaticRules() {
  checkRoutesAndPages()
  checkBasicInfo()
  checkMilestones()
  checkPlansAndFunds()
  checkEvaluationChangeAudit()
}

async function request(method, urlPath, { token, body } = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  const started = Date.now()
  try {
    const headers = { Accept: 'application/json' }
    if (token) headers.Authorization = `Bearer ${token}`
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    const res = await fetch(`${BASE}${urlPath}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
    const text = await res.text()
    let json = null
    try { json = text ? JSON.parse(text) : null } catch {}
    return { ok: res.ok, status: res.status, ms: Date.now() - started, text, json }
  } catch (error) {
    return { ok: false, status: 0, ms: Date.now() - started, text: '', json: null, error: String(error?.message || error) }
  } finally {
    clearTimeout(timer)
  }
}

function apiPassed(result) {
  return result.status >= 200 && result.status < 300 && result.json && Number(result.json.code) === 0
}

function apiData(result) {
  return result?.json?.data
}

function rows(data) {
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.records)) return data.records
  if (Array.isArray(data?.list)) return data.list
  return []
}

async function login(username, password = username) {
  const result = await request('POST', '/api/auth/login', { body: { username, password } })
  const data = apiData(result) || {}
  if (!apiPassed(result) || !data.token) {
    issue('FAIL', '线上登录', `账号 ${username} 登录失败`, `HTTP ${result.status}; ${result.error || result.json?.msg || result.text}`, '检查测试账号、密码和后端服务状态。')
    return null
  }
  log('PASS', '线上登录', `账号 ${username} 登录成功`, data.user?.realName || data.realName || '')
  return { token: data.token, user: data.user || data }
}

async function checkLiveApi(node, urlPath, token, { required = true } = {}) {
  const result = await request('GET', urlPath, { token })
  if (apiPassed(result)) {
    log('PASS', node, `GET ${urlPath}`, `${result.ms}ms`)
  } else {
    issue(required ? 'FAIL' : 'WARN', node, `接口异常：GET ${urlPath}`, `HTTP ${result.status}; ${result.error || result.json?.msg || result.text}`, '检查服务、权限和接口路由。')
  }
  return result
}

async function checkLiveRules() {
  log('INFO', '线上只读巡检', '开始检查实施阶段接口可读性', BASE)
  const admin = await login(process.env.RPM_ADMIN_USER || '100001', process.env.RPM_ADMIN_PASSWORD || process.env.RPM_ADMIN_USER || '100001')
  if (!admin) return
  const token = admin.token

  await checkLiveApi('健康检查', '/api/health', token, { required: false })
  const projectResult = await checkLiveApi('项目列表', '/api/projects?page=1&size=5', token)
  const projectRows = rows(apiData(projectResult))
  if (!projectRows.length) {
    issue('WARN', '项目样本', '项目列表为空，无法抽样巡检实施阶段子模块', '', '准备至少一个已立项或实施中项目样本。')
  }

  for (const project of projectRows.slice(0, 2)) {
    const id = project.id
    const label = `${project.projectNo || id} ${project.name || ''}`
    await checkLiveApi('项目基本信息', `/api/projects/${id}`, token)
    await checkLiveApi('全生命周期聚合', `/api/projects/${id}/overview`, token)
    const ms = await checkLiveApi('里程碑列表', `/api/projects/${id}/milestones`, token)
    await checkLiveApi('计划列表', `/api/projects/${id}/plans`, token)
    await checkLiveApi('经费预算', `/api/projects/${id}/fund/budgets`, token)
    await checkLiveApi('经费核销', `/api/projects/${id}/fund/payments`, token)
    await checkLiveApi('评估检查', `/api/projects/${id}/evaluations`, token)
    const msRows = rows(apiData(ms))
    if (!msRows.length) issue('WARN', '里程碑样本', '项目没有里程碑，无法验证四色预警与完成审核', label, '为测试项目补齐年度里程碑清单。')
    for (const m of msRows.slice(0, 3)) {
      await checkLiveApi('里程碑材料', `/api/milestones/${m.id}/materials`, token, { required: false })
    }
  }

  await checkLiveApi('经费待办', '/api/fund/reviews/pending', token)
  await checkLiveApi('变更列表', '/api/changes?page=1&size=10', token)
  await checkLiveApi('审计日志', '/api/audit-logs?page=1&size=10', token, { required: false })
  await checkLiveApi('我的已办', '/api/audit-logs/mine/done?page=1&size=10', token, { required: false })
}

function writeReports() {
  const summary = `PASS ${stats.PASS} / WARN ${stats.WARN} / FAIL ${stats.FAIL} / INFO ${stats.INFO}`
  const rowsMd = issues.length
    ? issues.map((x) => `| ${x.id} | ${x.level} | ${x.node} | ${String(x.message).replace(/\|/g, '\\|')} | ${String(compact(x.evidence, 900)).replace(/\|/g, '\\|')} | ${String(x.recommendation).replace(/\|/g, '\\|')} |`).join('\n')
    : '| - | - | - | 未发现问题 | - | - |'

  const report = [
    '# 实施阶段模块 V19.1 自动化检查报告',
    '',
    `- 时间：${new Date().toLocaleString('zh-CN')}`,
    `- 工程：${repoRoot}`,
    `- 模式：${RUN_LIVE ? `静态检查 + 线上只读巡检（${BASE}）` : '本地静态检查'}`,
    `- 汇总：${summary}`,
    '',
    '## 问题与差异',
    '',
    '| 编号 | 级别 | 节点 | 问题 | 证据 | 建议 |',
    '|---|---|---|---|---|---|',
    rowsMd,
    '',
    '## 说明',
    '',
    '- FAIL 表示缺少关键入口、接口或业务规则，建议作为缺陷处理。',
    '- WARN 表示需求与现实现状可能不一致，或需要产品确认最终口径。',
    '- 线上巡检为只读检查，不覆盖提交、审批、驳回和越权写接口的完整端到端验证。',
  ].join('\n')

  fs.writeFileSync(logFile, `${lines.join('\n')}\n`, 'utf8')
  fs.copyFileSync(logFile, latestLog)
  fs.writeFileSync(reportFile, `${report}\n`, 'utf8')
  fs.copyFileSync(reportFile, latestReport)
  console.log(`LOG_FILE=${logFile}`)
  console.log(`REPORT_FILE=${reportFile}`)
  console.log(`SUMMARY=${summary}`)
}

try {
  checkStaticRules()
  if (RUN_LIVE) await checkLiveRules()
} catch (error) {
  issue('FAIL', '脚本异常', '检查脚本异常终止', String(error?.stack || error), '查看堆栈并修复脚本或运行环境后重试。')
}

writeReports()
process.exitCode = stats.FAIL > 0 ? 1 : 0
