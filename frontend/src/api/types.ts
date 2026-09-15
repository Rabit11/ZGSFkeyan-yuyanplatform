/**
 * 平台统一领域模型定义
 * 字段与 backend/src/main/resources/db/schema.sql 保持一致
 */

/** 统一响应体 */
export interface Res<T> {
  code: number
  msg: string
  data: T
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface PageQuery {
  page?: number
  size?: number
  [key: string]: any
}

/* ------------------------------ 四色状态 ------------------------------ */
export type ColorStatus = 'GREEN' | 'BLUE' | 'YELLOW' | 'RED'

export const COLOR_TEXT: Record<ColorStatus, string> = {
  GREEN: '已完成',
  BLUE: '正常推进',
  YELLOW: '临期预警',
  RED: '逾期告警',
}

/** 颜色权重，用于聚合取最严重状态（红 > 黄 > 蓝 > 绿） */
export const COLOR_WEIGHT: Record<ColorStatus, number> = {
  RED: 4,
  YELLOW: 3,
  BLUE: 2,
  GREEN: 1,
}

/* ------------------------------ 项目层级 ------------------------------ */
export type LevelCode = 'NATIONAL' | 'LOCAL' | 'COMPANY'
export const LEVEL_TEXT: Record<LevelCode, string> = {
  NATIONAL: '国家级',
  LOCAL: '地方级',
  COMPANY: '公司级',
}

/* ------------------------------ 项目状态 ------------------------------ */
export const PROJECT_STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿',
  DECLARING: '申报中',
  FILING: '立项中',
  IMPLEMENTING: '进行中',
  DELAYED: '已延期',
  ACCEPTING: '验收中',
  COMPANY_ACCEPTED: '已通过公司级验收',
  GOV_ACCEPTED: '已通过机关验收',
  FINISHED: '已完成',
  TERMINATED: '已终止',
}

/* ------------------------------ 角色 ------------------------------ */
export type RoleCode =
  | 'PROJECT_TEAM'
  | 'CHIEF_ENGINEER'
  | 'MANAGEMENT'
  | 'FINANCE'
  | 'ADMIN'

export const ROLE_TEXT: Record<RoleCode, string> = {
  PROJECT_TEAM: '项目团队',
  CHIEF_ENGINEER: '责任总师',
  MANAGEMENT: '管理团队',
  FINANCE: '财务团队',
  ADMIN: '超级管理员',
}

/* ------------------------------ 数据字典 ------------------------------ */
export interface SysDict {
  id?: number
  dictType: string
  dictCode: string
  dictName: string
  parentCode?: string
  sort?: number
  status?: number
}

/* ------------------------------ 项目渠道 ------------------------------ */
export interface ProjChannel {
  id: number
  channelCode: string
  channelName: string
  levelCode: LevelCode
  channelDept?: string
  channelOffice?: string
  innerDept?: string
  innerOffice?: string
  flowNodes?: string
  declareMaterial?: string
  filingMaterial?: string
  status?: number
}

/* ------------------------------ 项目一本账 ------------------------------ */
export interface ProjInfo {
  supplement?: { status:string; pending:number; returned:number; approved:number; total:number; submitted:number; updatedAt?:string; approvedSections?:any[] }
  supplementReconciliation?: boolean
  id: number
  projectNo: string
  name: string
  goal?: string
  startDate?: string
  endDate?: string
  levelCode?: LevelCode
  filingDept?: string
  channelId?: number
  channelName?: string
  leadOrgId?: number
  leadOrgName?: string
  mainWork?: string
  status?: string
  transformStatus?: string
  warnColor?: ColorStatus
  totalFund?: number
  expenseTotal?: number
  yearBudget?: number
  yearExpense?: number
  outsourceAmount?: number
  nationalFund?: number
  selfFund?: number
  /** 管理/需求单位 */
  manageOrgName?: string
  /** 司局/处室（辅助筛选与列表展示） */
  bureauOffice?: string
  /** 项目类型 */
  projectType?: string
  major1?: string
  major2?: string
  /** 数据来源：PLATFORM 平台同步 / FORM_MAINT 表单维护导入 */
  dataSource?: 'PLATFORM' | 'FORM_MAINT'
  /** 项目负责人（表单维护） */
  ownerName?: string
  /** 验收状态文案 */
  acceptStatus?: string
  orgId?: number
  orgName?: string
  createByName?: string
  createdAt?: string
  participants?: ProjParticipant[]
  teamMembers?: ProjTeamMember[]
  annualPlans?: ProjAnnualPlan[]
}

