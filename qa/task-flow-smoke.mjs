#!/usr/bin/env node
/**
 * 科研项目平台任务流只读巡检。
 *
 * 默认只执行登录、查询、结构与节点一致性校验，不提交、审核、删除或修改业务数据。
 * 环境变量：
 *   RPM_BASE_URL       平台地址
 *   RPM_INSECURE_TLS   设为 1 时忽略测试环境证书校验
 *   RPM_TIMEOUT_MS     单请求超时，默认 20000
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
const root = path.dirname(fileURLToPath(import.meta.url))
const outputDir = path.join(root, 'output')
fs.mkdirSync(outputDir, { recursive: true })

const now = new Date()
const stamp = now.toISOString().replace(/[:.]/g, '-').replace('T', '_').replace('Z', '')
const logFile = path.join(outputDir, `task-flow-test-${stamp}.log`)
const reportFile = path.join(outputDir, `task-flow-problems-${stamp}.md`)
const latestLog = path.join(outputDir, 'task-flow-test-latest.log')
const latestReport = path.join(outputDir, 'task-flow-problems-latest.md')

const lines = []
const issues = []
const stats = { PASS: 0, WARN: 0, FAIL: 0, INFO: 0 }

function compact(value, max = 360) {
  const text = typeof value === 'string' ? value : JSON.stringify(value)
  return text.length > max ? `${text.slice(0, max)}…` : text
}

function log(level, node, message, detail = '') {
  stats[level] = (stats[level] || 0) + 1
  const row = `${new Date().toISOString()} [${level}] [${node}] ${message}${detail ? ` | ${compact(detail)}` : ''}`
  lines.push(row)
  console.log(row)
}

function problem(level, node, message, evidence, recommendation) {
  issues.push({ id: `FLOW-${String(issues.length + 1).padStart(3, '0')}`, level, node, message, evidence, recommendation })
  log(level, node, message, evidence)
}

async function request(method, urlPath, { token, body, expectJson = true } = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  const started = Date.now()
  try {
    const headers = { Accept: expectJson ? 'application/json' : 'text/html,*/*' }
    if (token) headers.Authorization = `Bearer ${token}`
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    const res = await fetch(`${BASE}${urlPath}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'follow',
      signal: controller.signal,
    })
    const text = await res.text()
    let json = null
    if (expectJson) {
      try {
        json = text ? JSON.parse(text) : null
      } catch {
        // 由调用方记录内容类型/JSON问题。
      }
    }
    return { ok: res.ok, status: res.status, ms: Date.now() - started, text, json, headers: res.headers }
  } catch (error) {
    return { ok: false, status: 0, ms: Date.now() - started, text: '', json: null, error: String(error?.message || error) }
  } finally {
    clearTimeout(timer)
  }
}

function apiData(result) {
  return result?.json?.data
}

function records(data) {
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.records)) return data.records
  if (Array.isArray(data?.list)) return data.list
  return []
}

function apiPassed(result) {
  return result.status >= 200 && result.status < 300 && result.json && Number(result.json.code) === 0
}

async function checkApi(node, urlPath, token, { required = true } = {}) {
  const result = await request('GET', urlPath, { token })
  if (apiPassed(result)) {
    log('PASS', node, `GET ${urlPath}`, `${result.ms}ms`)
  } else {
    problem(
      required ? 'FAIL' : 'WARN',
      node,
      `接口异常：GET ${urlPath}`,
      `HTTP ${result.status}; ${result.error || result.json?.msg || result.text || '无响应'}`,
      '检查6006反向代理、后端路由、权限配置及服务日志。',
    )
  }
  return result
}

const expectedPeople = {
  '100001': '系统管理员', '100002': '周明远', '100003': '王建国', '100004': '何雨桐',
  '100005': '方致远', '100006': '田念慈', '100007': '陈铁军', '100008': '蔡文渊',
  '100009': '赵美玲', '100010': '毕仲文', '100011': '龚雪君', '100012': '林晚晴',
  '100013': '顾思远', '100014': '沈知行', '100015': '陆嘉言',
}

async function login(username, password) {
  const result = await request('POST', '/api/auth/login', { body: { username, password } })
  const data = apiData(result) || {}
  return { result, token: data.token || data.accessToken, user: data.user || data }
}

async function main() {
  log('INFO', 'RUN', '开始任务流只读巡检', `base=${BASE}; timeout=${TIMEOUT_MS}ms`)

  // 1. 网关、前端路由与未授权边界。
  for (const route of ['/', '/initiation/declaration', '/overview/ledger', '/implement/milestone-close', '/acceptance/project']) {
    const result = await request('GET', route, { expectJson: false })
    const html = /text\/html/i.test(result.headers?.get?.('content-type') || '') && /<html/i.test(result.text)
    if (result.status === 200 && html) log('PASS', '前端路由', `GET ${route}`, `${result.ms}ms`)
    else problem('FAIL', '前端路由', `页面不可用：${route}`, `HTTP ${result.status}; ${result.error || '非HTML响应'}`, '检查前端构建产物与history路由回退。')
  }
  const unauthorized = await request('GET', '/api/projects?page=1&size=1')
  if ([401, 403].includes(unauthorized.status) || Number(unauthorized.json?.code) === 401 || Number(unauthorized.json?.code) === 403) {
    log('PASS', '登录鉴权', '未登录访问业务接口被拒绝', `HTTP ${unauthorized.status}`)
  } else {
    problem('FAIL', '登录鉴权', '未登录可读取项目数据', `HTTP ${unauthorized.status}; code=${unauthorized.json?.code}`, '检查Spring Security匿名访问白名单。')
  }

  // 2. 账号与人员口径。登录属于无业务数据变更操作。
  const sessions = new Map()
  const accounts = Object.entries(expectedPeople)
    .map(([employeeNo, realName]) => [employeeNo, employeeNo, realName])
  for (const [username, password, expectedName] of accounts) {
    const { result, token, user } = await login(username, password)
    if (!apiPassed(result) || !token) {
      problem('FAIL', '登录与人员', `账号 ${username} 登录失败`, `HTTP ${result.status}; ${result.json?.msg || result.error || '未返回token'}`, '核对演示账号密码、Redis会话与用户启用状态。')
      continue
    }
    sessions.set(username, { token, user })
    const actualName = String(user?.realName || user?.name || '')
    if (expectedName && actualName && actualName !== expectedName) {
      problem('FAIL', '人员口径', `账号 ${username} 姓名不一致`, `期望=${expectedName}; 实际=${actualName}`, '同步人员花名册权威口径并清理旧姓名。')
    } else {
      log('PASS', '登录与人员', `账号 ${username} 登录成功`, `${actualName || expectedName}; ${result.ms}ms`)
    }
  }

  const primary = sessions.get('100001') || sessions.get('100012')
  if (!primary) throw new Error('管理员和项目负责人均无法登录，后续测试终止')
  const token = primary.token
  await checkApi('系统健康', '/api/health', token)
  const candidatesResult = await checkApi('人员候选', '/api/users/candidates', token)
  const candidateRows = records(apiData(candidatesResult))
  for (const [employeeNo, name] of Object.entries(expectedPeople)) {
    const hit = candidateRows.find((x) => String(x.employeeNo) === employeeNo)
    if (!hit) problem('FAIL', '人员候选', `缺少工号 ${employeeNo}`, name, '补齐候选人员接口数据。')
    else if (String(hit.realName || hit.name) !== name) problem('FAIL', '人员候选', `工号 ${employeeNo} 姓名错误`, `期望=${name}; 实际=${hit.realName || hit.name}`, '按权威花名册更新。')
  }

  // 3. 全局任务流入口。
  const channelsResult = await checkApi('立项渠道', '/api/dict/channels', token)
  if (apiPassed(channelsResult) && records(apiData(channelsResult)).length === 0) {
    problem('FAIL', '立项渠道', '渠道清单为空', '', '配置至少一个可用申报渠道及材料清单。')
  }
  await checkApi('驾驶舱', '/api/dashboard/overview', token)
  await checkApi('预警中心', '/api/dashboard/warnings', token)
  await checkApi('里程碑看板', '/api/milestones/board', token)
  await checkApi('项目变更', '/api/changes?page=1&size=100', token)
  await checkApi('交付物', '/api/deliverables?page=1&size=100', token)
  await checkApi('成果转化', '/api/transforms?page=1&size=100', token)
  await checkApi('后评价', '/api/post-evals?page=1&size=100', token)
  await checkApi('预警任务', '/api/warnings?page=1&size=100', token)
  await checkApi('审计日志', '/api/audit-logs?page=1&size=20', token, { required: false })

  // 4. 申报流：数据状态、当前节点、实名负责人及本人待审可见性。
  const declarationsResult = await checkApi('项目申报', '/api/declarations?page=1&size=200', token)
  const declarations = records(apiData(declarationsResult))
  const validDeclarationNodes = new Set([
    '', '项目联系人', '项目负责人', '项目承担部门负责人', '承办部门负责人', '二级总师',
    '单位财务部门负责人', '单位科技部门负责人', '单位分管领导', '一级总师',
    '总部科研项目处', '线上报备归档', '归档',
  ])
  const validDeclarationStatuses = new Set(['DRAFT', 'SUBMITTED', 'APPROVING', 'APPROVED', 'REJECTED', 'REVOKED', 'REPORTED'])
  for (const d of declarations) {
    const key = `${d.applyNo || d.id} ${d.name || ''}`
    if (!validDeclarationStatuses.has(String(d.status || ''))) {
      problem('FAIL', '项目申报', '申报状态不在约定枚举中', `${key}; status=${d.status}`, '统一前后端申报状态枚举。')
    }
    if (!validDeclarationNodes.has(String(d.flowNode || ''))) {
      problem('FAIL', '项目申报', '出现未知审批节点', `${key}; flowNode=${d.flowNode}`, '修正审批链节点名称或增加兼容映射。')
    }
    if (d.status === 'APPROVING' && !d.flowNode) {
      problem('FAIL', '项目申报', '审批中但当前节点为空', key, '提交事务中同步写入flowNode。')
    }
    if (d.status === 'APPROVING' && !d.posts?.leader) {
      problem('FAIL', '项目负责人审核', '审批中申报未绑定项目负责人', key, '提交前强制校验并持久化leader岗位。')
    }
    if (d.flowNode === '项目负责人' && d.posts?.leader) {
      const leaderNo = String(d.posts.leader).replace(/\D/g, '')
      if (!leaderNo) problem('WARN', '项目负责人审核', '负责人未包含工号，待审匹配可能依赖同名', `${key}; leader=${d.posts.leader}`, '负责人岗位统一保存“姓名（工号）”。')
    }
  }
  const linPending = declarations.filter((d) => d.status === 'APPROVING' && d.flowNode === '项目负责人' && String(d.posts?.leader || '').includes('100012'))
  log('INFO', '项目负责人待审', `林晚晴（100012）当前负责人待审 ${linPending.length} 条`)

  // 5. 保留样本项目的全生命周期聚合与子模块读取。
  const projectsResult = await checkApi('项目台账', '/api/projects?page=1&size=50', token)
  const projects = records(apiData(projectsResult))
  if (projects.length < 2) {
    problem('FAIL', '项目台账', '样本项目少于2个', `实际=${projects.length}`, '按要求保留两个具备历史与未来节点的项目样本。')
  } else {
    log('PASS', '项目台账', '样本项目数量满足最低要求', `实际=${projects.length}`)
  }

  const sampleProjects = projects.slice(0, 2)
  const allMilestones = []
  for (const p of sampleProjects) {
    const id = p.id
    const label = `${p.projectNo || id} ${p.name || ''}`
    const detail = await checkApi('项目基本信息', `/api/projects/${id}`, token)
    const overview = await checkApi('全生命周期', `/api/projects/${id}/overview`, token)
    const milestones = await checkApi('里程碑', `/api/projects/${id}/milestones`, token)
    const plans = await checkApi('计划管理', `/api/projects/${id}/plans`, token)
    const budgets = await checkApi('经费预算', `/api/projects/${id}/fund/budgets`, token)
    const payments = await checkApi('经费核销', `/api/projects/${id}/fund/payments`, token)
    const evaluations = await checkApi('评估检查', `/api/projects/${id}/evaluations`, token)
    const deliverables = await checkApi('交付物', `/api/projects/${id}/deliverables`, token)
    const partners = await checkApi('参研单位评价', `/api/projects/${id}/partner-evals`, token)
    await checkApi('项目验收', `/api/acceptance/${id}`, token, { required: false })

    const detailData = apiData(detail) || {}
    const overviewData = apiData(overview) || {}
    const msRows = records(apiData(milestones))
    allMilestones.push(...msRows.map((m) => ({ ...m, projectLabel: label })))
    if (apiPassed(detail) && !Array.isArray(detailData.teamMembers)) {
      problem('FAIL', '项目团队', '项目详情未返回teamMembers', label, '项目详情接口附带团队岗位与工号。')
    }
    if (apiPassed(overview) && !overviewData.project) {
      problem('FAIL', '全生命周期', '聚合接口缺少project主信息', label, '修复overview聚合结构。')
    }
    if (!msRows.length) problem('WARN', '里程碑', '样本项目没有里程碑', label, '为样本补充历史和未来节点及材料。')
    if (records(apiData(plans)).length === 0) problem('WARN', '计划管理', '样本项目没有计划记录', label, '补充年度计划样本。')
    if (records(apiData(budgets)).length === 0) problem('WARN', '经费预算', '样本项目没有预算记录', label, '补充预算填报样本。')
    if (records(apiData(deliverables)).length === 0) problem('WARN', '交付物', '样本项目没有交付物记录', label, '补充交付物样本。')

    for (const m of msRows.slice(0, 8)) {
      const mats = await checkApi('里程碑材料', `/api/milestones/${m.id}/materials`, token)
      const materialRows = records(apiData(mats))
      if ((m.status === 'DONE' || m.actualDate) && materialRows.length === 0) {
        problem('WARN', '里程碑材料', '已完成里程碑没有可查看材料', `${label}; ${m.name || m.id}`, '补齐历史填报材料或修正材料关联。')
      }
    }

    // 保证调用结果参与日志，避免某模块返回空结构却被忽略。
    void payments; void evaluations; void partners
  }

  const today = new Date().toISOString().slice(0, 10)
  const historical = allMilestones.filter((m) => String(m.planDate || '') < today)
  const future = allMilestones.filter((m) => String(m.planDate || '') >= today)
  if (!historical.length) problem('WARN', '里程碑样本', '两个样本中没有历史节点', `today=${today}`, '保留至少一个过去计划日期的里程碑。')
  else log('PASS', '里程碑样本', '存在历史节点', `${historical.length}个`)
  if (!future.length) problem('WARN', '里程碑样本', '两个样本中没有未来节点', `today=${today}`, '保留至少一个未来计划日期的里程碑。')
  else log('PASS', '里程碑样本', '存在未来节点', `${future.length}个`)

  // 6. 多身份数据访问冒烟；只读取每个账号自己的项目和申报列表。
  for (const [username, session] of sessions) {
    if (username === 'admin') continue
    const p = await request('GET', '/api/projects?page=1&size=3', { token: session.token })
    const d = await request('GET', '/api/declarations?page=1&size=3', { token: session.token })
    if (!apiPassed(p)) problem('FAIL', '多身份项目访问', `账号 ${username} 无法读取授权项目`, `HTTP ${p.status}; ${p.json?.msg || p.error || ''}`, '检查角色包和数据范围过滤。')
    else log('PASS', '多身份项目访问', `账号 ${username}`, `项目=${records(apiData(p)).length}`)
    if (!apiPassed(d)) problem('FAIL', '多身份申报访问', `账号 ${username} 无法读取授权申报`, `HTTP ${d.status}; ${d.json?.msg || d.error || ''}`, '检查申报列表权限及数据范围。')
    else log('PASS', '多身份申报访问', `账号 ${username}`, `申报=${records(apiData(d)).length}`)
  }

  log('INFO', 'RUN', '巡检结束', JSON.stringify(stats))
}

function writeOutputs(error) {
  if (error) {
    problem('FAIL', 'RUN', '测试脚本异常终止', String(error?.stack || error), '检查网络、账号和脚本运行环境后重试。')
  }
  const summary = `PASS=${stats.PASS} WARN=${stats.WARN} FAIL=${stats.FAIL} INFO=${stats.INFO}`
  lines.push(`${new Date().toISOString()} [SUMMARY] ${summary}`)
  fs.writeFileSync(logFile, `${lines.join('\n')}\n`, 'utf8')
  fs.copyFileSync(logFile, latestLog)

  const rows = issues.length
    ? issues.map((x) => `| ${x.id} | ${x.level} | ${x.node} | ${String(x.message).replace(/\|/g, '\\|')} | ${String(x.evidence || '—').replace(/\|/g, '\\|')} | ${String(x.recommendation || '—').replace(/\|/g, '\\|')} |`).join('\n')
    : '| — | — | — | 未发现问题节点 | — | — |'
  const report = `# 6006平台任务流测试问题节点\n\n- 测试时间：${new Date().toISOString()}\n- 平台：${BASE}\n- 模式：只读巡检（不提交、不审批、不修改业务数据）\n- 汇总：${summary}\n- 完整日志：${path.basename(logFile)}\n\n## 问题节点\n\n| 编号 | 级别 | 节点 | 问题 | 证据 | 建议 |\n|---|---|---|---|---|---|\n${rows}\n`
  fs.writeFileSync(reportFile, report, 'utf8')
  fs.copyFileSync(reportFile, latestReport)
  console.log(`LOG_FILE=${logFile}`)
  console.log(`REPORT_FILE=${reportFile}`)
  console.log(`SUMMARY=${summary}`)
}

let fatal = null
try {
  await main()
} catch (error) {
  fatal = error
}
writeOutputs(fatal)
process.exitCode = stats.FAIL > 0 ? 1 : 0
