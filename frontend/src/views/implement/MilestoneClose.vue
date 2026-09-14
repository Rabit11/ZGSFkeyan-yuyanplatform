<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { deliverableApi, fileApi, milestoneApi, projectApi } from '@/api/modules'
import { isSilentAuthError } from '@/api/request'
import { dueText, fmtDate } from '@/utils/format'
import { useDictStore } from '@/stores/dict'
import StatusTag from '@/components/StatusTag.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'
import { useUserStore } from '@/stores/user'
import { downloadAuthenticatedFile } from '@/utils/authFile'
import type { WorkDutyCode } from '@/constants/workDuty'

const OWNERS = ['公司', '各单位', '参研单位', '外协单位']
const TYPES = [
  { value: 'PATENT', label: '专利' },
  { value: 'PAPER', label: '论文' },
  { value: 'SOFTWARE', label: '软著' },
  { value: 'STANDARD', label: '技术标准' },
  { value: 'PROTOTYPE', label: '原理样机' },
  { value: 'EQUIPMENT', label: '设备' },
  { value: 'TECH_PACKAGE', label: '成套技术成果' },
  { value: 'REPORT', label: '报告' },
  { value: 'OTHER', label: '其他' },
]
const QUICK_TYPES = ['PATENT', 'PAPER', 'STANDARD', 'PROTOTYPE']

async function openMaterial(fileUrl?: string, fileName?: string) {
  try {
    await downloadAuthenticatedFile(fileUrl, fileName)
  } catch (e: any) {
    message.error(e?.message || '附件下载失败')
  }
}

type CompileNode = {
  key: string
  id?: number
  name: string
  planDate: string
  locked: boolean
}

