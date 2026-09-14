<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, RobotOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { declarationApi, projectApi, userApi } from '@/api/modules'
import { useDictStore } from '@/stores/dict'
import { LEVEL_TEXT, type DeclarationPosts, type SysUser } from '@/api/types'
import { fmtDate } from '@/utils/format'
import majorDict from '@/config/major1-major2.json'
import DeclareFlowDialog from '@/components/declare/DeclareFlowDialog.vue'
import { encodeDeclarationPostsRemark, unwrapDeclarationDetail } from '@/utils/flowLive'
import { firstDeclareAuditNode, nextDeclareAuditNode } from '@/utils/declareFlow'
import { useUserStore } from '@/stores/user'
import { usePendingStore } from '@/stores/pending'
import { canActOnHandlers, canAuditByIdentity } from '@/utils/flowActor'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'

const dictStore = useDictStore()
const user = useUserStore()
const pendingStore = usePendingStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ page: 1, size: 10, keyword: '', status: undefined, channelId: undefined })
const pendingReviews = ref<any[]>([])
const pendingMaintenanceReviews = ref<any[]>([])
const reviewOnly = ref(false)
const reviewing = ref<any>(null)
const tableRows = computed(() =>
  reviewOnly.value
    ? [
        ...pendingReviews.value.map((row) => ({ ...row, __kind: 'declaration' })),
        ...pendingMaintenanceReviews.value.map((row) => ({ ...row, __kind: 'maintenance' })),
      ]
    : rows.value,
)

const members = ref<SysUser[]>([])
const open = ref(false)
const editing = ref<any>(null)
const saving = ref(false)
const submitting = ref(false)
const checking = ref(false)
const recognizing = ref(false)
const flowOpen = ref(false)
const flowDecl = ref<any>(null)
const aiOpen = ref(false)
const aiFileName = ref('')
const aiPreview = ref<Record<string, any>>({})
const formMats = ref<{ fieldCode: string; fieldName: string; fileName?: string; fileUrl?: string; pendingName?: string; locked?: number }[]>([])
const pendingUploads = ref<Record<string, string>>({})
const channelMaintOpen = ref(false)

async function openFlow(record: any) {
  try {
    const [detailRes, matRes] = await Promise.all([
      declarationApi.detail(record.id),
      declarationApi.materials(record.id),
    ])
    const { declaration, materials } = unwrapDeclarationDetail(detailRes.data)
    const ch =
      dictStore.channels.find((c) => c.id === (declaration?.channelId || record.channelId)) ||
      (detailRes.data as any)?.channel
    flowDecl.value = {
      ...record,
      ...(declaration || {}),
      materials: (materials.length ? materials : matRes.data) || [],
      declareMaterial: ch?.declareMaterial || (detailRes.data as any)?.declareMaterial,
      channelDeclareMaterial: ch?.declareMaterial || (detailRes.data as any)?.declareMaterial,
    }
  } catch {
    flowDecl.value = record
  }
  flowOpen.value = true
}

type PostKey = keyof DeclarationPosts
const personSearch = reactive<Partial<Record<PostKey, string>>>({})

const POST_GROUPS: { name: string; posts: { key: PostKey; label: string }[] }[] = [
  {
    name: '项目团队',
    posts: [
      { key: 'contact', label: '项目联系人' },
      { key: 'leader', label: '项目负责人' },
      { key: 'techLeader', label: '技术负责人' },
      { key: 'supervisor', label: '项目主管' },
    ],
  },
  {
    name: '责任专家',
    posts: [
      { key: 'chief1', label: '一级总师' },
      { key: 'chief2', label: '二级总师' },
    ],
  },
  {
    name: '管理团队',
    posts: [
      { key: 'deptHead', label: '项目承担部门负责人' },
      { key: 'hqDirector', label: '总部处室处长' },
      { key: 'hqSupervisor', label: '总部处室主管' },
      { key: 'unitTechDirector', label: '单位科技部长' },
      { key: 'unitTechSupervisor', label: '单位科技主管' },
    ],
  },
  {
    name: '财务团队',
    posts: [
      { key: 'hqFinance', label: '总部财务主管' },
      { key: 'unitFinanceDirector', label: '单位财务部长' },
      { key: 'unitFinanceSupervisor', label: '单位财务主管' },
    ],
  },
]

const ALL_POST_KEYS = POST_GROUPS.flatMap((g) => g.posts.map((p) => p.key))

const LEAD_ORGS = ['中国商飞总部', '上飞院', '上飞公司', '北研中心', '客服公司', '试飞中心']

function emptyPosts(): DeclarationPosts {
  return {
    contact: undefined,
    leader: undefined,
    techLeader: undefined,
    supervisor: undefined,
    chief1: undefined,
    chief2: undefined,
    hqDirector: undefined,
    hqSupervisor: undefined,
    unitTechDirector: undefined,
    unitTechSupervisor: undefined,
    deptHead: undefined,
    hqFinance: undefined,
    unitFinanceDirector: undefined,
    unitFinanceSupervisor: undefined,
  }
}

function emptyForm() {
  return {
    name: '',
    channelId: undefined as number | undefined,
    needApproval: 1,
    goal: '',
    applyFund: undefined as number | undefined,
    startDate: '2026-09-01',
    endDate: '2029-08-31',
    partnerOrgs: '',
    major1: undefined as string | undefined,
    major2: undefined as string | undefined,
    demandOrg: '',
    leadOrgName: '中国商飞总部',
    leadWorkContent: '',
    posts: emptyPosts(),
  }
}

const form = reactive(emptyForm())
const { can, guard } = useWorkDuty('declare', form)
const formMode = ref<'create' | 'edit' | 'view'>('create')
const formReadonly = computed(() => formMode.value === 'view')
const isCurrentProjectLeader = computed(() =>
  canActOnHandlers(
    form.posts.leader ? [{ label: form.posts.leader }] : [],
    { employeeNo: user.employeeNo, realName: user.realName, identityCode: user.identityCode },
  ),
)
const submitButtonText = computed(() =>
  isCurrentProjectLeader.value ? '提交申报并进入本人审核' : '校验并提交负责人审核',
)

const major1Options = majorDict.major1 as string[]
const major2Options = computed(() => {
  if (!form.major1) return [] as string[]
  return ((majorDict.major2ByMajor1 as Record<string, string[]>)[form.major1] || [])
})

function personLabel(u: SysUser) {
  return `${u.realName}（${u.employeeNo}）`
}

function personMeta(u: SysUser) {
  const unit = u.orgName || '—'
  const dept = u.deptName || '—'
  const identity = u.identity || '—'
  return `${unit} / ${dept} · ${identity}`
}

