import dayjs from 'dayjs'
import type {
  AchvTransform,
  ColorStatus,
  FundBudget,
  FundPayment,
  HqFundBudget,
  HqFundQuota,
  HqFundTransfer,
  PartnerEval,
  ProjAcceptance,
  ProjAcceptanceItem,
  ProjAnnualPlan,
  ProjChange,
  ProjChannel,
  ProjDeclaration,
  ProjDeliverable,
  ProjEvaluation,
  ProjInfo,
  ProjMaterial,
  ProjMilestone,
  ProjPlan,
  ProjPostEval,
  SysAuditLog,
  SysDict,
  SysWarning,
  SysUser,
} from '@/api/types'
import { calcColor, mergeColor } from '@/utils/color'
import majorConfig from '@/config/major1-major2.json'
import { cloneDefaultMatrix } from '@/constants/permission'

let seed = 1000
const nid = () => ++seed

const d = (offset: number) => dayjs().add(offset, 'day').format('YYYY-MM-DD')
const TODAY = dayjs()

/* ============================ 数据字典 ============================ */
export const dicts: SysDict[] = [
  { dictType: 'PROJECT_LEVEL', dictCode: 'NATIONAL', dictName: '国家级' },
  { dictType: 'PROJECT_LEVEL', dictCode: 'LOCAL', dictName: '地方级' },
  { dictType: 'PROJECT_LEVEL', dictCode: 'COMPANY', dictName: '公司级' },
  { dictType: 'DELIVERABLE_TYPE', dictCode: 'PATENT', dictName: '专利' },
  { dictType: 'DELIVERABLE_TYPE', dictCode: 'PAPER', dictName: '论文' },
  { dictType: 'DELIVERABLE_TYPE', dictCode: 'SOFTWARE', dictName: '软著' },
  { dictType: 'DELIVERABLE_TYPE', dictCode: 'STANDARD', dictName: '技术标准' },
  { dictType: 'DELIVERABLE_TYPE', dictCode: 'PROTOTYPE', dictName: '原理样机' },
  { dictType: 'DELIVERABLE_TYPE', dictCode: 'EQUIPMENT', dictName: '设备' },
  { dictType: 'DELIVERABLE_TYPE', dictCode: 'TECH_PACKAGE', dictName: '成套技术成果' },
  { dictType: 'TRANSFORM_WAY', dictCode: 'MODEL', dictName: '向型号转化' },
  { dictType: 'TRANSFORM_WAY', dictCode: 'MARKET', dictName: '向市场转化' },
  { dictType: 'TRANSFORM_FORM', dictCode: 'INSTALLED', dictName: '装机' },
  { dictType: 'TRANSFORM_FORM', dictCode: 'UNINSTALLED', dictName: '未装机' },
  { dictType: 'TRANSFORM_FORM', dictCode: 'TRANSFER', dictName: '转让' },
  { dictType: 'TRANSFORM_FORM', dictCode: 'LICENSE', dictName: '许可' },
  { dictType: 'TRANSFORM_FORM', dictCode: 'JOINT', dictName: '联合实施' },
  { dictType: 'TRANSFORM_FORM', dictCode: 'INVEST', dictName: '作价投资' },
  { dictType: 'TRANSFORM_FORM', dictCode: 'OTHER', dictName: '其他' },
  { dictType: 'PARTNER_TYPE', dictCode: 'LEAD', dictName: '牵头' },
  { dictType: 'PARTNER_TYPE', dictCode: 'PARTNER', dictName: '参研' },
  { dictType: 'PARTNER_TYPE', dictCode: 'OUTSOURCE', dictName: '科研外协' },
  { dictType: 'EVAL_TYPE', dictCode: 'MID', dictName: '中期评估' },
  { dictType: 'EVAL_TYPE', dictCode: 'QUARTER', dictName: '季度评估' },
  { dictType: 'EVAL_TYPE', dictCode: 'YEAR', dictName: '年度评估' },
  { dictType: 'EVAL_TYPE', dictCode: 'STAGE', dictName: '阶段性检查' },
  { dictType: 'EVAL_TYPE', dictCode: 'SUPERVISE', dictName: '现场督导' },
  { dictType: 'CHANGE_CATEGORY', dictCode: 'MILESTONE_DELAY', dictName: '里程碑延期' },
  { dictType: 'CHANGE_CATEGORY', dictCode: 'FUND', dictName: '经费调整' },
  { dictType: 'CHANGE_CATEGORY', dictCode: 'PERIOD', dictName: '周期变更' },
  { dictType: 'CHANGE_CATEGORY', dictCode: 'OUTSOURCE', dictName: '外协单位更换' },
  { dictType: 'CHANGE_CATEGORY', dictCode: 'PAYMENT', dictName: '付款节点调整' },
  { dictType: 'CHANGE_CATEGORY', dictCode: 'INDICATOR', dictName: '核心指标变动' },
  { dictType: 'CHANGE_CATEGORY', dictCode: 'BASIC', dictName: '基础信息纠错' },
  { dictType: 'CHANGE_CATEGORY', dictCode: 'LEVEL', dictName: '项目层级修正' },
  { dictType: 'ACCEPT_LEVEL', dictCode: 'UNIT', dictName: '单位级验收' },
  { dictType: 'ACCEPT_LEVEL', dictCode: 'COMPANY', dictName: '公司级验收' },
  { dictType: 'ACCEPT_LEVEL', dictCode: 'NATIONAL', dictName: '国家级验收' },
  { dictType: 'ACCEPT_LEVEL', dictCode: 'LOCAL', dictName: '属地主管部门验收' },
]