type CompileDv = {
  key: string
  id?: number
  nodeKey: string
  deliverType: string
  name: string
  fileName?: string
  fileUrl?: string
  locked: boolean
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const project = ref<any>(null)
const milestones = ref<any[]>([])
const deliverables = ref<any[]>([])
const focusId = ref<number>()
const evidenceName = reactive<Record<string, string>>({})
const evidenceFile = reactive<Record<string, { fileName?: string; fileUrl?: string }>>({})
const draft = reactive<Record<string, { deliverType: string; name: string; owners: string[]; dueDate: string; achievementNo: string }>>({})

const dictStore = useDictStore()
const user = useUserStore()
const projectId = computed(() => Number(route.query.projectId || route.query.id || 0) || undefined)
const isCompile = computed(() => String(route.query.mode || '') === 'compile')
const dutyCode = computed<WorkDutyCode>(() => (isCompile.value ? 'milestone_compile' : 'milestone_close'))
const { can, guard } = useWorkDuty(dutyCode, project)
const newMsOpen = ref(false)
const newMs = reactive({ name: '', planDate: '', budget: 0 })
const compileSaving = ref(false)
const compileYear = ref(new Date().getFullYear())
const annualGoal = ref('')
const compileNodes = ref<CompileNode[]>([])
const compileDvs = ref<CompileDv[]>([])
let compileSeq = 0
const activeAnnualPlan = computed(() => {
  const plans = project.value?.annualPlans || []
  return plans.find((p: any) => Number(p.year) === compileYear.value) || null
})
const annualStatus = computed(() => String(activeAnnualPlan.value?.finishStatus || ''))
const compilePendingAudit = computed(() => annualStatus.value === 'PENDING_AUDIT')
const compileArchived = computed(() => annualStatus.value === 'DONE')
const compileReturned = computed(() => annualStatus.value === 'RETURN')
const canCompileEdit = computed(() => !compilePendingAudit.value && (can.value.fill || can.value.edit || can.value.submit))
const compileReadonly = computed(() => isCompile.value && !canCompileEdit.value)
const canAuditCompile = computed(() => isCompile.value && compilePendingAudit.value && can.value.audit)
const canCloseOperate = computed(() => !isCompile.value && currentUserIsProjectOwner(project.value))
const compileStatusType = computed(() => compilePendingAudit.value ? 'warning' : compileReturned.value ? 'error' : compileArchived.value ? 'success' : 'info')
const compileStatusText = computed(() => {
  if (compilePendingAudit.value) return '里程碑节点与交付物清单已提交审查，当前流转到二级单位科技部门负责人。审核期间填报项锁定。'
  if (compileReturned.value) return '里程碑节点与交付物清单已被驳回，请负责人修改后重新提交审查。'
  if (compileArchived.value) return '里程碑节点与交付物清单已审核归档，后续可按节点上传交付证明并销项。'
  return '请填写年度目标、里程碑节点和各节点交付物清单，完成后提交二级单位科技部门审核。'
})

const typeOptions = computed(() => {
  const fromDict = dictStore.options('DELIVERABLE_TYPE')
  return fromDict.length ? fromDict : TYPES
})

const quickTypeChips = computed(() => {
  const all = typeOptions.value
  const preferred = QUICK_TYPES.map((v) => all.find((t) => t.value === v)).filter(Boolean) as { value: string; label: string }[]
  const rest = all.filter((t) => !QUICK_TYPES.includes(String(t.value)))
  return preferred.length ? [...preferred, ...rest] : all
})

function digitsOnly(value?: string | number) {
  return String(value || '').replace(/\D/g, '')
}

function sameCurrentPerson(employeeNo?: string | number, name?: string) {
  const myNo = digitsOnly(user.employeeNo)
  const targetNo = digitsOnly(employeeNo)
  if (myNo && targetNo && myNo === targetNo) return true
  const nameNo = digitsOnly(name)
  if (myNo && nameNo && nameNo.includes(myNo)) return true
  const myName = String(user.realName || '').trim()
  const targetName = String(name || '').trim()
  return !!myName && !!targetName && (myName === targetName || targetName.includes(myName))
}

function findProjectMember(keys: string[]) {
  const members = project.value?.teamMembers || project.value?.members || []
  return members.find((m: any) => {
    const code = String(m.roleCode || '')
    const role = String(m.roleName || '')
    return keys.some((key) => code === key || role === key || role.includes(key))
  })
}

function isProjectOwnerMember(m: any) {
  const code = String(m?.roleCode || '')
  const role = String(m?.roleName || '')
  return code === 'PROJECT_LEADER' || role === '项目负责人' || role.includes('项目负责人')
}

function currentUserIsProjectOwner(target: any) {
  if (!target) return false
  const members = target.teamMembers || target.members || []
  const ownerMatched = members.some((m: any) => {
    return isProjectOwnerMember(m) && sameCurrentPerson(m.employeeNo, m.userName || m.realName || m.name || m.label)
  })
  if (ownerMatched) return true
  const ownerText = String(target.ownerName || '')
  const myNo = digitsOnly(user.employeeNo)
  const myName = String(user.realName || '').trim()
  return (!!myNo && digitsOnly(ownerText).includes(myNo)) || (!!myName && ownerText.includes(myName))
}

function isCloseAuditStatus(status?: string) {
  return status === 'CLOSE_DEPT_AUDIT' || status === 'CLOSE_UNIT_AUDIT'
}

function canCloseUpload(ms?: any) {
  return canCloseOperate.value && !isCloseAuditStatus(ms?.status)
}

function closeAuditNode(ms: any) {
  if (ms?.status === 'CLOSE_DEPT_AUDIT') return '项目承担部门负责人审核'
  if (ms?.status === 'CLOSE_UNIT_AUDIT') return '单位科研管理部门负责人审核'
  return ''
}

function canAuditCloseMilestone(ms: any) {
  if (!ms) return false
  if (ms.status === 'CLOSE_DEPT_AUDIT') {
    const handler = findProjectMember(['DEPT_HEAD', '项目承担部门负责人'])
    if (handler) return sameCurrentPerson(handler.employeeNo, handler.userName || handler.realName || handler.name)
    return user.identityCode === 'deptHead'
  }
  if (ms.status === 'CLOSE_UNIT_AUDIT') {
    const handler = findProjectMember(['UNIT_MINISTER', '单位科技部长', '单位科研管理部门负责人'])
    if (handler) return sameCurrentPerson(handler.employeeNo, handler.userName || handler.realName || handler.name)
    return user.identityCode === 'unitHead'
  }
  return false
}

function requireCloseOwner(ms?: any) {
  if (!canCloseOperate.value) {
    message.warning('仅项目团队负责人可上传销项材料')
    return false
  }
  if (isCloseAuditStatus(ms?.status)) {
    message.warning('该节点销项已提交审核，请等待审核结果')
    return false
  }
  return true
}
function typeLabel(code?: string) {
  if (!code) return '—'
  const fromDict = dictStore.label('DELIVERABLE_TYPE', code)
  if (fromDict && fromDict !== '-' && fromDict !== code) return fromDict
  return TYPES.find((t) => t.value === code)?.label || code
}

function newCompileKey(prefix = 'row') {
  compileSeq += 1
  return `${prefix}-${compileSeq}`
}

function emptyNode(): CompileNode {
  return { key: newCompileKey('ms'), name: '', planDate: '', locked: false }
}

function emptyDv(nodeKey = ''): CompileDv {
  return { key: newCompileKey('dv'), nodeKey, deliverType: '', name: '', locked: false }
}

function yearOf(m: any) {
  const y = Number(m?.year)
  return Number.isFinite(y) && y > 0 ? y : undefined
}

function yearMilestones() {
  return milestones.value.filter((m) => {
    const y = yearOf(m)
    return y == null || y === compileYear.value
  })
}

const periodText = computed(() => {
  const s = project.value?.startDate
  const e = project.value?.endDate
  if (!s && !e) return ''
  return `${fmtDate(s)} - ${fmtDate(e)}`
})

function nodeNo(index: number) {
  return index + 1
}

const nodeOptions = computed(() =>
  compileNodes.value.map((n, i) => ({
    value: n.key,
    label: `${i + 1}　${n.name.trim() || '未命名节点'}`,
  })),
)

const nodeDvStats = computed(() =>
  compileNodes.value.map((n, i) => {
    const count = compileDvs.value.filter((d) => d.nodeKey === n.key && (d.name.trim() || d.deliverType)).length
    return {
      key: n.key,
      label: `${i + 1}-${count}项交付物`,
      count,
    }
  }),
)

function dvDue(row: CompileDv) {
  return compileNodes.value.find((n) => n.key === row.nodeKey)?.planDate || ''
}

function hydrateAnnualGoal() {
  const plans = project.value?.annualPlans || []
  const plan = plans.find((p: any) => Number(p.year) === compileYear.value) || plans[0]
  annualGoal.value = plan?.annualGoal || project.value?.annualGoal || ''
}

function bindDvToNode(d: any, nodes: CompileNode[]) {
  if (d.milestoneId) {
    const hit = nodes.find((n) => Number(n.id) === Number(d.milestoneId))
    if (hit) return hit.key
  }
  const due = String(d.dueDate || '').slice(0, 10)
  if (due) {
    const hit = nodes.find((n) => n.planDate === due)
    if (hit) return hit.key
  }
  return nodes[0]?.key || ''
}

function hydrateCompile() {
  hydrateAnnualGoal()
  const yearMs = yearMilestones()
  if (!yearMs.length) {
    compileNodes.value = [emptyNode(), emptyNode(), emptyNode()]
    compileDvs.value = [emptyDv(compileNodes.value[0].key)]
    return
  }
  const nodes = yearMs.map((m) => ({
    key: `ms-${m.id}`,
    id: m.id,
    name: m.name || '',
    planDate: String(m.planDate || '').slice(0, 10),
    locked: m.status === 'DONE' || m.colorStatus === 'GREEN',
  }))
  compileNodes.value = nodes
  const yearMsIds = new Set(yearMs.map((m) => Number(m.id)))
  const yearDates = new Set(nodes.map((n) => n.planDate).filter(Boolean))
  const dvs = deliverables.value.filter((d) => {
    if (d.milestoneId && yearMsIds.has(Number(d.milestoneId))) return true
    if (d.milestoneId) return false
    const due = String(d.dueDate || '').slice(0, 10)
    return due && yearDates.has(due)
  })
  compileDvs.value = dvs.length
    ? dvs.map((d) => ({
        key: `dv-${d.id}`,
        id: d.id,
        nodeKey: bindDvToNode(d, nodes),
        deliverType: d.deliverType || '',
        name: d.name || '',
        fileName: d.fileName,
        fileUrl: d.fileUrl,
        locked: d.status === 'DELIVERED' || Boolean(d.fileUrl && d.status === 'DELIVERED'),
      }))
    : [emptyDv(nodes[0]?.key || '')]
}

function addCompileNode() {
  const node = emptyNode()
  compileNodes.value = [...compileNodes.value, node]
  compileDvs.value = [...compileDvs.value, emptyDv(node.key)]
}

function removeCompileNode(row: CompileNode) {
  if (row.locked) {
    message.warning('已销项节点不可删除')
    return
  }
  const bound = compileDvs.value.filter((d) => d.nodeKey === row.key)
  if (bound.some((d) => d.locked)) {
    message.warning('该节点下已有提交证明的交付物，不可删除')
    return
  }
  Modal.confirm({
    title: '移除该节点？',
    content: row.id ? '提交审查后将从本年度节点中删除，其下未销项交付物一并移除。' : '仅从当前编制表中移除。',
    onOk: () => {
      compileNodes.value = compileNodes.value.filter((r) => r.key !== row.key)
      compileDvs.value = compileDvs.value.filter((d) => d.nodeKey !== row.key)
    },
  })
}

function addCompileDv(type?: string, nodeKey?: string) {
  const key = nodeKey || compileNodes.value[0]?.key || ''
  compileDvs.value = [...compileDvs.value, { ...emptyDv(key), deliverType: type || '' }]
}

function compileDvsOfNode(node: CompileNode) {
  return compileDvs.value.filter((d) => d.nodeKey === node.key)
}

function removeCompileDv(row: CompileDv) {
  if (row.locked) {
    message.warning('已提交证明的交付物不可删除')
    return
  }
  compileDvs.value = compileDvs.value.filter((r) => r.key !== row.key)
}

function autoFillDvByNode() {
  const missing = compileNodes.value.filter((n) => !compileDvs.value.some((d) => d.nodeKey === n.key))
  if (!missing.length) {
    message.info('各节点已有交付物行，如需增补请点「增加交付物」')
    return
  }
  compileDvs.value = [...compileDvs.value, ...missing.map((n) => emptyDv(n.key))]
}

async function pickDvFile(row: CompileDv, file: File) {
  try {
    const uploaded = await uploadFile(file)
    row.fileName = uploaded.fileName
    row.fileUrl = uploaded.fileUrl
    message.success(`已选择：${uploaded.fileName}`)
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '上传失败')
  }
  return false
}

