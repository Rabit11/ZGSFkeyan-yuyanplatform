<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  PlusOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import { projectApi } from '@/api/modules'
import { isSilentAuthError } from '@/api/request'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { fmtAmount } from '@/utils/format'
import { withAllOption } from '@/utils/filterOptions'
import StatusTag from '@/components/StatusTag.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'
import majorConfig from '@/config/major1-major2.json'
import {
  LEVEL_TEXT,
  type ColorStatus,
  type LevelCode,
  type ProjInfo,
  type ProjMilestone,
} from '@/api/types'

type LedgerRow = ProjInfo & {
  leadOrgShort?: string
  canEdit?: boolean
  canDelete?: boolean
  milestoneDone?: number
  milestoneTotal?: number
  milestonePercent?: number
  leaderName?: string
  deliverableDone?: number
  deliverableTotal?: number
  partnerDone?: number
  partnerTotal?: number
  hasBlacklist?: boolean
  transformDone?: number
  transformTotal?: number
  nextMilestone?: ProjMilestone
  nextFallback?: string
}

type ColumnKey =
  | 'projectNo'
  | 'levelChannel'
  | 'majorOrg'
  | 'period'
  | 'status'
  | 'warnColor'
  | 'milestone'
  | 'fundSplit'
  | 'annualFund'
  | 'leader'
  | 'delivery'
  | 'transform'
  | 'nextNode'

const route = useRoute()
const router = useRouter()
const dictStore = useDictStore()
const userStore = useUserStore()

const loading = ref(false)
const rows = ref<LedgerRow[]>([])
const query = reactive<Record<string, any>>({
  keyword: '',
  levelCode: undefined,
  channelId: undefined,
  bureauOffice: undefined,
  projectType: undefined,
  major1: undefined,
  major2: undefined,
  orgId: undefined,
  status: undefined,
  warnColor: undefined,
  dataSource: 'ALL',
})
const drawerOpen = ref(false)
const editingId = ref<number>()
const saving = ref(false)
const form = reactive<any>({})
const { can, guard } = useWorkDuty('ledger', form)
let searchTimer: ReturnType<typeof setTimeout> | undefined

const columnLabels: Record<ColumnKey, string> = {
  projectNo: '编号',
  levelChannel: '层级/渠道',
  majorOrg: '专业/单位',
  period: '项目周期',
  status: '状态',
  warnColor: '预警',
  milestone: '里程碑',
  fundSplit: '经费拆分',
  annualFund: '年度预算/支出',
  leader: '负责人',
  delivery: '交付/协作',
  transform: '成果转化',
  nextNode: '下一节点',
}
const visibleColumns = ref<ColumnKey[]>(Object.keys(columnLabels) as ColumnKey[])

const STATUS_FILTER = [
  { value: 'DECLARING', label: '申报中' },
  { value: 'FILING', label: '立项中' },
  { value: 'IMPLEMENTING', label: '实施中' },
  { value: 'ACCEPTING', label: '验收中' },
  { value: 'ACCEPTED', label: '已验收' },
  { value: 'TERMINATED', label: '已终止' },
]

const TRANSFORM_TEXT: Record<string, string> = {
  APPLIED: '已转化应用',
  CONTINUE: '接续研发立项',
  RESERVE: '技术储备待应用',
}

function emptyText(v?: string | number | null) {
  const s = String(v ?? '').trim()
  if (!s || s === '待指定' || s === '未形成成果包' || s === '0/0') return '暂无'
  return s
}

function statusText(status?: string) {
  const map: Record<string, string> = {
    DRAFT: '申报中',
    DECLARING: '申报中',
    FILING: '立项中',
    IMPLEMENTING: '实施中',
    DELAYED: '实施中',
    ACCEPTING: '验收中',
    COMPANY_ACCEPTED: '已验收',
    GOV_ACCEPTED: '已验收',
    FINISHED: '已验收',
    TERMINATED: '已终止',
  }
  return map[status || ''] || status || '暂无'
}

function statusColor(status?: string) {
  const t = statusText(status)
  if (t === '已验收') return 'success'
  if (t === '验收中') return 'warning'
  if (t === '已终止') return 'default'
  return 'processing'
}

function matchStatusFilter(rowStatus?: string, filter?: string) {
  if (!filter) return true
  if (filter === 'ACCEPTED') return ['COMPANY_ACCEPTED', 'GOV_ACCEPTED', 'FINISHED'].includes(rowStatus || '')
  if (filter === 'IMPLEMENTING') return ['IMPLEMENTING', 'DELAYED'].includes(rowStatus || '')
  if (filter === 'DECLARING') return ['DECLARING', 'DRAFT'].includes(rowStatus || '')
  return rowStatus === filter
}

function levelTagColor(level?: LevelCode) {
  if (level === 'NATIONAL') return 'blue'
  if (level === 'LOCAL') return 'purple'
  return 'default'
}

function periodMonths(start?: string, end?: string) {
  if (!start || !end) return '—'
  const m = Math.max(1, dayjs(end).diff(dayjs(start), 'month'))
  return `${m} 个月`
}

function periodRange(start?: string, end?: string) {
  if (!start && !end) return '暂无'
  const a = start ? dayjs(start).format('YYYY-MM') : '—'
  const b = end ? dayjs(end).format('YYYY-MM') : '—'
  return `${a} – ${b}`
}