/* ============================ 项目渠道 ============================ */
export const channels: ProjChannel[] = [
  { id: 1, channelCode: 'MJKY', channelName: 'MJKY', levelCode: 'NATIONAL', channelDept: 'GXB', channelOffice: '装备二司', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '建议书申报→立项批复→任务书/可研报告申报→任务书/可研报告申报批复→中期评估→单位、公司两级验收评审→国家级验收', declareMaterial: '建议书,建议书意见', filingMaterial: '立项批复' },
  { id: 2, channelCode: '04ZXJX', channelName: '04专项接续', levelCode: 'NATIONAL', channelDept: 'GXB', channelOffice: '装备一司', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '建议书申报→立项批复→合同书签署→中期评估→单位、公司两级验收评审→国家级验收', declareMaterial: '建议书,建议书意见', filingMaterial: '立项批复' },
  { id: 3, channelCode: 'ZDYFJH', channelName: '重点研发计划', levelCode: 'NATIONAL', channelDept: 'GXB', channelOffice: '高新技术司', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '申请书提交→申请书评审→任务书签署→启动会→中期评估→单位、公司两级验收评审→综合绩效评价', declareMaterial: '申请书,申请书评审', filingMaterial: '立项批复' },
  { id: 4, channelCode: 'XX25', channelName: 'XX25专项', levelCode: 'NATIONAL', channelDept: '国资委', channelOffice: '科技创新局', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '任务清单报送→任务清单评估并下达→签署任务书→季度会/双月报/年度评估→国资委现场督导→单位、公司两级验收评审→验收评估', declareMaterial: '申报通知,任务清单,任务清单评估', filingMaterial: '立项批复' },
  { id: 5, channelCode: 'ZRJJ', channelName: '国家自然科学基金', levelCode: 'NATIONAL', channelDept: '科学技术部', channelOffice: '国自然', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '申请书提交→申请书评审→批准通知→年度实施报告→中期评估→单位、公司两级验收评审→国家级验收', declareMaterial: '申请书,申请书评审', filingMaterial: '批准通知' },
  { id: 6, channelCode: 'FGGGXJC', channelName: 'FGW GXJC项目', levelCode: 'NATIONAL', channelDept: 'FGW', channelOffice: '高技术司', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '建议书申报→立项批复→任务书签署→中期评估→单位、公司两级验收评审→国家级验收', declareMaterial: '建议书,建议书意见', filingMaterial: '立项批复' },
  { id: 7, channelCode: 'SHJBGS', channelName: '上海市科技攻关揭榜挂帅', levelCode: 'LOCAL', channelDept: '上海市科委', channelOffice: '空天海洋处', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '榜单梳理→榜单发布→榜单答疑→申请书评审并批复立项→合同签订→中期评审→单位验收评审→科委验收', declareMaterial: '榜单答疑', filingMaterial: '申请书评审' },
  { id: 8, channelCode: 'SHKJCX', channelName: '上海市科技创新行动计划', levelCode: 'LOCAL', channelDept: '上海市科委', channelOffice: '空天海洋处', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '建议书申报→建议书评审→项目立项→合同签订→阶段性检查→单位验收评审→综合绩效评价', declareMaterial: '建议书,建议书评审', filingMaterial: '立项通知' },
  { id: 9, channelCode: 'YYGD', channelName: '预研三年滚动计划', levelCode: 'COMPANY', channelDept: '科技部', channelOffice: '科研项目处', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '建议书申报→建议书评审→项目立项→任务书提交→任务书确认并签订合同→阶段性检查→单位级验收评审→公司级验收评审', declareMaterial: '建议书,建议书评审', filingMaterial: '立项通知' },
  { id: 10, channelCode: 'ZDZX', channelName: '重大科技创新专项', levelCode: 'COMPANY', channelDept: '科技部', channelOffice: '科研项目处', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '建议书申报→建议书评审→项目立项→任务书签署→阶段性检查→单位级验收评审→公司级验收评审', declareMaterial: '建议书,建议书评审', filingMaterial: '立项通知' },
  { id: 11, channelCode: 'XJQX', channelName: '新疆大飞机气象创新中心', levelCode: 'COMPANY', channelDept: '科技部', channelOffice: '科研项目处', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '申请书提交→申请书评审→技术委员会/主任委员会/理事会审议→项目立项→任务书提交→任务书确认和合同签订→阶段性检查→单位验收评审', declareMaterial: '申请书,申请书评审,委员会审议', filingMaterial: '立项通知' },
  { id: 12, channelCode: 'KJZ', channelName: '科技周', levelCode: 'COMPANY', channelDept: '科技部', channelOffice: '科技发展处', innerDept: '科技部', innerOffice: '科技发展处', flowNodes: '发布拟立项项目清单→各单位立项→实施→验收', declareMaterial: '合作需求,需求对接总结,技术发展战略委员会审议', filingMaterial: '拟立项通知,立项文件' },
  { id: 13, channelCode: 'DFJYJY', channelName: '大飞机研究院', levelCode: 'COMPANY', channelDept: '科技部', channelOffice: '科技发展处', innerDept: '科技部', innerOffice: '科技发展处', flowNodes: '项目建议书编制→项目建议书评审→形成拟立项清单→理事会审议→立项→项目实施→项目验收', declareMaterial: '项目申请书,学术委员会审议', filingMaterial: '立项通知' },
  { id: 14, channelCode: 'CLM', channelName: '大飞机先进材料创新联盟', levelCode: 'COMPANY', channelDept: '科技部', channelOffice: '技术基础处', innerDept: '科技部', innerOffice: '技术基础处', flowNodes: '项目申报→申请书评审→联盟专委会审议→联盟理事会审议→报批→发布立项通知→合同书签署→项目实施→承担单位验收评审', declareMaterial: '项目申请书', filingMaterial: '立项建议清单,联盟专委会审议意见,联盟理事会审议意见' },
  { id: 15, channelCode: 'BOKH', channelName: '“中国商飞-波音”可持续航空技术研究中心项目', levelCode: 'COMPANY', channelDept: '科技部', channelOffice: '科研项目处', innerDept: '科技部', innerOffice: '科研项目处', flowNodes: '项目波音指导委员会立项→项目合同签订→向公司报备→项目实施→项目承担单位验收→与总部签订拨款合同→拨款', declareMaterial: '波音指导委员会会议纪要', filingMaterial: '三方合同' },
]

