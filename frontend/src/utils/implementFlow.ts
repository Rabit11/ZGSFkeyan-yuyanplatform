/** 实施阶段 · 流转图（岗位定责，姓名取平台花名册工号） */

import { personnelDisplay } from '@/constants/personnel'
import { readonlyFlowActionLabel, readonlyFlowPath } from '@/utils/flowEntryPolicy'

export type ImplNodeType = 'ACTION' | 'AUDIT' | 'SYSTEM' | 'CLOSED'
export type ImplNodeStatus = 'done' | 'current' | 'pending' | 'return' | 'overdue'

export interface ImplFlowPerson {
  role: string
  employeeNo?: string
  name?: string
  label: string
}

export interface ImplFlowNode {
  nodeCode: string
  title: string
  desc?: string
  roleLine?: string
  nodeType: ImplNodeType
  status: ImplNodeStatus
  statusLabel: string
  handlers: ImplFlowPerson[]
  actionLabel?: string
  actionPath?: string
}

export interface ImplLaneSegment {
  kind: 'setup' | 'milestone'
  id?: number | string
  seq?: number
  date?: string
  name?: string
  progressLabel?: string
  nodes: ImplFlowNode[]
}

export interface ImplLane {
  laneCode: string
  title: string
  ownerLabel?: string
  nodes: ImplFlowNode[]
  segments?: ImplLaneSegment[]
}

export interface ImplMilestoneFlow {
  id: number | string
  name: string
  planDate?: string
  actualDate?: string
  colorStatus?: string
  msStatus?: string
  progressLabel: string
  seq?: number
  nodes: ImplFlowNode[]
  returnBranch: ImplFlowNode[]
  returnActive: boolean
  materials: ImplAttachment[]
}

export interface ImplAttachment {
  code: string
  name: string
  uploaded: boolean
  fileName?: string
  fileUrl?: string
  uploadedAt?: string
}

export interface ImplTimelineItem {
  title: string
  operator: string
  at: string
  comment?: string
  result?: string
}

export interface ImplModuleEntry {
  code: string
  title: string
  path: string
  entryType: string
}

export function implListPath(
  projectId?: number | string,
  milestoneId?: number | string,
  mode: 'compile' | 'close' = 'close',
) {
  const q = new URLSearchParams()
  if (projectId !== undefined && projectId !== null && String(projectId) !== '' && !String(projectId).startsWith('demo')) {
    q.set('projectId', String(projectId))
  }
  if (milestoneId !== undefined && milestoneId !== null && String(milestoneId) !== '' && !String(milestoneId).startsWith('demo')) {
    q.set('milestoneId', String(milestoneId))
  }
  if (mode === 'compile') q.set('mode', 'compile')
  const s = q.toString()
  return s ? `/implement/milestone-close?${s}` : mode === 'compile' ? '/implement/milestone-close?mode=compile' : '/implement/milestone-close'
}

export function implClosePath(projectId?: number | string, milestoneId?: number | string) {
  return implListPath(projectId, milestoneId, 'close')
}

export function implCompilePath(projectId?: number | string, milestoneId?: number | string) {
  return implListPath(projectId, milestoneId, 'compile')
}

export interface ImplFlowOpts {
  projectId?: number | string
  code?: string
  name?: string
  ownerLabel: string
  dept?: string
  panelStatus: 'HANDLING' | 'DONE'
  panelStatusLabel: string
  nextAction: string
  nextActionPath?: string
  annualGoal: string
  msDone: number
  msTotal: number
  lanes: ImplLane[]
  milestoneFlows: ImplMilestoneFlow[]
  posts: { role: string; name: string }[]
  staff: ImplFlowPerson[]
  modules: ImplModuleEntry[]
  attachments: ImplAttachment[]
  timeline: ImplTimelineItem[]
  processHint: string
  /** 7.3 经费：预算填报 → 节点核销 → 全部闭环后总核 */
  fundNodes: ImplFlowNode[]
}

export const IMPL_ROLE_EMPLOYEE: Record<string, string> = {
  system: '100001',
  hq: '100003',
  hqStaff: '100004',
  unitHead: '100005',
  unitStaff: '100006',
  deptHead: '100016',
  chief1: '100007',
  chief2: '100008',
  hqFin: '100009',
  unitFin: '100010',
  eval: '100011',
  owner: '100012',
  contact: '100013',
  tech: '100014',
  pm: '100015',
}

const TYPE_LABEL: Record<ImplNodeType, string> = {
  ACTION: '确认/上传',
  AUDIT: '审核',
  SYSTEM: '系统',
  CLOSED: '已结束且合规',
}

function statusLabelOf(st: ImplNodeStatus) {
  return ({ done: '已办', current: '当前办理', pending: '待流转', return: '退回', overdue: '逾期' } as Record<ImplNodeStatus, string>)[st]
}

function fromRoster(empNo: string, role: string): ImplFlowPerson {
  const label = personnelDisplay(empNo)
  return {
    role,
    employeeNo: empNo,
    name: label.split('（')[0],
    label,
  }
}

