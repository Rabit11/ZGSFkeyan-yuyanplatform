import type { TransformPackage } from '@/api/transform'
import type { ProjDeliverable } from '@/api/types'
export const WORKFLOW_TEXT: Record<string, string> = { DRAFT: '草稿', UNIT_REVIEW: '待二级单位审核', RETURNED: '已退回', HQ_RECORD: '待总部备案', RECORDED: '已备案' }
export const STATUS_TEXT: Record<string, string> = { NOT_STARTED: '未启动', NEGOTIATING: '洽谈中', SIGNED: '已签协议', DONE: '已完成' }
export const WAY_TEXT: Record<string, string> = { MODEL: '向型号转化', MARKET: '向市场转化' }
export const ACTION_TEXT: Record<string, string> = { CREATE: '创建成果包', UPDATE: '更新转化信息', BIND: '调整成果构成', SUBMIT: '提交审核', APPROVE: '审核通过', REJECT: '退回修改', RECORD: '总部备案', REOPEN: '发起进展更新' }
export const FORM_PARENT: Record<string, string> = { INSTALLED: 'MODEL', UNINSTALLED: 'MODEL', TRANSFER: 'MARKET', LICENSE: 'MARKET', JOINT: 'MARKET', INVEST: 'MARKET', OTHER: 'MARKET' }
export function parseArray(value?: string): any[] {
  if (!value) return []
  try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed : [] } catch { return [] }
}
export function bindingReason(d: ProjDeliverable, achievementNo?: string) {
  if (d.status !== 'DELIVERED') return '尚未交付'
  if (d.achievementNo && d.achievementNo !== achievementNo) return '已纳入其他成果包'
  return ''
}
export function validatePackage(form: Partial<TransformPackage>, evidence: any[]) {
  if (!form.name?.trim()) return '请填写成果名称'
  if (!form.projectId) return '请选择所属项目'
  if (!form.deliverableIds?.length) return '至少选择一项已交付交付物'
  if ((form.intro?.length || 0) > 100) return '成果简介不能超过100字'
  if (!form.transformWay || !form.transformForm) return '请选择转化方式和转化形式'
  if (!form.planDate || !form.dutyOrg?.trim()) return '请填写计划转化时间和责任单位'
  if (!form.introDetail?.trim()) return '请填写转化简介'
  if (form.status === 'DONE' && (!form.actualDate || !evidence.length)) return '完成转化必须填写实际日期并上传成效佐证'
  return ''
}
