<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CheckCircleFilled, RightOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { declarationApi, dictApi, projectApi, acceptanceApi, fileApi } from '@/api/modules'
import { fmtAmount, fmtDate } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import { LEVEL_TEXT, PROJECT_STATUS_TEXT } from '@/api/types'
import {
  buildLifecycle,
  channelPathLabel,
  stageBadge,
  type LifecycleNode,
} from '@/utils/lifecycle'
import { buildDeclareFlowOpts, type DeclareFlowOpts } from '@/utils/declareFlow'
import DeclareFlowDialog from '@/components/declare/DeclareFlowDialog.vue'
import { buildFilingFlowOpts, type FilingFlowOpts } from '@/utils/filingFlow'
import FilingFlowDialog from '@/components/filing/FilingFlowDialog.vue'
import ImplementFlowDialog from '@/components/implement/ImplementFlowDialog.vue'
import { buildImplementFlowOptsFromOverview } from '@/utils/implementFlow'
import AcceptFlowDialog from '@/components/acceptance/AcceptFlowDialog.vue'
import { buildAcceptFlowOptsFromOverview } from '@/utils/acceptFlow'
import TransformFlowDialog from '@/components/transform/TransformFlowDialog.vue'
import { buildTransformFlowOptsFromOverview } from '@/utils/transformFlow'
import {
  inferDeclareStatus,
  liveMaterials,
  mergePosts,
  personLabelOf,
  postsFromTeamMembers,
  channelRequiredMaterials,
  unwrapDeclarationDetail,
} from '@/utils/flowLive'
import { READONLY_FLOW_NOTICE } from '@/utils/flowEntryPolicy'
import { useDictStore } from '@/stores/dict'
import ProjectVizPanel from '@/components/ProjectVizPanel.vue'

const dictStore = useDictStore()
const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const data = ref<any>({})
const active = ref('overview')
const loading = ref(false)
const drawerOpen = ref(false)
const activeNode = ref<LifecycleNode | null>(null)
const flowOpen = ref(false)
const filingFlowOpen = ref(false)
const implementFlowOpen = ref(false)
const acceptFlowOpen = ref(false)
const transformFlowOpen = ref(false)
const maintenanceAuditOpen = ref(false)
const maintenanceAuditKind = ref<'unit' | 'hq'>('unit')
const maintenanceAuditForm = reactive({ pass: true, opinion: '' })

const p = computed(() => data.value.project || {})
const channel = computed(() => data.value.channel || {})
const isMaintenanceProject = computed(() => p.value.dataSource === 'FORM_MAINT')
const maintenanceMaterials = computed(() => data.value.maintenanceMaterials || [])
const maintenanceFlow = computed(() => data.value.maintenanceFlow || {})
const currentChannel = computed(() => {
  if (channel.value?.id || channel.value?.channelName) return channel.value
  const channels = dictStore.channels || []
  return (
    channels.find((c: any) => p.value.channelId && c.id === p.value.channelId) ||
    channels.find((c: any) => p.value.channelName && c.channelName === p.value.channelName) ||
    {}
  )
})
const maintenanceStatusColor = computed(() => {
  const map: Record<string, string> = {
    MAINT_UNIT_REVIEW: 'processing',
    MAINT_HQ_REVIEW: 'blue',
    MAINT_DONE: 'success',
    MAINT_REJECTED: 'error',
  }
  return map[maintenanceFlow.value.status] || 'warning'
})

const ACCEPTANCE_MATERIALS: Record<string, string[]> = {
  UNIT: ['验收申请书', '技术总结报告', '经费决算表', '交付物清单'],
  COMPANY: ['公司级验收申请表', '评审专家意见', '验收结论'],
  NATIONAL: ['国家级验收申请', '主管机关批复', '综合绩效评价材料'],
  LOCAL: ['属地验收申请', '科委验收意见', '综合绩效评价材料'],
}

function acceptMaterialNames(levelCode?: string) {
  if (levelCode === 'NATIONAL') {
    return [...ACCEPTANCE_MATERIALS.UNIT, ...ACCEPTANCE_MATERIALS.COMPANY, ...ACCEPTANCE_MATERIALS.NATIONAL]
  }
  if (levelCode === 'LOCAL') {
    return [...ACCEPTANCE_MATERIALS.UNIT, ...ACCEPTANCE_MATERIALS.LOCAL]
  }
  if (levelCode === 'COMPANY') {
    return [...ACCEPTANCE_MATERIALS.UNIT, ...ACCEPTANCE_MATERIALS.COMPANY]
  }
  return ACCEPTANCE_MATERIALS.UNIT
}

function uniq(list: string[]) {
  return Array.from(new Set(list.map((s) => String(s || '').trim()).filter(Boolean)))
}

function materialCode(stageKey: string, index: number) {
  return `MAINTAIN_MATERIAL_${stageKey}_${String(index + 1).padStart(2, '0')}`
}

const maintenanceRows = computed(() => {
  const rows: any[] = []
  const usedCodes = new Set<string>()
  const records = Array.isArray(maintenanceMaterials.value) ? maintenanceMaterials.value : []
  const pushRows = (stageKey: string, stageName: string, names: string[], required = true) => {
    uniq(names).forEach((name, index) => {
      const code = materialCode(stageKey, index)
      const rec =
        records.find((m: any) => m.fieldCode === code) ||
        records.find((m: any) => m.fieldCode === 'MAINTAIN_MATERIAL' && m.fieldName === name)
      if (rec?.fieldCode) usedCodes.add(rec.fieldCode)
      rows.push({
        key: code,
        code,
        stageKey,
        stageName,
        materialName: name,
        required,
        uploaded: !!(rec?.fileName || rec?.fileUrl || rec?.uploadedAt),
        fileName: rec?.fileName,
        fileUrl: rec?.fileUrl,
        uploadedBy: rec?.uploadedBy,
        uploadedAt: rec?.uploadedAt,
      })
    })
  }

  pushRows('DECLARE', '项目申报', channelRequiredMaterials(currentChannel.value, 'declare'))
  pushRows('FILING', '立项备案', channelRequiredMaterials(currentChannel.value, 'filing'))
  pushRows('IMPLEMENT', '实施阶段', [
    '年度计划与实施方案',
    '里程碑及交付物清单',
    '节点完成佐证材料',
    '阶段检查/评估结论材料',
    '经费预算及核销凭证材料',
    '项目变更申请及支撑材料',
  ])
  pushRows('ACCEPT', '项目验收', acceptMaterialNames(p.value.levelCode))
  pushRows('TRANSFORM', '成果转化', ['成果包材料', '成果转化申请/证明材料'])

  records
    .filter((m: any) => m.fieldCode && !usedCodes.has(m.fieldCode))
    .forEach((m: any) => {
      if (!String(m.fieldCode).startsWith('MAINTAIN_MATERIAL')) return
      rows.push({
        key: m.fieldCode || m.id,
        code: m.fieldCode,
        stageKey: 'OTHER',
        stageName: '其他',
        materialName: m.fieldName || '维护材料',
        required: false,
        uploaded: !!(m.fileName || m.fileUrl || m.uploadedAt),
        fileName: m.fileName,
        fileUrl: m.fileUrl,
        uploadedBy: m.uploadedBy,
        uploadedAt: m.uploadedAt,
      })
    })
  return rows
})

