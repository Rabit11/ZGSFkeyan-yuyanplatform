<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { fileApi, fundApi, milestoneApi, projectApi } from '@/api/modules'
import { isSilentAuthError } from '@/api/request'
import ProjectSelect from '@/components/ProjectSelect.vue'
import ImplementFlowDialog from '@/components/implement/ImplementFlowDialog.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'
import { useUserStore } from '@/stores/user'
import { usePendingStore } from '@/stores/pending'
import { fmtAmount, fmtDate } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const pendingStore = usePendingStore()

const projectId = ref<number>()
const project = ref<any>(null)
const tab = ref('exec')
const loading = ref(false)
const savingWriteoff = ref(false)
let execRequest = 0
const page = ref<'list' | 'budget' | 'writeoff'>('list')
const flowOpen = ref(false)

const budgets = ref<any[]>([])
const payments = ref<any[]>([])
const milestones = ref<any[]>([])

const hqYear = ref(new Date().getFullYear())
const hqBudgets = ref<any[]>([])
const quotas = ref<any[]>([])
const transfers = ref<any[]>([])
const curBudget = computed(() => hqBudgets.value.find((x) => x.year === hqYear.value))
const transferOpen = ref(false)
const tForm = reactive<any>({ quotaId: undefined, amount: 0, reason: '' })

const viewOnly = computed(() => String(route.query.view || '') === '1')
const ident = computed(() => user.identityCode || '')
const { can: budgetCan, guard: budgetGuard } = useWorkDuty('fund_budget', project)
const { can: writeoffCan, guard: writeoffGuard } = useWorkDuty('fund_writeoff', project)
const budgetReadonly = computed(() => viewOnly.value || !budgetCan.value.fill)
const writeoffReadonly = computed(() => viewOnly.value || !writeoffCan.value.fill)
const isLeader = computed(() => budgetCan.value.submit || writeoffCan.value.submit)
const isTeam = computed(() => budgetCan.value.fill || writeoffCan.value.fill)
const isFinHead = computed(() => ident.value === 'finHead')
const isFinStaff = computed(() => ident.value === 'finStaff')
const isUnitFin = computed(() => ['finHead', 'finStaff'].includes(ident.value) && (budgetCan.value.audit || writeoffCan.value.audit))
const isHqFin = computed(() => ident.value === 'finHq' && budgetCan.value.audit)
const canViewHqFund = computed(() => user.isAdmin || ['finHq', 'hqHead', 'hqStaff'].includes(ident.value))
const isFinanceUser = computed(() => isUnitFin.value || isHqFin.value)

const nodeBudgets = computed(() => budgets.value.filter((b) => b.milestoneName !== '项目经费总核'))

const bYear = ref(new Date().getFullYear())
const bRemark = ref('')
const bLines = ref<{ milestoneId: number; name: string; planDate?: string; amount: number; budgetId?: number; status?: string }[]>([])

type WItem = { occurDate: string; amount: number; name: string; voucherNo: string; fileName: string; fileUrl: string }
const wYear = ref(new Date().getFullYear())
const wMsId = ref<number>()
const wItems = ref<WItem[]>([{ occurDate: new Date().toISOString().slice(0, 10), amount: 0, name: '', voucherNo: '', fileName: '', fileUrl: '' }])

function isMsClosed(m: any) {
  return m?.status === 'DONE' || m?.colorStatus === 'GREEN'
}

const closedMs = computed(() => milestones.value.filter(isMsClosed))
const allClosed = computed(() => milestones.value.length > 0 && closedMs.value.length === milestones.value.length)
const hasClosedMs = computed(() => closedMs.value.length > 0)
const fundLockMessage = computed(() => {
  if (!milestones.value.length) return '尚未编制里程碑节点，暂无可核销的节点'
  return `经费核销须选择已闭环的里程碑节点；当前已闭环 ${closedMs.value.length}/${milestones.value.length}`
})

/** 预算填报随里程碑绑定，无需闭环；仅需已编制里程碑节点。 */
function requireBudgetReady() {
  if (milestones.value.length) return true
  message.warning('请先在里程碑管理编制节点，预算按节点绑定填报')
  return false
}

/** 经费核销须存在已闭环节点（对应节点闭环校验在提交时执行）。 */
function requireWriteoffReady() {
  if (hasClosedMs.value) return true
  message.warning(fundLockMessage.value)
  return false
}

function paysOfMs(mid: number) {
  return payments.value.filter((p) => Number(p.budgetId) === mid)
}

const nodeWrittenAll = computed(() =>
  allClosed.value &&
  closedMs.value.every((m) => {
    const list = paysOfMs(Number(m.id))
    return list.length > 0 && list.every((p) => p.writeoffStatus === 'WRITTEN')
  }),
)

const selectedMs = computed(() => milestones.value.find((m) => m.id === wMsId.value))
const nodeBudgetAmt = computed(() => {
  const b = nodeBudgets.value.find((x) => x.milestoneId === wMsId.value)
  return Number(b?.amount || selectedMs.value?.budget || 0)
})
const nodeWrittenAmt = computed(() =>
  paysOfMs(Number(wMsId.value || 0)).filter((p) => p.writeoffStatus === 'WRITTEN').reduce((s, p) => s + Number(p.amount || 0), 0),
)
const nodePendingAmt = computed(() =>
  paysOfMs(Number(wMsId.value || 0)).filter((p) => ['PENDING', 'UNIT_OK', 'DRAFT'].includes(String(p.writeoffStatus || ''))).reduce((s, p) => s + Number(p.amount || 0), 0),
)
const pendingWriteoffMilestones = computed(() =>
  closedMs.value.filter((m) => {
    const budget = nodeBudgets.value.find((b) => Number(b.milestoneId) === Number(m.id) && b.status === 'APPROVED')
    if (!budget) return false
    const rows = paysOfMs(Number(m.id))
    return rows.length === 0 || rows.some((p) => p.writeoffStatus === 'DRAFT')
  }),
)

const wBatchTotal = computed(() => wItems.value.reduce((s, x) => s + Number(x.amount || 0), 0))

const financeRoleProfile = computed(() => {
  if (isHqFin.value) {
    return {
      title: '总部财务主管工作台',
      desc: '负责总部年度预算总盘子、二级单位额度拨付、项目节点预算复核备案。',
      role: '总部财务',
    }
  }
  if (isFinHead.value) {
    return {
      title: '二级单位财务负责人工作台',
      desc: '负责本单位项目预算审核、核销凭证审核确认、异常核销退回。',
      role: '单位财务负责人',
    }
  }
  if (isFinStaff.value) {
    return {
      title: '二级单位财务经办工作台',
      desc: '负责预算初核协同、本单位经费执行数据查看和财务材料协同。',
      role: '单位财务经办',
    }
  }
  return { title: '项目经费工作台', desc: '项目团队按里程碑闭环结果填报预算与查看核销。', role: '项目团队' }
})

