/**
 * 工作内容定责目录：谁填写 / 谁提交 / 谁审核 / 谁查看 / 谁编辑
 * 岗位取 16 类登录任职身份；进入具体项目后由团队花名解析到人。
 */

import type { IdentityCode } from '@/constants/permission'

export type WorkAction = 'fill' | 'submit' | 'audit' | 'view' | 'edit'

export type WorkDutyCode =
  | 'declare'
  | 'filing'
  | 'basic'
  | 'milestone_compile'
  | 'milestone_close'
  | 'plan'
  | 'fund_budget'
  | 'fund_writeoff'
  | 'evaluation'
  | 'change'
  | 'deliverable'
  | 'partner_eval'
  | 'accept'
  | 'transform'
  | 'post_eval'
  | 'ledger'
  | 'members'

export interface WorkActorRule {
  /** 办理任职身份 */
  identities: IdentityCode[]
  /** 对应项目团队岗位键（flowLive TEAM_POST_KEYS） */
  postKeys: string[]
  /** 岗位中文，用于一览表 */
  roleLabels: string[]
  hint?: string
}

export interface WorkDutyDef {
  code: WorkDutyCode
  stage: string
  title: string
  path: string
  fill: WorkActorRule
  submit: WorkActorRule
  audit: WorkActorRule
  view: WorkActorRule
  edit: WorkActorRule
}

const TEAM: IdentityCode[] = ['owner', 'techLead', 'projectPm', 'contactLogin']
const TEAM_POSTS = ['leader', 'techLeader', 'supervisor', 'contact']
const TEAM_LABELS = ['项目负责人（含项目联系人全部权限）', '技术负责人', '项目主管', '项目联系人']

const MGMT: IdentityCode[] = ['hqHead', 'hqStaff', 'unitHead', 'unitStaff', 'deptHead']
const MGMT_POSTS = ['hqDirector', 'hqSupervisor', 'unitTechDirector', 'unitTechSupervisor', 'deptHead']
const MGMT_LABELS = ['总部处室处长', '总部处室主管', '单位科技部长', '单位科技主管', '项目承担部门负责人']

const VIEW_ALL: IdentityCode[] = [
  'admin',
  'leader',
  'hqHead',
  'hqStaff',
  'unitHead',
  'unitStaff',
  'deptHead',
  'owner',
  'techLead',
  'projectPm',
  'contactLogin',
  'chief1',
  'chief2',
  'finHq',
  'finHead',
  'finStaff',
]

function rule(identities: IdentityCode[], postKeys: string[], roleLabels: string[], hint?: string): WorkActorRule {
  return { identities, postKeys, roleLabels, hint }
}

const none = rule([], [], ['—'], '本环节无此动作')