const maintenanceRequiredComplete = computed(() =>
  maintenanceRows.value.filter((row) => row.required).every((row) => row.uploaded),
)
const maintenanceMissingCount = computed(() =>
  maintenanceRows.value.filter((row) => row.required && !row.uploaded).length,
)
const canSubmitMaintenance = computed(() => !!maintenanceFlow.value.canSubmit && maintenanceRequiredComplete.value)
const maintenanceHandlers = computed(() => maintenanceFlow.value.handlers || {})

function maintenanceHandlerText(key: 'owner' | 'unitReviewer' | 'hqReviewer', fallback: string) {
  return maintenanceHandlers.value?.[key] || fallback
}

function livePosts() {
  const decl = data.value.declaration || {}
  return mergePosts(
    postsFromTeamMembers(p.value.teamMembers, {
      contact: p.value.createByName,
      leader: p.value.ownerName,
    }),
    decl.posts,
  )
}

const declareFlowOpts = computed<DeclareFlowOpts>(() => {
  const decl = data.value.declaration || {}
  const inferred = inferDeclareStatus(p.value.status)
  const posts = livePosts()
  const materials = liveMaterials({
    requiredNames: channelRequiredMaterials(channel.value, 'declare'),
    records: data.value.declarationMaterials || decl.materials,
  })
  return buildDeclareFlowOpts(
    {
      applyNo: decl.applyNo || p.value.projectNo,
      name: decl.name || p.value.name,
      channelName: channel.value.channelName || p.value.channelName,
      levelCode: decl.levelCode || p.value.levelCode,
      needApproval: decl.needApproval ?? 1,
      status: decl.status || inferred.status,
      flowNode: decl.flowNode || inferred.flowNode,
      applicant: posts.contact,
      posts: posts as any,
      materials: data.value.declarationMaterials || decl.materials,
      declareMaterial: channel.value.declareMaterial,
      channelDeclareMaterial: channel.value.declareMaterial,
    } as any,
    {
      channelLevel: decl.levelCode || p.value.levelCode,
      materials,
      declareMaterial: channelRequiredMaterials(channel.value, 'declare'),
    },
  )
})

async function openDeclareFlow() {
  await hydrateFlowSources()
  flowOpen.value = true
}

const filingFlowOpts = computed<FilingFlowOpts>(() => {
  const decl = data.value.declaration || {}
  const filing = data.value.filing || {}
  const posts = livePosts()
  const st = String(p.value.status || '')
  return buildFilingFlowOpts({
    applyNo: decl.applyNo || p.value.projectNo,
    name: decl.name || p.value.name,
    channelName: channel.value.channelName || p.value.channelName,
    filingMaterial: channelRequiredMaterials(channel.value, 'filing').join('、'),
    channelFilingMaterial: channelRequiredMaterials(channel.value, 'filing').join('、'),
    filingMaterials: data.value.filingMaterials,
    materials: data.value.declarationMaterials || decl.materials,
    projectStatus: st,
    projectStatusLabel: PROJECT_STATUS_TEXT[st] || undefined,
    status: decl.status,
    filingStatus: filing.status,
    posts: posts as any,
  } as any)
})

async function openFilingFlow() {
  await hydrateFlowSources()
  filingFlowOpen.value = true
}

const implementFlowOpts = computed(() => buildImplementFlowOptsFromOverview(data.value))

function openImplementFlow() {
  load().finally(() => {
    implementFlowOpen.value = true
  })
}

const acceptFlowOpts = computed(() =>
  buildAcceptFlowOptsFromOverview(data.value, { items: data.value.acceptance?.items }),
)

async function openAcceptFlow() {
  await load()
  try {
    const res = await acceptanceApi.detail(id)
    const acc = res.data || {}
    data.value = {
      ...data.value,
      acceptance: { ...(data.value.acceptance || {}), ...acc },
    }
  } catch {
    /* 无验收主记录时仍按概览展示流转 */
  }
  acceptFlowOpen.value = true
}

const transformFlowOpts = computed(() => buildTransformFlowOptsFromOverview(data.value))

function openTransformFlow() {
  transformFlowOpen.value = true
  load()
}

async function hydrateFlowSources() {
  await load()
  const next = { ...data.value }
  let changed = false
  if (p.value.channelId && !(next.channel?.declareMaterial || next.channel?.filingMaterial)) {
    try {
      const fromStore = dictStore.channels.find((c) => c.id === p.value.channelId)
      if (fromStore) {
        next.channel = { ...next.channel, ...fromStore }
        changed = true
      } else {
        const res = await dictApi.channel(p.value.channelId)
        next.channel = { ...next.channel, ...(res.data || {}) }
        changed = true
      }
    } catch {
      /* 渠道拉取失败时仍用概览已有字段 */
    }
  }
  if (!next.declaration && p.value.name) {
    try {
      const res = await declarationApi.page({ keyword: p.value.name, page: 1, size: 20 })
      const records = (res.data as any)?.records || []
      const hit =
        records.find((r: any) => r.name === p.value.name) ||
        records.find((r: any) => r.applyNo && r.applyNo === p.value.projectNo)
      if (hit?.id) {
        const detail = await declarationApi.detail(hit.id)
        const unwrapped = unwrapDeclarationDetail(detail.data)
        next.declaration = { ...hit, ...unwrapped.declaration }
        next.declarationMaterials = unwrapped.materials
        changed = true
      }
    } catch {
      /* 无关联申报单时按项目团队/渠道字典展示 */
    }
  } else if (next.declaration?.id && !next.declarationMaterials?.length) {
    try {
      const mats = await declarationApi.materials(next.declaration.id)
      next.declarationMaterials = mats.data || []
      changed = true
    } catch {
      /* ignore */
    }
  }
  if (changed) data.value = next
}

async function load() {
  loading.value = true
  try {
    const res = await projectApi.overview(id)
    data.value = res.data || {}
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await dictStore.loadChannels().catch(() => undefined)
  await load()
})
const visibleLifecycle = (nodes: LifecycleNode[]) =>
  nodes
    .filter((node) => node.nodeCode !== 'ARCHIVE')
    .map((node, index) => ({ ...node, seq: index + 1 }))

