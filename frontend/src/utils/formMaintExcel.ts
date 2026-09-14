/**
 * 表单维护 Excel 解析 / 预校验 / 导出
 * 对齐《表格导入预校验功能说明》
 */
import * as XLSX from 'xlsx'
import type { Font, Borders, Alignment } from 'exceljs'
import type { LevelCode, ProjInfo } from '@/api/types'
import majorConfig from '@/config/major1-major2.json'

export type ProjectDataSource = 'PLATFORM' | 'FORM_MAINT'
export type PreviewAction = 'add' | 'update' | 'keep' | 'skip' | 'delete'
export type IssueCategory = '必填' | '经费' | '数值' | '日期' | '字典' | '专业' | '其他'

export const DATA_SOURCE_TEXT: Record<ProjectDataSource | 'ALL', string> = {
  ALL: '全部',
  PLATFORM: '平台同步',
  FORM_MAINT: '表单维护导入',
}

export const ACTION_TEXT: Record<PreviewAction, string> = {
  add: '新增',
  update: '更新',
  keep: '保持',
  skip: '跳过',
  delete: '删除',
}

const LEVEL_FROM_TEXT: Record<string, LevelCode> = {
  国家级: 'NATIONAL',
  地方级: 'LOCAL',
  公司级: 'COMPANY',
  NATIONAL: 'NATIONAL',
  LOCAL: 'LOCAL',
  COMPANY: 'COMPANY',
}

const LEVEL_TO_TEXT: Record<string, string> = {
  NATIONAL: '国家级',
  LOCAL: '地方级',
  COMPANY: '公司级',
}

const STATUS_FROM_TEXT: Record<string, string> = {
  草稿: 'DRAFT',
  申报中: 'DECLARING',
  立项中: 'FILING',
  实施中: 'IMPLEMENTING',
  进行中: 'IMPLEMENTING',
  已延期: 'DELAYED',
  验收中: 'ACCEPTING',
  已完成: 'FINISHED',
  已终止: 'TERMINATED',
}

const STATUS_TO_TEXT: Record<string, string> = {
  DRAFT: '草稿',
  DECLARING: '申报中',
  FILING: '立项中',
  IMPLEMENTING: '实施中',
  DELAYED: '实施中',
  ACCEPTING: '验收中',
  COMPANY_ACCEPTED: '已完成',
  GOV_ACCEPTED: '已完成',
  FINISHED: '已完成',
  TERMINATED: '已终止',
}

/** 标准表头（含别名）→ 内部字段 */
const HEADER_ALIASES: Record<string, string[]> = {
  seqNo: ['序号'],
  level: ['级别'],
  channelName: ['项目来源/渠道', '项目来源／渠道', '渠道', '项目来源'],
  bureauOffice: ['司局/处室', '司局／处室', '司局处室', '管理司局/处室', '管理司局／处室'],
  projectType: ['项目类型'],
  major1: ['一级专业'],
  major2: ['二级专业'],
  name: ['项目名称'],
  manageOrgName: ['管理/需求单位', '管理／需求单位', '需求单位', '管理单位'],
  leadOrgName: ['责任单位'],
  status: ['项目状态'],
  acceptStatus: ['验收状态'],
  ownerName: ['负责人', '中国商飞内部负责人'],
  filingYm: ['项目立项年月', '立项年月'],
  startYm: ['项目开始年月', '开始年月'],
  endYm: ['项目结束年月', '结束年月'],
  period: ['项目周期'],
  totalFund: ['总经费（万元）', '总经费(万元)', '总经费'],
  nationalFund: ['国拨经费（万元）', '国拨经费(万元)', '国拨经费'],
  nationalFundInner: [
    '其中商飞内部单位国拨经费（万元）',
    '其中商飞内部单位国拨经费(万元)',
  ],
  selfFund: ['自筹经费（万元）', '自筹经费(万元)', '自筹经费'],
  selfFundInner: [
    '其中商飞内部单位自筹经费（万元）',
    '其中商飞内部单位自筹经费(万元)',
  ],
  expenseTotal: ['累计支出（万元）', '累计支出(万元)', '累计支出'],
  yearBudget: ['2026年预算（万元）', '2026年预算(万元)', '年度预算（万元）'],
  yearExec: ['2026年实际执行经费（万元）', '2026年实际执行经费(万元)'],
  yearExecRate: ['2026年预算执行率', '预算执行率'],
  finishExecFund: ['已结题项目实际执行经费（万元）', '已结题项目实际执行经费(万元)'],
  finishNationalExec: ['已结题项目国拨经费执行（万元）', '已结题项目国拨经费执行(万元)'],
  finishSelfExec: [
    '已结题项目自筹经费执行（万元）',
    '已结题项目自筹经费执行(万元)',
    '已结题项目国自筹经费执行（万元）',
  ],
  finishExecRate: ['已结题项目经费执行率', '执行率'],
  achvCount: ['产生成果数量'],
  achvName: ['生成成果名称', '产生成果名称'],
  transformCount: ['已转化数量'],
  transformName: ['转化成果名称'],
  transformYear: ['转化年份', '转化年月'],
  transformModel: ['转化型号'],
  techReadyCount: ['技术成熟度数量', '技术储备数量'],
  reserveName: ['储备成果名称'],
  expectTransformYear: ['预计转化年度'],
  remark: ['备注'],
}

const REQUIRED_HEADERS = [
  'level',
  'channelName',
  'projectType',
  'major1',
  'major2',
  'name',
  'leadOrgName',
  'status',
  'totalFund',
] as const