/* ============================ 用户 / 成员（按现网成员管理台账 15 人） ============================ */
export const users: SysUser[] = [
  { id: 1, username: 'admin', realName: '系统管理员', employeeNo: '100001', orgId: 1, orgName: '中国商飞总部', deptName: '科研项目处', identity: '系统管理员', identityCode: 'admin', projectPost: '暂无项目角色', rankTitle: '工程师', dataScope: 'COMPANY', finishAuth: 1, formMaintScope: 'hq', declareResultAccess: 1, status: 1, roles: ['ADMIN'], email: '100001@comac.cc' },
  { id: 2, username: '100002', realName: '周明远', employeeNo: '100002', orgId: 1, orgName: '中国商飞总部', deptName: '科技管理部（总部办公室）', identity: '公司领导', identityCode: 'leader', projectPost: '暂无项目角色', rankTitle: '专家', dataScope: 'COMPANY', finishAuth: 1, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['MANAGEMENT'], email: '100002@comac.cc' },
  { id: 3, username: '100003', realName: '王建国', employeeNo: '100003', orgId: 1, orgName: '中国商飞总部', deptName: '科技管理部（总部办公室）', identity: '总部责任处室处长', identityCode: 'hqHead', projectPost: '总部处室处长', rankTitle: '研究员', dataScope: 'COMPANY', finishAuth: 1, formMaintScope: 'hq', declareResultAccess: 0, status: 1, roles: ['MANAGEMENT'], email: '100003@comac.cc' },
  { id: 4, username: '100004', realName: '何雨桐', employeeNo: '100004', orgId: 1, orgName: '中国商飞总部', deptName: '科研项目处', identity: '总部科研项目主管', identityCode: 'hqStaff', projectPost: '总部处室主管', rankTitle: '高级工程师', dataScope: 'COMPANY', finishAuth: 0, formMaintScope: 'hq', declareResultAccess: 0, status: 1, roles: ['MANAGEMENT'], email: '100004@comac.cc' },
  { id: 5, username: '100005', realName: '方致远', employeeNo: '100005', orgId: 10, orgName: '上飞院', deptName: '科技管理部', identity: '单位科研管理部门负责人', identityCode: 'unitHead', projectPost: '单位科技部长', rankTitle: '高级工程师', dataScope: 'UNIT', finishAuth: 1, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['MANAGEMENT'], email: '100005@comac.cc' },
  { id: 6, username: '100006', realName: '田念慈', employeeNo: '100006', orgId: 10, orgName: '上飞院', deptName: '科技管理部', identity: '单位项目主管', identityCode: 'unitStaff', projectPost: '单位科技主管', rankTitle: '工程师', dataScope: 'UNIT', finishAuth: 0, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['MANAGEMENT'], email: '100006@comac.cc' },
  { id: 16, username: '100016', realName: '韩承泽', employeeNo: '100016', orgId: 10, orgName: '上飞院', deptName: '总体气动部', identity: '项目承担部门负责人', identityCode: 'deptHead', projectPost: '项目承担部门负责人', rankTitle: '高级工程师', dataScope: 'DEPT', finishAuth: 1, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['MANAGEMENT'], email: '100016@comac.cc' },
  { id: 7, username: '100007', realName: '陈铁军', employeeNo: '100007', orgId: 1, orgName: '中国商飞总部', deptName: '科技管理部（总部办公室）', identity: '一级总师（公司级）', identityCode: 'chief1', projectPost: '一级总师', rankTitle: '研究员', dataScope: 'SELF', finishAuth: 0, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['CHIEF_ENGINEER'], email: '100007@comac.cc' },
  { id: 8, username: '100008', realName: '蔡文渊', employeeNo: '100008', orgId: 10, orgName: '上飞院', deptName: '科技管理部', identity: '二级总师（单位级）', identityCode: 'chief2', projectPost: '二级总师', rankTitle: '研究员', dataScope: 'SELF', finishAuth: 0, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['CHIEF_ENGINEER'], email: '100008@comac.cc' },
  { id: 9, username: '100009', realName: '赵美玲', employeeNo: '100009', orgId: 1, orgName: '中国商飞总部', deptName: '财务部', identity: '总部财务主管', identityCode: 'finHq', projectPost: '总部财务主管', rankTitle: '财务', dataScope: 'COMPANY', finishAuth: 0, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['FINANCE'], email: '100009@comac.cc' },
  { id: 10, username: '100010', realName: '毕仲文', employeeNo: '100010', orgId: 10, orgName: '上飞院', deptName: '财务部', identity: '单位财务部长', identityCode: 'finHead', projectPost: '单位财务部长', rankTitle: '财务', dataScope: 'UNIT', finishAuth: 0, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['FINANCE'], email: '100010@comac.cc' },
  { id: 11, username: '100011', realName: '龚雪君', employeeNo: '100011', orgId: 10, orgName: '上飞院', deptName: '财务部', identity: '单位财务主管', identityCode: 'finStaff', projectPost: '单位财务主管', rankTitle: '财务', dataScope: 'UNIT', finishAuth: 0, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['FINANCE'], email: '100011@comac.cc' },
  { id: 12, username: '100012', realName: '林晚晴', employeeNo: '100012', orgId: 10, orgName: '上飞院', deptName: '科研项目处', identity: '项目负责人', identityCode: 'owner', projectPost: '项目负责人', rankTitle: '工程师', dataScope: 'SELF', finishAuth: 0, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['PROJECT_TEAM'], email: '100012@comac.cc' },
  { id: 13, username: '100013', realName: '顾思远', employeeNo: '100013', orgId: 10, orgName: '上飞院', deptName: '科研项目处', identity: '项目联系人', identityCode: 'contactLogin', projectPost: '项目联系人', rankTitle: '工程师', dataScope: 'SELF', finishAuth: 0, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['PROJECT_TEAM'], email: '100013@comac.cc' },
  { id: 14, username: '100014', realName: '沈知行', employeeNo: '100014', orgId: 10, orgName: '上飞院', deptName: '科研项目处', identity: '技术负责人', identityCode: 'techLead', projectPost: '技术负责人', rankTitle: '高级工程师', dataScope: 'SELF', finishAuth: 0, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['PROJECT_TEAM'], email: '100014@comac.cc' },
  { id: 15, username: '100015', realName: '陆嘉言', employeeNo: '100015', orgId: 10, orgName: '上飞院', deptName: '科研项目处', identity: '项目主管', identityCode: 'projectPm', projectPost: '项目主管', rankTitle: '工程师', dataScope: 'SELF', finishAuth: 0, formMaintScope: '', declareResultAccess: 0, status: 1, roles: ['PROJECT_TEAM'], email: '100015@comac.cc' },
]

/** 项目岗位办理权限矩阵（可被管理员修改） */
export let postPermMatrix = cloneDefaultMatrix()

export function resetPostPermMatrix() {
  postPermMatrix = cloneDefaultMatrix()
  return postPermMatrix
}

/* ============================ 项目一本账 ============================ */
interface ProjectSeed {
  name: string
  channelId: number
  orgId: number
  orgName: string
  totalFund: number
  status: string
  startOffset: number
  endOffset: number
  partners: string[]
  ms: [string, number][] // 里程碑名, 距今天数
  dvs: [string, string, number][] // 交付物名, 类型, 距今天数
  goal: string
}