const lifecycle = computed<LifecycleNode[]>(() => {
  if (data.value.lifecycle?.length) return visibleLifecycle(data.value.lifecycle)
  const transforms = Array.isArray(data.value.transforms) ? data.value.transforms : []
  const transformDone =
    transforms.length > 0 && transforms.every((t: any) => t.status === 'DONE')
  return visibleLifecycle(
    buildLifecycle({
      status: p.value.status,
      teamMembers: p.value.teamMembers,
      createByName: p.value.createByName,
      transformDone,
    }),
  )
})

const lifecycleCycles = computed(() => {
  const nodes = lifecycle.value
  const group = (cycleName: string, codes: string[]) => ({
    cycleName,
    nodes: codes.map((code) => nodes.find((node) => node.nodeCode === code)).filter(Boolean) as LifecycleNode[],
  })
  return [
    group('立项准备', ['DECLARE', 'FILING']),
    group('实施推进', ['IMPLEMENT']),
    group('验收闭环', ['ACCEPT']),
    group('成果转化', ['TRANSFORM']),
  ].filter((cycle) => cycle.nodes.length)
})

const channelPath = computed(
  () => data.value.channelPath || channelPathLabel({ ...channel.value, channelName: p.value.channelName }),
)
const channelFlowNodes = computed<string[]>(() => {
  if (data.value.channelFlowNodes?.length) return data.value.channelFlowNodes
  const raw = channel.value.flowNodes || ''
  return String(raw)
    .split('→')
    .map((s: string) => s.trim())
    .filter(Boolean)
})

const msList = computed(() => data.value.milestones || [])
const msDone = computed(() => msList.value.filter((m: any) => m.status === 'DONE' || m.colorStatus === 'GREEN').length)
const msTotal = computed(() => msList.value.length)
const msRate = computed(() => (msTotal.value ? Math.round((msDone.value / msTotal.value) * 100) : 0))

const spent = computed(() => {
  if (p.value.expenseTotal != null) return Number(p.value.expenseTotal) || 0
  return (data.value.payments || []).reduce((s: number, x: any) => s + (x.amount || 0), 0)
})
const spentRate = computed(() => {
  const total = Number(p.value.totalFund) || 0
  if (!total) return 0
  return Math.min(100, Math.round((spent.value / total) * 100))
})

const stage = computed(() => stageBadge(p.value.status))
const currentAnnualGoal = computed(() => {
  const plans = p.value.annualPlans || []
  if (!plans.length) return '—'
  const y = new Date().getFullYear()
  return plans.find((a: any) => a.year === y)?.annualGoal || plans[0].annualGoal || '—'
})

const teamFlat = computed(() => {
  const preferred = [
    '项目负责人',
    '技术负责人',
    '项目主管',
    '一级总师',
    '二级总师',
    '总部处室处长',
    '单位科技部长',
    '单位财务主管',
  ]
  const members = p.value.teamMembers || []
  const list: { roleName: string; userName: string }[] = []
  for (const role of preferred) {
    const hit = members.find((m: any) => m.roleName === role)
    if (hit) list.push({ roleName: hit.roleName, userName: personLabelOf(hit) || hit.userName })
  }
  if (!list.length) {
    return members.slice(0, 8).map((m: any) => ({ roleName: m.roleName, userName: personLabelOf(m) || m.userName }))
  }
  return list
})

const transformStatusText = computed(() => {
  const map: Record<string, string> = {
    APPLIED: '已转化应用',
    CONTINUE: '接续研发立项',
    RESERVE: '技术储备待应用',
    NOT_STARTED: '未启动',
    NEGOTIATING: '洽谈中',
    SIGNED: '已签协议',
    DONE: '已完成',
  }
  if (!(data.value.transforms || []).length && !p.value.transformStatus) return '暂无'
  return map[p.value.transformStatus] || p.value.transformStatus || '暂无'
})

const partnerText = computed(() => {
  const parts = p.value.participants || []
  if (!parts.length) return '暂无'
  return parts.map((x: any) => x.orgName).join('、')
})

const TRANSFORM_STATUS: Record<string, string> = {
  NOT_STARTED: '未启动',
  NEGOTIATING: '洽谈中',
  SIGNED: '已签协议',
  DONE: '已完成',
}
const WAY_TEXT: Record<string, string> = { MODEL: '向型号转化', MARKET: '向市场转化' }
const GRADE_TEXT: Record<string, string> = {
  EXCELLENT: '优秀',
  GOOD: '良好',
  PASS: '合格',
  FAIL: '不合格',
}
const PARTNER_TEXT: Record<string, string> = { LEAD: '牵头', PARTNER: '参研', OUTSOURCE: '科研外协' }

const msColumns = [
  { title: '里程碑', dataIndex: 'name', width: 240 },
  { title: '年度', dataIndex: 'year', width: 80 },
  { title: '计划完成', dataIndex: 'planDate', width: 120 },
  { title: '实际完成', dataIndex: 'actualDate', width: 120 },
  { title: '节点预算(万元)', dataIndex: 'budget', width: 130, align: 'right' as const },
  { title: '状态', dataIndex: 'colorStatus', width: 110 },
  { title: '滞后原因', dataIndex: 'lagReason' },
]
const planColumns = [
  { title: '计划标题', dataIndex: 'title' },
  { title: '来源', dataIndex: 'source', width: 90 },
  { title: '到期日', dataIndex: 'dueDate', width: 120 },
  { title: '责任人', dataIndex: 'owner', width: 100 },
  { title: '状态', dataIndex: 'colorStatus', width: 110 },
]
const dvColumns = [
  { title: '交付物名称', dataIndex: 'name', width: 280 },
  { title: '类型', dataIndex: 'deliverType', width: 120 },
  { title: '应交付', dataIndex: 'dueDate', width: 120 },
  { title: '实际交付', dataIndex: 'deliverDate', width: 120 },
  { title: '权属', dataIndex: 'ownerOrgs', width: 160 },
  { title: '关联成果编号', dataIndex: 'achievementNo', width: 140 },
  { title: '状态', dataIndex: 'colorStatus', width: 110 },
]
const peColumns = [
  { title: '协作单位', dataIndex: 'partnerName', width: 220 },
  { title: '类型', dataIndex: 'partnerType', width: 100 },
  { title: '技术能力', dataIndex: 'techScore', width: 90 },
  { title: '交付质量', dataIndex: 'qualityScore', width: 90 },
  { title: '进度履约', dataIndex: 'progressScore', width: 90 },
  { title: '服务配合', dataIndex: 'serviceScore', width: 90 },
  { title: '合规性', dataIndex: 'complianceScore', width: 90 },
  { title: '总分', dataIndex: 'score', width: 80 },
  { title: '等级', dataIndex: 'grade', width: 90 },
]

const maintenanceColumns = [
  { title: '环节', dataIndex: 'stageName', width: 140 },
  { title: '需维护信息 / 材料', dataIndex: 'materialName' },
  { title: '要求', dataIndex: 'required', width: 80 },
  { title: '上传状态', dataIndex: 'uploaded', width: 110 },
  { title: '已上传文件', dataIndex: 'fileName', width: 260 },
  { title: '上传人 / 时间', dataIndex: 'uploadedAt', width: 180 },
  { title: '操作', dataIndex: 'action', width: 120, fixed: 'right' as const },
]

