<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {useVisibleRefresh} from '@/composables/useVisibleRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  changeApi,
  deliverableApi,
  milestoneApi,
  partnerApi,
  projectApi,
  transformApi,
} from '@/api/modules'
import { isSilentAuthError } from '@/api/request'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import EChart from '@/components/EChart.vue'
import { fmtAmount, fmtAxisAmount, fmtYi } from '@/utils/format'
import { withAllOption } from '@/utils/filterOptions'
import {
  aggregateDashboard,
  milestonesFromBoard,
  plansFromMilestoneBoard,
  recordsOf,
  type DashboardCtx,
} from '@/utils/dashboardAgg'
import majorConfig from '@/config/major1-major2.json'

const BRAND = '#0064EF'
const DONE = '#00C91A'
const WARN = '#FF9900'
const LEVEL_COLOR: Record<string, string> = {
  国家级: '#0064EF',
  地方级: '#13C2C2',
  公司级: '#52C41A',
  未明确层级: '#8C8C8C',
}

const router = useRouter()
const route = useRoute()
const dictStore = useDictStore()
const userStore = useUserStore()
const data = ref<any>({})
const loading = ref(false)
const errorMsg = ref('')
const denied = ref(false)
const ledgerCtx = ref<DashboardCtx | null>(null)
const currentYear = new Date().getFullYear()
const yearOptions = Array.from({ length: 5 }, (_, i) => currentYear - 4 + i)

const filter = reactive({
  year: currentYear as number | undefined,
  level: undefined as string | undefined,
  sourceChannel: undefined as string | undefined,
  orgOffice: undefined as string | undefined,
  projectType: undefined as string | undefined,
  major1: undefined as string | undefined,
  major2: undefined as string | undefined,
  unit: undefined as string | undefined,
})

const kpis = computed(() => data.value.kpis || {})
const isPreResearch = computed(
  () => String(route.query.screen || '') === 'pre-research' || route.path.includes('pre-research'),
)
const screenParam = computed(() => (isPreResearch.value ? 'pre-research' : undefined))

const channelOptions = computed(() =>
  dictStore.channels
    .filter((c) => !filter.level || c.levelCode === filter.level)
    .map((c) => ({ label: c.channelName, value: c.channelName })),
)
const officeOptions = computed(() => {
  const list = dictStore.channels
    .filter((c) => !filter.level || c.levelCode === filter.level)
    .filter((c) => !filter.sourceChannel || c.channelName === filter.sourceChannel)
  return [...new Set(list.flatMap((c) => [c.channelOffice, c.innerOffice].filter(Boolean) as string[]))].map(
    (value) => ({ value, label: value }),
  )
})
const major2Options = computed(() =>
  filter.major1
    ? ((majorConfig.major2ByMajor1 as Record<string, string[]>)[filter.major1] || []).map((value) => ({
        value,
        label: value,
      }))
    : [],
)
const typeOptions = computed(() =>
  (data.value.filterMeta?.projectTypes || ['预先研究', '应用研究', '基础研究', '试验验证']).map((value: string) => ({
    value,
    label: value,
  })),
)
const unitOptions = computed(() =>
  (data.value.filterMeta?.units || []).map((u: any) => ({
    value: String(u.id || u.name),
    label: u.name,
  })),
)

function tipBase() {
  return {
    backgroundColor: '#fff',
    borderColor: BRAND,
    borderWidth: 1,
    textStyle: { color: '#262626', fontFamily: 'Microsoft YaHei, PingFang SC, sans-serif', fontSize: 12 },
  }
}

