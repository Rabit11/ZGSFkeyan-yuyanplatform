/** 成果转化阶段 · 流转图（居中弹窗） */

import { personnelDisplay } from '@/constants/personnel'

export type TfNodeStatus = 'done' | 'current' | 'pending' | 'return' | 'closed'
export type TfNodeType = 'ACTION' | 'AUDIT' | 'SYSTEM' | 'CLOSED'

export interface TfPerson {
  role: string
  employeeNo?: string
  name?: string
  label: string
}

export interface TfFlowNode {
  nodeCode: string
  title: string
  lane: string
  desc?: string
  nodeType: TfNodeType
  status: TfNodeStatus
  statusLabel: string
  handlers: TfPerson[]
}

export interface TfPackage {
  id?: number
  achievementNo?: string
  name?: string
  status?: string
  statusLabel: string
  transformWay?: string
  itemCount?: number
}

export interface TfTrace {
  delivered: number
  bound: number
  packages: number
  materials: number
}

export interface TfFlowOpts {
  code?: string
  name?: string
  ownerLabel: string
  panelStatus: 'HANDLING' | 'DONE' | 'NOT_STARTED'
  panelStatusLabel: string
  currentFlow: string
  latestProcess: string
  doneCount: number
  totalCount: number
  nextAction: string
  nextActionPath?: string
  nodes: TfFlowNode[]
  packages: TfPackage[]
  trace: TfTrace
  processHint: string
}

export const TRANSFORM_ROLE_EMPLOYEE = {
  owner: '100012',
  unitHead: '100005',
  unitClerk: '100008',
  hq: '100003',
  system: '100001',
}

const WAY_TEXT: Record<string, string> = { MODEL: '向型号转化', MARKET: '向市场转化' }
const PKG_STATUS: Record<string, string> = {
  NOT_STARTED: '未启动',
  NEGOTIATING: '洽谈中',
  SIGNED: '已签协议',
  DONE: '已完成',
}

function statusLabelOf(st: TfNodeStatus) {
  return ({ done: '已办', current: '当前', pending: '待办', return: '退回', closed: '已办结' } as Record<TfNodeStatus, string>)[st]
}

function fromRoster(empNo: string, role: string): TfPerson {
  const label = personnelDisplay(empNo)
  return { role, employeeNo: empNo, name: label.split('（')[0], label }
}

function pickMember(members: any[] | undefined, roleKeys: string[], fallbackEmpNo: string, roleTitle: string): TfPerson {
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
      if (userName) return { role: roleTitle, name: String(userName), label: String(userName) }
    }
  }
  return fromRoster(fallbackEmpNo, roleTitle)
}

export function who(node?: TfFlowNode | null) {
  if (!node?.handlers?.length) return ''
  if (node.handlers[0]?.label === '系统') return ''
  return node.handlers.map((h) => h.label).filter(Boolean).join('、')
}

function stOf(order: number, current: number, done: boolean, dualCurrent?: number[]): TfNodeStatus {
  if (done) return order === 7 ? 'closed' : 'done'
  if (dualCurrent?.includes(order) && order === current) return 'current'
  if (dualCurrent?.includes(order) && current <= 2 && order <= 2) return 'current'
  if (order < current) return 'done'
  if (order === current) return 'current'
  return 'pending'
}

function inferCurrent(transforms: any[], projectStatus?: string): { current: number; done: boolean } {
  const pkgs = transforms || []
  if (!pkgs.length) return { current: 1, done: false }
  // 审核节点取持久化流程状态，业务进度不能代替审批结果。
  if (pkgs.some(t => !t.workflowStatus || ['DRAFT', 'RETURNED'].includes(t.workflowStatus))) return { current: 2, done: false }
  if (pkgs.some(t => t.workflowStatus === 'UNIT_REVIEW')) return { current: 3, done: false }
  if (pkgs.some(t => t.workflowStatus === 'HQ_RECORD')) return { current: 4, done: false }
  const complete = pkgs.every(t => t.status === 'DONE' && t.workflowStatus === 'RECORDED')
  if (complete && projectStatus === 'FINISHED') return { current: 99, done: true }
  return { current: complete ? 7 : 6, done: false }
}

