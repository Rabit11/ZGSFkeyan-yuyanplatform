/** 立项备案阶段 · 流转图（对齐「立项备案流转图」弹窗截图） */

import type { ProjDeclaration } from '@/api/types'
import {
  inferFilingPhase,
  liveMaterials,
  materialSummary,
  splitMaterialNames,
} from '@/utils/flowLive'

export type FilingNodeStatus = 'done' | 'current' | 'pending' | 'result'

export interface FilingFlowPerson {
  role: string
  employeeNo?: string
  name?: string
  label: string
}

export interface FilingFlowNode {
  nodeCode: string
  title: string
  /** 第二行说明 / 材料摘要 */
  desc?: string
  /** 岗位 · 动作，如「项目负责人 · 上传」 */
  actionLine?: string
  status: FilingNodeStatus
  statusLabel: string
  handlers: FilingFlowPerson[]
  materials?: string[]
}

export interface FilingAttachment {
  code: string
  name: string
  uploaded: boolean
  fileName?: string
  uploadedAt?: string
}

export interface FilingTimelineItem {
  title: string
  operator: string
  at: string
  comment?: string
  result?: string
}

export interface FilingFlowOpts {
  code?: string
  name?: string
  /** 副标题状态，如「实施中」 */
  projectStatusLabel?: string
  channelName?: string
  ownerLabel: string
  auditorLabel: string
  /** 当前节点条文案；空表示「暂无进行中节点」 */
  currentTip: string
  panelStatus: 'HANDLING' | 'DONE' | 'RETURNED'
  panelStatusLabel: string
  auditConclusion: string
  nodes: FilingFlowNode[]
  /** 本渠道必传材料（底部红框 + F2 摘要同源） */
  channelMaterials: string[]
  returnActive: boolean
  attachments: FilingAttachment[]
  timeline: FilingTimelineItem[]
  processHint: string
}

/** 岗位身份 → 默认工号（仅当接口已给出该工号时用于补全姓名，不再拿花名册顶替接口空值） */
export const FILING_ROLE_EMPLOYEE: Record<string, string> = {
  chief2: '100008',
  chief1: '100007',
  hqDirector: '100003',
  owner: '100001',
  unitHead: '100005',
  unitStaff: '100006',
  contact: '100013',
}

export const DEFAULT_CHANNEL_MATERIALS: string[] = []

function statusLabelOf(st: FilingNodeStatus) {
  return ({ done: '已办', current: '当前', pending: '待办', result: '' } as Record<FilingNodeStatus, string>)[st]
}

function parsePostLabel(raw?: string): FilingFlowPerson | null {
  if (!raw) return null
  const text = String(raw).trim()
  if (!text || text === '待指定') return null
  const m = text.match(/^(.+?)[（(](\d{5,})[）)]$/)
  if (m) {
    return {
      role: '',
      name: m[1],
      employeeNo: m[2],
      label: `${m[1]}（${m[2]}）`,
    }
  }
  return { role: '', name: text, label: text }
}

/** 按岗位身份取人：只认申报 posts / 团队接口，缺人显示待指定 */
export function resolveByIdentity(
  roleKey: keyof typeof FILING_ROLE_EMPLOYEE,
  roleTitle: string,
  posts?: Record<string, string | undefined>,
  postKeys: string[] = [],
): FilingFlowPerson {
  for (const k of postKeys) {
    const hit = parsePostLabel(posts?.[k])
    if (hit) return { ...hit, role: roleTitle }
  }
  const raw = posts?.[roleKey]
  const fromKey = parsePostLabel(raw)
  if (fromKey) return { ...fromKey, role: roleTitle }
  return { role: roleTitle, label: '待指定', name: '待指定' }
}

function whoLine(handlers: FilingFlowPerson[]) {
  return handlers.map((h) => h.label).filter(Boolean).join('、') || '待指定'
}

export function who(node?: FilingFlowNode | null) {
  if (!node) return '待指定'
  return whoLine(node.handlers)
}

export function resolveChannelMaterials(
  raw?: string | string[] | null,
): string[] {
  return splitMaterialNames(raw)
}

type FilingPhase = 'SUBMIT' | 'AUDIT' | 'PASS' | 'RETURN' | 'DONE'