async function uploadMaintenanceMaterial(options: any, row: any) {
  try {
    const form = new FormData()
    form.append('file', options.file)
    const uploaded = (await fileApi.upload(form, 'form-maint-maintenance')).data as any
    await projectApi.saveMaintenanceMaterial(id, {
      fieldCode: row.code,
      fieldName: row.materialName || '维护材料',
      fileName: uploaded.fileName || options.file?.name,
      fileUrl: uploaded.fileUrl,
      fileSize: uploaded.fileSize || options.file?.size,
    })
    message.success(`已上传：${row.materialName}`)
    options.onSuccess?.(uploaded)
    await load()
  } catch (e: any) {
    options.onError?.(e)
    message.error(e.message || '维护材料上传失败')
  }
}

async function submitMaintenance() {
  try {
    await projectApi.submitMaintenance(id)
    message.success('已提交本单位科技管理部负责人审核')
    await load()
  } catch (e: any) {
    message.error(e.message || '提交失败')
  }
}

function openMaintenanceAudit(kind: 'unit' | 'hq') {
  maintenanceAuditKind.value = kind
  maintenanceAuditForm.pass = true
  maintenanceAuditForm.opinion = ''
  maintenanceAuditOpen.value = true
}

async function confirmMaintenanceAudit() {
  const payload = {
    pass: maintenanceAuditForm.pass,
    opinion: maintenanceAuditForm.opinion,
  }
  try {
    if (maintenanceAuditKind.value === 'unit') {
      await projectApi.unitAuditMaintenance(id, payload)
      message.success(payload.pass ? '单位审核通过，已提交总部主管审核' : '已退回项目负责人维护')
    } else {
      await projectApi.hqAuditMaintenance(id, payload)
      message.success(payload.pass ? '总部主管审核通过，维护完成' : '已退回项目负责人维护')
    }
    maintenanceAuditOpen.value = false
    await load()
  } catch (e: any) {
    message.error(e.message || '审核失败')
  }
}

function openNode(node: LifecycleNode) {
  // 未办理节点也允许只读查看流转/详情（不强制先办完上一阶段）
  if (node.nodeCode === 'DECLARE') {
    openDeclareFlow()
    return
  }
  if (node.nodeCode === 'FILING') {
    openFilingFlow()
    return
  }
  if (node.nodeCode === 'IMPLEMENT') {
    openImplementFlow()
    return
  }
  if (node.nodeCode === 'ACCEPT') {
    openAcceptFlow()
    return
  }
  if (node.nodeCode === 'TRANSFORM') {
    openTransformFlow()
    return
  }
  activeNode.value = node
  drawerOpen.value = true
}

function fmtDot(d?: string) {
  if (!d) return '—'
  return String(d).replace(/-/g, '.').slice(0, 10)
}

function nameInitial(name?: string) {
  const s = String(name || '').replace(/（.*?）|\(.*?\)/g, '').trim()
  return s.slice(0, 1) || '—'
}
</script>