const REQUIRED_LABEL: Record<(typeof REQUIRED_HEADERS)[number], string> = {
  level: '级别',
  channelName: '项目来源/渠道',
  projectType: '项目类型',
  major1: '一级专业',
  major2: '二级专业',
  name: '项目名称',
  leadOrgName: '责任单位',
  status: '项目状态',
  totalFund: '总经费（万元）',
}

export const FORM_MAINT_HEADERS = [
  '序号',
  '级别',
  '项目来源/渠道',
  '项目类型',
  '一级专业',
  '二级专业',
  '项目名称',
  '管理/需求单位',
  '责任单位',
  '项目状态',
  '验收状态',
  '负责人',
  '项目立项年月',
  '项目开始年月',
  '项目结束年月',
  '项目周期',
  '总经费（万元）',
  '国拨经费（万元）',
  '其中商飞内部单位国拨经费（万元）',
  '自筹经费（万元）',
  '其中商飞内部单位自筹经费（万元）',
  '累计支出（万元）',
  '2026年预算（万元）',
  '已结题项目实际执行经费（万元）',
  '已结题项目国拨经费执行（万元）',
  '已结题项目自筹经费执行（万元）',
  '已结题项目经费执行率',
  '产生成果数量',
  '生成成果名称',
  '已转化数量',
  '转化成果名称',
  '转化年份',
  '转化型号',
  '技术成熟度数量',
  '储备成果名称',
  '预计转化年度',
  '备注',
] as const

const MAJOR1_SET = new Set(majorConfig.major1 as string[])
const MAJOR2_MAP = majorConfig.major2ByMajor1 as Record<string, string[]>

export interface FormMaintIssue {
  category: IssueCategory
  message: string
  forceable?: boolean
}

export interface FormMaintRow extends ProjInfo {
  dataSource?: ProjectDataSource
  ownerName?: string
  acceptStatus?: string
  filingYm?: string
  nationalFundInner?: number
  selfFundInner?: number
  yearExec?: number
  yearExecRate?: string
  finishExecFund?: number
  finishNationalExec?: number
  finishSelfExec?: number
  finishExecRate?: string
  achvCount?: number
  achvName?: string
  transformCount?: number
  transformName?: string
  transformYear?: string
  transformModel?: string
  techReadyCount?: number
  reserveName?: string
  expectTransformYear?: string
  remark?: string
  period?: string
  seqNo?: string | number
  excelRow?: number
  uniqueKey?: string
  action?: PreviewAction
  issues?: FormMaintIssue[]
  warnings?: string[]
  fieldDiffs?: { field: string; before: string; after: string }[]
  validateOk?: boolean
  validateMsg?: string
  forceable?: boolean
  pendingChannel?: boolean
  pendingType?: boolean
  /** 原始级别文本（未映射时保留） */
  levelRaw?: string
  startYm?: string
  endYm?: string
}

export interface ImportPreviewResult {
  rows: FormMaintRow[]
  sheetName: string
  fileName?: string
  mode: 'merge' | 'replace'
  stats: {
    parsed: number
    added: number
    updated: number
    kept: number
    skipped: number
    deleted: number
    issueCount: number
  }
  pendingChannels: { channelName: string; projectType?: string; excelRows: number[] }[]
  pendingPeople: { name: string; employeeNo?: string; excelRows: number[]; projects: string[] }[]
  batchStatus: '待修正' | '待确认'
  error?: string
}