const seeds: ProjectSeed[] = [
  { name: '大型客机复合材料主承力结构关键技术研究', channelId: 1, orgId: 10, orgName: '上海飞机设计研究院', totalFund: 12800, status: 'IMPLEMENTING', startOffset: -420, endOffset: 300, partners: ['南京航空航天大学', '中国航空研究院'], goal: '突破复合材料主承力结构设计与验证关键技术，形成自主可控的设计-制造-验证一体化能力，支撑宽体客机复合材料用量提升至 25%。', ms: [['方案设计评审', -12], ['详细设计冻结', 45], ['典型件试验验证', 160], ['全尺寸件验证', 280]], dvs: [['复合材料主承力结构设计规范', 'STANDARD', 120], ['典型件试验验证报告', 'TECH_PACKAGE', 40], ['发明专利：一种复合材料主承力结构', 'PATENT', -8], ['原理样机 1 套', 'PROTOTYPE', 210]],  },
  { name: '民用飞机机载系统综合验证技术研究', channelId: 2, orgId: 10, orgName: '上海飞机设计研究院', totalFund: 9600, status: 'IMPLEMENTING', startOffset: -300, endOffset: 420, partners: ['北京航空航天大学'], goal: '构建机载系统综合验证平台，实现航电、飞控、液压等多系统交联验证能力。', ms: [['验证平台方案评审', -30], ['平台一期建设', 25], ['系统交联试验', 180]], dvs: [['机载系统综合验证平台', 'EQUIPMENT', 60], ['综合验证技术规范', 'STANDARD', 150], ['软件著作权 2 项', 'SOFTWARE', 240]] },
  { name: '航空发动机短舱气动噪声抑制技术研究', channelId: 3, orgId: 30, orgName: '北京民用飞机技术研究中心', totalFund: 7400, status: 'IMPLEMENTING', startOffset: -200, endOffset: 380, partners: ['西北工业大学'], goal: '研究短舱降噪构型，实现起飞阶段噪声降低 3 EPNdB。', ms: [['噪声源机理研究', 60], ['降噪构型设计', 200], ['风洞试验验证', 340]], dvs: [['降噪构型设计方法', 'PAPER', 180], ['风洞试验数据集', 'TECH_PACKAGE', 330], ['发明专利 1 项', 'PATENT', 260]] },
  { name: '民机健康管理系统（PHM）技术研究', channelId: 4, orgId: 10, orgName: '上海飞机设计研究院', totalFund: 15200, status: 'IMPLEMENTING', startOffset: -500, endOffset: 120, partners: ['中国航空研究院', '南京航空航天大学'], goal: '建立民机 PHM 体系架构，实现关键系统故障预测准确率 ≥ 85%。', ms: [['PHM 架构设计评审', -60], ['故障诊断算法开发', -5], ['地面验证试验', 50], ['机上试飞验证', 110]], dvs: [['PHM 系统架构规范', 'STANDARD', 30], ['故障诊断算法库', 'SOFTWARE', 55], ['验证试验报告', 'TECH_PACKAGE', 100]] },
  { name: '飞机结冰适航符合性验证技术研究', channelId: 5, orgId: 30, orgName: '北京民用飞机技术研究中心', totalFund: 3200, status: 'ACCEPTING', startOffset: -600, endOffset: -20, partners: [], goal: '建立结冰条件下适航符合性验证方法体系。', ms: [['冰形预测方法研究', -400], ['结冰风洞试验', -160], ['符合性验证报告', -30]], dvs: [['结冰适航符合性验证方法', 'STANDARD', -45], ['学术论文 3 篇', 'PAPER', -50]] },
  { name: '增材制造钛合金结构件疲劳性能研究', channelId: 6, orgId: 20, orgName: '上海飞机制造有限公司', totalFund: 5800, status: 'IMPLEMENTING', startOffset: -260, endOffset: 260, partners: ['西北工业大学'], goal: '建立增材制造钛合金结构件疲劳性能数据库与评定方法。', ms: [['工艺参数优化', 15], ['疲劳试样制备', 90], ['疲劳性能评定', 220]], dvs: [['疲劳性能数据库', 'TECH_PACKAGE', 200], ['增材制造工艺规范', 'STANDARD', 120]] },
  { name: '民机智能制造装配线数字孪生技术研究', channelId: 7, orgId: 20, orgName: '上海飞机制造有限公司', totalFund: 4300, status: 'IMPLEMENTING', startOffset: -180, endOffset: 300, partners: ['上海交通大学'], goal: '构建装配线数字孪生体，实现装配节拍仿真与瓶颈识别。', ms: [['孪生模型构建', 20], ['仿真验证', 140], ['产线应用示范', 260]], dvs: [['数字孪生软件平台', 'SOFTWARE', 200], ['应用示范报告', 'TECH_PACKAGE', 280]] },
  { name: '客舱内饰阻燃材料关键技术研究', channelId: 8, orgId: 20, orgName: '上海飞机制造有限公司', totalFund: 2600, status: 'IMPLEMENTING', startOffset: -150, endOffset: 240, partners: ['东华大学'], goal: '研发满足 CCAR25 阻燃要求的国产客舱内饰材料。', ms: [['材料配方设计', 8], ['阻燃性能试验', 100], ['适航符合性验证', 220]], dvs: [['阻燃内饰材料', 'EQUIPMENT', 180], ['材料性能试验报告', 'TECH_PACKAGE', 90]] },
  { name: '预研三年滚动计划——民机机翼气动优化设计', channelId: 9, orgId: 10, orgName: '上海飞机设计研究院', totalFund: 6200, status: 'IMPLEMENTING', startOffset: -320, endOffset: 340, partners: [], goal: '开展机翼气动外形多学科优化设计，实现巡航效率提升 2%。', ms: [['基准构型标定', -20], ['气动优化设计', 80], ['风洞验证', 260]], dvs: [['机翼气动优化设计方法', 'PAPER', 150], ['优化构型数据集', 'TECH_PACKAGE', 280]] },
  { name: '重大科技创新专项——民机总装脉动生产线关键技术', channelId: 10, orgId: 20, orgName: '上海飞机制造有限公司', totalFund: 18900, status: 'IMPLEMENTING', startOffset: -400, endOffset: 400, partners: ['同济大学'], goal: '构建脉动式总装生产线，实现总装周期缩短 20%。', ms: [['产线布局设计', -90], ['节拍仿真优化', 60], ['示范线建设', 250], ['节拍达标验证', 380]], dvs: [['脉动生产线设计方案', 'TECH_PACKAGE', 120], ['节拍控制软件', 'SOFTWARE', 300]] },
  { name: '新疆大飞机气象创新中心——高原机场运行性能分析', channelId: 11, orgId: 30, orgName: '北京民用飞机技术研究中心', totalFund: 1800, status: 'DECLARING', startOffset: -30, endOffset: 600, partners: ['新疆气象局'], goal: '建立高原机场运行气象条件数据库与性能分析模型。', ms: [['气象数据采集', 200], ['性能分析建模', 400]], dvs: [['高原机场气象数据库', 'TECH_PACKAGE', 520]] },
  { name: '科技周——民机雷电防护设计技术科普与验证', channelId: 12, orgId: 20, orgName: '上海飞机制造有限公司', totalFund: 380, status: 'IMPLEMENTING', startOffset: -60, endOffset: 180, partners: [], goal: '开展雷电防护设计技术验证与科普推广。', ms: [['科普方案编制', 40], ['验证试验', 150]], dvs: [['雷电防护验证报告', 'TECH_PACKAGE', 170]] },
  { name: '大飞机研究院——基于 MBSE 的民机需求工程方法研究', channelId: 13, orgId: 10, orgName: '上海飞机设计研究院', totalFund: 2100, status: 'IMPLEMENTING', startOffset: -120, endOffset: 300, partners: ['北京航空航天大学'], goal: '构建基于模型的系统工程需求工程方法论。', ms: [['方法论框架设计', 30], ['工具链集成', 160], ['型号试点应用', 280]], dvs: [['MBSE 需求工程方法论', 'STANDARD', 200], ['试点应用报告', 'TECH_PACKAGE', 290]] },
  { name: '大飞机先进材料创新联盟——航空铝合金先进焊接工艺研究', channelId: 14, orgId: 20, orgName: '上海飞机制造有限公司', totalFund: 3400, status: 'IMPLEMENTING', startOffset: -220, endOffset: 200, partners: ['哈尔滨工业大学'], goal: '突破铝合金搅拌摩擦焊工艺，实现接头强度系数 ≥ 0.85。', ms: [['焊接工艺试验', -8], ['接头性能评定', 90], ['工艺规范编制', 170]], dvs: [['搅拌摩擦焊工艺规范', 'STANDARD', 150], ['发明专利 2 项', 'PATENT', 60]] },
  { name: '“中国商飞-波音”可持续航空燃料（SAF）应用评估', channelId: 15, orgId: 30, orgName: '北京民用飞机技术研究中心', totalFund: 4600, status: 'COMPANY_ACCEPTED', startOffset: -700, endOffset: -120, partners: ['波音公司'], goal: '评估 SAF 在国产民机上的适用性与适航符合性路径。', ms: [['SAF 样品分析', -600], ['台架试验', -400], ['评估报告编制', -150]], dvs: [['SAF 应用评估报告', 'TECH_PACKAGE', -140], ['学术论文 2 篇', 'PAPER', -160]] },
  { name: '民机结构损伤容限评定技术研究', channelId: 1, orgId: 10, orgName: '上海飞机设计研究院', totalFund: 8600, status: 'FINISHED', startOffset: -900, endOffset: -300, partners: ['南京航空航天大学'], goal: '建立金属与复合材料结构损伤容限评定技术体系。', ms: [['评定方法研究', -800], ['试验验证', -600], ['技术体系集成', -320]], dvs: [['损伤容限评定技术体系', 'TECH_PACKAGE', -310], ['行业标准 1 项', 'STANDARD', -330]] },
  { name: '某型号试验件预研课题（已终止）', channelId: 9, orgId: 20, orgName: '上海飞机制造有限公司', totalFund: 520, status: 'TERMINATED', startOffset: -400, endOffset: -100, partners: [], goal: '课题因技术路线调整终止，保留过程资料备查。', ms: [['方案编制', -350]], dvs: [['终止说明', 'TECH_PACKAGE', -120]] },
  // —— 补充样本：覆盖延期、机关验收、系统/飞行/运行支持等专业口径 ——
  { name: '民机飞控余度架构可靠性与重构技术研究', channelId: 2, orgId: 10, orgName: '上海飞机设计研究院', totalFund: 7800, status: 'DELAYED', startOffset: -380, endOffset: 90, partners: ['北京航空航天大学', '某外协测控公司'], goal: '突破飞控余度架构故障重构关键技术，支撑适航符合性验证。', ms: [['余度架构方案评审', -90], ['重构算法验证', -15], ['半物理仿真试验', 70]], dvs: [['飞控余度重构技术规范', 'STANDARD', 50], ['半物理仿真验证报告', 'TECH_PACKAGE', 80], ['发明专利 1 项', 'PATENT', 40]] },
  { name: '民机液压能源系统高效转换与余热利用技术', channelId: 3, orgId: 10, orgName: '上海飞机设计研究院', totalFund: 5100, status: 'IMPLEMENTING', startOffset: -210, endOffset: 350, partners: ['西北工业大学'], goal: '提升液压能源转换效率并探索余热回收路径，降低机上能耗。', ms: [['系统方案设计', -10], ['台架试验', 120], ['机上适配评估', 300]], dvs: [['液压能源高效转换方案', 'TECH_PACKAGE', 140], ['学术论文 2 篇', 'PAPER', 200]] },
  { name: '民机试飞测试数据融合与智能判读技术研究', channelId: 5, orgId: 30, orgName: '北京民用飞机技术研究中心', totalFund: 4200, status: 'IMPLEMENTING', startOffset: -160, endOffset: 280, partners: ['南京航空航天大学'], goal: '构建多源试飞测试数据融合与智能判读能力，缩短试飞数据分析周期。', ms: [['数据融合框架设计', 20], ['智能判读算法验证', 150], ['试飞科目试点应用', 250]], dvs: [['数据融合软件', 'SOFTWARE', 180], ['试点应用报告', 'TECH_PACKAGE', 260]] },
  { name: '航线维修工程能力建设与技术出版物协同研究', channelId: 9, orgId: 20, orgName: '上海飞机制造有限公司', totalFund: 2900, status: 'IMPLEMENTING', startOffset: -100, endOffset: 320, partners: ['上海飞机客户服务有限公司'], goal: '打通维修工程与技术出版物协同链路，提升航线支援响应效率。', ms: [['维修工程流程梳理', 30], ['出版物协同试点', 160], ['能力评估与推广', 290]], dvs: [['维修工程协同规范', 'STANDARD', 200], ['试点总结报告', 'TECH_PACKAGE', 300]] },
  { name: '民机环控系统适航符合性验证方法研究', channelId: 1, orgId: 10, orgName: '上海飞机设计研究院', totalFund: 6800, status: 'GOV_ACCEPTED', startOffset: -820, endOffset: -60, partners: ['中国航空研究院'], goal: '形成环控系统适航符合性验证方法体系并通过机关验收。', ms: [['验证方法研究', -700], ['试验验证', -400], ['机关验收材料编制', -80]], dvs: [['环控适航验证方法', 'STANDARD', -90], ['成套验证数据包', 'TECH_PACKAGE', -70]] },
]