const levelPie = computed(() => {
  const rows = data.value.byLevel || []
  const total = rows.reduce((s: number, x: any) => s + Number(x.count || x.value || 0), 0)
  return {
    tooltip: {
      ...tipBase(),
      trigger: 'item',
      formatter: (p: any) => `${p.name}<br/>项目 ${p.value} 项（${p.percent}%）`,
    },
    legend: { bottom: 0, type: 'scroll' },
    title: {
      text: String(total),
      subtext: '在库项目',
      left: 'center',
      top: '38%',
      textStyle: { fontSize: 22, color: '#262626', fontWeight: 600 },
      subtextStyle: { fontSize: 12, color: '#8c8c8c' },
    },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '44%'],
        data: rows.map((d: any) => ({
          name: d.name,
          value: d.count ?? d.value,
          level: d.level,
          itemStyle: { color: LEVEL_COLOR[d.name] || BRAND },
        })),
        label: { formatter: '{b}\n{c} 项' },
      },
    ],
  }
})

const channelTop = computed(() => {
  const rows = [...(data.value.channelTop8 || data.value.byChannel || [])].slice(0, 8).reverse()
  return {
    tooltip: {
      ...tipBase(),
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (ps: any[]) => {
        const p = ps?.[0]
        return p ? `${p.name}<br/>项目 ${p.value} 项` : ''
      },
    },
    grid: { left: 16, right: 28, top: 10, bottom: 10, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: rows.map((d: any) => d.name),
      axisLabel: {
        fontSize: 11,
        width: 120,
        overflow: 'truncate',
      },
    },
    series: [
      {
        type: 'bar',
        data: rows.map((d: any) => d.count ?? d.value),
        itemStyle: { color: BRAND, borderRadius: [0, 4, 4, 0] },
        barWidth: 14,
      },
    ],
  }
})

const channelRing = computed(() => {
  const rows = data.value.byChannel || []
  return {
    tooltip: { ...tipBase(), trigger: 'item', formatter: '{b}<br/>{c} 项（{d}%）' },
    legend: { type: 'scroll', bottom: 0, height: 42 },
    series: [
      {
        type: 'pie',
        radius: ['42%', '66%'],
        center: ['50%', '42%'],
        data: rows.map((d: any, i: number) => ({
          name: d.name,
          value: d.count ?? d.value,
          itemStyle: { color: ['#0064EF', '#1374C8', '#36CFC9', '#52C41A', '#FAAD14', '#722ED1', '#13C2C2', '#2F54EB'][i % 8] },
        })),
        label: { show: false },
      },
    ],
  }
})

