<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { projectApi } from '@/api/modules'
import http from '@/api/request'
import { supplementApi } from '@/api/supplement'
import { detailStages, materialRows, sourceLabel, sectionTab, attachNativeMaterials, sectionStage } from '@/utils/projectDetailPresentation'
import ProjectStageMaterials from '@/components/ProjectStageMaterials.vue'
import ReadonlyMaterialFile from '@/components/ReadonlyMaterialFile.vue'
import { fmtAmount, fmtDate } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import { LEVEL_TEXT, PROJECT_STATUS_TEXT } from '@/api/types'
import {
  channelPathLabel,
  stageBadge,
} from '@/utils/lifecycle'
import { personLabelOf } from '@/utils/flowLive'
import { useDictStore } from '@/stores/dict'
import ProjectVizPanel from '@/components/ProjectVizPanel.vue'
import SupplementSummary from '@/components/SupplementSummary.vue'

const dictStore = useDictStore()
const route = useRoute()
const router = useRouter()
let id = Number(route.params.id)
const data = ref<any>({})
const active = ref('overview')
const loading = ref(false)
const p = computed(() => data.value.project || {})
const channel = computed(() => data.value.channel || {})
const isMaintenanceProject = computed(() => p.value.dataSource === 'FORM_MAINT')
const selectedStage = ref<number | null>(null)
const supplementSections = ref<any[]>([])
const approvedSections = ref<any[]>([])
const materialError = ref('')
const supplementError = ref('')
const nativeFiles = computed(() => {
  const files = [...(data.value.projectMaterials || []), ...(data.value.acceptanceMaterials || []).map((f:any) => ({...f,id:`acceptance-${f.id}`,sectionKey:'acceptance'}))]
  for (const transform of data.value.transforms || []) {
    try {
      const evidence = JSON.parse(transform.evidenceJson || '[]')
      if (Array.isArray(evidence)) files.push(...evidence.map((f:any,i:number) => ({...f,id:`transform-${transform.id}-${i}`,sectionKey:'transform',fieldName:'成果转化佐证',fileName:f.fileName || f.name || '成果转化附件'})))
    } catch { /* Legacy invalid payloads remain available in the source module. */ }
  }
  return files
})
const sections = computed(() => {
  const base = data.value.materialSections || []
  const records = supplementSections.value.length ? supplementSections.value : approvedSections.value
  const combined = base.map((s:any) => records.find((r:any) => r.key === s.key) || s)
    .concat(records.filter((r:any) => !base.some((s:any) => s.key === r.key)))
  return attachNativeMaterials(combined, nativeFiles.value)
})
const stageRows = computed(() => materialRows(sections.value, p.value.status))
const lifecycleCycles = computed(() => detailStages(p.value.status,
  (data.value.transforms || []).length > 0 && data.value.transforms.every((t:any) => t.status === 'DONE')))
function materialSummary(stageId:number) {
  if (!data.value.materialChannelResolved || !sections.value.length) return '材料要求待确认'
  const rows = stageRows.value.filter(r => r.stage === stageId)
  if (rows.some(r => r.state === 'unknown') || sections.value.some((s:any) => sectionStage(s) === stageId && s.configurationPending)) return '部分材料要求待确认'
  const due = rows.filter(r => r.required && !['future','inapplicable'].includes(r.state))
  const missing = due.filter(r => ['missing','returned'].includes(r.state)).length
  const future = rows.filter(r => r.state === 'future').length
  if (!due.length) return future ? `后续材料 ${future} 项` : rows.some(r=>r.state === 'condition') ? '适用条件待确认' : '暂无已触发的必交材料'
  return `材料齐备 ${due.filter(r=>r.state === 'complete').length}/${due.length} · 待补 ${missing} 项`
}
const approvedForTab = computed(() => [...approvedSections.value, ...supplementSections.value.filter(s => s.status !== 'APPROVED')].filter(s => sectionTab(s.key) === active.value))
const tabFiles = computed(() => nativeFiles.value.filter(f => sectionTab(f.sectionKey) === active.value))