function personSearchText(u: SysUser) {
  return [u.realName, u.employeeNo, u.orgName, u.deptName, u.identity, u.projectPost]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
}

const coveredPostCount = computed(
  () => ALL_POST_KEYS.filter((k) => !!(form.posts as any)?.[k]).length,
)

const STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿', SUBMITTED: '已提交', APPROVING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', REVOKED: '已撤销', REPORTED: '已归档',
}
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default', SUBMITTED: 'blue', APPROVING: 'processing', APPROVED: 'success', REJECTED: 'error', REVOKED: 'warning', REPORTED: 'cyan',
}
const MAINT_STATUS_TEXT: Record<string, string> = {
  MAINT_UNIT_REVIEW: '待单位科技管理部审核',
  MAINT_HQ_REVIEW: '待总部主管审核',
  MAINT_DONE: '维护完成',
  MAINT_REJECTED: '退回待维护',
  MAINT_DRAFT: '待维护',
}
const MAINT_STATUS_COLOR: Record<string, string> = {
  MAINT_UNIT_REVIEW: 'processing',
  MAINT_HQ_REVIEW: 'blue',
  MAINT_DONE: 'success',
  MAINT_REJECTED: 'error',
  MAINT_DRAFT: 'warning',
}
function isMaintenanceReview(record: any) {
  return record?.__kind === 'maintenance'
}

function openMaintenanceReview(record: any) {
  router.push(`/overview/detail/${record.id}`)
}

const columns = [
  { title: '申报单号', dataIndex: 'applyNo', width: 140 },
  { title: '项目名称', dataIndex: 'name', width: 280 },
  { title: '项目层级', dataIndex: 'levelCode', width: 100 },
  { title: '渠道类别', dataIndex: 'channelName', width: 160 },
  { title: '责任单位', key: 'leadOrg', width: 140 },
  { title: '一级专业', dataIndex: 'major1', width: 140 },
  { title: '负责人', dataIndex: 'applicant', width: 100 },
  { title: '申报日期', dataIndex: 'applyAt', width: 120 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '当前节点', dataIndex: 'flowNode', width: 160 },
  { title: '操作', key: 'action', width: 210, fixed: 'right' as const },
]

const FLOW_NODE_POST_KEYS: { re: RegExp; keys: PostKey[] }[] = [
  { re: /联系人/, keys: ['contact'] },
  { re: /项目负责人/, keys: ['leader'] },
  { re: /承担部门|承办部门/, keys: ['deptHead'] },
  { re: /二级总师/, keys: ['chief2'] },
  { re: /一级总师/, keys: ['chief1'] },
  { re: /总部.*财务/, keys: ['hqFinance'] },
  { re: /财务/, keys: ['unitFinanceDirector', 'unitFinanceSupervisor'] },
  { re: /总部|科研项目处/, keys: ['hqDirector', 'hqSupervisor'] },
  { re: /科技部门/, keys: ['unitTechDirector', 'unitTechSupervisor'] },
  { re: /分管/, keys: ['unitTechDirector'] },
]

async function loadMembers() {
  const res = await userApi.candidates()
  members.value = ((res.data as any[]) || []) as SysUser[]
}

async function load() {
  loading.value = true
  try {
    const [res, pendingRes, maintenanceRes, filingRes] = await Promise.all([
      declarationApi.page(query),
      declarationApi.page({ page: 1, size: 200 }),
      projectApi.pendingMaintenance(),
      declarationApi.page({ page: 1, size: 200, status: 'APPROVED' }),
    ])
    rows.value = (res.data as any)?.records || []
    total.value = (res.data as any)?.total || 0
    pendingReviews.value = ((pendingRes.data as any)?.records || []).filter(
      (row: any) =>
        ['SUBMITTED', 'APPROVING'].includes(row.status) &&
        canAuditDeclaration(row),
    )
    pendingMaintenanceReviews.value = ((maintenanceRes.data as any[]) || []).map((row: any) => ({
      ...row,
      applyNo: row.projectNo,
      applicant: row.ownerName,
      applyAt: undefined,
      status: row.acceptStatus,
    }))
    pendingStore.setDeclarationReviewCount(pendingReviews.value.length)
    pendingStore.setMaintenanceReviewCount(pendingMaintenanceReviews.value.length)
    pendingStore.setFilingPendingCount(Number((filingRes.data as any)?.total || 0))
  } finally {
    loading.value = false
  }
}

function canAuditDeclaration(row: any) {
  if (user.identityCode === 'admin') return true
  const node = String(row?.flowNode || '')
  const hit = FLOW_NODE_POST_KEYS.find((item) => item.re.test(node))
  const labels = hit?.keys
    .map((key) => row?.posts?.[key])
    .filter((label): label is string => !!label) || []
  if (labels.length) {
    return canActOnHandlers(
      labels.map((label) => ({ label })),
      { employeeNo: user.employeeNo, realName: user.realName, identityCode: user.identityCode },
    )
  }
  return canAuditByIdentity(row?.flowNode, user.identityCode)
}

onMounted(async () => {
  reviewOnly.value = route.query.review === 'mine'
  await dictStore.loadChannels()
  await loadMembers()
  await load()
  const doneId = Number(route.query.doneId)
  if (doneId) {
    const record = rows.value.find((row: any) => Number(row.id) === doneId) || { id: doneId }
    await onView(record)
  }
})

watch(
  () => route.query.review,
  (value) => {
    reviewOnly.value = value === 'mine'
  },
)

watch(
  () => route.query.doneId,
  async (value) => {
    const doneId = Number(value)
    if (!doneId) return
    const record = rows.value.find((row: any) => Number(row.id) === doneId) || { id: doneId }
    await onView(record)
  },
)

const POST_RECOMMEND_ALIASES: Record<PostKey, string[]> = {
  contact: ['项目联系人'],
  leader: ['项目负责人'],
  techLeader: ['技术负责人'],
  supervisor: ['项目主管'],
  chief1: ['一级总师'],
  chief2: ['二级总师'],
  deptHead: ['项目承担部门负责人', '承担部门负责人'],
  hqDirector: ['总部责任处室处长', '总部处室处长'],
  hqSupervisor: ['总部科研项目主管', '总部处室主管'],
  unitTechDirector: ['单位科研管理部门负责人', '单位科技部长'],
  unitTechSupervisor: ['单位项目主管', '单位科技主管'],
  hqFinance: ['总部财务主管'],
  unitFinanceDirector: ['单位财务部长'],
  unitFinanceSupervisor: ['单位财务主管'],
}

function isRecommendedPerson(u: SysUser, post: { key: PostKey; label: string }) {
  const text = [u.identity, u.projectPost, u.deptName]
    .filter(Boolean)
    .join(' ')
  return (POST_RECOMMEND_ALIASES[post.key] || [post.label]).some((alias) => text.includes(alias))
}

