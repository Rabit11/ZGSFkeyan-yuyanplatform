import http from './request'

export const authApi = {
  login: (data: { username: string; password: string }) =>
    http.post('/api/auth/login', data),
  profile: () => http.get('/api/auth/profile'),
  logout: () => http.post('/api/auth/logout'),
}

export const dictApi = {
  byType: (type: string) => http.get(`/api/dict/${type}`),
  channels: (levelCode?: string) =>
    http.get('/api/dict/channels', { levelCode }),
  channel: (id: number) => http.get(`/api/dict/channel/${id}`),
}

export const projectApi = {
  page: (params: any) => http.get('/api/projects', params),
  detail: (id: number) => http.get(`/api/projects/${id}`),
  overview: (id: number) => http.get(`/api/projects/${id}/overview`),
  create: (data: any) => http.post('/api/projects', data),
  update: (id: number, data: any) => http.put(`/api/projects/${id}`, data),
  updateFromFormMaint: (id: number, data: any) => http.put(`/api/projects/form-maint/${id}`, data),
  saveAnnualPlan: (id: number, data: any) => http.put(`/api/projects/${id}/annual-plan`, data),
  saveMaintenanceMaterial: (id: number, data: any) => http.post(`/api/projects/${id}/maintenance/materials`, data),
  submitMaintenance: (id: number) => http.post(`/api/projects/${id}/maintenance/submit`),
  unitAuditMaintenance: (id: number, data: any) => http.post(`/api/projects/${id}/maintenance/unit-audit`, data),
  hqAuditMaintenance: (id: number, data: any) => http.post(`/api/projects/${id}/maintenance/hq-audit`, data),
  pendingMaintenance: () => http.get('/api/projects/maintenance/pending'),
  remove: (id: number) => http.del(`/api/projects/${id}`),
  removeFromFormMaint: (id: number) => http.del(`/api/projects/form-maint/${id}`),
  submit: (id: number) => http.post(`/api/projects/${id}/submit`),
  refreshStatus: (id: number) => http.post(`/api/projects/${id}/refresh-status`),
  export: (params: any) => http.get('/api/projects/export', params),
}

export const milestoneApi = {
  list: (projectId: number, params?: any) =>
    http.get(`/api/projects/${projectId}/milestones`, params),
  board: (params?: any) => http.get('/api/milestones/board', params),
  detail: (id: number) => http.get(`/api/milestones/${id}`),
  create: (data: any) => http.post('/api/milestones', data),
  update: (id: number, data: any) => http.put(`/api/milestones/${id}`, data),
  remove: (id: number) => http.del(`/api/milestones/${id}`),
  close: (id: number, data?: any) => http.post(`/api/milestones/${id}/close`, data),
  auditClose: (id: number, data?: any) => http.post(`/api/milestones/${id}/close-audit`, data),
  delay: (id: number) => http.post(`/api/milestones/${id}/delay`),
  materials: (id: number) => http.get(`/api/milestones/${id}/materials`),
  saveMaterial: (id: number, data: any) => http.post(`/api/milestones/${id}/materials`, data),
  auditAnnualPlan: (projectId: number, data: any) =>
    http.post(`/api/milestones/annual-plan/audit?projectId=${projectId}&year=${data?.year || ''}`, data),
}

export const fileApi = {
  upload: (data: FormData, bizType = 'evidence') =>
    http.post(`/api/files/upload?bizType=${encodeURIComponent(bizType)}`, data),
}

export const planApi = {
  list: (projectId: number, params?: any) =>
    http.get(`/api/projects/${projectId}/plans`, params),
  create: (data: any) => http.post('/api/plans', data),
  update: (id: number, data: any) => http.put(`/api/plans/${id}`, data),
  remove: (id: number) => http.del(`/api/plans/${id}`),
  finishApply: (id: number) => http.post(`/api/plans/${id}/finish-apply`),
  finishAudit: (id: number, data: any) =>
    http.post(`/api/plans/${id}/finish-audit`, data),
  sync: () => http.post('/api/plans/sync'),
}