export function buildTransformFlowOptsFromOverview(overview: any): TfFlowOpts {
  const p = overview?.project || overview || {}
  const members = p.teamMembers || overview?.teamMembers || []
  const transforms = Array.isArray(overview?.transforms) ? overview.transforms : []
  const deliverables = Array.isArray(overview?.deliverables) ? overview.deliverables : []

  const owner = pickMember(members, ['PROJECT_LEADER', '项目负责人'], TRANSFORM_ROLE_EMPLOYEE.owner, '项目负责人')
  const unitHead = pickMember(members, ['UNIT_MINISTER', '单位科技部长', '二级单位'], TRANSFORM_ROLE_EMPLOYEE.unitHead, '二级单位管理团队')
  const unitClerk = pickMember(members, ['UNIT_SUPERVISOR', '单位科技主管', '二级总师'], TRANSFORM_ROLE_EMPLOYEE.unitClerk, '二级单位管理团队')
  const hq = pickMember(members, ['HQ_DIRECTOR', '总部处室处长', '总部管理'], TRANSFORM_ROLE_EMPLOYEE.hq, '总部管理团队')
  const sys = fromRoster(TRANSFORM_ROLE_EMPLOYEE.system, '系统')

  const { current, done } = inferCurrent(transforms, p.status)
  const dual = current <= 2 ? [1, 2] : undefined

  const nodes: TfFlowNode[] = [
    {
      nodeCode: 'TF_PRECHECK',
      title: '系统前置校验',
      lane: '系统',
      desc: '仅已交付成果可纳入成果包；生成全局唯一成果编号并关联项目与交付物。',
      nodeType: 'SYSTEM',
      status: stOf(1, current, done, dual),
      statusLabel: statusLabelOf(stOf(1, current, done, dual)),
      handlers: [sys],
    },
    {
      nodeCode: 'TF_SUBMIT',
      title: '项目负责人核对并提交成果包',
      lane: '项目团队',
      desc: '系统自动抓取全过程信息、已交付成果及对应附件；负责人核对并修改转化字段后提交。',
      nodeType: 'ACTION',
      status: stOf(2, current, done, dual),
      statusLabel: statusLabelOf(stOf(2, current, done, dual)),
      handlers: [owner],
    },
    {
      nodeCode: 'TF_UNIT',
      title: '二级单位管理团队审核确认',
      lane: '二级单位管理团队',
      desc: '核验合规与齐套；可退回修改，全过程留痕。',
      nodeType: 'AUDIT',
      status: stOf(3, current, done),
      statusLabel: statusLabelOf(stOf(3, current, done)),
      handlers: [unitHead],
    },
    {
      nodeCode: 'TF_HQ',
      title: '总部管理团队备案',
      lane: '总部管理团队',
      desc: '审核通过后全部材料备案，不再重复审核。',
      nodeType: 'AUDIT',
      status: stOf(4, current, done),
      statusLabel: statusLabelOf(stOf(4, current, done)),
      handlers: [hq],
    },
    {
      nodeCode: 'TF_SYNC',
      title: '转化数据双向同步',
      lane: '系统',
      desc: '核心信息同步项目台账与可视化看板，进度回写交付物，支持双向追溯。',
      nodeType: 'SYSTEM',
      status: stOf(5, current, done),
      statusLabel: statusLabelOf(stOf(5, current, done)),
      handlers: [sys],
    },
    {
      nodeCode: 'TF_EVIDENCE',
      title: '转化进展与成效佐证',
      lane: '项目团队',
      desc: '项目团队可持续更新转化进展；成果包完成时记录实际日期并上传成效佐证。',
      nodeType: 'ACTION',
      status: stOf(6, current, done),
      statusLabel: statusLabelOf(stOf(6, current, done)),
      handlers: [owner],
    },
    {
      nodeCode: 'TF_ARCHIVE',
      title: '项目完成归档',
      lane: '二级单位 / 总部',
      desc: '全部成果包转化并上传佐证后，可发起二级单位核验与总部归档确认；后评价不是前置条件。',
      nodeType: done ? 'CLOSED' : 'SYSTEM',
      status: stOf(7, current, done),
      statusLabel: statusLabelOf(stOf(7, current, done)),
      handlers: [unitClerk],
    },
  ]

  const now = nodes.filter((n) => n.status === 'current')
  const pkgDone = transforms.filter((t: any) => t.status === 'DONE').length
  const delivered = deliverables.filter((d: any) => d.status === 'DELIVERED').length
  const bound = deliverables.filter((d: any) => d.achievementNo).length

  const packages: TfPackage[] = transforms.map((t: any) => ({
    id: t.id,
    achievementNo: t.achievementNo,
    name: t.name,
    status: t.status,
    statusLabel: PKG_STATUS[t.status] || t.status || '未启动',
    transformWay: WAY_TEXT[t.transformWay] || t.transformWay,
    itemCount: t.itemCount || (t.deliverables || []).length,
  }))

  const currentFlow = done
    ? '已办结 · 可归档'
    : now.length
      ? `${now[now.length - 1].lane} · ${who(now[now.length - 1])}`
      : '待发起'

  const latestProcess = done
    ? '成果转化已办结'
    : current <= 2
      ? '尚未发起本阶段流程'
      : `${now[0]?.title || '办理中'}（${now[0]?.statusLabel || ''}）`

  return {
    code: p.projectNo,
    name: p.name,
    ownerLabel: owner.label,
    panelStatus: done ? 'DONE' : current <= 2 && !transforms.length ? 'NOT_STARTED' : current <= 2 ? 'NOT_STARTED' : 'HANDLING',
    panelStatusLabel: done ? '已完成' : current <= 2 ? '未开始' : '办理中',
    currentFlow,
    latestProcess,
    doneCount: pkgDone,
    totalCount: Math.max(transforms.length, 1),
    nextAction: done ? '去归档' : current <= 2 ? '去提交成果包' : '去办理',
    nextActionPath: '/transform',
    nodes,
    packages,
    trace: {
      delivered,
      bound,
      packages: transforms.length,
      materials: transforms.reduce((n: number, t: any) => { try { return n + (JSON.parse(t.evidenceJson || '[]').length || 0) } catch { return n } }, 0),
    },
    processHint:
      '仅已交付交付物可纳入成果包。审核通过后数据双向同步台账与看板；全部成果包完成并上传佐证后可发起项目完成归档。',
  }
}

export function currentTransformNodes(flow: TfFlowOpts) {
  return flow.nodes.filter((n) => n.status === 'current' || n.status === 'return')
}