function personOptions(post: { key: PostKey; label: string }) {
  const q = String(personSearch[post.key] || '').trim().toLowerCase()
  if (!q) return members.value.filter((u) => isRecommendedPerson(u, post))
  if (/^\d+$/.test(q)) return members.value.filter((u) => String(u.employeeNo || '').includes(q))
  return members.value.filter((u) => isRecommendedPerson(u, post) && personSearchText(u).includes(q))
}

function onMajor1Change() {
  form.major2 = undefined
}

const selectedChannel = computed(() => dictStore.channels.find((c) => c.id === form.channelId))
const channelGroups = computed(() =>
  (['NATIONAL', 'LOCAL', 'COMPANY'] as const)
    .map((levelCode) => ({
      levelCode,
      title: LEVEL_TEXT[levelCode],
      channels: dictStore.channels.filter((c) => c.levelCode === levelCode && c.status !== 0),
    }))
    .filter((g) => g.channels.length),
)
const declarationStep = computed(() => (form.channelId ? (submitting.value ? 2 : 1) : 0))

function channelPath(c: any) {
  if (!c) return '—'
  return [c.channelDept, c.channelOffice || c.innerOffice].filter(Boolean).join(' · ') || '平台统一渠道'
}

function channelFlowPreview(c: any) {
  const text = String(c.flowNodes || '').trim()
  return text || '渠道确定后由系统自动配置申报与审签流程'
}

function selectChannel(c: any) {
  if (formReadonly.value) return
  form.channelId = c.id
  form.needApproval = /(?:无需审批|直接报备)/.test(String(c.flowNodes || '')) ? 0 : 1
  if (!form.demandOrg) form.demandOrg = c.innerDept || c.channelDept || ''
  onChannelChange()
}

function clearChannel() {
  if (formReadonly.value) return
  form.channelId = undefined
  formMats.value = []
  pendingUploads.value = {}
}

function goChannelMaintenance() {
  open.value = false
  router.push('/system/channel')
}