function normalizeHeader(s: string) {
  return String(s || '')
    .replace(/\s+/g, '')
    .replace(/\(/g, '（')
    .replace(/\)/g, '）')
    .replace(/\//g, '/')
    .replace(/／/g, '/')
    .trim()
}

function cellVal(v: unknown): string | undefined {
  if (v == null || v === '') return undefined
  if (typeof v === 'number' && Number.isFinite(v)) return String(v)
  const s = String(v).trim()
  return s || undefined
}

function parseNumber(v: unknown): { value?: number; rawInvalid?: boolean } {
  if (v == null || v === '') return {}
  if (typeof v === 'number' && Number.isFinite(v)) {
    return { value: Math.round(v * 100) / 100 }
  }
  let s = String(v).trim()
  if (!s) return {}
  s = s.replace(/,/g, '').replace(/万元/g, '').replace(/%$/g, '').trim()
  const n = Number(s)
  if (!Number.isFinite(n)) return { rawInvalid: true }
  return { value: Math.round(n * 100) / 100 }
}

function ymNorm(ym?: string): string | undefined {
  if (!ym) return undefined
  const s = String(ym).trim().replace(/年|月/g, '-').replace(/\./g, '-')
  const m = s.match(/(\d{4})-(\d{1,2})/)
  if (!m) return s.slice(0, 7)
  return `${m[1]}-${m[2].padStart(2, '0')}`
}

function ymToDate(ym?: string): string | undefined {
  const n = ymNorm(ym)
  if (!n || !/^\d{4}-\d{2}/.test(n)) return undefined
  return `${n.slice(0, 7)}-01`
}

function dateToYm(d?: string): string {
  if (!d) return ''
  const m = String(d).match(/(\d{4})-(\d{2})/)
  return m ? `${m[1]}.${m[2]}` : String(d)
}

function normalizeKeyPart(s: string) {
  return s.replace(/\s+/g, '').toLowerCase()
}

function resolveMajor1(raw?: string): string | undefined {
  if (!raw) return undefined
  const t = raw.trim()
  if (MAJOR1_SET.has(t)) return t
  const byCode = [...MAJOR1_SET].find((m) => m.startsWith(t.slice(0, 2) + '-') || m === t || m.includes(t))
  return byCode
}

function resolveMajor2(major1: string | undefined, raw?: string): string | undefined {
  if (!raw) return undefined
  const t = raw.trim()
  const list = major1 ? MAJOR2_MAP[major1] || [] : Object.values(MAJOR2_MAP).flat()
  if (list.includes(t)) return t
  const hit = list.find((m) => m.startsWith(t.slice(0, 4)) || m.includes(t) || t.includes(m))
  return hit
}

function buildUniqueKey(row: { seqNo?: string | number; name?: string; channelName?: string; leadOrgName?: string }) {
  const seq = cellVal(row.seqNo)
  if (seq) return normalizeKeyPart(seq)
  const name = cellVal(row.name)
  if (!name) return ''
  return normalizeKeyPart(`${name}|${row.channelName || ''}|${row.leadOrgName || ''}`)
}

function existingUniqueKey(p: ProjInfo & Record<string, any>) {
  // 无独立序号字段时，用名称+渠道+责任单位
  return normalizeKeyPart(`${p.name || ''}|${p.channelName || ''}|${p.leadOrgName || p.orgName || ''}`)
}

function findHeaderRow(matrix: any[][]): { headerIdx: number; colMap: Record<string, number> } | null {
  for (let r = 0; r < Math.min(matrix.length, 30); r++) {
    const row = matrix[r] || []
    const texts = row.map((c: any) => normalizeHeader(String(c ?? '')))
    const hasType = texts.some((t) => t.includes('项目类型'))
    const hasName = texts.some((t) => t.includes('项目名称'))
    if (!hasType || !hasName) continue
    const colMap: Record<string, number> = {}
    Object.entries(HEADER_ALIASES).forEach(([field, aliases]) => {
      const idx = texts.findIndex((t) =>
        aliases.some((a) => t === normalizeHeader(a) || t.includes(normalizeHeader(a))),
      )
      if (idx >= 0) colMap[field] = idx
    })
    if (colMap.projectType != null && colMap.name != null) {
      return { headerIdx: r, colMap }
    }
  }
  return null
}

function pickSheet(wb: XLSX.WorkBook): { name: string; matrix: any[][] } {
  const preferred = ['总表', '预先研究项目信息', '预研项目信息', 'Sheet1']
  const names = wb.SheetNames
  const scored = names.map((name) => {
    const sheet = wb.Sheets[name]
    const matrix = XLSX.utils.sheet_to_json<any[]>(sheet, { header: 1, defval: '' }) as any[][]
    const nonEmpty = matrix.some((r) => (r || []).some((c) => c !== '' && c != null))
    let score = 0
    if (preferred.includes(name)) score += 100
    if (name.includes('总表') || name.includes('汇总') || name.includes('台账')) score += 50
    if (nonEmpty) score += 10
    if (findHeaderRow(matrix)) score += 20
    return { name, matrix, score, nonEmpty }
  })
  scored.sort((a, b) => b.score - a.score)
  const best = scored.find((s) => s.nonEmpty && findHeaderRow(s.matrix)) || scored[0]
  return { name: best.name, matrix: best.matrix }
}

function getCell(row: any[], colMap: Record<string, number>, field: string) {
  const idx = colMap[field]
  if (idx == null) return undefined
  return row[idx]
}

function isInstructionRow(row: any[], colMap: Record<string, number>) {
  const name = cellVal(getCell(row, colMap, 'name'))
  const type = cellVal(getCell(row, colMap, 'projectType'))
  const text = `${name || ''}${type || ''}`
  return /填写说明|示例|样例|表头/.test(text)
}

function isEmptyDataRow(row: any[], colMap: Record<string, number>) {
  return Object.keys(colMap).every((f) => {
    const v = getCell(row, colMap, f)
    return v == null || v === ''
  })
}

function validateRow(
  row: FormMaintRow,
  channelNames: Set<string>,
): { issues: FormMaintIssue[]; warnings: string[]; pendingChannel: boolean; pendingType: boolean } {
  const issues: FormMaintIssue[] = []
  const warnings: string[] = []
  let pendingChannel = false
  let pendingType = false

  const requiredChecks: Array<[keyof typeof REQUIRED_LABEL, unknown]> = [
    ['level', row.levelCode ? LEVEL_TO_TEXT[row.levelCode] : undefined],
    ['channelName', row.channelName],
    ['projectType', row.projectType],
    ['major1', row.major1],
    ['major2', row.major2],
    ['name', row.name],
    ['leadOrgName', row.leadOrgName],
    ['status', row.status],
    ['totalFund', row.totalFund],
  ]
  for (const [key, val] of requiredChecks) {
    if (val == null || val === '') {
      issues.push({ category: '必填', message: `${REQUIRED_LABEL[key]}必填`, forceable: key !== 'name' })
    }
  }

  // 级别枚举
  if (row.levelRaw && !row.levelCode) {
    issues.push({ category: '字典', message: '级别只能是国家级、地方级或公司级', forceable: true })
  }

  // 专业
  if (row.major1) {
    if (!MAJOR1_SET.has(row.major1)) {
      issues.push({ category: '专业', message: `一级专业「${row.major1}」不在专业字典`, forceable: true })
    }
  }
  if (row.major2) {
    const list = row.major1 ? MAJOR2_MAP[row.major1] || [] : []
    if (row.major1 && MAJOR1_SET.has(row.major1) && !list.includes(row.major2)) {
      issues.push({
        category: '专业',
        message: `二级专业「${row.major2}」不属于一级专业「${row.major1}」`,
        forceable: true,
      })
    } else if (!Object.values(MAJOR2_MAP).flat().includes(row.major2)) {
      issues.push({ category: '专业', message: `二级专业「${row.major2}」不在专业字典`, forceable: true })
    }
  }

  // 经费
  if (row.totalFund != null) {
    if (!(row.totalFund > 0)) {
      issues.push({ category: '经费', message: '总经费必须大于0', forceable: true })
    }
    const n = Number(row.nationalFund || 0)
    const s = Number(row.selfFund || 0)
    const t = Number(row.totalFund || 0)
    const diff = Math.round((t - (n + s)) * 100) / 100
    if (Math.abs(diff) > 0.01) {
      const msg =
        diff > 0
          ? `总经费比国拨与自筹合计多 ${diff} 万元`
          : `国拨与自筹合计比总经费多 ${Math.abs(diff)} 万元`
      issues.push({ category: '经费', message: msg, forceable: true })
    }
  }
  if (
    row.nationalFundInner != null &&
    row.nationalFund != null &&
    row.nationalFundInner > row.nationalFund + 0.01
  ) {
    issues.push({ category: '经费', message: '商飞内部单位国拨经费不得大于国拨经费', forceable: true })
  }
  if (row.selfFundInner != null && row.selfFund != null && row.selfFundInner > row.selfFund + 0.01) {
    issues.push({ category: '经费', message: '商飞内部单位自筹经费不得大于自筹经费', forceable: true })
  }
  if (row.yearBudget != null && row.yearExec != null && !row.yearExecRate) {
    warnings.push('建议补充2026年预算执行率')
  }

  // 日期
  const start = ymNorm(row.startYm || dateToYm(row.startDate))
  const end = ymNorm(row.endYm || dateToYm(row.endDate))
  if (start && end && start > end) {
    issues.push({ category: '日期', message: '项目开始年月不得晚于结束年月', forceable: true })
  }

  // 渠道 / 类型（待新增不阻断）
  if (row.channelName && channelNames.size && !channelNames.has(row.channelName)) {
    pendingChannel = true
    warnings.push(`渠道「${row.channelName}」不在字典，确认入库时需同意写入`)
  }
  if (row.projectType) {
    // 无独立类型字典时：有渠道即可；仅作提示
    pendingType = false
  }

  return { issues, warnings, pendingChannel, pendingType }
}

// （保留导出辅助）

function parseDataRow(
  row: any[],
  colMap: Record<string, number>,
  excelRow: number,
  numInvalid: string[],
): FormMaintRow {
  const levelRaw = cellVal(getCell(row, colMap, 'level'))
  const statusRaw = cellVal(getCell(row, colMap, 'status'))
  const major1Raw = cellVal(getCell(row, colMap, 'major1'))
  const major2Raw = cellVal(getCell(row, colMap, 'major2'))
  const major1 = resolveMajor1(major1Raw) || major1Raw
  const major2 = resolveMajor2(major1 && MAJOR1_SET.has(major1) ? major1 : undefined, major2Raw) || major2Raw

  const takeNum = (field: string) => {
    const raw = getCell(row, colMap, field)
    const { value, rawInvalid } = parseNumber(raw)
    if (rawInvalid) numInvalid.push(field)
    return value
  }

  const startYm = cellVal(getCell(row, colMap, 'startYm'))
  const endYm = cellVal(getCell(row, colMap, 'endYm'))
  const filingYm = cellVal(getCell(row, colMap, 'filingYm'))
  const ownerName = cellVal(getCell(row, colMap, 'ownerName'))
  const name = cellVal(getCell(row, colMap, 'name')) || ''

  const out: FormMaintRow & { levelRaw?: string; startYm?: string; endYm?: string } = {
    id: 0,
    projectNo: '',
    seqNo: cellVal(getCell(row, colMap, 'seqNo')),
    excelRow,
    name,
    levelRaw,
    levelCode: levelRaw ? LEVEL_FROM_TEXT[levelRaw] : undefined,
    channelName: cellVal(getCell(row, colMap, 'channelName')),
    bureauOffice: cellVal(getCell(row, colMap, 'bureauOffice')),
    projectType: cellVal(getCell(row, colMap, 'projectType')),
    major1,
    major2,
    manageOrgName: cellVal(getCell(row, colMap, 'manageOrgName')),
    leadOrgName: cellVal(getCell(row, colMap, 'leadOrgName')),
    orgName: cellVal(getCell(row, colMap, 'leadOrgName')),
    status: statusRaw ? STATUS_FROM_TEXT[statusRaw] || statusRaw : undefined,
    acceptStatus: cellVal(getCell(row, colMap, 'acceptStatus')) || '未验收',
    ownerName,
    filingYm,
    startYm,
    endYm,
    startDate: ymToDate(startYm),
    endDate: ymToDate(endYm),
    period: cellVal(getCell(row, colMap, 'period')),
    totalFund: takeNum('totalFund'),
    nationalFund: takeNum('nationalFund'),
    nationalFundInner: takeNum('nationalFundInner'),
    selfFund: takeNum('selfFund'),
    selfFundInner: takeNum('selfFundInner'),
    expenseTotal: takeNum('expenseTotal'),
    yearBudget: takeNum('yearBudget'),
    yearExec: takeNum('yearExec'),
    yearExecRate: cellVal(getCell(row, colMap, 'yearExecRate')),
    finishExecFund: takeNum('finishExecFund'),
    finishNationalExec: takeNum('finishNationalExec'),
    finishSelfExec: takeNum('finishSelfExec'),
    finishExecRate: cellVal(getCell(row, colMap, 'finishExecRate')),
    achvCount: takeNum('achvCount'),
    achvName: cellVal(getCell(row, colMap, 'achvName')),
    transformCount: takeNum('transformCount'),
    transformName: cellVal(getCell(row, colMap, 'transformName')),
    transformYear: cellVal(getCell(row, colMap, 'transformYear')),
    transformModel: cellVal(getCell(row, colMap, 'transformModel')),
    techReadyCount: takeNum('techReadyCount'),
    reserveName: cellVal(getCell(row, colMap, 'reserveName')),
    expectTransformYear: cellVal(getCell(row, colMap, 'expectTransformYear')),
    remark: cellVal(getCell(row, colMap, 'remark')),
    dataSource: 'FORM_MAINT',
    warnColor: 'BLUE',
    createByName: ownerName,
    teamMembers: ownerName
      ? [{ groupCode: 'TECH', roleCode: 'PROJECT_LEADER', roleName: '项目负责人', userName: ownerName }]
      : undefined,
  }
  return out
}

function compareFields(before: ProjInfo, after: FormMaintRow) {
  const fields: Array<[string, string | number | undefined | null, string | number | undefined | null]> = [
    ['项目名称', before.name, after.name],
    ['级别', before.levelCode, after.levelCode],
    ['渠道', before.channelName, after.channelName],
    ['项目类型', before.projectType, after.projectType],
    ['一级专业', before.major1, after.major1],
    ['二级专业', before.major2, after.major2],
    ['责任单位', before.leadOrgName || before.orgName, after.leadOrgName],
    ['项目状态', before.status, after.status],
    ['总经费', before.totalFund, after.totalFund],
    ['国拨经费', before.nationalFund, after.nationalFund],
    ['自筹经费', before.selfFund, after.selfFund],
    ['负责人', (before as any).ownerName || before.createByName, after.ownerName],
  ]
  const diffs: { field: string; before: string; after: string }[] = []
  for (const [label, a, b] of fields) {
    const sa = a == null ? '' : String(a)
    const sb = b == null ? '' : String(b)
    if (sa !== sb) diffs.push({ field: label, before: sa || '—', after: sb || '—' })
  }
  return diffs
}

function parsePeople(owner?: string) {
  if (!owner) return [] as { name: string; employeeNo?: string }[]
  return owner
    .split(/[、,，;；/|]/)
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => {
      const m = p.match(/^(.+?)[（(](\d{6})[）)]$/) || p.match(/(\d{6})/)
      if (m && m[2]) return { name: (m[1] || p.replace(m[2], '')).trim(), employeeNo: m[2] }
      if (m && m[1] && /^\d{6}$/.test(m[1])) {
        return { name: p.replace(m[1], '').trim() || p, employeeNo: m[1] }
      }
      return { name: p }
    })
}