function shortOrg(name?: string) {
  if (!name) return ''
  return name
    .replace('上海飞机设计研究院', '上飞院')
    .replace('上海飞机制造有限公司', '上飞公司')
    .replace('北京民用飞机技术研究中心', '北研中心')
    .replace('中国商飞客户服务有限公司', '客服公司')
    .replace('中国商飞民用飞机试飞中心', '试飞中心')
    .replace('中国商飞总部', '总部')
}

function warnBarColor(color?: ColorStatus) {
  return (
    {
      RED: '#f5222d',
      YELLOW: '#faad14',
      BLUE: '#0064EF',
      GREEN: '#52c41a',
    } as Record<string, string>
  )[color || 'BLUE']
}

function milestonePercent(row: LedgerRow) {
  if (row.milestoneTotal) return Math.round(((row.milestoneDone || 0) / row.milestoneTotal) * 100)
  if (['COMPANY_ACCEPTED', 'GOV_ACCEPTED', 'FINISHED'].includes(row.status || '')) return 100
  return 0
}

function daysText(date?: string) {
  const days = date ? dayjs(date).startOf('day').diff(dayjs().startOf('day'), 'day') : 0
  return days < 0 ? `超期 ${Math.abs(days)} 天` : `剩余 ${days} 天`
}

function nextTone(date?: string) {
  const days = date ? dayjs(date).startOf('day').diff(dayjs().startOf('day'), 'day') : 31
  return days < 0 ? 'danger' : days <= 30 ? 'warning' : 'muted'
}

const isAdmin = computed(() => userStore.isAdmin)
const isManagement = computed(() => userStore.isManagement)
const isTeam = computed(() => userStore.roles.includes('PROJECT_TEAM'))
const isLeaderReadonly = computed(
  () => userStore.roles.includes('MANAGEMENT') && (userStore.realName === '周明远' || userStore.userId === 2),
)
const canExport = computed(() => isAdmin.value || (isManagement.value && !isLeaderReadonly.value))
const canMutate = computed(() => isAdmin.value || (isManagement.value && !isLeaderReadonly.value))
const canCreateProject = computed(() => canMutate.value)
const canShowActionColumn = computed(() => canMutate.value || rows.value.some((r) => r.canEdit === true || r.canDelete === true))

const roleNotice = computed(() => {
  if (userStore.roles.includes('CHIEF_ENGINEER')) return { text: '技术把关 · 整栏独有', tone: 'orange' }
  if (userStore.isFinance) {
    const unitOnly = !!userStore.orgName && !userStore.orgName.includes('总部') && !userStore.orgName.includes('中国商飞总部')
    return {
      text: unitOnly ? '经费管控 · 无总部拨付、无修改' : '经费管控 · 财务独有',
      tone: 'orange',
    }
  }
  if (isTeam.value) return { text: '我的科研 · 仅本人关联项目', tone: 'orange' }
  if (isLeaderReadonly.value) return { text: '领导只读：看板 / 台账 / 成果转化台账', tone: 'blue' }
  if (isAdmin.value) return { text: '系统管理员 · 全平台项目', tone: 'blue' }
  if (isManagement.value) return { text: '管理视角 · 权限范围内项目', tone: 'blue' }
  return undefined
})

const sourceTabs = [
  { value: 'ALL', label: '全部' },
  { value: 'PLATFORM', label: '平台同步' },
  { value: 'FORM_MAINT', label: '待维护' },
] as const

function setDataSource(v: string) {
  query.dataSource = v
}

const channelOptions = computed(() =>
  dictStore.channels
    .filter((c) => !query.levelCode || c.levelCode === query.levelCode)
    .map((c) => ({ label: c.channelName, value: c.id })),
)
const officeOptions = computed(() => {
  const list = dictStore.channels
    .filter((c) => !query.levelCode || c.levelCode === query.levelCode)
    .filter((c) => !query.channelId || c.id === Number(query.channelId))
  return unique(list.flatMap((c) => [c.channelOffice, c.innerOffice].filter(Boolean) as string[]))
})
const typeOptions = computed(() => unique(rows.value.map((r) => r.projectType)))
const major2Options = computed(() =>
  query.major1
    ? (majorConfig.major2ByMajor1 as Record<string, string[]>)[query.major1] || []
    : Object.values(majorConfig.major2ByMajor1 as Record<string, string[]>).flat(),
)
const orgOptions = computed(() => {
  const map = new Map<number, string>()
  rows.value.forEach((r) => {
    if (r.orgId) map.set(r.orgId, r.leadOrgName || r.orgName || String(r.orgId))
  })
  return [...map].map(([value, label]) => ({ value, label }))
})

const filteredRows = computed(() =>
  rows.value.filter((r) => {
    const key = String(query.keyword || '').trim().toLowerCase()
    if (key && !`${r.name || ''} ${r.projectNo || ''}`.toLowerCase().includes(key)) return false
    if (query.levelCode && r.levelCode !== query.levelCode) return false
    if (query.channelId && r.channelId !== Number(query.channelId)) return false
    if (query.bureauOffice && r.bureauOffice !== query.bureauOffice) return false
    if (query.projectType && r.projectType !== query.projectType) return false
    if (query.major1 && r.major1 !== query.major1) return false
    if (query.major2 && r.major2 !== query.major2) return false
    if (query.orgId && r.orgId !== Number(query.orgId) && r.leadOrgId !== Number(query.orgId)) return false
    const unitAlias = String(route.query.unit || '')
    if (
      !query.orgId &&
      unitAlias &&
      r.leadOrgName !== unitAlias &&
      r.orgName !== unitAlias &&
      String(r.orgId) !== unitAlias &&
      String(r.leadOrgId) !== unitAlias
    )
      return false
    if (!matchStatusFilter(r.status, query.status)) return false
    if (query.warnColor && r.warnColor !== query.warnColor) return false
    if (query.dataSource && query.dataSource !== 'ALL') {
      const src = r.dataSource || 'PLATFORM'
      if (src !== query.dataSource) return false
    }
    return true
  }),
)