function createdId(res: any): number | undefined {
  const raw = res?.data ?? res
  const n = Number(raw)
  return Number.isFinite(n) && n > 0 ? n : undefined
}

async function saveCompile() {
  if (!guard('submit')) return
  if (!projectId.value) {
    message.warning('缺少项目编号，请从实施详情进入')
    return
  }
  const nodes = compileNodes.value.filter((r) => r.name.trim() || r.planDate || r.id)
  if (!nodes.length) {
    message.warning('请至少编制 1 个里程碑节点')
    return
  }
  for (let i = 0; i < nodes.length; i++) {
    const r = nodes[i]
    if (!r.name.trim() || !r.planDate) {
      message.warning(`请完整填写第 ${i + 1} 个节点的名称和计划完成日期`)
      return
    }
  }
  const dvs = compileDvs.value.filter((d) => d.name.trim() || d.deliverType || d.id)
  if (!dvs.length) {
    message.warning('请至少编制 1 项本年度交付物')
    return
  }
  for (let i = 0; i < dvs.length; i++) {
    const d = dvs[i]
    if (!d.deliverType || !d.name.trim() || !d.nodeKey) {
      message.warning(`请完整填写第 ${i + 1} 项交付物的类型、名称和对应节点`)
      return
    }
    if (!dvDue(d)) {
      message.warning(`第 ${i + 1} 项交付物对应节点尚未填写计划完成日期`)
      return
    }
  }
  compileSaving.value = true
  try {
    await projectApi.saveAnnualPlan(projectId.value, {
      year: compileYear.value,
      annualGoal: annualGoal.value,
      finishStatus: 'PENDING_AUDIT',
      colorStatus: 'YELLOW',
    })
    const keepIds = new Set(nodes.map((r) => r.id).filter(Boolean) as number[])
    for (const m of yearMilestones()) {
      if (m.status === 'DONE' || m.colorStatus === 'GREEN') continue
      if (keepIds.has(Number(m.id))) continue
      for (const d of dvsOf(m)) {
        if (d.status !== 'DELIVERED' && !d.fileUrl) await deliverableApi.remove(d.id)
      }
      await milestoneApi.remove(m.id)
    }
    const year = compileYear.value
    for (const r of nodes) {
      const payload = {
        projectId: projectId.value,
        name: r.name.trim(),
        year,
        planDate: r.planDate,
      }
      if (r.id) {
        if (!r.locked) await milestoneApi.update(r.id, payload)
      } else {
        const created = await milestoneApi.create({ ...payload, budget: 0, status: 'DOING' })
        r.id = createdId(created)
      }
    }
    const keyToId = new Map(nodes.filter((n) => n.id).map((n) => [n.key, n.id as number]))
    const keepDvIds = new Set(dvs.map((d) => d.id).filter(Boolean) as number[])
    for (const old of deliverables.value) {
      if (old.status === 'DELIVERED') continue
      const bound = nodes.some((n) => Number(old.milestoneId) === Number(n.id))
        || (!old.milestoneId && nodes.some((n) => n.planDate && n.planDate === String(old.dueDate || '').slice(0, 10)))
      if (bound && old.id && !keepDvIds.has(Number(old.id))) {
        await deliverableApi.remove(old.id)
      }
    }
    for (const d of dvs) {
      const milestoneId = keyToId.get(d.nodeKey)
      if (!milestoneId) continue
      const dueDate = dvDue(d)
      const old = deliverables.value.find((x) => x.id === d.id)
      const dvBody: any = {
        projectId: projectId.value,
        milestoneId,
        name: d.name.trim(),
        deliverType: d.deliverType,
        dueDate,
        ownerOrgs: old?.ownerOrgs || '公司',
        status: old?.status === 'DELIVERED' ? 'DELIVERED' : 'PENDING',
        fileName: d.fileName || old?.fileName,
        fileUrl: d.fileUrl || old?.fileUrl,
      }
      if (d.id && old) {
        await deliverableApi.update(d.id, { ...old, ...dvBody })
      } else {
        const created = await deliverableApi.create(dvBody)
        d.id = createdId(created)
      }
    }
    message.success('已提交审查，等待二级单位科技部门审核存档')
    await router.push('/implement/milestone')
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '提交失败')
  } finally {
    compileSaving.value = false
  }
}