export const projects: ProjInfo[] = []
export const milestones: ProjMilestone[] = []
export const plans: ProjPlan[] = []
export const budgets: FundBudget[] = []
export const payments: FundPayment[] = []
export const evaluations: ProjEvaluation[] = []
export const changes: ProjChange[] = []
export const declarations: ProjDeclaration[] = []
export const materials: ProjMaterial[] = []
export const acceptances: ProjAcceptance[] = []
export const acceptanceItems: ProjAcceptanceItem[] = []
export const deliverables: ProjDeliverable[] = []
export const partnerEvals: PartnerEval[] = []
export const transforms: AchvTransform[] = []
export const postEvals: ProjPostEval[] = []
export const warnings: SysWarning[] = []
export const auditLogs: SysAuditLog[] = []
export const hqBudgets: HqFundBudget[] = []
export const quotas: HqFundQuota[] = []
export const transfers: HqFundTransfer[] = []
export const blacklist: any[] = []

/** 项目团队岗位与现网花名册对齐（工号 100003–100015） */
const MEMBER_ROSTER: [string, string, string, string, string][] = [
  ['TECH', 'PROJECT_LEADER', '项目负责人', '林晚晴', '100012'],
  ['TECH', 'TECH_LEADER', '技术负责人', '沈知行', '100014'],
  ['TECH', 'PROJECT_SUPERVISOR', '项目主管', '陆嘉言', '100015'],
  ['TECH', 'PROJECT_CONTACT', '项目联系人', '顾思远', '100013'],
  ['EXPERT', 'L1_CHIEF', '一级总师', '陈铁军', '100007'],
  ['EXPERT', 'L2_CHIEF', '二级总师', '蔡文渊', '100008'],
  ['MGMT', 'HQ_DIRECTOR', '总部处室处长', '王建国', '100003'],
  ['MGMT', 'HQ_SUPERVISOR', '总部处室主管', '何雨桐', '100004'],
  ['MGMT', 'UNIT_MINISTER', '单位科技部长', '方致远', '100005'],
  ['MGMT', 'UNIT_SUPERVISOR', '单位科技主管', '田念慈', '100006'],
  ['MGMT', 'DEPT_HEAD', '项目承担部门负责人', '韩承泽', '100016'],
  ['FIN', 'HQ_FINANCE', '总部财务主管', '赵美玲', '100009'],
  ['FIN', 'UNIT_FIN_MINISTER', '单位财务部长', '毕仲文', '100010'],
  ['FIN', 'UNIT_FIN_SUPERVISOR', '单位财务主管', '龚雪君', '100011'],
]
const NAMES = MEMBER_ROSTER.map((m) => m[3])