<template>
  <div class="page-container ledger-detail">
    <a-spin :spinning="loading">
      <!-- A. 项目头区 -->
      <div class="detail-header">
        <div class="header-main">
          <a-button type="link" class="back-btn" @click="router.back()">← 返回台账</a-button>
          <div class="title-row">
            <h1 class="proj-name" :title="p.name">{{ p.name || '项目详情' }}</h1>
            <div class="title-tags">
              <a-tag v-if="PROJECT_STATUS_TEXT[p.status]" color="success">
                {{ PROJECT_STATUS_TEXT[p.status] }}
              </a-tag>
              <a-tag v-if="isMaintenanceProject" :color="maintenanceStatusColor">
                {{ maintenanceFlow.statusText || '待维护' }}
              </a-tag>
              <a-tag v-if="stage" :color="stage.color">{{ stage.text }}</a-tag>
              <StatusTag v-if="p.warnColor" :color="p.warnColor" />
            </div>
          </div>
          <div class="meta-row">
            <span class="meta-chip"><em>编号</em>{{ p.projectNo || '—' }}</span>
            <span class="meta-chip"><em>层级</em>{{ LEVEL_TEXT[p.levelCode] || '—' }}</span>
            <span class="meta-chip" :title="channelPath"><em>渠道</em>{{ channelPath }}</span>
            <span class="meta-chip"><em>管理单位</em>{{ p.leadOrgName || '—' }}</span>
            <span class="meta-chip"><em>周期</em>{{ fmtDot(p.startDate) }} – {{ fmtDot(p.endDate) }}</span>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat">
            <div class="stat-label">总经费</div>
            <div class="stat-value">{{ fmtAmount(p.totalFund) }} <small>万元</small></div>
          </div>
          <div class="stat">
            <div class="stat-label">累计支出</div>
            <div class="stat-value">
              {{ spentRate }}%
              <small>{{ fmtAmount(spent) }} 万元</small>
            </div>
            <a-progress :percent="spentRate" :show-info="false" :stroke-width="8" stroke-color="#0064EF" />
          </div>
          <div class="stat">
            <div class="stat-label">里程碑</div>
            <div class="stat-value">{{ msDone }}/{{ msTotal }}</div>
            <a-progress
              :percent="msRate"
              :show-info="false"
              :stroke-width="8"
              :stroke-color="msRate >= 100 ? '#52c41a' : '#0064EF'"
            />
          </div>
        </div>
      </div>

      <!-- B. 生命周期流程条 -->
      <div class="lifecycle-wrap">
        <div class="lifecycle-head">
          <div class="lifecycle-title">项目生命周期</div>
          <span class="lifecycle-hint">点击节点查看只读详情，办理请从左侧任务栏进入</span>
        </div>
        <div class="lifecycle-track">
          <template v-for="(cycle, cycleIdx) in lifecycleCycles" :key="cycle.cycleName">
            <div class="life-cycle">
              <div class="cycle-title">{{ cycle.cycleName }}</div>
              <div class="cycle-nodes">
                <template v-for="(node, nodeIdx) in cycle.nodes" :key="node.nodeCode">
                  <div
                    class="life-node"
                    :class="{
                      done: node.status === 'DONE',
                      todo: node.status === 'TODO',
                      pending: node.status === 'PENDING',
                    }"
                    @click="openNode(node)"
                  >
                    <div class="node-head">
                      <span class="node-seq">{{ String(node.seq).padStart(2, '0') }}</span>
                      <span class="node-name">{{ node.nodeName }}</span>
                      <CheckCircleFilled v-if="node.status === 'DONE'" class="node-check" />
                      <a-tag v-else-if="node.status === 'TODO'" color="processing" class="node-tag">待办</a-tag>
                      <a-tag v-else class="node-tag">未办理</a-tag>
                    </div>
                    <div class="node-body">
                      <div class="owner">{{ node.ownerName }}</div>
                      <template v-if="node.status === 'TODO'">
                        <div class="sub">
                          下一流程：{{ node.nextFlowName }}
                          <template v-if="node.nextHandlerName"> · {{ node.nextHandlerName }}</template>
                        </div>
                        <div class="hint">查看详情</div>
                      </template>
                    </div>
                  </div>
                  <div v-if="nodeIdx < cycle.nodes.length - 1" class="node-connector inner" aria-hidden="true">
                    <RightOutlined />
                  </div>
                </template>
              </div>
            </div>
            <div v-if="cycleIdx < lifecycleCycles.length - 1" class="node-connector" aria-hidden="true">
              <RightOutlined />
            </div>
          </template>
        </div>
      </div>

      <!-- C. Tab 内容 -->
      <a-card :body-style="{ padding: '12px 20px 20px' }" class="detail-card">
        <a-tabs v-model:activeKey="active">
          <a-tab-pane key="overview" tab="概览">
            <div v-if="isMaintenanceProject" class="maintenance-panel">
              <div class="maintenance-head">
                <div>
                  <div class="maintenance-title">待维护项目材料</div>
                  <div class="maintenance-desc">
                    项目负责人上传维护相关材料并提交，本单位科技管理部负责人审核后，流转至总部主管终审。
                  </div>
                </div>
                <a-space wrap>
                  <a-tag :color="maintenanceStatusColor">{{ maintenanceFlow.statusText || '待维护' }}</a-tag>
                  <a-button
                    type="primary"
                    :disabled="!canSubmitMaintenance"
                    @click="submitMaintenance"
                  >
                    提交单位审核
                  </a-button>
                  <a-button
                    v-if="maintenanceFlow.canUnitAudit"
                    type="primary"
                    ghost
                    @click="openMaintenanceAudit('unit')"
                  >
                    单位审核
                  </a-button>
                  <a-button
                    v-if="maintenanceFlow.canHqAudit"
                    type="primary"
                    ghost
                    @click="openMaintenanceAudit('hq')"
                  >
                    总部审核
                  </a-button>
                </a-space>
              </div>
              <a-row :gutter="16" class="maintenance-flow">
                <a-col :span="8">
                  <div class="maintenance-step done">
                    <div class="maintenance-step-title">1 项目负责人上传 / 提交</div>
                    <div class="maintenance-step-person">
                      办理人：{{ maintenanceHandlerText('owner', p.ownerName || '待指定') }}
                    </div>
                  </div>
                </a-col>
                <a-col :span="8">
                  <div
                    class="maintenance-step"
                    :class="{ active: maintenanceFlow.status === 'MAINT_UNIT_REVIEW', done: ['MAINT_HQ_REVIEW', 'MAINT_DONE'].includes(maintenanceFlow.status) }"
                  >
                    <div class="maintenance-step-title">2 本单位科技管理部负责人审核</div>
                    <div class="maintenance-step-person">
                      审核人：{{ maintenanceHandlerText('unitReviewer', '待指定') }}
                    </div>
                  </div>
                </a-col>
                <a-col :span="8">
                  <div
                    class="maintenance-step"
                    :class="{ active: maintenanceFlow.status === 'MAINT_HQ_REVIEW', done: maintenanceFlow.status === 'MAINT_DONE' }"
                  >
                    <div class="maintenance-step-title">3 总部主管审核</div>
                    <div class="maintenance-step-person">
                      审核人：{{ maintenanceHandlerText('hqReviewer', '待指定') }}
                    </div>
                  </div>
                </a-col>
              </a-row>
              <div class="maintenance-files">
                <div class="sub-title-row">
                  <div class="sub-title">维护材料清单</div>
                  <span class="missing-tip" v-if="maintenanceMissingCount">
                    还有 {{ maintenanceMissingCount }} 项必传材料未上传
                  </span>
                </div>
                <a-table
                  size="small"
                  row-key="key"
                  :pagination="false"
                  :data-source="maintenanceRows"
                  :columns="maintenanceColumns"
                  :scroll="{ x: 960 }"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'required'">
                      <a-tag :color="record.required ? 'red' : 'default'">
                        {{ record.required ? '必传' : '选传' }}
                      </a-tag>
                    </template>
                    <template v-else-if="column.dataIndex === 'uploaded'">
                      <a-tag :color="record.uploaded ? 'green' : 'orange'">
                        {{ record.uploaded ? '已上传' : '待上传' }}
                      </a-tag>
                    </template>
                    <template v-else-if="column.dataIndex === 'fileName'">
                      <a v-if="record.fileUrl" :href="record.fileUrl" target="_blank" rel="noopener">
                        {{ record.fileName || '查看附件' }}
                      </a>
                      <span v-else-if="record.fileName">{{ record.fileName }}</span>
                      <span v-else class="empty-text">—</span>
                    </template>
                    <template v-else-if="column.dataIndex === 'uploadedAt'">
                      <div v-if="record.uploadedAt">
                        <div>{{ record.uploadedBy || '—' }}</div>
                        <div class="time-text">{{ fmtDate(record.uploadedAt) }}</div>
                      </div>
                      <span v-else class="empty-text">—</span>
                    </template>
                    <template v-else-if="column.dataIndex === 'action'">
                      <a-upload
                        :show-upload-list="false"
                        :disabled="!maintenanceFlow.canUpload"
                        :custom-request="(options) => uploadMaintenanceMaterial(options, record)"
                      >
                        <a-button size="small" :disabled="!maintenanceFlow.canUpload">
                          {{ record.uploaded ? '重新上传' : '上传' }}
                        </a-button>
                      </a-upload>
                    </template>
                  </template>
                </a-table>
              </div>
              <div class="maintenance-tracks" v-if="(maintenanceFlow.tracks || []).length">
                <div class="sub-title">办理轨迹</div>
                <a-timeline>
                  <a-timeline-item v-for="(track, idx) in maintenanceFlow.tracks" :key="idx">
                    <div class="track-action">{{ track.action }}</div>
                    <div class="track-meta">{{ track.time ? fmtDate(track.time) : '—' }} · {{ track.actor || '—' }}</div>
                    <div v-if="track.opinion" class="track-opinion">意见：{{ track.opinion }}</div>
                  </a-timeline-item>
                </a-timeline>
              </div>
            </div>
            <a-row :gutter="16">
              <a-col :span="14">
                <div class="panel">
                  <div class="panel-title">项目信息</div>
                  <a-descriptions
                    :column="2"
                    size="small"
                    :label-style="{ width: '96px', color: '#8c8c8c' }"
                    :content-style="{ color: '#262626' }"
                  >
                    <a-descriptions-item label="项目目标" :span="2">{{ p.goal || '—' }}</a-descriptions-item>
                    <a-descriptions-item label="年度目标" :span="2">{{ currentAnnualGoal }}</a-descriptions-item>
                    <a-descriptions-item label="项目渠道" :span="2">{{ channelPath }}</a-descriptions-item>
                    <a-descriptions-item label="渠道流程" :span="2">
                      <div class="flow-tags">
                        <a-tag v-for="(t, i) in channelFlowNodes" :key="i" class="flow-tag">{{ t }}</a-tag>
                        <span v-if="!channelFlowNodes.length">—</span>
                      </div>
                    </a-descriptions-item>
                    <a-descriptions-item label="成果转化">{{ transformStatusText }}</a-descriptions-item>
                    <a-descriptions-item label="协作单位">{{ partnerText }}</a-descriptions-item>
                  </a-descriptions>
                </div>
              </a-col>
              <a-col :span="10">
                <div class="panel">
                  <div class="panel-title-row">
                    <div class="panel-title">项目团队</div>
                    <a-space :size="4">
                      <a-button type="link" size="small" @click="openDeclareFlow">审批流转</a-button>
                      <a-button type="link" size="small" @click="openImplementFlow">实施流转</a-button>
                      <a-button type="link" size="small" @click="openAcceptFlow">验收流转</a-button>
                    </a-space>
                  </div>
                  <div class="team-list">
                    <div v-for="(m, i) in teamFlat" :key="i" class="team-item">
                      <span class="team-avatar">{{ nameInitial(m.userName) }}</span>
                      <div class="team-info">
                        <span class="team-name">{{ m.userName }}</span>
                        <span class="team-role">{{ m.roleName }}</span>
                      </div>
                    </div>
                    <a-empty v-if="!teamFlat.length" description="暂无团队成员" />
                  </div>
                </div>
              </a-col>
            </a-row>
          </a-tab-pane>

          <a-tab-pane key="ms">
            <template #tab>里程碑 {{ msDone }}/{{ msTotal }}</template>
            <div style="margin-bottom: 8px">
              <a-button type="primary" ghost size="small" @click="openImplementFlow">查看实施阶段流转图</a-button>
            </div>
            <a-table size="small" row-key="id" :pagination="false" :data-source="msList" :columns="msColumns">
              <template #bodyCell="{ column, record }">
                <template v-if="['planDate', 'actualDate'].includes(column.dataIndex)">{{ fmtDate(record[column.dataIndex]) }}</template>
                <template v-else-if="column.dataIndex === 'budget'"><span class="num-col">{{ fmtAmount(record.budget) }}</span></template>
                <template v-else-if="column.dataIndex === 'colorStatus'"><StatusTag :color="record.colorStatus" /></template>
              </template>
            </a-table>
          </a-tab-pane>

          <a-tab-pane key="plan">
            <template #tab>计划 {{ (data.plans || []).length }}</template>
            <a-table size="small" row-key="id" :pagination="false" :data-source="data.plans || []" :columns="planColumns">
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'dueDate'">{{ fmtDate(record.dueDate) }}</template>
                <template v-else-if="column.dataIndex === 'colorStatus'"><StatusTag :color="record.colorStatus" /></template>
              </template>
            </a-table>
          </a-tab-pane>

          <a-tab-pane key="fund" tab="经费">
            <a-row :gutter="16">
              <a-col :span="12">
                <div style="font-weight: 600; margin-bottom: 8px">节点预算</div>
                <a-table
                  size="small"
                  row-key="id"
                  :pagination="false"
                  :data-source="data.budgets || []"
                  :columns="[
                    { title: '里程碑', dataIndex: 'milestoneName' },
                    { title: '金额(万元)', dataIndex: 'amount', width: 120, align: 'right' },
                    { title: '状态', dataIndex: 'status', width: 100 },
                  ]"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'amount'"><span class="num-col">{{ fmtAmount(record.amount) }}</span></template>
                  </template>
                </a-table>
              </a-col>
              <a-col :span="12">
                <div style="font-weight: 600; margin-bottom: 8px">付款 / 核销</div>
                <a-table
                  size="small"
                  row-key="id"
                  :pagination="false"
                  :data-source="data.payments || []"
                  :columns="[
                    { title: '凭证号', dataIndex: 'voucherNo', width: 150 },
                    { title: '金额(万元)', dataIndex: 'amount', width: 110, align: 'right' },
                    { title: '发生日期', dataIndex: 'occurDate', width: 110 },
                    { title: '核销', dataIndex: 'writeoffStatus', width: 100 },
                  ]"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'amount'"><span class="num-col">{{ fmtAmount(record.amount) }}</span></template>
                    <template v-else-if="column.dataIndex === 'writeoffStatus'">
                      <a-tag :color="record.writeoffStatus === 'WRITTEN' ? 'green' : 'orange'">
                        {{ record.writeoffStatus === 'WRITTEN' ? '已核销' : '待核销' }}
                      </a-tag>
                    </template>
                  </template>
                </a-table>
              </a-col>
            </a-row>
          </a-tab-pane>

          <a-tab-pane key="dv">
            <template #tab>交付物 {{ (data.deliverables || []).filter((d: any) => d.status === 'DELIVERED').length }}/{{ (data.deliverables || []).length }}</template>
            <div style="margin-bottom: 8px">
              <a-button type="primary" ghost size="small" @click="openAcceptFlow">查看项目验收流转</a-button>
            </div>
            <a-table size="small" row-key="id" :pagination="false" :data-source="data.deliverables || []" :columns="dvColumns">
              <template #bodyCell="{ column, record }">
                <template v-if="['dueDate', 'deliverDate'].includes(column.dataIndex)">{{ fmtDate(record[column.dataIndex]) }}</template>
                <template v-else-if="column.dataIndex === 'colorStatus'"><StatusTag :color="record.colorStatus" /></template>
              </template>
            </a-table>
          </a-tab-pane>

          <a-tab-pane key="pe">
            <template #tab>协作评价 {{ (data.partnerEvals || []).length }}</template>
            <a-table size="small" row-key="id" :pagination="false" :data-source="data.partnerEvals || []" :columns="peColumns">
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'partnerType'">{{ PARTNER_TEXT[record.partnerType] || '-' }}</template>
                <template v-else-if="column.dataIndex === 'grade'">
                  <a-tag
                    v-if="record.grade"
                    :color="['green', 'blue', 'orange', 'red'][['EXCELLENT', 'GOOD', 'PASS', 'FAIL'].indexOf(record.grade)]"
                  >
                    {{ GRADE_TEXT[record.grade] }}
                  </a-tag>
                  <span v-else>-</span>
                </template>
              </template>
            </a-table>
          </a-tab-pane>

          <a-tab-pane key="tf">
            <template #tab>成果转化 {{ (data.transforms || []).length }}</template>
            <div style="margin-bottom: 8px">
              <a-button type="primary" ghost size="small" @click="openTransformFlow">查看成果转化流转</a-button>
            </div>
            <a-table
              size="small"
              row-key="id"
              :pagination="false"
              :data-source="data.transforms || []"
              :columns="[
                { title: '成果编号', dataIndex: 'achievementNo', width: 130 },
                { title: '成果名称', dataIndex: 'name', width: 240 },
                { title: '转化方式', dataIndex: 'transformWay', width: 120 },
                { title: '计划转化', dataIndex: 'planDate', width: 120 },
                { title: '状态', dataIndex: 'status', width: 110 },
                { title: '关联交付物', dataIndex: 'itemCount', width: 100 },
              ]"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'transformWay'">{{ WAY_TEXT[record.transformWay] || '-' }}</template>
                <template v-else-if="column.dataIndex === 'planDate'">{{ fmtDate(record.planDate) }}</template>
                <template v-else-if="column.dataIndex === 'status'">{{ TRANSFORM_STATUS[record.status] || '-' }}</template>
              </template>
            </a-table>
          </a-tab-pane>

          <a-tab-pane key="archive" tab="审批与归档">
            <a-descriptions title="验收摘要" :column="3" bordered size="small" style="margin-bottom: 16px">
              <a-descriptions-item label="验收层级">{{ data.acceptance?.acceptLevel || '—' }}</a-descriptions-item>
              <a-descriptions-item label="状态">{{ data.acceptance?.status || '—' }}</a-descriptions-item>
              <a-descriptions-item label="责任总师技术复核">{{ data.acceptance?.expertReview ? '需要' : '不需要' }}</a-descriptions-item>
              <a-descriptions-item label="协作单位评价到期日">{{ fmtDate(data.acceptance?.partnerDueDate) }}</a-descriptions-item>
              <a-descriptions-item label="结论" :span="2">{{ data.acceptance?.conclusion || '—' }}</a-descriptions-item>
            </a-descriptions>
            <div style="font-weight: 600; margin-bottom: 8px">项目变更</div>
            <a-table
              size="small"
              row-key="id"
              :pagination="false"
              style="margin-bottom: 16px"
              :data-source="data.changes || []"
              :columns="[
                { title: '变更单号', dataIndex: 'changeNo', width: 130 },
                { title: '类型', dataIndex: 'changeType', width: 100 },
                { title: '标题', dataIndex: 'title', width: 220 },
                { title: '状态', dataIndex: 'status', width: 100 },
                { title: '当前节点', dataIndex: 'flowNode', width: 180 },
              ]"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'changeType'">
                  <a-tag :color="record.changeType === 'PROJECT' ? 'blue' : 'cyan'">
                    {{ record.changeType === 'PROJECT' ? '项目变更' : '数据变更' }}
                  </a-tag>
                </template>
              </template>
            </a-table>
            <div style="font-weight: 600; margin-bottom: 8px">评估检查</div>
            <a-table
              size="small"
              row-key="id"
              :pagination="false"
              :data-source="data.evaluations || []"
              :columns="[
                { title: '评估名称', dataIndex: 'name', width: 220 },
                { title: '类型', dataIndex: 'evalType', width: 110 },
                { title: '到期日', dataIndex: 'dueDate', width: 120 },
                { title: '结论', dataIndex: 'result', width: 100 },
                { title: '状态', dataIndex: 'status', width: 100 },
              ]"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'dueDate'">{{ fmtDate(record.dueDate) }}</template>
                <template v-else-if="column.dataIndex === 'result'">
                  <a-tag v-if="record.result" :color="record.result === 'PASS' ? 'green' : 'red'">
                    {{ record.result === 'PASS' ? '合格' : '不合格' }}
                  </a-tag>
                  <span v-else>-</span>
                </template>
              </template>
            </a-table>
          </a-tab-pane>

          <a-tab-pane key="viz" tab="项目可视化">
            <ProjectVizPanel :data="data" :loading="loading" />
          </a-tab-pane>
        </a-tabs>
      </a-card>
    </a-spin>

    <!-- 节点下钻弹窗（居中，与生命周期其他节点一致） -->
    <a-modal
      v-model:open="drawerOpen"
      :width="640"
      :footer="null"
      centered
      destroy-on-close
      :title="activeNode ? `${activeNode.nodeName} · 节点详情` : '节点详情'"
    >
      <template v-if="activeNode">
        <a-alert type="info" show-icon :message="READONLY_FLOW_NOTICE" style="margin-bottom: 16px" />
        <a-descriptions bordered size="small" :column="2" style="margin-bottom: 16px">
          <a-descriptions-item label="节点">{{ activeNode.nodeName }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag v-if="activeNode.status === 'DONE'" color="success">已完成</a-tag>
            <a-tag v-else-if="activeNode.status === 'TODO'" color="processing">待办</a-tag>
            <a-tag v-else>未办理</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="负责人">{{ activeNode.ownerRole }} · {{ activeNode.ownerName }}</a-descriptions-item>
          <a-descriptions-item label="下一办理">
            <template v-if="activeNode.nextHandlerName">
              {{ activeNode.nextHandlerRole }} · {{ activeNode.nextHandlerName }}
            </template>
            <template v-else>—</template>
          </a-descriptions-item>
        </a-descriptions>

        <a-tabs>
          <a-tab-pane key="detail" tab="详情">
            <p style="color: #595959; line-height: 1.7">
              本节点为平台级生命周期阶段「{{ activeNode.nodeName }}」。渠道内部子步骤见概览「渠道流程」标签；
              具体业务单据与材料请从左侧任务栏进入对应功能页面办理。
              <template v-if="activeNode.status === 'PENDING'">
                当前为未办理预览，可先查看节点信息与办理路径，正式办理需待上一阶段完成后进行。
              </template>
            </p>
            <a-tag color="blue">办理入口：左侧任务栏 / 对应功能页面</a-tag>
          </a-tab-pane>
          <a-tab-pane key="file" tab="附件">
            <a-empty description="暂无附件（演示环境）" />
          </a-tab-pane>
          <a-tab-pane key="track" tab="办理轨迹">
            <a-timeline>
              <a-timeline-item v-for="(t, i) in activeNode.tracks || []" :key="i" :color="i === 0 ? 'blue' : 'gray'">
                <div style="font-weight: 600">{{ t.action }}</div>
                <div style="color: #8c8c8c; font-size: 12px">{{ t.time }} · {{ t.actor }}</div>
                <div v-if="t.opinion">意见：{{ t.opinion }}</div>
              </a-timeline-item>
            </a-timeline>
            <a-empty v-if="!(activeNode.tracks || []).length" description="暂无轨迹" />
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-modal>

    <a-modal
      v-model:open="maintenanceAuditOpen"
      :title="maintenanceAuditKind === 'unit' ? '本单位科技管理部负责人审核' : '总部主管审核'"
      centered
      @ok="confirmMaintenanceAudit"
    >
      <a-form layout="vertical">
        <a-form-item label="审核结论">
          <a-radio-group v-model:value="maintenanceAuditForm.pass">
            <a-radio :value="true">通过</a-radio>
            <a-radio :value="false">退回</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="审核意见">
          <a-textarea
            v-model:value="maintenanceAuditForm.opinion"
            :rows="4"
            placeholder="请输入审核意见，将写入办理轨迹"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <DeclareFlowDialog v-model:open="flowOpen" :opts="declareFlowOpts" />
    <FilingFlowDialog v-model:open="filingFlowOpen" :opts="filingFlowOpts" />
    <ImplementFlowDialog v-model:open="implementFlowOpen" :opts="implementFlowOpts" />
    <AcceptFlowDialog v-model:open="acceptFlowOpen" :opts="acceptFlowOpts" :overview="data" />
    <TransformFlowDialog v-model:open="transformFlowOpen" :opts="transformFlowOpts" :overview="data" />
  </div>
</template>

<style scoped>
.ledger-detail {
  padding-bottom: 28px;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: stretch;
  gap: 32px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 16px 20px 20px;
  margin-bottom: 16px;
}
.header-main {
  flex: 1;
  min-width: 0;
}
.back-btn {
  padding: 0;
  height: 22px;
  color: #8c8c8c;
  margin-bottom: 8px;
}
.title-row {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-bottom: 12px;
}
.title-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 4px;
}
.proj-name {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
  color: #262626;
  max-width: 100%;
}
.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.meta-chip {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  max-width: 280px;
  padding: 4px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-chip em {
  font-style: normal;
  color: #8c8c8c;
  flex-shrink: 0;
}
.header-stats {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
  align-items: stretch;
}
.stat {
  min-width: 132px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 4px;
}
.stat-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 8px;
  line-height: 1.2;
}
.stat-value small {
  display: inline;
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: #8c8c8c;
}