async function auditCompile(pass: boolean) {
  if (!guard('audit')) return
  if (!projectId.value) {
    message.warning('缺少项目编号，请从实施详情进入')
    return
  }
  Modal.confirm({
    title: pass ? '确认审核通过并归档？' : '确认驳回给负责人修改？',
    content: pass
      ? '通过后，该项目本年度里程碑节点与交付物清单进入归档状态，并开放后续节点交付证明上传。'
      : '驳回后，项目负责人、技术负责人可重新修改清单并再次提交审查。',
    onOk: async () => {
      try {
        await milestoneApi.auditAnnualPlan(projectId.value!, { year: compileYear.value, pass })
        message.success(pass ? '已审核通过并归档' : '已驳回，等待负责人修改')
        await router.push('/implement/milestone')
      } catch (e: any) {
        if (!isSilentAuthError(e)) message.error(e.message || '审核失败')
      }
    },
  })
}

function proved(row: any) {
  return row.status === 'DELIVERED' || Boolean(row.fileUrl || row.fileName)
}

function dvsOf(ms: any) {
  const bound = deliverables.value.filter((d) => Number(d.milestoneId) === Number(ms.id))
  if (bound.length) return bound
  const plan = String(ms.planDate || '').slice(0, 10)
  return deliverables.value.filter((d) => !d.milestoneId && String(d.dueDate || '').slice(0, 10) === plan)
}

function remainProof(ms: any) {
  return dvsOf(ms).filter((d) => !proved(d)).length
}

function msStatusText(ms: any) {
  if (ms.status === 'CLOSE_DEPT_AUDIT') return '待项目承担部门负责人审核'
  if (ms.status === 'CLOSE_UNIT_AUDIT') return '待单位科研管理部门负责人审核'
  if (ms.status === 'DONE' || ms.colorStatus === 'GREEN') return '已销项'
  if (ms.status === 'OVERDUE' || ms.colorStatus === 'RED') return '待销项 · 逾期'
  return '待销项'
}

const stats = computed(() => {
  const dvs = deliverables.value
  const passed = dvs.filter((d) => d.status === 'DELIVERED').length
  const auditing = dvs.filter((d) => d.status === 'PENDING' && d.fileUrl).length + milestones.value.filter((m) => isCloseAuditStatus(m.status)).length
  const closed = milestones.value.filter((m) => m.status === 'DONE' || m.colorStatus === 'GREEN').length
  const rate = dvs.length ? Math.round((passed / dvs.length) * 100) : 0
  return { passed, totalDv: dvs.length, auditing, closed, totalMs: milestones.value.length, rate }
})

function ensureDraft(msId: string | number) {
  const key = String(msId)
  if (!draft[key]) {
    draft[key] = { deliverType: 'PATENT', name: '', owners: ['公司'], dueDate: '', achievementNo: '' }
  }
  return draft[key]
}

async function load() {
  if (!projectId.value) {
    message.warning('缺少项目编号，请从实施详情进入')
    return
  }
  loading.value = true
  try {
    const [ov, dres] = await Promise.all([
      projectApi.overview(projectId.value),
      deliverableApi.list(projectId.value),
    ])
    const data = (ov.data || {}) as any
    project.value = data.project || data
    milestones.value = [...(data.milestones || project.value?.milestones || [])].sort((a, b) =>
      String(a.planDate || '').localeCompare(String(b.planDate || '')),
    )
    deliverables.value = (dres.data as any[]) || []
    const qid = Number(route.query.milestoneId)
    focusId.value = qid || milestones.value.find((m) => m.status !== 'DONE' && m.colorStatus !== 'GREEN')?.id
    for (const m of milestones.value) {
      ensureDraft(m.id)
      const mats = m.materials || []
      const ev = mats.find((x: any) => x.fieldCode === 'EVIDENCE') || mats[0]
      if (ev) {
        evidenceName[String(m.id)] = ev.fieldName || evidenceName[String(m.id)] || ''
        evidenceFile[String(m.id)] = { fileName: ev.fileName, fileUrl: ev.fileUrl }
      }
    }
    if (isCompile.value) hydrateCompile()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || (isCompile.value ? '加载清单失败' : '加载销项页失败'))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await dictStore.load('DELIVERABLE_TYPE')
  await load()
})
watch(() => [route.query.projectId, route.query.id, route.query.milestoneId, route.query.mode], load)
watch(compileYear, () => {
  if (isCompile.value && project.value) hydrateCompile()
})

async function uploadFile(file: File) {
  const data = new FormData()
  data.append('file', file)
  const uploaded = (await fileApi.upload(data, 'evidence')).data as any
  return uploaded
}