const totalFund = computed(() => filteredRows.value.reduce((sum, r) => sum + Number(r.totalFund || 0), 0))
const colorCounts = computed(() =>
  (['RED', 'YELLOW', 'BLUE', 'GREEN'] as ColorStatus[]).reduce(
    (acc, color) => {
      acc[color] = filteredRows.value.filter((r) => r.warnColor === color).length
      return acc
    },
    {} as Record<ColorStatus, number>,
  ),
)

function unique(values: Array<string | undefined>) {
  return [...new Set(values.filter(Boolean) as string[])]
}
function canShow(key: ColumnKey) {
  return visibleColumns.value.includes(key)
}

async function load() {
  loading.value = true
  try {
    // 列表接口已带台账摘要字段，禁止再对每条调用 overview（原先 N+1 是打开延迟的主因）
    const res = await projectApi.page({ page: 1, size: 500 })
    const list = ((res.data as any)?.records || []) as LedgerRow[]
    rows.value = list.map((row) => {
      const next = { ...row, canEdit: row.canEdit === true, canDelete: row.dataSource !== 'FORM_MAINT' && row.canDelete === true }
      if (next.milestonePercent == null) next.milestonePercent = milestonePercent(next)
      if (!next.leaderName) {
        next.leaderName =
          next.ownerName ||
          next.teamMembers?.find((m) => m.roleCode === 'PROJECT_LEADER' || m.roleName === '项目负责人')
            ?.userName ||
          ''
      }
      if (next.nationalFund == null && next.totalFund != null) {
        next.nationalFund = Math.round(Number(next.totalFund) * 0.6 * 100) / 100
        next.selfFund = Math.round((Number(next.totalFund) - next.nationalFund) * 100) / 100
      }
      if (
        !next.nextMilestone &&
        !next.nextFallback &&
        ['IMPLEMENTING', 'DELAYED'].includes(next.status || '')
      ) {
        next.nextFallback = '计划结束'
      }
      return next
    })
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e?.message || '项目台账加载失败')
  } finally {
    loading.value = false
  }
}

function restoreQuery() {
  Object.keys(query).forEach((key) => {
    const value = route.query[key]
    if (value === undefined || value === '') {
      query[key] = key === 'keyword' ? '' : key === 'dataSource' ? 'ALL' : undefined
      return
    }
    query[key] = ['channelId', 'orgId'].includes(key) ? Number(value) : String(value)
  })
  query.keyword ||= ''
  if (!query.dataSource) query.dataSource = 'ALL'
}

function syncUrl() {
  router.replace({
    query: Object.fromEntries(
      Object.entries(query).filter(([, value]) => value !== undefined && value !== ''),
    ),
  })
}

function resetFilters() {
  Object.keys(query).forEach((key) => {
    if (key === 'keyword') query[key] = ''
    else if (key === 'dataSource') query[key] = 'ALL'
    else query[key] = undefined
  })
}

function toggleColor(color: ColorStatus) {
  query.warnColor = query.warnColor === color ? undefined : color
}

function openCreate() {
  editingId.value = undefined
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, {
    status: 'DECLARING',
    warnColor: 'BLUE',
    dataSource: 'PLATFORM',
    acceptStatus: '未验收',
    totalFund: 0,
    nationalFund: 0,
    selfFund: 0,
    yearBudget: 0,
    yearExpense: 0,
    expenseTotal: 0,
  })
  drawerOpen.value = true
}

function openEdit(row: LedgerRow) {
  editingId.value = row.id
  Object.keys(form).forEach((key) => delete form[key])
  const leaderName =
    row.ownerName ||
    row.leaderName ||
    row.teamMembers?.find((m) => m.roleCode === 'PROJECT_LEADER' || m.roleName === '项目负责人')?.userName ||
    ''
  Object.assign(form, { ...row, ownerName: leaderName, leaderName })
  drawerOpen.value = true
}

function openView(row: LedgerRow) {
  router.push(`/overview/detail/${row.id}`)
}

watch(
  () => [form.nationalFund, form.selfFund],
  () => {
    const n = Number(form.nationalFund || 0)
    const s = Number(form.selfFund || 0)
    if ((form.totalFund == null || form.totalFund === 0) && (n || s)) form.totalFund = Math.round((n + s) * 100) / 100
  },
)

