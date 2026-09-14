/** 项目验收阶段 · 流转图（对齐「项目验收 · 详情与附件」） */

import { personnelDisplay } from '@/constants/personnel'
import { fmtAmount, fmtDate } from '@/utils/format'

export type AcceptNodeStatus = 'done' | 'current' | 'pending' | 'return' | 'closed'
export type AcceptNodeType = 'ACTION' | 'AUDIT' | 'SYSTEM' | 'CLOSED'
export type AcceptPhase = 'GATE' | 'APPLY' | 'UNIT' | 'CHIEF' | 'FINAL' | 'DONE' | 'RETURN'

export interface AcceptPerson {
  role: string
  employeeNo?: string
  name?: string
  label: string
}

export interface AcceptCheck {
  key: string
  label: string
  passed: boolean
  message: string
}

export interface AcceptMaterialFile {
  name: string
  fileName?: string
  uploader?: string
  uploadedAt?: string
  fileSize?: string
  fileUrl?: string
  uploaded: boolean
}

export interface AcceptMaterialGroup {
  code: string
  name: string
  locked: boolean
  uploaded: boolean
  files: AcceptMaterialFile[]
}

export interface AcceptFlowNode {
  nodeCode: string
  title: string
  lane: string
  desc?: string
  actionLine?: string
  nodeType: AcceptNodeType
  status: AcceptNodeStatus
  statusLabel: string
  handlers: AcceptPerson[]
  opinion?: string
  at?: string
  diamond?: boolean
}

export interface AcceptTimelineItem {
  title: string
  operator: string
  at: string
  comment?: string
  result?: string
  status: string
}

export interface AcceptFlowOpts {
  code?: string
  name?: string
  ownerLabel: string
  panelStatus: 'HANDLING' | 'DONE' | 'NOT_STARTED'
  panelStatusLabel: string
  currentContent: string
  latestProcess: string
  doneCount: number
  totalCount: number
  nextAction: string
  nextActionPath?: string
  finishAt?: string
  partnerDueDate?: string
  conclusion?: string
  acceptLevel?: string
  acceptLevelLabel: string
  expertReview: boolean
  checks: AcceptCheck[]
  allChecksPassed: boolean
  nodes: AcceptFlowNode[]
  groups: AcceptMaterialGroup[]
  timeline: AcceptTimelineItem[]
  processHint: string
}

export const ACCEPT_ROLE_EMPLOYEE = {
  owner: '100012',
  unitHead: '100005',
  chief: '100007',
  hq: '100003',
  system: '100001',
}

const LEVEL_NAME: Record<string, string> = {
  UNIT: '单位级验收材料',
  COMPANY: '公司级验收材料',
  NATIONAL: '国家级验收材料',
  LOCAL: '属地主管部门验收材料',
}

const LEVEL_MATS: Record<string, string[]> = {
  UNIT: ['验收申请书', '技术总结报告', '经费决算表', '交付物清单'],
  COMPANY: ['公司级验收申请表', '评审专家意见', '验收结论'],
  NATIONAL: ['国家级验收申请', '主管机关批复', '综合绩效评价材料'],
  LOCAL: ['属地验收申请', '科委验收意见', '综合绩效评价材料'],
}

function statusLabelOf(st: AcceptNodeStatus) {
  return ({ done: '已办', current: '当前', pending: '待办', return: '退回', closed: '已办结' } as Record<AcceptNodeStatus, string>)[st]
}

function fromRoster(empNo: string, role: string): AcceptPerson {
  const label = personnelDisplay(empNo)
  return { role, employeeNo: empNo, name: label.split('（')[0], label }
}