export function runImportPreview(
  buf: ArrayBuffer,
  existing: ProjInfo[],
  opts: {
    mode: 'merge' | 'replace'
    fileName?: string
    fileSize?: number
    channelNames?: string[]
    knownPeople?: string[]
  },
): ImportPreviewResult {
  if (opts.fileSize != null && opts.fileSize > 40 * 1024 * 1024) {
    throw new Error('上传文件超过 40MB 限制')
  }
  const wb = XLSX.read(buf, { type: 'array' })
  if (wb.SheetNames.length > 100) throw new Error('工作表数量超过 100 个，已拒绝解析')

  const { name: sheetName, matrix } = pickSheet(wb)
  if (matrix.length > 20000) throw new Error('单个工作表行数超过 20000，已拒绝解析')
  if ((matrix[0] || []).length > 120) throw new Error('单个工作表列数超过 120，已拒绝解析')

  const header = findHeaderRow(matrix)
  if (!header) {
    throw new Error('未识别到表头（需同时包含「项目类型」和「项目名称」），请检查模板')
  }
  const missingRequired = REQUIRED_HEADERS.filter((h) => header.colMap[h] == null)
  if (missingRequired.length) {
    throw new Error(
      `工作表缺少必要列：${missingRequired.map((h) => REQUIRED_LABEL[h]).join('、')}`,
    )
  }

  // 数据起始行：表头下一行若已有类型+名称则直接开始，否则再跳一行子表头
  let dataStart = header.headerIdx + 1
  const next = matrix[dataStart] || []
  const nextType = cellVal(getCell(next, header.colMap, 'projectType'))
  const nextName = cellVal(getCell(next, header.colMap, 'name'))
  if (!nextType && !nextName) dataStart += 1

  const channelSet = new Set(opts.channelNames || [])
  const knownPeople = new Set((opts.knownPeople || []).map((n) => n.trim()).filter(Boolean))
  const existingMap = new Map<string, ProjInfo>()
  existing.forEach((p) => existingMap.set(existingUniqueKey(p), p))

  const rows: FormMaintRow[] = []
  const seenKeys = new Map<string, number>()
  const pendingChannelMap = new Map<string, { channelName: string; projectType?: string; excelRows: number[] }>()
  const pendingPeopleMap = new Map<
    string,
    { name: string; employeeNo?: string; excelRows: number[]; projects: string[] }
  >()

  for (let i = dataStart; i < matrix.length; i++) {
    const raw = matrix[i] || []
    if (isEmptyDataRow(raw, header.colMap)) continue
    if (isInstructionRow(raw, header.colMap)) continue
    const excelRow = i + 1
    const numInvalid: string[] = []
    const row = parseDataRow(raw, header.colMap, excelRow, numInvalid) as FormMaintRow & {
      levelRaw?: string
      startYm?: string
      endYm?: string
    }

    // 只要有任一标准字段有值即纳入
    if (!row.name && !row.projectType && !row.channelName && !row.leadOrgName && row.totalFund == null) {
      continue
    }

    numInvalid.forEach((f) => {
      // 已在 parseNumber 标记；补充数值类问题
      row.issues = row.issues || []
    })

    const v = validateRow(row, channelSet)
    const issues = [...v.issues]
    numInvalid.forEach(() => {
      issues.push({ category: '数值', message: '存在无法解析的数字单元格（已置空）', forceable: true })
    })

    const uniqueKey = buildUniqueKey(row)
    row.uniqueKey = uniqueKey
    if (!uniqueKey) {
      issues.push({ category: '其他', message: '无法生成项目唯一标识（缺少序号或项目名称）', forceable: false })
    } else if (seenKeys.has(uniqueKey)) {
      issues.push({
        category: '其他',
        message: `与第 ${seenKeys.get(uniqueKey)} 行项目唯一性冲突`,
        forceable: false,
      })
    } else {
      seenKeys.set(uniqueKey, excelRow)
    }

    if (v.pendingChannel && row.channelName) {
      const key = `${row.channelName}||${row.projectType || ''}`
      const cur = pendingChannelMap.get(key) || {
        channelName: row.channelName,
        projectType: row.projectType,
        excelRows: [],
      }
      cur.excelRows.push(excelRow)
      pendingChannelMap.set(key, cur)
    }

    parsePeople(row.ownerName).forEach((p) => {
      if (knownPeople.size && knownPeople.has(p.name)) return
      if (!p.name) return
      if (knownPeople.size === 0) return // 无成员名录时不强制待确认
      const cur = pendingPeopleMap.get(p.name) || {
        name: p.name,
        employeeNo: p.employeeNo,
        excelRows: [],
        projects: [],
      }
      cur.excelRows.push(excelRow)
      if (row.name) cur.projects.push(row.name)
      pendingPeopleMap.set(p.name, cur)
    })

    let action: PreviewAction = 'add'
    let fieldDiffs: FormMaintRow['fieldDiffs'] = []
    const hit2 =
      (uniqueKey ? existingMap.get(uniqueKey) : undefined) ||
      existing.find(
        (x) =>
          x.name === row.name &&
          (x.leadOrgName || x.orgName) === (row.leadOrgName || row.orgName),
      )

    if (issues.length > 0 || !uniqueKey || issues.some((x) => x.message.includes('唯一性冲突'))) {
      action = 'skip'
    } else if (hit2) {
      fieldDiffs = compareFields(hit2, row)
      action = fieldDiffs.length ? 'update' : 'keep'
      row.id = hit2.id
      row.projectNo = hit2.projectNo
    } else {
      action = 'add'
    }

    if (!uniqueKey || issues.some((x) => x.message.includes('唯一性冲突') || x.message.includes('无法生成'))) {
      action = 'skip'
    }

    row.action = action
    row.issues = issues
    row.warnings = v.warnings
    row.fieldDiffs = fieldDiffs
    row.pendingChannel = v.pendingChannel
    row.pendingType = v.pendingType
    row.validateOk = issues.length === 0
    row.validateMsg = issues.length ? issues.map((x) => x.message).join('；') : '通过'
    row.forceable = issues.length > 0 && issues.every((x) => x.forceable !== false) && !!uniqueKey
    rows.push(row)
  }

  if (!rows.length) throw new Error('没有解析到有效项目行，请检查表头及数据起始行')

  // 覆盖模式：删除预览
  if (opts.mode === 'replace') {
    const keys = new Set(rows.map((r) => r.uniqueKey).filter(Boolean) as string[])
    existing.forEach((p) => {
      const k = existingUniqueKey(p)
      if (!keys.has(k)) {
        rows.push({
          ...projectToFormRow(p),
          action: 'delete',
          validateOk: true,
          validateMsg: '覆盖删除',
          uniqueKey: k,
          issues: [],
          warnings: [],
        })
      }
    })
  }

  const stats = {
    parsed: rows.length,
    added: rows.filter((r) => r.action === 'add').length,
    updated: rows.filter((r) => r.action === 'update').length,
    kept: rows.filter((r) => r.action === 'keep').length,
    skipped: rows.filter((r) => r.action === 'skip').length,
    deleted: rows.filter((r) => r.action === 'delete').length,
    issueCount: rows.filter((r) => !r.validateOk || r.action === 'skip').length,
  }

  return {
    rows,
    sheetName,
    fileName: opts.fileName,
    mode: opts.mode,
    stats,
    pendingChannels: [...pendingChannelMap.values()],
    pendingPeople: [...pendingPeopleMap.values()],
    batchStatus: stats.issueCount > 0 ? '待修正' : '待确认',
  }
}