function pickMember(
  members: any[] | undefined,
  roleKeys: string[],
  fallbackEmpNo: string,
  roleTitle: string,
): ImplFlowPerson {
  for (const key of roleKeys) {
    const hit = (members || []).find((m) => {
      const code = String(m.roleCode || '')
      const name = String(m.roleName || '')
      const user = String(m.userName || m.realName || '')
      return code === key || name === key || name.includes(key) || user.includes(key)
    })
    if (hit) {
      const emp = String(hit.employeeNo || '').replace(/\D/g, '')
      const userName = hit.userName || hit.realName || hit.name
      if (emp && emp.length >= 5) {
        return { role: roleTitle, employeeNo: emp, name: userName, label: personnelDisplay(emp) }
      }
      if (userName) {
        return { role: roleTitle, name: String(userName), label: String(userName) }
      }
    }
  }
  return fromRoster(fallbackEmpNo, roleTitle)
}

function whoLine(handlers: ImplFlowPerson[]) {
  return handlers.map((h) => h.label).filter(Boolean).join('、') || '待指定'
}

export function who(node?: ImplFlowNode | null) {
  if (!node) return '待指定'
  return whoLine(node.handlers)
}

export function typeLabel(t: ImplNodeType) {
  return TYPE_LABEL[t]
}

function fmtDay(raw?: string) {
  if (!raw) return ''
  return String(raw).slice(0, 10)
}

function fmtNow() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function nodeOf(
  code: string,
  title: string,
  type: ImplNodeType,
  status: ImplNodeStatus,
  handlers: ImplFlowPerson[],
  desc?: string,
  actionLabel?: string,
  actionPath?: string,
  roleLine?: string,
  keepAction?: boolean,
): ImplFlowNode {
  const handleable = keepAction || status === 'current' || status === 'overdue' || status === 'return'
  const viewLabel = readonlyFlowActionLabel(actionLabel)
  return {
    nodeCode: code,
    title,
    desc,
    roleLine,
    nodeType: type,
    status,
    statusLabel: statusLabelOf(status),
    handlers,
    actionLabel: handleable ? readonlyFlowActionLabel(actionLabel) : viewLabel,
    actionPath: readonlyFlowPath(actionPath),
  }
}

function msProgress(m: any): string {
  if (m.status === 'CLOSE_DEPT_AUDIT') return '待项目承担部门负责人审核'
  if (m.status === 'CLOSE_UNIT_AUDIT') return '待单位科研管理部门负责人审核'
  if (m.status === 'DONE' || m.colorStatus === 'GREEN') return '已完成'
  if (m.status === 'OVERDUE' || m.colorStatus === 'RED') return '逾期'
  if (m.colorStatus === 'YELLOW') return '临期'
  return '进行中'
}

function msInnerStatus(
  m: any,
  isActive: boolean,
): { m1: ImplNodeStatus; m2: ImplNodeStatus; m3: ImplNodeStatus; m4: ImplNodeStatus; m5: ImplNodeStatus; ret: boolean } {
  if (m.status === 'DONE' || m.colorStatus === 'GREEN') {
    return { m1: 'done', m2: 'done', m3: 'done', m4: 'done', m5: 'pending', ret: false }
  }
  if (!isActive) {
    return { m1: 'pending', m2: 'pending', m3: 'pending', m4: 'pending', m5: 'pending', ret: false }
  }
  if (m.status === 'CLOSE_DEPT_AUDIT') {
    return { m1: 'done', m2: 'done', m3: 'current', m4: 'pending', m5: 'pending', ret: false }
  }
  if (m.status === 'CLOSE_UNIT_AUDIT') {
    return { m1: 'done', m2: 'done', m3: 'done', m4: 'current', m5: 'pending', ret: false }
  }
  if (m.status === 'OVERDUE' || m.colorStatus === 'RED') {
    return { m1: 'done', m2: 'overdue', m3: 'pending', m4: 'pending', m5: 'current', ret: false }
  }
  if (m.evidence) {
    return { m1: 'done', m2: 'done', m3: 'current', m4: 'pending', m5: 'pending', ret: false }
  }
  return { m1: 'done', m2: 'current', m3: 'pending', m4: 'pending', m5: 'pending', ret: false }
}

export interface ImplementFlowInput {
  id?: number | string
  projectId?: number | string
  projectNo?: string
  name?: string
  goal?: string
  startDate?: string
  endDate?: string
  levelCode?: string
  channelId?: number
  status?: string
  leadOrgName?: string
  deptName?: string
  orgName?: string
  annualGoal?: string
  annualPlans?: { year?: number; annualGoal?: string; finishStatus?: string; colorStatus?: string }[]
  teamMembers?: any[]
  milestones?: any[]
  evaluations?: any[]
  plans?: any[]
  payments?: any[]
  budgets?: any[]
  changes?: any[]
  deliverables?: any[]
}