const financeCounts = computed(() => ({
  unitBudget: nodeBudgets.value.filter((b) => b.status === 'PENDING').length,
  hqBudget: nodeBudgets.value.filter((b) => b.status === 'UNIT_OK').length,
  writeoffUpload: pendingWriteoffMilestones.value.length,
  writeoffSubmit: payments.value.filter((p) => p.writeoffStatus === 'DRAFT').length,
  unitWriteoff: payments.value.filter((p) => ['PENDING', 'UNIT_OK'].includes(String(p.writeoffStatus || ''))).length,
  hqWriteoff: 0,
}))

const hasUnitBudgetReview = computed(() => isUnitFin.value && nodeBudgets.value.some((b) => b.status === 'PENDING'))
const hasHqBudgetReview = computed(() => isHqFin.value && nodeBudgets.value.some((b) => b.status === 'UNIT_OK'))
const canAuditBudgetPage = computed(() => hasUnitBudgetReview.value || hasHqBudgetReview.value)
const budgetPageTitle = computed(() => {
  if (hasUnitBudgetReview.value) return '经费预算审核'
  if (hasHqBudgetReview.value) return '经费预算复核备案'
  return budgetReadonly.value ? '查看经费预算填报' : '按里程碑节点填报经费预算'
})
const budgetPageHint = computed(() => {
  if (hasUnitBudgetReview.value) return '当前节点为二级单位财务审核，请核对各里程碑节点预算后选择审核通过或退回项目团队。'
  if (hasHqBudgetReview.value) return '当前节点为总部财务复核备案，请核对二级单位已审核的节点预算后选择复核通过或退回二级单位财务。'
  if (budgetReadonly.value) return '只读查看已填或待填节点预算，不改动审签状态。'
  return '项目团队按里程碑节点填报预算（无需等待里程碑闭环）；审核通过后汇总为本年度预算并同步经费台账。团队成员可暂存，负责人提交审签。'
})

const financeTaskCards = computed(() => {
  const cards: { key: string; title: string; count: number; desc: string; action: string; disabled?: boolean }[] = []
  if (isUnitFin.value) {
    cards.push({ key: 'unitBudget', title: '预算待审', count: financeCounts.value.unitBudget, desc: '项目团队提交后由二级单位财务先审。', action: '去审核' })
    cards.push({ key: 'writeoff', title: isFinHead.value ? '核销上传/办理' : '核销登记', count: isFinHead.value ? financeCounts.value.writeoffUpload + financeCounts.value.unitWriteoff : financeCounts.value.writeoffSubmit, desc: isFinHead.value ? '对应里程碑闭环后上传付款凭证并完成本级核销，系统同步总部经费看板。' : '按已闭环里程碑节点登记付款凭证。', action: isFinHead.value ? '去办理' : '去登记', disabled: !hasClosedMs.value })
  }
  if (isHqFin.value) {
    cards.push({ key: 'hqBudget', title: '预算复核备案', count: financeCounts.value.hqBudget, desc: '二级单位财务通过后由总部财务复核备案。', action: '去复核' })
    cards.push({ key: 'hqControl', title: '总部经费管控', count: transfers.value.filter((t) => t.status !== 'PAID').length, desc: '年度总盘子、拨付额度和拨付执行台账。', action: '进入管控' })
  }
  return cards
})

const financeExecFlow = computed(() => [
  { no: '1', title: '里程碑节点编制', desc: `预算按节点绑定填报，核销时对应节点需闭环（已闭环 ${closedMs.value.length}/${milestones.value.length}）`, state: milestones.value.length ? 'done' : 'todo' },
  { no: '2', title: '项目团队预算填报', desc: '负责人提交审签，项目联系人权限负责人全部具备', state: nodeBudgets.value.some((b) => ['PENDING', 'UNIT_OK', 'APPROVED'].includes(b.status)) ? 'done' : 'todo' },
  { no: '3', title: '二级单位财务审核', desc: '单位财务负责人/经办处理预算审核', state: nodeBudgets.value.some((b) => b.status === 'PENDING') ? 'current' : nodeBudgets.value.some((b) => ['UNIT_OK', 'APPROVED'].includes(b.status)) ? 'done' : 'todo' },
  { no: '4', title: '总部财务复核备案', desc: '总部财务主管复核预算并备案生效', state: nodeBudgets.value.some((b) => b.status === 'UNIT_OK') ? 'current' : nodeBudgets.value.some((b) => b.status === 'APPROVED') ? 'done' : 'todo' },
  { no: '5', title: '节点核销', desc: '对应里程碑闭环后，二级单位财务上传付款凭证完成本级核销并同步总部看板', state: nodeWrittenAll.value ? 'done' : hasClosedMs.value && (payments.value.length > 0 || nodeBudgets.value.some((b) => b.status === 'APPROVED')) ? 'current' : 'todo' },
].map((step) => ({
  ...step,
  statusText: step.state === 'done' ? '已完成' : step.state === 'current' ? '当前办理' : '未开始',
})))

async function loadExec() {
  if (!projectId.value) return
  const requestedProjectId = projectId.value
  const requestId = ++execRequest
  loading.value = true
  try {
    const [a, b, m, p] = await Promise.all([
      fundApi.budgets(requestedProjectId),
      fundApi.payments(requestedProjectId),
      milestoneApi.list(requestedProjectId),
      projectApi.detail(requestedProjectId),
    ])
    if (requestId !== execRequest || requestedProjectId !== projectId.value) return
    const switchedProject = Number(project.value?.id) !== Number(requestedProjectId)
    budgets.value = (a.data as any[]) || []
    payments.value = (b.data as any[]) || []
    milestones.value = (m.data as any[]) || []
    project.value = p.data
    if (switchedProject && page.value === 'budget') openBudget()
    if (switchedProject && page.value === 'writeoff') {
      wItems.value = [{ occurDate: new Date().toISOString().slice(0, 10), amount: 0, name: '', voucherNo: '', fileName: '', fileUrl: '' }]
      wMsId.value = (pendingWriteoffMilestones.value[0] || closedMs.value[0])?.id
    }
  } catch (error: any) {
    if (requestId !== execRequest || requestedProjectId !== projectId.value) return
    project.value = null
    budgets.value = []
    payments.value = []
    milestones.value = []
    bLines.value = []
    wMsId.value = undefined
    if (!isSilentAuthError(error)) message.error('经费数据加载失败，请重新选择项目或刷新页面')
  } finally {
    if (requestId === execRequest) loading.value = false
  }
}

async function loadHq() {
  if (!canViewHqFund.value) {
    hqBudgets.value = []
    quotas.value = []
    transfers.value = []
    return
  }
  loading.value = true
  try {
    const [a, b] = await Promise.all([fundApi.hqBudgets(), fundApi.transfers()])
    hqBudgets.value = (a.data as any[]) || []
    transfers.value = (b.data as any[]) || []
    if (curBudget.value) {
      const q = await fundApi.quotas(curBudget.value.id)
      quotas.value = (q.data as any[]) || []
    }
  } finally {
    loading.value = false
  }
}

function applyMode(mode?: string) {
  if (String(route.query.tab || '') === 'hq') {
    tab.value = canViewHqFund.value ? 'hq' : 'exec'
    page.value = 'list'
    return
  }
  if (mode === 'budget') openBudget()
  else if (mode === 'writeoff') openWriteoff()
  else {
    page.value = 'list'
    tab.value = 'exec'
  }
}

