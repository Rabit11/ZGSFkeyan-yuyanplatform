<script setup lang="ts">
import { computed } from 'vue'
import { fmtAmount } from '@/utils/format'
import { calcColor } from '@/utils/color'
import dayjs from 'dayjs'

const props = defineProps<{ data: any; loading?: boolean; error?: string }>()

const p = computed(() => props.data?.project || {})
const channel = computed(() => props.data?.channel || {})

function dash(v?: string | number | null, empty = '—') {
  const s = String(v ?? '').trim()
  return s || empty
}

const currentYear = new Date().getFullYear()

const milestones = computed(() => {
  const all = [...(props.data?.milestones || [])]
  const yearRows = all.filter((m: any) => Number(m.year) === currentYear || String(m.planDate || '').startsWith(String(currentYear)))
  const rows = (yearRows.length ? yearRows : all).sort((a: any, b: any) =>
    String(a.planDate || '9999').localeCompare(String(b.planDate || '9999')),
  )
  return rows.slice(0, 3)
})

function msView(m: any) {
  const done = m.status === 'DONE' || !!m.actualDate
  const color = m.colorStatus || calcColor(m.planDate, done)
  if (done) return { label: '已完成', percent: 100, color: '#00C91A', extra: '100%' }
  if (color === 'RED') return { label: '逾期', percent: 70, color: '#f5222d', extra: m.planDate || '逾期' }
  if (color === 'YELLOW') return { label: '临期', percent: 55, color: '#FF9900', extra: m.planDate || '临期' }
  return { label: m.planDate ? `截止日期 ${m.planDate}` : '待开展', percent: 18, color: '#0064EF', extra: m.planDate || '待开展' }
}

const emptyMs = computed(() => {
  const rows = milestones.value
  if (rows.length) return rows
  return [{ id: 'e1' }, { id: 'e2' }, { id: 'e3' }]
})

const DV_MAP: [string, string[]][] = [
  ['专利', ['PATENT']],
  ['论文', ['PAPER']],
  ['样机', ['PROTOTYPE']],
  ['标准', ['STANDARD']],
  ['设备', ['EQUIPMENT']],
  ['其他', ['SOFTWARE', 'TECH_PACKAGE', 'OTHER']],
]

const dvOverview = computed(() => {
  const list = props.data?.deliverables || []
  return DV_MAP.map(([name, codes]) => {
    const n = list.filter((d: any) => codes.includes(d.deliverType)).length
    return { name, value: n ? String(n) : '暂无' }
  })
})

const transforms = computed(() => props.data?.transforms || [])
const toModel = computed(() => transforms.value.some((t: any) => t.transformWay === 'MODEL'))
const toMarket = computed(() => transforms.value.some((t: any) => t.transformWay === 'MARKET'))

const fundRow = computed(() => {
  const budgets = props.data?.budgets || []
  const yearBudgets = budgets.filter((b: any) => Number(b.year) === currentYear)
  const use = yearBudgets.length ? yearBudgets : budgets
  const budget = use.length
    ? use.reduce((s: number, b: any) => s + Number(b.amount || 0), 0)
    : Number(p.value.yearBudget || 0)
  const payments = props.data?.payments || []
  const yearPays = payments.filter((x: any) => String(x.occurDate || '').startsWith(String(currentYear)))
  const expense = yearPays.length
    ? yearPays.reduce((s: number, x: any) => s + Number(x.amount || 0), 0)
    : Number(p.value.yearExpense || 0)
  const rate = budget ? Math.round((expense / budget) * 1000) / 10 : 0
  return { budget, expense, rate, bar: Math.min(100, rate) }
})

const techName = computed(() => {
  const m = (p.value.teamMembers || []).find((x: any) => x.roleCode === 'TECH_LEADER' || x.roleName === '技术负责人')
  return m?.userName || '待指定'
})

const partners = computed(() => {
  const parts = p.value.participants || []
  if (!parts.length) return '—'
  return parts.map((x: any) => x.orgName).join('、')
})

const annualGoal = computed(() => {
  const plans = p.value.annualPlans || []
  if (!plans.length) return '—'
  return plans.find((a: any) => a.year === currentYear)?.annualGoal || plans[0].annualGoal || '—'
})

const period = computed(() => {
  const a = p.value.startDate ? dayjs(p.value.startDate).format('YYYY.MM.DD') : '—'
  const b = p.value.endDate ? dayjs(p.value.endDate).format('YYYY.MM.DD') : '—'
  return `${a} — ${b}`
})
</script>