.lifecycle-wrap {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 16px 20px 20px;
  margin-bottom: 16px;
}
.lifecycle-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
}
.lifecycle-title {
  font-weight: 600;
  font-size: 15px;
  color: #262626;
}
.lifecycle-hint {
  font-size: 12px;
  color: #8c8c8c;
}
.lifecycle-track {
  display: flex;
  align-items: stretch;
  gap: 0;
  overflow-x: auto;
}
.life-cycle {
  flex: 1 0 220px;
  min-width: 220px;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  padding: 10px;
  background: #fff;
}
.life-cycle:first-child {
  flex-basis: 420px;
  min-width: 420px;
}
.cycle-title {
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
  margin-bottom: 8px;
}
.cycle-nodes {
  display: flex;
  align-items: stretch;
  gap: 0;
}
.life-node {
  flex: 1;
  min-width: 0;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 12px 16px;
  background: #fafafa;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.life-node:hover {
  border-color: #91caff;
}
.life-node.done {
  background: #f6ffed;
  border-color: #d9f7be;
}
.life-node.todo {
  background: #fff;
  border: 2px solid #0064ef;
  box-shadow: 0 0 0 2px rgba(0, 100, 239, 0.08);
}
.life-node.pending {
  background: #fafafa;
}
.node-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.node-seq {
  font-size: 12px;
  color: #8c8c8c;
  font-variant-numeric: tabular-nums;
}
.node-name {
  font-weight: 600;
  font-size: 14px;
  color: #262626;
  flex: 1;
  min-width: 0;
}
.node-tag {
  margin-inline-end: 0;
}
.node-check {
  color: #52c41a;
  font-size: 16px;
}
.node-body {
  font-size: 12px;
  color: #595959;
  line-height: 1.6;
}
.node-body .owner {
  color: #262626;
}
.node-body .sub {
  color: #8c8c8c;
  margin-top: 2px;
}
.node-body .hint {
  margin-top: 8px;
  color: #0064ef;
}
.node-connector {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  flex-shrink: 0;
  color: #bfbfbf;
  font-size: 10px;
}
.node-connector.inner {
  width: 18px;
}