export function buildImplementFlowOpts(input: ImplementFlowInput | null | undefined): ImplFlowOpts {
  const src = input || {}
  const projectId = src.id ?? src.projectId
  const members = src.teamMembers || []
  const msList = [...(src.milestones || [])]
    .filter((m) => String(m?.name || '').trim() && String(m?.planDate || '').trim())
    .sort((a, b) => String(a.planDate || '').localeCompare(String(b.planDate || '')))
  const evals = src.evaluations || []
  const plans = src.plans || []
  const payments = src.payments || []
  const budgets = src.budgets || []

  const owner = pickMember(members, ['PROJECT_LEADER', '项目负责人'], IMPL_ROLE_EMPLOYEE.owner, '项目负责人')
  const tech = pickMember(members, ['TECH_LEADER', '技术负责人'], IMPL_ROLE_EMPLOYEE.tech, '技术负责人')
  const unitHead = pickMember(members, ['UNIT_MINISTER', '单位科技部长', '单位科研管理部门负责人'], IMPL_ROLE_EMPLOYEE.unitHead, '二级单位内审')
  const deptHead = pickMember(members, ['DEPT_HEAD', '项目承担部门负责人'], IMPL_ROLE_EMPLOYEE.deptHead, '项目承担部门负责人')
  const hq = pickMember(members, ['HQ_DIRECTOR', '总部处室处长'], IMPL_ROLE_EMPLOYEE.hq, '总部科研')
  const hqFin = pickMember(members, ['HQ_FINANCE', '总部财务主管'], IMPL_ROLE_EMPLOYEE.hqFin, '总部科研财务')
  const unitFin = pickMember(members, ['UNIT_FIN_MINISTER', '单位财务部长'], IMPL_ROLE_EMPLOYEE.unitFin, '单位财务')
  const chief1 = pickMember(members, ['L1_CHIEF', '一级总师'], IMPL_ROLE_EMPLOYEE.chief1, '一级总师')
  const chief2 = pickMember(members, ['L2_CHIEF', '二级总师'], IMPL_ROLE_EMPLOYEE.chief2, '二级总师')
  const sys = fromRoster(IMPL_ROLE_EMPLOYEE.system, '系统')

  const msDone = msList.filter((m) => m.status === 'DONE' || m.colorStatus === 'GREEN').length
  const msTotal = msList.length
  const allMsDone = msTotal > 0 && msDone === msTotal
  const hasOverdue = msList.some((m) => m.status === 'OVERDUE' || m.colorStatus === 'RED')
  const pendingMs = msList.find((m) => m.status !== 'DONE' && m.colorStatus !== 'GREEN')
  const hasEvidencePending = msList.some((m) => m.status !== 'DONE' && m.colorStatus !== 'GREEN' && m.evidence)
  const written = payments.filter((p) => p.writeoffStatus === 'WRITTEN').length
  const nodeBudgets = budgets.filter((b) => String(b.milestoneName || '') !== '项目经费总核')
  const finalRow = budgets.find((b) => String(b.milestoneName || '') === '项目经费总核')
  const budgetDraft = nodeBudgets.some((b) => b.status === 'DRAFT' || !b.status)
  const budgetUnit = nodeBudgets.some((b) => b.status === 'PENDING' || b.status === 'UNIT_AUDIT')
  const budgetHq = nodeBudgets.some((b) => b.status === 'UNIT_OK' || b.status === 'HQ_AUDIT')
  const budgetAllOk = nodeBudgets.length > 0 && nodeBudgets.every((b) => b.status === 'APPROVED')
  const closedMs = msList.filter((m) => m.status === 'DONE' || m.colorStatus === 'GREEN')
  const writeoffPays = payments.filter((p) => p.flowType === 'WRITEOFF' || p.writeoffStatus)
  const pendingWrite = writeoffPays.some((p) => ['DRAFT', 'PENDING', 'UNIT_OK'].includes(String(p.writeoffStatus || '')))
  const writeoffDraft = writeoffPays.some((p) => p.writeoffStatus === 'DRAFT')
  const writeoffUnit = writeoffPays.some((p) => p.writeoffStatus === 'PENDING' || p.writeoffStatus === 'UNIT_OK')
  const nodeWritten = (mid: number) =>
    writeoffPays.some((p) => Number(p.budgetId) === mid || Number(p.milestoneId) === mid) &&
    writeoffPays
      .filter((p) => Number(p.budgetId) === mid || Number(p.milestoneId) === mid)
      .every((p) => p.writeoffStatus === 'WRITTEN')
  const allClosedWritten =
    msTotal > 0 &&
    allMsDone &&
    closedMs.every((m) => nodeWritten(Number(m.id))) &&
    !pendingWrite
  const finalDone = finalRow?.status === 'APPROVED' || finalRow?.status === 'FINAL_DONE'
  const evalFail = evals.some((e) => e.result === 'FAIL' && e.status !== 'DONE')
  const evalPending = evals.some((e) => e.status !== 'DONE')
  const evalAllDone = evals.length > 0 && evals.every((e) => e.status === 'DONE') && !evalFail
  const unpublishedPlan = plans.some((p) => p.planType === 'TODO' && p.applyStatus === 'NONE')
  const changeList = src.changes || []
  const openChange = changeList.find((c) => ['DRAFT', 'REJECTED', 'APPROVING'].includes(String(c.status)))
  const changeHq = /总部|终审/.test(String(openChange?.flowNode || ''))
  const hasOwner = members.some((m) => ['PROJECT_LEADER', '项目负责人'].includes(String(m.roleCode || m.roleName || '')))
  const basicComplete = Boolean(src.name && src.goal && src.startDate && src.endDate && src.levelCode && src.channelId && src.leadOrgName && hasOwner)

  const laterThanFiling = !['DRAFT', 'DECLARING', 'PENDING_FILING'].includes(String(src.status || 'IMPLEMENTING'))
  const stageDone = allMsDone && ['ACCEPTING', 'COMPANY_ACCEPTED', 'GOV_ACCEPTED', 'FINISHED'].includes(String(src.status || ''))
  const dvs = src.deliverables || []
  const dvOfMs = (m: any) =>
    dvs.filter(
      (d) =>
        Number(d.milestoneId) === Number(m.id) ||
        (!d.milestoneId && String(d.dueDate || '').slice(0, 10) === String(m.planDate || '').slice(0, 10)),
    )
  const listCompiled = msTotal > 0 && dvs.length > 0
  const flowYear = new Date().getFullYear()
  const annualPlan = (src.annualPlans || []).find((a) => Number(a.year) === flowYear) || (src.annualPlans || [])[0]
  const annualStatus = String(annualPlan?.finishStatus || '')
  const annualPendingAudit = annualStatus === 'PENDING_AUDIT'
  const annualReturned = annualStatus === 'RETURN'
  const annualArchived = annualStatus === 'DONE' || (listCompiled && !annualStatus)
  const stMsCompile: ImplNodeStatus = annualReturned ? 'return' : listCompiled || annualPendingAudit || annualArchived ? 'done' : 'current'
  const stMsUnit: ImplNodeStatus = annualArchived ? 'done' : annualPendingAudit ? 'current' : 'pending'
  const stMsAdd: ImplNodeStatus = annualArchived ? 'current' : 'pending'
  const compilePath = implCompilePath(projectId)
  const canHandleMilestones = annualArchived

  const stG5: ImplNodeStatus = !canHandleMilestones ? 'pending' : allMsDone ? 'done' : hasEvidencePending ? 'done' : hasOverdue ? 'overdue' : pendingMs ? 'current' : 'pending'
  const stG6: ImplNodeStatus = !canHandleMilestones ? 'pending' : allMsDone ? 'done' : hasEvidencePending ? 'current' : 'pending'
  const stF1: ImplNodeStatus = laterThanFiling ? 'done' : 'pending'
  const stF2: ImplNodeStatus = written > 0 && budgets.length === written ? 'done' : laterThanFiling ? (written > 0 ? 'current' : 'current') : 'pending'
  const stF3: ImplNodeStatus = written > 0 ? 'done' : stF2 === 'current' ? 'pending' : 'pending'
  const stF4: ImplNodeStatus = written > 0 ? 'done' : 'pending'
  const stE1: ImplNodeStatus = evalFail ? 'return' : evalAllDone ? 'done' : evals.length ? 'done' : 'current'
  const stE2: ImplNodeStatus = evalAllDone ? 'done' : evals.length ? (evalPending || evalFail ? 'current' : 'done') : 'pending'
  const stE3: ImplNodeStatus = evalAllDone ? 'done' : stE2 === 'current' ? 'pending' : evals.length && !evalPending ? 'current' : 'pending'
  const stC1: ImplNodeStatus =
    openChange?.status === 'REJECTED' ? 'return' : openChange?.status === 'APPROVING' ? 'done' : 'current'
  const stC2: ImplNodeStatus =
    openChange?.status === 'APPROVING' ? (changeHq ? 'done' : 'current') : 'pending'
  const stC3: ImplNodeStatus = openChange?.status === 'APPROVING' && changeHq ? 'current' : 'pending'
  const lanes: ImplLane[] = [
    {
      laneCode: 'IMPL_FUND',
      title: '项目基本信息',
      nodes: [
        nodeOf('BASIC_SYNC', '系统立项数据回显', 'SYSTEM', 'done', [sys]),
        nodeOf('BASIC_SUPPLEMENT', '项目团队补充缺失字段', 'ACTION', basicComplete ? 'done' : 'current', [owner], basicComplete ? '必填信息已补齐' : '仍有项目目标、周期、专业、团队等字段待补充', '去填报', '/implement/basic'),
        nodeOf('BASIC_UNIT_AUDIT', '二级单位内部审核', 'AUDIT', basicComplete ? 'done' : 'pending', [unitHead]),
        nodeOf('BASIC_HQ_ARCHIVE', '总部科研项目处备案', 'AUDIT', basicComplete && laterThanFiling ? 'done' : 'pending', [hq]),
        nodeOf('BASIC_SYNC_CHANGE', '数据同步项目变更', 'SYSTEM', basicComplete && laterThanFiling ? 'done' : 'pending', [sys]),
      ],
    },
    {
      laneCode: 'IMPL_MANAGE',
      title: '里程碑管理',
      ownerLabel: tech.label,
      nodes: [],
      segments: [],
    },
    {
      laneCode: 'IMPL_FUND_EXEC',
      title: '项目经费',
      nodes: [],
    },
    {
      laneCode: 'IMPL_EVAL',
      title: '评估检查',
      ownerLabel: owner.label,
      nodes: [
        nodeOf(
          'EVAL_APPLY',
          '按日常进度发起评估申请',
          'ACTION',
          stE1,
          [owner],
          '项目负责人 · 填报',
          stE1 === 'current' || stE1 === 'return' ? '去填报' : '查看',
          '/implement/evaluation',
        ),
        nodeOf(
          'EVAL_UPLOAD',
          '上传评审结论和检查材料',
          'ACTION',
          stE2,
          [unitHead],
          '单位科技部长 · 上传',
          stE2 === 'current' ? '去上传' : '查看',
          '/implement/evaluation',
        ),
        nodeOf(
          'EVAL_ARCHIVE',
          '线上归档并同步进度',
          'AUDIT',
          stE3,
          [hq],
          '总部处室处长 · 审批',
          stE3 === 'current' ? '去审批' : '查看',
          '/implement/evaluation',
        ),
      ],
    },
    {
      laneCode: 'IMPL_CHANGE',
      title: '项目变更',
      ownerLabel: owner.label,
      nodes: [
        nodeOf(
          'CHG_SUBMIT',
          '填报变更内容及支撑材料',
          'ACTION',
          stC1,
          [owner],
          '项目负责人 · 上传',
          stC1 === 'current' || stC1 === 'return' ? '去上传' : '查看',
          '/implement/change',
        ),
        nodeOf(
          'CHG_UNIT_AUDIT',
          '二级单位主管部门初审',
          'AUDIT',
          stC2,
          [unitHead],
          '单位科技部长 · 审批',
          stC2 === 'current' ? '去审批' : '查看',
          '/implement/change',
        ),
        nodeOf(
          'CHG_HQ_FINAL',
          '总部管理部门终审',
          'AUDIT',
          stC3,
          [hq],
          '总部处室处长 · 审批',
          stC3 === 'current' ? '去审批' : '查看',
          '/implement/change',
        ),
      ],
    },
  ]

  const hasRealProject = Boolean(src.id || src.projectId || src.projectNo)
  const useDemoMs = !hasRealProject && !msList.length
  const milestoneFlows: ImplMilestoneFlow[] = (msList.length
    ? msList
    : useDemoMs
      ? [
          { id: 'demo-1', name: '方案评审', planDate: '2024-10-01', status: 'DONE', colorStatus: 'GREEN', evidence: 1 },
          { id: 'demo-2', name: '测试', planDate: '2024-10-07', status: 'DONE', colorStatus: 'GREEN', evidence: 1 },
          { id: 'demo-3', name: '中期评估', planDate: '2024-10-08', status: 'OVERDUE', colorStatus: 'RED' },
        ]
      : []
  ).map((m, idx) => {
    const closed = m.status === 'DONE' || m.colorStatus === 'GREEN'
    const overdue = m.status === 'OVERDUE' || m.colorStatus === 'RED'
    const isActive =
      !closed && (overdue || (pendingMs != null && String(pendingMs.id) === String(m.id)))
    const inner = msInnerStatus(m, isActive)
    const list = dvOfMs(m)
    const delivered = list.filter((d) => d.status === 'DELIVERED' || d.fileUrl).length
    const types = [...new Set(list.map((d) => d.deliverType).filter(Boolean))]
    const typeHint = types.length ? `（${types.join('、')}）` : ''
    const warnSt: ImplNodeStatus = inner.m1
    const uploadSt: ImplNodeStatus = inner.m2
    const deptAuditSt: ImplNodeStatus = inner.m3
    const unitAuditSt: ImplNodeStatus = inner.m4
    const delaySt: ImplNodeStatus = inner.m5
    const materials: ImplAttachment[] = (m.materials || []).map((a: any, index: number) => ({
      code: String(a.id || `${m.id}-${index}`),
      name: a.fieldName || '节点完成佐证材料',
      uploaded: Boolean(a.fileName || a.fileUrl),
      fileName: a.fileName,
      fileUrl: a.fileUrl,
      uploadedAt: a.uploadedAt,
    }))
    const nodes = [
      nodeOf(
        `${m.id}-M1`,
        '系统自动计时预警',
        'SYSTEM',
        warnSt,
        [sys],
        '到期前 30 天黄灯，超期转红',
        undefined,
        undefined,
        '系统 · 系统',
      ),
      nodeOf(
        `${m.id}-M2`,
        '按期完成：按清单上传交付物证明',
        'ACTION',
        uploadSt,
        [owner],
        `年初清单 ${delivered}/${list.length || 0} 已交付${typeHint}；逐项填写信息并上传证明，核验通过后销项`,
        '去填报/上传',
        implClosePath(projectId, m.id),
        '项目负责人 · 上传',
      ),
      nodeOf(
        `${m.id}-M3`,
        '项目承担部门负责人审核销项',
        'AUDIT',
        deptAuditSt,
        [deptHead],
        '项目负责人提交节点证明后，先由项目承担部门负责人审核。',
        deptAuditSt === 'current' ? '去审核' : '查看',
        implClosePath(projectId, m.id),
        '项目承担部门负责人 · 审批',
      ),
      nodeOf(
        `${m.id}-M4`,
        '单位科研管理部门负责人审核销项',
        'AUDIT',
        unitAuditSt,
        [unitHead],
        '项目承担部门负责人通过后，单位科研管理部门负责人终审；通过后节点才完成销项。',
        unitAuditSt === 'current' ? '去审核' : '查看',
        implClosePath(projectId, m.id),
        '单位科研管理部门负责人 · 审批',
      ),
      nodeOf(
        `${m.id}-M5`,
        '未按期：标注滞后并走项目变更',
        'ACTION',
        delaySt,
        [owner],
        undefined,
        '去变更',
        '/implement/change',
        '项目负责人 · 填报',
      ),
    ]
    const returnBranch: ImplFlowNode[] = []
    return {
      id: m.id,
      name: m.name,
      planDate: fmtDay(m.planDate),
      actualDate: fmtDay(m.actualDate),
      colorStatus: m.colorStatus,
      msStatus: m.status,
      progressLabel: msProgress(m),
      seq: idx + 1,
      nodes,
      returnBranch,
      returnActive: false,
      materials,
    }
  })

  const setupNodes: ImplFlowNode[] = [
    nodeOf(
      'MS_ANNUAL_LIST',
      '编制里程碑节点',
      'ACTION',
      stMsCompile,
      [tech, sys],
      annualReturned
        ? '清单已被驳回，请修改年度目标、节点和交付物后重新提交审查。'
        : annualPendingAudit
          ? '清单已提交，正在等待二级单位科技部门审核存档。'
          : '先填写本年度目标与节点（名称、计划完成日期），再按节点编制交付物清单。提交后由二级单位科技部门审核存档。',
      stMsCompile === 'current' || stMsCompile === 'return' ? '去填报' : '查看清单',
      compilePath,
      '技术负责人 · 填报',
      true,
    ),
    nodeOf(
      'MS_UNIT_ARCHIVE',
      '二级单位科技部门审核存档',
      'AUDIT',
      stMsUnit,
      [unitHead],
      annualArchived
        ? '节点清单已由二级单位科技部门审核归档，后续办理按该清单执行。'
        : annualPendingAudit
          ? '当前清单已流转至二级单位科技部门负责人待审核。'
          : '节点清单齐备后，由二级单位科技部门审核并归档。',
      stMsUnit === 'current' ? '去审核' : '查看',
      compilePath,
      '单位科技部长 · 审批',
    ),
    nodeOf(
      'MS_ADD_NODE',
      '新增里程碑节点',
      'ACTION',
      stMsAdd,
      [tech, sys],
      annualArchived
        ? '清单归档后如需增补节点，可按同一口径补充提交。'
        : '需先完成年度里程碑清单审核归档，再开放增补节点。',
      stMsAdd === 'current' ? '去填报' : '查看',
      compilePath,
      '技术负责人 · 填报',
      true,
    ),
  ]
  const msLane = lanes.find((l) => l.laneCode === 'IMPL_MANAGE')
  if (msLane) {
    const segs: ImplLaneSegment[] = [
      { kind: 'setup', nodes: setupNodes },
      ...milestoneFlows.map((ms) => ({
        kind: 'milestone' as const,
        id: ms.id,
        seq: ms.seq,
        date: ms.planDate,
        name: ms.name,
        progressLabel: ms.progressLabel,
        nodes: ms.nodes,
      })),
    ]
    msLane.segments = segs
    msLane.nodes = segs.flatMap((s) => s.nodes)
  }

  const stFb1: ImplNodeStatus = !allMsDone ? 'pending' : budgetAllOk ? 'done' : budgetDraft || !nodeBudgets.length ? 'current' : 'done'
  const stFb2: ImplNodeStatus = !allMsDone ? 'pending' : budgetAllOk ? 'done' : budgetUnit ? 'current' : budgetHq ? 'done' : 'pending'
  const stFb3: ImplNodeStatus = !allMsDone ? 'pending' : budgetAllOk ? 'done' : budgetHq ? 'current' : 'pending'
  const canWrite = allMsDone && budgetAllOk
  const stFw1: ImplNodeStatus = allClosedWritten ? 'done' : !canWrite ? 'pending' : writeoffUnit ? 'done' : 'current'
  const stFw2: ImplNodeStatus = allClosedWritten ? 'done' : writeoffUnit ? 'current' : written > 0 ? 'done' : writeoffDraft ? 'pending' : 'pending'
  const stFw3: ImplNodeStatus = !allMsDone ? 'pending' : written > 0 && !pendingWrite ? 'done' : 'pending'
  const finalInAudit = ['FINAL_PENDING', 'FINAL_UNIT_OK'].includes(String(finalRow?.status || ''))
  const stFf: ImplNodeStatus = finalDone ? 'done' : finalInAudit || (allMsDone && allClosedWritten) ? 'current' : 'pending'

  const fundNodes: ImplFlowNode[] = [
    nodeOf(
      'FUND_B1',
      '项目团队填写经费预算填报表（绑定里程碑）',
      'ACTION',
      stFb1,
      [owner],
      allMsDone
        ? '全部里程碑已闭环，请按节点填报预算。团队成员可暂存，负责人提交审签。'
        : `须全部里程碑闭环后再填报预算（当前 ${msDone}/${msTotal || 0}）。`,
      stFb1 === 'current' ? '去填报' : '查看填报',
      '/implement/fund?mode=budget',
    ),
    nodeOf('FUND_B2', '二级单位财务部门审核', 'AUDIT', stFb2, [unitFin], undefined, stFb2 === 'current' ? '去审核' : '查看', '/implement/fund?mode=budget'),
    nodeOf('FUND_B3', '总部财务团队复核备案', 'AUDIT', stFb3, [hqFin], undefined, stFb3 === 'current' ? '去复核' : '查看', '/implement/fund?mode=budget'),
    nodeOf(
      'FUND_W1',
      '二级单位财务上传付款凭证并完成本级核销',
      'ACTION',
      stFw1,
      [unitFin],
      '节点预算完成总部复核备案后开放。二级单位财务负责人按节点上传付款凭证并填报核销信息。',
      stFw1 === 'current' ? '去填报/上传' : '查看',
      '/implement/fund?mode=writeoff&desk=writeoff-upload',
    ),
    nodeOf('FUND_W2', '二级单位财务完成本级核销', 'AUDIT', stFw2, [unitFin], '二级单位财务核对付款凭证并完成本级核销；退回则回到核销填报上传环节补正。', stFw2 === 'current' ? '去办理' : '查看', '/implement/fund?mode=writeoff&desk=writeoff'),
    nodeOf('FUND_W3', '系统同步二级单位经费核销数据到总部', 'SYSTEM', stFw3, [sys], '二级单位财务完成本级核销后，系统自动同步单位经费数据至总部经费看板。'),
    nodeOf(
      'FUND_FINAL',
      '项目经费总核（全部节点闭环后）',
      finalDone ? 'CLOSED' : 'AUDIT',
      stFf,
      [unitFin, hqFin],
      allMsDone
        ? finalRow?.status === 'FINAL_PENDING'
          ? '总核已流转至二级单位财务负责人办理，通过后进入总部财务主管复核。'
          : finalRow?.status === 'FINAL_UNIT_OK'
            ? '二级单位财务负责人已通过，待总部财务主管复核。'
            : '全部里程碑已闭环，两级财务按顺序对全年节点预算与核销进行总核。'
        : `须全部里程碑闭环后再发起总核（当前 ${msDone}/${msTotal || 0}）。`,
      stFf === 'current' ? '去总核' : '查看',
      '/implement/fund?mode=final',
    ),
  ]
  const fundLane = lanes.find((l) => l.laneCode === 'IMPL_FUND_EXEC')
  if (fundLane) fundLane.nodes = fundNodes

  const year = new Date().getFullYear()
  const annualGoal =
    src.annualGoal ||
    (src.annualPlans || []).find((a) => a.year === year)?.annualGoal ||
    (src.annualPlans || [])[0]?.annualGoal ||
    '—'

  let nextAction = '查看实施办理'
  let nextActionPath = '/implement/milestone'
  if (unpublishedPlan) {
    nextAction = '同步 CMOS 发布计划'
    nextActionPath = '/implement/plan'
  } else if (stMsCompile === 'current') {
    nextAction = '编制里程碑节点'
    nextActionPath = compilePath
  } else if (stG5 === 'current' || stG5 === 'overdue') {
    nextAction = '按清单上传交付物证明'
    nextActionPath = implClosePath(projectId, pendingMs?.id)
  } else if (stG6 === 'current') {
    nextAction = '审核里程碑销项'
    nextActionPath = '/implement/review'
  } else if (stE1 === 'current' || stE1 === 'return') {
    nextAction = '办理评估检查'
    nextActionPath = '/implement/evaluation'
  } else if (stFw1 === 'current') {
    nextAction = '二级单位填报节点核销信息并上传材料'
    nextActionPath = '/implement/fund?mode=writeoff&desk=writeoff-upload'
  } else if (stFw2 === 'current') {
    nextAction = '办理二级单位本级核销'
    nextActionPath = '/implement/fund?mode=writeoff&desk=writeoff'
  } else if (stFf === 'current') {
    nextAction = '全部节点已闭环，发起经费总核'
    nextActionPath = '/implement/fund?mode=final'
  } else if (allMsDone) {
    nextAction = '进入项目验收'
    nextActionPath = '/acceptance/accept'
  }

  const attachments: ImplAttachment[] = milestoneFlows.flatMap((ms) =>
    ms.materials.map((a) => ({ ...a, code: `${ms.id}-${a.code}`, name: `${ms.name} · ${a.name}` })),
  )

  const timeline: ImplTimelineItem[] = []
  if (laterThanFiling) {
    timeline.push({ title: '立项备案办结，进入实施阶段', operator: unitHead.label, at: fmtNow(), result: '已办结' })
    timeline.push({ title: '制定实施方案及预算明细清单', operator: owner.label, at: fmtNow(), result: '已通过' })
  }
  msList.filter((m) => m.status === 'DONE' || m.colorStatus === 'GREEN').forEach((m) => {
    timeline.push({
      title: `里程碑「${m.name}」销项办结`,
      operator: hq.label,
      at: fmtDay(m.actualDate) || fmtNow(),
      result: '已完成',
      comment: '总部采纳',
    })
  })
  if (pendingMs) {
    timeline.push({
      title: `里程碑「${pendingMs.name}」办理中`,
      operator: owner.label,
      at: fmtDay(pendingMs.planDate) || fmtNow(),
      result: msProgress(pendingMs),
      comment: pendingMs.lagReason,
    })
  }

  const posts = [
    { role: '项目负责', name: owner.label },
    { role: '一级负责人', name: chief1.label },
    { role: '二级负责人', name: chief2.label },
  ]

  const staff = [owner, tech, deptHead, unitHead, unitFin, hq, hqFin, chief1, chief2]

  const modules: ImplModuleEntry[] = [
    { code: 'basic', title: '项目基本信息', path: '/implement/basic', entryType: '个人入口' },
    { code: 'milestone', title: '里程碑', path: '/implement/milestone', entryType: '个人入口' },
    { code: 'plan', title: '计划管理', path: '/implement/plan', entryType: '个人入口' },
    { code: 'fund', title: '项目经费', path: '/implement/fund', entryType: '个人入口' },
    { code: 'evaluation', title: '评估检查', path: '/implement/evaluation', entryType: '专家入口' },
    { code: 'change', title: '项目变更', path: '/implement/change', entryType: '个人入口' },
  ]

  return {
    projectId,
    code: src.projectNo,
    name: src.name,
    ownerLabel: owner.label,
    dept: src.deptName || src.leadOrgName || src.orgName,
    panelStatus: stageDone || allMsDone ? 'DONE' : 'HANDLING',
    panelStatusLabel: stageDone || allMsDone ? '节点已办结' : '实施中',
    nextAction,
    nextActionPath,
    annualGoal,
    msDone,
    msTotal,
    lanes,
    milestoneFlows,
    posts,
    staff,
    modules,
    attachments,
    timeline,
    fundNodes,
    processHint:
      '经费：全部里程碑节点完成全部审核后，项目团队提交节点预算 → 二级单位财务审核 → 总部财务复核备案 → 二级单位财务上传付款凭证并完成本级核销 → 系统同步单位经费数据到总部经费看板 → 发起项目经费总核。',
  }
}

