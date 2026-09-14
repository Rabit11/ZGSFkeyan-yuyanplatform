/** 将工作定责目录解析到本项目实名办理人，并判断当前用户可办动作 */

import type { IdentityCode } from '@/constants/permission'
import {
  WORK_ACTION_LABEL,
  workDutyByCode,
  type WorkAction,
  type WorkActorRule,
  type WorkDutyCode,
  type WorkDutyDef,
} from '@/constants/workDuty'
import { canActOnHandlers, type ActorUser } from '@/utils/flowActor'
import { TEAM_POST_KEYS, findTeamMember, personLabelOf } from '@/utils/flowLive'

export type DutyPerson = {
  role: string
  employeeNo?: string
  name?: string
  label: string
}

export type WorkDutyContext = {
  teamMembers?: any[]
  posts?: Record<string, any>
  ownerName?: string
  createByName?: string
}

export type DutyActorUser = ActorUser & {
  identityCode?: string
  roles?: string[]
}

export type ResolvedAction = {
  people: DutyPerson[]
  labels: string
  can: boolean
  reason: string
  pending: boolean
}

export type ResolvedDuty = {
  def: WorkDutyDef
  fill: ResolvedAction
  submit: ResolvedAction
  audit: ResolvedAction
  view: ResolvedAction
  edit: ResolvedAction
  mine: WorkAction[]
}

export const IDENTITY_TO_POST_KEY: Record<string, string> = {
  contactLogin: 'contact',
  owner: 'leader',
  techLead: 'techLeader',
  projectPm: 'supervisor',
  chief1: 'chief1',
  chief2: 'chief2',
  hqHead: 'hqDirector',
  hqStaff: 'hqSupervisor',
  unitHead: 'unitTechDirector',
  unitStaff: 'unitTechSupervisor',
  deptHead: 'deptHead',
  finHq: 'hqFinance',
  finHead: 'unitFinanceDirector',
  finStaff: 'unitFinanceSupervisor',
}

function asCtx(project: any): WorkDutyContext {
  if (!project) return {}
  return {
    teamMembers: project.teamMembers || project.members,
    posts: project.posts || project.declaration?.posts,
    ownerName: project.ownerName,
    createByName: project.createByName,
  }
}

function personFromMember(m: any, role: string): DutyPerson | null {
  const label = personLabelOf(m)
  if (!label) return null
  return {
    role,
    employeeNo: String(m.employeeNo || '').replace(/\D/g, '') || undefined,
    name: String(m.userName || m.realName || m.name || '').trim() || undefined,
    label,
  }
}

function personFromText(raw: any, role: string): DutyPerson | null {
  if (raw == null) return null
  if (typeof raw === 'object') return personFromMember(raw, role)
  const text = String(raw).trim()
  if (!text || text === '待指定') return null
  const m = text.match(/^(.+?)[（(](\d+)[）)]$/)
  if (m) return { role, name: m[1], employeeNo: m[2], label: `${m[1]}（${m[2]}）` }
  return { role, name: text, label: text }
}

export function namedPeopleForPosts(ctx: WorkDutyContext, postKeys: string[], roleLabels: string[]): DutyPerson[] {
  const out: DutyPerson[] = []
  const seen = new Set<string>()
  postKeys.forEach((key, i) => {
    const role = roleLabels[i] || roleLabels[0] || key
    const keys = TEAM_POST_KEYS[key] || [key]
    const member = findTeamMember(ctx.teamMembers, keys)
    const fromTeam = personFromMember(member, role)
    const fromPost = personFromText(ctx.posts?.[key], role)
    const fallback =
      key === 'leader'
        ? personFromText(ctx.ownerName, role)
        : key === 'contact'
          ? personFromText(ctx.createByName, role)
          : null
    const hit = fromTeam || fromPost || fallback
    if (!hit) return
    const id = hit.employeeNo || hit.label
    if (seen.has(id)) return
    seen.add(id)
    out.push(hit)
  })
  return out
}

function isAdmin(user?: DutyActorUser | null) {
  return !!user?.roles?.includes('ADMIN') || user?.identityCode === 'admin'
}

function isLeaderReadonly(user?: DutyActorUser | null) {
  return user?.identityCode === 'leader' && !isAdmin(user)
}

function matchesNamed(user: DutyActorUser | null | undefined, people: DutyPerson[]) {
  return canActOnHandlers(people, user)
}

function resolveAction(
  rule: WorkActorRule,
  ctx: WorkDutyContext,
  user: DutyActorUser | null | undefined,
  action: WorkAction,
): ResolvedAction {
  const verb = WORK_ACTION_LABEL[action]
  if (!rule.identities.length && !rule.postKeys.length) {
    return { people: [], labels: '—', can: action === 'view', reason: rule.hint || `本环节无需${verb}`, pending: false }
  }

  const people = namedPeopleForPosts(ctx, rule.postKeys, rule.roleLabels)
  const roleText = rule.roleLabels.filter((x) => x && x !== '—').join('、') || '相关岗位'
  const labels = people.length ? people.map((p) => `${p.role} ${p.label}`).join('；') : `${roleText}（待指定到人）`
  const pending = people.length === 0

  if (action === 'view') {
    return { people, labels, can: true, reason: '', pending }
  }
  if (isLeaderReadonly(user)) {
    return { people, labels, can: false, reason: `公司领导只读，不能${verb}`, pending }
  }
  if (people.length && matchesNamed(user, people)) {
    return { people, labels, can: true, reason: '', pending }
  }
  if (isAdmin(user)) {
    return { people, labels, can: true, reason: people.length ? `运维代办（指定办理人：${labels}）` : '', pending }
  }
  if (people.length) {
    return {
      people,
      labels,
      can: false,
      reason: `本项已指定给 ${people.map((p) => p.label).join('、')}，仅该办理人可${verb}`,
      pending,
    }
  }
  const ident = user?.identityCode as IdentityCode | undefined
  if (ident && rule.identities.includes(ident)) {
    return { people, labels, can: true, reason: '项目岗位尚未点名，暂按任职身份办理', pending: true }
  }
  return {
    people,
    labels,
    can: false,
    reason: `仅${roleText}可${verb}${pending ? '，请先在项目团队中指定到人' : ''}`,
    pending,
  }
}

export function resolveWorkDuty(
  code: WorkDutyCode,
  project: any,
  user?: DutyActorUser | null,
): ResolvedDuty {
  const def = workDutyByCode(code)
  const ctx = asCtx(project)
  const fill = resolveAction(def.fill, ctx, user, 'fill')
  const submit = resolveAction(def.submit, ctx, user, 'submit')
  const audit = resolveAction(def.audit, ctx, user, 'audit')
  const view = resolveAction(def.view, ctx, user, 'view')
  const edit = resolveAction(def.edit, ctx, user, 'edit')
  const mine = (['fill', 'submit', 'audit', 'view', 'edit'] as WorkAction[]).filter((a) => {
    if (a === 'view') return true
    return ({ fill, submit, audit, view, edit }[a] as ResolvedAction).can
  })
  return { def, fill, submit, audit, view, edit, mine }
}

export function myDutyHint(resolved: ResolvedDuty) {
  const acts = resolved.mine.filter((a) => a !== 'view')
  if (!acts.length) return '您在本页仅可查看'
  return `您可：${acts.map((a) => WORK_ACTION_LABEL[a]).join('、')}`
}
