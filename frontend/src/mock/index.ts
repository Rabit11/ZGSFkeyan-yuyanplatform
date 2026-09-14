import { mockTransform } from './transform'
import type { Res } from '@/api/types'
import type { RequestOptions } from '@/api/request'
import { calcColor, mergeColor } from '@/utils/color'
import { buildLifecycle, channelPathLabel } from '@/utils/lifecycle'
import { identityByLabel } from '@/constants/permission'
import { canActOnHandlers, identitiesForFlowNode } from '@/utils/flowActor'
import { declarationAuditNodes, declarationNodes, declarationWorkflowId } from '@/utils/declarationWorkflow'
import * as DB from './data'
import { buildDashboard, canAccessPreResearch, currentMockUser } from './dashboard'

const ok = <T>(data: T): Res<T> => ({ code: 0, msg: 'ok', data })
const fail = (msg: string): Res<any> => ({ code: 500, msg, data: null as any })

function fundLockError(projectId: number) {
  const list = DB.milestones.filter((x) => Number(x.projectId) === Number(projectId))
  if (!list.length) return '尚未编制里程碑节点，不能执行项目经费流程'
  const open = list.filter((x) => x.status !== 'DONE' && x.colorStatus !== 'GREEN').length
  return open ? `须全部里程碑节点闭环后，才能执行项目经费流程（仍有 ${open} 个节点未闭环）` : ''
}

const DECLARATION_POST_ROLES = [
  ['contact', 'TECH', 'PROJECT_CONTACT', '项目联系人'],
  ['leader', 'TECH', 'PROJECT_LEADER', '项目负责人'],
  ['techLeader', 'TECH', 'TECH_LEADER', '技术负责人'],
  ['supervisor', 'TECH', 'PROJECT_SUPERVISOR', '项目主管'],
  ['chief1', 'EXPERT', 'L1_CHIEF', '一级总师'],
  ['chief2', 'EXPERT', 'L2_CHIEF', '二级总师'],
  ['deptHead', 'MGMT', 'DEPT_HEAD', '项目承担部门负责人'],
  ['hqDirector', 'MGMT', 'HQ_DIRECTOR', '总部处室处长'],
  ['hqSupervisor', 'MGMT', 'HQ_SUPERVISOR', '总部处室主管'],
  ['unitTechDirector', 'MGMT', 'UNIT_MINISTER', '单位科技部长'],
  ['unitTechSupervisor', 'MGMT', 'UNIT_SUPERVISOR', '单位科技主管'],
  ['hqFinance', 'FIN', 'HQ_FINANCE', '总部财务主管'],
  ['unitFinanceDirector', 'FIN', 'UNIT_FIN_MINISTER', '单位财务部长'],
  ['unitFinanceSupervisor', 'FIN', 'UNIT_FIN_SUPERVISOR', '单位财务主管'],
] as const

function parsePersonLabel(label: unknown) {
  const raw = String(label || '').trim()
  const match = raw.match(/^\s*(.*?)\s*[（(]\s*([^）)]+)\s*[）)]\s*$/)
  return { userName: match?.[1]?.trim() || raw, employeeNo: match?.[2]?.trim() || '' }
}

function teamMembersFromDeclaration(posts: any, projectId: number) {
  return DECLARATION_POST_ROLES.flatMap(([key, groupCode, roleCode, roleName], index) => {
    const label = posts?.[key]
    if (!label) return []
    const person = parsePersonLabel(label)
    return [{
      id: Date.now() + index,
      projectId,
      groupCode,
      roleCode,
      roleName,
      userName: person.userName,
      employeeNo: person.employeeNo,
    }]
  })
}

function identityRoles(identity?: string) {
  return identityByLabel(identity)?.roles || []
}

function applyIdentityOnCreate(body: any) {
  const def = identityByLabel(body.identity)
  if (!def) return body
  return {
    ...body,
    identityCode: body.identityCode || def.code,
    dataScope: body.dataScope || def.dataScope,
    roles: body.roles?.length ? body.roles : [...def.roles],
    formMaintScope: body.formMaintScope || def.formMaintScope || '',
    declareResultAccess: def.code === 'admin' ? 1 : body.declareResultAccess ?? 0,
    finishAuth: def.code === 'admin' ? 1 : body.finishAuth ?? 0,
  }
}

function paginate(list: any[], query: any) {
  const page = Number(query.page || 1)
  const size = Number(query.size || 10)
  return { records: list.slice((page - 1) * size, page * size), total: list.length, page, size }
}

const DECLARATION_NODE_POST_KEYS: Record<string, string[]> = {
  联系人: ['contact'],
  项目负责人: ['leader'],
  承担部门: ['deptHead'],
  承办部门: ['deptHead'],
  二级总师: ['chief2'],
  一级总师: ['chief1'],
  财务: ['unitFinanceDirector', 'unitFinanceSupervisor'],
  科技部门: ['unitTechDirector', 'unitTechSupervisor'],
  分管: ['unitTechDirector', 'unitTechSupervisor'],
  总部: ['hqDirector', 'hqSupervisor'],
}

function declarationPosts(row: any) {
  if (row?.posts && typeof row.posts === 'object') return row.posts
  const remark = String(row?.remark || '')
  const prefix = '__DECLARATION_POSTS__:'
  if (!remark.startsWith(prefix)) return {}
  try {
    return JSON.parse(remark.slice(prefix.length)) || {}
  } catch {
    return {}
  }
}

function isMockDeclarationApprover(row: any, user: any) {
  if (!user || user.identityCode === 'admin') return true
  const node = String(row?.flowNode || '')
  const workflowNode = declarationAuditNodes({ ...row, posts: declarationPosts(row) }).find((n) => n.title === node || n.title.includes(node))
  const keys = workflowNode?.roleKeys || Object.entries(DECLARATION_NODE_POST_KEYS).find(([label]) => node.includes(label))?.[1] || []
  const posts = declarationPosts(row)
  const labels = keys.map((key) => posts[key]).filter(Boolean)
  if (labels.length) {
    return canActOnHandlers(labels.map((label) => ({ label: String(label) })), {
      employeeNo: user.employeeNo,
      realName: user.realName,
      username: user.username,
      identityCode: user.identityCode,
    })
  }
  if (!identitiesForFlowNode(node).includes(user.identityCode)) return false
  if (user.dataScope === 'COMPANY') return true
  if (row.orgId && user.orgId && Number(row.orgId) === Number(user.orgId)) return true
  return row.applicant && (String(row.applicant).startsWith(String(user.realName || '')) || String(row.applicant).startsWith(String(user.username || '')))
}