seeds.forEach((s, idx) => {
  const ch = channels.find((c) => c.id === s.channelId)!
  const pid = idx + 1
  const yStart = dayjs().add(s.startOffset, 'day')
  const yEnd = dayjs().add(s.endOffset, 'day')

  // 里程碑
  const msList: ProjMilestone[] = s.ms.map(([name, off], i) => {
    const planDate = d(off)
    const finished = off < -20
    const status: ProjMilestone['status'] = finished ? 'DONE' : off < 0 ? 'OVERDUE' : 'DOING'
    return {
      id: nid(),
      projectId: pid,
      year: dayjs(planDate).year(),
      name,
      planDate,
      actualDate: finished ? d(off + 5) : undefined,
      budget: Math.round(s.totalFund / (s.ms.length + 1) / 10) * 10,
      status,
      colorStatus: calcColor(planDate, finished),
      evidence: finished ? 1 : 0,
      lagReason: status === 'OVERDUE' ? '受关键试验设备排期影响，节点顺延，已启动延期变更流程' : undefined,
    }
  })
  milestones.push(...msList)

  // 计划（CMOS 同步）
  for (let i = 0; i < 3; i++) {
    const off = [12, -6, 75, 200][i] ?? 100
    const done = off < -20
    plans.push({
      id: nid(),
      projectId: pid,
      source: 'CMOS',
      title: `【${ch.channelName}】${['年度计划编制', '季度进展报送', '阶段成果提交', '年度总结归档'][i]}`,
      planType: done ? 'DONE' : 'TODO',
      dueDate: d(off),
      finishDate: done ? d(off + 3) : undefined,
      owner: NAMES[(idx + i) % NAMES.length],
      status: done ? 'DONE' : off < 0 ? 'OVERDUE' : 'DOING',
      colorStatus: calcColor(d(off), done),
      applyStatus: done ? 'APPROVED' : 'NONE',
    })
  }

  // 交付物
  const dvs: ProjDeliverable[] = s.dvs.map(([name, type, off]) => {
    const delivered = off < -20
    return {
      id: nid(),
      projectId: pid,
      name,
      deliverType: type,
      dueDate: d(off),
      deliverDate: delivered ? d(off + 10) : undefined,
      status: delivered ? 'DELIVERED' : off < 0 ? 'OVERDUE' : 'PENDING',
      colorStatus: calcColor(d(off), delivered),
      ownerOrgs: '公司,各单位',
    }
  })
  deliverables.push(...dvs)

  // 经费
  msList.forEach((m) => {
    const b: FundBudget = {
      id: nid(),
      projectId: pid,
      year: dayjs().year(),
      milestoneId: m.id,
      milestoneName: m.name,
      amount: m.budget,
      status: m.status === 'DONE' ? 'APPROVED' : 'PENDING',
    }
    budgets.push(b)
    if (m.status === 'DONE') {
      payments.push({
        id: nid(),
        projectId: pid,
        budgetId: b.id,
        flowType: 'PAY',
        amount: Math.round((b.amount || 0) * 0.8),
        voucherNo: `PZ${dayjs().year()}${String(nid()).slice(-6)}`,
        occurDate: m.actualDate,
        writeoffStatus: 'WRITTEN',
        operator: '龚雪君',
      })
    }
  })

  // 评估检查
  evaluations.push({
    id: nid(),
    projectId: pid,
    evalType: ['MID', 'STAGE', 'YEAR', 'QUARTER', 'SUPERVISE'][idx % 5],
    name: ['中期评估', '阶段性检查', '年度评估', '季度会', '国资委现场督导'][idx % 5],
    dueDate: d(idx % 3 === 0 ? -45 : 90),
    result: idx % 4 === 0 ? 'FAIL' : 'PASS',
    status: idx % 3 === 0 ? 'DONE' : 'PENDING',
  })

  // 协作单位评价（含 1 条不合格样本，用于黑名单演示）
  s.partners.forEach((p, i) => {
    const isFailSample = p.includes('某外协')
    const submitted = isFailSample || idx % 2 === 0
    const dims = !submitted
      ? [0, 0, 0, 0, 0]
      : isFailSample
        ? [8, 7, 6, 9, 8] // 38 分 → FAIL
        : [18, 18, 17, 19, 18]
    const score = dims.reduce((a, b) => a + b, 0)
    partnerEvals.push({
      id: nid(),
      projectId: pid,
      partnerName: p,
      partnerType: i === 0 ? 'PARTNER' : 'OUTSOURCE',
      techScore: dims[0],
      qualityScore: dims[1],
      progressScore: dims[2],
      serviceScore: dims[3],
      complianceScore: dims[4],
      score,
      grade: !submitted ? undefined : score >= 90 ? 'EXCELLENT' : score >= 80 ? 'GOOD' : score >= 60 ? 'PASS' : 'FAIL',
      evalDate: submitted ? d(-10) : undefined,
      evaluator: submitted ? '林晚晴' : undefined,
      dueDate: d(i === 0 ? 25 : 60),
      status: submitted ? 'DONE' : 'PENDING',
    })
  })

  // 年度目标与计划
  const annualPlans: ProjAnnualPlan[] = [0, 1].map((i) => ({
    id: nid(),
    projectId: pid,
    year: dayjs().year() + i - 1,
    annualGoal: `${dayjs().year() + i - 1} 年度完成${s.ms[i]?.[0] ?? '关键技术攻关'}`,
    planContent: `${s.ms[i]?.[0] ?? '关键技术攻关'}及配套验证`,
    dueDate: d(i === 0 ? 120 : 480),
    finishStatus: i === 0 ? 'DOING' : 'DOING',
    colorStatus: calcColor(d(i === 0 ? 120 : 480), false),
  }))

  // 团队（与花名册一致，便于按工号联调演示）
  const teamMembers = MEMBER_ROSTER.map(([g, code, roleName, userName, employeeNo]) => ({
    id: nid(),
    projectId: pid,
    groupCode: g as any,
    roleCode: code,
    roleName,
    userName,
    employeeNo,
  }))

  let warnColor: ColorStatus = mergeColor([
    ...msList.map((m) => m.colorStatus),
    ...dvs.map((x) => x.colorStatus),
  ])
  // 已终止项目综合预警按口径显示绿色，状态标签仍为「已终止」
  if (s.status === 'TERMINATED' || s.status === 'FINISHED') warnColor = 'GREEN'

  const major1List = majorConfig.major1 as string[]
  const major1 = major1List[idx % major1List.length]
  const major2List = ((majorConfig.major2ByMajor1 as Record<string, string[]>)[major1] || [])
  const major2 = major2List[idx % Math.max(major2List.length, 1)] || major2List[0]
  const nationalFund = Math.round(s.totalFund * (0.55 + (idx % 4) * 0.05) * 100) / 100
  const selfFund = Math.round((s.totalFund - nationalFund) * 100) / 100
  const projectTypes = ['预先研究', '应用研究', '基础研究', '试验验证']
  const bureauOffice = ch.channelOffice || ch.innerOffice || '—'
  const manageOrgName = ch.levelCode === 'COMPANY' ? '中国商飞总部' : (ch.innerDept ? `中国商飞${ch.innerDept}` : '中国商飞总部')
  const shortLead = (s.orgName || '')
    .replace('上海飞机设计研究院', '上飞院')
    .replace('上海飞机制造有限公司', '上飞公司')
    .replace('北京民用飞机技术研究中心', '北研中心')
    .replace('中国商飞总部', '总部')

  projects.push({
    id: pid,
    projectNo: `XM${dayjs().format('YYYY')}${String(1000 + pid)}`,
    name: s.name,
    goal: s.goal,
    startDate: yStart.format('YYYY-MM-DD'),
    endDate: yEnd.format('YYYY-MM-DD'),
    levelCode: ch.levelCode,
    filingDept: ch.channelDept,
    channelId: ch.id,
    channelName: ch.channelName,
    leadOrgId: s.orgId,
    leadOrgName: s.orgName,
    mainWork: `${s.goal.slice(0, 20)}等研究工作`,
    status: s.status,
    transformStatus: idx % 4 === 0 ? 'APPLIED' : idx % 4 === 1 ? 'CONTINUE' : 'RESERVE',
    warnColor,
    totalFund: s.totalFund,
    nationalFund,
    selfFund,
    expenseTotal: Math.round(s.totalFund * (0.3 + (idx % 5) * 0.12)),
    yearBudget: Math.round(s.totalFund * 0.25),
    yearExpense: Math.round(s.totalFund * 0.18),
    outsourceAmount: Math.round(s.totalFund * 0.15),
    orgId: s.orgId,
    orgName: s.orgName,
    manageOrgName,
    bureauOffice,
    projectType: projectTypes[idx % projectTypes.length],
    major1,
    major2,
    dataSource: 'PLATFORM',
    ownerName: NAMES[idx % NAMES.length],
    acceptStatus: s.status === 'FINISHED' || s.status === 'COMPANY_ACCEPTED' || s.status === 'GOV_ACCEPTED' ? '已验收' : '未验收',
    createByName: NAMES[idx % NAMES.length],
    createdAt: yStart.format('YYYY-MM-DD HH:mm:ss'),
    participants: s.partners.map((p, i) => ({
      id: nid(),
      projectId: pid,
      orgName: p,
      workContent: `承担${s.ms[i]?.at(0) ?? '相关'}分课题研究`,
      sort: i + 1,
    })),
    teamMembers,
    annualPlans,
    // 列表摘要用简称（不改变 leadOrgName 全称）
    ...( { leadOrgShort: shortLead } as any ),
  })

  // 验收
  acceptances.push({
    id: nid(),
    projectId: pid,
    acceptLevel: ch.levelCode === 'NATIONAL' ? 'NATIONAL' : ch.levelCode === 'LOCAL' ? 'LOCAL' : 'COMPANY',
    status: ['ACCEPTING', 'NOT_STARTED', 'DONE'][idx % 3],
    expertReview: ch.levelCode === 'NATIONAL' ? 1 : 0,
    partnerDueDate: d(idx % 3 === 2 ? -10 : 30),
  })

  // 后评价
  if (idx % 3 === 2) {
    postEvals.push({
      id: nid(),
      projectId: pid,
      projectName: s.name,
      dueDate: d(idx === 2 ? 20 : 400),
      score: 85 + (idx % 8),
      status: 'PENDING',
      colorStatus: calcColor(d(idx === 2 ? 20 : 400), false),
    })
  }
})

