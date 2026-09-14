import dayjs from 'dayjs'
import { calcColor, remainDays } from '@/utils/color'
import type { ColorStatus, ProjInfo } from '@/api/types'

export type DashboardQuery = {
  year?: string | number
  level?: string
  sourceChannel?: string
  orgOffice?: string
  projectType?: string
  major1?: string
  major2?: string
  unit?: string
  screen?: string
}

export type DashboardChannel = {
  id?: number
  channelName?: string
  channelCode?: string
}

export type DashboardCtx = {
  projects: ProjInfo[]
  channels?: DashboardChannel[]
  milestones?: any[]
  plans?: any[]
  deliverables?: any[]
  transforms?: any[]
  budgets?: any[]
  payments?: any[]
  changes?: any[]
  partnerEvals?: any[]
  blacklist?: any[]
}

const LEVEL_ORDER = [
  { code: 'NATIONAL', name: '国家级' },
  { code: 'LOCAL', name: '地方级' },
  { code: 'COMPANY', name: '公司级' },
] as const

const STATUS_ORDER = ['申报中', '立项中', '实施中', '验收中', '已验收', '已终止'] as const

const DELIV_TYPES: { code: string; name: string }[] = [
  { code: 'PATENT', name: '专利' },
  { code: 'PAPER', name: '论文' },
  { code: 'SOFTWARE', name: '软件著作权' },
  { code: 'STANDARD', name: '技术标准' },
  { code: 'PROTOTYPE', name: '原理样机' },
  { code: 'EQUIPMENT', name: '设备' },
  { code: 'TECH_PACKAGE', name: '成套技术成果' },
]

const TRANSFORM_STAGES = [
  { code: 'NOT_STARTED', name: '未启动' },
  { code: 'NEGOTIATING', name: '洽谈中' },
  { code: 'SIGNED', name: '已签协议' },
  { code: 'DONE', name: '已完成' },
] as const

function num(v: unknown) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function round2(v: number) {
  return Math.round(v * 100) / 100
}

function round4(v: number) {
  return Math.round(v * 10000) / 10000
}

function yearOf(d?: string | null) {
  if (!d) return null
  const y = Number(String(d).slice(0, 4))
  return Number.isFinite(y) && y > 1900 ? y : null
}

function overlapsYear(p: ProjInfo, year?: number) {
  if (!year) return true
  const sy = yearOf(p.startDate) ?? 0
  const ey = yearOf(p.endDate) ?? 9999
  return sy <= year && ey >= year
}

function durationYears(p: ProjInfo) {
  const sy = yearOf(p.startDate)
  const ey = yearOf(p.endDate)
  if (!sy || !ey) return 1
  return Math.max(1, ey - sy + 1)
}

export function stdStatus(status?: string) {
  const s = String(status || '')
  if (/终止|中止/.test(s) || s === 'TERMINATED') return '已终止'
  if (/已验收|验收完成|结题/.test(s) || ['COMPANY_ACCEPTED', 'GOV_ACCEPTED', 'FINISHED'].includes(s))
    return '已验收'
  if (/验收/.test(s) || s === 'ACCEPTING') return '验收中'
  if (/立项/.test(s) || s === 'FILING') return '立项中'
  if (/申报|草稿/.test(s) || ['DRAFT', 'DECLARING'].includes(s)) return '申报中'
  if (!s) return '实施中'
  return '实施中'
}

export function isRunningStatus(status?: string) {
  const raw = String(status || '')
  const std = stdStatus(status)
  return std === '实施中' || std === '验收中' || /进行中/.test(raw)
}

function normalizeLevel(v?: string) {
  const s = String(v || '').trim()
  if (!s) return ''
  if (s === 'NATIONAL' || s === '国家级') return 'NATIONAL'
  if (s === 'LOCAL' || s === '地方级') return 'LOCAL'
  if (s === 'COMPANY' || s === '公司级') return 'COMPANY'
  return s
}

function levelBucket(v?: string) {
  const code = normalizeLevel(v)
  return LEVEL_ORDER.some((lv) => lv.code === code) ? code : 'UNSPECIFIED'
}