function openBudget() {
  tab.value = 'exec'
  const yearMs = milestones.value.filter((x) => !x.year || Number(x.year) === Number(bYear.value))
  const src = yearMs.length ? yearMs : milestones.value
  bLines.value = src.map((m) => {
    const hit = nodeBudgets.value.find((b) => b.milestoneId === m.id)
    return {
      milestoneId: m.id,
      name: m.name,
      planDate: m.planDate,
      amount: Number(hit?.amount ?? m.budget ?? 0),
      budgetId: hit?.id,
      status: hit?.status,
    }
  })
  page.value = 'budget'
}

function openBudgetView() {
  openBudget()
  syncFundQuery('budget', true)
}

function goFillBudget() {
  if (!requireBudgetReady()) return
  openBudget()
  syncFundQuery('budget', false)
}

function syncFundQuery(mode?: string, view?: boolean) {
  if (!projectId.value) return
  const query: Record<string, string> = { projectId: String(projectId.value) }
  if (mode) query.mode = mode
  if (view) query.view = '1'
  router.replace({ path: '/implement/fund', query })
}

function openWriteoff() {
  if (!requireWriteoffReady()) return
  tab.value = 'exec'
  wItems.value = [{ occurDate: new Date().toISOString().slice(0, 10), amount: 0, name: '', voucherNo: '', fileName: '', fileUrl: '' }]
  const preferred = pendingWriteoffMilestones.value[0] || closedMs.value[0]
  wMsId.value = preferred?.id
  page.value = 'writeoff'
}

function fundPendingScope() {
  const mode = String(route.query.mode || '')
  const desk = String(route.query.desk || '')
  if (mode === 'budget' || desk.includes('budget')) return 'budget'
  if (mode === 'writeoff' || desk === 'writeoff') return 'writeoff'
  return ''
}

async function initProjectFromQueryOrPending() {
  const queryProjectId = Number(route.query.projectId)
  if (queryProjectId) {
    projectId.value = queryProjectId
    return
  }
  if (user.identityCode) {
    if (!pendingStore.fundReviews.length) {
      await pendingStore.loadDeclarationReviews(user.identityCode)
    }
    const scope = fundPendingScope()
    const pending = pendingStore.fundReviews.find((row: any) => !scope || row.mode === scope)
    if (pending?.projectId) {
      projectId.value = Number(pending.projectId)
      router.replace({
        path: '/implement/fund',
        query: {
          ...route.query,
          projectId: String(pending.projectId),
          mode: String(pending.mode),
          desk: String(pending.desk),
        },
      })
      return
    }
  }
  const res = await projectApi.page({ page: 1, size: 200 })
  const records = (((res.data as any)?.records || []) as any[]).filter((row) => !isInternalQaProject(row))
  projectId.value = records[0]?.id
}

onMounted(async () => {
  await initProjectFromQueryOrPending()
  await loadExec()
  loadHq()
  applyMode(String(route.query.mode || ''))
})

watch(() => [route.query.mode, route.query.tab, route.query.desk], () => applyMode(String(route.query.mode || '')))
watch(
  () => route.query.projectId,
  async (v) => {
    const next = Number(v)
    if (!next || next === projectId.value) return
    projectId.value = next
    await loadExec()
    applyMode(String(route.query.mode || ''))
  },
)

const execStat = computed(() => {
  const budget = nodeBudgets.value.filter((x) => x.status === 'APPROVED').reduce((s: number, x: any) => s + (x.amount || 0), 0)
  const paid = payments.value.reduce((s: number, x: any) => s + (x.amount || 0), 0)
  const written = payments.value.filter((x: any) => x.writeoffStatus === 'WRITTEN').reduce((s: number, x: any) => s + (x.amount || 0), 0)
  return { budget, paid, written, rate: budget ? (written / budget) * 100 : 0 }
})

const hqStat = computed(() => {
  const total = curBudget.value?.totalAmount || 0
  const quota = quotas.value.reduce((s: number, x: any) => s + (x.quotaAmount || 0), 0)
  const used = quotas.value.reduce((s: number, x: any) => s + (x.usedAmount || 0), 0)
  return { total, quota, used, remain: quota - used }
})

const yearBudgetSum = computed(() => bLines.value.reduce((s, x) => s + Number(x.amount || 0), 0))

function budgetStatusText(st?: string) {
  return (
    {
      DRAFT: '暂存',
      PENDING: '待二级单位财务审',
      UNIT_OK: '待总部复核备案',
      APPROVED: '已备案',
    } as Record<string, string>
  )[st || ''] || '未填报'
}

async function persistBudgetLines(status: string) {
  for (const line of bLines.value) {
    const payload = {
      projectId: projectId.value,
      year: bYear.value,
      milestoneId: line.milestoneId,
      milestoneName: line.name,
      amount: line.amount,
      status,
    }
    if (line.budgetId) await fundApi.updateBudget(line.budgetId, payload)
    else await fundApi.createBudget(payload)
  }
}

async function saveBudgetDraft() {
  if (!requireBudgetReady()) return
  if (!budgetGuard('fill')) return
  await persistBudgetLines('DRAFT')
  message.success('已暂存，负责人可见并可提交审签')
  await loadExec()
  page.value = 'list'
}

async function submitBudget() {
  if (!requireBudgetReady()) return
  if (!budgetGuard('submit')) return
  if (!bLines.value.length) return message.warning('请先编制里程碑节点')
  await persistBudgetLines('PENDING')
  message.success('已提交：二级单位财务部门审核 → 总部财务团队复核备案')
  await loadExec()
  page.value = 'list'
}

async function auditBudgetUnit(pass: boolean) {
  if (!budgetGuard('audit')) return
  if (!isUnitFin.value) return message.warning('须二级单位财务审核')
  const pending = nodeBudgets.value.filter((b) => b.status === 'PENDING')
  if (!pending.length) return message.warning('暂无待审预算')
  for (const b of pending) {
    await fundApi.updateBudget(b.id, { ...b, status: pass ? 'UNIT_OK' : 'DRAFT' })
  }
  message.success(pass ? '二级单位财务已通过，待总部复核备案' : '已退回项目团队')
  loadExec()
}

async function auditBudgetHq(pass: boolean) {
  if (!budgetGuard('audit')) return
  if (!isHqFin.value) return message.warning('须总部财务复核')
  const pending = nodeBudgets.value.filter((b) => b.status === 'UNIT_OK')
  if (!pending.length) return message.warning('暂无待复核预算')
  for (const b of pending) {
    await fundApi.updateBudget(b.id, { ...b, status: pass ? 'APPROVED' : 'PENDING' })
  }
  message.success(pass ? '总部已复核备案，节点预算生效' : '已退回二级单位财务')
  loadExec()
}

function addWItem() {
  wItems.value.push({ occurDate: new Date().toISOString().slice(0, 10), amount: 0, name: '', voucherNo: '', fileName: '', fileUrl: '' })
}