async function load() {
  loading.value = true
  try {
    const res = await projectApi.overview(id)
    data.value = res.data || {}
    materialError.value = data.value.materialSections ? '' : '当前服务未返回渠道材料清单，请更新配套后端后重试。'
    approvedSections.value = []; supplementSections.value = []; supplementError.value = ''
    if (isMaintenanceProject.value) {
      try { approvedSections.value = (await http.get<any[]>(`/api/supplement/${id}/approved`)).data || [] }
      catch { supplementError.value = '已审核补录信息读取失败或无查看权限，请重试或联系管理员。' }
      try { supplementSections.value = (await supplementApi.detail(String(id))).data.sections || [] }
      catch { materialError.value = '当前补录材料读取失败或无查看权限；下方仅展示可访问的已审核版本，不能据此判定当前缺项。' }
    }
  } finally {
    loading.value = false
  }
}
watch(() => route.params.id, value => {
  id = Number(value)
  active.value = 'overview'
  selectedStage.value = null
  data.value = {}
  void load()
})
onMounted(async () => {
  await dictStore.loadChannels().catch(() => undefined)
  await load()
})
const channelPath = computed(
  () => data.value.channelPath || channelPathLabel({ ...channel.value, channelName: p.value.channelName }),
)
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
              <a-tag color="blue">{{ sourceLabel(p.dataSource) }}</a-tag>
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

      <!-- 三阶段仅展示状态与材料，不承载审批流转 -->
      <div class="lifecycle-wrap">
        <div class="lifecycle-head"><div class="lifecycle-title">项目生命周期</div><span class="lifecycle-hint">材料只读查看 · 办理请从左侧功能栏进入</span></div>
        <div class="lifecycle-track">
          <section v-for="cycle in lifecycleCycles" :key="cycle.id" class="life-cycle" :class="[cycle.status, {selected:selectedStage===cycle.id}]">
            <div class="cycle-head"><h2><span>{{String(cycle.id+1).padStart(2,'0')}}</span>{{cycle.name}}</h2><a-tag :color="cycle.status==='current'?'processing':cycle.status==='done'?'success':'default'">{{cycle.label}}</a-tag></div>
            <p class="material-count">{{ materialError ? '材料状态待核对' : materialSummary(cycle.id) }}</p>
            <p class="stage-owner">项目负责人：{{p.ownerName || '待指定'}}</p>
            <a-button type="link" size="small" :aria-expanded="selectedStage===cycle.id" @click="selectedStage=selectedStage===cycle.id?null:cycle.id">{{selectedStage===cycle.id?'收起材料':cycle.status==='future'?'查看要求':'查看材料'}}</a-button>
          </section>
        </div>
        <a-alert v-if="materialError" type="warning" :message="materialError" style="margin-top:12px" show-icon><template #action><a-button size="small" @click="load">重试</a-button></template></a-alert>
        <ProjectStageMaterials v-if="selectedStage!==null" :sections="sections" :status="p.status" :stage="selectedStage" :resolved="!!data.materialChannelResolved" :imported="isMaintenanceProject" :project-id="id" />
      </div>

      <!-- C. Tab 内容 -->
      <a-card :body-style="{ padding: '12px 20px 20px' }" class="detail-card">
        <a-tabs v-model:activeKey="active">
          <a-tab-pane key="overview" tab="概览">
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
                    <a-descriptions-item label="成果转化">{{ transformStatusText }}</a-descriptions-item>
                    <a-descriptions-item label="协作单位">{{ partnerText }}</a-descriptions-item>
                  </a-descriptions>
                </div>
              </a-col>
              <a-col :span="10">
                <div class="panel">
                  <div class="panel-title-row">
                    <div class="panel-title">项目团队</div>
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
                <div style="font-weight: 600; margin-bottom: 8px">项目预算</div>
                <a-table
                  size="small"
                  row-key="id"
                  :pagination="false"
                  :data-source="data.budgets || []"
                  :columns="[
                    { title: '年度', dataIndex: 'year' },
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
        <section v-if="tabFiles.length" class="business-files" aria-label="业务附件">
          <h3>已有材料</h3>
          <div v-for="file in tabFiles" :key="file.id" class="business-file"><ReadonlyMaterialFile :file="file" /><small>{{file.fieldName}} · {{file.uploadedBy || '上传人未记录'}} · {{file.uploadedAt || '时间未记录'}}</small></div>
        </section>
        <a-alert v-if="supplementError" type="warning" :message="supplementError" show-icon />
        <SupplementSummary v-if="approvedForTab.length" :project-id="id" :provided-sections="approvedForTab" />
      </a-card>
    </a-spin>

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
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  border: 1px solid #e6f0ff;
  border-radius: 10px;
  padding: 14px 18px 16px;
  margin-bottom: 16px;
  box-shadow: 0 6px 18px rgba(0, 39, 102, 0.04);
}
.lifecycle-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}
.lifecycle-title {
  font-weight: 600;
  font-size: 16px;
  color: #262626;
}
.lifecycle-hint {
  font-size: 12px;
  color: #8c8c8c;
}
.lifecycle-track { display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px; }
.life-cycle { position:relative;min-width:0;border:1px solid #e2e8f0;border-radius:8px;padding:14px 16px;background:#fff; }
.life-cycle:not(:last-child)::after {content:'→';position:absolute;right:-17px;top:45%;color:#98a9bd;}
.life-cycle.current {border-color:#91caff;background:#f5faff;}
.life-cycle.done {border-color:#c9e6b9;}
.life-cycle.selected {box-shadow:inset 0 -3px #1677ff;}
.cycle-head {display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;}
.cycle-head h2 {font-size:16px;font-weight:600;margin:0;}
.cycle-head h2 span {font-size:12px;color:#78889a;margin-right:8px;}
.cycle-head .ant-tag {margin:0;white-space:normal;}
.material-count {font-size:13px;margin:12px 0 6px;color:#46566c;}
.stage-owner {font-size:12px;color:#697586;margin:0 0 8px;}
@media(max-width:760px){.lifecycle-track{grid-template-columns:1fr}.life-cycle:not(:last-child)::after{display:none}.lifecycle-head{gap:8px;flex-wrap:wrap}}

.detail-card {
  border-radius: 4px;
}
.business-files {margin-top:16px;border-top:1px solid #e5eaf1;padding-top:12px;}
.business-files h3 {font-size:14px;font-weight:600;}
.business-file {padding:8px 0;border-bottom:1px solid #f0f0f0;}
.business-file small {display:block;color:#78889a;font-size:12px;}
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

