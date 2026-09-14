import http from './request'
import type { AchvTransform, ProjDeliverable } from './types'
export interface TransformPackage extends AchvTransform {
  orphanedProject?: boolean
  confirmedStatus?: string
  confirmedActualDate?: string
  legacyRecord?: boolean
  projectName?: string
  reportedActualDate?: string
  reportedStatus?: string
  workflowStatus: string
  revision: number
  evidenceJson?: string
  historyJson?: string
  deliverableIds?: number[]
  deliverables?: ProjDeliverable[]
  allowedActions?: string[]
}
export const transformWorkflowApi = {
  permissions: (projectId: number) => http.get('/api/transforms/permissions', { params: { projectId } }),
  upload: (data: FormData) => http.post('/api/files/transform/upload', data),
  act: (id: number, revision: number, action: string, note = '') => http.post(`/api/transforms/${id}/workflow`, { revision, action, note }),
}