async function uploadWFile(item: WItem, file: File) {
  const fd = new FormData()
  fd.append('file', file)
  const res = await fileApi.upload(fd, 'fund')
  const d: any = res.data
  item.fileName = d?.fileName || file.name
  item.fileUrl = d?.fileUrl || d?.url || ''
}

async function saveWriteoff(asDraft: boolean) {
  if (savingWriteoff.value) return
  if (!requireWriteoffReady()) return
  if (!wMsId.value) return message.warning('请选择对应里程碑节点')
  if (!asDraft && !writeoffGuard('submit')) return
  if (asDraft && !writeoffGuard('fill')) return
  if (!wItems.value.length) return message.warning('请至少添加一条核销明细')
  const batch = wItems.value.map(it => ({ ...it }))
  for (const it of batch) {
    if (!Number.isFinite(Number(it.amount)) || Number(it.amount) < 0 || (!asDraft && Number(it.amount) <= 0)) {
      return message.warning('核销金额须为有效数字，正式提交须大于零')
    }
    if (!asDraft && (!it.name?.trim() || !it.fileUrl?.trim() || !it.occurDate)) {
      return message.warning('核销日期、用途与材料为必填')
    }
  }
  const batchProjectId = projectId.value
  const batchMilestoneId = wMsId.value
  savingWriteoff.value = true
  try {
    for (const it of batch) {
      await fundApi.createPayment({
      projectId: batchProjectId,
      budgetId: batchMilestoneId,
      flowType: 'WRITEOFF',
      amount: it.amount,
      voucherNo: it.voucherNo,
      occurDate: it.occurDate,
      writeoffStatus: asDraft ? 'DRAFT' : 'WRITTEN',
      operator: user.realName,
      remark: `${it.name}||${it.fileName}||${it.fileUrl}`,
      })
      // 已确认保存的行移出本次表单，后续行失败后重试不会重发这些行。
      wItems.value.shift()
    }
    message.success(asDraft ? '核销信息已暂存' : '二级单位财务已完成本级核销，数据已同步总部经费看板')
    await loadExec()
    page.value = 'list'
  } catch (error: any) {
    if (!isSilentAuthError(error)) message.error('核销未全部保存，已保留未完成的明细；请核对核销记录后重试')
  } finally {
    savingWriteoff.value = false
  }
}

function canProcessPayment(row: any) {
  if (!hasClosedMs.value || row.writeoffStatus === 'WRITTEN' || row.writeoffStatus === 'DRAFT') return false
  if (['PENDING', 'UNIT_OK'].includes(String(row.writeoffStatus || ''))) return isFinHead.value || user.isAdmin
  return false
}

function paymentActionText(row: any) {
  if (['PENDING', 'UNIT_OK'].includes(String(row.writeoffStatus || ''))) return '完成本级核销'
  return '处理'
}

async function unitWriteoff(row: any) {
  if (!requireWriteoffReady()) return
  if (!canProcessPayment(row)) return message.warning('须二级单位财务负责人办理本级核销')
  await fundApi.writeoff(row.id)
  message.success('二级单位财务已完成本级核销，数据已同步总部经费看板')
  loadExec()
}

function isReverseRow(row: any) {
  return String(row.flowType || '') === 'REVERSE'
}

function canReversePayment(row: any) {
  return row.writeoffStatus === 'WRITTEN' && !isReverseRow(row) && (isFinHead.value || user.isAdmin)
}

async function reversePayment(row: any) {
  if (!canReversePayment(row)) return message.warning('须二级单位财务负责人对已核销记录办理红冲')
  Modal.confirm({
    title: '经费核销红冲',
    content: '核销后不可撤销，将生成一条负额红冲记录同步抵减总部经费看板，原记录保留。确认红冲？',
    okText: '确认红冲',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await fundApi.reversePayment(row.id)
      message.success('已生成红冲记录，总部经费看板同步抵减')
      await loadExec()
    },
  })
}

function openFinanceTask(key: string) {
  if (key === 'hqControl') {
    if (!canViewHqFund.value) {
      message.warning('二级单位财务仅可办理本单位经费审核、核销事项')
      return
    }
    tab.value = 'hq'
    page.value = 'list'
    router.replace({ path: '/implement/fund', query: { tab: 'hq', desk: 'hq-budget-control' } })
    return
  }
  if (key === 'writeoff' || key === 'hqWriteoff') {
    openWriteoff()
    router.replace({ path: '/implement/fund', query: { projectId: String(projectId.value || ''), mode: 'writeoff', desk: 'writeoff' } })
    return
  }
  openBudget()
  router.replace({ path: '/implement/fund', query: { projectId: String(projectId.value || ''), mode: 'budget', desk: isHqFin.value ? 'hq-budget' : 'unit-budget' } })
}

function parseRemark(row: any) {
  const parts = String(row.remark || '').split('||')
  return { name: parts[0] || row.remark || '—', fileName: parts[1], fileUrl: parts[2] }
}

function payStatusText(st?: string) {
  if (st === 'WRITTEN') return '已核销/已同步总部看板'
  if (st === 'UNIT_OK') return '待二级单位完成本级核销'
  if (st === 'PENDING') return '待二级单位完成本级核销'
  if (st === 'DRAFT') return '暂存'
  return '待提交'
}

async function saveTransfer() {
  try {
    await fundApi.createTransfer({ ...tForm, budgetId: curBudget.value?.id })
    message.success('拨付申请已提交，待总部科技部与财务部双审')
    transferOpen.value = false
    loadHq()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message)
  }
}
async function auditTransfer(row: any) {
  await fundApi.auditTransfer(row.id, { pass: true, opinion: '同意拨付' })
  message.success('双审通过，拨付完成，台账已更新')
  loadHq()
}

const TRANSFER_STATUS: Record<string, string> = {
  PENDING: '待提交', UNIT_AUDIT: '二级单位审核中', HQ_AUDIT: '总部审核中', PAID: '已拨付', REJECTED: '已驳回',
}

const overview = computed(() => ({
  project: project.value,
  milestones: milestones.value,
  budgets: budgets.value,
  payments: payments.value,
}))

function isInternalQaProject(row: any) {
  const no = String(row?.projectNo || '')
  const name = String(row?.name || '')
  return no.startsWith('QA_') || no.startsWith('QA_MS_AUDIT_') || name.startsWith('QA_')
}

function backList() {
  page.value = 'list'
  router.replace({ path: '/implement/fund', query: projectId.value ? { projectId: String(projectId.value) } : {} })
}
</script>