async function saveProject() {
  const formMaintOwnerEdit = form.dataSource === 'FORM_MAINT' && form.canEdit === true
  if (!formMaintOwnerEdit && !guard('edit')) return
  if (!String(form.name || '').trim()) return message.warning('请填写项目名称')
  const n = Number(form.nationalFund || 0)
  const s = Number(form.selfFund || 0)
  const t = Number(form.totalFund || 0)
  if (Math.abs(t - (n + s)) > 0.01 && (n || s)) {
    return message.warning('总经费必须等于国拨经费与自筹经费之和（误差≤0.01万元）')
  }
  if (form.major1 && form.major2) {
    const code1 = String(form.major1).slice(0, 2)
    const code2 = String(form.major2).slice(0, 2)
    if (code1 !== code2) {
      return message.warning(`二级专业「${form.major2}」不属于一级专业「${form.major1}」（须按附件1编码前两位严格对应）`)
    }
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      ownerName: form.ownerName || form.leaderName,
      createByName: form.createByName || form.ownerName || form.leaderName,
    }
    if (form.channelId) {
      const ch = dictStore.channels.find((c) => c.id === Number(form.channelId))
      if (ch) {
        payload.channelName = ch.channelName
        payload.levelCode = form.levelCode || ch.levelCode
        payload.bureauOffice = form.bureauOffice || ch.channelOffice
      }
    }
    if (editingId.value) await projectApi.update(editingId.value, payload)
    else await projectApi.create(payload)
    message.success(editingId.value ? '项目已更新' : '项目已添加')
    drawerOpen.value = false
    await load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function removeProject(row: LedgerRow) {
  if (row.dataSource === 'FORM_MAINT') {
    message.warning('表单维护导入的项目请在表单维护页面删除')
    return
  }
  if (row.canDelete === false) return
  Modal.confirm({
    title: '删除项目',
    content: `确认删除“${row.name}”及其关联台账信息？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await projectApi.remove(row.id)
      message.success('项目已删除')
      await load()
    },
  })
}

function exportCsv() {
  const header = [
    '项目编号',
    '项目名称',
    '层级',
    '渠道',
    '牵头单位',
    '开始时间',
    '结束时间',
    '项目状态',
    '预警',
    '总经费(万元)',
    '历年支出',
    '年度预算',
    '年度支出',
    '里程碑进度',
    '项目负责人',
  ]
  const body = filteredRows.value.map((r) => [
    r.projectNo,
    r.name,
    r.levelCode ? LEVEL_TEXT[r.levelCode] : '',
    r.channelName,
    r.leadOrgName || r.orgName,
    r.startDate,
    r.endDate,
    statusText(r.status),
    r.warnColor,
    r.totalFund,
    r.expenseTotal,
    r.yearBudget,
    r.yearExpense,
    `${r.milestoneDone || 0}/${r.milestoneTotal || 0}`,
    r.leaderName,
  ])
  const csv =
    '\uFEFF' +
    [header, ...body]
      .map((line) => line.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
      .join('\n')
  const link = document.createElement('a')
  link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  link.download = `预研项目_总表_项目台账_${filteredRows.value.length}条_${dayjs().format('YYYYMMDD-HHmmss')}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
  message.success(`已导出 ${filteredRows.value.length} 条项目`)
}

watch(
  query,
  () => {
    clearTimeout(searchTimer)
    searchTimer = setTimeout(syncUrl, query.keyword ? 350 : 0)
  },
  { deep: true },
)
watch(
  () => query.levelCode,
  () => {
    if (query.channelId && !channelOptions.value.some((o) => o.value === query.channelId)) query.channelId = undefined
  },
)
watch(
  () => query.channelId,
  () => {
    if (query.bureauOffice && !officeOptions.value.includes(query.bureauOffice)) query.bureauOffice = undefined
  },
)
watch(
  () => query.major1,
  () => {
    if (query.major2 && !major2Options.value.includes(query.major2)) query.major2 = undefined
  },
)
watch(
  () => form.major1,
  () => {
    if (form.major2 && form.major1) {
      const list = (majorConfig.major2ByMajor1 as Record<string, string[]>)[form.major1] || []
      if (!list.includes(form.major2)) form.major2 = undefined
    }
  },
)

onMounted(async () => {
  await Promise.all([dictStore.loadChannels(), dictStore.load('PROJECT_LEVEL')])
  restoreQuery()
  await load()
  restoreQuery()
})
</script>

<template>
  <div class="page-container ledger-page">
    <div class="page-heading">
      <div>
        <h2 class="page-title">项目台账</h2>
        <div class="page-desc">
          按渠道、状态和专业筛选项目，点击项目名称查看全生命周期信息。
        </div>
      </div>
      <a-space>
        <a-button v-if="canCreateProject" type="primary" @click="openCreate">
          <PlusOutlined />添加项目
        </a-button>
        <a-button v-if="canExport" @click="exportCsv">
          <DownloadOutlined />导出 Excel
        </a-button>
      </a-space>
    </div>

    <details class="duty-disclosure"><summary>岗位职责与操作权限<span>展开查看</span></summary><WorkDutyBar code="ledger" :project="form" /></details>

    <div v-if="roleNotice" class="role-notice" :class="roleNotice.tone">
      <span class="role-notice-text">{{ roleNotice.text }}</span>
      <div class="source-tabs" role="tablist" aria-label="项目来源分类">
        <button
          v-for="tab in sourceTabs"
          :key="tab.value"
          type="button"
          class="source-tab"
          :class="{ active: query.dataSource === tab.value }"
          @click="setDataSource(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>
    <div v-else class="role-notice blue">
      <span class="role-notice-text">项目来源</span>
      <div class="source-tabs" role="tablist" aria-label="项目来源分类">
        <button
          v-for="tab in sourceTabs"
          :key="tab.value"
          type="button"
          class="source-tab"
          :class="{ active: query.dataSource === tab.value }"
          @click="setDataSource(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>

    <a-card class="ledger-card" :body-style="{ padding: '16px 20px 12px' }">
      <div class="filter-heading"><strong>筛选项目</strong><span>筛选即时生效</span></div>
      <div class="toolbar">
        <div class="filter-bar">
          <a-input v-model:value="query.keyword" aria-label="项目名称或编号" placeholder="项目名称 / 编号" allow-clear style="width: 210px">
            <template #prefix><SearchOutlined /></template>
          </a-input>
          <a-select
            v-model:value="query.levelCode" aria-label="项目层级"
            placeholder="全部层级"
            allow-clear
            style="width: 116px"
            :options="withAllOption(dictStore.options('PROJECT_LEVEL'))"
          />
          <a-select
            v-model:value="query.channelId" aria-label="来源渠道"
            placeholder="全部渠道"
            allow-clear
            show-search
            option-filter-prop="label"
            style="width: 190px"
            :options="withAllOption(channelOptions)"
          />
          <a-select
            v-model:value="query.bureauOffice" aria-label="司局或处室"
            placeholder="全部司局/处室"
            allow-clear
            style="width: 150px"
            :options="withAllOption(officeOptions.map((value) => ({ value, label: value })))"
          />
          <a-select
            v-model:value="query.projectType" aria-label="项目类型"
            placeholder="全部项目类型"
            allow-clear
            style="width: 140px"
            :options="withAllOption(typeOptions.map((value) => ({ value, label: value })))"
          />
          <a-select
            v-model:value="query.major1" aria-label="一级专业"
            placeholder="全部一级专业"
            allow-clear
            show-search
            style="width: 155px"
            :options="withAllOption(majorConfig.major1.map((value) => ({ value, label: value })))"
          />
          <a-select
            v-model:value="query.major2" aria-label="二级专业"
            placeholder="全部二级专业"
            allow-clear
            show-search
            style="width: 180px"
            :options="withAllOption(major2Options.map((value) => ({ value, label: value })))"
          />
          <a-select
            v-if="!isTeam"
            v-model:value="query.orgId" aria-label="所属单位"
            placeholder="全部单位"
            allow-clear
            style="width: 170px"
            :options="withAllOption(orgOptions)"
          />
          <a-select
            v-model:value="query.status" aria-label="项目状态"
            placeholder="全部状态"
            allow-clear
            style="width: 120px"
            :options="withAllOption(STATUS_FILTER)"
          />
          <a-select
            v-model:value="query.warnColor" aria-label="风险预警"
            placeholder="全部预警"
            allow-clear
            style="width: 160px"
            :options="withAllOption([
              { value: 'RED', label: '红色 · 逾期告警' },
              { value: 'YELLOW', label: '黄色 · 临期预警' },
              { value: 'BLUE', label: '蓝色 · 正常推进' },
              { value: 'GREEN', label: '绿色 · 已完成' },
            ])"
          />
          <a-button @click="resetFilters">清空筛选</a-button>
        </div>
        <a-popover placement="bottomRight" trigger="click">
          <template #content>
            <div class="column-picker">
              <div class="picker-tip">项目名称固定显示，不可隐藏</div>
              <a-checkbox-group v-model:value="visibleColumns">
                <a-checkbox v-for="(label, key) in columnLabels" :key="key" :value="key">{{ label }}</a-checkbox>
              </a-checkbox-group>
            </div>
          </template>
          <a-button><SettingOutlined />列配置</a-button>
        </a-popover>
      </div>

      <div class="summary-bar">
        <span>共 <strong>{{ filteredRows.length }}</strong> 项</span>
        <a-divider type="vertical" />
        <span>经费合计 <strong>{{ fmtAmount(totalFund) }}</strong> 万元</span>
        <span class="summary-spacer" />
        <button
          v-for="color in (['RED', 'YELLOW', 'BLUE', 'GREEN'] as ColorStatus[])"
          :key="color"
          class="color-filter"
          :class="{ active: query.warnColor === color }"
          @click="toggleColor(color)"
        >
          <span class="color-dot" :class="`color-${color}`" />
          {{ { RED: '红', YELLOW: '黄', BLUE: '蓝', GREEN: '绿' }[color] }} {{ colorCounts[color] }}
        </button>
        <a-tag v-if="userStore.isFinance" color="blue">项目台账 · 经费视角</a-tag>
        <a-tag v-else-if="isTeam" color="blue">仅显示本人关联项目</a-tag>
        <a-tag v-else-if="isLeaderReadonly" color="default">领导只读</a-tag>
      </div>

      <a-table
        :data-source="filteredRows"
        :loading="loading"
        row-key="id"
        :pagination="false"
        :scroll="{ x: 2480, y: 'calc(100vh - 320px)' }"
        :custom-row="(record: LedgerRow) => ({ onClick: () => openView(record) })"
        class="ledger-table"
      >
        <a-table-column title="项目名称" data-index="name" :width="300" fixed="left">
          <template #default="{ record }">
            <div class="name-cell" @click.stop="openView(record)">
              <span
                class="risk-dot"
                :class="[`color-${record.warnColor || 'BLUE'}`, { pulse: record.warnColor === 'RED' }]"
              />
              <a class="project-name" :title="record.name">{{ emptyText(record.name) }}</a>
              <a-tag v-if="record.dataSource === 'FORM_MAINT'" color="orange" class="todo-tag">待维护</a-tag>
            </div>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('projectNo')" title="编号" data-index="projectNo" :width="140">
          <template #default="{ text }"><span class="mono">{{ emptyText(text) }}</span></template>
        </a-table-column>

        <a-table-column v-if="canShow('levelChannel')" title="层级 / 渠道 / 类型" :width="210">
          <template #default="{ record }">
            <div class="cell-stack">
              <div class="inline-row">
                <a-tag :color="levelTagColor(record.levelCode)" class="mini-tag">
                  {{ record.levelCode ? LEVEL_TEXT[record.levelCode] : '暂无' }}
                </a-tag>
                <span>{{ emptyText(record.channelName) }}</span>
              </div>
              <span class="sub-text">{{ emptyText(record.bureauOffice) }} · {{ emptyText(record.projectType) }}</span>
            </div>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('majorOrg')" title="专业 / 管理单位" :width="220">
          <template #default="{ record }">
            <div class="cell-stack">
              <span>{{ record.major1 || record.major2 ? `${record.major1 || '—'} / ${record.major2 || '—'}` : '暂无' }}</span>
              <span class="sub-text">
                {{ emptyText(record.manageOrgName) }} · {{ emptyText(record.leadOrgShort || shortOrg(record.leadOrgName || record.orgName)) }}
              </span>
            </div>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('period')" title="项目周期" :width="150">
          <template #default="{ record }">
            <div class="cell-stack">
              <span>{{ periodRange(record.startDate, record.endDate) }}</span>
              <span class="sub-text">{{ periodMonths(record.startDate, record.endDate) }}</span>
            </div>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('status')" title="状态" :width="100">
          <template #default="{ record }">
            <a-space direction="vertical" :size="2">
              <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
              <a-tag v-if="record.dataSource === 'FORM_MAINT'" color="orange">待维护</a-tag>
            </a-space>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('warnColor')" title="预警" :width="140">
          <template #default="{ record }">
            <StatusTag :color="record.warnColor" />
            <div v-if="record.status === 'TERMINATED'" class="sub-text tip-line">状态为已终止，预警色表示闭环口径</div>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('milestone')" title="里程碑进度" :width="150">
          <template #default="{ record }">
            <div class="progress-cell">
              <span>{{ record.milestoneDone || 0 }} / {{ record.milestoneTotal || 0 }}</span>
              <a-progress
                :percent="milestonePercent(record)"
                :show-info="false"
                size="small"
                :stroke-color="warnBarColor(record.warnColor)"
              />
            </div>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('fundSplit')" title="经费拆分（万元）" :width="160" align="right">
          <template #default="{ record }">
            <div class="cell-stack num-col">
              <span>{{ fmtAmount(record.totalFund) }}</span>
              <span class="sub-text">国拨 {{ fmtAmount(record.nationalFund) }} / 自筹 {{ fmtAmount(record.selfFund) }}</span>
            </div>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('annualFund')" title="年度预算 / 支出" :width="150" align="right">
          <template #default="{ record }">
            <span class="num-col">{{ fmtAmount(record.yearBudget || 0) }} / {{ fmtAmount(record.yearExpense || 0) }}</span>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('leader')" title="负责人" :width="100">
          <template #default="{ record }">{{ emptyText(record.leaderName) }}</template>
        </a-table-column>

        <a-table-column v-if="canShow('delivery')" title="交付 / 协作" :width="150">
          <template #default="{ record }">
            <div class="cell-stack">
              <span>{{ record.deliverableTotal ? `${record.deliverableDone}/${record.deliverableTotal}` : '暂无' }}</span>
              <span class="sub-text">
                {{ record.partnerTotal ? `${record.partnerDone}/${record.partnerTotal}` : '暂无' }}
                <b v-if="record.hasBlacklist" class="danger-text"> · 含黑名单</b>
              </span>
            </div>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('transform')" title="成果转化" :width="140">
          <template #default="{ record }">
            <span v-if="record.transformTotal" class="brand-text">
              {{ record.transformDone }}/{{ record.transformTotal }} 个成果包
            </span>
            <span v-else>{{ TRANSFORM_TEXT[record.transformStatus || ''] || '暂无' }}</span>
          </template>
        </a-table-column>

        <a-table-column v-if="canShow('nextNode')" title="下一到期节点" :width="180">
          <template #default="{ record }">
            <div v-if="record.nextMilestone" class="cell-stack">
              <span class="ellipsis" :title="record.nextMilestone.name">{{ record.nextMilestone.name }}</span>
              <span class="node-days" :class="nextTone(record.nextMilestone.planDate)">
                {{ daysText(record.nextMilestone.planDate) }}
              </span>
            </div>
            <div v-else-if="record.nextFallback" class="cell-stack">
              <span>{{ record.nextFallback }}</span>
              <span class="node-days" :class="nextTone(record.endDate)">{{ daysText(record.endDate) }}</span>
            </div>
            <span v-else>—</span>
          </template>
        </a-table-column>

        <a-table-column v-if="canShowActionColumn" title="操作" :width="168" fixed="right">
          <template #default="{ record }">
            <a-space :size="0">
              <a-button type="link" size="small" @click.stop="openView(record)"><EyeOutlined />查看</a-button>
              <a-button type="link" size="small" :disabled="record.canEdit !== true" @click.stop="openEdit(record)">
                <EditOutlined />编辑
              </a-button>
              <a-button type="link" danger size="small" :disabled="record.dataSource === 'FORM_MAINT' || record.canDelete === false" :title="record.dataSource === 'FORM_MAINT' ? '请在表单维护页面删除' : undefined" @click.stop="removeProject(record)">
                <DeleteOutlined />
              </a-button>
            </a-space>
          </template>
        </a-table-column>

        <template #emptyText>
          <div class="empty-state">没有符合筛选条件的项目</div>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="drawerOpen" :title="editingId ? '编辑项目台账' : '添加项目'" width="720" destroy-on-close>
      <WorkDutyBar code="ledger" :project="form" />
      <a-form layout="vertical">
        <div class="form-section">项目基本信息</div>
        <div class="form-grid">
          <a-form-item label="项目编号">
            <a-input v-model:value="form.projectNo" :disabled="!!editingId" placeholder="留空则自动编号" />
          </a-form-item>
          <a-form-item label="项目状态">
            <a-select
              v-model:value="form.status"
              :options="[
                ...STATUS_FILTER.filter((s) => s.value !== 'ACCEPTED'),
                { value: 'FINISHED', label: '已验收' },
              ]"
            />
          </a-form-item>
          <a-form-item label="项目名称" required class="span-2">
            <a-input v-model:value="form.name" placeholder="按立项文件全称填写" />
          </a-form-item>
          <a-form-item label="级别">
            <a-select v-model:value="form.levelCode" :options="dictStore.options('PROJECT_LEVEL')" allow-clear />
          </a-form-item>
          <a-form-item label="项目来源 / 渠道">
            <a-select
              v-model:value="form.channelId"
              :options="channelOptions"
              show-search
              option-filter-prop="label"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="司局 / 处室">
            <a-input v-model:value="form.bureauOffice" placeholder="如：装备二司" />
          </a-form-item>
          <a-form-item label="项目类型">
            <a-select
              v-model:value="form.projectType"
              allow-clear
              :options="['预先研究', '应用研究', '基础研究', '试验验证'].map((v) => ({ value: v, label: v }))"
            />
          </a-form-item>
          <a-form-item label="一级专业">
            <a-select
              v-model:value="form.major1"
              allow-clear
              show-search
              :options="majorConfig.major1.map((value) => ({ value, label: value }))"
            />
          </a-form-item>
          <a-form-item label="二级专业">
            <a-select
              v-model:value="form.major2"
              allow-clear
              show-search
              :placeholder="form.major1 ? '请选择' : '请先选一级专业'"
              :options="
                (form.major1
                  ? (majorConfig.major2ByMajor1 as Record<string, string[]>)[form.major1] || []
                  : []
                ).map((value) => ({ value, label: value }))
              "
            />
          </a-form-item>
          <a-form-item label="责任单位">
            <a-input v-model:value="form.leadOrgName" />
          </a-form-item>
          <a-form-item label="管理 / 需求单位">
            <a-input v-model:value="form.manageOrgName" />
          </a-form-item>
          <a-form-item label="负责人">
            <a-input v-model:value="form.ownerName" placeholder="项目负责人姓名" />
          </a-form-item>
          <a-form-item label="开始日期">
            <a-date-picker v-model:value="form.startDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-item>
          <a-form-item label="结束日期">
            <a-date-picker v-model:value="form.endDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-item>
        </div>

        <div class="form-section">经费情况（万元）</div>
        <div class="form-grid">
          <a-form-item label="总经费"><a-input-number v-model:value="form.totalFund" :min="0" :precision="2" style="width: 100%" /></a-form-item>
          <a-form-item label="国拨经费"><a-input-number v-model:value="form.nationalFund" :min="0" :precision="2" style="width: 100%" /></a-form-item>
          <a-form-item label="自筹经费"><a-input-number v-model:value="form.selfFund" :min="0" :precision="2" style="width: 100%" /></a-form-item>
          <a-form-item label="累计支出"><a-input-number v-model:value="form.expenseTotal" :min="0" :precision="2" style="width: 100%" /></a-form-item>
          <a-form-item label="年度预算"><a-input-number v-model:value="form.yearBudget" :min="0" :precision="2" style="width: 100%" /></a-form-item>
          <a-form-item label="年度实际执行"><a-input-number v-model:value="form.yearExpense" :min="0" :precision="2" style="width: 100%" /></a-form-item>
        </div>

        <div class="form-section">目标与备注</div>
        <div class="form-grid">
          <a-form-item label="项目目标" class="span-2"><a-textarea v-model:value="form.goal" :rows="3" /></a-form-item>
          <a-form-item label="备注" class="span-2"><a-textarea v-model:value="form.mainWork" :rows="2" /></a-form-item>
        </div>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="drawerOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="saveProject">保存</a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.ledger-page {
  min-width: 0;
}
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 4px;
}
.role-notice {
  padding: 9px 14px;
  margin-bottom: 12px;
  border-radius: 4px;
  font-size: 13px;
  border: 1px solid;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.role-notice-text {
  font-weight: 500;
}
.source-tabs {
  display: inline-flex;
  gap: 4px;
  background: rgba(255, 255, 255, 0.65);
  border-radius: 4px;
  padding: 2px;
  border: 1px solid rgba(0, 0, 0, 0.06);
}
.source-tab {
  border: none;
  background: transparent;
  height: 28px;
  padding: 0 12px;
  border-radius: 3px;
  cursor: pointer;
  color: #595959;
  font-size: 13px;
}
.source-tab.active {
  background: #0064ef;
  color: #fff;
}
.role-notice.orange {
  color: #ad4e00;
  background: #fff7e6;
  border-color: #ffd591;
}
.role-notice.blue {
  color: #0958d9;
  background: #e6f4ff;
  border-color: #91caff;
}
.ledger-card {
  border-radius: 4px;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}
.filter-bar {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.summary-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 46px;
  padding: 9px 12px;
  margin: 0 0 12px;
  background: #f7f9fc;
  border: 1px solid #edf0f5;
  border-radius: 4px;
  white-space: nowrap;
}
.summary-bar strong {
  color: var(--zgsf-brand);
  font-size: 17px;
}
.summary-spacer {
  flex: 1;
}
.color-filter {
  appearance: none;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 14px;
  padding: 3px 8px;
  cursor: pointer;
  color: #595959;
}
.color-filter:hover,
.color-filter.active {
  background: #fff;
  border-color: #d9d9d9;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.color-filter .color-dot {
  margin-right: 5px;
}
.ledger-table :deep(.ant-table-tbody > tr) {
  cursor: pointer;
}
.ledger-table :deep(.ant-table-tbody > tr:hover > td) {
  background: #f0f7ff !important;
}
.ledger-table :deep(.ant-table-thead > tr > th),
.ledger-table :deep(.ant-table-tbody > tr:not(.ant-table-measure-row) > td) {
  height: 55px;
}
.ledger-table :deep(.ant-table-cell-fix-left) {
  background: #fff;
}
.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.risk-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.risk-dot.pulse {
  box-shadow: 0 0 0 0 rgba(245, 34, 45, 0.55);
  animation: risk-pulse 1.4s infinite;
}
@keyframes risk-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(245, 34, 45, 0.5);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(245, 34, 45, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(245, 34, 45, 0);
  }
}
.project-name {
  display: block;
  overflow: hidden;
  color: var(--zgsf-brand);
  font-weight: 500;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.todo-tag {
  margin-left: auto;
  flex-shrink: 0;
}
.mono {
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  letter-spacing: 0.02em;
}
.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  line-height: 1.35;
}
.inline-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.mini-tag {
  margin: 0;
}
.sub-text {
  color: #8c8c8c;
  font-size: 12px;
}
.tip-line {
  margin-top: 2px;
  max-width: 120px;
  white-space: normal;
  line-height: 1.3;
}
.progress-cell {
  min-width: 110px;
}
.progress-cell > span {
  font-variant-numeric: tabular-nums;
}
.node-days {
  font-size: 12px;
}
.node-days.danger,
.danger-text {
  color: #f5222d;
}
.node-days.warning {
  color: #d48806;
}
.node-days.muted {
  color: #8c8c8c;
}
.brand-text {
  color: var(--zgsf-brand);
  font-weight: 500;
}
.ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 160px;
}
.empty-state {
  color: #8c8c8c;
  padding: 36px 0;
}
.column-picker {
  width: 330px;
}
.picker-tip {
  color: #8c8c8c;
  margin-bottom: 10px;
}
.column-picker :deep(.ant-checkbox-group) {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 9px 12px;
}
.form-section {
  font-weight: 600;
  margin: 8px 0 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
  color: #262626;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.span-2 {
  grid-column: span 2;
}
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
@media (max-width: 1200px) {
  .summary-spacer {
    display: none;
  }
  .summary-bar {
    overflow-x: auto;
  }
}