export interface ProjParticipant {
  id?: number
  projectId?: number
  orgName?: string
  workContent?: string
  sort?: number
}

export interface ProjTeamMember {
  id?: number
  projectId?: number
  groupCode?: 'TECH' | 'EXPERT' | 'MGMT' | 'FIN'
  roleCode?: string
  roleName?: string
  userName?: string
  employeeNo?: string
}

export interface ProjAnnualPlan {
  id?: number
  projectId?: number
  year?: number
  annualGoal?: string
  planContent?: string
  dueDate?: string
  finishStatus?: string
  colorStatus?: ColorStatus
}

/* ------------------------------ 里程碑 ------------------------------ */
export interface ProjMilestone {
  id: number
  projectId: number
  year?: number
  name: string
  planDate?: string
  actualDate?: string
  budget?: number
  status?: 'DOING' | 'DONE' | 'OVERDUE' | 'CLOSE_DEPT_AUDIT' | 'CLOSE_UNIT_AUDIT'
  colorStatus?: ColorStatus
  evidence?: number
  materials?: ProjMaterial[]
  lagReason?: string
  projectName?: string
  projectNo?: string
  ownerName?: string
}

export interface MilestoneTodo {
  draftId?: number
  flowNodeName?: string
  submittedBy?: string
  submittedAt?: string
  taskType: 'COMPILE' | 'COMPILE_AUDIT' | 'CLOSE' | 'CLOSE_AUDIT' | 'BASIC_AUDIT'
  typeLabel?: string
  projectId: number
  projectNo?: string
  projectName?: string
  ownerName?: string
  year?: number
  milestoneId?: number
  milestoneName?: string
  planDate?: string
  colorStatus?: ColorStatus
  status?: string
  flowNode?: string
  materialCount?: number
  materials?: ProjMaterial[]
}

export interface MilestoneProjectBoard {
  projectId: number
  projectNo?: string
  projectName?: string
  ownerName?: string
  annualGoal?: string
  planContent?: string
  annualStatus?: string
  annualAuditPending?: boolean
  annualArchived?: boolean
  year?: number
  warnColor?: ColorStatus
  msDone?: number
  msTotal?: number
  milestones?: ProjMilestone[]
}

/* ------------------------------ 计划 ------------------------------ */
export interface ProjPlan {
  id: number
  projectId: number
  source?: string
  title?: string
  planType?: 'TODO' | 'DONE'
  dueDate?: string
  finishDate?: string
  owner?: string
  status?: string
  colorStatus?: ColorStatus
  applyStatus?: string
}

/* ------------------------------ 经费 ------------------------------ */
export interface FundBudget {
  id: number
  projectId: number
  year?: number
  milestoneId?: number
  milestoneName?: string
  amount?: number
  status?: string
}

export interface FundPayment {
  id: number
  projectId: number
  budgetId?: number
  flowType?: 'PAY' | 'EXPENSE' | 'WRITEOFF'
  amount?: number
  voucherNo?: string
  occurDate?: string
  writeoffStatus?: string
  operator?: string
}

export interface HqFundBudget {
  id: number
  year: number
  totalAmount?: number
  status?: string
  approveStatus?: string
}

export interface HqFundQuota {
  id: number
  budgetId: number
  orgId?: number
  orgName?: string
  quotaAmount?: number
  usedAmount?: number
}

export interface HqFundTransfer {
  id: number
  budgetId: number
  quotaId?: number
  orgId?: number
  orgName?: string
  amount?: number
  applyNo?: string
  reason?: string
  status?: string
  applyAt?: string
}

/* ------------------------------ 评估检查 ------------------------------ */
export interface ProjEvaluation {
  id: number
  projectId: number
  evalType?: string
  name?: string
  dueDate?: string
  result?: 'PASS' | 'FAIL'
  status?: string
}

/* ------------------------------ 变更 ------------------------------ */
export interface ProjChange {
  id: number
  changeNo?: string
  projectId: number
  projectName?: string
  changeType?: 'PROJECT' | 'DATA'
  category?: string
  title?: string
  reason?: string
  beforeValue?: string
  afterValue?: string
  legalReview?: number
  status?: string
  flowNode?: string
  applicant?: string
  createdAt?: string
}

/* ------------------------------ 立项申报/备案 ------------------------------ */
/** 申报须填岗位：姓名及工号（存展示文案，如「顾思远（100013）」） */
export interface DeclarationPosts {
  contact?: string
  leader?: string
  techLeader?: string
  supervisor?: string
  chief1?: string
  chief2?: string
  hqDirector?: string
  hqSupervisor?: string
  unitTechDirector?: string
  unitTechSupervisor?: string
  deptHead?: string
  hqFinance?: string
  unitFinanceDirector?: string
  unitFinanceSupervisor?: string
}

