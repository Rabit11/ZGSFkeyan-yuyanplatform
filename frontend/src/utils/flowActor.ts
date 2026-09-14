import type { IdentityCode } from '@/constants/permission'

export type FlowHandler = {
  employeeNo?: string
  name?: string
  label?: string
}

export type ActorUser = {
  employeeNo?: string
  username?: string
  realName?: string
  identityCode?: string
}

function digits(v?: string) {
  return String(v || '').replace(/\D/g, '')
}

/** 当前登录人是否为该节点指定办理人（按工号 / 姓名） */
export function canActOnHandlers(handlers?: FlowHandler[] | null, user?: ActorUser | null) {
  if (!handlers?.length || !user) return false
  const myNo = digits(user.employeeNo || user.username)
  if (myNo) {
    const hit = handlers.some((h) => {
      const no = digits(h.employeeNo || h.label)
      return no && no === myNo
    })
    if (hit) return true
  }
  const myName = (user.realName || '').trim()
  if (!myName) return false
  return handlers.some((h) => {
    const n = (h.name || '').trim()
    if (n && n === myName) return true
    const label = h.label || ''
    return label.startsWith(myName)
  })
}

/** 当前审批节点对应的任职身份（仅这些身份可点「审批通过」） */
export function identitiesForFlowNode(node?: string): IdentityCode[] {
  const t = String(node || '')
  if (/联系人/.test(t)) return ['contactLogin', 'owner']
  if (/承担部门|承办部门/.test(t)) return ['deptHead']
  if (/项目负责人/.test(t) && !/处/.test(t)) return ['owner']
  if (/二级总师/.test(t)) return ['chief2']
  if (/一级总师/.test(t)) return ['chief1']
  if (/总部/.test(t) && /财务/.test(t)) return ['finHq']
  if (/财务/.test(t)) return ['finHead', 'finStaff']
  if (/法务/.test(t)) return ['hqHead', 'hqStaff']
  if (/总部/.test(t) || /科研项目处|科技主管/.test(t)) return ['hqHead', 'hqStaff']
  if (/二级单位|内审|科技部门|分管|主管部门/.test(t)) return ['unitHead', 'unitStaff']
  return ['unitHead', 'hqHead', 'hqStaff']
}

export function canAuditByIdentity(node: string | undefined, identityCode?: string) {
  if (!identityCode) return false
  return identitiesForFlowNode(node).includes(identityCode as IdentityCode)
}

export const AUDIT_IDENTITIES = {
  planFinish: ['unitHead'] as IdentityCode[],
  acceptFinish: ['unitHead', 'hqHead', 'hqStaff'] as IdentityCode[],
  fundUnit: ['finHead', 'finStaff'] as IdentityCode[],
  fundHq: ['finHq'] as IdentityCode[],
  fundOwner: ['owner'] as IdentityCode[],
  team: ['owner', 'techLead', 'projectPm', 'contactLogin'] as IdentityCode[],
}

export function hasIdentity(code: string | undefined, allowed: IdentityCode[]) {
  return !!code && allowed.includes(code as IdentityCode)
}
