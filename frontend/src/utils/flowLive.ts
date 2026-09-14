/** 流转图人员 / 材料：一律按接口实时数据展示，不回落演示花名册 */

export interface LiveMaterial {
  code: string
  name: string
  required: boolean
  uploaded: boolean
  fileName?: string
  fileUrl?: string
  uploadedAt?: string
  uploadedBy?: string
}

export const TEAM_POST_KEYS: Record<string, string[]> = {
  contact: ['PROJECT_CONTACT', '项目联系人'],
  leader: ['PROJECT_LEADER', '项目负责人'],
  techLeader: ['TECH_LEADER', '技术负责人'],
  supervisor: ['PROJECT_SUPERVISOR', '项目主管'],
  chief1: ['L1_CHIEF', '一级总师'],
  chief2: ['L2_CHIEF', '二级总师'],
  hqDirector: ['HQ_DIRECTOR', '总部处室处长'],
  hqSupervisor: ['HQ_SUPERVISOR', '总部处室主管'],
  unitTechDirector: ['UNIT_MINISTER', '单位科技部长'],
  unitTechSupervisor: ['UNIT_SUPERVISOR', '单位科技主管'],
  deptHead: ['DEPT_HEAD', '项目承担部门负责人'],
  hqFinance: ['HQ_FINANCE', '总部财务主管'],
  unitFinanceDirector: ['UNIT_FIN_MINISTER', '单位财务部长'],
  unitFinanceSupervisor: ['UNIT_FIN_SUPERVISOR', '单位财务主管'],
}

export const DECLARATION_POST_ROLES = [
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

const POSTS_REMARK_PREFIX = '__DECLARATION_POSTS__:'

export function parsePersonLabel(label: unknown) {
  const raw = String(label || '').trim()
  const match = raw.match(/^\s*(.*?)\s*[（(]\s*([^）)]+)\s*[）)]\s*$/)
  return { userName: match?.[1]?.trim() || raw, employeeNo: match?.[2]?.trim() || '' }
}

export function teamMembersFromDeclarationPosts(posts: any, projectId: number) {
  return DECLARATION_POST_ROLES.flatMap(([key, groupCode, roleCode, roleName]) => {
    const label = posts?.[key]
    if (!label) return []
    const person = parsePersonLabel(label)
    return [{ projectId, groupCode, roleCode, roleName, ...person }]
  })
}

/** 兼容尚未执行岗位表迁移的服务端：把岗位快照同时保存在申报备注。 */
export function encodeDeclarationPostsRemark(posts: Record<string, string | undefined>) {
  return `${POSTS_REMARK_PREFIX}${JSON.stringify(posts || {})}`
}