/* ------------------------- 总部经费 ------------------------- */
;[2024, 2025, dayjs().year()].forEach((y, i) => {
  const total = [86000, 94000, 102000][i]
  hqBudgets.push({
    id: i + 1,
    year: y,
    totalAmount: total,
    status: y === dayjs().year() ? 'DRAFT' : 'LOCKED',
    approveStatus: 'APPROVED',
  })
  const orgNames = ['上海飞机设计研究院', '上海飞机制造有限公司', '北京民用飞机技术研究中心', '上海飞机客户服务有限公司']
  orgNames.forEach((n, j) => {
    const q = Math.round(total * [0.32, 0.28, 0.22, 0.18][j])
    quotas.push({
      id: i * 10 + j + 1,
      budgetId: i + 1,
      orgId: [10, 20, 30, 40][j],
      orgName: n,
      quotaAmount: q,
      usedAmount: Math.round(q * (0.4 + 0.13 * ((i + j) % 4))),
    })
  })
})
for (let i = 0; i < 8; i++) {
  const q = quotas[i % quotas.length]
  transfers.push({
    id: i + 1,
    budgetId: q.budgetId,
    quotaId: q.id,
    orgId: q.orgId,
    orgName: q.orgName,
    amount: Math.round((q.quotaAmount || 0) / (3 + (i % 3))),
    applyNo: `BF${dayjs().year()}${String(2000 + i)}`,
    reason: `${dayjs().year()} 年第 ${(i % 4) + 1} 批科研经费拨付`,
    status: ['PAID', 'HQ_AUDIT', 'UNIT_AUDIT', 'PENDING'][i % 4],
    applyAt: d(-30 * (i + 1)),
  })
}

/* ------------------------- 成果转化 ------------------------- */
const DONE_DVS = deliverables.filter((x) => x.status === 'DELIVERED')
for (let i = 0; i < 6; i++) {
  const p = projects[i * 2]
  const achvNo = `CG${dayjs().format('YYYY')}${String(3000 + i)}`
  const bind = DONE_DVS.filter((x) => x.projectId === p?.id).slice(0, 2)
  bind.forEach((b) => (b.achievementNo = achvNo))
  transforms.push({
    id: i + 1,
    achievementNo: achvNo,
    name: `${p?.name.slice(0, 12)}成果包`,
    projectId: p?.id,
    projectNo: p?.projectNo,
    intro: '面向型号应用的关键技术成果，具备直接工程化应用条件。',
    transformWay: i % 2 === 0 ? 'MODEL' : 'MARKET',
    transformForm: i % 2 === 0 ? (i % 4 === 0 ? 'INSTALLED' : 'UNINSTALLED') : ['TRANSFER', 'LICENSE', 'JOINT'][i % 3],
    planDate: d(i % 3 === 0 ? -15 : 90 + i * 20),
    actualDate: i % 3 === 0 ? d(-5) : undefined,
    status: ['DONE', 'SIGNED', 'NEGOTIATING', 'NOT_STARTED'][i % 4],
    colorStatus: calcColor(d(i % 3 === 0 ? -15 : 90 + i * 20), i % 3 === 0),
    dutyOrg: p?.leadOrgName,
    itemCount: bind.length,
    deliverables: bind,
  })
}

/* ------------------------- 立项申报（5 条演示样本） ------------------------- */
const DEMO_POSTS = {
  contact: '顾思远（100013）',
  leader: '林晚晴（100012）',
  techLeader: '沈知行（100014）',
  supervisor: '陆嘉言（100015）',
  chief1: '陈铁军（100007）',
  chief2: '蔡文渊（100008）',
  hqDirector: '王建国（100003）',
  hqSupervisor: '何雨桐（100004）',
  unitTechDirector: '方致远（100005）',
  unitTechSupervisor: '田念慈（100006）',
  deptHead: '韩承泽（100016）',
  hqFinance: '赵美玲（100009）',
  unitFinanceDirector: '毕仲文（100010）',
  unitFinanceSupervisor: '龚雪君（100011）',
}

const DECL_SEEDS: Array<{
  channelId: number
  name: string
  major1: string
  major2: string
  leadOrgName: string
  orgId: number
  orgName: string
  applyFund: number
  status: string
  flowNode?: string
  daysAgo: number
}> = [
  {
    channelId: 1,
    name: 'MJKY — 民机复合材料壁板自动铺丝与固化工艺研究',
    major1: '50-复合材料',
    major2: '5002-复合材料与工艺',
    leadOrgName: '上飞公司',
    orgId: 20,
    orgName: '上海飞机制造有限公司',
    applyFund: 1860,
    status: 'APPROVING',
    flowNode: '二级总师',
    daysAgo: 18,
  },
  {
    channelId: 2,
    name: '04专项接续 — 飞控余度健康监控与重构算法研究',
    major1: '30-系统',
    major2: '3002-飞控',
    leadOrgName: '上飞院',
    orgId: 10,
    orgName: '上海飞机设计研究院',
    applyFund: 1520,
    status: 'SUBMITTED',
    flowNode: '项目承担部门负责人',
    daysAgo: 12,
  },
  {
    channelId: 9,
    name: '预研三年滚动计划 — 民机机翼气动多学科优化设计',
    major1: '10-总体气动',
    major2: '1001-总体与气动',
    leadOrgName: '上飞院',
    orgId: 10,
    orgName: '上海飞机设计研究院',
    applyFund: 980,
    status: 'DRAFT',
    daysAgo: 5,
  },
  {
    channelId: 10,
    name: '重大科技创新专项 — 增材制造钛合金装机件疲劳评定',
    major1: '40-制造',
    major2: '4005-增材制造',
    leadOrgName: '上飞公司',
    orgId: 20,
    orgName: '上海飞机制造有限公司',
    applyFund: 1240,
    status: 'APPROVING',
    flowNode: '一级总师',
    daysAgo: 9,
  },
  {
    channelId: 3,
    name: '重点研发计划 — 民机环控系统适航符合性验证方法',
    major1: '30-系统',
    major2: '3006-环控与氧气',
    leadOrgName: '上飞院',
    orgId: 10,
    orgName: '上海飞机设计研究院',
    applyFund: 1680,
    status: 'SUBMITTED',
    flowNode: '项目承担部门负责人',
    daysAgo: 2,
  },
]

