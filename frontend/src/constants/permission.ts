/**
 * 人员权限常量（对齐《不同人员权限梳理》V1.0）
 * 四层：登录任职身份 / 项目岗位 / 数据范围 / 专项授权
 */

import type { DataScope, RoleCode } from '@/api/types'

/* ------------------------------ 16 类登录任职身份 ------------------------------ */
export type IdentityCode =
  | 'admin'
  | 'leader'
  | 'hqHead'
  | 'hqStaff'
  | 'unitHead'
  | 'unitStaff'
  | 'deptHead'
  | 'owner'
  | 'techLead'
  | 'projectPm'
  | 'contactLogin'
  | 'chief1'
  | 'chief2'
  | 'finHq'
  | 'finHead'
  | 'finStaff'

export interface IdentityDef {
  code: IdentityCode
  label: string
  /** 建议菜单角色包 */
  roles: RoleCode[]
  /** 默认数据范围 */
  dataScope: DataScope
  /** 表单维护默认范围（空表示默认关闭） */
  formMaintScope?: FormMaintScope | ''
  /** 默认首页提示 */
  homeHint?: string
}

export const IDENTITY_DEFS: IdentityDef[] = [
  {
    code: 'admin',
    label: '系统管理员',
    roles: ['ADMIN'],
    dataScope: 'COMPANY',
    formMaintScope: 'hq',
    homeHint: '配置中心',
  },
  {
    code: 'leader',
    label: '公司领导',
    roles: ['MANAGEMENT'],
    dataScope: 'COMPANY',
    homeHint: '预研指挥舱（只读）',
  },
  {
    code: 'hqHead',
    label: '总部责任处室处长',
    roles: ['MANAGEMENT'],
    dataScope: 'COMPANY',
    formMaintScope: 'hq',
  },
  {
    code: 'hqStaff',
    label: '总部科研项目主管',
    roles: ['MANAGEMENT'],
    dataScope: 'COMPANY',
    formMaintScope: 'hq',
  },
  {
    code: 'unitHead',
    label: '单位科研管理部门负责人',
    roles: ['MANAGEMENT'],
    dataScope: 'UNIT',
  },
  {
    code: 'unitStaff',
    label: '单位项目主管',
    roles: ['MANAGEMENT'],
    dataScope: 'UNIT',
  },
  {
    code: 'deptHead',
    label: '项目承担部门负责人',
    roles: ['MANAGEMENT'],
    dataScope: 'DEPT',
    homeHint: '申报审签',
  },
  {
    code: 'owner',
    label: '项目负责人',
    roles: ['PROJECT_TEAM'],
    dataScope: 'SELF',
  },
  {
    code: 'techLead',
    label: '技术负责人',
    roles: ['PROJECT_TEAM'],
    dataScope: 'SELF',
    homeHint: '里程碑',
  },
  {
    code: 'projectPm',
    label: '项目主管',
    roles: ['PROJECT_TEAM'],
    dataScope: 'SELF',
    homeHint: '计划管理',
  },
  {
    code: 'contactLogin',
    label: '项目联系人',
    roles: ['PROJECT_TEAM'],
    dataScope: 'SELF',
  },
  {
    code: 'chief1',
    label: '一级总师（公司级）',
    roles: ['CHIEF_ENGINEER'],
    dataScope: 'SELF',
    homeHint: '评审工作台',
  },
  {
    code: 'chief2',
    label: '二级总师（单位级）',
    roles: ['CHIEF_ENGINEER'],
    dataScope: 'SELF',
    homeHint: '评审工作台',
  },
  {
    code: 'finHq',
    label: '总部财务主管',
    roles: ['FINANCE'],
    dataScope: 'COMPANY',
    homeHint: '经费台账',
  },
  {
    code: 'finHead',
    label: '单位财务部长',
    roles: ['FINANCE'],
    dataScope: 'UNIT',
  },
  {
    code: 'finStaff',
    label: '单位财务主管',
    roles: ['FINANCE'],
    dataScope: 'UNIT',
  },
]

export const IDENTITY_LABELS = IDENTITY_DEFS.map((d) => d.label)

export function identityByLabel(label?: string): IdentityDef | undefined {
  if (!label) return undefined
  const exact = IDENTITY_DEFS.find((d) => d.label === label || d.code === label)
  if (exact) return exact
  if (/承担部门|承办部门/.test(label)) return IDENTITY_DEFS.find((d) => d.code === 'deptHead')
  return undefined
}