/** 构建 accept 分级材料栏：国家级 → 单位/公司/国家；地方级 → 单位/属地；公司级 → 单位/公司 */
function buildAcceptItems(projectId: number, acceptanceId: number) {
  const p = DB.projects.find((x) => x.id === projectId)
  const level = p?.levelCode
  const defs: [string, string, string[]][] = [
    ['UNIT', '单位级验收', ['验收申请书', '技术总结报告', '经费决算表', '交付物清单']],
    ['COMPANY', '公司级验收', ['公司级验收申请表', '评审专家意见', '验收结论']],
    ['NATIONAL', '国家级验收', ['国家级验收申请', '主管机关批复', '综合绩效评价材料']],
    ['LOCAL', '属地主管部门验收', ['属地验收申请', '科委验收意见', '综合绩效评价材料']],
  ]
  const allow: string[] =
    level === 'NATIONAL' ? ['UNIT', 'COMPANY', 'NATIONAL'] : level === 'LOCAL' ? ['UNIT', 'LOCAL'] : ['UNIT', 'COMPANY']
  const items: any[] = []
  defs.forEach(([code, name, mats], di) => {
    const applicable = allow.includes(code)
    mats.forEach((m, mi) => {
      items.push({
        id: di * 100 + mi + 1,
        acceptanceId,
        levelCode: code,
        levelName: name,
        fieldCode: `${code}_${mi}`,
        materialName: m,
        required: applicable ? 1 : 0,
        locked: applicable ? 0 : 1,
        fileUrl: applicable && mi === 0 ? `/files/${m}.pdf` : undefined,
        status: applicable && mi === 0 ? 'UPLOADED' : 'EMPTY',
      })
    })
  })
  return items
}