const unitMatrix = computed(() => {
  const matrix = data.value.unitLevelMatrix || { units: [], series: [] }
  const zoom = (matrix.units || []).length > 8
  return {
    tooltip: { ...tipBase(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0 },
    grid: { left: 16, right: 24, top: 36, bottom: zoom ? 28 : 8, containLabel: true },
    xAxis: { type: 'value', name: '项目数量', minInterval: 1 },
    yAxis: { type: 'category', data: matrix.units || [], axisLabel: { fontSize: 11 } },
    dataZoom: zoom
      ? [{ type: 'slider', yAxisIndex: 0, width: 10, right: 4, start: 100 - Math.min(100, (8 / matrix.units.length) * 100), end: 100 }]
      : [],
    series: (matrix.series || []).map((s: any) => ({
      name: s.name,
      type: 'bar',
      stack: 'level',
      data: s.data,
      itemStyle: { color: LEVEL_COLOR[s.name] || BRAND },
    })),
  }
})

const fundTrend = computed(() => {
  const rows = data.value.fundsTrend || []
  return {
    tooltip: {
      ...tipBase(),
      trigger: 'axis',
      formatter: (ps: any[]) => {
        if (!ps?.length) return ''
        const year = ps[0].axisValue
        const budget = ps.find((p) => p.seriesName === '预算')?.value ?? 0
        const expense = ps.find((p) => p.seriesName === '支出')?.value ?? 0
        const rate = ps.find((p) => p.seriesName === '执行率')?.value ?? 0
        return `${year}<br/>预算 ${fmtAmount(budget)} 万元<br/>支出 ${fmtAmount(expense)} 万元<br/>执行率 ${fmtAmount(rate)}%`
      },
    },
    legend: { top: 0 },
    grid: { left: 48, right: 48, top: 36, bottom: 24 },
    xAxis: { type: 'category', data: rows.map((d: any) => d.year) },
    yAxis: [
      { type: 'value', name: '万元', axisLabel: { formatter: (v: number) => fmtAxisAmount(v) } },
      { type: 'value', name: '执行率', axisLabel: { formatter: '{value}%' }, max: 100 },
    ],
    series: [
      { name: '预算', type: 'bar', data: rows.map((d: any) => d.budget), itemStyle: { color: '#91CAFF', borderRadius: [4, 4, 0, 0] } },
      { name: '支出', type: 'bar', data: rows.map((d: any) => d.expense), itemStyle: { color: BRAND, borderRadius: [4, 4, 0, 0] } },
      { name: '执行率', type: 'line', yAxisIndex: 1, data: rows.map((d: any) => d.rate), itemStyle: { color: DONE }, smooth: true },
    ],
  }
})

const fundPie = computed(() => {
  const s = data.value.fundStructure || {}
  return {
    tooltip: {
      ...tipBase(),
      trigger: 'item',
      formatter: (p: any) => `${p.name}<br/>${fmtAmount(p.value)} 万元（${p.percent}%）`,
    },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['46%', '68%'],
        data: [
          { name: '国拨经费', value: s.national || 0, itemStyle: { color: BRAND } },
          { name: '自筹经费', value: s.self || 0, itemStyle: { color: '#36CFC9' } },
          { name: '商飞内部经费', value: s.inner || 0, itemStyle: { color: '#FAAD14' } },
        ],
        label: { formatter: (p: any) => `${p.name}\n${fmtAmount(p.value)}` },
      },
    ],
  }
})

const majorPie = computed(() => {
  const rows = data.value.byMajor1 || []
  return {
    tooltip: {
      ...tipBase(),
      trigger: 'item',
      formatter: (p: any) => `${p.name}<br/>经费 ${fmtAmount(p.value)} 万元（${p.percent}%）`,
    },
    legend: { type: 'scroll', bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['42%', '66%'],
        data: rows.map((d: any, i: number) => ({
          name: d.name,
          value: d.fund ?? d.value,
          major1: d.major1,
          itemStyle: { color: ['#0064EF', '#13C2C2', '#52C41A', '#FAAD14', '#722ED1', '#2F54EB', '#EB2F96', '#8C8C8C'][i % 8] },
        })),
        label: { formatter: '{b}' },
      },
    ],
  }
})