/** 可视化看板：系统管理员 + 总部人员（公司领导 / 总部处室 / 总部财务） */
export const BOARD_HQ_IDENTITY_CODES: IdentityCode[] = ['admin', 'leader', 'hqHead', 'hqStaff', 'finHq']

export function canViewVisualBoard(user?: {
  roles?: string[]
  identityCode?: string
  identity?: string
  dataScope?: string
} | null) {
  if (!user) return false
  if (user.roles?.includes('ADMIN')) return true
  if (user.dataScope === 'COMPANY') return true
  if (user.identityCode && BOARD_HQ_IDENTITY_CODES.includes(user.identityCode as IdentityCode)) return true
  const ident = String(user.identity || '')
  if (/系统管理员/.test(ident) || /公司领导/.test(ident)) return true
  return /总部/.test(ident) && !/单位/.test(ident)
}

/* ------------------------------ 数据范围（对齐文档） ------------------------------ */
export const DATA_SCOPE_OPTIONS: { value: DataScope; label: string }[] = [
  { value: 'COMPANY', label: '全公司/总部' },
  { value: 'UNIT', label: '本单位' },
  { value: 'DEPT', label: '本部门' },
  { value: 'PROJECT', label: '本人参与项目' },
  { value: 'SELF', label: '仅本人' },
  { value: 'SELECTED', label: '指定范围' },
]

/* ------------------------------ 表单维护专项权限 ------------------------------ */
export type FormMaintScope = 'hq' | 'unit' | 'channel' | 'type' | 'self'

export const FORM_MAINT_OPTIONS: { value: FormMaintScope | ''; label: string }[] = [
  { value: '', label: '无表单维护权限' },
  { value: 'hq', label: '总部全部台账' },
  { value: 'unit', label: '本单位' },
  { value: 'channel', label: '指定渠道' },
  { value: 'type', label: '指定项目类型' },
  { value: 'self', label: '仅本人' },
]

/* ------------------------------ 14 类项目岗位 ------------------------------ */
export type ProjectPostCode =
  | 'contact'
  | 'owner'
  | 'tech'
  | 'pm'
  | 'chief1'
  | 'chief2'
  | 'hqHead'
  | 'hqStaff'
  | 'unitDeptHead'
  | 'unitStaff'
  | 'deptHead'
  | 'finHq'
  | 'finHead'
  | 'finStaff'

export interface ProjectPostDef {
  code: ProjectPostCode
  label: string
}

export const PROJECT_POST_DEFS: ProjectPostDef[] = [
  { code: 'contact', label: '项目联系人' },
  { code: 'owner', label: '项目负责人' },
  { code: 'tech', label: '技术负责人' },
  { code: 'pm', label: '项目主管' },
  { code: 'chief1', label: '一级总师' },
  { code: 'chief2', label: '二级总师' },
  { code: 'hqHead', label: '总部处室处长' },
  { code: 'hqStaff', label: '总部处室主管' },
  { code: 'unitDeptHead', label: '单位科技部长' },
  { code: 'unitStaff', label: '单位科技主管' },
  { code: 'deptHead', label: '项目承担部门负责人' },
  { code: 'finHq', label: '总部财务主管' },
  { code: 'finHead', label: '单位财务部长' },
  { code: 'finStaff', label: '单位财务主管' },
]

/** 成员管理展示用岗位选项（含“暂无”） */
export const MEMBER_POST_OPTIONS = ['暂无项目角色', ...PROJECT_POST_DEFS.map((p) => p.label)]

/* ------------------------------ 18 项项目办理权限 ------------------------------ */
export type ProjectPermCode =
  | 'baseinfo_edit'
  | 'milestone_plan'
  | 'milestone_close'
  | 'plan_manage'
  | 'funds_submit'
  | 'funds_voucher'
  | 'deliverable_manage'
  | 'eval_collaborator'
  | 'transform_update'
  | 'declare_submit'
  | 'filing_upload'
  | 'initiate_approval'
  | 'assess_submit'
  | 'assess_archive'
  | 'change_submit'
  | 'contract_register'
  | 'accept_apply'
  | 'members_edit'

export interface ProjectPermDef {
  code: ProjectPermCode
  label: string
  group: string
}