export async function mockRequest<T = any>(opts: RequestOptions): Promise<Res<T>> {
  const method = (opts.method || 'get').toUpperCase()
  const url = opts.url.replace(/^\/api/, '')
  const [pathname] = url.split('?')
  const segs = pathname.split('/').filter(Boolean)
  const query = { ...(opts.params || {}), ...(opts.data || {}) }
  const body = opts.data || {}
  const transformResult = mockTransform(method, pathname, query, body)
  if (transformResult) return transformResult as Res<T>

  const m = (mth: string, pattern: string, fn: (p: Record<string, string>) => any) => {
    if (method !== mth) return null
    const ps = pattern.split('/').filter(Boolean)
    if (ps.length !== segs.length) return null
    const params: Record<string, string> = {}
    for (let i = 0; i < ps.length; i++) {
      if (ps[i].startsWith(':')) params[ps[i].slice(1)] = segs[i]
      else if (ps[i] !== segs[i]) return null
    }
    return fn(params)
  }

  const routes: (any | null)[] = []

  /* ------------------------------ 认证 ------------------------------ */
  routes.push(
    m('POST', '/auth/login', () => {
      const key = String(body.username || '')
      const u = DB.users.find(
        (x) => x.username === key || x.employeeNo === key,
      )
      if (!u) return fail('用户不存在')
      if (String(body.password || '') !== String(u.employeeNo || '')) return fail('用户名或密码错误')
      return ok({
        token: 'mock-token-' + u.id,
        userId: u.id,
        realName: u.realName,
        roles: u.roles?.length ? u.roles : (identityRoles(u.identity) || []),
        orgId: u.orgId,
        orgName: u.orgName,
        identity: u.identity,
        identityCode: u.identityCode,
        employeeNo: u.employeeNo,
        username: u.username,
        dataScope: u.dataScope,
        formMaintScope: u.formMaintScope,
        declareResultAccess: u.declareResultAccess,
      })
    }),
    m('GET', '/auth/profile', () => ok(DB.users[0])),
    m('POST', '/auth/logout', () => ok(true)),
  )

  /* ------------------------------ 字典 ------------------------------ */
  routes.push(
    m('GET', '/dict/channels', () => {
      const list = query.levelCode ? DB.channels.filter((c) => c.levelCode === query.levelCode) : DB.channels
      return ok(list)
    }),
    m('GET', '/dict/channel/:id', (p) => {
      const c = DB.channels.find((x) => x.id === Number(p.id))
      return ok({ ...c, flowNodeList: (c?.flowNodes || '').split('→').filter(Boolean) })
    }),
    m('GET', '/dict/:type', (p) => ok(DB.dicts.filter((x) => x.dictType === p.type))),
  )

  /* ------------------------------ 项目 ------------------------------ */
  routes.push(
    m('GET', '/projects/export', () => ok(DB.projects)),
    m('GET', '/projects/:id/overview', (p) => {
      const id = Number(p.id)
      const project = DB.projects.find((x) => x.id === id)
      const channel = DB.channels.find((c) => c.id === project?.channelId)
      const transforms = DB.transforms.filter((x) => x.projectId === id)
      const transformDone = transforms.length > 0 && transforms.every((t) => t.status === 'DONE')
      const lifecycle = buildLifecycle({
        status: project?.status,
        teamMembers: project?.teamMembers,
        createByName: project?.createByName,
        transformDone,
      })
      const channelPath = channelPathLabel({
        channelDept: channel?.channelDept,
        channelOffice: channel?.channelOffice,
        channelName: project?.channelName || channel?.channelName,
      })
      const channelFlowNodes = String(channel?.flowNodes || '')
        .split('→')
        .map((s: string) => s.trim())
        .filter(Boolean)
      return ok({
        project,
        channel,
        channelPath,
        channelFlowNodes,
        lifecycle,
        milestones: DB.milestones.filter((x) => x.projectId === id),
        plans: DB.plans.filter((x) => x.projectId === id),
        budgets: DB.budgets.filter((x) => x.projectId === id),
        payments: DB.payments.filter((x) => x.projectId === id),
        evaluations: DB.evaluations.filter((x) => x.projectId === id),
        changes: DB.changes.filter((x) => x.projectId === id),
        acceptance: DB.acceptances.find((x) => x.projectId === id),
        deliverables: DB.deliverables.filter((x) => x.projectId === id),
        partnerEvals: DB.partnerEvals.filter((x) => x.projectId === id),
        transforms,
        postEval: DB.postEvals.find((x) => x.projectId === id),
      })
    }),
    m('GET', '/projects/:id', (p) => ok(DB.projects.find((x) => x.id === Number(p.id)))),
    m('GET', '/projects', () => {
      let list = [...DB.projects]
      if (query.keyword) list = list.filter((x) => (x.name || '').includes(query.keyword) || (x.projectNo || '').includes(query.keyword))
      if (query.levelCode) list = list.filter((x) => x.levelCode === query.levelCode)
      if (query.channelId) list = list.filter((x) => x.channelId === Number(query.channelId))
      if (query.status) list = list.filter((x) => x.status === query.status)
      if (query.warnColor) list = list.filter((x) => x.warnColor === query.warnColor)
      if (query.orgId) list = list.filter((x) => x.orgId === Number(query.orgId))
      if (query.dataSource && query.dataSource !== 'ALL') {
        list = list.filter((x) => (x.dataSource || 'PLATFORM') === query.dataSource)
      }
      // 台账列表一次返回摘要字段，避免前端对每条再请求 overview（N+1）
      const enriched = list.map((project) => {
        const id = project.id
        const milestones = DB.milestones.filter((x) => x.projectId === id)
        const deliverables = DB.deliverables.filter((x) => x.projectId === id)
        const partners = DB.partnerEvals.filter((x) => x.projectId === id)
        const transforms = DB.transforms.filter((x) => x.projectId === id)
        const pending = milestones
          .filter((m) => m.status !== 'DONE' && !m.actualDate)
          .sort((a, b) => String(a.planDate || '9999').localeCompare(String(b.planDate || '9999')))
        const milestoneDone = milestones.filter((m) => m.status === 'DONE' || m.actualDate).length
        const milestoneTotal = milestones.length
        const accepted = ['COMPANY_ACCEPTED', 'GOV_ACCEPTED', 'FINISHED'].includes(project.status || '')
        const nationalFund =
          project.nationalFund != null
            ? project.nationalFund
            : Math.round(Number(project.totalFund || 0) * 0.6 * 100) / 100
        const selfFund =
          project.selfFund != null
            ? project.selfFund
            : Math.round((Number(project.totalFund || 0) - nationalFund) * 100) / 100
        return {
          ...project,
          nationalFund,
          selfFund,
          milestoneDone,
          milestoneTotal,
          milestonePercent: milestoneTotal
            ? Math.round((milestoneDone / milestoneTotal) * 100)
            : accepted
              ? 100
              : 0,
          leaderName:
            (project as any).ownerName ||
            project.teamMembers?.find((m) => m.roleCode === 'PROJECT_LEADER' || m.roleName === '项目负责人')
              ?.userName ||
            '',
          deliverableDone: deliverables.filter((d) => d.status === 'DELIVERED').length,
          deliverableTotal: deliverables.length,
          partnerDone: partners.filter((p) => p.status === 'DONE' || p.evalDate).length,
          partnerTotal: partners.length || project.participants?.length || 0,
          hasBlacklist: partners.some((p: any) => p.blacklisted || p.isBlacklist),
          transformDone: transforms.filter((t) => t.status === 'DONE' || t.actualDate).length,
          transformTotal: transforms.length,
          nextMilestone: pending[0] || null,
          nextFallback:
            !pending[0] && ['IMPLEMENTING', 'DELAYED'].includes(project.status || '')
              ? '计划结束'
              : undefined,
          canEdit: true,
          canDelete: project.dataSource !== 'FORM_MAINT',
        }
      })
      return ok(paginate(enriched, query))
    }),
    m('POST', '/projects', () => {
      const id = Math.max(0, ...DB.projects.map((x) => x.id)) + 1
      const owner = body.ownerName || body.createByName
      const teamMembers =
        body.teamMembers ||
        (owner
          ? [
              {
                id: Date.now(),
                projectId: id,
                groupCode: 'TECH',
                roleCode: 'PROJECT_LEADER',
                roleName: '项目负责人',
                userName: owner,
              },
            ]
          : undefined)
      DB.projects.unshift({
        id,
        projectNo: `XM${new Date().getFullYear()}${9000 + id}`,
        warnColor: 'BLUE',
        status: 'DRAFT',
        dataSource: 'PLATFORM',
        acceptStatus: '未验收',
        ...body,
        teamMembers,
        createByName: body.createByName || owner,
        ownerName: owner,
      })
      return ok(id)
    }),
    m('PUT', '/projects/:id', (p) => {
      const i = DB.projects.findIndex((x) => x.id === Number(p.id))
      if (i >= 0) {
        const owner = body.ownerName || body.createByName || DB.projects[i].ownerName
        let teamMembers = body.teamMembers || DB.projects[i].teamMembers
        if (owner) {
          const selectedOwner = (teamMembers || []).find(
            (m: any) => m.roleCode === 'PROJECT_LEADER' || m.roleName === '项目负责人',
          )
          const rest = (teamMembers || []).filter(
            (m: any) => m.roleCode !== 'PROJECT_LEADER' && m.roleName !== '项目负责人',
          )
          teamMembers = [
            {
              ...(selectedOwner || {}),
              id: Date.now(),
              projectId: Number(p.id),
              groupCode: 'TECH',
              roleCode: 'PROJECT_LEADER',
              roleName: '项目负责人',
              userName: owner,
            },
            ...rest,
          ]
        }
        DB.projects[i] = {
          ...DB.projects[i],
          ...body,
          ownerName: owner,
          teamMembers,
          dataSource: body.dataSource || DB.projects[i].dataSource || 'PLATFORM',
        }
      }
      return ok(true)
    }),
    m('DELETE', '/projects/form-maint/:id', (p) => {
      if (currentMockUser()?.identityCode !== 'admin') return fail('仅平台管理员可办理「表单维护删除项目」')
      const i = DB.projects.findIndex((x) => x.id === Number(p.id))
      if (i < 0) return fail('项目不存在或已删除')
      DB.projects.splice(i, 1)
      return ok(true)
    }),
    m('DELETE', '/projects/:id', (p) => {
      const i = DB.projects.findIndex((x) => x.id === Number(p.id))
      if (i < 0) return fail('项目不存在或已删除')
      if (DB.projects[i].dataSource === 'FORM_MAINT') return fail('表单维护导入的项目请在表单维护页面删除')
      if (i >= 0) DB.projects.splice(i, 1)
      return ok(true)
    }),
    m('POST', '/projects/:id/submit', (p) => {
      const x = DB.projects.find((y) => y.id === Number(p.id))
      if (x) x.status = 'DECLARING'
      return ok(true)
    }),
    m('POST', '/projects/:id/refresh-status', (p) => {
      DB.recomputeProject(Number(p.id))
      return ok(DB.projects.find((x) => x.id === Number(p.id)))
    }),
  )

  /* ------------------------------ 里程碑 ------------------------------ */
  const attachMs = (m0: any) => {
    const mats = DB.materials.filter((x) => x.bizType === 'MILESTONE' && x.bizId === m0.id)
    const p = DB.projects.find((x) => x.id === m0.projectId)
    const finished = m0.status === 'DONE'
    const color = calcColor(m0.planDate, finished)
    return {
      ...m0,
      colorStatus: color,
      status: color === 'RED' && !finished ? 'OVERDUE' : m0.status,
      materials: mats,
      evidence: mats.length ? 1 : m0.evidence || 0,
      projectName: p?.name,
      projectNo: p?.projectNo,
      ownerName: p?.ownerName,
    }
  }
  const buildMsBoard = (year?: number) => {
    const y = Number(year || new Date().getFullYear())
    const todos: any[] = []
    const boards: any[] = []
    let total = 0
    let done = 0
    let yellow = 0
    let red = 0
    for (const p of DB.projects) {
      const ms = DB.milestones.filter((x) => x.projectId === p.id && x.year === y).map(attachMs)
      const plan = (p.annualPlans || []).find((x: any) => Number(x.year) === y)
      const annualGoal = plan?.annualGoal || ''
      if (!ms.length) {
        todos.push({
          taskType: 'COMPILE',
          typeLabel: '编制里程碑节点',
          projectId: p.id,
          projectNo: p.projectNo,
          projectName: p.name,
          ownerName: p.ownerName,
          year: y,
          milestoneName: '里程碑节点清单',
          planDate: `${y}-04-30`,
          colorStatus: 'BLUE',
          status: 'DOING',
          materialCount: 0,
          materials: [],
        })
      }
      ms.forEach((item) => {
        total++
        if (item.status === 'DONE') done++
        else {
          todos.push({
            taskType: 'CLOSE',
            typeLabel: '节点销项',
            projectId: p.id,
            projectNo: p.projectNo,
            projectName: p.name,
            ownerName: p.ownerName,
            year: y,
            milestoneId: item.id,
            milestoneName: item.name,
            planDate: item.planDate,
            colorStatus: item.colorStatus,
            status: item.status,
            materialCount: item.materials?.length || 0,
            materials: item.materials,
          })
        }
        if (item.colorStatus === 'YELLOW') yellow++
        if (item.colorStatus === 'RED') red++
      })
      boards.push({
        projectId: p.id,
        projectNo: p.projectNo,
        projectName: p.name,
        ownerName: p.ownerName,
        annualGoal,
        planContent: plan?.planContent || '',
        year: y,
        warnColor: mergeColor(ms.map((x) => x.colorStatus)),
        msDone: ms.filter((x) => x.status === 'DONE').length,
        msTotal: ms.length,
        milestones: ms,
      })
    }
    const compile = todos.filter((t) => t.taskType === 'COMPILE').length
    const close = todos.filter((t) => t.taskType === 'CLOSE').length
    return {
      summary: { year: y, todo: todos.length, compile, close, yellow, red, total, done },
      todos,
      projects: boards,
    }
  }
  routes.push(
    m('GET', '/milestones/board', () => ok(buildMsBoard(query.year))),
    m('GET', '/projects/:id/milestones', (p) => {
      let list = DB.milestones.filter((x) => x.projectId === Number(p.id))
      if (query.year) list = list.filter((x) => x.year === Number(query.year))
      return ok(list.map(attachMs))
    }),
    m('GET', '/milestones/:id/materials', (p) =>
      ok(DB.materials.filter((x) => x.bizType === 'MILESTONE' && x.bizId === Number(p.id)))),
    m('POST', '/milestones/:id/materials', (p) => {
      const id = Number(p.id)
      const x = DB.milestones.find((y) => y.id === id)
      if (!x) return fail('里程碑不存在')
      const mid = (DB.materials.reduce((n, i) => Math.max(n, i.id), 0) || 0) + 1
      const row = {
        id: mid,
        bizType: 'MILESTONE',
        bizId: id,
        fieldCode: body.fieldCode || 'EVIDENCE',
        fieldName: body.fieldName || '节点完成佐证材料',
        fileName: body.fileName,
        fileUrl: body.fileUrl,
        fileSize: body.fileSize,
        version: 1,
      }
      const i = DB.materials.findIndex((y) => y.bizType === 'MILESTONE' && y.bizId === id && y.fieldCode === row.fieldCode)
      if (i >= 0) DB.materials[i] = { ...DB.materials[i], ...row, id: DB.materials[i].id }
      else DB.materials.push(row as any)
      x.evidence = 1
      return ok(row.id)
    }),
    m('GET', '/milestones/:id', (p) => {
      const x = DB.milestones.find((y) => y.id === Number(p.id))
      if (!x) return fail('里程碑不存在')
      return ok(attachMs(x))
    }),
    m('POST', '/milestones', () => {
      const id = Math.max(...DB.milestones.map((x) => x.id)) + 1
      DB.milestones.push({ id, status: 'DOING', ...body, colorStatus: calcColor(body.planDate, false) })
      return ok(id)
    }),
    m('PUT', '/milestones/:id', (p) => {
      const x = DB.milestones.find((y) => y.id === Number(p.id))
      if (x) Object.assign(x, body, { colorStatus: calcColor(body.planDate ?? x.planDate, (body.status ?? x.status) === 'DONE') })
      return ok(true)
    }),
    m('DELETE', '/milestones/:id', (p) => {
      const i = DB.milestones.findIndex((y) => y.id === Number(p.id))
      if (i >= 0) DB.milestones.splice(i, 1)
      return ok(true)
    }),
    m('POST', '/milestones/:id/close', (p) => {
      const x = DB.milestones.find((y) => y.id === Number(p.id))
      if (!x) return fail('里程碑不存在')
      const hasFile = DB.materials.some((mat) => mat.bizType === 'MILESTONE' && mat.bizId === x.id && mat.fileUrl)
      if (!x.evidence && !hasFile && !body?.evidence) return fail('请先上传节点佐证材料，再执行闭环销项')
      Object.assign(x, { status: 'DONE', actualDate: new Date().toISOString().slice(0, 10), colorStatus: 'GREEN', evidence: 1 })
      DB.recomputeProject(x.projectId)
      return ok(true)
    }),
    m('POST', '/milestones/:id/delay', () => fail('节点已超期，禁止直接修改日期，请通过【项目变更】模块发起延期审批')),
  )

  /* ------------------------------ 计划 ------------------------------ */
  routes.push(
    m('GET', '/projects/:id/plans', (p) => {
      let list = DB.plans.filter((x) => x.projectId === Number(p.id))
      if (query.planType) list = list.filter((x) => x.planType === query.planType)
      return ok(list)
    }),
    m('POST', '/plans/sync', () => ok(DB.plans.length)),
    m('POST', '/plans', () => {
      const id = Math.max(...DB.plans.map((x) => x.id)) + 1
      DB.plans.push({ id, source: 'CMOS', planType: 'TODO', ...body, colorStatus: calcColor(body.dueDate, false) })
      return ok(id)
    }),
    m('PUT', '/plans/:id', (p) => {
      const x = DB.plans.find((y) => y.id === Number(p.id))
      if (x) Object.assign(x, body, { colorStatus: calcColor(body.dueDate ?? x.dueDate, (body.status ?? x.status) === 'DONE') })
      return ok(true)
    }),
    m('DELETE', '/plans/:id', (p) => {
      const i = DB.plans.findIndex((y) => y.id === Number(p.id))
      if (i >= 0) DB.plans.splice(i, 1)
      return ok(true)
    }),
    m('POST', '/plans/:id/finish-apply', (p) => {
      const x = DB.plans.find((y) => y.id === Number(p.id))
      if (x) x.applyStatus = 'PENDING'
      return ok(true)
    }),
    m('POST', '/plans/:id/finish-audit', (p) => {
      const x = DB.plans.find((y) => y.id === Number(p.id))
      if (x && body.pass) Object.assign(x, { planType: 'DONE', status: 'DONE', colorStatus: 'GREEN', applyStatus: 'APPROVED', finishDate: new Date().toISOString().slice(0, 10) })
      else if (x) x.applyStatus = 'REJECTED'
      return ok(true)
    }),
  )

  /* ------------------------------ 经费 ------------------------------ */
  routes.push(
    m('GET', '/projects/:id/fund/budgets', (p) => ok(DB.budgets.filter((x) => x.projectId === Number(p.id)))),
    m('GET', '/projects/:id/fund/payments', (p) => ok(DB.payments.filter((x) => x.projectId === Number(p.id)))),
    m('POST', '/fund/budgets', () => {
      const locked = fundLockError(Number(body.projectId))
      if (locked) return fail(locked)
      const id = Math.max(...DB.budgets.map((x) => x.id)) + 1
      DB.budgets.push({ id, status: 'PENDING', ...body })
      return ok(id)
    }),
    m('PUT', '/fund/budgets/:id', (p) => {
      const x = DB.budgets.find((y) => y.id === Number(p.id))
      const locked = fundLockError(Number(body.projectId || x?.projectId))
      if (locked) return fail(locked)
      if (x) Object.assign(x, body)
      return ok(true)
    }),
    m('DELETE', '/fund/budgets/:id', (p) => {
      const i = DB.budgets.findIndex((y) => y.id === Number(p.id))
      const locked = fundLockError(Number(i >= 0 ? DB.budgets[i].projectId : 0))
      if (locked) return fail(locked)
      if (i >= 0) DB.budgets.splice(i, 1)
      return ok(true)
    }),
    m('POST', '/fund/payments', () => {
      const locked = fundLockError(Number(body.projectId))
      if (locked) return fail(locked)
      const id = Math.max(...DB.payments.map((x) => x.id)) + 1
      DB.payments.push({ id, flowType: 'PAY', writeoffStatus: 'PENDING', ...body })
      return ok(id)
    }),
    m('POST', '/fund/payments/:id/writeoff', (p) => {
      const x = DB.payments.find((y) => y.id === Number(p.id))
      const locked = fundLockError(Number(x?.projectId))
      if (locked) return fail(locked)
      if (x) x.writeoffStatus = 'WRITTEN'
      return ok(true)
    }),
    m('GET', '/hq-fund/budgets', () => {
      const list = query.year ? DB.hqBudgets.filter((x) => x.year === Number(query.year)) : DB.hqBudgets
      return ok(list)
    }),
    m('POST', '/hq-fund/budgets', () => {
      const id = Math.max(...DB.hqBudgets.map((x) => x.id)) + 1
      DB.hqBudgets.push({ id, status: 'DRAFT', approveStatus: 'PENDING', ...body })
      return ok(id)
    }),
    m('POST', '/hq-fund/budgets/:id/lock', (p) => {
      const x = DB.hqBudgets.find((y) => y.id === Number(p.id))
      if (x) x.status = 'LOCKED'
      return ok(true)
    }),
    m('GET', '/hq-fund/quotas', () => ok(DB.quotas.filter((x) => !query.budgetId || x.budgetId === Number(query.budgetId)))),
    m('POST', '/hq-fund/quotas', () => {
      const id = Math.max(...DB.quotas.map((x) => x.id)) + 1
      DB.quotas.push({ id, usedAmount: 0, ...body })
      return ok(id)
    }),
    m('GET', '/hq-fund/transfers', () => ok(DB.transfers.filter((x) => !query.budgetId || x.budgetId === Number(query.budgetId)))),
    m('POST', '/hq-fund/transfers', () => {
      const q = DB.quotas.find((x) => x.id === Number(body.quotaId))
      if (q && (q.usedAmount || 0) + Number(body.amount || 0) > (q.quotaAmount || 0)) return fail('拨付金额超出该单位年度额度上限')
      const id = Math.max(...DB.transfers.map((x) => x.id)) + 1
      DB.transfers.unshift({ id, status: 'PENDING', applyAt: new Date().toISOString().slice(0, 10), ...body })
      return ok(id)
    }),
    m('POST', '/hq-fund/transfers/:id/audit', (p) => {
      const x = DB.transfers.find((y) => y.id === Number(p.id))
      if (x) {
        x.status = body.pass ? 'PAID' : 'REJECTED'
        if (body.pass) {
          const q = DB.quotas.find((y) => y.id === x.quotaId)
          if (q) q.usedAmount = (q.usedAmount || 0) + (x.amount || 0)
        }
      }
      return ok(true)
    }),
  )

  /* ------------------------------ 评估检查 ------------------------------ */
  routes.push(
    m('GET', '/projects/:id/evaluations', (p) => ok(DB.evaluations.filter((x) => x.projectId === Number(p.id)))),
    m('POST', '/evaluations', () => {
      const id = Math.max(...DB.evaluations.map((x) => x.id)) + 1
      DB.evaluations.push({ id, status: 'PENDING', ...body })
      return ok(id)
    }),
    m('PUT', '/evaluations/:id', (p) => {
      const x = DB.evaluations.find((y) => y.id === Number(p.id))
      if (x) Object.assign(x, body)
      return ok(true)
    }),
    m('DELETE', '/evaluations/:id', (p) => {
      const i = DB.evaluations.findIndex((y) => y.id === Number(p.id))
      if (i >= 0) DB.evaluations.splice(i, 1)
      return ok(true)
    }),
  )

  /* ------------------------------ 变更 ------------------------------ */
  routes.push(
    m('GET', '/changes', () => {
      let list = [...DB.changes]
      if (query.projectId) list = list.filter((x) => x.projectId === Number(query.projectId))
      if (query.changeType) list = list.filter((x) => x.changeType === query.changeType)
      if (query.status) list = list.filter((x) => x.status === query.status)
      return ok(paginate(list, query))
    }),
    m('POST', '/changes', () => {
      const id = Math.max(...DB.changes.map((x) => x.id)) + 1
      const legal = ['OUTSOURCE', 'PERIOD', 'FUND'].includes(body.category)
      DB.changes.unshift({ id, changeNo: `BG${new Date().getFullYear()}${7000 + id}`, status: 'DRAFT', legalReview: legal ? 1 : 0, applicant: '当前用户', createdAt: new Date().toISOString().slice(0, 10), ...body })
      return ok(id)
    }),
    m('PUT', '/changes/:id', (p) => {
      const x = DB.changes.find((y) => y.id === Number(p.id))
      if (x) Object.assign(x, body)
      return ok(true)
    }),
    m('DELETE', '/changes/:id', (p) => {
      const i = DB.changes.findIndex((y) => y.id === Number(p.id))
      if (i >= 0) DB.changes.splice(i, 1)
      return ok(true)
    }),
    m('POST', '/changes/:id/submit', (p) => {
      const x = DB.changes.find((y) => y.id === Number(p.id))
      if (x) Object.assign(x, { status: 'APPROVING', flowNode: x.changeType === 'DATA' ? '二级单位内部审批' : x.legalReview ? '法务部门审核' : '二级单位主管部门初审' })
      return ok(true)
    }),
    m('POST', '/changes/:id/audit', (p) => {
      const x = DB.changes.find((y) => y.id === Number(p.id))
      if (x) x.status = body.pass ? 'APPROVED' : 'REJECTED'
      return ok(true)
    }),
  )

  /* ------------------------------ 立项申报 ------------------------------ */
  routes.push(
    m('GET', '/declarations', () => {
      let list = [...DB.declarations]
      if (query.status) list = list.filter((x) => x.status === query.status)
      if (query.channelId) list = list.filter((x) => x.channelId === Number(query.channelId))
      if (query.keyword) list = list.filter((x) => (x.name || '').includes(query.keyword))
      return ok(paginate(list, query))
    }),
    m('GET', '/declarations/pending', () => {
      const user = currentMockUser()
      return ok(DB.declarations.filter((x) => ['SUBMITTED', 'APPROVING'].includes(String(x.status)) && isMockDeclarationApprover(x, user)))
    }),
    m('GET', '/declarations/:id', (p) => {
      const d = DB.declarations.find((x) => x.id === Number(p.id))
      return ok({ ...d, materials: DB.materials.filter((x) => x.bizType === 'DECLARATION' && x.bizId === Number(p.id)) })
    }),
    m('GET', '/declarations/:id/materials', (p) =>
      ok(DB.materials.filter((x) => x.bizType === 'DECLARATION' && x.bizId === Number(p.id))),
    ),
    m('POST', '/declarations/:id/materials', (p) => {
      const x = DB.materials.find((y) => y.bizId === Number(p.id) && y.fieldCode === body.fieldCode)
      if (x) Object.assign(x, { fileName: body.fileName, version: (x.version || 1) + 1, uploadedAt: new Date().toISOString().slice(0, 10) })
      return ok(true)
    }),
    m('POST', '/declarations', () => {
      const id = (DB.declarations.reduce((m, x) => Math.max(m, x.id), 0) || 0) + 1
      const ch = DB.channels.find((c) => c.id === Number(body.channelId))
      const workflowVersion = declarationWorkflowId({ ...body, channelCode: ch?.channelCode, channelName: ch?.channelName, flowNodes: ch?.flowNodes, posts: body.posts || {} })
      const snapshotPosts = { ...(body.posts || {}), __workflow: workflowVersion }
      const applicant =
        body.applicant ||
        (body.posts?.contact ? String(body.posts.contact).replace(/（.*?）/, '').trim() : '') ||
        '未填写'
      DB.declarations.unshift({
        id,
        applyNo: `SB${new Date().getFullYear()}${5000 + id}`,
        status: 'DRAFT',
        channelName: ch?.channelName,
        levelCode: ch?.levelCode,
        applyAt: new Date().toISOString().slice(0, 10),
        ...body,
        needApproval: workflowVersion === 'report-v1' ? 0 : 1,
        posts: snapshotPosts,
        remark: `__DECLARATION_POSTS__:${JSON.stringify(snapshotPosts)}`,
        applicant,
        orgName: body.leadOrgName || body.orgName,
      })
      const allFields = ['建议书', '建议书意见', '申请书', '申请书评审', '榜单答疑', '任务清单', '任务清单评估', '申报通知', '委员会审议', '学术委员会审议', '合作需求', '需求对接总结', '技术发展战略委员会审议', '项目申请书', '波音指导委员会会议纪要']
      const need = (ch?.declareMaterial || '').split(',').filter(Boolean)
      const mid = () => (DB.materials.reduce((m, x) => Math.max(m, x.id), 0) || 0) + 1
      allFields.forEach((f) => {
        DB.materials.push({ id: mid(), bizType: 'DECLARATION', bizId: id, fieldCode: `F_${f}`, fieldName: f, required: need.includes(f) ? 1 : 0, locked: need.includes(f) ? 0 : 1, version: 1 })
      })
      return ok(id)
    }),
    m('POST', '/declarations/check-duplicate', () => {
      const name = String(body.name || '').trim()
      if (!name) return ok({ duplicated: false, matches: [] })
      const matches = [
        ...DB.declarations.filter((x) => (x.name || '').includes(name) || name.includes(x.name || '')),
        ...DB.projects.filter((x) => (x.name || '').includes(name) || name.includes(x.name || '')),
      ].map((x) => ({ id: x.id, name: x.name, source: 'applyNo' in x ? '申报' : '项目' }))
      return ok({ duplicated: matches.length > 0, matches })
    }),
    m('PUT', '/declarations/:id', (p) => {
      const x = DB.declarations.find((y) => y.id === Number(p.id))
      if (x) {
        const ch = DB.channels.find((c) => c.id === Number(body.channelId ?? x.channelId))
        const version = declarationWorkflowId({
          ...x,
          ...body,
          status: 'DRAFT',
          channelCode: ch?.channelCode,
          channelName: ch?.channelName,
          flowNodes: ch?.flowNodes,
          posts: {},
        })
        const posts = { ...declarationPosts(x), ...(body.posts || {}), __workflow: version }
        Object.assign(x, body, {
          channelName: ch?.channelName,
          levelCode: ch?.levelCode,
          needApproval: version === 'report-v1' ? 0 : 1,
          posts,
          remark: `__DECLARATION_POSTS__:${JSON.stringify(posts)}`,
        })
      }
      return ok(true)
    }),
    m('POST', '/declarations/:id/submit', (p) => {
      const x = DB.declarations.find((y) => y.id === Number(p.id))
      if (!x) return fail('申报记录不存在，请先暂存')
      if (!['DRAFT', 'REJECTED'].includes(String(x.status))) return fail('仅草稿或退回的申报可以提交审核')
      const submitUser = currentMockUser()
      const submitLabels = ['contact', 'leader', 'techLeader', 'supervisor']
        .map((key) => declarationPosts(x)[key]).filter(Boolean).map((label) => ({ label: String(label) }))
      if (submitUser?.identityCode !== 'admin' && submitLabels.length && !canActOnHandlers(submitLabels, submitUser)) {
        return fail('仅本项目指定填报人或项目团队成员可以提交')
      }
      const missing = DB.materials
        .filter((y) => y.bizType === 'DECLARATION' && y.bizId === Number(p.id) && y.required === 1 && !y.locked && !y.fileName)
        .map((y) => y.fieldName)
      if (missing.length) return fail(`渠道材料未齐，请上传：${missing.join('、')}`)
      const posts = declarationPosts(x)
      const version = declarationWorkflowId({ ...x, posts })
      const first = declarationAuditNodes({ ...x, posts }).at(0)
      if (first) Object.assign(x, { status: 'APPROVING', flowNode: first.title, needApproval: version === 'report-v1' ? 0 : 1 })
      return ok(true)
    }),
    m('POST', '/declarations/:id/audit', (p) => {
      const x = DB.declarations.find((y) => y.id === Number(p.id))
      if (!x) return fail('申报记录不存在')
      const posts = declarationPosts(x)
      const chain = declarationAuditNodes({ ...x, posts })
      const current = chain.find((node) => node.title === x.flowNode)
      if (x.status !== 'APPROVING' || !current) return fail('申报当前不在有效审核节点')
      if (!isMockDeclarationApprover(x, currentMockUser())) return fail('仅本节点指定办理人可以办理')
      if (typeof body.pass !== 'boolean') return fail('请选择通过或退回')
      if (body.pass && current.evidence && (!String(body.opinion || '').trim() || !String(body.evidence || '').trim())) {
        return fail('请填写办理结论及评审纪要/发布文件等佐证引用')
      }
      if (!body.pass) {
        Object.assign(x, { status: 'REJECTED', opinion: body.opinion, flowNode: declarationNodes({ ...x, posts })[0]?.title })
      } else {
        let i = chain.findIndex((t) => t.title === x.flowNode || t.title.includes(String(x.flowNode || '')))
        const next = i < 0 ? chain[0] : i >= chain.length - 1 ? null : chain[i + 1]
        Object.assign(x, next
          ? { status: 'APPROVING', opinion: body.opinion, flowNode: next.title }
          : x.needApproval === 0
            ? { status: 'REPORTED', opinion: body.opinion, flowNode: '线上报备归档' }
            : { status: 'APPROVED', opinion: body.opinion, flowNode: '归档' })
      }
      return ok(true)
    }),
    m('POST', '/declarations/:id/revoke', (p) => {
      const x = DB.declarations.find((y) => y.id === Number(p.id))
      if (x) x.status = 'DRAFT'
      return ok(true)
    }),
    m('POST', '/declarations/:id/filing', (p) => {
      const x = DB.declarations.find((y) => y.id === Number(p.id))
      const ch = DB.channels.find((c) => c.id === x?.channelId)
      const pid = (DB.projects.reduce((m, y) => Math.max(m, y.id), 0) || 0) + 1
      const linkedMembers = teamMembersFromDeclaration(x?.posts, pid)
      const owner = parsePersonLabel(x?.posts?.leader)
      const contact = parsePersonLabel(x?.posts?.contact)
      DB.projects.unshift({
        id: pid,
        projectNo: `XM${new Date().getFullYear()}${2000 + pid}`,
        name: x?.name || '',
        levelCode: ch?.levelCode,
        channelId: ch?.id,
        channelName: ch?.channelName,
        filingDept: ch?.channelDept,
        leadOrgName: x?.leadOrgName || x?.orgName,
        orgId: x?.orgId,
        orgName: x?.leadOrgName || x?.orgName,
        status: 'IMPLEMENTING',
        warnColor: 'BLUE',
        totalFund: Number(x?.applyFund || 0),
        expenseTotal: 0,
        yearBudget: 0,
        yearExpense: 0,
        outsourceAmount: 0,
        startDate: x?.startDate,
        endDate: x?.endDate,
        goal: x?.goal,
        mainWork: x?.leadWorkContent,
        major1: x?.major1,
        major2: x?.major2,
        ownerName: owner.userName,
        createByName: contact.userName,
        teamMembers: linkedMembers,
      } as any)
      if (x) x.status = 'REPORTED'
      return ok({ projectId: pid })
    }),
  )

  /* ------------------------------ 验收 ------------------------------ */
  routes.push(
    m('GET', '/acceptance/:projectId', (p) => {
      const pid = Number(p.projectId)
      let acc = DB.acceptances.find((x) => x.projectId === pid)
      if (!acc) {
        acc = { id: Math.max(...DB.acceptances.map((x) => x.id || 0)) + 1, projectId: pid, status: 'NOT_STARTED' }
        DB.acceptances.push(acc)
      }
      let items = DB.acceptanceItems.filter((x) => x.acceptanceId === acc!.id)
      if (!items.length) {
        items = buildAcceptItems(pid, acc.id!)
        DB.acceptanceItems.push(...items)
      }
      return ok({ ...acc, items })
    }),
    m('POST', '/acceptance/:projectId/check', (p) => {
      const pid = Number(p.projectId)
      const ms = DB.milestones.filter((x) => x.projectId === pid)
      const dvs = DB.deliverables.filter((x) => x.projectId === pid)
      const pays = DB.payments.filter((x) => x.projectId === pid)
      const msOpen = ms.filter((x) => x.status !== 'DONE').length
      const dvOpen = dvs.filter((x) => x.status === 'OVERDUE').length
      const payOpen = pays.filter((x) => x.writeoffStatus !== 'WRITTEN').length
      return ok([
        { key: 'MILESTONE', label: '全部里程碑闭环', passed: msOpen === 0, message: msOpen === 0 ? '全部里程碑已完成销项' : `仍有 ${msOpen} 个里程碑未完成闭环` },
        { key: 'DELIVERABLE', label: '外协交付物验收合格', passed: dvOpen === 0, message: dvOpen === 0 ? '交付物状态正常' : `存在 ${dvOpen} 项交付物已逾期` },
        { key: 'FUND', label: '节点经费匹配核销完毕', passed: payOpen === 0, message: payOpen === 0 ? '经费已全部核销' : `存在 ${payOpen} 笔经费未核销` },
        { key: 'CORE_DV', label: '核心交付物已交付', passed: dvs.filter((x) => x.status === 'DELIVERED').length > 0, message: `已交付 ${dvs.filter((x) => x.status === 'DELIVERED').length}/${dvs.length} 项` },
      ])
    }),
    m('POST', '/acceptance/:projectId/submit', (p) => {
      const x = DB.acceptances.find((y) => y.projectId === Number(p.projectId))
      if (x) Object.assign(x, { status: 'APPLYING', applyAt: new Date().toISOString() })
      return ok(true)
    }),
    m('POST', '/acceptance/:projectId/materials', (p) => {
      const x = DB.acceptanceItems.find((y) => y.fieldCode === body.fieldCode)
      if (x) Object.assign(x, { fileUrl: body.fileUrl || `/files/${body.fieldCode}.pdf`, status: 'UPLOADED' })
      return ok(true)
    }),
    m('POST', '/acceptance/:projectId/audit', (p) => {
      const x = DB.acceptances.find((y) => y.projectId === Number(p.projectId))
      if (x && body.pass) {
        Object.assign(x, { status: 'DONE', conclusion: body.opinion, finishAt: new Date().toISOString(), partnerDueDate: new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10) })
      }
      return ok(true)
    }),
  )

  /* ------------------------------ 交付物 ------------------------------ */
  routes.push(
    m('GET', '/deliverables', () => {
      const kw = String(query.keyword || '').trim()
      let list = DB.deliverables.map((x) => {
        const p = DB.projects.find((y) => y.id === x.projectId)
        return { ...x, projectName: p?.name, projectNo: p?.projectNo }
      })
      if (kw) {
        list = list.filter(
          (x) =>
            String(x.name || '').includes(kw) ||
            String(x.achievementNo || '').includes(kw) ||
            String(x.projectNo || '').includes(kw) ||
            String(x.projectName || '').includes(kw),
        )
      }
      if (query.status) list = list.filter((x) => x.status === query.status)
      if (query.deliverType) list = list.filter((x) => x.deliverType === query.deliverType)
      if (query.ownerOrg) list = list.filter((x) => String(x.ownerOrgs || '').includes(String(query.ownerOrg)))
      return ok(paginate(list, query))
    }),
    m('GET', '/projects/:id/deliverables', (p) => ok(DB.deliverables.filter((x) => x.projectId === Number(p.id)))),
    m('POST', '/deliverables', () => {
      const id = Math.max(...DB.deliverables.map((x) => x.id)) + 1
      DB.deliverables.push({ id, status: 'PENDING', ...body, colorStatus: calcColor(body.dueDate, false) })
      return ok(id)
    }),
    m('PUT', '/deliverables/:id', (p) => {
      const x = DB.deliverables.find((y) => y.id === Number(p.id))
      if (x) Object.assign(x, body, { colorStatus: calcColor(body.dueDate ?? x.dueDate, (body.status ?? x.status) === 'DELIVERED') })
      return ok(true)
    }),
    m('DELETE', '/deliverables/:id', (p) => {
      const i = DB.deliverables.findIndex((y) => y.id === Number(p.id))
      if (i >= 0) DB.deliverables.splice(i, 1)
      return ok(true)
    }),
    m('POST', '/deliverables/:id/bind', (p) => {
      const x = DB.deliverables.find((y) => y.id === Number(p.id))
      if (x) x.achievementNo = body.achievementNo
      return ok(true)
    }),
  )

  /* ------------------------------ 协作单位评价 ------------------------------ */
  routes.push(
    m('GET', '/partners/blacklist', () => ok(DB.blacklist)),
    m('GET', '/projects/:id/partner-evals', (p) => ok(DB.partnerEvals.filter((x) => x.projectId === Number(p.id)))),
    m('POST', '/partner-evals', () => {
      const id = Math.max(...DB.partnerEvals.map((x) => x.id)) + 1
      DB.partnerEvals.push({ id, status: 'PENDING', score: 0, ...body })
      return ok(id)
    }),
    m('PUT', '/partner-evals/:id', (p) => {
      const x = DB.partnerEvals.find((y) => y.id === Number(p.id))
      if (x) Object.assign(x, body)
      return ok(true)
    }),
    m('DELETE', '/partner-evals/:id', (p) => {
      const i = DB.partnerEvals.findIndex((y) => y.id === Number(p.id))
      if (i >= 0) DB.partnerEvals.splice(i, 1)
      return ok(true)
    }),
    m('POST', '/partner-evals/:id/submit', (p) => {
      const x = DB.partnerEvals.find((y) => y.id === Number(p.id))
      if (!x) return fail('评价记录不存在')
      const score = [x.techScore, x.qualityScore, x.progressScore, x.serviceScore, x.complianceScore].reduce((a, b) => a + (b || 0), 0)
      x.score = score
      x.grade = DB.gradeOf(score)
      x.status = 'DONE'
      x.evalDate = new Date().toISOString().slice(0, 10)
      x.evaluator = '当前用户'
      if (x.grade === 'FAIL' && !DB.blacklist.some((b: any) => b.partnerName === x.partnerName)) {
        DB.blacklist.push({ id: DB.blacklist.length + 1, partnerName: x.partnerName, reason: `评价得分 ${score} 分，不合格，纳入黑名单`, inDate: x.evalDate })
      }
      return ok(true)
    }),
  )

  /* ------------------------------ 后评价 ------------------------------ */
  routes.push(
    m('GET', '/post-evals', () => {
      let list = [...DB.postEvals]
      if (query.projectId) list = list.filter((x) => x.projectId === Number(query.projectId))
      return ok(paginate(list, query))
    }),
    m('GET', '/post-evals/:id', (p) => ok(DB.postEvals.find((x) => x.id === Number(p.id)))),
    m('POST', '/post-evals', () => {
      const id = Math.max(...DB.postEvals.map((x) => x.id)) + 1
      DB.postEvals.unshift({ id, status: 'PENDING', colorStatus: calcColor(body.dueDate, false), ...body })
      return ok(id)
    }),
    m('PUT', '/post-evals/:id', (p) => {
      const x = DB.postEvals.find((y) => y.id === Number(p.id))
      if (x) Object.assign(x, body)
      return ok(true)
    }),
    m('DELETE', '/post-evals/:id', (p) => {
      const i = DB.postEvals.findIndex((y) => y.id === Number(p.id))
      if (i >= 0) DB.postEvals.splice(i, 1)
      return ok(true)
    }),
    m('POST', '/post-evals/:id/submit', (p) => {
      const x = DB.postEvals.find((y) => y.id === Number(p.id))
      if (x) Object.assign(x, { status: 'DONE', colorStatus: 'GREEN' })
      return ok(true)
    }),
  )

  /* ------------------------------ 看板 / 预警 / 审计 ------------------------------ */
  routes.push(
    m('GET', '/dashboard', () => ok(buildDashboard(query, currentMockUser()))),
    m('GET', '/dashboard/cockpit', () => {
      const user = currentMockUser()
      if (String(query.screen || '') === 'pre-research' && !canAccessPreResearch(user)) {
        return { code: 403, msg: '当前身份无权访问可视化看板', data: null as any }
      }
      return ok(buildDashboard(query, user))
    }),
    m('GET', '/dashboard/overview', () => ok(buildDashboard(query, currentMockUser()))),
    m('GET', '/dashboard/warnings', () => ok(DB.warnings.slice(0, 30))),
    m('GET', '/warnings', () => ok(paginate(DB.warnings, query))),
    m('POST', '/warnings/scan', () => ok(DB.warnings.length)),
    m('POST', '/warnings/:id/read', (p) => {
      const x = DB.warnings.find((y) => y.id === Number(p.id))
      if (x) x.isRead = 1
      return ok(true)
    }),
    m('GET', '/audit-logs', () => ok(paginate(DB.auditLogs, query))),
  )

  /* ------------------------------ 用户 / 成员 ------------------------------ */
  routes.push(
    m('GET', '/users/candidates', () => ok(DB.users.filter((x) => x.status !== 0).map((x) => ({
      id: x.id,
      realName: x.realName,
      employeeNo: x.employeeNo,
      orgId: x.orgId,
      orgName: x.orgName,
      deptName: x.deptName,
      identity: x.identity,
      identityCode: x.identityCode,
      projectPost: x.projectPost,
    })))),
    m('GET', '/users', () => {
      let list = [...DB.users]
      if (query.keyword) {
        const kw = String(query.keyword)
        list = list.filter(
          (u) =>
            (u.realName || '').includes(kw) ||
            (u.employeeNo || '').includes(kw) ||
            (u.username || '').includes(kw) ||
            (u.orgName || '').includes(kw),
        )
      }
      if (query.identity) list = list.filter((u) => u.identity === query.identity)
      if (query.dataScope) list = list.filter((u) => u.dataScope === query.dataScope)
      if (query.status !== undefined && query.status !== null && query.status !== '') {
        list = list.filter((u) => u.status === Number(query.status))
      }
      return ok(paginate(list, query))
    }),
    m('POST', '/users', () => {
      if (!body.identity) return fail('请选择任职身份，以便自动分配功能权限')
      const payload = applyIdentityOnCreate(body)
      if (!payload.roles?.length) return fail('未能根据任职身份推导功能角色')
      const id = (DB.users.reduce((m, x) => Math.max(m, x.id), 0) || 0) + 1
      const employeeNo = payload.employeeNo || String(100000 + id)
      DB.users.push({
        id,
        status: 1,
        finishAuth: payload.finishAuth ?? 0,
        username: payload.username || employeeNo,
        employeeNo,
        ...payload,
        roles: [...payload.roles],
      })
      return ok(id)
    }),
    m('POST', '/users/sync-identity-roles', () => {
      let fixed = 0
      const details: string[] = []
      for (const u of DB.users) {
        const def = identityByLabel(u.identity)
        if (!def) continue
        const expected = [...def.roles]
        const current = u.roles || []
        const same = current.length === expected.length && expected.every((r) => current.includes(r))
        if (!same) {
          u.roles = expected
          u.identityCode = u.identityCode || def.code
          u.dataScope = u.dataScope || def.dataScope
          fixed++
          details.push(`${u.employeeNo} ${u.realName} → ${expected.join(',')}`)
        }
      }
      return ok({ total: DB.users.length, fixed, details })
    }),
    m('PUT', '/users/:id', (p) => {
      const x = DB.users.find((y) => y.id === Number(p.id))
      if (x) Object.assign(x, body)
      return ok(true)
    }),
    m('DELETE', '/users/:id', (p) => {
      const u = DB.users.find((y) => y.id === Number(p.id))
      if (u?.employeeNo === '100001' || u?.username === 'admin' || u?.roles?.includes('ADMIN')) {
        return fail('系统管理员不可删除')
      }
      const i = DB.users.findIndex((y) => y.id === Number(p.id))
      if (i >= 0) DB.users.splice(i, 1)
      return ok(true)
    }),
    m('POST', '/users/:id/roles', (p) => {
      const x = DB.users.find((y) => y.id === Number(p.id))
      if (x) x.roles = body
      return ok(true)
    }),
    m('POST', '/users/:id/reset-password', () => ok(true)),
    m('POST', '/users/:id/finish-auth', (p) => {
      const x = DB.users.find((y) => y.id === Number(p.id))
      if (x) x.finishAuth = body.enabled ? 1 : 0
      return ok(true)
    }),
    m('GET', '/permission-matrix', () => ok({ ...DB.postPermMatrix })),
    m('PUT', '/permission-matrix', () => {
      if (body && typeof body === 'object') {
        Object.assign(DB.postPermMatrix, body)
      }
      return ok(true)
    }),
    m('POST', '/permission-matrix/reset', () => ok(DB.resetPostPermMatrix())),
  )

  const hit = routes.find((r) => r !== null && r !== undefined)
  if (hit) return hit as Res<T>
  return ok(null as any)
}
