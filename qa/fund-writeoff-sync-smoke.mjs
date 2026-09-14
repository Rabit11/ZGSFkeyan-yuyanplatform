#!/usr/bin/env node
/**
 * 经费预算/核销流转只读巡检。
 *
 * 默认不提交、不审批、不删除业务数据，只检查：
 * 1. 源码与构建产物中，预算核销是否为“二级单位财务上传付款凭证、完成本级核销、同步总部看板”；
 * 2. 线上待办是否推送给二级单位财务负责人，且不推给项目负责人或总部财务核销备案；
 * 3. 后端核销提交是否会写入 WRITTEN，避免回退到“待总部备案”的旧流程。
 *
 * 环境变量：
 *   RPM_BASE_URL       平台地址，默认 6006 公网地址
 *   RPM_INSECURE_TLS   设为 1 时忽略测试环境证书校验
 *   RPM_TIMEOUT_MS     单请求超时，默认 20000
 *   RPM_SKIP_LIVE      设为 1 时跳过线上接口，只做本地静态检查
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

if (process.env.RPM_INSECURE_TLS === '1') process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'

const BASE = String(
  process.env.RPM_BASE_URL ||
    'https://u476948-r3ys-6923790a.westd.seetacloud.com:8443',
).replace(/\/$/, '')
const TIMEOUT_MS = Number(process.env.RPM_TIMEOUT_MS || 20000)
const qaDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(process.argv[2] || path.join(qaDir, '..'))
const outputDir = path.join(qaDir, 'output')
fs.mkdirSync(outputDir, { recursive: true })

const stamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').replace('Z', '')
const logFile = path.join(outputDir, `fund-writeoff-sync-${stamp}.log`)
const reportFile = path.join(outputDir, `fund-writeoff-sync-problems-${stamp}.md`)
const latestLog = path.join(outputDir, 'fund-writeoff-sync-latest.log')
const latestReport = path.join(outputDir, 'fund-writeoff-sync-problems-latest.md')

const lines = []
const issues = []
const stats = { PASS: 0, WARN: 0, FAIL: 0, INFO: 0 }

function compact(value, max = 520) {
  const text = typeof value === 'string' ? value : JSON.stringify(value)
  return text.length > max ? `${text.slice(0, max)}…` : text
}

function log(level, node, message, detail = '') {
  stats[level] = (stats[level] || 0) + 1
  const row = `${new Date().toISOString()} [${level}] [${node}] ${message}${detail ? ` | ${compact(detail)}` : ''}`
  lines.push(row)
  console.log(row)
}

function issue(level, node, message, evidence, recommendation) {
  issues.push({ id: `FUND-${String(issues.length + 1).padStart(3, '0')}`, level, node, message, evidence, recommendation })
  log(level, node, message, evidence)
}

function read(relPath) {
  const abs = path.join(repoRoot, relPath)
  if (!fs.existsSync(abs)) {
    issue('FAIL', '本地文件', `缺少文件：${relPath}`, abs, '检查备份目录或工程目录是否完整。')
    return ''
  }
  return fs.readFileSync(abs, 'utf8')
}

function assertIncludes(node, relPath, text, label = text) {
  const body = read(relPath)
  if (body.includes(text)) log('PASS', node, `包含：${label}`, relPath)
  else issue('FAIL', node, `缺少关键实现：${label}`, relPath, '恢复当前 V6 规则对应的源码或重新构建。')
}

function assertNotIncludes(node, relPath, text, label = text) {
  const body = read(relPath)
  if (!body.includes(text)) log('PASS', node, `无废弃实现：${label}`, relPath)
  else issue('FAIL', node, `发现废弃实现：${label}`, relPath, '删除旧的总部核销备案/重复审核逻辑。')
}

function bundledText(relDir) {
  const abs = path.join(repoRoot, relDir)
  if (!fs.existsSync(abs)) return ''
  const chunks = []
  for (const name of fs.readdirSync(abs)) {
    if (name.endsWith('.js')) chunks.push(fs.readFileSync(path.join(abs, name), 'utf8'))
  }
  return chunks.join('\n')
}

function checkStaticRules() {
  log('INFO', '本地静态检查', '开始检查经费核销流程源码与构建产物', repoRoot)
  assertIncludes('后端核销提交', 'backend/src/main/java/com/comac/rpm/modules/fund/controller/FundController.java', 'body.setWriteoffStatus("WRITTEN")', '核销提交写入 WRITTEN')
  assertIncludes('后端核销待办', 'backend/src/main/java/com/comac/rpm/modules/fund/controller/FundController.java', '二级单位财务上传付款凭证并完成本级核销')
  assertIncludes('后端核销办理', 'backend/src/main/java/com/comac/rpm/modules/fund/controller/FundController.java', '二级单位财务完成本级核销并同步总部经费看板')
  assertNotIncludes('后端废弃流程', 'backend/src/main/java/com/comac/rpm/modules/fund/controller/FundController.java', '总部财务备案经费核销')

  assertIncludes('前端核销按钮', 'frontend/src/views/implement/Fund.vue', '完成本级核销并同步总部看板')
  assertIncludes('前端核销说明', 'frontend/src/views/implement/Fund.vue', '数据自动同步至总部经费看板')
  assertIncludes('前端核销状态', 'frontend/src/views/implement/Fund.vue', "writeoffStatus: asDraft ? 'DRAFT' : 'WRITTEN'", '提交核销直接进入 WRITTEN')
  assertNotIncludes('前端废弃按钮', 'frontend/src/views/implement/Fund.vue', '提交单位财务负责人审核')
  assertNotIncludes('前端废弃说明', 'frontend/src/views/implement/Fund.vue', '流转总部财务备案')

  assertIncludes('实施流程图', 'frontend/src/utils/implementFlow.ts', '二级单位财务上传付款凭证并完成本级核销')
  assertIncludes('实施流程图', 'frontend/src/utils/implementFlow.ts', '系统同步单位经费数据到总部经费看板')
  assertNotIncludes('实施流程图废弃节点', 'frontend/src/utils/implementFlow.ts', '总部财务主管备案确认')

  assertIncludes('工作定责', 'frontend/src/constants/workDuty.ts', '上传付款凭证并完成本级核销后，数据自动同步总部经费看板')
  assertIncludes('待办过滤', 'frontend/src/stores/pending.ts', "row.node.includes('本级核销')")
  assertIncludes('项目下拉过滤', 'frontend/src/components/ProjectSelect.vue', "no.startsWith('QA_')", '业务项目下拉过滤内部 QA 测试项目')
  assertIncludes('项目下拉选中项补全', 'frontend/src/components/ProjectSelect.vue', 'ensureSelectedProject', '当前选中项目不在列表时用详情接口补全')
  assertIncludes('项目下拉编号搜索', 'frontend/src/components/ProjectSelect.vue', 'p.projectNo ||', '项目下拉支持按项目编号搜索')
  assertIncludes('经费页路由刷新', 'frontend/src/views/implement/Fund.vue', 'route.query.projectId', '同一经费页面内切换待办 projectId 时重新加载项目')

  const distText = bundledText('frontend/dist/assets')
  if (!distText) {
    issue('WARN', '构建产物', '未找到 frontend/dist/assets，可先运行 npm run build', 'frontend/dist/assets', '部署前必须重新构建。')
    return
  }
  for (const text of ['完成本级核销并同步总部看板', '二级单位财务上传付款凭证并完成本级核销', '系统同步单位经费数据到总部经费看板']) {
    if (distText.includes(text)) log('PASS', '构建产物', `包含：${text}`)
    else issue('FAIL', '构建产物', `缺少：${text}`, 'frontend/dist/assets/*.js', '运行前端构建并重新部署。')
  }
  for (const text of ['QA_MS_AUDIT_', 'startsWith("QA_")', 'projectId', '.detail(Number(']) {
    if (distText.includes(text)) log('PASS', '构建产物', `包含页面修复点：${text}`)
    else issue('FAIL', '构建产物', `缺少页面修复点：${text}`, 'frontend/dist/assets/*.js', '运行前端构建并重新部署。')
  }
  for (const stale of ['总部财务备案经费核销', '总部财务主管备案确认', '提交单位财务负责人审核', '流转总部财务备案']) {
    if (!distText.includes(stale)) log('PASS', '构建产物', `无废弃文案：${stale}`)
    else issue('FAIL', '构建产物', `发现废弃文案：${stale}`, 'frontend/dist/assets/*.js', '重新构建并确认部署目录。')
  }
}

async function request(method, urlPath, { token, body } = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
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
    return { ok: res.ok, status: res.status, text, json }
  } catch (error) {
    return { ok: false, status: 0, text: '', json: null, error: String(error?.message || error) }
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

async function login(username) {
  const result = await request('POST', '/api/auth/login', { body: { username, password: username } })
  const data = apiData(result) || {}
  if (!apiPassed(result) || !data.token) {
    issue('FAIL', '线上登录', `账号 ${username} 登录失败`, `HTTP ${result.status}; ${result.error || result.json?.msg || result.text}`, '检查演示账号、后端服务和网络。')
    return null
  }
  log('PASS', '线上登录', `账号 ${username} 登录成功`, data.user?.realName || data.realName || '')
  return { token: data.token, user: data.user || data }
}

async function pendingRows(username) {
  const session = await login(username)
  if (!session) return []
  const result = await request('GET', '/api/fund/reviews/pending', { token: session.token })
  if (!apiPassed(result)) {
    issue('FAIL', '线上待办', `账号 ${username} 待办接口异常`, `HTTP ${result.status}; ${result.error || result.json?.msg || result.text}`, '检查 /api/fund/reviews/pending 路由和权限。')
    return []
  }
  const rows = Array.isArray(apiData(result)) ? apiData(result) : []
  log('PASS', '线上待办', `账号 ${username} 返回 ${rows.length} 条经费待办`, rows.map((r) => `${r.mode}/${r.desk}/${r.node}`).join('；'))
  return rows
}

async function checkHqFundAccess(username, shouldAllow, label) {
  const session = await login(username)
  if (!session) return
  const result = await request('GET', '/api/hq-fund/budgets', { token: session.token })
  const allowed = apiPassed(result)
  const denied = result.status === 403 || Number(result.json?.code) === 403
  if (shouldAllow && allowed) {
    log('PASS', '总部经费权限', `${label} 可以访问总部经费预算管控`, `HTTP ${result.status}`)
  } else if (!shouldAllow && denied) {
    log('PASS', '总部经费权限', `${label} 已被限制访问总部经费预算管控`, `HTTP ${result.status}; code ${result.json?.code}`)
  } else {
    issue(
      'FAIL',
      '总部经费权限',
      `${label} 总部经费预算管控权限不正确`,
      `expect=${shouldAllow ? 'allow' : 'deny'}; HTTP ${result.status}; ${result.json?.msg || result.text || result.error || ''}`,
      '二级单位财务部长只能办理本单位经费审核、核销、总核；总部经费预算管控仅限总部财务/总部科技/管理员。',
    )
  }
}

function writeoffRows(rows) {
  return rows.filter((row) => row?.mode === 'writeoff')
}

async function checkLiveRules() {
  if (process.env.RPM_SKIP_LIVE === '1') {
    log('WARN', '线上待办', '已按 RPM_SKIP_LIVE=1 跳过线上接口检查')
    return
  }
  log('INFO', '线上待办', '开始检查角色推送', BASE)
  const finHead = writeoffRows(await pendingRows('100010'))
  const owner = writeoffRows(await pendingRows('100012'))
  const hqFinance = writeoffRows(await pendingRows('100009'))
  const admin = writeoffRows(await pendingRows('100001'))

  if (owner.length === 0) log('PASS', '项目负责人待办', '项目负责人没有收到核销上传/办理待办')
  else issue('FAIL', '项目负责人待办', '项目负责人不应收到核销上传/办理待办', owner, '核销待办只推给二级单位财务负责人。')

  if (hqFinance.length === 0) log('PASS', '总部财务待办', '总部财务没有收到核销备案待办')
  else issue('FAIL', '总部财务待办', '总部财务不应收到核销备案待办', hqFinance, '核销本级完成后自动同步看板，不再人工总部备案。')

  for (const row of [...finHead, ...admin]) {
    const node = String(row.node || '')
    const desk = String(row.desk || '')
    if (/总部|备案/.test(node) && row.mode === 'writeoff') {
      issue('FAIL', '核销待办节点', '出现废弃总部核销备案节点', row, '删除总部核销备案待办生成逻辑。')
    } else if (!['writeoff', 'writeoff-upload'].includes(desk)) {
      issue('FAIL', '核销待办入口', '核销待办 desk 不正确', row, '核销入口只能是 writeoff-upload 或 writeoff。')
    } else if (/上传付款凭证|本级核销/.test(node)) {
      log('PASS', '核销待办节点', `待办节点正确：${node}`, row.projectNo || '')
    } else {
      issue('WARN', '核销待办节点', '核销待办节点不是当前推荐文案', row, '如为旧数据兼容可忽略，否则统一文案。')
    }
  }

  if (admin.length > 0 && finHead.length === 0) {
    issue('FAIL', '二级单位财务推送', '管理员能看到核销待办，但二级单位财务负责人看不到', { admin }, '检查 canActOnProject、岗位人员和待办过滤。')
  } else if (finHead.length > 0) {
    log('PASS', '二级单位财务推送', '核销待办已推给二级单位财务负责人', finHead)
  } else {
    issue('WARN', '二级单位财务推送', '当前环境没有未完成核销待办可验证', '', '可在业务产生新核销节点后重跑脚本。')
  }

  await checkHqFundAccess('100010', false, '二级单位财务部长毕仲文')
  await checkHqFundAccess('100009', true, '总部财务主管赵美玲')
  await checkHqFundAccess('100001', true, '管理员')
}

function writeReports() {
  const report = [
    '# 经费核销流转巡检报告',
    '',
    `- 时间：${new Date().toLocaleString('zh-CN')}`,
    `- 工程：${repoRoot}`,
    `- 环境：${process.env.RPM_SKIP_LIVE === '1' ? '仅本地静态检查' : BASE}`,
    `- 统计：PASS ${stats.PASS} / WARN ${stats.WARN} / FAIL ${stats.FAIL}`,
    '',
    issues.length ? '## 问题清单' : '## 结果',
    '',
    issues.length
      ? issues.map((x) => `- ${x.id} [${x.level}] ${x.node}：${x.message}\n  - 证据：${compact(x.evidence, 900)}\n  - 建议：${x.recommendation}`).join('\n')
      : '未发现阻断问题。',
    '',
  ].join('\n')
  fs.writeFileSync(logFile, `${lines.join('\n')}\n`, 'utf8')
  fs.writeFileSync(latestLog, `${lines.join('\n')}\n`, 'utf8')
  fs.writeFileSync(reportFile, report, 'utf8')
  fs.writeFileSync(latestReport, report, 'utf8')
  log('INFO', '报告', '已写入巡检报告', latestReport)
}

checkStaticRules()
await checkLiveRules()
writeReports()

if (stats.FAIL > 0) {
  console.error(`FAIL: ${stats.FAIL} 个阻断问题，详见 ${latestReport}`)
  process.exit(1)
}
console.log(`PASS: 经费核销流转巡检完成；WARN=${stats.WARN}；报告=${latestReport}`)
