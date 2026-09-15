<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { dashboardApi, warningApi } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import { fmtAmount } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import { ROLE_TEXT } from '@/api/types'
import { WORK_DUTY_DEFS, WORK_ACTION_LABEL, type WorkAction, type WorkDutyCode } from '@/constants/workDuty'
import { resolveWorkDuty } from '@/utils/workDuty'

const router = useRouter()
const userStore = useUserStore()
const ov = ref<any>({})
const warns = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    // 首页的两个数据源彼此独立；单个接口权限不足时仍保留可用内容，不能把整页判成登录失效。
    const [overviewResult, warningResult] = await Promise.allSettled([
      dashboardApi.overview(),
      dashboardApi.warnings(),
    ])
    ov.value = overviewResult.status === 'fulfilled' ? overviewResult.value.data || {} : {}
    warns.value = warningResult.status === 'fulfilled' ? (warningResult.value.data as any[]) || [] : []
  } finally {
    loading.value = false
  }
}
onMounted(load)

const statCards = computed(() => [
  { label: '科研项目总数（项）', value: ov.value.projectCount ?? 0, cls: '' },
  { label: '在研/申报中（项）', value: ov.value.runningCount ?? 0, cls: '' },
  { label: '逾期告警（条）', value: ov.value.overdueCount ?? 0, cls: 'warn' },
  { label: '经费总额（万元）', value: fmtAmount(ov.value.totalFund), cls: '' },
  { label: '年度预算（万元）', value: fmtAmount(ov.value.yearBudget), cls: '' },
  { label: '成果转化（项）', value: ov.value.transformCount ?? 0, cls: 'ok' },
])

const quickLinks = [
  { title: '项目台账', desc: '全集团项目一本账查询与导出', path: '/overview/ledger', hqOnly: false },
  { title: '可视化看板', desc: '多维度统计分析，总部专属', path: '/overview/board', hqOnly: true },
  { title: '预研管理大屏', desc: '领导/总部只读态势大屏', path: '/overview/pre-research', hqOnly: true },
  { title: '项目申报', desc: '按渠道自适应填报栏位', path: '/initiation/declaration', hqOnly: false },
  { title: '里程碑', desc: '年度节点四色预警闭环', path: '/implement/milestone', hqOnly: false },
  { title: '项目经费', desc: '执行账与总部预算账双体系', path: '/implement/fund', hqOnly: false },
  { title: '项目验收', desc: '前置校验 + 分级材料归档', path: '/acceptance/accept', hqOnly: false },
]

const visibleQuickLinks = computed(() =>
  quickLinks.filter((q) => !q.hqOnly || userStore.canViewBoard),
)

const myDuties = computed(() => {
  const actor = {
    employeeNo: userStore.employeeNo,
    realName: userStore.realName,
    identityCode: userStore.identityCode,
    roles: userStore.roles,
  }
  return WORK_DUTY_DEFS.map((d) => {
    const r = resolveWorkDuty(d.code as WorkDutyCode, null, actor)
    const acts = (['fill', 'submit', 'audit', 'edit'] as WorkAction[]).filter((a) => r[a].can)
    return {
      key: d.code,
      stage: d.stage,
      title: d.title,
      path: d.path.split('?')[0],
      acts: acts.map((a) => WORK_ACTION_LABEL[a]).join('、') || '查看',
      writable: acts.length > 0,
    }
  }).filter((x) => x.writable)
})

async function markRead(id: number) {
  await warningApi.read(id)
  message.success('已标记已读')
  load()
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">首页</h2>
    <div class="page-desc">
      您好，{{ userStore.realName }}（{{ userStore.roles.map((r) => ROLE_TEXT[r]).join('、') || '—' }}），
      这里是平台运行总览，展示项目、经费、风险与成果的整体态势。
    </div>

    <a-spin :spinning="loading">
      <a-row :gutter="[16, 16]" class="dashboard-stat-row">
        <a-col v-for="c in statCards" :key="c.label" :xs="24" :sm="12" :lg="8" :xl="4">
          <div class="stat-card" :class="c.cls">
            <div class="label">{{ c.label }}</div>
            <div class="value">{{ c.value }}</div>
          </div>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :span="14">
          <div class="page-card" style="margin-bottom: 16px">
            <div style="font-weight: 600; margin-bottom: 12px">常用功能</div>
            <a-row :gutter="12">
              <a-col :span="8" v-for="q in visibleQuickLinks" :key="q.path" style="margin-bottom: 12px">
                <a-card hoverable size="small" @click="router.push(q.path)" style="cursor: pointer">
                  <div style="font-weight: 600; margin-bottom: 4px">{{ q.title }}</div>
                  <div style="font-size: 12px; color: #8a919f">{{ q.desc }}</div>
                </a-card>
              </a-col>
            </a-row>
          </div>

          <div class="page-card" style="margin-bottom: 16px">
            <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
              <span style="font-weight: 600">我的办理职责</span>
              <a-button type="link" size="small" @click="router.push('/system/work-duty')">工作定责一览</a-button>
            </div>
            <a-empty v-if="!myDuties.length" description="当前身份仅可查看，无填写/提交/审核职责" />
            <a-list v-else :data-source="myDuties" size="small">
              <template #renderItem="{ item }">
                <a-list-item style="cursor: pointer" @click="router.push(item.path)">
                  <a-list-item-meta>
                    <template #title>{{ item.stage }} · {{ item.title }}</template>
                    <template #description>您可：{{ item.acts }}</template>
                  </a-list-item-meta>
                </a-list-item>
              </template>
            </a-list>
          </div>

          <div class="page-card">
            <div style="font-weight: 600; margin-bottom: 12px">
              项目层级分布
              <a-tag v-for="l in ov.levelDist || []" :key="l.name" color="blue" style="margin-left: 8px">
                {{ l.name }} {{ l.value }}
              </a-tag>
            </div>
            <a-progress
              v-for="l in ov.levelDist || []"
              :key="l.name"
              :percent="Math.round((l.value / (ov.projectCount || 1)) * 100)"
              :stroke-color="['#0A5CAD', '#1374C8', '#52C41A'][(ov.levelDist || []).indexOf(l)]"
            >
              <template #format>
                <span>{{ l.name }}：{{ l.value }} 项</span>
              </template>
            </a-progress>
          </div>
        </a-col>

        <a-col :span="10">
          <div class="page-card">
            <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
              <span style="font-weight: 600">风险预警</span>
              <a-button type="link" size="small" @click="router.push('/system/warning')">
                查看全部
              </a-button>
            </div>
            <a-empty v-if="!warns.length" description="暂无预警" />
            <a-list :data-source="warns.slice(0, 8)" size="small">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <span :style="{ color: item.warnLevel === 'RED' ? '#f5222d' : '#faad14' }">
                        ●
                      </span>
                      {{ item.title }}
                    </template>
                    <template #description>
                      <div style="font-size: 12px; color: #8a919f">{{ item.projectName }}</div>
                      <div style="font-size: 12px; color: #8a919f">{{ item.content }}</div>
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a-button type="link" size="small" @click="markRead(item.id)">已读</a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </div>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<style scoped>
.dashboard-stat-row {
  margin-bottom: 16px;
}
.dashboard-stat-row :deep(.ant-col) {
  min-width: 0;
}
.dashboard-stat-row .stat-card {
  height: 100%;
  min-width: 0;
}
.dashboard-stat-row .value {
  font-size: 22px;
  line-height: 30px;
  overflow-wrap: anywhere;
}
</style>