export function buildImplementFlowOptsFromOverview(data: any): ImplFlowOpts {
  const p = data?.project || data || {}
  return buildImplementFlowOpts({
    id: p.id,
    projectId: p.id,
    projectNo: p.projectNo,
    name: p.name,
    goal: p.goal,
    startDate: p.startDate,
    endDate: p.endDate,
    levelCode: p.levelCode,
    channelId: p.channelId,
    status: p.status,
    leadOrgName: p.leadOrgName,
    deptName: p.deptName,
    orgName: p.orgName,
    annualGoal: p.annualGoal,
    annualPlans: p.annualPlans,
    teamMembers: p.teamMembers,
    milestones: data?.milestones || p.milestones,
    evaluations: data?.evaluations || p.evaluations,
    changes: data?.changes || p.changes,
    plans: data?.plans || p.plans,
    payments: data?.payments || p.payments,
    budgets: data?.budgets || p.budgets,
    deliverables: data?.deliverables || p.deliverables,
  })
}

export function currentImplNodes(opts: ImplFlowOpts): ImplFlowNode[] {
  const fromMs = opts.milestoneFlows.flatMap((m) => m.nodes.filter((n) => n.status === 'current' || n.status === 'overdue' || n.status === 'return'))
  const fromFund = (opts.fundNodes || []).filter((n) => n.status === 'current' || n.status === 'overdue' || n.status === 'return')
  const fromLanes = opts.lanes.flatMap((l) => l.nodes.filter((n) => n.status === 'current' || n.status === 'overdue' || n.status === 'return'))
  return [...fromMs, ...fromFund, ...fromLanes].filter((node, index, all) => all.findIndex((x) => x.nodeCode === node.nodeCode) === index)
}

