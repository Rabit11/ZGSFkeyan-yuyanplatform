import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'

const root = path.resolve(import.meta.dirname, '..')
const sqlPath = path.resolve(root, '..', 'V7/project/database/business.sql')
const outPath = path.resolve(root, 'docs/现有人员部门角色权限清单.md')

const sql = fs.readFileSync(sqlPath, 'utf8')

const tableColumns = (table) => {
  const re = new RegExp(`CREATE TABLE \`${table}\` \\((?<body>[\\s\\S]*?)\\n\\) ENGINE=`, 'm')
  const match = sql.match(re)
  if (!match) throw new Error(`Missing table ${table}`)
  return match.groups.body
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.startsWith('`'))
    .map((line) => line.match(/^`([^`]+)`/)?.[1])
    .filter(Boolean)
}

const insertValues = (table) => {
  const marker = `INSERT INTO \`${table}\` VALUES `
  const start = sql.indexOf(marker)
  if (start < 0) return []
  const end = sql.indexOf(';\n', start)
  const raw = sql.slice(start + marker.length, end)
  const rows = []
  let row = null
  let cell = ''
  let quoted = false
  for (let i = 0; i < raw.length; i += 1) {
    const ch = raw[i]
    const next = raw[i + 1]
    if (quoted) {
      if (ch === "'" && next === "'") {
        cell += "'"
        i += 1
      } else if (ch === "'" && raw[i - 1] !== '\\') {
        quoted = false
      } else {
        cell += ch
      }
      continue
    }
    if (ch === "'") {
      quoted = true
      continue
    }
    if (ch === '(') {
      row = []
      cell = ''
      continue
    }
    if (ch === ',' && row) {
      row.push(toValue(cell))
      cell = ''
      continue
    }
    if (ch === ')' && row) {
      row.push(toValue(cell))
      rows.push(row)
      row = null
      cell = ''
      continue
    }
    if (row) cell += ch
  }
  return rows
}

const toValue = (value) => {
  const v = value.trim()
  if (v === 'NULL') return ''
  return v
}

const maps = (table) => {
  const cols = tableColumns(table)
  return insertValues(table).map((row) => Object.fromEntries(cols.map((col, index) => [col, row[index] ?? ''])))
}

const users = maps('sys_user')
const roles = maps('sys_role')
const userRoles = maps('sys_user_role')
const postPermissions = maps('sys_post_permission').filter((row) => row.enabled === '1')
const projectMembers = maps('proj_team_member')
const projects = maps('proj_info')

const roleById = new Map(roles.map((role) => [role.id, role]))
const roleCodesByUser = new Map()
for (const row of userRoles) {
  const role = roleById.get(row.role_id)
  if (!role) continue
  const list = roleCodesByUser.get(row.user_id) ?? []
  list.push(`${role.role_name}(${role.role_code})`)
  roleCodesByUser.set(row.user_id, list)
}

const permLabels = {
  baseinfo_edit: '项目基本信息维护',
  milestone_plan: '里程碑计划维护',
  milestone_close: '里程碑节点销项/关闭',
  funds_submit: '经费/预算提交或审核',
  funds_voucher: '付款凭证/核销材料',
  deliverable_manage: '交付物维护',
  eval_collaborator: '协作单位评价',
  transform_update: '成果转化维护',
  declare_submit: '项目申报提交',
  filing_upload: '立项备案上传',
  initiate_approval: '立项流程审批',
  assess_submit: '后评价提交',
  change_submit: '项目变更提交',
  members_edit: '项目团队成员维护',
  plan_manage: '计划管理',
  contract_register: '合同登记',
  assess_archive: '后评价/归档审核',
  accept_apply: '项目验收申请',
}

const identityToPost = {
  admin: 'admin',
  leader: '',
  hqHead: 'hqHead',
  hqStaff: 'hqStaff',
  unitHead: 'unitDeptHead',
  unitStaff: 'unitStaff',
  deptHead: 'deptHead',
  owner: 'owner',
  techLead: 'tech',
  projectPm: 'pm',
  contactLogin: 'contact',
  chief1: 'chief1',
  chief2: 'chief2',
  finHq: 'finHq',
  finHead: 'finHead',
  finStaff: 'finStaff',
}