export interface ProjDeclaration {
  id: number
  applyNo?: string
  name: string
  channelId?: number
  channelName?: string
  levelCode?: LevelCode
  /** 是否需要审批：1 需审批 / 0 无需审批直接报备 */
  needApproval?: number
  goal?: string
  applyFund?: number
  startDate?: string
  endDate?: string
  partnerOrgs?: string
  major1?: string
  major2?: string
  demandOrg?: string
  /** 责任单位/牵头单位 */
  leadOrgName?: string
  leadWorkContent?: string
  orgId?: number
  orgName?: string
  applicant?: string
  posts?: DeclarationPosts
  applyAt?: string
  status?: string
  flowNode?: string
  opinion?: string
  /** 审签步骤（流转图用；无则前端按 needApproval/posts/flowNode 推导） */
  steps?: {
    title: string
    dept?: string
    status?: string
    owner?: { name?: string; label?: string }
    assignee?: string
  }[]
}

export interface ProjMaterial {
  id: number
  bizType?: string
  bizId?: number
  fieldCode?: string
  fieldName?: string
  fileName?: string
  fileUrl?: string
  version?: number
  required?: number
  locked?: number
  uploadedAt?: string
}

/* ------------------------------ 验收 ------------------------------ */
export interface ProjAcceptance {
  id?: number
  projectId: number
  acceptLevel?: string
  status?: string
  currentNode?: string
  latestOpinion?: string
  latestProcessAt?: string
  applyAt?: string
  finishAt?: string
  conclusion?: string
  expertReview?: number
  partnerDueDate?: string
}

export interface ProjAcceptanceItem {
  id: number
  acceptanceId?: number
  levelCode?: string
  levelName?: string
  fieldCode?: string
  materialName?: string
  required?: number
  locked?: number
  fileName?: string
  fileSize?: number
  uploadedBy?: string
  uploadedAt?: string
  fileUrl?: string
  status?: string
}

export interface CheckItem {
  key: string
  label: string
  passed: boolean
  message: string
}

export interface AcceptanceResultHandoff {
  projectId: number
  projectNo?: string
  projectName?: string
  acceptanceId?: number
  acceptLevel?: string
  acceptanceStatus?: string
  currentNode?: string
  acceptedAt?: string
  conclusion?: string
  partnerDueDate?: string
  resultReady: boolean
  nextBiz: 'ACHIEVEMENT_ACCEPTANCE'
  nextBizStatus: 'READY' | 'WAIT_ACCEPTANCE_DONE'
  materialCount: number
  deliverableCount: number
  deliveredDeliverableCount: number
  acceptanceMaterials: ProjAcceptanceItem[]
  deliveredDeliverables: ProjDeliverable[]
  achievementNos: string[]
}

/* ------------------------------ 交付物 ------------------------------ */
export interface ProjDeliverable {
  id: number
  projectId: number
  milestoneId?: number
  name: string
  deliverType?: string
  dueDate?: string
  deliverDate?: string
  status?: 'PENDING' | 'DELIVERED' | 'OVERDUE'
  colorStatus?: ColorStatus
  ownerOrgs?: string
  achievementNo?: string
  fileName?: string
  fileUrl?: string
}

/* ------------------------------ 协作单位评价 ------------------------------ */
export interface PartnerEval {
  id: number
  projectId: number
  partnerName: string
  partnerType?: 'LEAD' | 'PARTNER' | 'OUTSOURCE'
  techScore?: number
  qualityScore?: number
  progressScore?: number
  serviceScore?: number
  complianceScore?: number
  score?: number
  grade?: 'EXCELLENT' | 'GOOD' | 'PASS' | 'FAIL'
  evalDate?: string
  evaluator?: string
  dueDate?: string
  status?: string
}

/* ------------------------------ 成果转化 ------------------------------ */
export interface AchvTransform {
  id: number
  achievementNo: string
  name: string
  projectId?: number
  projectNo?: string
  intro?: string
  transformWay?: 'MODEL' | 'MARKET'
  transformForm?: string
  planDate?: string
  actualDate?: string
  status?: string
  colorStatus?: ColorStatus
  introDetail?: string
  dutyOrg?: string
  itemCount?: number
  deliverables?: ProjDeliverable[]
}