<template>
  <div class="viz-panel">
    <a-spin :spinning="loading" tip="正在加载项目可视化…">
      <a-alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 16px" />
      <a-row :gutter="[16, 16]">
        <a-col :span="24">
          <div class="block">
            <div class="block-title">项目基本信息</div>
            <a-descriptions :column="2" size="small" :label-style="{ width: '110px', color: '#8c8c8c' }">
              <a-descriptions-item label="项目名称" :span="2">{{ dash(p.name) }}</a-descriptions-item>
              <a-descriptions-item label="项目来源">{{ dash(channel.channelDept || p.filingDept) }}</a-descriptions-item>
              <a-descriptions-item label="项目类型">{{ dash(p.projectType) }}</a-descriptions-item>
              <a-descriptions-item label="渠道">{{ dash(p.channelName || channel.channelName) }}</a-descriptions-item>
              <a-descriptions-item label="司局/处室">{{ dash(p.bureauOffice || channel.channelOffice) }}</a-descriptions-item>
              <a-descriptions-item label="起止时间">{{ period }}</a-descriptions-item>
              <a-descriptions-item label="WBS编号">{{ dash(p.projectNo) }}</a-descriptions-item>
              <a-descriptions-item label="参研单位" :span="2">{{ partners }}</a-descriptions-item>
              <a-descriptions-item label="牵头单位">{{ dash(p.leadOrgName || p.orgName) }}</a-descriptions-item>
              <a-descriptions-item label="项目负责人">{{ dash(p.ownerName, '待指定') }}</a-descriptions-item>
              <a-descriptions-item label="技术负责人">{{ techName }}</a-descriptions-item>
              <a-descriptions-item label="项目目标" :span="2">{{ dash(p.goal) }}</a-descriptions-item>
              <a-descriptions-item label="年度目标" :span="2">{{ annualGoal }}</a-descriptions-item>
            </a-descriptions>
          </div>
        </a-col>
        <a-col :xs="24" :lg="10">
          <div class="block">
            <div class="block-title">年度里程碑</div>
            <div class="ms-hint">未完成进度条为展示性比例，不代表实际完成率</div>
            <div v-for="(m, i) in emptyMs" :key="m.id || i" class="ms-item">
              <template v-if="m.name">
                <div class="ms-row">
                  <span>{{ m.name }}</span>
                  <span :style="{ color: msView(m).color }">{{ msView(m).label }}</span>
                </div>
                <a-progress :percent="msView(m).percent" :stroke-color="msView(m).color" :show-info="false" size="small" />
                <div class="ms-extra">{{ msView(m).extra }}</div>
              </template>
              <div v-else class="ms-empty">暂无里程碑</div>
            </div>
          </div>
        </a-col>
        <a-col :xs="24" :lg="7">
          <div class="block">
            <div class="block-title">交付物概览</div>
            <div class="dv-grid">
              <div v-for="d in dvOverview" :key="d.name" class="dv-item">
                <div class="dv-name">{{ d.name }}</div>
                <div class="dv-value">{{ d.value }}</div>
              </div>
            </div>
            <div class="block-title" style="margin-top: 16px">成果转化方向</div>
            <a-checkbox :checked="toModel" disabled>对内转化</a-checkbox>
            <a-checkbox :checked="toMarket" disabled>对外转化</a-checkbox>
          </div>
        </a-col>
        <a-col :xs="24" :lg="7">
          <div class="block">
            <div class="block-title">年度经费计划</div>
            <div class="fund-num">预算 {{ fmtAmount(fundRow.budget) }} 万元</div>
            <div class="fund-num">已执行 {{ fmtAmount(fundRow.expense) }} 万元</div>
            <a-progress :percent="fundRow.bar" :stroke-color="'#0064EF'" />
            <div class="ms-extra">执行率 {{ fundRow.rate }}%</div>
          </div>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<style scoped>
.block {
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 16px;
  min-height: 220px;
  background: #fff;
}
.block-title {
  font-weight: 600;
  margin-bottom: 12px;
}
.ms-hint,
.ms-extra {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 8px;
}
.ms-item {
  margin-bottom: 12px;
}
.ms-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.ms-empty {
  color: #8c8c8c;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f0f0;
}
.dv-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.dv-item {
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  padding: 8px;
}
.dv-name {
  font-size: 12px;
  color: #8c8c8c;
}
.dv-value {
  font-weight: 600;
  font-size: 16px;
}
.fund-num {
  margin-bottom: 8px;
}
</style>