const dataScopeLabels = {
  COMPANY: '公司/总部范围',
  UNIT: '本单位范围',
  DEPT: '本部门范围',
  PROJECT: '项目范围',
  SELF: '本人/参与项目范围',
  SELECTED: '指定范围',
}

const formMaintLabels = {
  hq: '总部表单维护',
  unit: '单位表单维护',
  channel: '按渠道维护',
  type: '按类型维护',
  self: '本人维护',
}

const projectRoleLabels = {
  PROJECT_CONTACT: '项目联系人',
  PROJECT_LEADER: '项目负责人',
  TECH_LEADER: '技术负责人',
  PROJECT_SUPERVISOR: '项目主管',
  L1_CHIEF: '一级总师',
  L2_CHIEF: '二级总师',
  DEPT_HEAD: '项目承担部门负责人',
  HQ_DIRECTOR: '总部处室处长',
  HQ_SUPERVISOR: '总部处室主管',
  UNIT_MINISTER: '单位科技部长',
  UNIT_SUPERVISOR: '单位科技主管',
  HQ_FINANCE: '总部财务主管',
  UNIT_FIN_MINISTER: '单位财务部长',
  UNIT_FIN_SUPERVISOR: '单位财务主管',
}

const permsByPost = new Map()
for (const row of postPermissions) {
  const list = permsByPost.get(row.post_code) ?? []
  list.push(row.perm_code)
  permsByPost.set(row.post_code, list)
}

const projectById = new Map(projects.map((p) => [p.id, p]))
const projectPostsByEmployee = new Map()
for (const member of projectMembers) {
  const key = member.employee_no
  if (!key) continue
  const project = projectById.get(member.project_id)
  const list = projectPostsByEmployee.get(key) ?? []
  list.push({
    projectId: member.project_id,
    projectNo: project?.project_no ?? '',
    projectName: project?.project_name ?? '',
    roleName: member.role_name,
    roleCode: member.role_code,
    groupCode: member.group_code,
  })
  projectPostsByEmployee.set(key, list)
}

const uniqueProjectPosts = (employeeNo) => {
  const rows = projectPostsByEmployee.get(employeeNo) ?? []
  const byRole = new Map()
  for (const row of rows) {
    const roleName = projectRoleLabels[row.roleCode] ?? row.roleName
    const key = `${roleName}(${row.roleCode})`
    const set = byRole.get(key) ?? new Set()
    set.add(row.projectNo || `项目ID:${row.projectId}`)
    byRole.set(key, set)
  }
  return [...byRole.entries()]
    .map(([role, set]) => `${role}：${set.size}个项目`)
    .join('；') || '未在项目团队表中分配'
}

const mdEscape = (value) => String(value ?? '').replace(/\|/g, '\\|').replace(/\r?\n/g, '<br>')
const yn = (value) => value === '1' ? '是' : '否'
const listPerms = (postCode) => {
  if (postCode === 'admin') return '全部系统管理权限'
  const perms = permsByPost.get(postCode) ?? []
  if (!perms.length) return '未配置项目岗位权限'
  return perms.map((code) => `${permLabels[code] ?? code}(${code})`).join('；')
}

const generatedAt = new Date().toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
const sha = crypto.createHash('sha256').update(sql).digest('hex')

