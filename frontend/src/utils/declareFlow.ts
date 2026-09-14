/** 项目申报 · 流转图 / 完整审批泳道 */

import { LEVEL_TEXT, type LevelCode, type ProjDeclaration } from '@/api/types'
import {
  liveMaterials,
  materialSummary,
  splitMaterialNames,
  type LiveMaterial,
} from '@/utils/flowLive'
import { declarationAuditNodes, declarationNodes, declarationWorkflowId, type WorkflowSource } from '@/utils/declarationWorkflow'

export type FlowStepStatus = 'current' | 'approved' | 'done' | 'pending' | 'rejected'

export interface DeclareFlowStep {
  title: string
  dept?: string
  status: FlowStepStatus
  owner?: { name?: string; label?: string }
  assignee?: string
}

export interface DeclareFlowOpts {
  needApproval: boolean
  code?: string
  name?: string
  channel?: string
  channelLevel?: string
  nodes: DeclareFlowStep[]
  contactLabel?: string
  transferred?: boolean
  /** 本渠道申报材料（接口实时：必传项 + 上传状态） */
  materials: LiveMaterial[]
  materialSummary: string
  offlineTail?: string
}

export const DEFAULT_APPROVAL_CHAIN = [
  '项目联系人',
  '项目负责人',
  '项目承担部门负责人',
  '二级总师',
  '单位财务部门负责人',
  '单位科技部门负责人',
  '单位分管领导',
  '一级总师',
  '总部科研项目处',
] as const

export const REPORT_CHAIN = ['项目联系人', '项目负责人', '线上报备归档'] as const

/** 提交后的审签节点（不含填报人） */
export const DECLARE_AUDIT_CHAIN = DEFAULT_APPROVAL_CHAIN.filter((t) => t !== '项目联系人')

const NODE_ALIAS: Record<string, string> = {
  承办部门负责人: '项目承担部门负责人',
}

export function firstDeclareAuditNode(_needApproval = true) {
  return DECLARE_AUDIT_CHAIN[0]
}

/** 当前节点通过后的下一审签岗；已是末岗则返回 null（归档） */
export function nextDeclareAuditNode(current?: string, needApproval = true): string | null {
  const cur = NODE_ALIAS[current || ''] || current || ''
  if (!needApproval) return cur === '项目负责人' ? '线上报备归档' : '项目负责人'
  let i = DECLARE_AUDIT_CHAIN.indexOf(cur as (typeof DECLARE_AUDIT_CHAIN)[number])
  if (i < 0 && cur) {
    i = DECLARE_AUDIT_CHAIN.findIndex((t) => cur.includes(t) || t.includes(cur))
  }
  if (i < 0) return DECLARE_AUDIT_CHAIN[0]
  if (i >= DECLARE_AUDIT_CHAIN.length - 1) return null
  return DECLARE_AUDIT_CHAIN[i + 1]
}

/** 按渠道返回实际申报链；旧数据没有流程版本时沿用兼容固定链。 */
export function declarationTitles(decl: Partial<ProjDeclaration> & { channelCode?: string; flowNodes?: string; posts?: any }) {
  const source: WorkflowSource = decl as any
  const ids = declarationNodes(source)
  return ids.map(n => n.title)
}

export function firstDeclarationAuditNode(decl: Partial<ProjDeclaration> & { channelCode?: string; flowNodes?: string; posts?: any }) {
  return declarationAuditNodes(decl as any)[0]?.title || firstDeclareAuditNode(decl.needApproval !== 0)
}

export function nextDeclarationAuditNode(current: string | undefined, decl: Partial<ProjDeclaration> & { channelCode?: string; flowNodes?: string; posts?: any }) {
  const chain = declarationAuditNodes(decl as any)
  const normalized = current === '承办部门负责人' ? '项目承担部门负责人' : current
  const i = chain.findIndex(n => n.title === normalized || n.title.includes(normalized || ''))
  return i >= 0 && i + 1 < chain.length ? chain[i + 1].title : null
}

export const SWIM_PEOPLE_KEYS = [
  'contact',
  'owner',
  'deptHead',
  'chief2',
  'finHead',
  'sciHead',
  'unitLeader',
  'chief1',
  'hqOffice',
] as const

export type SwimPeopleKey = (typeof SWIM_PEOPLE_KEYS)[number]

const TITLE_TO_SWIM: { re: RegExp; key: SwimPeopleKey }[] = [
  { re: /联系人/, key: 'contact' },
  { re: /项目负责人/, key: 'owner' },
  { re: /承办|承担部门/, key: 'deptHead' },
  { re: /二级总师/, key: 'chief2' },
  { re: /财务/, key: 'finHead' },
  { re: /科技部门/, key: 'sciHead' },
  { re: /分管领导/, key: 'unitLeader' },
  { re: /一级总师/, key: 'chief1' },
  { re: /总部|科研项目处/, key: 'hqOffice' },
]

export function statusLabel(st?: string) {
  return ({ current: '当前', approved: '已办', done: '已办', pending: '待办', rejected: '驳回' } as Record<string, string>)[st || ''] || ''
}

export function who(node?: DeclareFlowStep | null) {
  if (!node) return '待指定'
  return node.owner?.label || node.owner?.name || node.assignee || '待指定'
}

export function peopleMapFrom(nodes: DeclareFlowStep[]): Partial<Record<SwimPeopleKey, string>> {
  const map: Partial<Record<SwimPeopleKey, string>> = {}
  for (const n of nodes) {
    const hit = TITLE_TO_SWIM.find((x) => x.re.test(n.title || ''))
    if (!hit) continue
    const name = who(n)
    if (name && name !== '待指定') map[hit.key] = name
  }
  return map
}