export const WORK_DUTY_DEFS: WorkDutyDef[] = [
  {
    code: 'declare',
    stage: '立项',
    title: '项目申报信息与材料',
    path: '/initiation/declaration',
    fill: rule(TEAM, TEAM_POSTS, TEAM_LABELS, '项目团队可协同填写，草稿可暂存'),
    submit: rule(TEAM, TEAM_POSTS, TEAM_LABELS, '材料齐套后提交项目负责人审核'),
    audit: rule(
      ['owner', 'deptHead', 'chief2', 'finHead', 'unitHead', 'chief1', 'hqHead', 'hqStaff'],
      ['leader', 'deptHead', 'chief2', 'unitFinanceDirector', 'unitTechDirector', 'chief1', 'hqDirector', 'hqSupervisor'],
      ['项目负责人 → 项目承担部门负责人 → 二级总师 → 单位财务 → 单位科技部长 → 一级总师 → 总部处室'],
      '按当前审批节点指定到人；二级总师前须经项目承担部门负责人审核',
    ),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员'], '能进项目即可查看'),
    edit: rule(TEAM, TEAM_POSTS, TEAM_LABELS, '项目团队在草稿/驳回状态可协同修改'),
  },
  {
    code: 'filing',
    stage: '立项',
    title: '立项支撑材料备案',
    path: '/initiation/filing',
    fill: rule(['owner', 'contactLogin'], ['leader', 'contact'], ['项目负责人', '项目联系人'], '上传本渠道必传原件'),
    submit: rule(['owner'], ['leader'], ['项目负责人'], '齐套后提交总部科技部科研项目处审核备案'),
    audit: rule(['hqHead', 'hqStaff'], ['hqDirector', 'hqSupervisor'], ['总部处室处长', '总部处室主管'], '总部审核备案通过后台账转实施中'),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['owner', 'contactLogin'], ['leader', 'contact'], ['项目负责人', '项目联系人'], '退回补正时可改'),
  },
  {
    code: 'basic',
    stage: '实施',
    title: '项目基本信息 / 工作内容补录',
    path: '/implement/basic',
    fill: rule(['owner', 'contactLogin', 'techLead', 'projectPm'], TEAM_POSTS, TEAM_LABELS, '补齐目标、周期、专业、牵头/参研工作内容、团队'),
    submit: rule(['owner'], ['leader'], ['项目负责人'], '保存并提交二级单位审核'),
    audit: rule(['unitHead'], ['unitTechDirector'], ['单位科技部长'], '通过后总部科研项目处备案'),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['owner', 'contactLogin', 'techLead', 'projectPm'], TEAM_POSTS, TEAM_LABELS, '审核通过前可改；通过后走项目变更'),
  },
  {
    code: 'milestone_compile',
    stage: '实施',
    title: '编制里程碑节点',
    path: '/implement/milestone-close?mode=compile',
    fill: rule(['techLead', 'owner', 'contactLogin'], ['techLeader', 'leader', 'contact'], ['技术负责人', '项目负责人', '项目联系人']),
    submit: rule(['techLead', 'owner'], ['techLeader', 'leader'], ['技术负责人', '项目负责人'], '清单齐备后提交存档'),
    audit: rule(['unitHead'], ['unitTechDirector'], ['单位科技部长'], '二级单位科技部门审核存档'),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['techLead', 'owner', 'contactLogin'], ['techLeader', 'leader', 'contact'], ['技术负责人', '项目负责人', '项目联系人']),
  },
  {
    code: 'milestone_close',
    stage: '实施',
    title: '里程碑销项 / 上传佐证',
    path: '/implement/milestone-close',
    fill: rule(['owner'], ['leader'], ['项目负责人'], '仅项目团队负责人可上传销项材料'),
    submit: rule(['owner'], ['leader'], ['项目负责人'], '仅项目团队负责人可闭环销项'),
    audit: rule(['deptHead', 'unitHead'], ['deptHead', 'unitTechDirector'], ['项目承担部门负责人 → 单位科研管理部门负责人'], '负责人提交销项后，先由项目承担部门负责人审核，再由单位科研管理部门负责人审核；两级都通过后才算节点完成和核销闭环'),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['owner'], ['leader'], ['项目负责人'], '仅项目团队负责人可维护销项信息'),
  },
  {
    code: 'plan',
    stage: '实施',
    title: '计划填报与办结',
    path: '/implement/plan',
    fill: rule(['projectPm', 'owner'], ['supervisor', 'leader'], ['项目主管', '项目负责人']),
    submit: rule(['projectPm', 'owner'], ['supervisor', 'leader'], ['项目主管', '项目负责人'], '办结申请'),
    audit: rule(['unitHead'], ['unitTechDirector'], ['单位科技部长'], '二级单位管理团队终审，无总部审批'),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['projectPm', 'owner'], ['supervisor', 'leader'], ['项目主管', '项目负责人']),
  },
  {
    code: 'fund_budget',
    stage: '实施',
    title: '经费预算填报',
    path: '/implement/fund?mode=budget',
    fill: rule(TEAM, TEAM_POSTS, TEAM_LABELS, '按项目年度和预算项填写，团队成员可暂存'),
    submit: rule(['owner'], ['leader'], ['项目负责人'], '负责人提交审签'),
    audit: rule(
      ['finHead', 'finStaff', 'finHq'],
      ['unitFinanceDirector', 'unitFinanceSupervisor', 'hqFinance'],
      ['单位财务部长 → 总部财务主管'],
      '单位财务审核后总部复核备案',
    ),
    view: rule(
      ['admin', 'leader', 'hqHead', 'hqStaff', 'unitHead', 'finHq', 'finHead', 'finStaff', ...TEAM],
      TEAM_POSTS.concat(['hqFinance', 'unitFinanceDirector', 'unitFinanceSupervisor']),
      ['项目团队、两级财务、管理团队'],
    ),
    edit: rule(TEAM, TEAM_POSTS, TEAM_LABELS, '草稿/退回可改'),
  },
    {
      code: 'fund_writeoff',
      stage: '实施',
      title: '经费核销',
      path: '/implement/fund?mode=writeoff',
      fill: rule(['finHead'], ['unitFinanceDirector'], ['二级单位财务负责人'], '按实际费用上传付款凭证并填报核销信息'),
      submit: rule(['finHead'], ['unitFinanceDirector'], ['二级单位财务负责人'], '上传付款凭证并完成本级核销后，数据自动同步总部经费看板'),
      audit: rule(['finHead'], ['unitFinanceDirector'], ['二级单位财务负责人'], '二级单位财务完成本级核销即完成经费核销，系统同步总部经费看板'),
      view: rule(
        ['admin', 'leader', 'hqHead', 'finHq', 'finHead', 'finStaff', ...TEAM],
        TEAM_POSTS.concat(['hqFinance', 'unitFinanceDirector']),
        ['项目团队、两级财务'],
      ),
      edit: rule(['finHead'], ['unitFinanceDirector'], ['二级单位财务负责人'], '暂存可改'),
    },
  {
    code: 'evaluation',
    stage: '实施',
    title: '评估检查',
    path: '/implement/evaluation',
    fill: rule(['owner', 'projectPm', 'contactLogin'], ['leader', 'supervisor', 'contact'], ['项目负责人', '项目主管', '项目联系人']),
    submit: rule(['owner'], ['leader'], ['项目负责人']),
    audit: rule(['unitHead', 'hqHead', 'hqStaff'], ['unitTechDirector', 'hqDirector', 'hqSupervisor'], ['单位科技部长', '总部处室'], '单位上传结论，总部归档'),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['owner', 'projectPm', 'contactLogin'], ['leader', 'supervisor', 'contact'], ['项目负责人', '项目主管', '项目联系人']),
  },
  {
    code: 'change',
    stage: '实施',
    title: '项目 / 数据变更',
    path: '/implement/change',
    fill: rule(['owner', 'projectPm', 'techLead', 'contactLogin'], TEAM_POSTS, TEAM_LABELS),
    submit: rule(['owner', 'projectPm', 'contactLogin'], ['leader', 'supervisor', 'contact'], ['项目负责人', '项目主管', '项目联系人']),
    audit: rule(
      ['unitHead', 'hqHead', 'hqStaff'],
      ['unitTechDirector', 'hqDirector', 'hqSupervisor'],
      ['单位主管部门 → 总部（重大变更含法务）'],
    ),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['owner', 'projectPm', 'techLead', 'contactLogin'], TEAM_POSTS, TEAM_LABELS, '草稿/驳回可改'),
  },
  {
    code: 'deliverable',
    stage: '验收',
    title: '交付物维护 / 交付',
    path: '/acceptance/deliverable',
    fill: rule(['techLead', 'owner'], ['techLeader', 'leader'], ['技术负责人', '项目负责人']),
    submit: rule(['owner', 'techLead'], ['leader', 'techLeader'], ['项目负责人', '技术负责人']),
    audit: none,
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['techLead', 'owner'], ['techLeader', 'leader'], ['技术负责人', '项目负责人']),
  },
  {
    code: 'partner_eval',
    stage: '验收',
    title: '协作单位评价',
    path: '/acceptance/partner',
    fill: rule(['owner'], ['leader'], ['项目负责人']),
    submit: rule(['owner'], ['leader'], ['项目负责人']),
    audit: none,
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['owner'], ['leader'], ['项目负责人']),
  },
  {
    code: 'accept',
    stage: '验收',
    title: '项目验收申请与办结',
    path: '/acceptance/accept',
    fill: rule(TEAM, TEAM_POSTS, TEAM_LABELS, '上传本层级验收材料'),
    submit: rule(['owner'], ['leader'], ['项目负责人'], '前置校验通过后提交'),
    audit: rule(['unitHead', 'hqHead', 'hqStaff'], ['unitTechDirector', 'hqDirector', 'hqSupervisor'], ['单位科技部长', '总部处室']),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(TEAM, TEAM_POSTS, TEAM_LABELS),
  },
  {
    code: 'transform',
    stage: '转化',
    title: '成果转化',
    path: '/transform',
    fill: rule(['owner', 'contactLogin'], ['leader', 'contact'], ['项目负责人', '项目联系人']),
    submit: rule(['owner'], ['leader'], ['项目负责人']),
    audit: rule(['unitHead', 'hqHead', 'hqStaff'], ['unitTechDirector', 'hqDirector', 'hqSupervisor'], ['单位科技部长', '总部处室']),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['owner', 'contactLogin'], ['leader', 'contact'], ['项目负责人', '项目联系人']),
  },
  {
    code: 'post_eval',
    stage: '后评价',
    title: '后评价',
    path: '/post-eval',
    fill: rule(['owner', 'unitHead', 'hqStaff'], ['leader', 'unitTechDirector', 'hqSupervisor'], ['项目负责人', '单位科技部长', '总部处室主管']),
    submit: rule(['owner', 'unitHead'], ['leader', 'unitTechDirector'], ['项目负责人', '单位科技部长']),
    audit: rule(['hqHead', 'hqStaff'], ['hqDirector', 'hqSupervisor'], ['总部处室']),
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['owner', 'unitHead', 'hqStaff'], ['leader', 'unitTechDirector', 'hqSupervisor'], ['项目负责人', '单位科技部长', '总部处室主管']),
  },
  {
    code: 'ledger',
    stage: '总览',
    title: '项目台账维护',
    path: '/overview/ledger',
    fill: none,
    submit: none,
    audit: none,
    view: rule(VIEW_ALL, [], ['按数据范围：全公司 / 本单位 / 本人项目'], '公司领导只读'),
    edit: rule(['hqHead', 'hqStaff', 'unitHead', 'unitStaff', 'admin'], MGMT_POSTS, ['总部处室', '单位科技管理'], '项目团队改基本信息请走实施填报'),
  },
  {
    code: 'members',
    stage: '实施',
    title: '指定 / 转办项目岗位',
    path: '/implement/basic',
    fill: rule(['owner', 'unitHead', 'unitStaff', 'contactLogin'], ['leader', 'unitTechDirector', 'unitTechSupervisor', 'contact'], ['项目负责人', '单位科技部长', '单位科技主管', '项目联系人']),
    submit: rule(['owner', 'unitHead'], ['leader', 'unitTechDirector'], ['项目负责人', '单位科技部长']),
    audit: none,
    view: rule(VIEW_ALL, TEAM_POSTS.concat(MGMT_POSTS), ['项目相关人员']),
    edit: rule(['owner', 'unitHead', 'unitStaff', 'contactLogin'], ['leader', 'unitTechDirector', 'unitTechSupervisor', 'contact'], ['项目负责人', '单位科技部长', '单位科技主管', '项目联系人']),
  },
]

export const WORK_ACTION_LABEL: Record<WorkAction, string> = {
  fill: '填写',
  submit: '提交',
  audit: '审核',
  view: '查看',
  edit: '编辑',
}

export function workDutyByCode(code: WorkDutyCode): WorkDutyDef {
  return WORK_DUTY_DEFS.find((d) => d.code === code) || WORK_DUTY_DEFS[0]
}

export function workDutyByPath(path?: string): WorkDutyDef | undefined {
  const p = String(path || '')
  return WORK_DUTY_DEFS.find((d) => p === d.path || p.startsWith(d.path.split('?')[0]))
}