.detail-card {
  border-radius: 4px;
}
.maintenance-panel {
  border: 1px solid #ffd591;
  background: #fffaf0;
  border-radius: 4px;
  padding: 16px;
  margin-bottom: 16px;
}
.maintenance-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.maintenance-title {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 4px;
}
.maintenance-desc {
  color: #8c8c8c;
  font-size: 13px;
}
.maintenance-flow {
  margin-bottom: 12px;
}
.maintenance-step {
  border: 1px solid #f0f0f0;
  background: #fff;
  border-radius: 4px;
  padding: 10px 12px;
  color: #8c8c8c;
  min-height: 66px;
}
.maintenance-step-title {
  font-weight: 600;
}
.maintenance-step-person {
  margin-top: 6px;
  font-size: 12px;
  color: #595959;
  line-height: 1.5;
  word-break: break-all;
}
.maintenance-step.active {
  border-color: #1677ff;
  color: #1677ff;
  background: #e6f4ff;
}
.maintenance-step.done {
  border-color: #b7eb8f;
  color: #389e0d;
  background: #f6ffed;
}
.maintenance-files,
.maintenance-tracks {
  border-top: 1px solid #ffe7ba;
  padding-top: 12px;
  margin-top: 12px;
}
.sub-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.sub-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.sub-title-row .sub-title {
  margin-bottom: 0;
}
.missing-tip {
  color: #fa8c16;
  font-size: 12px;
}
.empty-text,
.time-text {
  color: #8c8c8c;
  font-size: 12px;
}
.track-action {
  font-weight: 600;
}
.track-meta,
.track-opinion {
  color: #8c8c8c;
  font-size: 12px;
  margin-top: 2px;
}
.panel {
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 16px 20px 20px;
  min-height: 280px;
  background: #fff;
}
.panel-title {
  font-weight: 600;
  margin-bottom: 16px;
  font-size: 15px;
  color: #262626;
}
.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}
.panel-title-row .panel-title {
  margin-bottom: 0;
}
.flow-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.flow-tag {
  margin: 0;
  border-radius: 4px;
}
.team-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.team-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.team-item:last-child {
  border-bottom: none;
}
.team-avatar {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  background: #e8f1ff;
  color: #0064ef;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}
.team-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.team-name {
  font-weight: 500;
  color: #262626;
  font-size: 14px;
}
.team-role {
  font-size: 12px;
  color: #8c8c8c;
}
.viz-card {
  text-align: center;
  padding: 24px 12px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
}
.viz-label {
  margin-bottom: 12px;
  color: #8c8c8c;
  font-size: 13px;
}
@media (max-width: 1200px) {
  .detail-header {
    flex-direction: column;
    gap: 16px;
  }
  .header-stats {
    width: 100%;
  }
  .stat {
    flex: 1;
  }
}
</style>
