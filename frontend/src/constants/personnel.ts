/**
 * 人员花名册权威口径（任职人员姓名）
 * 以后新增/演示/导出/登录页一键进入均以此为准。
 * 演示登录账号、密码均为工号。
 */
export const PERSONNEL_ROSTER: Array<{
  employeeNo: string
  realName: string
  username: string
  identity: string
  group: string
}> = [
  { employeeNo: '100001', realName: '系统管理员', username: '100001', identity: '系统管理员', group: '管理岗位' },
  { employeeNo: '100002', realName: '周明远', username: '100002', identity: '公司领导', group: '管理岗位' },
  { employeeNo: '100003', realName: '王建国', username: '100003', identity: '总部责任处室处长', group: '管理岗位' },
  { employeeNo: '100004', realName: '何雨桐', username: '100004', identity: '总部科研项目主管', group: '管理岗位' },
  { employeeNo: '100005', realName: '方致远', username: '100005', identity: '单位科研管理部门负责人', group: '管理岗位' },
  { employeeNo: '100006', realName: '田念慈', username: '100006', identity: '单位项目主管', group: '管理岗位' },
  { employeeNo: '100016', realName: '韩承泽', username: '100016', identity: '项目承担部门负责人', group: '管理岗位' },
  { employeeNo: '100007', realName: '陈铁军', username: '100007', identity: '一级总师（公司级）', group: '责任总师' },
  { employeeNo: '100008', realName: '蔡文渊', username: '100008', identity: '二级总师（单位级）', group: '责任总师' },
  { employeeNo: '100009', realName: '赵美玲', username: '100009', identity: '总部财务主管', group: '财务岗位' },
  { employeeNo: '100010', realName: '毕仲文', username: '100010', identity: '单位财务部长', group: '财务岗位' },
  { employeeNo: '100011', realName: '龚雪君', username: '100011', identity: '单位财务主管', group: '财务岗位' },
  { employeeNo: '100012', realName: '林晚晴', username: '100012', identity: '项目负责人', group: '项目团队' },
  { employeeNo: '100013', realName: '顾思远', username: '100013', identity: '项目联系人', group: '项目团队' },
  { employeeNo: '100014', realName: '沈知行', username: '100014', identity: '技术负责人', group: '项目团队' },
  { employeeNo: '100015', realName: '陆嘉言', username: '100015', identity: '项目主管', group: '项目团队' },
]

export const PERSONNEL_GROUPS = ['管理岗位', '责任总师', '财务岗位', '项目团队'] as const

export function personnelDisplay(employeeNo: string): string {
  const hit = PERSONNEL_ROSTER.find((p) => p.employeeNo === employeeNo)
  return hit ? `${hit.realName}（${hit.employeeNo}）` : employeeNo
}

export function personnelRealName(employeeNo: string): string | undefined {
  return PERSONNEL_ROSTER.find((p) => p.employeeNo === employeeNo)?.realName
}