function postLabel(posts: Record<string, string | undefined> | undefined, key: string) {
  return posts?.[key] || undefined
}

/** 从申报单 posts / flowNode / status 生成审签节点 */
export function buildDeclareSteps(decl: Partial<ProjDeclaration> & { steps?: DeclareFlowStep[] }): DeclareFlowStep[] {
  if (Array.isArray(decl.steps) && decl.steps.length) return decl.steps

  const workflowId = declarationWorkflowId(decl as any)
  const need = workflowId !== 'report-v1' && workflowId !== 'legacy-report-v0' && decl.needApproval !== 0
  const posts = (decl.posts || {}) as Record<string, string | undefined>
  const titles = declarationNodes(decl as any).map((n) => n.title)

  const assigneeByTitle: Record<string, string | undefined> = {
    项目联系人: postLabel(posts, 'contact') || decl.applicant,
    项目负责人: postLabel(posts, 'leader'),
    项目承担部门负责人: postLabel(posts, 'deptHead') || postLabel(posts, 'unitTechDirector'),
    二级总师: postLabel(posts, 'chief2'),
    单位财务部门负责人: postLabel(posts, 'unitFinanceDirector') || postLabel(posts, 'unitFinanceSupervisor'),
    单位科技部门负责人: postLabel(posts, 'unitTechDirector'),
    单位分管领导: postLabel(posts, 'unitLeader') || postLabel(posts, 'unitTechDirector') || postLabel(posts, 'unitTechSupervisor'),
    一级总师: postLabel(posts, 'chief1'),
    总部科研项目处: postLabel(posts, 'hqDirector') || postLabel(posts, 'hqSupervisor'),
    线上报备归档: postLabel(posts, 'contact') || decl.applicant,
  }

  const currentTitle = decl.flowNode || ''
  const status = decl.status || 'DRAFT'

  const templateNodes = declarationNodes(decl as any)
  return titles.map((title, index) => {
    const template = templateNodes[index]
    const label = template?.roleKeys.map((key) => posts[key]).find(Boolean) || assigneeByTitle[title]
    let st: FlowStepStatus = 'pending'
    if (status === 'APPROVED' || status === 'REPORTED') st = 'approved'
    else if (status === 'REJECTED' && title === '项目联系人') st = 'current'
    else if (status === 'REJECTED') st = 'approved'
    else if (status === 'DRAFT' || status === 'SUBMITTED') {
      st = title === '项目联系人' ? (status === 'SUBMITTED' ? 'approved' : 'current') : 'pending'
    } else if (status === 'APPROVING') {
      if (currentTitle && title === currentTitle) st = 'current'
      else if (currentTitle) {
        const ci = titles.indexOf(currentTitle as any)
        const ti = titles.indexOf(title as any)
        if (ci >= 0 && ti >= 0 && ti < ci) st = 'approved'
        else st = 'pending'
      } else st = 'pending'
    }
    return {
      title,
      status: st,
      owner: label ? { name: label.split('（')[0], label } : undefined,
      assignee: label,
    }
  })
}

export function buildDeclareFlowOpts(decl: Partial<ProjDeclaration> & { steps?: DeclareFlowStep[] }, extras?: {
  channelLevel?: LevelCode | string
  transferred?: boolean
  materials?: LiveMaterial[]
  declareMaterial?: string | string[]
}): DeclareFlowOpts {
  const workflowId = declarationWorkflowId(decl as any)
  const need = decl.needApproval !== 0
  const nodes = buildDeclareSteps(decl)
  const contact = nodes.find((n) => /联系人/.test(n.title)) || nodes[0]
  const level = extras?.channelLevel || decl.levelCode
  const levelText = level ? (LEVEL_TEXT[level as LevelCode] || String(level)) : undefined
  const required = splitMaterialNames(
    extras?.declareMaterial ||
      (decl as any).channelDeclareMaterial ||
      (decl as any).declareMaterial,
  )
  const materials =
    extras?.materials ||
    liveMaterials({
      requiredNames: required,
      records: (decl as any).materials || (decl as any).declarationMaterials,
    })
  return {
    needApproval: need,
    code: decl.applyNo,
    name: decl.name,
    channel: decl.channelName,
    channelLevel: levelText,
    nodes,
    contactLabel: who(contact),
    transferred: extras?.transferred === true,
    materials,
    materialSummary: materialSummary(materials) || (required.length ? required.join('、') : ''),
    offlineTail: ['common-v1', 'xx25-v1'].includes(workflowId)
      ? '线上审签完成后，余下流程由科技部按渠道线下办理报批。'
      : undefined,
  }
}

export function compactChain(nodes: DeclareFlowStep[], max = 4) {
  if (nodes.length <= max) return { show: nodes, hidden: 0 }
  const curIdx = nodes.findIndex((n) => n.status === 'current')
  const i = curIdx >= 0 ? curIdx : nodes.findIndex((n) => n.status === 'pending')
  const start = Math.max(0, (i < 0 ? nodes.length - 3 : i) - 1)
  const end = Math.min(nodes.length, start + max)
  const show = nodes.slice(start, end)
  return { show, hidden: nodes.length - show.length }
}

export function currentItems(nodes: DeclareFlowStep[]) {
  return nodes.filter((n) => {
    const st = n.status || 'pending'
    return st === 'current' || st === 'rejected'
  })
}