export function postsFromDeclarationRemark(remark: unknown): Record<string, string> {
  const raw = String(remark || '')
  if (!raw.startsWith(POSTS_REMARK_PREFIX)) return {}
  try {
    const parsed = JSON.parse(raw.slice(POSTS_REMARK_PREFIX.length))
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

export function unwrapDeclarationDetail(raw: any): { declaration: any; materials: any[] } {
  if (!raw) return { declaration: null, materials: [] }
  if (raw.declaration && typeof raw.declaration === 'object') {
    const materials = Array.isArray(raw.materials)
      ? raw.materials
      : Array.isArray(raw.declaration.materials)
        ? raw.declaration.materials
        : []
    const declaration = {
        ...raw.declaration,
        posts: raw.declaration.posts || postsFromDeclarationRemark(raw.declaration.remark),
        materials,
        declareMaterial: raw.declareMaterial || raw.channel?.declareMaterial || raw.declaration.declareMaterial,
        filingMaterial: raw.filingMaterial || raw.channel?.filingMaterial || raw.declaration.filingMaterial,
        channel: raw.channel,
      }
    return {
      declaration,
      materials,
    }
  }
  return {
    declaration: {
      ...raw,
      posts: raw.posts || postsFromDeclarationRemark(raw.remark),
    },
    materials: Array.isArray(raw.materials) ? raw.materials : [],
  }
}

export function personLabelOf(m: any): string {
  if (!m) return ''
  if (typeof m === 'string') return m.trim()
  const name = String(m.userName || m.realName || m.name || m.applicant || '').trim()
  const no = String(m.employeeNo || '').replace(/\D/g, '')
  if (name && no) return `${name}（${no}）`
  return name || no
}

export function findTeamMember(members: any[] | undefined, keys: string[]): any | undefined {
  for (const key of keys) {
    const hit = (members || []).find((m) => {
      const code = String(m.roleCode || '')
      const role = String(m.roleName || '')
      return code === key || role === key || role.includes(key)
    })
    if (hit) return hit
  }
  return undefined
}

export function postsFromTeamMembers(
  members: any[] | undefined,
  fallback: Record<string, string | undefined> = {},
): Record<string, string | undefined> {
  const posts: Record<string, string | undefined> = { ...fallback }
  for (const [key, keys] of Object.entries(TEAM_POST_KEYS)) {
    const hit = findTeamMember(members, keys)
    const label = personLabelOf(hit)
    if (label) posts[key] = label
  }
  return posts
}

export function mergePosts(
  ...sources: Array<Record<string, string | undefined> | undefined | null>
): Record<string, string | undefined> {
  const out: Record<string, string | undefined> = {}
  for (const src of sources) {
    if (!src) continue
    for (const [k, v] of Object.entries(src)) {
      const val = String(v || '').trim()
      if (val && val !== '待指定') out[k] = val
    }
  }
  return out
}

export function splitMaterialNames(raw?: string | string[] | null): string[] {
  if (Array.isArray(raw)) {
    return raw.map((s) => String(s).trim()).filter(Boolean)
  }
  if (typeof raw === 'string' && raw.trim()) {
    return raw
      .split(/[,，、;；|/]+/)
      .map((s) => String(s).trim())
      .filter(Boolean)
  }
  return []
}

export function channelRequiredMaterials(channel: any, kind: 'declare' | 'filing'): string[] {
  if (!channel) return []
  const list = kind === 'declare' ? channel.declareMaterialList : channel.filingMaterialList
  const raw = kind === 'declare' ? channel.declareMaterial : channel.filingMaterial
  const fromList = splitMaterialNames(list)
  return fromList.length ? fromList : splitMaterialNames(raw)
}

export function isMaterialUploaded(m: any): boolean {
  if (!m) return false
  if (m.uploaded === true || m.uploaded === 1) return true
  return !!(m.fileName || m.fileUrl || m.uploadedAt)
}

function materialNameOf(m: any): string {
  return String(m?.fieldName || m?.name || m?.materialName || '').trim()
}

/** 渠道必传清单 × 接口已传附件，生成流转图材料行 */
export function liveMaterials(opts: {
  requiredNames?: string[]
  records?: any[] | null
}): LiveMaterial[] {
  const records = Array.isArray(opts.records) ? opts.records : []
  const applicable = records.filter((m) => Number(m.locked) !== 1)
  let required = (opts.requiredNames || []).map((s) => s.trim()).filter(Boolean)

  if (!required.length) {
    required = applicable
      .filter((m) => Number(m.required) === 1 || Number(m.locked) === 0)
      .map(materialNameOf)
      .filter(Boolean)
  }

  const seen = new Set<string>()
  const list: LiveMaterial[] = []

  const push = (name: string, rec?: any, requiredFlag = true) => {
    if (!name || seen.has(name)) return
    seen.add(name)
    const hit =
      rec ||
      applicable.find((m) => materialNameOf(m) === name) ||
      records.find((m) => materialNameOf(m) === name)
    list.push({
      code: String(hit?.fieldCode || hit?.code || hit?.id || name),
      name,
      required: requiredFlag,
      uploaded: isMaterialUploaded(hit),
      fileName: hit?.fileName,
      fileUrl: hit?.fileUrl,
      uploadedAt: hit?.uploadedAt ? String(hit.uploadedAt).slice(0, 16).replace('T', ' ') : undefined,
      uploadedBy: hit?.uploadedBy,
    })
  }

  for (const name of required) push(name, undefined, true)

  for (const rec of applicable) {
    const name = materialNameOf(rec)
    if (!name) continue
    if (required.length && !required.includes(name)) continue
    if (isMaterialUploaded(rec) || Number(rec.required) === 1) push(name, rec, Number(rec.required) === 1)
  }

  return list
}

export function materialSummary(items: LiveMaterial[]): string {
  if (!items.length) return ''
  return items
    .map((m) => {
      if (m.uploaded) return `${m.name}（已传${m.fileName ? `：${m.fileName}` : ''}）`
      return `${m.name}（未传）`
    })
    .join('、')
}

export function inferDeclareStatus(projectStatus?: string): { status: string; flowNode?: string } {
  const st = String(projectStatus || '')
  if (st === 'DRAFT' || st === 'DECLARING') return { status: 'APPROVING' }
  if (st === 'FILING' || st === 'PENDING_FILING') return { status: 'APPROVED' }
  return { status: 'APPROVED' }
}

export function inferFilingPhase(projectStatus?: string, filingStatus?: string): string {
  const fs = String(filingStatus || '').toUpperCase()
  if (fs === 'ARCHIVED' || fs === 'FILED' || fs === 'DONE') return 'DONE'
  if (fs === 'RETURN' || fs === 'REJECTED') return 'RETURN'
  if (fs === 'AUDIT' || fs === 'APPROVING') return 'AUDIT'
  if (fs === 'PENDING' || fs === 'SUBMIT') return 'SUBMIT'
  const st = String(projectStatus || '')
  if (st === 'DECLARING' || st === 'DRAFT') return 'SUBMIT'
  if (st === 'FILING' || st === 'PENDING_FILING') return 'SUBMIT'
  if (st === 'REJECTED') return 'RETURN'
  return 'DONE'
}
