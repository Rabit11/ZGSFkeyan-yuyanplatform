export const READONLY_FLOW_NOTICE = '流程图仅用于查看进度、责任人、节点要求和历史记录；填报、上传、提交、审核请从左侧任务栏进入对应功能页面办理。'

const BUSINESS_ACTION_WORDS = [
  '去填报',
  '去上传',
  '上传',
  '去审核',
  '审核',
  '去审批',
  '审批',
  '去办理',
  '办理',
  '去提交',
  '提交',
  '去复核',
  '复核',
  '去总核',
  '总核',
  '确认/上传',
  '按清单上传交付物证明',
]

export function isBusinessActionLabel(label?: string): boolean {
  const text = String(label || '').trim()
  if (!text) return false
  if (text.startsWith('查看')) return false
  return BUSINESS_ACTION_WORDS.some((word) => text.includes(word))
}

export function readonlyFlowActionLabel(label?: string): string {
  const text = String(label || '').trim()
  if (!text || isBusinessActionLabel(text)) return '查看'
  return text
}

export function readonlyFlowPath(_path?: string): undefined {
  return undefined
}