function stripMajorPrefix(name?: string) {
  const s = String(name || '').trim()
  return s.replace(/^\d{2,4}-/, '') || s || '未填专业'
}

function shortUnit(name?: string) {
  return String(name || '未填单位')
    .replace('上海飞机设计研究院', '上飞院')
    .replace('上海飞机制造有限公司', '上飞公司')
    .replace('北京民用飞机技术研究中心', '北研中心')
    .replace('中国商飞总部', '总部')
}

function matchChannel(p: ProjInfo, sourceChannel: string | undefined, channels: DashboardChannel[]) {
  if (!sourceChannel) return true
  const ch = channels.find((c) => c.id === p.channelId)
  const names = [p.channelName, ch?.channelName, ch?.channelCode].filter(Boolean) as string[]
  return names.some((n) => n === sourceChannel)
}

function matchUnit(p: ProjInfo, unit?: string) {
  if (!unit) return true
  if (String(p.orgId) === String(unit) || String(p.leadOrgId) === String(unit)) return true
  const names = [p.orgName, p.leadOrgName, shortUnit(p.orgName), shortUnit(p.leadOrgName)]
  return names.some((n) => n && n === unit)
}

function applyFilters(list: ProjInfo[], q: DashboardQuery, channels: DashboardChannel[]) {
  const year = q.year ? Number(q.year) : undefined
  const level = normalizeLevel(q.level)
  return list.filter((p) => {
    if (year && !overlapsYear(p, year)) return false
    if (level && normalizeLevel(p.levelCode) !== level) return false
    if (q.sourceChannel && !matchChannel(p, String(q.sourceChannel), channels)) return false
    if (q.orgOffice && String(p.bureauOffice || '') !== String(q.orgOffice)) return false
    if (q.projectType && String(p.projectType || '') !== String(q.projectType)) return false
    if (q.major1 && String(p.major1 || '') !== String(q.major1)) return false
    if (q.major2 && String(p.major2 || '') !== String(q.major2)) return false
    if (q.unit && !matchUnit(p, String(q.unit))) return false
    return true
  })
}

function dataMode(list: ProjInfo[]) {
  const hasPlat = list.some((p) => (p.dataSource || 'PLATFORM') === 'PLATFORM')
  const hasForm = list.some((p) => p.dataSource === 'FORM_MAINT')
  if (hasPlat && hasForm) return 'mixed'
  if (hasForm && !hasPlat) return 'form-ledger'
  return 'projects'
}

function parseModelTarget(intro?: string, form?: string) {
  const text = String(intro || '')
  const m = text.match(/应用对象[：:]\s*([^\s，,；;]+)/)
  if (m?.[1]) return m[1]
  if (form === 'INSTALLED') return '型号装机'
  if (form === 'UNINSTALLED') return '型号未装机'
  return '未明确对象'
}

function rate(a: number, b: number) {
  if (!b) return 0
  return round2((a / b) * 100)
}

export function recordsOf(res: any): any[] {
  const d = res?.data
  if (Array.isArray(d)) return d
  if (Array.isArray(d?.records)) return d.records
  if (Array.isArray(d?.list)) return d.list
  return []
}

export function milestonesFromBoard(res: any): any[] {
  const d = res?.data || {}
  const boards = d.boards || d.projects || []
  const out: any[] = []
  for (const b of boards) {
    for (const m of b.milestones || []) {
      out.push({
        ...m,
        projectId: m.projectId ?? b.projectId,
        name: m.name || m.milestoneName,
      })
    }
  }
  return out
}