export const PROJECT_PERM_DEFS: ProjectPermDef[] = [
  { code: 'baseinfo_edit', label: '完善基本信息', group: '基础' },
  { code: 'milestone_plan', label: '编制里程碑计划', group: '里程碑' },
  { code: 'milestone_close', label: '里程碑销项/佐证', group: '里程碑' },
  { code: 'plan_manage', label: '计划填报/办结', group: '计划' },
  { code: 'funds_submit', label: '经费预算填报', group: '经费' },
  { code: 'funds_voucher', label: '经费使用/核销材料', group: '经费' },
  { code: 'deliverable_manage', label: '交付物维护/交付', group: '成果' },
  { code: 'eval_collaborator', label: '协作单位评价', group: '成果' },
  { code: 'transform_update', label: '更新成果转化', group: '成果' },
  { code: 'declare_submit', label: '发起申报', group: '立项' },
  { code: 'filing_upload', label: '上传立项备案材料', group: '立项' },
  { code: 'initiate_approval', label: '发起审批并上传材料', group: '流程' },
  { code: 'assess_submit', label: '评估检查填报', group: '评估' },
  { code: 'assess_archive', label: '上传评估结论/检查材料', group: '评估' },
  { code: 'change_submit', label: '项目/数据变更', group: '变更' },
  { code: 'contract_register', label: '外协合同登记', group: '变更' },
  { code: 'accept_apply', label: '发起验收申请', group: '验收' },
  { code: 'members_edit', label: '指定/转办项目岗位', group: '成员' },
]

export type PostPermMatrix = Record<ProjectPostCode, ProjectPermCode[]>

/** 推荐默认矩阵（文档第四节） */
export const DEFAULT_POST_PERM_MATRIX: PostPermMatrix = {
  contact: [
    'baseinfo_edit',
    'milestone_plan',
    'milestone_close',
    'funds_submit',
    'transform_update',
    'declare_submit',
    'filing_upload',
    'initiate_approval',
    'assess_submit',
    'change_submit',
    'members_edit',
  ],
  owner: [
    'baseinfo_edit',
    'milestone_plan',
    'milestone_close',
    'funds_submit',
    'funds_voucher',
    'deliverable_manage',
    'eval_collaborator',
    'transform_update',
    'declare_submit',
    'filing_upload',
    'initiate_approval',
    'assess_submit',
    'change_submit',
    'accept_apply',
    'members_edit',
  ],
  tech: [
    'baseinfo_edit',
    'milestone_plan',
    'milestone_close',
    'funds_submit',
    'deliverable_manage',
    'initiate_approval',
    'change_submit',
  ],
  pm: [
    'baseinfo_edit',
    'plan_manage',
    'funds_submit',
    'initiate_approval',
    'assess_submit',
    'change_submit',
    'contract_register',
  ],
  chief1: ['funds_submit'],
  chief2: ['funds_submit'],
  hqHead: ['assess_archive'],
  hqStaff: ['assess_archive'],
  unitDeptHead: ['milestone_close', 'initiate_approval', 'assess_archive', 'members_edit'],
  unitStaff: ['milestone_close', 'assess_archive', 'members_edit'],
  deptHead: ['initiate_approval', 'assess_archive', 'members_edit'],
  finHq: ['funds_submit'],
  finHead: ['funds_submit'],
  finStaff: [],
}

export function cloneDefaultMatrix(): PostPermMatrix {
  const out = {} as PostPermMatrix
  for (const p of PROJECT_POST_DEFS) {
    out[p.code] = [...(DEFAULT_POST_PERM_MATRIX[p.code] || [])]
  }
  return out
}

/** 同项目多岗位取并集 */
export function unionPostPerms(matrix: PostPermMatrix, posts: ProjectPostCode[]): ProjectPermCode[] {
  const set = new Set<ProjectPermCode>()
  for (const post of posts) {
    for (const perm of matrix[post] || []) set.add(perm)
  }
  return Array.from(set)
}

/** 总部表单导入入口；不扩大公司领导、财务或单位岗位的导入权限。 */
export function canMaintainImportedProjects(user: { identityCode?: string; roles?: string[] }): boolean {
  return (user.roles || []).includes('ADMIN') || ['admin', 'hqHead', 'hqStaff'].includes(user.identityCode || '')
}