/** 兼容旧调用：仅解析校验 */
export function parseFormMaintWorkbook(buf: ArrayBuffer): FormMaintRow[] {
  return runImportPreview(buf, [], { mode: 'merge' }).rows
}

export function toProjectPayload(row: FormMaintRow) {
  return {
    name: row.name,
    levelCode: row.levelCode,
    channelName: row.channelName,
    bureauOffice: row.bureauOffice,
    projectType: row.projectType,
    major1: row.major1,
    major2: row.major2,
    manageOrgName: row.manageOrgName,
    leadOrgName: row.leadOrgName,
    orgName: row.leadOrgName || row.orgName,
    status: row.status || 'IMPLEMENTING',
    acceptStatus: row.acceptStatus || '未验收',
    ownerName: row.ownerName,
    createByName: row.ownerName || row.createByName,
    startDate: row.startDate,
    endDate: row.endDate,
    totalFund: row.totalFund ?? 0,
    nationalFund: row.nationalFund ?? 0,
    selfFund: row.selfFund ?? 0,
    expenseTotal: row.expenseTotal ?? 0,
    yearBudget: row.yearBudget ?? 0,
    dataSource: 'FORM_MAINT' as const,
    warnColor: row.warnColor || 'BLUE',
  }
}

export async function buildFormMaintWorkbook(rows: FormMaintRow[]): Promise<ArrayBuffer> {
  // 严格按官方总表模板导出（保留合并单元格、表头样式与列宽）
  const ExcelJS = (await import('exceljs')).default
  const templateUrl = `${import.meta.env.BASE_URL}templates/${encodeURIComponent('预研项目_总表模板.xlsx')}`
  const resp = await fetch(templateUrl)
  if (!resp.ok) {
    throw new Error('未找到导出模板文件 public/templates/预研项目_总表模板.xlsx')
  }
  const templateBuf = await resp.arrayBuffer()
  const workbook = new ExcelJS.Workbook()
  await workbook.xlsx.load(templateBuf)
  const sheet = workbook.worksheets[0] || workbook.getWorksheet('预先研究项目信息')
  if (!sheet) throw new Error('模板缺少工作表「预先研究项目信息」')

  // 记录样例数据行样式（第 5 行），再清空第 5 行起的样本数据
  const styleProbe = sheet.getRow(5)
  const colStyles: Array<{
    font?: Partial<Font>
    border?: Partial<Borders>
    alignment?: Partial<Alignment>
    numFmt?: string
  }> = []
  for (let c = 1; c <= 37; c++) {
    const cell = styleProbe.getCell(c)
    colStyles.push({
      font: cell.font ? { ...cell.font } : undefined,
      border: cell.border ? { ...cell.border } : undefined,
      alignment: cell.alignment ? { ...cell.alignment } : undefined,
      numFmt: cell.numFmt,
    })
  }
  if (sheet.rowCount >= 5) {
    sheet.spliceRows(5, sheet.rowCount - 4)
  }

  const toYm = (d?: string, fallback?: string) => {
    if (fallback) return fallback
    return dateToYm(d).replace(/-/g, '.') || ''
  }

  rows.forEach((r, idx) => {
    const values: Array<string | number | null> = [
      r.seqNo ?? idx + 1,
      LEVEL_TO_TEXT[r.levelCode || ''] || r.levelCode || '',
      r.channelName || '',
      r.projectType || '',
      r.major1 || '',
      r.major2 || '',
      r.name || '',
      r.manageOrgName || '',
      r.leadOrgName || r.orgName || '',
      STATUS_TO_TEXT[r.status || ''] || r.status || '',
      r.acceptStatus || '未验收',
      r.ownerName || r.createByName || '',
      toYm(r.startDate, r.filingYm),
      toYm(r.startDate, r.startYm),
      toYm(r.endDate, r.endYm),
      r.period || '',
      r.totalFund ?? null,
      r.nationalFund ?? null,
      r.nationalFundInner ?? null,
      r.selfFund ?? null,
      r.selfFundInner ?? null,
      r.expenseTotal ?? null,
      r.yearBudget ?? null,
      r.finishExecFund ?? null,
      r.finishNationalExec ?? null,
      r.finishSelfExec ?? null,
      r.finishExecRate ?? '',
      r.achvCount ?? null,
      r.achvName || '',
      r.transformCount ?? null,
      r.transformName || '',
      r.transformYear || '',
      r.transformModel || '',
      r.techReadyCount ?? null,
      r.reserveName || '',
      r.expectTransformYear || '',
      r.remark || '',
    ]
    const row = sheet.getRow(5 + idx)
    row.height = 26
    values.forEach((v, i) => {
      const cell = row.getCell(i + 1)
      cell.value = v === '' ? null : v
      const st = colStyles[i]
      if (st?.font) cell.font = st.font
      if (st?.border) cell.border = st.border
      if (st?.alignment) cell.alignment = st.alignment
      if (st?.numFmt) cell.numFmt = st.numFmt
    })
    row.commit()
  })

  const out = await workbook.xlsx.writeBuffer()
  return out as ArrayBuffer
}