async function submitProof(row: any, file: File, ms?: any) {
  if (!requireCloseOwner(ms)) return
  try {
    const uploaded = await uploadFile(file)
    await deliverableApi.update(row.id, {
      ...row,
      fileName: uploaded.fileName,
      fileUrl: uploaded.fileUrl,
      status: 'DELIVERED',
      deliverDate: new Date().toISOString().slice(0, 10),
    })
    message.success(`已提交证明：${uploaded.fileName}`)
    await load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '提交证明失败')
  }
}

async function addAndProof(ms: any, file?: File) {
  if (!requireCloseOwner(ms)) return
  const form = ensureDraft(ms.id)
  if (!form.name.trim()) {
    message.warning('请填写交付物名称')
    return
  }
  try {
    const payload: any = {
      projectId: projectId.value,
      milestoneId: ms.id,
      name: form.name.trim(),
      deliverType: form.deliverType,
      dueDate: form.dueDate || ms.planDate,
      ownerOrgs: (form.owners || []).join(','),
      achievementNo: form.achievementNo || undefined,
      status: 'PENDING',
    }
    if (file) {
      const uploaded = await uploadFile(file)
      payload.fileName = uploaded.fileName
      payload.fileUrl = uploaded.fileUrl
      payload.status = 'DELIVERED'
      payload.deliverDate = new Date().toISOString().slice(0, 10)
    }
    await deliverableApi.create(payload)
    message.success(file ? '已新增并提交证明' : isCompile.value ? '已列入节点清单' : '已新增临时交付物')
    form.name = ''
    form.achievementNo = ''
    await load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '新增失败')
  }
}

async function uploadEvidence(ms: any, file: File) {
  if (!requireCloseOwner(ms)) return
  try {
    const uploaded = await uploadFile(file)
    evidenceFile[String(ms.id)] = { fileName: uploaded.fileName, fileUrl: uploaded.fileUrl }
    await milestoneApi.saveMaterial(ms.id, {
      fieldCode: 'EVIDENCE',
      fieldName: evidenceName[String(ms.id)] || '节点完成佐证材料',
      fileName: uploaded.fileName,
      fileUrl: uploaded.fileUrl,
      fileSize: uploaded.fileSize,
    })
    message.success(`已上传节点佐证：${uploaded.fileName}`)
    await load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '佐证上传失败')
  }
}

async function closeMs(ms: any) {
  if (!requireCloseOwner(ms)) return
  const left = remainProof(ms)
  if (left > 0) {
    message.warning(`还剩 ${left} 项未提交证明`)
    return
  }
  const ev = evidenceFile[String(ms.id)]
  if (!ev?.fileUrl && !(ms.materials || []).length && !ms.evidence) {
    message.warning('请先上传节点完成佐证文件')
    return
  }
  Modal.confirm({
    title: '确认提交节点销项审核？',
    content: `将里程碑「${ms.name}」提交项目承担部门负责人审核，通过后继续流转单位科研管理部门负责人。`,
    onOk: async () => {
      try {
        if (evidenceName[String(ms.id)] && ev?.fileUrl) {
          await milestoneApi.saveMaterial(ms.id, {
            fieldCode: 'EVIDENCE',
            fieldName: evidenceName[String(ms.id)],
            fileName: ev.fileName,
            fileUrl: ev.fileUrl,
          })
        }
        await milestoneApi.close(ms.id)
        message.success('已提交项目承担部门负责人审核')
        await load()
      } catch (e: any) {
        if (!isSilentAuthError(e)) message.error(e.message)
      }
    },
  })
}

async function auditClose(ms: any, pass: boolean) {
  if (!canAuditCloseMilestone(ms)) {
    message.warning('当前账号不是该销项审核节点办理人')
    return
  }
  Modal.confirm({
    title: pass ? `确认通过「${closeAuditNode(ms)}」？` : `确认退回「${ms.name}」销项？`,
    content: pass ? '通过后会流转到下一审核节点，单位科研管理部门负责人通过后才完成销项。' : '退回后项目负责人可重新上传材料并提交销项审核。',
    onOk: async () => {
      try {
        await milestoneApi.auditClose(ms.id, { pass })
        message.success(pass ? '审核已通过' : '已退回项目负责人')
        await load()
      } catch (e: any) {
        if (!isSilentAuthError(e)) message.error(e.message || '销项审核失败')
      }
    },
  })
}

async function createMilestone() {
  if (!projectId.value || !newMs.name.trim() || !newMs.planDate) {
    message.warning('请填写节点名称与计划日期')
    return
  }
  try {
    await milestoneApi.create({
      projectId: projectId.value,
      name: newMs.name.trim(),
      year: new Date().getFullYear(),
      planDate: newMs.planDate,
      budget: newMs.budget || 0,
    })
    message.success('已新增里程碑节点，请继续编制该节点清单')
    newMsOpen.value = false
    newMs.name = ''
    newMs.planDate = ''
    newMs.budget = 0
    await load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '新增节点失败')
  }
}

async function removeDv(row: any) {
  Modal.confirm({
    title: '从清单中移除该项？',
    onOk: async () => {
      await deliverableApi.remove(row.id)
      message.success('已移除')
      await load()
    },
  })
}

function pickFile(cb: (file: File) => void) {
  const input = document.createElement('input')
  input.type = 'file'
  input.onchange = () => {
    const file = input.files?.[0]
    if (file) cb(file)
  }
  input.click()
}

function dvStatus(row: any): { text: string; color: import('@/api/types').ColorStatus } {
  if (isCompile.value) return { text: '已列入清单', color: 'BLUE' }
  if (proved(row)) return { text: '已提交证明', color: 'GREEN' }
  if (row.colorStatus === 'RED' || row.status === 'OVERDUE') return { text: '待交付-逾期', color: 'RED' }
  if (row.colorStatus === 'YELLOW') return { text: '待交付-临期', color: 'YELLOW' }
  return { text: '待交付', color: 'BLUE' }
}