/** 无全局计划列表时，用里程碑看板节点作为计划办结率口径 */
export function plansFromMilestoneBoard(res: any): any[] {
  const d = res?.data || {}
  const boards = d.boards || d.projects || []
  const todos = d.todos || []
  const plans: any[] = []
  for (const b of boards) {
    for (const m of b.milestones || []) {
      const done = m.status === 'DONE' || !!m.actualDate
      plans.push({
        id: m.id,
        projectId: m.projectId ?? b.projectId,
        status: done ? 'DONE' : 'TODO',
        planType: done ? 'DONE' : 'TODO',
        dueDate: m.planDate,
        finishDate: m.actualDate,
        colorStatus: m.colorStatus,
      })
    }
  }
  for (const t of todos) {
    if (t.taskType !== 'COMPILE') continue
    plans.push({
      id: `compile-${t.projectId}`,
      projectId: t.projectId,
      status: 'TODO',
      planType: 'TODO',
      dueDate: t.planDate,
      colorStatus: t.colorStatus || 'BLUE',
    })
  }
  return plans
}

export function aggregateDashboard(query: DashboardQuery = {}, ctx: DashboardCtx) {
  const today = dayjs().format('YYYY-MM-DD')
  const year = query.year ? Number(query.year) : dayjs().year()
  const scoped = ctx.projects || []
  const channels = ctx.channels || []
  const projects = applyFilters(scoped, query, channels)
  const ids = new Set(projects.map((p) => p.id))
  const mode = dataMode(projects)

  const unitsMeta = new Map<string, { id?: number; name: string }>()
  scoped.forEach((p) => {
    const name = p.leadOrgName || p.orgName
    if (!name) return
    const key = String(p.leadOrgId || p.orgId || name)
    if (!unitsMeta.has(key)) unitsMeta.set(key, { id: p.leadOrgId || p.orgId, name })
  })
  const projectTypes = [...new Set(scoped.map((p) => p.projectType).filter(Boolean) as string[])]

  const milestones = (ctx.milestones || []).filter((x) => ids.has(x.projectId))
  const plans = (ctx.plans || []).filter((x) => ids.has(x.projectId))
  const deliverables = (ctx.deliverables || []).filter((x) => ids.has(x.projectId))
  const transforms = (ctx.transforms || []).filter((x) => x.projectId && ids.has(x.projectId))
  const budgets = (ctx.budgets || []).filter((x) => ids.has(x.projectId))
  const payments = (ctx.payments || []).filter((x) => ids.has(x.projectId))
  const changes = (ctx.changes || []).filter((x) => x.projectId && ids.has(x.projectId))
  const partnerEvals = (ctx.partnerEvals || []).filter((x) => ids.has(x.projectId))
  const blacklist = ctx.blacklist || []

  const totalFund = round2(projects.reduce((s, p) => s + num(p.totalFund), 0))
  const nationalFund = round2(projects.reduce((s, p) => s + num(p.nationalFund), 0))
  const selfFund = round2(projects.reduce((s, p) => s + num(p.selfFund), 0))
  const outsource = round2(projects.reduce((s, p) => s + num(p.outsourceAmount), 0))
  const innerFund = round2(Math.max(0, totalFund - outsource))
  const expenseTotal = round2(projects.reduce((s, p) => s + num(p.expenseTotal), 0))
  const yearBudget = round2(projects.reduce((s, p) => s + num(p.yearBudget), 0))
  const yearExpense = round2(projects.reduce((s, p) => s + num(p.yearExpense), 0))
  const running = projects.filter((p) => isRunningStatus(p.status))
  const runningFund = round2(running.reduce((s, p) => s + num(p.totalFund), 0))

  const levels: { code: string; name: string }[] = [...LEVEL_ORDER]
  if (projects.some((p) => levelBucket(p.levelCode) === 'UNSPECIFIED')) {
    levels.push({ code: 'UNSPECIFIED', name: '未明确层级' })
  }
  const byLevel = levels.map((lv) => {
    const rows = projects.filter((p) => levelBucket(p.levelCode) === lv.code)
    return {
      name: lv.name,
      level: lv.code,
      value: rows.length,
      count: rows.length,
      fund: round2(rows.reduce((s, p) => s + num(p.totalFund), 0)),
    }
  })

  const channelMap = new Map<string, { name: string; count: number; fund: number }>()
  projects.forEach((p) => {
    const ch = channels.find((c) => c.id === p.channelId)
    const name =
      mode === 'form-ledger'
        ? `${p.channelName || '未填渠道'} / ${p.projectType || '未填类型'}`
        : p.channelName || ch?.channelName || '未填渠道'
    const cur = channelMap.get(name) || { name, count: 0, fund: 0 }
    cur.count += 1
    cur.fund += num(p.totalFund)
    channelMap.set(name, cur)
  })
  const byChannel = [...channelMap.values()]
    .map((x) => ({ ...x, fund: round2(x.fund), value: x.count }))
    .sort((a, b) => b.count - a.count)
  const channelTop8 = byChannel.slice(0, 8)

  const unitMap = new Map<
    string,
    {
      name: string
      orgId?: number
      count: number
      fund: number
      red: number
      yellow: number
      blue: number
      green: number
      running: number
      accepted: number
      levels: Record<string, number>
    }
  >()
  projects.forEach((p) => {
    const name = shortUnit(p.leadOrgName || p.orgName || '未填单位')
    const key = String(p.leadOrgId || p.orgId || name)
    const cur = unitMap.get(key) || {
      name,
      orgId: p.leadOrgId || p.orgId,
      count: 0,
      fund: 0,
      red: 0,
      yellow: 0,
      blue: 0,
      green: 0,
      running: 0,
      accepted: 0,
      levels: { NATIONAL: 0, LOCAL: 0, COMPANY: 0 },
    }
    cur.count += 1
    cur.fund += num(p.totalFund)
    const color = (p.warnColor || 'BLUE') as ColorStatus
    if (color === 'RED') cur.red += 1
    else if (color === 'YELLOW') cur.yellow += 1
    else if (color === 'GREEN') cur.green += 1
    else cur.blue += 1
    if (isRunningStatus(p.status)) cur.running += 1
    if (stdStatus(p.status) === '已验收') cur.accepted += 1
    const level = levelBucket(p.levelCode)
    cur.levels[level] = (cur.levels[level] || 0) + 1
    unitMap.set(key, cur)
  })
  const byUnit = [...unitMap.values()]
    .map((x) => ({ ...x, fund: round2(x.fund) }))
    .sort((a, b) => b.count - a.count)
  const unitLevelMatrix = {
    units: byUnit.map((u) => u.name),
    series: levels.map((lv) => ({
      name: lv.name,
      level: lv.code,
      data: byUnit.map((u) => u.levels[lv.code] || 0),
    })),
    orgIds: byUnit.map((u) => u.orgId),
  }

  const thisYear = dayjs().year()
  const fundsTrend = Array.from({ length: 5 }, (_, i) => thisYear - 4 + i).map((y) => {
    let budget = 0
    let expense = 0
    const bHit = budgets.filter((b) => b.year === y)
    const pHit = payments.filter((pay) => yearOf(pay.occurDate) === y)
    if (bHit.length || pHit.length) {
      budget = bHit.reduce((s, b) => s + num(b.amount), 0)
      expense = pHit.reduce((s, pay) => s + num(pay.amount), 0)
    }
    if (!budget && !expense) {
      projects.forEach((p) => {
        if (!overlapsYear(p, y)) return
        if (y === thisYear) {
          budget += num(p.yearBudget)
          expense += num(p.yearExpense)
        } else {
          const span = durationYears(p)
          budget += num(p.totalFund) / span
          expense += num(p.expenseTotal) / span
        }
      })
    }
    budget = round2(budget)
    expense = round2(expense)
    return { year: y, budget, expense, rate: rate(expense, budget) }
  })

  const statusDist = STATUS_ORDER.map((name) => ({
    name,
    value: projects.filter((p) => stdStatus(p.status) === name).length,
  }))

  const majorMap = new Map<
    string,
    { name: string; major1: string; count: number; fund: number; children: Map<string, number> }
  >()
  projects.forEach((p) => {
    const major1 = p.major1 && /^\d{2}-/.test(p.major1) ? p.major1 : '未填专业'
    const cur = majorMap.get(major1) || {
      name: stripMajorPrefix(major1),
      major1,
      count: 0,
      fund: 0,
      children: new Map(),
    }
    cur.count += 1
    cur.fund += num(p.totalFund)
    if (p.major2) cur.children.set(p.major2, (cur.children.get(p.major2) || 0) + 1)
    majorMap.set(major1, cur)
  })
  const byMajor1 = [...majorMap.values()]
    .map((x) => ({
      name: x.name,
      major1: x.major1,
      count: x.count,
      value: round2(x.fund),
      fund: round2(x.fund),
      children: [...x.children.entries()].map(([name, count]) => ({ name, count })),
    }))
    .sort((a, b) => b.fund - a.fund)

  const delivCount = (code: string) => deliverables.filter((d) => d.deliverType === code)
  let delivByType = DELIV_TYPES.map((t) => {
    const rows = delivCount(t.code)
    const done = rows.filter((d) => d.status === 'DELIVERED' || !!d.deliverDate).length
    return { name: t.name, code: t.code, delivered: done, pending: rows.length - done, total: rows.length }
  })
  if (!deliverables.length && transforms.length) {
    const model = transforms.filter((t) => t.transformWay === 'MODEL')
    const market = transforms.filter((t) => t.transformWay === 'MARKET')
    delivByType = [
      {
        name: '向型号转化',
        code: 'MODEL',
        delivered: model.filter((t) => t.status === 'DONE').length,
        pending: model.filter((t) => t.status !== 'DONE').length,
        total: model.length,
      },
      {
        name: '向市场转化',
        code: 'MARKET',
        delivered: market.filter((t) => t.status === 'DONE').length,
        pending: market.filter((t) => t.status !== 'DONE').length,
        total: market.length,
      },
    ]
  }

  const transform = TRANSFORM_STAGES.map((st) => ({
    name: st.name,
    code: st.code,
    value: transforms.filter((t) => t.status === st.code).length,
  }))
  const overduePkgs = transforms.filter((t) => {
    const c = t.colorStatus || calcColor(t.planDate, t.status === 'DONE' || !!t.actualDate)
    return c === 'RED'
  }).length
  const transformSummary = {
    total: transforms.length,
    done: transforms.filter((t) => t.status === 'DONE').length,
    progressing: transforms.filter((t) => t.status === 'NEGOTIATING' || t.status === 'SIGNED').length,
    notStarted: transforms.filter((t) => t.status === 'NOT_STARTED').length,
    overdue: overduePkgs,
  }

  const modelMap = new Map<string, { name: string; done: number; doing: number }>()
  transforms
    .filter((t) => t.transformWay === 'MODEL')
    .forEach((t) => {
      const name = parseModelTarget(t.intro, t.transformForm)
      const cur = modelMap.get(name) || { name, done: 0, doing: 0 }
      if (t.status === 'DONE') cur.done += 1
      else cur.doing += 1
      modelMap.set(name, cur)
    })
  const modelTransform = [...modelMap.values()]
    .sort((a, b) => b.done + b.doing - (a.done + a.doing))
    .slice(0, 6)

  const planFinished = plans.filter((p) => p.status === 'DONE' || p.planType === 'DONE' || !!p.finishDate)
  const planOpen = plans.filter((p) => !planFinished.includes(p))
  const planColors = { red: 0, yellow: 0, blue: 0, green: planFinished.length }
  planOpen.forEach((p) => {
    const c = p.colorStatus || calcColor(p.dueDate, false)
    if (c === 'RED') planColors.red += 1
    else if (c === 'YELLOW') planColors.yellow += 1
    else planColors.blue += 1
  })
  const planStats = {
    total: plans.length,
    done: planFinished.length,
    todo: planOpen.length,
    finishRate: rate(planFinished.length, plans.length),
    colors: planColors,
    cmosSyncAt: null as string | null,
    cmosText: 'CMOS接口待联调',
  }

  type RiskItem = {
    id: string
    projectId: number
    projectName: string
    type: string
    title: string
    dueDate?: string
    remain: number
    color: 'RED' | 'YELLOW'
  }
  const risks: RiskItem[] = []
  const nameOf = (id: number) => projects.find((p) => p.id === id)?.name || ''
  milestones.forEach((m) => {
    const done = m.status === 'DONE' || !!m.actualDate
    const c = m.colorStatus || calcColor(m.planDate, done)
    if (c === 'RED' || c === 'YELLOW') {
      risks.push({
        id: `ms-${m.id}`,
        projectId: m.projectId,
        projectName: nameOf(m.projectId),
        type: '里程碑',
        title: m.name,
        dueDate: m.planDate,
        remain: remainDays(m.planDate) ?? 0,
        color: c,
      })
    }
  })
  deliverables.forEach((d) => {
    const done = d.status === 'DELIVERED' || !!d.deliverDate
    const c = d.colorStatus || calcColor(d.dueDate, done)
    if (c === 'RED') {
      risks.push({
        id: `dv-${d.id}`,
        projectId: d.projectId,
        projectName: nameOf(d.projectId),
        type: '交付物',
        title: d.name,
        dueDate: d.dueDate,
        remain: remainDays(d.dueDate) ?? 0,
        color: 'RED',
      })
    }
  })
  if (mode === 'form-ledger' && !milestones.length) {
    projects.forEach((p) => {
      if (p.warnColor === 'RED' || p.warnColor === 'YELLOW') {
        risks.push({
          id: `pj-${p.id}`,
          projectId: p.id,
          projectName: p.name,
          type: '台账项目',
          title: p.warnColor === 'RED' ? '项目逾期' : '项目临期',
          dueDate: p.endDate,
          remain: remainDays(p.endDate) ?? 0,
          color: p.warnColor,
        })
      }
    })
  }
  risks.sort((a, b) => {
    if (a.color !== b.color) return a.color === 'RED' ? -1 : 1
    return a.remain - b.remain
  })

  const redProjectIds = new Set(risks.filter((r) => r.color === 'RED').map((r) => r.projectId))
  const packageDone = transformSummary.done
  const deliveredCount = deliverables.filter((d) => d.status === 'DELIVERED' || !!d.deliverDate).length
  const blacklistCount = blacklist.length || partnerEvals.filter((x: any) => x.grade === 'FAIL').length
  const approvingCount = changes.filter((c) =>
    ['APPROVING', 'SUBMITTED', 'HQ_AUDIT', 'UNIT_AUDIT'].includes(String(c.status || '')),
  ).length

  const kpis = {
    projectCount: projects.length,
    runningCount: running.length,
    totalFund,
    totalFundYi: round4(totalFund / 10000),
    nationalFund,
    selfFund,
    innerFund,
    yearBudget,
    yearExpense,
    execRateTotal: rate(expenseTotal, totalFund),
    execRateYear: rate(yearExpense, yearBudget),
    overdueCount: redProjectIds.size,
    packageCount: transforms.length,
    packageDone,
    planFinishRate: planStats.finishRate,
    planTodo: planStats.todo,
    deliveredCount,
    blacklistCount,
    approvingCount,
  }

  return {
    today,
    updatedAt: dayjs().format('YYYY-MM-DD HH:mm:ss'),
    year,
    dataMode: mode,
    kpis,
    byLevel,
    byUnit,
    unitLevelMatrix,
    byChannel,
    channelTop8,
    fundsTrend,
    statusDist,
    byMajor1,
    delivByType,
    transform,
    transformSummary,
    modelTransform,
    planStats,
    fundStructure: {
      total: totalFund,
      national: nationalFund,
      self: selfFund,
      inner: innerFund,
      running: runningFund,
    },
    risks: risks.slice(0, 12),
    filterMeta: {
      units: [...unitsMeta.values()],
      projectTypes,
    },
    projectCount: kpis.projectCount,
    runningCount: kpis.runningCount,
    overdueCount: kpis.overdueCount,
    totalFund,
    yearBudget,
    yearExpense,
    deliverableCount: deliverables.length,
    transformCount: transforms.length,
    levelDist: byLevel.map((x) => ({ name: x.name, value: x.count })),
    channelDist: byChannel.map((x) => ({ name: x.name, value: x.count })),
    fundTrend: fundsTrend.map((x) => ({ month: String(x.year), budget: x.budget, expense: x.expense })),
  }
}