const delivBar = computed(() => {
  const rows = data.value.delivByType || []
  return {
    tooltip: { ...tipBase(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0 },
    grid: { left: 40, right: 16, top: 36, bottom: 28 },
    xAxis: { type: 'category', data: rows.map((d: any) => d.name), axisLabel: { interval: 0, rotate: 20, fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '已交付', type: 'bar', stack: 'd', data: rows.map((d: any) => d.delivered), itemStyle: { color: DONE } },
      { name: '待交付', type: 'bar', stack: 'd', data: rows.map((d: any) => d.pending), itemStyle: { color: '#D9D9D9' } },
    ],
  }
})

const transformFunnel = computed(() => {
  const rows = data.value.transform || []
  return {
    tooltip: { ...tipBase(), trigger: 'item', formatter: '{b}：{c}' },
    series: [
      {
        type: 'funnel',
        left: '12%',
        width: '76%',
        minSize: '20%',
        data: rows.map((d: any, i: number) => ({
          name: d.name,
          value: d.value,
          itemStyle: { color: ['#D9D9D9', '#FAAD14', BRAND, DONE][i] || BRAND },
        })),
        label: { formatter: '{b} {c}' },
      },
    ],
  }
})

const modelBar = computed(() => {
  const rows = [...(data.value.modelTransform || [])].reverse()
  return {
    tooltip: { ...tipBase(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0 },
    grid: { left: 16, right: 24, top: 32, bottom: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: rows.map((d: any) => d.name) },
    series: [
      { name: '已完成', type: 'bar', stack: 'm', data: rows.map((d: any) => d.done), itemStyle: { color: DONE } },
      { name: '转化中', type: 'bar', stack: 'm', data: rows.map((d: any) => d.doing), itemStyle: { color: BRAND } },
    ],
  }
})

const cards = computed(() => {
  const k = kpis.value
  return [
    { key: 'proj', label: '在库项目', value: k.projectCount ?? 0, unit: '项', sub: `在研 ${k.runningCount ?? 0} 项`, tone: '' },
    { key: 'scale', label: '经费总规模', value: fmtYi(k.totalFund), unit: '亿元', sub: `${fmtAmount(k.totalFund)} 万元 · 立项批复口径`, tone: '' },
    { key: 'nat', label: '国拨经费', value: fmtAmount(k.nationalFund), unit: '万元', sub: `自筹 ${fmtAmount(k.selfFund)} 万元`, tone: '' },
    { key: 'year', label: '年度预算', value: fmtAmount(k.yearBudget), unit: '万元', sub: `支出 ${fmtAmount(k.yearExpense)} 万元`, tone: '' },
    {
      key: 'rate',
      label: '总执行率',
      value: `${Number(k.execRateTotal ?? 0).toFixed(2)}`,
      unit: '%',
      sub: `年度执行率 ${Number(k.execRateYear ?? 0).toFixed(2)}%`,
      tone: Number(k.execRateTotal || 0) >= 50 ? 'ok' : 'warn-soft',
    },
    { key: 'overdue', label: '逾期告警', value: k.overdueCount ?? 0, unit: '项', sub: '含里程碑和交付物风险', tone: 'warn' },
    { key: 'pkg', label: '成果包', value: k.packageCount ?? 0, unit: '个', sub: `已完成 ${k.packageDone ?? 0} 个`, tone: '' },
    { key: 'plan', label: '计划办结率', value: `${Number(k.planFinishRate ?? 0).toFixed(2)}`, unit: '%', sub: `待办 ${k.planTodo ?? 0} 项`, tone: '' },
  ]
})

const planColors = computed(() => data.value.planStats?.colors || { red: 0, yellow: 0, blue: 0, green: 0 })

function queryPayload() {
  const none = <T extends string | number>(v: T | undefined) => (v === '' || v == null ? undefined : v)
  return {
    year: none(filter.year),
    level: none(filter.level),
    sourceChannel: none(filter.sourceChannel),
    orgOffice: none(filter.orgOffice),
    projectType: none(filter.projectType),
    major1: none(filter.major1),
    major2: none(filter.major2),
    unit: none(filter.unit),
    screen: screenParam.value,
  }
}

function settledValue<T>(r: PromiseSettledResult<T>, fallback: T): T {
  return r.status === 'fulfilled' ? r.value : fallback
}

async function loadAllProjects() {
  const first = await projectApi.page({ page: 1, size: 500 })
  const d = first.data as any
  const records = [...(d?.records || [])]
  const total = Number(d?.total || records.length)
  const size = Number(d?.size || 500) || 500
  let page = 2
  while (records.length < total && page <= 20) {
    const res = await projectApi.page({ page, size })
    const rec = (res.data as any)?.records || []
    if (!rec.length) break
    records.push(...rec)
    page += 1
  }
  return records
}

async function fetchLedger() {
  const projects = await loadAllProjects()
  const extras = await Promise.allSettled([
    deliverableApi.page({ page: 1, size: 500 }),
    transformApi.page({ page: 1, size: 500 }),
    changeApi.page({ page: 1, size: 200 }),
    partnerApi.blacklist(),
    milestoneApi.board({ year: filter.year }),
  ])
  const deliverables = recordsOf(settledValue(extras[0], { code: 0, msg: '', data: { records: [] } }))
  const transforms = recordsOf(settledValue(extras[1], { code: 0, msg: '', data: { records: [] } }))
  const changes = recordsOf(settledValue(extras[2], { code: 0, msg: '', data: { records: [] } }))
  const blacklist = recordsOf(settledValue(extras[3], { code: 0, msg: '', data: [] }))
  const boardRes = settledValue(extras[4], { code: 0, msg: '', data: { boards: [] } })
  ledgerCtx.value = {
    projects,
    channels: dictStore.channels,
    deliverables,
    transforms,
    changes,
    blacklist,
    milestones: milestonesFromBoard(boardRes),
    plans: plansFromMilestoneBoard(boardRes),
  }
}

function applyBoard() {
  const ctx = ledgerCtx.value
  if (!ctx) {
    data.value = {}
    return
  }
  data.value = aggregateDashboard(queryPayload(), {
    ...ctx,
    channels: dictStore.channels,
  })
}

async function load(force = false) {
  if (!userStore.canViewBoard) {
    denied.value = true
    data.value = {}
    errorMsg.value = ''
    return
  }
  loading.value = true
  errorMsg.value = ''
  denied.value = false
  try {
    if (force || !ledgerCtx.value) await fetchLedger()
    applyBoard()
  } catch (e: any) {
    if (!isSilentAuthError(e)) {
      errorMsg.value = e?.message || '可视化看板加载失败'
      message.error(errorMsg.value)
    }
    data.value = {}
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filter.year = currentYear
  filter.level = undefined
  filter.sourceChannel = undefined
  filter.orgOffice = undefined
  filter.projectType = undefined
  filter.major1 = undefined
  filter.major2 = undefined
  filter.unit = undefined
}

function goLedger(extra: Record<string, any> = {}) {
  router.push({ path: '/overview/ledger', query: extra })
}

function onLevelClick(p: any) {
  const level = p?.data?.level || { 国家级: 'NATIONAL', 地方级: 'LOCAL', 公司级: 'COMPANY' }[p?.name]
  if (level === 'UNSPECIFIED') {
    message.info('这些项目未填写层级或层级值无法识别，请在项目台账中补充核对')
    return
  }
  if (level) goLedger({ levelCode: level })
}

function onChannelClick(p: any) {
  const name = p?.name
  if (name) goLedger({ sourceChannel: name })
}

function onUnitClick(p: any) {
  const idx = (data.value.unitLevelMatrix?.units || []).indexOf(p?.name)
  const orgId = data.value.unitLevelMatrix?.orgIds?.[idx]
  if (orgId) goLedger({ orgId: String(orgId) })
  else if (p?.name) goLedger({ unit: p.name })
}

function onMajorClick(p: any) {
  const major1 = p?.data?.major1
  if (major1) goLedger({ major1 })
}

function onRisk(item: any) {
  if (item?.projectId) router.push(`/overview/detail/${item.projectId}`)
}

watch(
  () => filter.level,
  () => {
    if (filter.sourceChannel && !channelOptions.value.some((o) => o.value === filter.sourceChannel)) {
      filter.sourceChannel = undefined
    }
    if (filter.orgOffice && !officeOptions.value.some((o) => o.value === filter.orgOffice)) {
      filter.orgOffice = undefined
    }
  },
)
watch(
  () => filter.sourceChannel,
  (name) => {
    if (name) {
      const ch = dictStore.channels.find((c) => c.channelName === name)
      if (ch?.levelCode) filter.level = ch.levelCode
    }
    if (filter.orgOffice && !officeOptions.value.some((o) => o.value === filter.orgOffice)) {
      filter.orgOffice = undefined
    }
  },
)
watch(
  () => filter.orgOffice,
  (office) => {
    if (!office) return
    const hits = dictStore.channels.filter((c) => c.channelOffice === office || c.innerOffice === office)
    if (hits.length === 1) {
      filter.sourceChannel = hits[0].channelName
      filter.level = hits[0].levelCode
    }
  },
)
watch(
  () => filter.major1,
  () => {
    if (filter.major2 && !major2Options.value.some((o) => o.value === filter.major2)) filter.major2 = undefined
  },
)
const ready = ref(false)
let loadTimer: ReturnType<typeof setTimeout> | undefined
function scheduleLoad() {
  clearTimeout(loadTimer)
  loadTimer = setTimeout(() => {
    load()
  }, 80)
}
watch(
  () => filter.year,
  () => {
    ledgerCtx.value = null
  },
)
watch(
  filter,
  () => {
    if (ready.value) scheduleLoad()
  },
  { deep: true },
)

useVisibleRefresh(()=>load(true),()=>!loading.value)

onMounted(async () => {
  await dictStore.loadChannels().catch(() => undefined)
  await dictStore.load('PROJECT_LEVEL').catch(() => undefined)
  if (route.query.year) filter.year = Number(route.query.year)
  ready.value = true
  await load()
})
</script>

<template>
  <div class="page-container board-page">
    <div class="board-head">
      <div>
        <h2 class="page-title">{{ isPreResearch ? '科研预研信息管理大屏' : '可视化看板' }}</h2>
        <div class="page-desc">
          聚合项目台账、经费、计划、里程碑、交付物、成果转化与风险预警；统计口径与当前筛选下的项目台账保持一致。
        </div>
      </div>
      <div class="board-meta">
        <span>业务日期 {{ data.today || '—' }}</span>
        <span>更新于 {{ data.updatedAt || '—' }}</span>
        <a-button size="small" @click="load(true)">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </div>

    <a-alert
      v-if="denied"
      type="warning"
      show-icon
      message="当前身份无权访问可视化看板"
      description="该功能仅对系统管理员和总部人员开放。"
      style="margin-bottom: 16px"
    />

    <template v-else>
      <div class="page-card filter-card">
        <a-space wrap :size="8">
          <a-select
            v-model:value="filter.year"
            allow-clear
            placeholder="全部年份"
            style="width: 110px"
            :options="withAllOption(yearOptions.map((y) => ({ value: y, label: `${y}年` })))"
          />
          <a-select
            v-model:value="filter.level"
            allow-clear
            placeholder="全部层级"
            style="width: 120px"
            :options="withAllOption(dictStore.options('PROJECT_LEVEL'))"
          />
          <a-select
            v-model:value="filter.sourceChannel"
            allow-clear
            show-search
            option-filter-prop="label"
            placeholder="全部来源/渠道"
            style="width: 200px"
            :options="withAllOption(channelOptions)"
          />
          <a-select
            v-model:value="filter.orgOffice"
            allow-clear
            placeholder="全部司局/处室"
            style="width: 150px"
            :options="withAllOption(officeOptions)"
          />
          <a-select
            v-model:value="filter.projectType"
            allow-clear
            placeholder="全部项目类型"
            style="width: 140px"
            :options="withAllOption(typeOptions)"
          />
          <a-select
            v-model:value="filter.major1"
            allow-clear
            show-search
            placeholder="全部一级专业"
            style="width: 160px"
            :options="withAllOption(majorConfig.major1.map((value) => ({ value, label: value })))"
          />
          <a-select
            v-model:value="filter.major2"
            allow-clear
            show-search
            placeholder="全部二级专业"
            style="width: 190px"
            :options="withAllOption(major2Options)"
          />
          <a-select
            v-model:value="filter.unit"
            allow-clear
            show-search
            option-filter-prop="label"
            placeholder="全部承担单位"
            style="width: 180px"
            :options="withAllOption(unitOptions)"
          />
          <a-button @click="resetFilters">重置</a-button>
        </a-space>
      </div>

      <a-alert v-if="errorMsg && !denied" type="error" show-icon :message="errorMsg" style="margin-bottom: 16px" />

      <a-spin :spinning="loading" tip="正在加载驾驶舱…">
        <div class="kpi-grid" style="margin-bottom: 16px">
          <div class="stat-card" :class="c.tone" v-for="c in cards" :key="c.key">
            <div class="label">{{ c.label }}</div>
            <div class="value">
              {{ c.value }}<span class="unit">{{ c.unit }}</span>
            </div>
            <div class="sub">{{ c.sub }}</div>
          </div>
        </div>

        <a-row :gutter="[16, 16]" style="margin-bottom: 16px">
          <a-col :xs="24" :lg="8">
            <div class="page-card">
              <div class="chart-title">项目层级分布</div>
              <EChart :option="levelPie" :height="288" @chart-click="onLevelClick" />
            </div>
          </a-col>
          <a-col :xs="24" :lg="8">
            <div class="page-card">
              <div class="chart-title">渠道结构</div>
              <EChart :option="channelRing" :height="288" @chart-click="onChannelClick" />
            </div>
          </a-col>
          <a-col :xs="24" :lg="8">
            <div class="page-card">
              <div class="chart-title">层级关联下的渠道项目分布 TOP8</div>
              <EChart :option="channelTop" :height="288" @chart-click="onChannelClick" />
            </div>
          </a-col>
        </a-row>

        <a-row :gutter="[16, 16]" style="margin-bottom: 16px">
          <a-col :xs="24" :lg="14">
            <div class="page-card">
              <div class="chart-title">承担单位 × 项目层级矩阵</div>
              <EChart :option="unitMatrix" :height="440" @chart-click="onUnitClick" />
            </div>
          </a-col>
          <a-col :xs="24" :lg="10">
            <div class="page-card">
              <div class="chart-title">年度经费预算与执行（近 5 年）</div>
              <EChart :option="fundTrend" :height="218" />
              <div class="chart-title" style="margin-top: 12px">经费构成</div>
              <div class="fund-note">在研项目经费 {{ fmtAmount(data.fundStructure?.running) }} 万元 · 商飞内部经费为国拨/自筹中的内部单位部分，不重复计入总经费</div>
              <EChart :option="fundPie" :height="218" />
            </div>
          </a-col>
        </a-row>

        <a-row :gutter="[16, 16]" style="margin-bottom: 16px">
          <a-col :xs="24" :lg="8">
            <div class="page-card">
              <div class="chart-title">专业经费分布</div>
              <EChart :option="majorPie" :height="288" @chart-click="onMajorClick" />
            </div>
          </a-col>
          <a-col :xs="24" :lg="8">
            <div class="page-card">
              <div class="chart-title">计划管理状态</div>
              <div class="plan-grid">
                <button class="plan-block red" type="button" @click="router.push('/implement/plan')">
                  <div class="n">{{ planColors.red }}</div>
                  <div>红色 · 逾期</div>
                </button>
                <button class="plan-block yellow" type="button" @click="router.push('/implement/plan')">
                  <div class="n">{{ planColors.yellow }}</div>
                  <div>黄色 · 临期</div>
                </button>
                <button class="plan-block blue" type="button" @click="router.push('/implement/plan')">
                  <div class="n">{{ planColors.blue }}</div>
                  <div>蓝色 · 正常推进</div>
                </button>
                <button class="plan-block green" type="button" @click="router.push('/implement/plan')">
                  <div class="n">{{ planColors.green }}</div>
                  <div>绿色 · 已完成</div>
                </button>
              </div>
              <div class="cmos">{{ data.planStats?.cmosText || 'CMOS接口待联调' }}</div>
            </div>
          </a-col>
          <a-col :xs="24" :lg="8">
            <div class="page-card risk-card">
              <div class="chart-title">风险预警榜</div>
              <div v-if="!(data.risks || []).length" class="empty">当前无风险项目</div>
              <div v-else class="risk-list">
                <button
                  v-for="r in data.risks"
                  :key="r.id"
                  class="risk-item"
                  :class="{ pulse: r.color === 'RED' }"
                  type="button"
                  @click="onRisk(r)"
                >
                  <a-tag :color="r.color === 'RED' ? 'red' : 'orange'">{{ r.type }}</a-tag>
                  <div class="risk-body">
                    <div class="risk-name">{{ r.projectName }}</div>
                    <div class="risk-sub">{{ r.title }} · {{ r.dueDate || '—' }} · {{ r.remain < 0 ? `超期 ${Math.abs(r.remain)} 天` : `剩余 ${r.remain} 天` }}</div>
                  </div>
                </button>
              </div>
            </div>
          </a-col>
        </a-row>

        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :lg="8">
            <div class="page-card">
              <div class="chart-title">交付物产出结构</div>
              <EChart :option="delivBar" :height="288" />
            </div>
          </a-col>
          <a-col :xs="24" :lg="8">
            <div class="page-card">
              <div class="chart-title">成果转化推进</div>
              <div class="tf-summary">
                总数 {{ data.transformSummary?.total || 0 }} · 已完成 {{ data.transformSummary?.done || 0 }} · 推进中
                {{ data.transformSummary?.progressing || 0 }} · 未启动 {{ data.transformSummary?.notStarted || 0 }} · 逾期
                {{ data.transformSummary?.overdue || 0 }}
              </div>
              <EChart :option="transformFunnel" :height="248" />
            </div>
          </a-col>
          <a-col :xs="24" :lg="8">
            <div class="page-card">
              <div class="chart-title">型号转化分布</div>
              <EChart :option="modelBar" :height="288" @chart-click="router.push('/transform')" />
            </div>
          </a-col>
        </a-row>
      </a-spin>
    </template>
  </div>
</template>

<style scoped>
.board-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 8px;
}
.board-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #8c8c8c;
  font-size: 12px;
  white-space: nowrap;
}
.filter-card {
  margin-bottom: 16px;
  padding-bottom: 12px;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 16px;
}
.kpi-grid .value {
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}
@media (max-width: 1400px) {
  .kpi-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 900px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.stat-card .unit {
  font-size: 13px;
  color: #8c8c8c;
  margin-left: 4px;
  font-weight: 400;
}
.stat-card .sub {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 4px;
}
.stat-card.warn-soft {
  border-left-color: #faad14;
}
.chart-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #262626;
}
.fund-note,
.cmos,
.tf-summary {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 8px;
  line-height: 1.5;
}
.plan-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.plan-block {
  border: 1px solid #e8e8e8;
  background: #fff;
  border-radius: 4px;
  padding: 16px 12px;
  text-align: left;
  cursor: pointer;
}
.plan-block .n {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 4px;
}
.plan-block.red { border-left: 4px solid #f5222d; }
.plan-block.yellow { border-left: 4px solid #faad14; }
.plan-block.blue { border-left: 4px solid #0064ef; }
.plan-block.green { border-left: 4px solid #00c91a; }
.empty {
  color: #8c8c8c;
  padding: 32px 0;
  text-align: center;
}
.risk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 248px;
  overflow: auto;
}
.risk-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  border: 1px solid #e8e8e8;
  background: #fff;
  border-radius: 4px;
  padding: 8px;
  text-align: left;
  cursor: pointer;
}
.risk-item.pulse {
  border-color: #ffa39e;
  animation: riskPulse 1.6s ease-in-out infinite;
}
.risk-name {
  font-weight: 600;
  color: #262626;
}
.risk-sub {
  font-size: 12px;
  color: #8c8c8c;
}
@keyframes riskPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 34, 45, 0.18); }
  50% { box-shadow: 0 0 0 4px rgba(245, 34, 45, 0.12); }
}
</style>