<template>
  <div class="page-container fund-page">
    <h2 class="page-title">项目经费</h2>
    <div class="page-desc">
      <b>预算按里程碑节点填报，无需等待里程碑闭环；</b>
      经费核销须在对应里程碑节点闭环后办理。依次执行经费预算填报与审批、经费核销；经费数据同步总部看板，异常数据由两级财务联合核查。
    </div>
    <WorkDutyBar v-if="page !== 'writeoff'" code="fund_budget" :project="project" />
    <WorkDutyBar v-if="page === 'writeoff'" code="fund_writeoff" :project="project" />

    <div v-if="project" class="project-summary">
      <div class="project-summary-main">
        <div class="summary-eyebrow">当前项目</div>
        <div class="summary-title">{{ project.projectNo || project.no || '未生成编号' }} · {{ project.name }}</div>
        <div class="summary-meta">
          <a-tag color="blue">{{ project.channelCode || project.channelName || '项目渠道' }}</a-tag>
          <span>负责人：{{ project.ownerName || project.leaderName || '—' }}</span>
          <span>总经费：{{ fmtAmount(project.totalFund) }} 万元</span>
          <span>里程碑：{{ closedMs.length }}/{{ milestones.length }} 已闭环</span>
        </div>
      </div>
      <a-space>
        <a-tag :color="allClosed ? 'green' : hasClosedMs ? 'blue' : 'orange'">{{ allClosed ? '里程碑全部闭环' : hasClosedMs ? '可填报预算/部分节点可核销' : '可填报预算·待节点闭环后核销' }}</a-tag>
        <a-button size="small" @click="flowOpen = true">查看经费流转</a-button>
      </a-space>
    </div>

    <a-card class="fund-main-card" :loading="loading" :body-style="{ padding: '16px 20px' }">
      <a-tabs v-model:activeKey="tab">
        <a-tab-pane key="exec" tab="经费执行管理（项目级）">
          <a-alert
            v-if="!milestones.length"
            type="warning"
            show-icon
            style="margin: 8px 0 12px"
            message="尚未编制里程碑节点"
            description="请先在里程碑管理编制节点；预算按节点绑定填报，核销须在对应节点闭环后办理。"
          />
          <a-alert
            v-else-if="!hasClosedMs"
            type="info"
            show-icon
            style="margin: 8px 0 12px"
            message="可按里程碑节点填报经费预算"
            :description="`经费核销须在对应里程碑节点闭环后办理；当前已闭环 ${closedMs.length}/${milestones.length}。`"
          />
          <a-alert
            v-else
            type="success"
            show-icon
            style="margin: 8px 0 12px"
            :message="allClosed ? '全部里程碑节点已闭环，可办理各节点核销' : `预算填报开放，已闭环节点可办理核销（${closedMs.length}/${milestones.length}）`"
          />
          <div class="toolbar fund-toolbar">
            <a-space>
              <span>选择项目：</span>
              <ProjectSelect v-model="projectId" :disabled="savingWriteoff" @change="loadExec" />
            </a-space>
            <a-space v-if="page === 'list'">
              <a-button @click="openBudgetView">查看填报</a-button>
              <a-button :disabled="!milestones.length" @click="goFillBudget"><PlusOutlined />预算填报</a-button>
              <a-button type="primary" :disabled="!hasClosedMs" @click="openWriteoff"><PlusOutlined />核销信息填报</a-button>
            </a-space>
          </div>

          <!-- 列表 -->
          <template v-if="page === 'list'">
            <div v-if="isFinanceUser" class="finance-desk">
              <div class="finance-head">
                <div>
                  <div class="finance-title">{{ financeRoleProfile.title }}</div>
                  <div class="finance-desc">{{ financeRoleProfile.desc }}</div>
                </div>
                <a-tag color="blue">{{ financeRoleProfile.role }}</a-tag>
              </div>
              <a-row :gutter="[12, 12]">
                <a-col v-for="card in financeTaskCards" :key="card.key" :xs="24" :md="12" :xl="8">
                  <div class="finance-task" :class="{ disabled: card.disabled }">
                    <div>
                      <div class="task-title">{{ card.title }} <a-badge :count="card.count" :overflow-count="99" /></div>
                      <div class="task-desc">{{ card.desc }}</div>
                    </div>
                    <a-button size="small" type="primary" :disabled="card.disabled" @click="openFinanceTask(card.key)">{{ card.action }}</a-button>
                  </div>
                </a-col>
              </a-row>
            </div>
            <div class="fund-flow-design">
              <div v-for="step in financeExecFlow" :key="step.no" class="fund-flow-step" :class="step.state">
                <span class="step-no">{{ step.no }}</span>
                <div class="step-main">
                  <div class="step-head">
                    <b>{{ step.title }}</b>
                    <a-tag :color="step.state === 'done' ? 'green' : step.state === 'current' ? 'blue' : 'default'">{{ step.statusText }}</a-tag>
                  </div>
                  <small>{{ step.desc }}</small>
                </div>
              </div>
            </div>
            <a-row :gutter="[16, 16]" class="stat-row">
              <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card"><div class="label">已备案节点预算(万元)</div><div class="value">{{ fmtAmount(execStat.budget) }}</div></div></a-col>
              <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card"><div class="label">累计核销申报(万元)</div><div class="value">{{ fmtAmount(execStat.paid) }}</div></div></a-col>
              <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card ok"><div class="label">已核销(万元)</div><div class="value">{{ fmtAmount(execStat.written) }}</div></div></a-col>
              <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card"><div class="label">核销率</div><div class="value">{{ execStat.rate.toFixed(1) }}%</div></div></a-col>
            </a-row>

            <a-row :gutter="[16, 16]">
              <a-col :xs="24" :xl="12">
                <div class="sec-hd">
                  <span>节点预算（绑定里程碑）</span>
                  <a-space>
                    <a-button v-if="isUnitFin && nodeBudgets.some(b => b.status === 'PENDING')" size="small" type="primary" @click="auditBudgetUnit(true)">二级财务审核通过</a-button>
                    <a-button v-if="isHqFin && nodeBudgets.some(b => b.status === 'UNIT_OK')" size="small" type="primary" @click="auditBudgetHq(true)">总部复核备案</a-button>
                  </a-space>
                </div>
                <a-table class="fund-table" size="small" row-key="id" :pagination="false" :data-source="nodeBudgets"
                  :columns="[
                    { title: '年度', dataIndex: 'year', width: 80 },
                    { title: '里程碑', dataIndex: 'milestoneName' },
                    { title: '金额(万元)', dataIndex: 'amount', width: 110, align: 'right' },
                    { title: '状态', dataIndex: 'status', width: 140 },
                  ]">
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'amount'"><span class="num-col">{{ fmtAmount(record.amount) }}</span></template>
                    <template v-else-if="column.dataIndex === 'status'">
                      <a-tag :color="record.status === 'APPROVED' ? 'green' : record.status === 'DRAFT' ? 'default' : 'orange'">
                        {{ budgetStatusText(record.status) }}
                      </a-tag>
                    </template>
                  </template>
                </a-table>
              </a-col>
              <a-col :xs="24" :xl="12">
                <div class="sec-hd">付款 / 核销明细</div>
                <a-table class="fund-table" size="small" row-key="id" :pagination="false" :data-source="payments"
                  :columns="[
                    { title: '节点', dataIndex: 'budgetId', width: 80 },
                    { title: '用途', key: 'name' },
                    { title: '金额(万元)', dataIndex: 'amount', width: 100, align: 'right' },
                    { title: '核销', dataIndex: 'writeoffStatus', width: 120 },
                    { title: '操作', key: 'act', width: 110 },
                  ]">
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'amount'"><span class="num-col" :class="{ 'num-red': isReverseRow(record) }">{{ fmtAmount(record.amount) }}</span></template>
                    <template v-else-if="column.key === 'name'">
                      <a-tag v-if="isReverseRow(record)" color="red">红冲</a-tag>{{ parseRemark(record).name }}
                    </template>
                    <template v-else-if="column.dataIndex === 'writeoffStatus'">
                      <a-tag :color="record.writeoffStatus === 'WRITTEN' ? 'green' : record.writeoffStatus === 'UNIT_OK' ? 'blue' : record.writeoffStatus === 'DRAFT' ? 'default' : 'orange'">{{ payStatusText(record.writeoffStatus) }}</a-tag>
                    </template>
                    <template v-else-if="column.key === 'act'">
                      <a-button v-if="canProcessPayment(record)" type="link" size="small" @click="unitWriteoff(record)">{{ paymentActionText(record) }}</a-button>
                      <a-button v-else-if="canReversePayment(record)" type="link" size="small" danger @click="reversePayment(record)">红冲</a-button>
                      <span v-else>—</span>
                    </template>
                  </template>
                </a-table>
              </a-col>
            </a-row>
          </template>

          <!-- 图二 预算填报 -->
          <template v-else-if="page === 'budget'">
            <h3 class="form-title">{{ budgetPageTitle }}</h3>
            <p class="form-hint">{{ budgetPageHint }}</p>
            <div class="flow-banner">全部里程碑闭环 → 项目团队提报 → 二级单位财务部门审核 → 总部财务团队复核备案</div>
            <p class="proj-line">
              {{ project?.projectNo }} {{ project?.name }}　项目总经费 {{ fmtAmount(project?.totalFund) }} 万元。节点预算审核通过后汇总形成本年度预算。
            </p>
            <a-form class="budget-year-form" layout="vertical">
              <a-form-item label="预算年度"><a-input-number v-model:value="bYear" :disabled="budgetReadonly" style="width: 100%" @change="openBudget" /></a-form-item>
            </a-form>
            <a-table class="fund-table" size="small" row-key="milestoneId" :pagination="false" :data-source="bLines"
              :columns="[
                { title: '序号', key: 'idx', width: 70 },
                { title: '里程碑节点', dataIndex: 'name' },
                { title: '计划完成日期', dataIndex: 'planDate', width: 140 },
                { title: '节点预算(万元)', dataIndex: 'amount', width: 180 },
                { title: '状态', dataIndex: 'status', width: 140 },
              ]">
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'idx'">{{ index + 1 }}</template>
                <template v-else-if="column.dataIndex === 'planDate'">{{ fmtDate(record.planDate) }}</template>
                <template v-else-if="column.dataIndex === 'amount'">
                  <a-input-number v-model:value="record.amount" :min="0" :disabled="budgetReadonly" style="width: 100%" />
                </template>
                <template v-else-if="column.dataIndex === 'status'">
                  <a-tag :color="record.status === 'APPROVED' ? 'green' : record.status === 'DRAFT' ? 'default' : record.status ? 'orange' : 'default'">
                    {{ budgetStatusText(record.status) }}
                  </a-tag>
                </template>
              </template>
            </a-table>
            <div class="sum-line">节点数：{{ bLines.length }}　本年度预算合计：<b>{{ fmtAmount(yearBudgetSum) }} 万元</b></div>
            <a-form layout="vertical" style="margin-top: 12px">
              <a-form-item label="预算说明（选填）">
                <a-textarea v-model:value="bRemark" :rows="3" :disabled="budgetReadonly" placeholder="经费范围、用途说明" />
              </a-form-item>
            </a-form>
            <div class="form-actions">
              <a-button @click="backList">返回</a-button>
              <template v-if="hasUnitBudgetReview">
                <a-button danger @click="auditBudgetUnit(false)">退回项目团队</a-button>
                <a-button type="primary" @click="auditBudgetUnit(true)">二级财务审核通过</a-button>
              </template>
              <template v-else-if="hasHqBudgetReview">
                <a-button danger @click="auditBudgetHq(false)">退回二级单位财务</a-button>
                <a-button type="primary" @click="auditBudgetHq(true)">总部复核备案通过</a-button>
              </template>
              <template v-if="!budgetReadonly">
                <a-button @click="saveBudgetDraft">暂存</a-button>
                <a-button type="primary" @click="submitBudget">提交审签</a-button>
              </template>
            </div>
          </template>

          <!-- 图三 核销 -->
          <template v-else-if="page === 'writeoff'">
            <h3 class="form-title">{{ writeoffReadonly ? '查看经费核销' : `${project?.projectNo || ''} ${project?.name || ''} · 经费核销` }}</h3>
            <p class="form-hint">{{ writeoffReadonly ? (!hasClosedMs ? fundLockMessage : '只读查看已填或待填核销明细。') : '选择已闭环的里程碑节点，二级单位财务负责人上传付款凭证并填报核销信息；提交后即完成本级核销，数据自动同步至总部经费看板。' }}</p>
            <div class="flow-banner">对应节点闭环 → 二级单位财务上传付款凭证 → 完成本级核销 → 系统自动同步总部经费看板</div>
            <a-row :gutter="[12, 12]" class="stat-row">
              <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card"><div class="label">节点预算</div><div class="value">{{ wMsId ? fmtAmount(nodeBudgetAmt) : '—' }}</div></div></a-col>
              <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card ok"><div class="label">已核销</div><div class="value">{{ fmtAmount(nodeWrittenAmt) }}</div></div></a-col>
              <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card"><div class="label">审核中</div><div class="value">{{ fmtAmount(nodePendingAmt) }}</div></div></a-col>
              <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card"><div class="label">节点剩余</div><div class="value">{{ wMsId ? fmtAmount(nodeBudgetAmt - nodeWrittenAmt - nodePendingAmt) : '—' }}</div></div></a-col>
            </a-row>
            <a-form layout="vertical">
              <a-row :gutter="16">
                <a-col :xs="24" :md="8">
                  <a-form-item label="预算年度">
                    <a-input :value="String(wYear)" disabled />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="16">
                  <a-form-item label="对应里程碑节点" required>
                    <a-select v-model:value="wMsId" placeholder="请选择本次核销对应的已闭环节点" allow-clear :disabled="writeoffReadonly || savingWriteoff">
                      <a-select-option v-for="m in closedMs" :key="m.id" :value="m.id">
                        {{ m.name }}（已闭环）
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>
            </a-form>
            <div class="sec-hd">本次核销明细</div>
            <div v-for="(it, idx) in wItems" :key="idx" class="wo-card">
              <div class="wo-hd">核销项 {{ idx + 1 }} <a-button v-if="!writeoffReadonly" :disabled="savingWriteoff" type="link" danger size="small" @click="wItems.splice(idx, 1)">删除</a-button></div>
              <a-row :gutter="12" :inert="savingWriteoff ? true : undefined">
                <a-col :xs="24" :md="8"><a-form-item label="费用发生日期" required><a-date-picker v-model:value="it.occurDate" :disabled="writeoffReadonly" style="width: 100%" value-format="YYYY-MM-DD" /></a-form-item></a-col>
                <a-col :xs="24" :md="8"><a-form-item label="核销金额（万元）" required><a-input-number v-model:value="it.amount" :disabled="writeoffReadonly" style="width: 100%" /></a-form-item></a-col>
                <a-col :xs="24" :md="8"><a-form-item label="凭证编号"><a-input v-model:value="it.voucherNo" :disabled="writeoffReadonly" /></a-form-item></a-col>
                <a-col :xs="24" :md="12"><a-form-item label="核销项名称/用途" required><a-input v-model:value="it.name" :disabled="writeoffReadonly" placeholder="如试验件采购、外协测试费" /></a-form-item></a-col>
                <a-col :xs="24" :md="12">
                  <a-form-item label="核销材料（必传）">
                    <a-upload v-if="!writeoffReadonly" :show-upload-list="false" :before-upload="(f: File) => { uploadWFile(it, f); return false }">
                      <a-button>上传</a-button>
                    </a-upload>
                    <span v-if="it.fileName" style="margin-left: 8px">{{ it.fileName }}</span>
                    <span v-else-if="writeoffReadonly">—</span>
                  </a-form-item>
                </a-col>
              </a-row>
            </div>
            <a-button v-if="!writeoffReadonly" :disabled="savingWriteoff" type="dashed" block @click="addWItem">+ 添加核销项</a-button>
            <div class="sum-line">本批共 {{ wItems.length }} 项，合计 {{ fmtAmount(wBatchTotal) }} 万元。{{ wMsId ? '' : '请选择本次核销对应的里程碑节点。' }}</div>
            <div class="form-actions">
              <a-button :disabled="savingWriteoff" @click="backList">返回</a-button>
              <template v-if="!writeoffReadonly">
                <a-button :disabled="savingWriteoff" @click="saveWriteoff(true)">暂存</a-button>
                <a-button type="primary" :loading="savingWriteoff" @click="saveWriteoff(false)">完成本级核销并同步总部看板</a-button>
              </template>
            </div>
            <div class="sec-hd" style="margin-top: 20px">核销记录</div>
            <a-table class="fund-table" size="small" row-key="id" :pagination="false"
              :data-source="payments.filter(p => !wMsId || Number(p.budgetId) === wMsId)"
              :columns="[
                { title: '日期', dataIndex: 'occurDate', width: 120 },
                { title: '金额(万元)', dataIndex: 'amount', width: 120, align: 'right' },
                { title: '用途', key: 'name' },
                { title: '凭证', dataIndex: 'voucherNo', width: 120 },
                { title: '状态', dataIndex: 'writeoffStatus', width: 160 },
                { title: '节点', dataIndex: 'budgetId', width: 80 },
              ]">
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'occurDate'">{{ fmtDate(record.occurDate) }}</template>
                <template v-else-if="column.dataIndex === 'amount'"><span class="num-col">{{ fmtAmount(record.amount) }}</span></template>
                <template v-else-if="column.key === 'name'">{{ parseRemark(record).name }}</template>
                <template v-else-if="column.dataIndex === 'writeoffStatus'">
                  <a-tag :color="record.writeoffStatus === 'WRITTEN' ? 'green' : record.writeoffStatus === 'UNIT_OK' ? 'blue' : record.writeoffStatus === 'DRAFT' ? 'default' : 'orange'">{{ payStatusText(record.writeoffStatus) }}</a-tag>
                </template>
              </template>
              <template #emptyText>暂无核销记录</template>
            </a-table>
          </template>

        </a-tab-pane>

        <a-tab-pane v-if="canViewHqFund" key="hq" tab="总部经费预算管控">
          <div class="flow-banner">总部经费预算管控独立于项目端核销：总部财务维护年度总盘子与二级单位额度，拨付申请经总部科技部与财务双审后进入拨付台账。</div>
          <div class="toolbar fund-toolbar">
            <a-space>
              <span>年度：</span>
              <a-select v-model:value="hqYear" style="width: 120px" @change="loadHq">
                <a-select-option v-for="b in hqBudgets" :key="b.year" :value="b.year">{{ b.year }}</a-select-option>
              </a-select>
              <a-tag :color="curBudget?.status === 'LOCKED' ? 'green' : 'orange'">
                {{ curBudget?.status === 'LOCKED' ? '已锁定' : '编制中' }}
              </a-tag>
            </a-space>
            <a-button type="primary" @click="transferOpen = true"><PlusOutlined />经费拨付申请</a-button>
          </div>
          <a-row :gutter="[16, 16]" class="stat-row">
            <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card"><div class="label">年度总盘子(万元)</div><div class="value">{{ fmtAmount(hqStat.total) }}</div></div></a-col>
            <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card"><div class="label">核定额度合计(万元)</div><div class="value">{{ fmtAmount(hqStat.quota) }}</div></div></a-col>
            <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card"><div class="label">累计已拨付(万元)</div><div class="value">{{ fmtAmount(hqStat.used) }}</div></div></a-col>
            <a-col :xs="24" :sm="12" :xl="6"><div class="stat-card ok"><div class="label">剩余可用(万元)</div><div class="value">{{ fmtAmount(hqStat.remain) }}</div></div></a-col>
          </a-row>
          <div class="sec-hd">各二级单位拨付额度</div>
          <a-table class="fund-table" size="small" row-key="id" :pagination="false" :data-source="quotas" style="margin-bottom: 20px"
            :columns="[
              { title: '单位', dataIndex: 'orgName' },
              { title: '额度上限(万元)', dataIndex: 'quotaAmount', width: 140, align: 'right' },
              { title: '已拨付(万元)', dataIndex: 'usedAmount', width: 140, align: 'right' },
              { title: '剩余(万元)', key: 'remain', width: 140, align: 'right' },
              { title: '执行率', key: 'rate', width: 200 },
            ]">
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'quotaAmount'"><span class="num-col">{{ fmtAmount(record.quotaAmount) }}</span></template>
              <template v-else-if="column.dataIndex === 'usedAmount'"><span class="num-col">{{ fmtAmount(record.usedAmount) }}</span></template>
              <template v-else-if="column.key === 'remain'"><span class="num-col">{{ fmtAmount((record.quotaAmount || 0) - (record.usedAmount || 0)) }}</span></template>
              <template v-else-if="column.key === 'rate'">
                <a-progress :percent="Math.round(((record.usedAmount || 0) / (record.quotaAmount || 1)) * 100)" size="small" />
              </template>
            </template>
          </a-table>
          <div class="sec-hd">拨付执行台账</div>
          <a-table class="fund-table" size="small" row-key="id" :pagination="{ pageSize: 6 }" :data-source="transfers"
            :columns="[
              { title: '申请单号', dataIndex: 'applyNo', width: 140 },
              { title: '单位', dataIndex: 'orgName', width: 200 },
              { title: '金额(万元)', dataIndex: 'amount', width: 120, align: 'right' },
              { title: '事由', dataIndex: 'reason' },
              { title: '申请日期', dataIndex: 'applyAt', width: 120 },
              { title: '状态', dataIndex: 'status', width: 130 },
              { title: '操作', key: 'act', width: 80 },
            ]">
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'amount'"><span class="num-col">{{ fmtAmount(record.amount) }}</span></template>
              <template v-else-if="column.dataIndex === 'applyAt'">{{ fmtDate(record.applyAt) }}</template>
              <template v-else-if="column.dataIndex === 'status'">
                <a-tag :color="record.status === 'PAID' ? 'green' : record.status === 'REJECTED' ? 'red' : 'processing'">
                  {{ TRANSFER_STATUS[record.status] }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'act'">
                <a-button type="link" size="small" :disabled="record.status === 'PAID'" @click="auditTransfer(record)">双审</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <a-modal v-model:open="transferOpen" title="总部经费拨付申请" @ok="saveTransfer">
      <a-form layout="vertical">
        <a-form-item label="拨付单位（额度）">
          <a-select v-model:value="tForm.quotaId" placeholder="在核定年度额度内申请">
            <a-select-option v-for="q in quotas" :key="q.id" :value="q.id">
              {{ q.orgName }}（剩余 {{ fmtAmount((q.quotaAmount || 0) - (q.usedAmount || 0)) }} 万元）
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="拨付金额（万元）"><a-input-number v-model:value="tForm.amount" style="width: 100%" /></a-form-item>
        <a-form-item label="事由"><a-textarea v-model:value="tForm.reason" :rows="3" /></a-form-item>
      </a-form>
    </a-modal>
    <ImplementFlowDialog v-model:open="flowOpen" :overview="overview" />
  </div>
</template>

<style scoped>
.num-red {
  color: #cf1322;
}
.fund-page {
  background:
    radial-gradient(circle at 14% 0%, rgba(22, 119, 255, 0.08), transparent 28%),
    linear-gradient(180deg, #f7fbff 0%, #f4f6f9 220px, transparent 360px);
}
.project-summary {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin: 14px 0 16px;
  padding: 18px 20px;
  border: 1px solid #d6e8ff;
  border-radius: 12px;
  background:
    linear-gradient(135deg, rgba(22, 119, 255, 0.1), rgba(255, 255, 255, 0.95) 46%),
    #fff;
  box-shadow: 0 8px 22px rgba(0, 35, 90, 0.06);
}
.project-summary-main {
  min-width: 0;
}
.summary-eyebrow {
  margin-bottom: 4px;
  font-size: 12px;
  color: #1677ff;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.summary-title {
  color: #1f1f1f;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
}
.summary-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 10px;
  color: #596579;
  font-size: 13px;
}
.fund-main-card {
  border: 1px solid #e6eef8;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(15, 45, 95, 0.05);
  overflow: hidden;
}
.fund-main-card :deep(.ant-card-body) {
  background: #fff;
}
.fund-toolbar {
  align-items: center;
  margin-top: 8px;
  padding: 12px;
  border: 1px solid #edf2f7;
  border-radius: 10px;
  background: #fbfdff;
}
.stat-row {
  margin-bottom: 16px;
}
.stat-row .stat-card {
  min-height: 92px;
  border-radius: 10px;
  background: linear-gradient(180deg, #fff, #fbfdff);
  box-shadow: 0 4px 16px rgba(15, 45, 95, 0.04);
}
.flow-banner {
  margin-bottom: 16px;
  padding: 11px 14px;
  background: linear-gradient(90deg, #e6f4ff, #f6fbff);
  border: 1px solid #91caff;
  border-radius: 8px;
  color: #003a8c;
  font-size: 13px;
}
.form-title { margin: 8px 0 8px; font-size: 18px; color: #1f1f1f; font-weight: 700; }
.form-hint { color: #6b7280; font-size: 13px; margin-bottom: 12px; line-height: 1.7; }
.proj-line {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fafafa;
  color: #262626;
}
.sum-line {
  margin: 12px 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fafafa;
  color: #262626;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  padding: 12px;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.92);
}
.sec-hd {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  color: #1f2a44;
  font-weight: 700;
}
.budget-year-form {
  max-width: 240px;
}
.wo-card {
  border: 1px solid #d6e8ff;
  border-radius: 10px;
  padding: 14px 14px 2px;
  margin-bottom: 12px;
  background: linear-gradient(180deg, #fbfdff, #fff);
}
.wo-hd {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  margin-bottom: 12px;
  border-bottom: 1px dashed #dbe7f5;
  color: #1f2a44;
  font-weight: 700;
}
.finance-desk {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid #b8d9ff;
  border-radius: 12px;
  background:
    linear-gradient(135deg, rgba(22, 119, 255, 0.13), rgba(255, 255, 255, 0.78) 52%),
    #f0f7ff;
}
.finance-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 12px;
}
.finance-title { font-size: 16px; font-weight: 800; color: #003a8c; }
.finance-desc { margin-top: 4px; font-size: 12px; color: #4b5563; line-height: 1.6; }
.finance-task {
  min-height: 112px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 14px;
  border: 1px solid #d9e8ff;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(0, 72, 160, 0.05);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}
.finance-task:not(.disabled):hover {
  border-color: #69b1ff;
  box-shadow: 0 8px 22px rgba(0, 72, 160, 0.12);
  transform: translateY(-1px);
}
.finance-task.disabled { opacity: 0.65; }
.task-title { display: flex; align-items: center; justify-content: space-between; font-weight: 600; color: #262626; }
.task-desc { margin-top: 6px; min-height: 34px; font-size: 12px; color: #8c8c8c; }
.fund-flow-design {
  display: grid;
  grid-template-columns: repeat(6, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}
.fund-flow-step {
  position: relative;
  display: flex;
  gap: 10px;
  min-height: 94px;
  padding: 12px;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  background: linear-gradient(180deg, #fff, #fafafa);
}
.fund-flow-step.done { border-color: #b7eb8f; background: linear-gradient(180deg, #f6ffed, #fff); }
.fund-flow-step.current { border-color: #91caff; background: linear-gradient(180deg, #e6f4ff, #fff); box-shadow: inset 0 0 0 1px rgba(22, 119, 255, 0.12); }
.step-no {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 50%;
  background: #d9d9d9;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
}
.fund-flow-step.done .step-no { background: #52c41a; }
.fund-flow-step.current .step-no { background: #1677ff; }
.step-main { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.step-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.step-main b { color: #262626; }
.step-main small { color: #8c8c8c; line-height: 1.35; }
.fund-table {
  border: 1px solid #eef2f7;
  border-radius: 10px;
  overflow: hidden;
}
.fund-table :deep(.ant-table-thead > tr > th) {
  background: #f8fafc !important;
}
.fund-table :deep(.ant-table-tbody > tr:hover > td) {
  background: #f5f9ff !important;
}
@media (max-width: 1400px) {
  .fund-flow-design { grid-template-columns: repeat(3, minmax(160px, 1fr)); }
}
@media (max-width: 900px) {
  .project-summary {
    flex-direction: column;
  }
  .fund-flow-design {
    grid-template-columns: 1fr;
  }
  .form-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