export const fundApi = {
  pendingReviews: () => http.get('/api/fund/reviews/pending'),
  budgets: (projectId: number) => http.get(`/api/projects/${projectId}/fund/budgets`),
  createBudget: (data: any) => http.post('/api/fund/budgets', data),
  updateBudget: (id: number, data: any) => http.put(`/api/fund/budgets/${id}`, data),
  removeBudget: (id: number) => http.del(`/api/fund/budgets/${id}`),
  payments: (projectId: number) => http.get(`/api/projects/${projectId}/fund/payments`),
  createPayment: (data: any) => http.post('/api/fund/payments', data),
  writeoff: (id: number, data?: any) => http.post(`/api/fund/payments/${id}/writeoff`, data || { pass: true }),
  hqBudgets: (year?: number) => http.get('/api/hq-fund/budgets', { year }),
  createHqBudget: (data: any) => http.post('/api/hq-fund/budgets', data),
  lockHqBudget: (id: number) => http.post(`/api/hq-fund/budgets/${id}/lock`),
  quotas: (budgetId: number) => http.get('/api/hq-fund/quotas', { budgetId }),
  createQuota: (data: any) => http.post('/api/hq-fund/quotas', data),
  transfers: (budgetId?: number) => http.get('/api/hq-fund/transfers', { budgetId }),
  createTransfer: (data: any) => http.post('/api/hq-fund/transfers', data),
  auditTransfer: (id: number, data: any) =>
    http.post(`/api/hq-fund/transfers/${id}/audit`, data),
}

export const evalApi = {
  list: (projectId: number) => http.get(`/api/projects/${projectId}/evaluations`),
  create: (data: any) => http.post('/api/evaluations', data),
  update: (id: number, data: any) => http.put(`/api/evaluations/${id}`, data),
  remove: (id: number) => http.del(`/api/evaluations/${id}`),
}

export const changeApi = {
  page: (params: any) => http.get('/api/changes', params),
  create: (data: any) => http.post('/api/changes', data),
  update: (id: number, data: any) => http.put(`/api/changes/${id}`, data),
  remove: (id: number) => http.del(`/api/changes/${id}`),
  submit: (id: number) => http.post(`/api/changes/${id}/submit`),
  audit: (id: number, data: any) => http.post(`/api/changes/${id}/audit`, data),
}

export const declarationApi = {
  page: (params: any) => http.get('/api/declarations', params),
  pending: () => http.get('/api/declarations/pending'),
  detail: (id: number) => http.get(`/api/declarations/${id}`),
  create: (data: any) => http.post('/api/declarations', data),
  update: (id: number, data: any) => http.put(`/api/declarations/${id}`, data),
  checkDuplicate: (data: { name: string }) => http.post('/api/declarations/check-duplicate', data),
  materials: (id: number) => http.get(`/api/declarations/${id}/materials`),
  uploadMaterial: (id: number, data: any) =>
    http.post(`/api/declarations/${id}/materials`, data),
  submit: (id: number) => http.post(`/api/declarations/${id}/submit`),
  audit: (id: number, data: any) => http.post(`/api/declarations/${id}/audit`, data),
  revoke: (id: number) => http.post(`/api/declarations/${id}/revoke`),
  filing: (id: number, data?: any) => http.post(`/api/declarations/${id}/filing`, data),
  submitFiling: (id: number) => http.post(`/api/declarations/${id}/filing-submit`),
}

export const acceptanceApi = {
  detail: (projectId: number) => http.get(`/api/acceptance/${projectId}`),
  check: (projectId: number) => http.post(`/api/acceptance/${projectId}/check`),
  submit: (projectId: number) => http.post(`/api/acceptance/${projectId}/submit`),
  materials: (projectId: number, data: any) =>
    http.post(`/api/acceptance/${projectId}/materials`, data),
  audit: (projectId: number, data: any) =>
    http.post(`/api/acceptance/${projectId}/audit`, data),
}

