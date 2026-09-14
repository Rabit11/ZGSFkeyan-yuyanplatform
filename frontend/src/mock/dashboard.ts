import * as DB from './data'
import type { ProjInfo, SysUser } from '@/api/types'
import { canViewVisualBoard } from '@/constants/permission'
import { aggregateDashboard, type DashboardQuery } from '@/utils/dashboardAgg'

export type { DashboardQuery }
export { stdStatus, isRunningStatus, aggregateDashboard } from '@/utils/dashboardAgg'

function isMember(p: ProjInfo, user?: SysUser | null) {
  if (!user) return false
  const members = p.teamMembers || []
  return members.some(
    (m) =>
      (user.employeeNo && m.employeeNo === user.employeeNo) ||
      (user.realName && m.userName === user.realName) ||
      (user.username && m.employeeNo === user.username),
  )
}

export function currentMockUser(): SysUser | undefined {
  const uid = Number(localStorage.getItem('rpm_uid') || 0)
  return DB.users.find((u) => u.id === uid) || DB.users[0]
}

export function canAccessPreResearch(user?: SysUser | null) {
  return canViewVisualBoard(user)
}

export function visibleProjects(user?: SysUser | null): ProjInfo[] {
  const all = [...DB.projects]
  if (!user) return all
  if (user.roles?.includes('ADMIN') || user.dataScope === 'COMPANY') return all
  if (user.dataScope === 'UNIT') {
    return all.filter(
      (p) => p.orgId === user.orgId || p.leadOrgId === user.orgId || isMember(p, user),
    )
  }
  return all.filter((p) => isMember(p, user))
}

export function buildDashboard(query: DashboardQuery = {}, user?: SysUser | null) {
  return aggregateDashboard(query, {
    projects: visibleProjects(user),
    channels: DB.channels,
    milestones: DB.milestones,
    plans: DB.plans,
    deliverables: DB.deliverables,
    transforms: DB.transforms,
    budgets: DB.budgets,
    payments: DB.payments,
    changes: DB.changes,
    partnerEvals: DB.partnerEvals,
    blacklist: DB.blacklist,
  })
}