function inferPhase(decl: Partial<ProjDeclaration> & { filingPhase?: string; projectStatus?: string; filingStatus?: string }): FilingPhase {
  if (
    decl.filingPhase === 'RETURN' ||
    decl.filingPhase === 'SUBMIT' ||
    decl.filingPhase === 'AUDIT' ||
    decl.filingPhase === 'PASS' ||
    decl.filingPhase === 'DONE'
  ) {
    return decl.filingPhase
  }
  const inferred = inferFilingPhase(decl.projectStatus || decl.status, (decl as any).filingStatus)
  return inferred as FilingPhase
}

function nodeStatus(phase: FilingPhase, code: string): FilingNodeStatus {
  const order = ['FILING_DECLARE_DONE', 'FILING_SUBMIT', 'FILING_AUDIT', 'FILING_PASS_IMPL']
  if (phase === 'DONE') {
    if (code === 'FILING_PASS_IMPL') return 'result'
    return 'done'
  }
  if (phase === 'RETURN') {
    if (code === 'FILING_DECLARE_DONE') return 'done'
    if (code === 'FILING_SUBMIT') return 'current'
    if (code === 'FILING_PASS_IMPL') return 'pending'
    return 'pending'
  }
  if (phase === 'PASS') {
    if (code === 'FILING_PASS_IMPL') return 'result'
    return 'done'
  }
  const cur =
    phase === 'SUBMIT' ? 'FILING_SUBMIT' : phase === 'AUDIT' ? 'FILING_AUDIT' : 'FILING_PASS_IMPL'
  const ci = order.indexOf(cur)
  const ti = order.indexOf(code)
  if (ti < 0) return 'pending'
  if (ti < ci) return 'done'
  if (ti === ci) return code === 'FILING_PASS_IMPL' ? 'result' : 'current'
  return 'pending'
}

function fmtNow() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function projectStatusLabelOf(
  decl: Partial<ProjDeclaration> & { projectStatusLabel?: string; projectStatus?: string },
  phase: FilingPhase,
) {
  if (decl.projectStatusLabel) return decl.projectStatusLabel
  const st = String(decl.projectStatus || '')
  if (st === 'IMPLEMENTING' || phase === 'DONE' || phase === 'PASS') return '实施中'
  if (st === 'FILING' || phase === 'SUBMIT' || phase === 'AUDIT' || phase === 'RETURN') return '立项中'
  if (st === 'DECLARING') return '申报中'
  return phase === 'DONE' || phase === 'PASS' ? '实施中' : '立项中'
}