export function downloadArrayBuffer(buf: ArrayBuffer, filename: string) {
  const blob = new Blob([buf], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export function projectToFormRow(p: ProjInfo & Record<string, any>): FormMaintRow {
  const filingYm = p.filingYm || dateToYm(p.startDate)
  const startYm = p.startYm || dateToYm(p.startDate)
  const endYm = p.endYm || dateToYm(p.endDate)
  return {
    ...p,
    dataSource: (p.dataSource as ProjectDataSource) || 'PLATFORM',
    ownerName:
      p.ownerName ||
      p.teamMembers?.find((m) => m.roleCode === 'PROJECT_LEADER' || m.roleName === '项目负责人')?.userName ||
      p.createByName,
    acceptStatus: p.acceptStatus || '未验收',
    filingYm,
    startYm,
    endYm,
    period: p.period || '',
    nationalFundInner: p.nationalFundInner,
    selfFundInner: p.selfFundInner,
    finishExecFund: p.finishExecFund,
    finishNationalExec: p.finishNationalExec,
    finishSelfExec: p.finishSelfExec,
    finishExecRate: p.finishExecRate,
    achvCount: p.achvCount,
    achvName: p.achvName,
    transformCount: p.transformCount,
    transformName: p.transformName,
    transformYear: p.transformYear,
    transformModel: p.transformModel,
    techReadyCount: p.techReadyCount,
    reserveName: p.reserveName,
    expectTransformYear: p.expectTransformYear,
    remark: p.remark,
    validateOk: true,
    validateMsg: '通过',
    action: 'keep',
    issues: [],
    warnings: [],
  }
}

/** 官方总表第 4 行表头 → 列表/导出字段（严格 37 列，顺序不可改） */
export const FORM_TEMPLATE_COLS: { key: string; title: string; width: number; kind?: 'text' | 'ym' | 'num' }[] = [
  { key: 'seqNo', title: '序号', width: 70, kind: 'text' },
  { key: 'levelCode', title: '级别', width: 90, kind: 'text' },
  { key: 'channelName', title: '项目来源/渠道', width: 140, kind: 'text' },
  { key: 'projectType', title: '项目类型', width: 150, kind: 'text' },
  { key: 'major1', title: '一级专业', width: 120, kind: 'text' },
  { key: 'major2', title: '二级专业', width: 140, kind: 'text' },
  { key: 'name', title: '项目名称', width: 220, kind: 'text' },
  { key: 'manageOrgName', title: '管理/需求单位', width: 140, kind: 'text' },
  { key: 'leadOrgName', title: '责任单位', width: 160, kind: 'text' },
  { key: 'status', title: '项目状态', width: 100, kind: 'text' },
  { key: 'acceptStatus', title: '验收状态', width: 100, kind: 'text' },
  { key: 'ownerName', title: '负责人', width: 100, kind: 'text' },
  { key: 'filingYm', title: '项目立项年月', width: 120, kind: 'ym' },
  { key: 'startYm', title: '项目开始年月', width: 120, kind: 'ym' },
  { key: 'endYm', title: '项目结束年月', width: 120, kind: 'ym' },
  { key: 'period', title: '项目周期', width: 100, kind: 'text' },
  { key: 'totalFund', title: '总经费（万元）', width: 120, kind: 'num' },
  { key: 'nationalFund', title: '国拨经费（万元）', width: 130, kind: 'num' },
  { key: 'nationalFundInner', title: '其中商飞内部单位国拨经费（万元）', width: 200, kind: 'num' },
  { key: 'selfFund', title: '自筹经费（万元）', width: 130, kind: 'num' },
  { key: 'selfFundInner', title: '其中商飞内部单位自筹经费（万元）', width: 200, kind: 'num' },
  { key: 'expenseTotal', title: '累计支出（万元）', width: 130, kind: 'num' },
  { key: 'yearBudget', title: '2026年预算（万元）', width: 140, kind: 'num' },
  { key: 'finishExecFund', title: '已结题项目实际执行经费（万元）', width: 200, kind: 'num' },
  { key: 'finishNationalExec', title: '已结题项目国拨经费执行（万元）', width: 200, kind: 'num' },
  { key: 'finishSelfExec', title: '已结题项目自筹经费执行（万元）', width: 200, kind: 'num' },
  { key: 'finishExecRate', title: '已结题项目经费执行率', width: 150, kind: 'text' },
  { key: 'achvCount', title: '产生成果数量', width: 110, kind: 'num' },
  { key: 'achvName', title: '生成成果名称', width: 160, kind: 'text' },
  { key: 'transformCount', title: '已转化数量', width: 100, kind: 'num' },
  { key: 'transformName', title: '转化成果名称', width: 140, kind: 'text' },
  { key: 'transformYear', title: '转化年份', width: 100, kind: 'text' },
  { key: 'transformModel', title: '转化型号', width: 100, kind: 'text' },
  { key: 'techReadyCount', title: '技术成熟度数量', width: 120, kind: 'num' },
  { key: 'reserveName', title: '储备成果名称', width: 140, kind: 'text' },
  { key: 'expectTransformYear', title: '预计转化年度', width: 120, kind: 'text' },
  { key: 'remark', title: '备注', width: 140, kind: 'text' },
]

/** 列表默认展示：与设计稿主视区一致的模板字段（仍可用列配置打开全部 37 列） */
export const FORM_TEMPLATE_DEFAULT_VISIBLE = [
  'seqNo',
  'levelCode',
  'channelName',
  'projectType',
  'major1',
  'major2',
  'name',
  'manageOrgName',
  'leadOrgName',
  'status',
  'acceptStatus',
  'ownerName',
  'totalFund',
  'nationalFund',
  'selfFund',
]

/** 列表展示用：年月统一为模板口径 YYYY.MM */
export function formatTemplateYm(v?: string): string {
  if (!v) return ''
  const s = String(v).trim()
  const m1 = s.match(/^(\d{4})[.\-/](\d{1,2})/)
  if (m1) return `${m1[1]}.${m1[2].padStart(2, '0')}`
  return dateToYm(s) || s
}

/** 经费数值展示（空为空白，与模板一致不填 0） */
export function formatTemplateNum(v?: number | null): string {
  if (v == null || v === ('' as any) || Number.isNaN(Number(v))) return ''
  const n = Number(v)
  return Number.isInteger(n) ? String(n) : String(Math.round(n * 100) / 100)
}

export { LEVEL_TO_TEXT, STATUS_TO_TEXT }