/* Compact object workspace, scoped to the project ledger. */
.ledger-page { color:#24364b; }
.page-heading { margin-bottom:16px; align-items:center; }
.page-desc { color:#687b8f; font-size:13px; line-height:1.6; }
.duty-disclosure { margin-bottom:12px;border:1px solid #e1e7ef;border-radius:6px;background:#fff; }
.duty-disclosure summary { padding:10px 14px;cursor:pointer;font-size:13px;color:#40546b; }
.duty-disclosure summary span { float:right;color:#75879a;font-size:12px; }
.duty-disclosure[open] summary { border-bottom:1px solid #edf1f6; }
.role-notice.orange,.role-notice.blue { color:#40546b;background:#fff;border-color:#e1e7ef;border-radius:6px;padding:8px 12px; }
.source-tabs { background:#f2f5f9;border:0; }
.source-tab.active { background:#e5efff;color:#075bb6;font-weight:600; }
.source-tab:focus-visible { outline:2px solid #1677ff;outline-offset:2px; }
.ledger-card { border-color:#e1e7ef;border-radius:6px; }
.filter-heading { display:flex;align-items:center;gap:12px;margin-bottom:12px;font-size:13px; }
.filter-heading span { color:#75879a;font-size:12px; }
.toolbar { align-items:flex-start; }
.filter-bar { display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px;min-width:0; }
.filter-bar :deep(.ant-select),.filter-bar :deep(.ant-input-affix-wrapper) { width:100% !important;min-width:0; }
.filter-bar > :first-child { grid-column:span 2; }
.summary-bar { flex-wrap:wrap;gap:8px 12px;min-height:42px;background:#f5f8fc;border:0;margin-bottom:10px; }
.summary-bar strong { font-size:16px; }
.ledger-table :deep(.ant-table-thead > tr > th) { background:#f3f6fa;color:#41556b;font-size:13px;height:44px; }
.ledger-table :deep(.ant-table-cell) { padding:10px 12px; }
.ledger-table :deep(.ant-table-measure-row),.ledger-table :deep(.ant-table-measure-row > td) { height:0 !important;min-height:0 !important;padding:0 !important;line-height:0 !important;border:0 !important; }
@media(min-width:1600px){.filter-bar{grid-template-columns:repeat(7,minmax(0,1fr));}}
@media(max-width:1050px){.filter-bar{grid-template-columns:repeat(3,minmax(0,1fr));}.summary-spacer{display:none}.page-heading{flex-wrap:wrap}}
@media(max-width:650px){.toolbar{flex-wrap:wrap}.filter-bar{grid-template-columns:repeat(2,minmax(0,1fr));flex-basis:100%}.source-tabs{flex-wrap:wrap}}

</style>
