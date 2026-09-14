/** 项目台账 · 平台级生命周期（固定 6 节点） */

export type LifecycleStatus = 'DONE' | 'TODO' | 'PENDING'

export interface LifecycleNode {
  seq: number
  nodeCode: string
  nodeName: string
  status: LifecycleStatus
  ownerRole: string
  ownerName: string
  finishStatus?: string
  nextFlowName?: string
  nextHandlerRole?: string
  nextHandlerName?: string
  hint?: string
  tracks?: { time: string; action: string; actor: string; opinion?: string }[]
}

export interface LifecycleTeamMember {
  roleCode?: string
  roleName?: string
  userName?: string
}

const NODE_DEFS = [
  { seq: 1, nodeCode: 'DECLARE', nodeName: '项目申报', ownerRole: '项目联系人' },
  { seq: 2, nodeCode: 'FILING', nodeName: '立项备案', ownerRole: '项目负责人' },
  { seq: 3, nodeCode: 'IMPLEMENT', nodeName: '实施阶段', ownerRole: '项目负责人' },
  { seq: 4, nodeCode: 'ACCEPT', nodeName: '项目验收', ownerRole: '项目负责人' },
  { seq: 5, nodeCode: 'TRANSFORM', nodeName: '成果转化', ownerRole: '项目负责人' },
  { seq: 6, nodeCode: 'ARCHIVE', nodeName: '项目完成归档', ownerRole: '单位科技部长' },
] as const

/** 当前待办节点下标：0~5；全部完成返回 6 */
export function currentLifecycleIndex(projectStatus?: string): number {
  switch (projectStatus) {
    case 'DRAFT':
    case 'DECLARING':
      return 0
    case 'IMPLEMENTING':
    case 'DELAYED':
      return 2
    case 'ACCEPTING':
      return 3
    case 'COMPANY_ACCEPTED':
    case 'GOV_ACCEPTED':
      return 4
    case 'FINISHED':
      return 6
    default:
      return 2
  }
}

function pickName(
  members: LifecycleTeamMember[] | undefined,
  roleNames: string[],
  fallback?: string,
): string {
  for (const role of roleNames) {
    const hit = (members || []).find((m) => m.roleName === role || m.roleCode === role)
    if (hit?.userName) return hit.userName
  }
  return fallback || '待指定'
}

const DEPT_LABEL: Record<string, string> = {
  GXB: '工信部',
  FGW: '发改委',
}

export function channelPathLabel(ch?: {
  channelDept?: string
  channelOffice?: string
  channelName?: string
}): string {
  if (!ch) return '-'
  const dept = DEPT_LABEL[ch.channelDept || ''] || ch.channelDept || ''
  return [dept, ch.channelOffice, ch.channelName].filter(Boolean).join(' → ')
}

export function buildLifecycle(opts: {
  status?: string
  teamMembers?: LifecycleTeamMember[]
  createByName?: string
  transformDone?: boolean
}): LifecycleNode[] {
  const todoIdx = currentLifecycleIndex(opts.status)
  const contact = pickName(opts.teamMembers, ['项目联系人', '项目主管'], opts.createByName)
  const leader = pickName(opts.teamMembers, ['项目负责人'], opts.createByName)
  const minister = pickName(opts.teamMembers, ['单位科技部长'], '待指定')
  const unitHandler = pickName(opts.teamMembers, ['单位科技主管', '二级总师'], '待指定')

  return NODE_DEFS.map((def, i) => {
    let status: LifecycleStatus = 'PENDING'
    if (todoIdx >= 6 || i < todoIdx) status = 'DONE'
    else if (i === todoIdx) status = 'TODO'

    // 成果转化已完成时，归档为待办
    if (opts.transformDone && todoIdx === 4 && i === 4) status = 'DONE'
    if (opts.transformDone && todoIdx === 4 && i === 5) status = 'TODO'

    const ownerName =
      def.nodeCode === 'DECLARE' ? contact : def.nodeCode === 'ARCHIVE' ? minister : leader

    const node: LifecycleNode = {
      seq: def.seq,
      nodeCode: def.nodeCode,
      nodeName: def.nodeName,
      status,
      ownerRole: def.ownerRole,
      ownerName,
    }

    if (status === 'DONE') {
      node.finishStatus = '已办结'
      node.tracks = [
        { time: '2026-01-10 09:20', action: '提交办理', actor: ownerName },
        { time: '2026-01-12 16:40', action: '审核通过并办结', actor: unitHandler, opinion: '同意' },
      ]
    } else {
      node.nextFlowName =
        def.nodeCode === 'ARCHIVE' ? '二级单位管理团队办结归档' : '二级单位管理团队办理'
      node.nextHandlerRole = '二级单位管理团队办理人'
      node.nextHandlerName = unitHandler
      // 待办 / 未办理均可点击只读查看详情与流转图
      node.hint =
        status === 'TODO'
          ? '点击查看详情（只读），办理请从左侧任务栏进入'
          : '点击查看详情（未办理也可预览）'
      if (status === 'TODO') {
        node.tracks = [
          { time: '2026-08-20 10:00', action: '进入本节点', actor: ownerName },
          { time: '—', action: '待办理', actor: unitHandler },
        ]
      } else {
        node.tracks = [{ time: '—', action: '尚未进入本节点（只读预览）', actor: ownerName }]
      }
    }
    return node
  })
}

export function stageBadge(status?: string): { text: string; color: string } | null {
  switch (status) {
    case 'ACCEPTING':
      return { text: '验收中', color: 'processing' }
    case 'COMPANY_ACCEPTED':
    case 'GOV_ACCEPTED':
      return { text: '待归档', color: 'default' }
    case 'FINISHED':
      return { text: '已归档', color: 'success' }
    case 'DECLARING':
      return { text: '申报中', color: 'blue' }
    default:
      return null
  }
}
