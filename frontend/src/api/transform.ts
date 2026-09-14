import http from './request'
import type { AchvTransform, ProjDeliverable } from './types'
export interface TransformPackage extends AchvTransform {
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
  act: (id: number, revision: number, action: string, note = '') => http.post(`/api/transforms/${id}/workflow`, { revision, action, note }),
}