const listColumns = computed(() => {
  const cols: any[] = [
    { title: '类型', dataIndex: 'deliverType', width: 100 },
    { title: '交付物名称', dataIndex: 'name' },
    { title: '状态', key: 'st', width: 140 },
    { title: '权属', dataIndex: 'ownerOrgs', width: 200 },
    { title: '关联成果编号', dataIndex: 'achievementNo', width: 140 },
    { title: '计划日期', dataIndex: 'dueDate', width: 160 },
  ]
  if (!isCompile.value) cols.push({ title: '证明材料', key: 'proof', width: 160 })
  cols.push({ title: '操作', key: 'act', width: isCompile.value ? 90 : 140 })
  return cols
})
</script>

<template>
  <div class="page-container">
    <template v-if="isCompile">
      <WorkDutyBar :code="dutyCode" :project="project" />
      <a-spin :spinning="loading">
        <div class="compile-wrap">
          <section class="form-card">
            <div class="proj-head">
              <div class="proj-no">{{ project?.projectNo || '—' }} {{ project?.name || '' }}</div>
              <div class="proj-period">
                <div class="field-label">项目周期</div>
                <a-input :value="periodText" disabled />
              </div>
            </div>
            <div class="field-block">
              <div class="field-label">年度</div>
              <a-input-number v-model:value="compileYear" :min="2000" :max="2100" :disabled="compileReadonly" style="width: 100%" />
            </div>
            <div class="field-block">
              <div class="field-label">本年度目标</div>
              <a-textarea v-model:value="annualGoal" :rows="3" :disabled="compileReadonly" placeholder="本年度拟完成的科研任务、试验或节点" />
            </div>
          </section>

          <a-alert
            :type="compileStatusType"
            :message="compileStatusText"
            show-icon
            style="margin-bottom: 16px"
          />

          <section class="form-card">
            <div class="section-top">
              <div>
                <h3 class="sec-title">里程碑节点与交付物清单</h3>
                <p class="form-hint">
                  每个里程碑节点必须填写序号、节点名称和计划完成日期；交付物清单直接维护在对应节点下面，计划产出日期自动跟随该节点。
                </p>
              </div>
              <a-button v-if="!compileReadonly" type="primary" ghost @click="addCompileNode"><PlusOutlined />新建里程碑节点</a-button>
            </div>

            <div v-for="(node, idx) in compileNodes" :key="node.key" class="node-card">
              <div class="node-titlebar">
                <div class="node-badge">{{ nodeNo(idx) }}</div>
                <div class="node-fields">
                  <a-input
                    v-model:value="node.name"
                    :disabled="node.locked || compileReadonly"
                    :placeholder="`第 ${nodeNo(idx)} 个里程碑名称`"
                  />
                  <a-date-picker
                    v-model:value="node.planDate"
                    :disabled="node.locked || compileReadonly"
                    value-format="YYYY-MM-DD"
                    placeholder="计划完成日期"
                    style="width: 100%"
                  />
                </div>
                <a-button type="link" danger :disabled="node.locked || compileReadonly" @click="removeCompileNode(node)">删除节点</a-button>
              </div>

              <div class="node-deliverables">
                <div class="node-dv-title">
                  <b>交付物清单</b>
                  <span>已编制 {{ compileDvsOfNode(node).filter((d) => d.name.trim() || d.deliverType).length }} 项</span>
                </div>
                <div v-if="!compileReadonly" class="type-chips node-type-chips">
                  <a-tag
                    v-for="t in quickTypeChips"
                    :key="`${node.key}-${t.value}`"
                    class="type-chip"
                    @click="addCompileDv(String(t.value), node.key)"
                  >新增{{ t.label }}</a-tag>
                </div>
                <div class="dv-head nested">
                  <span>类型</span>
                  <span>名称</span>
                  <span>计划产出日期</span>
                  <span>清单附件</span>
                  <span />
                </div>
                <div v-for="row in compileDvsOfNode(node)" :key="row.key" class="dv-row nested">
                  <a-select
                    v-model:value="row.deliverType"
                    :disabled="row.locked || compileReadonly"
                    allow-clear
                    placeholder="类型"
                    :options="typeOptions"
                    style="width: 100%"
                  />
                  <a-input v-model:value="row.name" :disabled="row.locked || compileReadonly" placeholder="交付物名称" />
                  <a-date-picker :value="node.planDate" value-format="YYYY-MM-DD" disabled style="width: 100%" placeholder="随节点日期" />
                  <div class="file-cell">
                    <a-upload :show-upload-list="false" :disabled="row.locked || compileReadonly" :before-upload="(f: File) => pickDvFile(row, f)">
                      <a-button :disabled="row.locked || compileReadonly">选择文件</a-button>
                    </a-upload>
                    <span class="file-name" :class="{ empty: !row.fileName }">{{ row.fileName || '未选择文件' }}</span>
                    <div class="file-hint">清单或现场成果说明</div>
                  </div>
                  <a-button type="link" danger :disabled="row.locked || compileReadonly" @click="removeCompileDv(row)">删除</a-button>
                </div>
                <a-empty v-if="!compileDvsOfNode(node).length" description="本节点暂无交付物行" />
                <a-button v-if="!compileReadonly" type="link" class="add-link" @click="addCompileDv(undefined, node.key)">
                  <PlusOutlined />增加交付物行
                </a-button>
              </div>
            </div>
          </section>

          <div class="form-actions">
            <a-button @click="router.push('/implement/milestone')">返回</a-button>
            <a-button v-if="canAuditCompile" danger @click="auditCompile(false)">驳回修改</a-button>
            <a-button v-if="canAuditCompile" type="primary" @click="auditCompile(true)">审核通过并归档</a-button>
            <a-button v-if="!compileReadonly" type="primary" :loading="compileSaving" :disabled="!can.submit" @click="saveCompile">提交审查</a-button>
          </div>
        </div>
      </a-spin>
    </template>

    <template v-else>
      <div class="page-head">
        <div>
          <h2 class="page-title">里程碑销项</h2>
          <div class="page-desc">按已编制的里程碑节点填写交付信息并上传证明。流程图「确认/上传」进入本页。</div>
        </div>
        <a-space>
          <a-button @click="router.push('/implement/milestone')">返回里程碑填报</a-button>
          <a-button v-if="canCloseOperate" type="primary" ghost @click="newMsOpen = true"><PlusOutlined />新增里程碑节点</a-button>
          <a-button @click="load">刷新</a-button>
        </a-space>
      </div>
      <WorkDutyBar :code="dutyCode" :project="project" />

      <a-spin :spinning="loading">
        <div v-if="project" class="proj-banner">
          <div>
            <b>{{ project.projectNo || '—' }} {{ project.name }}</b>
            <p>
              实施中 · 年度目标 {{ project.annualGoal || '—' }}
              <template v-if="project.startDate"> · {{ fmtDate(project.startDate) }} 至 {{ fmtDate(project.endDate) }}</template>
            </p>
          </div>
        </div>

        <a-row :gutter="16" class="stat-row">
          <a-col :span="6"><div class="stat-card"><div class="label">交付物审核通过</div><div class="value">{{ stats.passed }}/{{ stats.totalDv }}</div></div></a-col>
          <a-col :span="6"><div class="stat-card"><div class="label">交付物审核中</div><div class="value">{{ stats.auditing }}</div></div></a-col>
          <a-col :span="6"><div class="stat-card"><div class="label">里程碑已销项</div><div class="value">{{ stats.closed }}/{{ stats.totalMs }}</div></div></a-col>
          <a-col :span="6"><div class="stat-card"><div class="label">交付完成率</div><div class="value">{{ stats.rate }}%</div></div></a-col>
        </a-row>

        <a-empty v-if="!milestones.length && !loading" description="暂无里程碑，请先编制里程碑节点" />

        <a-card
          v-for="(ms, idx) in milestones"
          :key="ms.id"
          :id="`ms-${ms.id}`"
          class="ms-card"
          :class="{ focus: Number(focusId) === Number(ms.id) }"
        >
          <template #title>
            <span>节点{{ idx + 1 }} · {{ ms.name }} · 计划 {{ fmtDate(ms.planDate) }}</span>
          </template>
          <template #extra>
            <StatusTag :color="ms.colorStatus" :text="msStatusText(ms)" />
          </template>

          <p class="node-hint">
            本节点清单 {{ dvsOf(ms).length }} 项
            · 已提交证明 {{ dvsOf(ms).filter((d) => proved(d)).length }} · 审核中 {{ dvsOf(ms).filter((d) => !proved(d) && d.fileUrl).length }}
          </p>

          <a-table
            size="small"
            :pagination="false"
            row-key="id"
            :data-source="dvsOf(ms)"
            :locale="{ emptyText: '本节点暂无交付物。请先完成里程碑节点编制。' }"
            :columns="listColumns"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'deliverType'">{{ typeLabel(record.deliverType) }}</template>
              <template v-else-if="column.key === 'st'">
                <StatusTag :color="dvStatus(record).color" :text="dvStatus(record).text" />
              </template>
              <template v-else-if="column.dataIndex === 'achievementNo'">{{ record.achievementNo || '—' }}</template>
              <template v-else-if="column.dataIndex === 'dueDate'">{{ fmtDate(record.dueDate) }} · {{ dueText(record.dueDate) }}</template>
              <template v-else-if="column.key === 'proof'">
                <a v-if="record.fileUrl" href="#" @click.prevent="openMaterial(record.fileUrl, record.fileName)">{{ record.fileName || '查看' }}</a>
                <span v-else>未上传</span>
              </template>
              <template v-else-if="column.key === 'act'">
                <template v-if="proved(record)">
                  <a v-if="record.fileUrl" href="#" @click.prevent="openMaterial(record.fileUrl, record.fileName)">{{ record.fileName || '查看证明' }}</a>
                  <span v-else>已提交</span>
                </template>
                <a-button v-else-if="canCloseUpload(ms)" type="primary" size="small" @click="pickFile((f) => submitProof(record, f, ms))">提交证明</a-button>
                <span v-else class="hint">仅项目负责人可上传</span>
              </template>
            </template>
          </a-table>

          <div v-if="ms.status !== 'DONE' && canCloseUpload(ms)" class="temp-row">
            <span class="temp-label">临时增补</span>
            <a-select v-model:value="ensureDraft(ms.id).deliverType" style="width: 120px" :options="TYPES" />
            <a-input v-model:value="ensureDraft(ms.id).name" placeholder="交付物名称" style="width: 180px" />
            <a-checkbox-group v-model:value="ensureDraft(ms.id).owners" :options="OWNERS" />
            <a-date-picker v-model:value="ensureDraft(ms.id).dueDate" value-format="YYYY-MM-DD" placeholder="计划日期" />
            <a-button type="primary" @click="pickFile((f) => addAndProof(ms, f))">新增并提交证明</a-button>
          </div>

          <div class="ev-row">
            <a-input v-model:value="evidenceName[String(ms.id)]" placeholder="节点完成佐证名称" style="max-width: 280px" :disabled="!canCloseUpload(ms) || ms.status === 'DONE'" />
            <a-button :disabled="!canCloseUpload(ms) || ms.status === 'DONE'" @click="pickFile((f) => uploadEvidence(ms, f))">
              {{ evidenceFile[String(ms.id)]?.fileName || '选择节点完成佐证文件' }}
            </a-button>
            <a-button type="primary" :disabled="ms.status === 'DONE' || !canCloseUpload(ms)" @click="closeMs(ms)">
              清单已齐 · 上传节点完成佐证并销项
            </a-button>
            <a-button v-if="canAuditCloseMilestone(ms)" type="primary" @click="auditClose(ms, true)">审核通过</a-button>
            <a-button v-if="canAuditCloseMilestone(ms)" danger @click="auditClose(ms, false)">退回负责人</a-button>
            <span class="hint" :class="{ warn: remainProof(ms) > 0 }">
              <template v-if="isCloseAuditStatus(ms.status)">销项已提交，当前节点：{{ closeAuditNode(ms) }}</template>
              <template v-else-if="ms.status === 'DONE'">本节点已销项归档</template>
              <template v-else-if="remainProof(ms) > 0">还剩 {{ remainProof(ms) }} 项未提交证明</template>
              <template v-else-if="!dvsOf(ms).length">请先按清单编制本节点交付物</template>
              <template v-else-if="canCloseUpload(ms)">清单证明已齐，可提交节点销项审核</template>
              <template v-else>仅项目负责人可上传销项材料</template>
            </span>
          </div>
        </a-card>
        <a-modal v-model:open="newMsOpen" title="新增里程碑节点" @ok="createMilestone">
          <a-form layout="vertical">
            <a-form-item label="节点名称" required><a-input v-model:value="newMs.name" /></a-form-item>
            <a-form-item label="计划完成时间" required>
              <a-date-picker v-model:value="newMs.planDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
            <a-form-item label="节点预算（万元）"><a-input-number v-model:value="newMs.budget" style="width: 100%" /></a-form-item>
          </a-form>
        </a-modal>
      </a-spin>
    </template>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 16px; flex-wrap: wrap; }
