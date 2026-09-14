// 与后端共用申报专用模板；版本一经使用不可原地改动，应新增版本。
import catalog from '@/config/declaration-workflows.json'

export interface DeclarationNode {
  code: string
  title: string
  roleKeys: string[]
  kind: string
  evidence: boolean
}
export type WorkflowSource = {
  channelCode?: string; channelName?: string; flowNodes?: string
  needApproval?: number; status?: string; flowNode?: string
  posts?: Record<string, string | undefined>
}
export function selectDeclarationWorkflow(source: WorkflowSource = {}): string {
  const byCode = (catalog.channels as Record<string, string>)[source.channelCode || '']
  const byName = (catalog.names as Record<string, string>)[source.channelName || '']
  // 有独立审签模板的渠道不能由客户端 needApproval=0 绕过。
  if (byCode && byCode !== 'common-v1') return byCode
  if (byName) return byName
  if (/无需审批|直接报备/.test(source.flowNodes || '')) return 'report-v1'
  return byCode || 'common-v1'
}
export function declarationWorkflowId(source: WorkflowSource = {}): string {
  const version = source.posts?.__workflow
  if (version) {
    if (!(version in catalog.templates)) throw new Error(`不支持的申报流程版本：${version}`)
    return version
  }
  // 已存在的项目只要带有渠道编码/名称，就按该渠道展示和执行对应的申报链。
  // 只有完全没有渠道信息的历史记录才使用旧固定链兜底。
  if (source.channelCode || source.channelName) return selectDeclarationWorkflow(source)
  if (source.status && !['DRAFT', 'REJECTED'].includes(source.status)) {
    return source.needApproval === 0 ? 'legacy-report-v0' : 'legacy-v0'
  }
  return selectDeclarationWorkflow(source)
}
export function declarationNodes(source: WorkflowSource = {}): DeclarationNode[] {
  const ids = (catalog.templates as Record<string, string[]>)[declarationWorkflowId(source)]
  return ids.map(code => catalog.nodes.find(node => node.code === code)!)
}
export function declarationAuditNodes(source: WorkflowSource = {}) {
  return declarationNodes(source).filter(node => node.kind !== 'fill' && node.kind !== 'end')
}
export function currentDeclarationNode(source: WorkflowSource) {
  const title = source.flowNode === '承办部门负责人' ? '项目承担部门负责人' : source.flowNode
  return declarationAuditNodes(source).find(node => node.title === title)
}
export function declarationActorLabels(source: WorkflowSource): string[] {
  return (currentDeclarationNode(source)?.roleKeys || []).map(key => source.posts?.[key]).filter(Boolean) as string[]
}
export function declarationRequiredPosts(source: WorkflowSource) {
  const seen = new Set<string>()
  return declarationNodes(source).flatMap(node => node.roleKeys.slice(0, 1).flatMap(key => {
    if (seen.has(key)) return []
    seen.add(key)
    return [{ key, label: node.title }]
  }))
}