function pickMember(members: any[] | undefined, roleKeys: string[], fallbackEmpNo: string, roleTitle: string): AcceptPerson {
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

export function who(node?: AcceptFlowNode | null) {
  if (!node?.handlers?.length) return '待指定'
  if (node.handlers[0]?.label === '系统') return '系统'
  return node.handlers.map((h) => h.label).filter(Boolean).join('、') || '待指定'
}

function allowedLevels(levelCode?: string) {
  if (levelCode === 'NATIONAL') return ['UNIT', 'COMPANY', 'NATIONAL']
  if (levelCode === 'LOCAL') return ['UNIT', 'LOCAL']
  return ['UNIT', 'COMPANY']
}

export function buildAcceptChecks(src: {
  milestones?: any[]
  deliverables?: any[]
  payments?: any[]
  evaluations?: any[]
}): AcceptCheck[] {
  const ms = src.milestones || []
  const dvs = src.deliverables || []
  const pays = src.payments || []
  const evals = src.evaluations || []

  const msOpen = ms.filter((m) => m.status !== 'DONE' && m.colorStatus !== 'GREEN').length
  const coreDone = dvs.filter((d) => d.status === 'DELIVERED').length
  const dvOverdue = dvs.filter((d) => d.status === 'OVERDUE' || d.colorStatus === 'RED').length
  const coreOk = (dvs.length === 0 || coreDone === dvs.length) && dvOverdue === 0
  const written = pays.filter((p) => p.writeoffStatus === 'WRITTEN')
  const payOpen = pays.filter((p) => p.writeoffStatus && p.writeoffStatus !== 'WRITTEN').length
  const fundOk = pays.length === 0 || payOpen === 0
  const writtenAmt = written.reduce((s, p) => s + Number(p.amount || 0), 0)
  const evalOpen = evals.filter((e) => e.result === 'FAIL' && e.status !== 'DONE').length

  return [
    {
      key: 'MILESTONE',
      label: '里程碑全闭环',
      passed: ms.length === 0 || msOpen === 0,
      message: msOpen === 0 ? '全部里程碑已完成销项' : `仍有 ${msOpen} 个里程碑未完成闭环`,
    },
    {
      key: 'CORE_DV',
      label: '核心交付物齐套',
      passed: coreOk,
      message: coreOk ? `核心交付物已交付 ${coreDone}/${dvs.length || coreDone}` : `已交付 ${coreDone}/${dvs.length}，逾期 ${dvOverdue} 项`,
    },
    {
      key: 'FUND',
      label: '经费核销与凭证归档',
      passed: fundOk,
      message: fundOk
        ? `已归档 ${written.length || 0} 张凭证，累计 ${fmtAmount(writtenAmt)} 万元`
        : `存在 ${payOpen} 笔经费未核销`,
    },
    {
      key: 'EVAL_RECTIFY',
      label: '不合格评估整改',
      passed: evalOpen === 0,
      message: evalOpen === 0 ? '无待整改不合格项' : `仍有 ${evalOpen} 项不合格评估未整改闭环`,
    },
  ]
}

function inferPhase(acc: any, projectStatus?: string): AcceptPhase {
  const accSt = String(acc?.status || '')
  const pst = String(projectStatus || '')
  if (accSt === 'DONE' || ['COMPANY_ACCEPTED', 'GOV_ACCEPTED', 'FINISHED'].includes(pst)) return 'DONE'
  if (accSt === 'APPLYING' && acc?.returned) return 'RETURN'
  if (accSt === 'ACCEPTING') {
    if (Number(acc?.expertReview) === 1 && !acc?.chiefDone) return 'CHIEF'
    return 'FINAL'
  }
  if (accSt === 'APPLYING') return 'UNIT'
  if (['ACCEPTING'].includes(pst)) return 'UNIT'
  if (accSt === 'CHECKING') return 'APPLY'
  return 'GATE'
}

function stOf(order: number, current: number, done: boolean, ret = false): AcceptNodeStatus {
  if (done) return order === 5 ? 'closed' : 'done'
  if (ret && order === 1) return 'return'
  if (order < current) return 'done'
  if (order === current) return 'current'
  return 'pending'
}

function nodeOf(
  code: string,
  title: string,
  lane: string,
  type: AcceptNodeType,
  status: AcceptNodeStatus,
  handlers: AcceptPerson[],
  extra: Partial<AcceptFlowNode> = {},
): AcceptFlowNode {
  return {
    nodeCode: code,
    title,
    lane,
    nodeType: type,
    status,
    statusLabel: statusLabelOf(status),
    handlers,
    ...extra,
  }
}

function buildGroups(levelCode: string | undefined, items: any[], ownerLabel: string, done: boolean): AcceptMaterialGroup[] {
  const allow = allowedLevels(levelCode)
  const byLevel: Record<string, any[]> = {}
  for (const it of items || []) {
    const code = String(it.levelCode || '')
    ;(byLevel[code] ||= []).push(it)
  }
  return Object.keys(LEVEL_MATS)
    .filter((code) => allow.includes(code) || (byLevel[code] || []).length)
    .map((code) => {
      const locked = !allow.includes(code)
      const list = byLevel[code] && byLevel[code].length
        ? byLevel[code]
        : LEVEL_MATS[code].map((name, i) => ({
            materialName: name,
            fieldCode: `${code}_${i}`,
            status: done && !locked ? 'UPLOADED' : 'EMPTY',
            locked: locked ? 1 : 0,
          }))
      const files: AcceptMaterialFile[] = list.map((it) => {
        const uploaded = it.status === 'UPLOADED' || !!it.fileUrl || (done && !locked)
        const matName = it.materialName || it.name || '验收材料'
        return {
          name: matName,
          fileName: it.fileName || (uploaded ? `${matName}.pdf` : undefined),
          uploader: it.uploadedBy || ownerLabel,
          uploadedAt: it.uploadedAt || it.updateTime,
          fileSize: it.fileSize || (uploaded ? '128 KB' : undefined),
          fileUrl: it.fileUrl,
          uploaded,
        }
      })
      return {
        code,
        name: LEVEL_NAME[code] || code,
        locked,
        uploaded: !locked && files.length > 0 && files.every((f) => f.uploaded),
        files,
      }
    })
}

export function buildAcceptFlowOptsFromOverview(overview: any, extra?: { items?: any[] }): AcceptFlowOpts {
  const p = overview?.project || overview || {}
  const acc = overview?.acceptance || {}
  const members = p.teamMembers || overview?.teamMembers || []
  const items = extra?.items || acc.items || []
  const owner = pickMember(members, ['PROJECT_LEADER', '项目负责人'], ACCEPT_ROLE_EMPLOYEE.owner, '项目负责人')
  const unitHead = pickMember(members, ['UNIT_MINISTER', '单位科技部长', '二级单位'], ACCEPT_ROLE_EMPLOYEE.unitHead, '二级单位管理团队')
  const chief = pickMember(members, ['L1_CHIEF', '一级总师', '责任总师'], ACCEPT_ROLE_EMPLOYEE.chief, '责任总师')
  const hq = pickMember(members, ['HQ_DIRECTOR', '总部处室处长', '总部管理'], ACCEPT_ROLE_EMPLOYEE.hq, '总部管理团队')
  const sys = fromRoster(ACCEPT_ROLE_EMPLOYEE.system, '系统')

  const levelCode = acc.acceptLevel || p.levelCode
  const expertReview = Number(acc.expertReview) === 1 || levelCode === 'NATIONAL'
  const checks = buildAcceptChecks({
    milestones: overview?.milestones,
    deliverables: overview?.deliverables,
    payments: overview?.payments,
    evaluations: overview?.evaluations,
  })
  const allChecksPassed = checks.every((c) => c.passed)
  const phase = inferPhase(acc, p.status)
  const done = phase === 'DONE'
  const currentOrder =
    phase === 'DONE' ? 99
      : phase === 'FINAL' ? 4
        : phase === 'CHIEF' ? 3
          : phase === 'UNIT' || phase === 'RETURN' ? 2
            : phase === 'APPLY' || allChecksPassed ? 1
              : 0

  const nodes: AcceptFlowNode[] = [
    nodeOf('ACCEPT_APPLY', '验收申请', '项目团队', 'ACTION', stOf(1, currentOrder, done, phase === 'RETURN'), [owner], {
      desc: '按项目层级上传本层级必传材料后提交',
      actionLine: '项目负责人 · 上传/提交',
    }),
    nodeOf('ACCEPT_UNIT_REVIEW', '初审', '二级单位管理团队', 'AUDIT', stOf(2, currentOrder, done), [unitHead], {
      desc: '材料齐套性、实施闭环与结论建议',
      actionLine: '二级单位管理团队 · 审批',
      diamond: true,
      opinion: done || currentOrder > 2 ? '同意' : undefined,
    }),
  ]
  if (expertReview) {
    nodes.push(nodeOf('ACCEPT_CHIEF_REVIEW', '责任总师技术复核', '责任总师', 'AUDIT', stOf(3, currentOrder, done), [chief], {
      desc: '国家级技术路线与指标达标复核',
      actionLine: '责任总师 · 审批',
      opinion: done || currentOrder > 3 ? '同意' : undefined,
    }))
  }
  nodes.push(
    nodeOf('ACCEPT_HQ_FINAL', '终审', '总规管理团队', 'AUDIT', stOf(4, currentOrder, done), [hq], {
      desc: '管理口终审，形成验收结论',
      actionLine: '总部管理团队 · 审批',
      opinion: done ? (acc.conclusion || '同意') : undefined,
    }),
    nodeOf('ACCEPT_ARCHIVE', '指标办归档', '指标办', done ? 'CLOSED' : 'SYSTEM', stOf(5, currentOrder, done), [sys], {
      desc: '终审通过后归档本阶段材料，开启协作评价倒计时',
      actionLine: '系统 · 归档办结',
    }),
  )

  const totalCount = nodes.length
  const doneCount = nodes.filter((n) => n.status === 'done' || n.status === 'closed').length
  const current = nodes.find((n) => n.status === 'current' || n.status === 'return')

  let nextAction = '去办理'
  let nextActionPath = '/acceptance/accept'
  if (done) {
    nextAction = '去协作单位评价'
    nextActionPath = '/acceptance/partner'
  } else if (!allChecksPassed || phase === 'GATE') {
    nextAction = '前置条件校验'
    nextActionPath = '/acceptance/accept'
  } else if (phase === 'APPLY' || phase === 'RETURN') {
    nextAction = phase === 'RETURN' ? '去补正' : '提交验收申请'
  } else if (current?.nodeType === 'AUDIT') {
    nextAction = '去审核'
  }

  const levelLabel = LEVEL_NAME[String(levelCode)]?.replace('材料', '') || String(levelCode || '—')
  const latestProcess = done
    ? `${levelCode === 'NATIONAL' ? '国家级' : levelCode === 'LOCAL' ? '属地' : '公司级'}验收申请（通过）`
    : current ? `${current.title}（${current.statusLabel}）` : '待发起'

  const finishAt = acc.finishAt ? fmtDate(acc.finishAt) : done ? fmtDate(new Date().toISOString()) : undefined
  const groups = buildGroups(p.levelCode || levelCode, items, owner.label, done)

  const timeline: AcceptTimelineItem[] = nodes
    .filter((n) => n.status === 'done' || n.status === 'closed' || n.status === 'current')
    .map((n) => ({
      title: n.title,
      operator: who(n),
      at: n.at || finishAt || '—',
      comment: n.opinion,
      result: n.status === 'closed' || n.status === 'done' ? '同意' : n.statusLabel,
      status: n.status,
    }))

  return {
    code: p.projectNo,
    name: p.name,
    ownerLabel: owner.label,
    panelStatus: done ? 'DONE' : phase === 'GATE' ? 'NOT_STARTED' : 'HANDLING',
    panelStatusLabel: done ? '已完成' : phase === 'GATE' ? '未启动' : '验收中',
    currentContent: done ? '已完成' : current ? `${current.lane} · ${current.title}` : '待发起',
    latestProcess,
    doneCount: done ? totalCount : doneCount,
    totalCount,
    nextAction,
    nextActionPath,
    finishAt,
    partnerDueDate: acc.partnerDueDate,
    conclusion: acc.conclusion,
    acceptLevel: levelCode,
    acceptLevelLabel: levelLabel,
    expertReview,
    checks,
    allChecksPassed,
    nodes,
    groups,
    timeline,
    processHint: done
      ? '验收已办结。请于 30 日内完成协作单位评价；本阶段材料已归档，不等于项目完成归档。'
      : allChecksPassed
        ? '前置条件已满足，按审批流逐级办理；退回则回到项目团队补正。'
        : '前置四项须全部通过后才能提交验收申请。',
  }
}

export function currentAcceptNodes(flow: AcceptFlowOpts) {
  return flow.nodes.filter((n) => n.status === 'current' || n.status === 'return')
}
