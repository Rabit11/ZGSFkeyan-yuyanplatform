import { defineStore } from 'pinia'
import { declarationApi, fundApi, milestoneApi, projectApi } from '@/api/modules'
import { useUserStore } from '@/stores/user'

export type PendingAuditTask = {
  key: string
  kind: 'declaration' | 'maintenance' | 'milestone' | 'fund'
  title: string
  subtitle: string
  node: string
  tag: string
  color: string
  route: string
}

type FundReviewRow = {
  key: string
  projectId: number
  projectNo?: string
  projectName?: string
  ownerName?: string
  node: string
  tag: string
  mode: 'budget' | 'writeoff' | 'final'
  desk: string
  count: number
  itemNames: string[]
}

async function loadFundReviewRows(user: ReturnType<typeof useUserStore>): Promise<FundReviewRow[]> {
  const identity = String(user.identityCode || '')
  const isAdmin = Boolean(user.isAdmin || identity === 'admin')
  const canUnitBudget = isAdmin || identity === 'finHead' || identity === 'finStaff'
  const canUnitFinal = isAdmin || identity === 'finHead'
  const canUnitWriteoff = isAdmin || identity === 'finHead'
  const canHqFund = isAdmin || identity === 'finHq'
  if (!canUnitBudget && !canUnitFinal && !canUnitWriteoff && !canHqFund) return []
  const res = await fundApi.pendingReviews()
  return ((res.data as FundReviewRow[]) || []).filter((row) => {
    if (row.mode === 'writeoff' && (row.node.includes('填报') || row.node.includes('上传') || row.node.includes('本级核销'))) return canUnitWriteoff
    if (row.mode === 'final' && row.node.includes('二级单位')) return canUnitFinal
    if (row.mode === 'final' && row.node.includes('总部')) return canHqFund
    if (row.mode === 'writeoff' && row.node.includes('单位财务负责人')) return canUnitWriteoff
    if (row.mode === 'budget' && row.node.includes('总部')) return canHqFund
    return canUnitBudget
  })
}