export const deliverableApi = {
  page: (params: any) => http.get('/api/deliverables', params),
  list: (projectId: number) => http.get(`/api/projects/${projectId}/deliverables`),
  create: (data: any) => http.post('/api/deliverables', data),
  update: (id: number, data: any) => http.put(`/api/deliverables/${id}`, data),
  remove: (id: number) => http.del(`/api/deliverables/${id}`),
  bind: (id: number, data: any) => http.post(`/api/deliverables/${id}/bind`, data),
}

export const partnerApi = {
  list: (projectId: number) => http.get(`/api/projects/${projectId}/partner-evals`),
  create: (data: any) => http.post('/api/partner-evals', data),
  update: (id: number, data: any) => http.put(`/api/partner-evals/${id}`, data),
  remove: (id: number) => http.del(`/api/partner-evals/${id}`),
  submit: (id: number) => http.post(`/api/partner-evals/${id}/submit`),
  blacklist: () => http.get('/api/partners/blacklist'),
}

export const transformApi = {
  page: (params: any) => http.get('/api/transforms', params),
  detail: (id: number) => http.get(`/api/transforms/${id}`),
  create: (data: any) => http.post('/api/transforms', data),
  update: (id: number, data: any) => http.put(`/api/transforms/${id}`, data),
  remove: (id: number) => http.del(`/api/transforms/${id}`),
  bind: (id: number, data: any) => http.post(`/api/transforms/${id}/bind`, data),
}

export const postEvalApi = {
  page: (params: any) => http.get('/api/post-evals', params),
  detail: (id: number) => http.get(`/api/post-evals/${id}`),
  create: (data: any) => http.post('/api/post-evals', data),
  update: (id: number, data: any) => http.put(`/api/post-evals/${id}`, data),
  remove: (id: number) => http.del(`/api/post-evals/${id}`),
  submit: (id: number) => http.post(`/api/post-evals/${id}/submit`),
}

export const dashboardApi = {
  overview: (params?: Record<string, any>) => http.get('/api/dashboard/overview', params),
  warnings: () => http.get('/api/dashboard/warnings'),
}

export const warningApi = {
  page: (params: any) => http.get('/api/warnings', params),
  read: (id: number) => http.post(`/api/warnings/${id}/read`),
  scan: () => http.post('/api/warnings/scan'),
}

export const auditApi = {
  page: (params: any) => http.get('/api/audit-logs', params),
  mineDone: (params: any) => http.get('/api/audit-logs/mine/done', params),
}

export const backupApi = {
  page: (params: any) => http.get('/api/backups', params),
  policy: () => http.get('/api/backups/policy'),
  create: (data?: { remark?: string; triggerType?: string }) =>
    http.post('/api/backups', data || {}, 180000),
  restore: (id: number, confirmNo: string) =>
    http.post(`/api/backups/${id}/restore`, { confirmNo }, 180000),
}

export const userApi = {
  page: (params: any) => http.get('/api/users', params),
  candidates: () => http.get('/api/users/candidates'),
  create: (data: any) => http.post('/api/users', data),
  update: (id: number, data: any) => http.put(`/api/users/${id}`, data),
  remove: (id: number) => http.del(`/api/users/${id}`),
  assignRoles: (id: number, data: RoleCode[]) => http.post(`/api/users/${id}/roles`, data),
  syncIdentityRoles: () => http.post('/api/users/sync-identity-roles'),
  resetPassword: (id: number) => http.post(`/api/users/${id}/reset-password`),
  setFinishAuth: (id: number, enabled: boolean) =>
    http.post(`/api/users/${id}/finish-auth`, { enabled }),
}

/** 项目岗位办理权限矩阵（系统管理员配置） */
export const permissionApi = {
  getMatrix: () => http.get('/api/permission-matrix'),
  saveMatrix: (data: Record<string, string[]>) => http.put('/api/permission-matrix', data),
  resetMatrix: () => http.post('/api/permission-matrix/reset'),
}

import type { RoleCode } from './types'