DECL_SEEDS.forEach((s, i) => {
  const ch = channels.find((c) => c.id === s.channelId)
  const did = i + 1
  declarations.push({
    id: did,
    applyNo: `SB${dayjs().format('YYYY')}S${String(did).padStart(3, '0')}`,
    name: s.name,
    channelId: ch?.id,
    channelName: ch?.channelName,
    levelCode: ch?.levelCode as any,
    needApproval: 1,
    goal: '面向预先研究与工程化验证的综合技术攻关。',
    applyFund: s.applyFund,
    startDate: '2026-09-01',
    endDate: '2029-08-31',
    leadOrgName: s.leadOrgName,
    leadWorkContent: '牵头总体方案与关键技术验证。',
    major1: s.major1,
    major2: s.major2,
    orgId: s.orgId,
    orgName: s.orgName,
    applicant: DEMO_POSTS.leader,
    posts: { ...DEMO_POSTS },
    applyAt: d(-s.daysAgo),
    status: s.status,
    flowNode: s.flowNode,
  })
  const need = (ch?.declareMaterial || '').split(',').filter(Boolean)
  const allFields = ['建议书', '建议书意见', '申请书', '申请书评审', '榜单答疑', '任务清单', '任务清单评估', '申报通知', '委员会审议', '学术委员会审议', '合作需求', '需求对接总结', '技术发展战略委员会审议', '项目申请书', '波音指导委员会会议纪要']
  allFields.forEach((f) => {
    const applicable = need.includes(f)
    materials.push({
      id: nid(),
      bizType: 'DECLARATION',
      bizId: did,
      fieldCode: `F_${f}`,
      fieldName: f,
      fileName: applicable && i % 3 !== 0 ? `${f}.pdf` : undefined,
      version: 1,
      required: applicable ? 1 : 0,
      locked: applicable ? 0 : 1,
      uploadedAt: applicable && i % 3 !== 0 ? d(-15) : undefined,
    })
  })
})

/* ------------------------- 变更 ------------------------- */
for (let i = 0; i < 6; i++) {
  const p = projects[i]
  changes.push({
    id: i + 1,
    changeNo: `BG${dayjs().format('YYYY')}${String(7000 + i)}`,
    projectId: p?.id || 1,
    projectName: p?.name,
    changeType: i % 3 === 0 ? 'DATA' : 'PROJECT',
    category: ['MILESTONE_DELAY', 'FUND', 'PERIOD', 'OUTSOURCE', 'BASIC', 'INDICATOR'][i],
    title: ['某里程碑节点延期 60 天', '年度预算额度调增 300 万元', '项目周期延长 6 个月', '外协单位更换申请', '年度任务填报数据纠错', '核心考核指标调整'][i],
    reason: ['关键试验设备排期冲突，需顺延节点', '外协费用上涨，需追加预算', '技术攻关难度高于预期', '原外协单位交付质量不达标', '填报时单位换算错误', '技术路线优化后指标可提升'][i],
    beforeValue: ['2026-08-30', '1200 万元', '2027-06-30', '原外协单位 A', '1200 吨', '效率 88%'][i],
    afterValue: ['2026-10-29', '1500 万元', '2027-12-31', '拟更换为外协单位 B', '12000 吨', '效率 91%'][i],
    legalReview: i === 3 ? 1 : 0,
    status: ['APPROVING', 'APPROVED', 'DRAFT', 'REJECTED', 'APPROVED', 'APPROVING'][i],
    flowNode: ['二级单位主管部门初审', '总部终审', '', '二级单位主管部门初审', '总部科技主管确认', '法务审核'][i],
    applicant: NAMES[i % NAMES.length],
    createdAt: d(-25 - i * 4),
  })
}

/* ------------------------- 预警 / 审计 ------------------------- */
function buildWarnings() {
  warnings.length = 0
  const push = (bizType: string, p: ProjInfo | undefined, level: 'YELLOW' | 'RED', title: string, content: string) =>
    warnings.push({ id: warnings.length + 1, bizType, projectId: p?.id, projectName: p?.name, warnLevel: level, title, content, isRead: 0, createdAt: d(-1) })
  milestones.forEach((m) => {
    if (m.colorStatus === 'RED') push('MILESTONE', projects.find((p) => p.id === m.projectId), 'RED', `里程碑逾期：${m.name}`, `计划完成时间 ${m.planDate}，已超期未完成，请尽快处理或发起延期变更。`)
    if (m.colorStatus === 'YELLOW') push('MILESTONE', projects.find((p) => p.id === m.projectId), 'YELLOW', `里程碑临期：${m.name}`, `距计划完成时间 ${m.planDate} 不足 30 天，请及时推进并准备佐证材料。`)
  })
  deliverables.forEach((x) => {
    if (x.colorStatus === 'RED') push('DELIVERABLE', projects.find((p) => p.id === x.projectId), 'RED', `交付物逾期：${x.name}`, `应交付时间 ${x.dueDate}，尚未交付。`)
    if (x.colorStatus === 'YELLOW') push('DELIVERABLE', projects.find((p) => p.id === x.projectId), 'YELLOW', `交付物临期：${x.name}`, `距应交付时间 ${x.dueDate} 不足 30 天。`)
  })
  plans.forEach((x) => {
    if (x.planType === 'TODO' && x.colorStatus === 'RED') push('PLAN', projects.find((p) => p.id === x.projectId), 'RED', `计划超期：${x.title}`, `待办计划已超期，请提交办结申请。`)
  })
  transforms.forEach((t) => {
    if (t.colorStatus === 'YELLOW') push('TRANSFORM', projects.find((p) => p.id === t.projectId), 'YELLOW', `成果转化临期：${t.name}`, `距计划转化时间 ${t.planDate} 不足 30 天。`)
    if (t.colorStatus === 'RED') push('TRANSFORM', projects.find((p) => p.id === t.projectId), 'RED', `成果转化逾期：${t.name}`, `已超计划转化时间 ${t.planDate}。`)
  })
  postEvals.forEach((e) => {
    if (e.colorStatus === 'YELLOW' || e.colorStatus === 'RED') push('POST_EVAL', projects.find((p) => p.id === e.projectId), e.colorStatus as any, `后评价到期提醒：${e.projectName}`, `后评价应于 ${e.dueDate} 前完成。`)
  })
}
buildWarnings()

for (let i = 0; i < 40; i++) {
  auditLogs.push({
    id: i + 1,
    userName: NAMES[i % NAMES.length],
    module: ['项目台账', '里程碑', '经费管理', '项目变更', '成果转化', '协作单位评价', '用户管理'][i % 7],
    action: ['CREATE', 'UPDATE', 'SUBMIT', 'APPROVE', 'EXPORT'][i % 5],
    content: `在【${['项目台账', '里程碑', '经费管理', '项目变更', '成果转化', '协作单位评价', '用户管理'][i % 7]}】模块执行了${['新增', '修改', '提交', '审批', '导出'][i % 5]}操作`,
    createdAt: d(-i).concat(' 10:00:00'),
  })
}

/* ------------------------- 协作单位黑名单样本 ------------------------- */
blacklist.push(
  {
    id: 1,
    partnerName: '某外协测控公司',
    reason: '评价得分 38 分（不合格），交付质量与进度履约不达标，纳入黑名单',
    inDate: d(-12),
    createBy: '林晚晴',
  },
  {
    id: 2,
    partnerName: '示意——华东某复合材料加工厂',
    reason: '历史项目多次延期且关键节点佐证材料虚假，经单位科技管理部确认列入黑名单',
    inDate: d(-120),
    createBy: '方致远',
  },
)

/* ------------------------- 对外操作工具 ------------------------- */
export function recomputeProject(id: number) {
  const p = projects.find((x) => x.id === id)
  if (!p) return
  p.warnColor = mergeColor([
    ...milestones.filter((m) => m.projectId === id).map((m) => m.colorStatus),
    ...deliverables.filter((x) => x.projectId === id).map((x) => x.colorStatus),
  ])
}

export function gradeOf(score: number) {
  if (score >= 90) return 'EXCELLENT'
  if (score >= 80) return 'GOOD'
  if (score >= 60) return 'PASS'
  return 'FAIL'
}