function splitMaterials(text?: string) {
  return String(text || '')
    .split(/[,，、;；/|]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

function rebuildChannelMats() {
  const need = splitMaterials(selectedChannel.value?.declareMaterial)
  formMats.value = need.map((name) => {
    const fieldCode = `F_${name}`
    const prev = formMats.value.find((m) => m.fieldCode === fieldCode)
    return {
      fieldCode,
      fieldName: name,
      fileName: prev?.fileName,
      fileUrl: prev?.fileUrl,
      pendingName: pendingUploads.value[fieldCode] || prev?.pendingName,
      locked: 0,
    }
  })
}

function onChannelChange() {
  pendingUploads.value = {}
  rebuildChannelMats()
}

async function loadFormMaterials(id: number) {
  const res = await declarationApi.materials(id)
  const list = ((res.data as any[]) || []).filter((m) => !m.locked)
  if (list.length) {
    formMats.value = list.map((m) => ({
      fieldCode: m.fieldCode,
      fieldName: m.fieldName,
      fileName: m.fileName,
      fileUrl: m.fileUrl,
      pendingName: pendingUploads.value[m.fieldCode],
      locked: m.locked,
    }))
  } else {
    rebuildChannelMats()
  }
}

function guessChannelByText(text: string) {
  const t = text.toLowerCase()
  return (
    dictStore.channels.find((c) => t.includes((c.channelName || '').toLowerCase()) || t.includes((c.channelCode || '').toLowerCase())) ||
    dictStore.channels.find((c) => c.channelCode === 'YYGD') ||
    dictStore.channels[0]
  )
}

function buildAiPreview(fileName: string) {
  const stem = fileName.replace(/\.[^.]+$/, '').replace(/任务书|计划任务书|申请书|建议书/g, '').trim()
  const ch = guessChannelByText(fileName)
  return {
    name: stem || '民机预先研究项目（任务书识别）',
    channelId: form.channelId || ch?.id,
    channelName: dictStore.channels.find((c) => c.id === (form.channelId || ch?.id))?.channelName,
    goal: `依据任务书约定，完成${stem || '关键技术'}攻关、验证与成果固化。`,
    applyFund: 860,
    startDate: '2026-09-01',
    endDate: '2029-08-31',
    partnerOrgs: '北研中心、南京航空航天大学',
    major1: '10-总体气动',
    major2: '1001-总体与气动',
    demandOrg: '科技部科研项目处',
    leadOrgName: form.leadOrgName || '上飞院',
    leadWorkContent: '牵头总体方案、任务分解与关键技术验证，按任务书节点提交阶段成果。',
  }
}

function onPickTaskBook(info: any) {
  if (info?.file?.status === 'removed') return
  const file = info?.file?.originFileObj || info?.file
  const name = file?.name
  if (!name) return
  recognizing.value = true
  aiFileName.value = name
  setTimeout(() => {
    aiPreview.value = buildAiPreview(name)
    recognizing.value = false
    aiOpen.value = true
  }, 600)
}

function applyAiPreview() {
  const p = aiPreview.value
  if (p.channelId) {
    const ch = dictStore.channels.find((c) => c.id === p.channelId)
    if (ch) selectChannel(ch)
    else form.channelId = p.channelId
  }
  form.name = p.name || form.name
  form.goal = p.goal || form.goal
  form.applyFund = p.applyFund
  form.startDate = p.startDate
  form.endDate = p.endDate
  form.partnerOrgs = p.partnerOrgs || form.partnerOrgs
  form.major1 = p.major1
  form.major2 = p.major2
  form.demandOrg = p.demandOrg || form.demandOrg
  form.leadOrgName = p.leadOrgName || form.leadOrgName
  form.leadWorkContent = p.leadWorkContent || form.leadWorkContent
  rebuildChannelMats()
  aiOpen.value = false
  message.success('已根据任务书识别结果回填，请核对后暂存或提交')
}

function onPickChannelFile(m: { fieldCode: string; fieldName: string }, info: any) {
  const file = info?.file?.originFileObj || info?.file
  const name = file?.name || `${m.fieldName}.pdf`
  pendingUploads.value = { ...pendingUploads.value, [m.fieldCode]: name }
  const hit = formMats.value.find((x) => x.fieldCode === m.fieldCode)
  if (hit) hit.pendingName = name
  message.success(`${m.fieldName} 已选择：${name}（暂存后写入申报单）`)
}

function resetForm(src?: any) {
  const base = emptyForm()
  pendingUploads.value = {}
  formMats.value = []
  if (!src) {
    Object.assign(form, base, { posts: emptyPosts() })
    return
  }
  Object.assign(form, base, {
    name: src.name || '',
    channelId: src.channelId,
    needApproval: src.needApproval ?? 1,
    goal: src.goal || '',
    applyFund: src.applyFund,
    startDate: src.startDate || '2026-09-01',
    endDate: src.endDate || '2029-08-31',
    partnerOrgs: src.partnerOrgs || '',
    major1: src.major1,
    major2: src.major2,
    demandOrg: src.demandOrg || '',
    leadOrgName: src.leadOrgName || src.orgName || '中国商飞总部',
    leadWorkContent: src.leadWorkContent || '',
    posts: { ...emptyPosts(), ...(src.posts || {}) },
  })
  rebuildChannelMats()
}

function onCreate() {
  reviewing.value = null
  editing.value = null
  formMode.value = 'create'
  resetForm()
  open.value = true
}

async function onView(record: any, asReview = false) {
  let detail = record
  if (record?.id) {
    try {
      const res = await declarationApi.detail(record.id)
      const { declaration } = unwrapDeclarationDetail(res.data)
      detail = { ...record, ...(declaration || {}) }
    } catch {
      message.warning('申报详情加载失败，已展示列表中的基础信息')
    }
  }
  reviewing.value = asReview ? detail : null
  editing.value = detail
  formMode.value = 'view'
  resetForm(detail)
  open.value = true
  if (record?.id) await loadFormMaterials(record.id)
}

async function onEdit(record: any) {
  reviewing.value = null
  editing.value = record
  formMode.value = can.value.fill ? 'edit' : 'view'
  resetForm(record)
  open.value = true
  if (record?.id) await loadFormMaterials(record.id)
}

async function onCheckDuplicate() {
  if (!form.name?.trim()) {
    message.warning('请先填写项目名称')
    return
  }
  checking.value = true
  try {
    const res = await declarationApi.checkDuplicate({ name: form.name.trim() })
    const data: any = res.data
    if (data?.duplicated) {
      message.warning(`发现相似项目/申报 ${data.matches?.length || 0} 条，请核对后继续`)
    } else {
      message.success('未发现重名项目，可继续填报')
    }
  } finally {
    checking.value = false
  }
}

function validateForm() {
  if (!form.name?.trim()) return '请填写项目名称'
  if (form.needApproval === undefined || form.needApproval === null) return '请选择是否需要审批'
  if (!form.channelId) return '请选择项目渠道（来源）'
  if (!form.major1) return '请选择一级专业'
  if (!form.major2) return '请选择二级专业'
  if (!form.leadOrgName) return '请选择责任单位/牵头单位'
  if (!form.leadWorkContent?.trim()) return '请填写牵头单位主要工作内容'
  for (const g of POST_GROUPS) {
    for (const p of g.posts) {
      if (!(form.posts as any)[p.key]) return `请选择${p.label}`
    }
  }
  return ''
}

function missingMaterials() {
  return formMats.value.filter((m) => !m.fileName && !m.pendingName && !pendingUploads.value[m.fieldCode])
}

function buildPayload() {
  return {
    ...form,
    orgName: form.leadOrgName,
    applicant: form.posts.leader || form.posts.contact,
    // 同步岗位快照，兼容尚未完成岗位表迁移的后端环境。
    remark: encodeDeclarationPostsRemark(form.posts),
  }
}

async function flushPendingUploads(id: number) {
  const entries = Object.entries(pendingUploads.value)
  for (const [fieldCode, fileName] of entries) {
    await declarationApi.uploadMaterial(id, { fieldCode, fileName })
    const hit = formMats.value.find((m) => m.fieldCode === fieldCode)
    if (hit) {
      hit.fileName = fileName
      hit.pendingName = undefined
    }
  }
  pendingUploads.value = {}
}

async function persistDraft() {
  const payload = buildPayload()
  if (editing.value?.id) {
    await declarationApi.update(editing.value.id, payload)
    await flushPendingUploads(editing.value.id)
    return editing.value.id as number
  }
  const res = await declarationApi.create(payload)
  const id = Number(res.data)
  editing.value = { id, ...payload, status: 'DRAFT' }
  await flushPendingUploads(id)
  await loadFormMaterials(id)
  return id
}

async function onSaveDraft() {
  if (!guard('fill')) return
  if (!form.name?.trim()) {
    message.warning('暂存至少需要填写项目名称')
    return
  }
  saving.value = true
  try {
    await persistDraft()
    message.success('已暂存为草稿，可继续补材料后提交')
    load()
  } finally {
    saving.value = false
  }
}

function collectSubmitIssues() {
  const issues: string[] = []
  const err = validateForm()
  if (err) issues.push(err)
  missingMaterials().forEach((m) => issues.push(`请上传本渠道材料：${m.fieldName}`))
  return issues
}

function submitErrorText(error: any) {
  const text = String(error?.message || '').trim()
  if (!text || text === 'Network Error') return '提交失败：未收到服务器响应，请稍后重试'
  if (/timeout/i.test(text)) return '提交超时：服务器响应较慢，请稍后重试；请勿连续重复提交'
  return `提交失败：${text}`
}

async function onConfirmSubmit() {
  if (submitting.value) return
  if (!guard('submit')) return
  const issues = collectSubmitIssues()
  if (issues.length) {
    Modal.warning({
      title: '提交前校验未通过',
      content: issues.slice(0, 8).join('；'),
    })
    return
  }
  Modal.confirm({
    title: isCurrentProjectLeader.value ? '确认提交申报并进入本人审核？' : '确认提交项目负责人审核？',
    content: isCurrentProjectLeader.value
      ? `您是本项目负责人。提交后不会跳过负责人节点，本申报将进入您的「待我审核」，需由您再审核一次。`
      : `将按渠道「${selectedChannel.value?.channelName || ''}」提交给本项目负责人审核。`,
    okText: isCurrentProjectLeader.value ? '提交并进入本人审核' : '提交负责人审核',
    cancelText: '返回修改',
    onOk: async () => {
      if (submitting.value) return Promise.reject(new Error('正在提交，请勿重复操作'))
      submitting.value = true
      message.loading({ content: '正在保存申报信息与岗位人员…', key: 'declaration-submit', duration: 0 })
      try {
        const id = await persistDraft()
        message.loading({ content: '保存成功，正在发起项目负责人审核…', key: 'declaration-submit', duration: 0 })
        await declarationApi.submit(id)
        message.success({
          content: `已提交项目负责人审核，流转至：${firstDeclareAuditNode(form.needApproval !== 0)}`,
          key: 'declaration-submit',
          duration: 3,
        })
        open.value = false
        await load()
      } catch (error: any) {
        message.error({ content: submitErrorText(error), key: 'declaration-submit', duration: 5 })
        throw error
      } finally {
        submitting.value = false
      }
    },
  })
}

async function openMaterials(row: any) {
  matRow.value = row
  const res = await declarationApi.materials(row.id)
  mats.value = (res.data as any[]) || []
  matOpen.value = true
}

const matOpen = ref(false)
const matRow = ref<any>(null)
const mats = ref<any[]>([])

async function uploadMat(m: any) {
  if (m.locked) return
  await declarationApi.uploadMaterial(matRow.value.id, { fieldCode: m.fieldCode, fileName: `${m.fieldName}.pdf` })
  message.success(`${m.fieldName} 已上传`)
  const res = await declarationApi.materials(matRow.value.id)
  mats.value = (res.data as any[]) || []
}

async function submitFlow(row: any) {
  if (!guard('submit')) return
  await declarationApi.submit(row.id)
  message.success(`已提交项目负责人审核，流转至：${firstDeclareAuditNode(row.needApproval !== 0)}`)
  load()
}
function audit(row: any) {
  if (!canAuditDeclaration(row)) {
    message.warning(`仅本项目指定的当前节点办理人可审批（${row.flowNode || '待指定'}）`)
    return
  }
  Modal.confirm({
    title: '确认审批通过？',
    content: `${row.applyNo || ''} · ${row.name || ''}，当前节点：${row.flowNode || '—'}。确认材料齐全并同意流转后再提交。`,
    okText: '确认通过',
    cancelText: '返回核对',
    onOk: async () => {
      await declarationApi.audit(row.id, { pass: true, opinion: '同意申报，材料齐全' })
      const next = nextDeclareAuditNode(row.flowNode, row.needApproval !== 0)
      message.success(next ? `审批通过，流转至：${next}` : '审批通过，申报审签结束')
      open.value = false
      reviewing.value = null
      await load()
    },
  })
}
async function revoke(row: any) {
  Modal.confirm({
    title: '确认撤销？',
    content: '撤销后项目回归草稿状态，撤销记录将永久留存。',
    onOk: async () => {
      await declarationApi.revoke(row.id)
      message.success('已撤销，回归草稿状态')
      load()
    },
  })
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">项目申报</h2>
    <div class="page-desc">
      项目团队可协同填报；材料齐套后提交本项目负责人审核，负责人本人发起也须完成负责人审核节点。
      申报流程办结后自动进入立项备案材料办理，负责人不能自行跳过审批。
    </div>
    <WorkDutyBar code="declare" :project="form" />

    <a-alert
      v-if="pendingReviews.length || pendingMaintenanceReviews.length"
      type="warning"
      show-icon
      class="pending-review-alert"
      :message="`待我审核：${pendingReviews.length} 条项目申报，${pendingMaintenanceReviews.length} 条待维护材料`"
      :description="`当前账号 ${user.realName}（${user.employeeNo || '—'}）是流程节点办理人，请核对申报信息、待维护材料和流程审核人后完成审批。`"
    >
      <template #action>
        <a-button type="primary" size="small" @click="reviewOnly = !reviewOnly">
          {{ reviewOnly ? '查看全部申报' : '只看待我审核' }}
        </a-button>
      </template>
    </a-alert>

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <div class="filter-bar">
          <a-input v-model:value="query.keyword" placeholder="项目名称" style="width: 220px" allow-clear @press-enter="load" />
          <a-select v-model:value="query.channelId" placeholder="全部渠道" style="width: 200px" allow-clear show-search
            :filter-option="(i: string, o: any) => String(o.label).includes(i)">
            <a-select-option value="">全部</a-select-option>
            <a-select-option v-for="c in dictStore.channels" :key="c.id" :value="c.id">{{ c.channelName }}</a-select-option>
          </a-select>
          <a-select v-model:value="query.status" placeholder="全部状态" style="width: 130px" allow-clear>
            <a-select-option value="">全部</a-select-option>
            <a-select-option v-for="(v, k) in STATUS_TEXT" :key="k" :value="k">{{ v }}</a-select-option>
          </a-select>
          <a-button type="primary" @click="load"><SearchOutlined />查询</a-button>
        </div>
        <a-button type="primary" :disabled="!can.fill" @click="onCreate"><PlusOutlined />新建申报</a-button>
      </div>

      <a-table
        :columns="columns"
        :data-source="tableRows"
        :loading="loading"
        :row-key="(record) => `${record.__kind || 'declaration'}-${record.id}`"
        :scroll="{ x: 1700 }"
        :pagination="reviewOnly ? false : { current: query.page, pageSize: query.size, total, showTotal: (t: number) => `共 ${t} 条` }"
        @change="(p: any) => { query.page = p.current; load() }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'levelCode'">
            <a-tag color="blue">{{ LEVEL_TEXT[record.levelCode] }}</a-tag>
          </template>
          <template v-else-if="column.key === 'leadOrg'">{{ record.leadOrgName || record.orgName || '—' }}</template>
          <template v-else-if="column.dataIndex === 'applyAt'">{{ fmtDate(record.applyAt) }}</template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag v-if="isMaintenanceReview(record)" :color="MAINT_STATUS_COLOR[record.status] || 'warning'">
              {{ MAINT_STATUS_TEXT[record.status] || record.status }}
            </a-tag>
            <a-tag v-else :color="STATUS_COLOR[record.status]">{{ STATUS_TEXT[record.status] }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="isMaintenanceReview(record)" :size="2">
              <a-button type="link" size="small" @click="openMaintenanceReview(record)">查看材料</a-button>
              <a-button type="primary" size="small" @click="openMaintenanceReview(record)">审核</a-button>
            </a-space>
            <a-space v-else :size="2">
              <a-button type="link" size="small" @click="onView(record)">查看</a-button>
              <a-button type="link" size="small" @click="openMaterials(record)">材料</a-button>
              <a-button type="link" size="small" @click="openFlow(record)">审批流转</a-button>
              <a-button
                v-if="canAuditDeclaration(record)"
                type="primary"
                size="small"
                @click="onView(record, true)"
              >
                审核
              </a-button>
              <a-divider type="vertical" />
              <a-dropdown>
                <a-button type="link" size="small">更多</a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item v-if="['DRAFT', 'REJECTED'].includes(record.status)" key="edit" @click="onEdit(record)">编辑</a-menu-item>
                    <a-menu-item key="flow" @click="openFlow(record)">查看审批流转</a-menu-item>
                    <a-menu-item v-if="['DRAFT', 'REJECTED'].includes(record.status)" key="submit" @click="submitFlow(record)">提交项目负责人审核</a-menu-item>
                    <a-menu-item v-if="record.status === 'APPROVING' && record.flowNode === '项目负责人'" key="revoke" @click="revoke(record)">撤销</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 项目基本信息（对齐沉淀文档） -->
    <a-drawer v-model:open="open" :title="reviewing ? '待我审核 · 项目申报详情' : formMode === 'view' ? '查看项目申报' : editing ? '编辑项目申报' : '新建项目申报'" :width="'70%'" :destroy-on-close="true">
      <a-alert
        v-if="reviewing"
        type="warning"
        show-icon
        class="review-detail-alert"
        :message="`${reviewing.applyNo || ''} · ${reviewing.name || ''}`"
        :description="`当前审核节点：${reviewing.flowNode || '—'}。请核对下方申报信息和材料后再执行审批。`"
      />
      <a-steps
        :current="declarationStep"
        size="small"
        class="declare-steps"
        :items="[
          { title: '选择立项渠道' },
          { title: '填报与材料上传' },
          { title: '确认提交' },
          { title: '完成' },
        ]"
      />

      <div v-if="!form.channelId" class="channel-first">
        <div class="channel-guide">
          <div>
            <b>请先选定立项渠道</b>
            <p>系统将自动带出项目层级、归口部门、申报材料和审签流程，无需逐项填写渠道属性。</p>
          </div>
          <a-space v-if="user.isAdmin">
            <a-button @click="channelMaintOpen = !channelMaintOpen">
              {{ channelMaintOpen ? '收起渠道维护' : '展开渠道维护' }}
            </a-button>
            <a-button type="link" @click="goChannelMaintenance">进入完整维护页</a-button>
          </a-space>
        </div>

        <div v-if="channelMaintOpen && user.isAdmin" class="channel-maint">
          <div class="channel-maint-title">系统管理员·渠道配置概览</div>
          <a-table
            :data-source="dictStore.channels"
            row-key="id"
            size="small"
            :pagination="false"
            :scroll="{ x: 1000, y: 260 }"
            :columns="[
              { title: '层级', dataIndex: 'levelCode', width: 90 },
              { title: '渠道', dataIndex: 'channelName', width: 170 },
              { title: '归口', key: 'owner', width: 180 },
              { title: '申报材料', dataIndex: 'declareMaterial', width: 220 },
              { title: '全周期流程', dataIndex: 'flowNodes' },
            ]"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'levelCode'">{{ LEVEL_TEXT[record.levelCode] }}</template>
              <template v-else-if="column.key === 'owner'">{{ channelPath(record) }}</template>
            </template>
          </a-table>
        </div>

        <section v-for="group in channelGroups" :key="group.levelCode" class="channel-group">
          <div class="channel-group-hd">
            <a-tag :color="group.levelCode === 'NATIONAL' ? 'blue' : group.levelCode === 'LOCAL' ? 'orange' : 'cyan'">
              {{ group.title }}
            </a-tag>
            <span>{{ group.channels.length }} 个项目类型</span>
          </div>
          <div class="channel-grid">
            <button
              v-for="c in group.channels"
              :key="c.id"
              type="button"
              class="channel-card"
              @click="selectChannel(c)"
            >
              <b>{{ c.channelName }}</b>
              <span>{{ channelPath(c) }}</span>
              <p>{{ channelFlowPreview(c) }}</p>
            </button>
          </div>
        </section>
      </div>

      <template v-else>
      <div class="selected-channel-bar">
        <div>
          <span class="selected-label">已选渠道</span>
          <b>{{ selectedChannel?.channelName }}</b>
          <a-tag color="blue">{{ LEVEL_TEXT[selectedChannel?.levelCode || 'NATIONAL'] }}</a-tag>
          <small>{{ channelPath(selectedChannel) }}</small>
        </div>
        <a-button v-if="!formReadonly" type="link" @click="clearChannel">重新选择渠道</a-button>
      </div>
      <div class="decl-form-title">项目基本信息</div>
      <a-form layout="vertical" class="decl-form" :disabled="formReadonly">
        <div v-if="!formReadonly" class="ai-entry">
          <div class="ai-entry-hd">
            <RobotOutlined />
            <span>AI 识别任务书</span>
          </div>
          <div class="ai-entry-bd">
            <span class="ai-entry-hint">上传任务书/申请书（PDF、Word），自动识别并回填项目名称、目标、经费、专业与渠道材料清单。</span>
            <a-upload
              :show-upload-list="false"
              accept=".pdf,.doc,.docx,.png,.jpg,.jpeg"
              :custom-request="() => {}"
              :before-upload="() => false"
              @change="onPickTaskBook"
            >
              <a-button type="primary" ghost :loading="recognizing">
                <UploadOutlined />{{ recognizing ? '识别中…' : '上传并识别' }}
              </a-button>
            </a-upload>
          </div>
        </div>

        <a-form-item label="项目名称" required>
          <div class="name-row">
            <a-input v-model:value="form.name" placeholder="按立项文件全称填写" allow-clear />
            <a-button :loading="checking" @click="onCheckDuplicate">项目查重</a-button>
          </div>
        </a-form-item>

        <div class="mat-box">
          <div class="mat-box-hd">按渠道上传申报材料</div>
          <a-alert v-if="!form.channelId" type="warning" show-icon message="请先选择项目渠道，系统将按渠道开放对应材料栏。" style="margin-bottom: 8px" />
          <template v-else>
            <div class="field-hint" style="margin-bottom: 8px">
              当前渠道：{{ selectedChannel?.channelName }}。提交前须齐套上传；可先选择文件再点「暂存」。
            </div>
            <a-table :data-source="formMats" row-key="fieldCode" :pagination="false" size="small"
              :columns="[
                { title: '材料名称', dataIndex: 'fieldName', width: 200 },
                { title: '文件', key: 'file' },
                { title: '操作', key: 'act', width: 120 },
              ]">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'file'">
                  <span v-if="record.pendingName">待写入：{{ record.pendingName }}</span>
                  <span v-else-if="record.fileName">{{ record.fileName }}</span>
                  <a-tag v-else color="orange">未上传</a-tag>
                </template>
                <template v-else-if="column.key === 'act'">
                  <template v-if="formReadonly">
                    <a v-if="record.fileUrl" :href="record.fileUrl" target="_blank" rel="noreferrer">打开</a>
                    <span v-else>—</span>
                  </template>
                  <a-upload
                    v-else
                    :show-upload-list="false"
                    accept=".pdf,.doc,.docx,.zip,.png,.jpg"
                    :before-upload="() => false"
                    :custom-request="() => {}"
                    @change="(info: any) => onPickChannelFile(record, info)"
                  >
                    <a-button type="link" size="small">{{ record.fileName || record.pendingName ? '替换' : '上传' }}</a-button>
                  </a-upload>
                </template>
              </template>
            </a-table>
          </template>
        </div>

        <a-alert type="info" show-icon class="channel-auto-rule">
          <template #message>
            审签方式、项目层级及归口信息已随渠道自动确定：
            <b>{{ form.needApproval ? '按渠道走线上审签' : '无需审批，直接线上报备' }}</b>
          </template>
        </a-alert>

        <a-form-item label="项目目标">
          <a-textarea v-model:value="form.goal" :rows="3" placeholder="项目整体目标..." />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="申请经费（万元）">
              <a-input-number v-model:value="form.applyFund" :min="0" :precision="2" style="width: 100%" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="开始时间">
              <a-date-picker v-model:value="form.startDate" style="width: 100%" value-format="YYYY-MM-DD" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="结束时间">
              <a-date-picker v-model:value="form.endDate" style="width: 100%" value-format="YYYY-MM-DD" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="参研单位">
          <a-input v-model:value="form.partnerOrgs" placeholder="如：北研中心、南京航空航天大学" allow-clear />
          <div class="field-hint">多个单位以顿号/逗号分隔</div>
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="一级专业" required>
              <a-select v-model:value="form.major1" placeholder="请选择" allow-clear show-search
                :options="major1Options.map((x) => ({ value: x, label: x }))"
                @change="onMajor1Change" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="二级专业" required>
              <a-select v-model:value="form.major2" :placeholder="form.major1 ? '请选择' : '请先选一级专业'"
                :disabled="!form.major1" allow-clear show-search
                :options="major2Options.map((x) => ({ value: x, label: x }))" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="管理/需求单位">
          <a-input v-model:value="form.demandOrg" placeholder="内部项目可与责任单位相同" allow-clear />
        </a-form-item>

        <a-form-item label="责任单位/牵头单位" required>
          <a-select v-model:value="form.leadOrgName" placeholder="请选择" show-search
            :options="LEAD_ORGS.map((x) => ({ value: x, label: x }))" />
        </a-form-item>

        <a-form-item label="牵头单位主要工作内容" required>
          <a-textarea v-model:value="form.leadWorkContent" :rows="3" placeholder="牵头单位在本项目中的主要任务" />
        </a-form-item>

        <a-alert
          :type="coveredPostCount >= ALL_POST_KEYS.length ? 'success' : 'info'"
          show-icon
          style="margin-bottom: 12px"
          :message="coveredPostCount >= ALL_POST_KEYS.length
            ? `候选人员已覆盖全部 ${ALL_POST_KEYS.length} 个申报岗位，可完成项目全流程流转`
            : `已选择 ${coveredPostCount}/${ALL_POST_KEYS.length} 个申报岗位，成员库已覆盖全部岗位人选`"
        />

        <div class="person-recommend-tip">
          <div>人员下拉默认仅推荐与当前岗位匹配的人员；输入<b>工号</b>可在全部成员中精确检索。</div>
          <div class="person-flow-link">
            <span class="link-mark">联动</span>
            此处选定人员将自动绑定申报审签节点，并在立项后同步为实施、验收、成果转化流程的对应办理人。
          </div>
        </div>

        <div class="post-table">
          <div class="post-head">
            <div class="post-col-role">申报须填岗位</div>
            <div class="post-col-person">姓名及工号</div>
          </div>
          <template v-for="g in POST_GROUPS" :key="g.name">
            <div class="post-group-row">
              <div class="post-group-name">{{ g.name }}</div>
              <div class="post-group-body">
                <div v-for="p in g.posts" :key="p.key" class="post-row">
                  <div class="post-col-role">
                    <span class="req">*</span>{{ p.label }}
                  </div>
                  <div class="post-col-person">
                    <a-select
                      v-model:value="(form.posts as any)[p.key]"
                      allow-clear
                      show-search
                      option-label-prop="label"
                      :placeholder="`推荐${p.label}，或输入工号搜索全员`"
                      :filter-option="false"
                      @search="(value: string) => { personSearch[p.key] = value }"
                      @change="() => { personSearch[p.key] = '' }"
                      @dropdown-visible-change="(visible: boolean) => { if (!visible) personSearch[p.key] = '' }"
                      style="width: 100%"
                      popup-class-name="decl-person-dropdown"
                    >
                      <a-select-option
                        v-for="u in personOptions(p)"
                        :key="u.id"
                        :value="personLabel(u)"
                        :label="personLabel(u)"
                      >
                        <div class="person-opt">
                          <div class="person-opt-main">
                            {{ personLabel(u) }}
                            <a-tag v-if="isRecommendedPerson(u, p)" color="blue" class="recommend-tag">岗位推荐</a-tag>
                          </div>
                          <div class="person-opt-sub">{{ personMeta(u) }}</div>
                        </div>
                      </a-select-option>
                    </a-select>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>

        <div class="field-hint foot-note">经费仅在上方「申请经费（万元）」填写一次。</div>
      </a-form>
      </template>

      <template #footer>
        <div style="text-align: right">
          <a-space v-if="formReadonly && reviewing">
            <a-button @click="openMaterials(reviewing)">查看材料</a-button>
            <a-button @click="openFlow(reviewing)">查看审批流转</a-button>
            <a-button type="primary" @click="audit(reviewing)">确认审核</a-button>
            <a-button @click="open = false; reviewing = null">关闭</a-button>
          </a-space>
          <a-button v-else-if="formReadonly" type="primary" @click="open = false">关闭</a-button>
          <a-button v-else-if="!form.channelId" @click="open = false">取消</a-button>
          <template v-else>
            <a-button style="margin-right: 8px" @click="open = false">取消</a-button>
            <a-button style="margin-right: 8px" :loading="saving" @click="onSaveDraft">暂存</a-button>
            <a-button type="primary" :loading="submitting" @click="onConfirmSubmit">{{ submitButtonText }}</a-button>
          </template>
        </div>
      </template>
    </a-drawer>

    <a-modal v-model:open="aiOpen" title="任务书识别结果（请核对后回填）" :width="640" ok-text="确认回填" cancel-text="取消" @ok="applyAiPreview">
      <p class="field-hint">来源文件：{{ aiFileName }}</p>
      <a-descriptions bordered size="small" :column="1">
        <a-descriptions-item label="项目名称">{{ aiPreview.name }}</a-descriptions-item>
        <a-descriptions-item label="识别渠道">{{ aiPreview.channelName }}</a-descriptions-item>
        <a-descriptions-item label="申请经费（万元）">{{ aiPreview.applyFund }}</a-descriptions-item>
        <a-descriptions-item label="实施周期">{{ aiPreview.startDate }} ~ {{ aiPreview.endDate }}</a-descriptions-item>
        <a-descriptions-item label="一级 / 二级专业">{{ aiPreview.major1 }} / {{ aiPreview.major2 }}</a-descriptions-item>
        <a-descriptions-item label="项目目标">{{ aiPreview.goal }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <a-drawer v-model:open="matOpen" :title="`申报材料清单 — ${matRow?.name || ''}`" :width="'60%'">
      <a-alert type="info" show-icon style="margin-bottom: 16px">
        <template #message>
          本渠道（{{ matRow?.channelName }}）所需材料已开放上传，其余字段按规则<b>锁定不可编辑</b>。
        </template>
      </a-alert>
      <a-table :data-source="mats" row-key="id" :pagination="false" size="small"
        :columns="[
          { title: '材料名称', dataIndex: 'fieldName', width: 220 },
          { title: '是否本渠道所需', dataIndex: 'locked', width: 140 },
          { title: '当前文件', dataIndex: 'fileName' },
          { title: '版本', dataIndex: 'version', width: 80 },
          { title: '操作', key: 'act', width: 120 },
        ]">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'locked'">
            <a-tag :color="record.locked ? 'default' : 'blue'">{{ record.locked ? '锁定（不适用）' : '必需' }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'fileName'">
            <span v-if="record.fileName">{{ record.fileName }}</span>
            <a-tag v-else color="orange">未上传</a-tag>
          </template>
          <template v-else-if="column.key === 'act'">
            <a-button type="link" size="small" :disabled="!!record.locked" @click="uploadMat(record)">
              {{ record.fileName ? '替换' : '上传' }}
            </a-button>
          </template>
        </template>
      </a-table>
    </a-drawer>

    <DeclareFlowDialog v-model:open="flowOpen" :declaration="flowDecl" />
  </div>
</template>

<style scoped>
.declare-steps {
  margin: 4px 0 24px;
  padding: 14px 18px;
  background: #f7faff;
  border-radius: 6px;
}
.channel-first {
  padding-bottom: 16px;
}
.channel-guide {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  margin-bottom: 20px;
  background: #fff;
  border: 1px solid #d6e4ff;
  border-left: 4px solid #0064ef;
  border-radius: 6px;
}
.channel-guide b {
  display: block;
  color: #1f1f1f;
  font-size: 15px;
}
.channel-guide p {
  margin: 5px 0 0;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 20px;
}
.channel-maint {
  margin-bottom: 20px;
  padding: 12px;
  background: #fafafa;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
}
.channel-maint-title {
  margin-bottom: 10px;
  color: #0048a0;
  font-weight: 600;
}
.channel-group {
  margin-bottom: 22px;
}
.channel-group-hd {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: #8c8c8c;
  font-size: 12px;
}
.channel-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.channel-card {
  min-height: 118px;
  padding: 16px 18px;
  text-align: left;
  font: inherit;
  color: inherit;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  cursor: pointer;
  box-shadow: 0 5px 16px rgba(0, 72, 160, 0.06);
  transition: border-color .16s ease, box-shadow .16s ease, transform .16s ease;
}
.channel-card:hover {
  border-color: #0064ef;
  box-shadow: 0 8px 22px rgba(0, 100, 239, 0.14);
  transform: translateY(-1px);
}
.channel-card b,
.channel-card span,
.channel-card p {
  display: block;
}
.channel-card b {
  margin-bottom: 7px;
  color: #1f1f1f;
  font-size: 15px;
}
.channel-card span {
  color: #8c8c8c;
  font-size: 12px;
}
.channel-card p {
  margin: 8px 0 0;
  color: #595959;
  font-size: 12px;
  line-height: 19px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.selected-channel-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #e6f4ff;
  border: 1px solid #91caff;
  border-radius: 6px;
}
.selected-channel-bar > div {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}
.selected-channel-bar .selected-label,
.selected-channel-bar small {
  color: #8c8c8c;
  font-size: 12px;
}
.selected-channel-bar b {
  color: #0048a0;
  font-size: 14px;
}
.channel-auto-rule {
  margin-bottom: 16px;
}
.decl-form-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e8e8e8;
}
.ai-entry {
  border: 1px solid #d6e4ff;
  background: #f7faff;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.ai-entry-hd {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #0048a0;
  font-weight: 600;
  margin-bottom: 8px;
}
.ai-entry-bd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.ai-entry-hint {
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
}
.mat-box {
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: #fff;
}
.mat-box-hd {
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 8px;
}
.name-row {
  display: flex;
  gap: 8px;
}
.name-row .ant-input {
  flex: 1;
}
.field-hint {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}
.foot-note {
  margin-top: 12px;
}
.person-recommend-tip {
  margin: 0 0 10px;
  padding: 9px 12px;
  color: #595959;
  font-size: 12px;
  line-height: 20px;
  background: #f7faff;
  border: 1px solid #d6e4ff;
  border-radius: 4px;
}
.person-recommend-tip b {
  color: #0064ef;
}
.person-flow-link {
  margin-top: 3px;
  color: #2457a6;
}
.link-mark {
  display: inline-block;
  margin-right: 6px;
  padding: 0 6px;
  color: #096dd9;
  line-height: 18px;
  background: #e6f4ff;
  border: 1px solid #91caff;
  border-radius: 9px;
}
.post-table {
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;
}
.post-head {
  display: flex;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
  font-weight: 600;
  color: #262626;
}
.post-col-role {
  width: 220px;
  flex-shrink: 0;
  padding: 10px 12px;
  display: flex;
  align-items: center;
}
.post-col-person {
  flex: 1;
  padding: 8px 12px;
  min-width: 0;
}
.post-group-row {
  display: flex;
  border-bottom: 1px solid #e8e8e8;
}
.post-group-row:last-child {
  border-bottom: none;
}
.post-group-name {
  width: 100px;
  flex-shrink: 0;
  background: #f5f7fa;
  border-right: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: #262626;
  padding: 8px;
  text-align: center;
}
.post-group-body {
  flex: 1;
  min-width: 0;
}
.post-row {
  display: flex;
  border-bottom: 1px solid #f0f0f0;
  align-items: center;
}
.post-row:last-child {
  border-bottom: none;
}
.req {
  color: #ff4d4f;
  margin-right: 4px;
}
.person-opt {
  line-height: 1.35;
  padding: 2px 0;
}
.person-opt-main {
  color: #1f1f1f;
  font-weight: 500;
}
.recommend-tag {
  margin-left: 8px;
  font-size: 11px;
  line-height: 18px;
}
.person-opt-sub {
  color: #8c8c8c;
  font-size: 12px;
  margin-top: 2px;
  white-space: normal;
}
@media (max-width: 1050px) {
  .channel-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .channel-grid { grid-template-columns: 1fr; }
  .channel-guide { flex-direction: column; }
}
</style>

<style>
.decl-person-dropdown .ant-select-item-option-content {
  white-space: normal;
}
.decl-person-dropdown .ant-select-item {
  min-height: 52px;
  padding-top: 6px;
  padding-bottom: 6px;
}
</style>