const lines = []
lines.push('# 现有人员部门角色权限清单')
lines.push('')
lines.push(`生成时间：${generatedAt}`)
lines.push('')
lines.push(`数据来源：\`${path.relative(root, sqlPath).replaceAll('\\', '/')}\`，SHA-256：\`${sha}\``)
lines.push('')
lines.push('说明：当前 `application.yml` 中数据库连接仍是占位配置，本机未检测到可直接读取在线库的 MySQL 客户端；因此本清单按 V7 运行包数据库快照导出。账号密码字段已排除。')
lines.push('')
lines.push('## 1. 系统角色包')
lines.push('')
lines.push('| 角色编码 | 角色名称 | 说明 |')
lines.push('| --- | --- | --- |')
for (const role of roles) {
  lines.push(`| ${mdEscape(role.role_code)} | ${mdEscape(role.role_name)} | ${mdEscape(role.remark)} |`)
}
lines.push('')
lines.push('## 2. 人员部门与权限总表')
lines.push('')
lines.push('| 姓名 | 工号/账号 | 组织 | 主部门 | 任职身份 | 身份编码 | 系统角色包 | 项目岗位 | 数据范围 | 办结权限 | 表单维护 | 立项结果专项 | 项目岗位权限 |')
lines.push('| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
for (const user of users.sort((a, b) => Number(a.id) - Number(b.id))) {
  const postCode = identityToPost[user.identity_code] ?? ''
  const rolesText = (roleCodesByUser.get(user.id) ?? []).join('；') || '未绑定系统角色'
  const formMaint = formMaintLabels[user.form_maint_scope] ?? user.form_maint_scope ?? ''
  lines.push(`| ${mdEscape(user.real_name)} | ${mdEscape(user.employee_no || user.username)} | ${mdEscape(user.org_name)} | ${mdEscape(user.dept_name)} | ${mdEscape(user.identity)} | ${mdEscape(user.identity_code)} | ${mdEscape(rolesText)} | ${mdEscape(user.project_post)} | ${mdEscape(dataScopeLabels[user.data_scope] ?? user.data_scope)} | ${yn(user.finish_auth)} | ${mdEscape(formMaint || '无')} | ${yn(user.declare_result_access)} | ${mdEscape(listPerms(postCode))} |`)
}
lines.push('')
lines.push('## 3. 项目岗位权限矩阵')
lines.push('')
lines.push('| 岗位编码 | 已配置权限 |')
lines.push('| --- | --- |')
for (const [postCode, codes] of [...permsByPost.entries()].sort(([a], [b]) => a.localeCompare(b))) {
  lines.push(`| ${mdEscape(postCode)} | ${mdEscape(codes.map((code) => `${permLabels[code] ?? code}(${code})`).join('；'))} |`)
}
lines.push('')
lines.push('## 4. 项目团队分配概览')
lines.push('')
lines.push('| 姓名 | 工号 | 项目团队分配 |')
lines.push('| --- | --- | --- |')
for (const user of users.filter((u) => u.employee_no).sort((a, b) => a.employee_no.localeCompare(b.employee_no))) {
  lines.push(`| ${mdEscape(user.real_name)} | ${mdEscape(user.employee_no)} | ${mdEscape(uniqueProjectPosts(user.employee_no))} |`)
}
lines.push('')
lines.push('## 5. 需要注意的不一致')
lines.push('')
const finStaffPerms = permsByPost.get('finStaff') ?? []
if (!finStaffPerms.length) {
  lines.push('- `finStaff`（单位财务主管）在人员身份中存在，但 `sys_post_permission` 中没有对应岗位权限配置。')
}
const leaderPerms = permsByPost.get('leader') ?? []
if (!leaderPerms.length) {
  lines.push('- `leader`（公司领导）在人员身份中存在，但 `sys_post_permission` 中没有对应岗位权限配置；该类账号主要依赖系统角色和流程守卫判断。')
}
lines.push('- `contactLogin`（登录身份）对应项目岗位权限矩阵中的 `contact`；`unitHead` 对应矩阵中的 `unitDeptHead`。')
lines.push('- 林晚晴（100012）当前账号身份为项目负责人，系统角色包为项目团队；项目岗位权限矩阵中 `owner` 已包含项目联系人常用权限，并额外包含付款凭证、交付物、验收申请等权限。')
lines.push('- 毕仲文（100010）当前账号身份为单位财务部长，数据范围为本单位；快照显示其系统角色包为财务团队，项目岗位表中以单位财务部长身份分配到多个项目。')
lines.push('- 王建国（100003）当前主部门为总部科技部科研项目处，身份为总部责任处室处长，数据范围为公司/总部范围。')
lines.push('')

fs.mkdirSync(path.dirname(outPath), { recursive: true })
fs.writeFileSync(outPath, lines.join('\n'), 'utf8')
console.log(outPath)