export const usePendingStore = defineStore('pending', {
  state: () => ({
    declarationReviewCount: 0,
    maintenanceReviewCount: 0,
    milestoneReviewCount: 0,
    filingPendingCount: 0,
    declarationReviews: [] as any[],
    maintenanceReviews: [] as any[],
    milestoneReviews: [] as any[],
    fundReviewCount: 0,
    fundReviews: [] as FundReviewRow[],
  }),
  getters: {
    totalReviewCount: (state) => state.declarationReviewCount + state.maintenanceReviewCount,
    totalAuditCount: (state) => state.declarationReviewCount + state.maintenanceReviewCount + state.milestoneReviewCount + state.fundReviewCount,
    fundBudgetReviewCount: (state) => state.fundReviews.filter((row) => row.mode === 'budget').length,
    fundWriteoffReviewCount: (state) => state.fundReviews.filter((row) => row.mode === 'writeoff').length,
    fundFinalReviewCount: (state) => state.fundReviews.filter((row) => row.mode === 'final').length,
    auditTasks: (state): PendingAuditTask[] => {
      const year = new Date().getFullYear()
      const declarationTasks = state.declarationReviews.map((row: any) => ({
        key: `declaration-${row.id}-${row.flowNode || ''}`,
        kind: 'declaration' as const,
        title: row.name || row.projectName || row.applyNo || '项目申报',
        subtitle: `申报单号 ${row.applyNo || row.projectNo || '—'} · 负责人 ${row.applicant || row.ownerName || '—'}`,
        node: row.flowNode || '项目申报审核',
        tag: '项目申报',
        color: 'blue',
        route: '/initiation/declaration?review=mine',
      }))
      const maintenanceTasks = state.maintenanceReviews.map((row: any) => ({
        key: `maintenance-${row.id || row.projectId}-${row.acceptStatus || row.status || ''}`,
        kind: 'maintenance' as const,
        title: row.name || row.projectName || row.projectNo || '待维护材料',
        subtitle: `项目编号 ${row.projectNo || '—'} · 负责人 ${row.ownerName || row.applicant || '—'}`,
        node: row.currentNode || row.flowNode || '待维护材料审核',
        tag: '待维护',
        color: 'orange',
        route: '/initiation/declaration?review=mine',
      }))
            const fundTasks = state.fundReviews.map((row: FundReviewRow) => {
        const params = new URLSearchParams()
        params.set('projectId', String(row.projectId))
        params.set('mode', row.mode)
        params.set('desk', row.desk)
        const items = row.itemNames.length ? ` · ${row.itemNames.slice(0, 3).join('、')}${row.itemNames.length > 3 ? '等' : ''}` : ''
        return {
          key: row.key,
          kind: 'fund' as const,
          title: row.projectName || row.projectNo || '项目经费审核',
          subtitle: `${row.projectNo || '—'} · ${row.count} 条经费事项${items}`,
          node: row.node,
          tag: row.tag,
          color: row.mode === 'final' ? 'red' : row.mode === 'writeoff' ? 'green' : 'cyan',
          route: `/implement/fund?${params.toString()}`,
        }
      })
      const milestoneTasks = state.milestoneReviews.map((row: any) => {
        const isCompileAudit = row.taskType === 'COMPILE_AUDIT'
        const params = new URLSearchParams()
        if (row.projectId) params.set('projectId', String(row.projectId))
        if (row.milestoneId) params.set('milestoneId', String(row.milestoneId))
        if (isCompileAudit) params.set('mode', 'compile')
        params.set('year', String(row.year || year))
        return {
          key: `milestone-${row.taskType}-${row.projectId}-${row.milestoneId || row.year || year}`,
          kind: 'milestone' as const,
          title: row.projectName || '实施阶段审核',
          subtitle: `${row.projectNo || '—'} · ${row.milestoneName || '里程碑节点与交付物清单'}`,
          node: isCompileAudit ? '二级单位科技部门审核存档' : row.flowNode || '里程碑销项审核',
          tag: row.typeLabel || '实施审核',
          color: isCompileAudit ? 'gold' : 'purple',
          route: `/implement/milestone-close?${params.toString()}`,
        }
      })
      return [...declarationTasks, ...maintenanceTasks, ...milestoneTasks, ...fundTasks]
    },
    auditPopupSignature(): string {
      return this.auditTasks.map((task: PendingAuditTask) => task.key).sort().join('|')
    },
  },
  actions: {
    setDeclarationReviewCount(count: number) {
      this.declarationReviewCount = Math.max(0, Number(count) || 0)
    },
    setMaintenanceReviewCount(count: number) {
      this.maintenanceReviewCount = Math.max(0, Number(count) || 0)
    },
    setMilestoneReviewCount(count: number) {
      this.milestoneReviewCount = Math.max(0, Number(count) || 0)
    },
    setFundReviewCount(count: number) {
      this.fundReviewCount = Math.max(0, Number(count) || 0)
    },
    setFilingPendingCount(count: number) {
      this.filingPendingCount = Math.max(0, Number(count) || 0)
    },
    async loadDeclarationReviews(identityCode?: string) {
      if (!identityCode) {
        this.declarationReviewCount = 0
        this.maintenanceReviewCount = 0
        this.milestoneReviewCount = 0
        this.fundReviewCount = 0
        this.filingPendingCount = 0
        this.declarationReviews = []
        this.maintenanceReviews = []
        this.milestoneReviews = []
        this.fundReviews = []
        return
      }
      try {
        const user = useUserStore()
        const [res, maintenanceRes, filingRes, milestoneRes] = await Promise.allSettled([
          declarationApi.pending(),
          projectApi.pendingMaintenance(),
          declarationApi.page({ page: 1, size: 200, status: 'APPROVED' }),
          milestoneApi.board({ year: new Date().getFullYear() }),
        ])
        const declarationData = res.status === 'fulfilled' ? res.value.data : undefined
        const maintenanceData = maintenanceRes.status === 'fulfilled' ? maintenanceRes.value.data : undefined
        const filingData = filingRes.status === 'fulfilled' ? filingRes.value.data : undefined
        const milestoneData = milestoneRes.status === 'fulfilled' ? milestoneRes.value.data : undefined
        const declarationReviews = Array.isArray(declarationData) ? declarationData : []
        const maintenanceReviews = ((maintenanceData as any[]) || [])
        const milestoneReviews = (((milestoneData as any)?.todos || []) as any[]).filter(
          (row: any) => row?.taskType === 'COMPILE_AUDIT' || row?.taskType === 'CLOSE_AUDIT',
        )
        this.declarationReviews = declarationReviews
        this.maintenanceReviews = maintenanceReviews
        this.milestoneReviews = milestoneReviews
        this.declarationReviewCount = declarationReviews.length
        this.maintenanceReviewCount = maintenanceReviews.length
        this.milestoneReviewCount = milestoneReviews.length
        let fundReviews: FundReviewRow[] = []
        try {
          fundReviews = await loadFundReviewRows(user)
        } catch {
          // 经费接口异常不能影响申报、维护和里程碑待办。
          fundReviews = []
        }
        this.fundReviews = fundReviews
        this.fundReviewCount = fundReviews.length
        this.filingPendingCount = Number((filingData as any)?.total || 0)
      } catch {
        this.declarationReviewCount = 0
        this.maintenanceReviewCount = 0
        this.milestoneReviewCount = 0
        this.fundReviewCount = 0
        this.filingPendingCount = 0
        this.declarationReviews = []
        this.maintenanceReviews = []
        this.milestoneReviews = []
        this.fundReviews = []
      }
    },
  },
})