/** 从申报单 / 项目概览生成立项备案流转图数据 */
export function buildFilingFlowOpts(
  decl:
    | (Partial<ProjDeclaration> & {
        filingPhase?: string
        filingMaterial?: string
        channelFilingMaterial?: string
        projectStatusLabel?: string
        projectStatus?: string
        filingStatus?: string
      })
    | null
    | undefined,
): FilingFlowOpts {
  const posts = (decl?.posts || {}) as Record<string, string | undefined>
  const phase = inferPhase(decl || {})
  const requiredNames = resolveChannelMaterials(
    (decl as any)?.channelFilingMaterial ||
      (decl as any)?.filingMaterial ||
      (Array.isArray((decl as any)?.filingMaterialList) ? (decl as any).filingMaterialList : undefined),
  )
  const live = liveMaterials({
    requiredNames,
    records:
      (Array.isArray((decl as any)?.filingMaterials) &&
      (decl as any).filingMaterials.some((x: any) => typeof x === 'object')
        ? (decl as any).filingMaterials
        : null) ||
      (decl as any)?.materials ||
      (decl as any)?.declarationMaterials,
  })
  const names = live.length ? live.map((m) => m.name) : requiredNames

  const owner = resolveByIdentity('owner', '项目负责人', posts, ['leader'])
  const auditor = resolveByIdentity('hqDirector', '总部科技部科研项目处', posts, [
    'hqDirector',
    'hqSupervisor',
  ])

  const nodes: FilingFlowNode[] = [
    {
      nodeCode: 'FILING_DECLARE_DONE',
      title: '申报审签 / 线上报备办结',
      desc: '上一节点完成后进入立项支撑材料办理',
      actionLine: '系统',
      status: nodeStatus(phase, 'FILING_DECLARE_DONE'),
      statusLabel: '',
      handlers: [{ role: '系统', label: '系统', name: '系统' }],
    },
    {
      nodeCode: 'FILING_SUBMIT',
      title: '项目负责人提交立项支撑材料',
      desc: materialSummary(live) || names.join('、') || '按本渠道必传清单上传',
      actionLine: '项目负责人 · 上传',
      status: nodeStatus(phase, 'FILING_SUBMIT'),
      statusLabel: '',
      handlers: [owner],
      materials: names,
    },
    {
      nodeCode: 'FILING_AUDIT',
      title: '总部科技部科研项目处审核备案',
      desc: '驳回则退回项目负责人补正',
      actionLine: '总部处室处长 · 审核备案',
      status: nodeStatus(phase, 'FILING_AUDIT'),
      statusLabel: '',
      handlers: [auditor],
    },
    {
      nodeCode: 'FILING_PASS_IMPL',
      title: '立项支撑材料审核通过，进入实施阶段',
      desc: '台账状态转为实施中',
      status: nodeStatus(phase, 'FILING_PASS_IMPL'),
      statusLabel: '',
      handlers: [],
    },
  ]

  nodes.forEach((n) => {
    n.statusLabel = statusLabelOf(n.status)
  })

  const attachments: FilingAttachment[] = live.map((m) => ({
    code: m.code,
    name: m.name,
    uploaded: m.uploaded,
    fileName: m.fileName,
    uploadedAt: m.uploadedAt,
  }))

  const timeline: FilingTimelineItem[] = []
  if (phase !== 'SUBMIT' || decl?.status === 'APPROVED' || decl?.status === 'REPORTED') {
    timeline.push({
      title: '项目负责人提交立项支撑材料',
      operator: owner.label,
      at: attachments.find((a) => a.uploaded)?.uploadedAt || (decl?.applyAt ? String(decl.applyAt).slice(0, 16).replace('T', ' ') : fmtNow()),
      comment: materialSummary(live) || '已提交',
      result: phase === 'RETURN' ? '已退回补正' : '已通过',
    })
  }
  if (phase === 'AUDIT' || phase === 'PASS' || phase === 'DONE') {
    timeline.push({
      title: '总部科技部科研项目处审核备案',
      operator: auditor.label,
      at: fmtNow(),
      comment: phase === 'DONE' || phase === 'PASS' ? '审核通过' : '审核中',
      result: phase === 'DONE' || phase === 'PASS' ? '已通过' : '办理中',
    })
  }
  if (phase === 'DONE' || phase === 'PASS') {
    timeline.push({
      title: '立项支撑材料审核通过，进入实施阶段',
      operator: '系统',
      at: fmtNow(),
      comment: '台账状态转为实施中',
      result: '已办结',
    })
  }
  if (phase === 'RETURN') {
    timeline.push({
      title: '驳回退回项目负责人补正',
      operator: auditor.label,
      at: fmtNow(),
      comment: names.join('、'),
      result: '已退回',
    })
  }

  let panelStatus: FilingFlowOpts['panelStatus'] = 'HANDLING'
  let panelStatusLabel = '办理中'
  if (phase === 'DONE' || phase === 'PASS') {
    panelStatus = 'DONE'
    panelStatusLabel = '已办结'
  } else if (phase === 'RETURN') {
    panelStatus = 'RETURNED'
    panelStatusLabel = '已退回'
  }

  const projName = decl?.name || '未命名项目'
  const auditConclusion =
    phase === 'DONE' || phase === 'PASS'
      ? `【${projName}】立项支撑材料审核（已通过）`
      : phase === 'RETURN'
        ? `【${projName}】立项支撑材料审核（已退回）`
        : phase === 'AUDIT'
          ? `【${projName}】立项支撑材料审核（审核中）`
          : `【${projName}】立项支撑材料（待提交/待审核）`

  const currentNode = nodes.find((n) => n.status === 'current')
  const currentTip = currentNode
    ? currentNode.title
    : phase === 'RETURN'
      ? '项目负责人提交立项支撑材料（补正）'
      : '立项备案暂无进行中节点'

  return {
    code: decl?.applyNo,
    name: decl?.name,
    projectStatusLabel: projectStatusLabelOf(decl || {}, phase),
    channelName: decl?.channelName,
    ownerLabel: owner.label,
    auditorLabel: auditor.label,
    currentTip,
    panelStatus,
    panelStatusLabel,
    auditConclusion,
    nodes,
    channelMaterials: names,
    returnActive: phase === 'RETURN',
    attachments,
    timeline,
    processHint:
      '项目负责人提交立项支撑材料 → 总部科技部科研项目处审核备案 → 审核通过后台账转为实施中；驳回则退回项目负责人补正后重新提交。',
  }
}

export function currentFilingNodes(nodes: FilingFlowNode[]) {
  return nodes.filter((n) => n.status === 'current')
}