.proj-banner { background: #fff; border: 1px solid #e8e8e8; border-radius: 4px; padding: 16px 20px; margin-bottom: 16px; }
.proj-banner b { font-size: 16px; color: #1f1f1f; }
.proj-banner p { margin: 6px 0 0; color: #8c8c8c; font-size: 13px; }
.stat-row { margin-bottom: 16px; }
.ms-card { margin-bottom: 16px; }
.ms-card.focus { border-color: #91caff; }
.temp-row, .ev-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 12px;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
}
.temp-label { color: #8c8c8c; font-size: 12px; }
.hint { font-size: 12px; color: #8c8c8c; }
.node-hint { margin: 0 0 12px; color: #8c8c8c; font-size: 13px; }

.compile-wrap { max-width: 1100px; margin: 0 auto; }
.form-card {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 20px 24px 16px;
  margin-bottom: 16px;
}
.proj-head { display: flex; justify-content: space-between; gap: 24px; align-items: flex-start; margin-bottom: 16px; }
.proj-no { font-size: 18px; font-weight: 600; color: #0064ef; line-height: 32px; }
.proj-period { min-width: 280px; flex: 0 0 42%; }
.field-label { color: #8c8c8c; font-size: 13px; margin-bottom: 8px; }
.field-block { margin-bottom: 16px; }
.sec-title { margin: 0 0 8px; font-size: 16px; font-weight: 600; color: #0064ef; }
.form-hint { color: #8c8c8c; font-size: 13px; margin: 0 0 16px; line-height: 1.6; }
.section-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 8px;
}
.node-card {
  border: 1px solid #d6e4ff;
  border-radius: 8px;
  background: #fbfdff;
  padding: 14px 16px 12px;
  margin-bottom: 14px;
}
.node-titlebar {
  display: grid;
  grid-template-columns: 48px 1fr 80px;
  gap: 12px;
  align-items: center;
}
.node-badge {
  width: 36px;
  height: 36px;
  border-radius: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #0064ef;
  color: #fff;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.node-fields {
  display: grid;
  grid-template-columns: 1fr 220px;
  gap: 12px;
}
.node-deliverables {
  margin: 14px 0 0 48px;
  padding: 12px 12px 4px;
  border: 1px dashed #b7d3ff;
  border-radius: 6px;
  background: #fff;
}
.node-dv-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #262626;
}
.node-dv-title span {
  color: #8c8c8c;
  font-size: 12px;
}
.node-type-chips :deep(.ant-tag) {
  margin-inline-end: 0;
}
.node-head, .dv-head {
  display: grid;
  gap: 12px;
  color: #8c8c8c;
  font-size: 13px;
  margin-bottom: 8px;
  padding: 0 4px;
}
.node-head { grid-template-columns: 56px 1fr 220px 56px; }
.dv-head { grid-template-columns: 140px 1fr 160px 180px 200px 56px; }
.dv-head.nested { grid-template-columns: 140px 1fr 180px 240px 56px; }
.node-row, .dv-row {
  display: grid;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}
.node-row { grid-template-columns: 56px 1fr 220px 56px; align-items: center; }
.node-no {
  height: 32px;
  line-height: 32px;
  text-align: center;
  color: #262626;
  font-variant-numeric: tabular-nums;
}
.dv-row { grid-template-columns: 140px 1fr 160px 180px 200px 56px; }
.dv-row.nested { grid-template-columns: 140px 1fr 180px 240px 56px; }
.add-link { padding-left: 0; height: 32px; }
.add-node { margin-left: 68px; }
.stat-chips, .type-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.type-chip { cursor: pointer; }
.file-cell { min-width: 0; }
.file-name { margin-left: 8px; color: #0064ef; font-size: 12px; word-break: break-all; }
.file-name.empty { color: #8c8c8c; }
.file-hint { margin-top: 4px; color: #8c8c8c; font-size: 12px; }
.dv-actions { display: flex; gap: 8px; }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin: 8px 0 24px; }
@media (max-width: 960px) {
  .proj-head { flex-direction: column; }
  .proj-period { flex: 1; width: 100%; }
  .section-top { flex-direction: column; }
  .node-titlebar, .node-fields { grid-template-columns: 1fr; }
  .node-deliverables { margin-left: 0; }
  .node-head, .node-row { grid-template-columns: 48px 1fr 180px 56px; }
  .add-node { margin-left: 60px; }
  .dv-head { display: none; }
  .dv-row { grid-template-columns: 1fr; }
}
</style>