/* ------------------------------ 后评价 ------------------------------ */
export interface ProjPostEval {
  id: number
  projectId: number
  projectName?: string
  dueDate?: string
  goalAchieve?: string
  progressCtrl?: string
  fundExec?: string
  achievementOutput?: string
  partnerPerform?: string
  riskCtrl?: string
  score?: number
  conclusion?: string
  status?: string
  colorStatus?: ColorStatus
}

/* ------------------------------ 预警 / 审计 ------------------------------ */
export interface SysWarning {
  id: number
  bizType?: string
  projectId?: number
  projectName?: string
  warnLevel?: 'YELLOW' | 'RED'
  title?: string
  content?: string
  isRead?: number
  createdAt?: string
}

export interface SysAuditLog {
  id: number
  userName?: string
  module?: string
  action?: string
  content?: string
  createdAt?: string
}

/* ------------------------------ 数据范围 ------------------------------ */
export type DataScope = 'COMPANY' | 'UNIT' | 'DEPT' | 'PROJECT' | 'SELF' | 'SELECTED'
export const DATA_SCOPE_TEXT: Record<DataScope, string> = {
  COMPANY: '全公司/总部',
  UNIT: '本单位',
  DEPT: '本部门',
  PROJECT: '本人参与项目',
  SELF: '仅本人',
  SELECTED: '指定范围',
}

/** 表单维护范围：hq总部全部 / unit本单位 / channel指定渠道 / type指定类型 / self仅本人 */
export type FormMaintScope = 'hq' | 'unit' | 'channel' | 'type' | 'self' | ''

/* ------------------------------ 用户 / 成员 ------------------------------ */
export interface SysUser {
  id: number
  username?: string
  realName?: string
  /** 工号 */
  employeeNo?: string
  orgId?: number
  /** 主单位 */
  orgName?: string
  /** 主部门 */
  deptName?: string
  email?: string
  phone?: string
  mobile?: string
  /** 登录任职身份（中文标签，对齐 16 类） */
  identity?: string
  /** 任职身份编码 */
  identityCode?: string
  /** 项目岗位（展示名；实际办理权按项目内岗位矩阵） */
  projectPost?: string
  /** 职级 */
  rankTitle?: string
  /** 数据范围 */
  dataScope?: DataScope
  /** 是否具备项目内办结权限 */
  finishAuth?: number
  /** 表单维护范围 */
  formMaintScope?: FormMaintScope
  /** 指定渠道（逗号分隔渠道编码） */
  formMaintChannels?: string
  /** 指定项目类型（逗号分隔） */
  formMaintTypes?: string
  /** 立项审批结果专项权限：1 具备 */
  declareResultAccess?: number
  status?: number
  roles?: RoleCode[]
}

/** 项目岗位办理权限矩阵 */
export type PostPermMatrix = Record<string, string[]>

export interface LoginResult {
  token: string
  userId: number
  realName: string
  roles: RoleCode[]
  orgId?: number
  orgName?: string
  identity?: string
  identityCode?: string
  employeeNo?: string
  username?: string
  dataScope?: string
}

/* ------------------------------ 看板 ------------------------------ */
export interface DashboardOverview {
  projectCount: number
  runningCount: number
  overdueCount: number
  totalFund: number
  yearBudget: number
  yearExpense: number
  deliverableCount: number
  transformCount: number
  levelDist: { name: string; value: number }[]
  statusDist: { name: string; value: number }[]
  channelDist: { name: string; value: number }[]
  fundTrend: { month: string; budget: number; expense: number }[]
}

export type BasicDraftStatus = 'NONE' | 'DRAFT' | 'APPROVING' | 'APPROVED' | 'REJECTED'

export interface BasicDraftFlowNode {
  code: string
  name: string
  skipped?: boolean
}

export interface BasicDraftAudit {
  node?: string
  nodeName?: string
  actor?: string
  actorNo?: string
  pass?: boolean
  opinion?: string
  time?: string
}

export interface BasicDraft {
  id?: number
  status: BasicDraftStatus
  flowNode?: string
  flowNodeName?: string
  flowNodes: BasicDraftFlowNode[]
  payload: (Partial<ProjInfo> & { participants?: ProjParticipant[]; teamMembers?: ProjTeamMember[] }) | null
  auditTrail: BasicDraftAudit[]
  canEdit?: boolean
  canSubmit?: boolean
  canAudit?: boolean
  submittedBy?: string
  submittedAt?: string
}

export interface PendingBasicDraft {
  draftId: number
  projectId: number
  projectNo?: string
  projectName?: string
  ownerName?: string
  flowNode?: string
  flowNodeName?: string
  submittedBy?: string
  submittedAt?: string
}
