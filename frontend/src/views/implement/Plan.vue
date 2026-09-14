<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { SyncOutlined } from '@ant-design/icons-vue'
import { planApi, projectApi } from '@/api/modules'
import ProjectSelect from '@/components/ProjectSelect.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { dueText, fmtDate } from '@/utils/format'
import { useWorkDuty } from '@/composables/useWorkDuty'

const projectId = ref<number>()
const project = ref<any>(null)
const rows = ref<any[]>([])
const loading = ref(false)
const tab = ref<'TODO' | 'DONE'>('TODO')
const { can, guard } = useWorkDuty('plan', project)

const columns = [
  { title: '计划标题', dataIndex: 'title' },
  { title: '来源', dataIndex: 'source', width: 90 },
  { title: '到期日', dataIndex: 'dueDate', width: 120 },
  { title: '完成日', dataIndex: 'finishDate', width: 120 },
  { title: '责任人', dataIndex: 'owner', width: 100 },
  { title: '剩余/超期', key: 'remain', width: 120 },
  { title: '办结审批', dataIndex: 'applyStatus', width: 110 },
  { title: '状态', dataIndex: 'colorStatus', width: 110 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const },
]

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const [res, p] = await Promise.all([
      planApi.list(projectId.value, { planType: tab.value }),
      projectApi.detail(projectId.value),
    ])
    rows.value = (res.data as any[]) || []
    project.value = p.data
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  const res = await projectApi.page({ page: 1, size: 1 })
  projectId.value = (res.data as any)?.records?.[0]?.id
  load()
})

const APPLY_TEXT: Record<string, string> = { NONE: '未申请', PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回' }

const stats = computed(() => {
  const all = rows.value
  return {
    todo: all.filter((x) => x.planType === 'TODO').length,
    overdue: all.filter((x) => x.colorStatus === 'RED').length,
  }
})

async function apply(row: any) {
  if (!guard('submit')) return
  await planApi.finishApply(row.id)
  message.success('办结申请已提交，等待二级单位管理团队终审')
  load()
}
async function audit(row: any) {
  if (!guard('audit')) return
  await planApi.finishAudit(row.id, { pass: true, opinion: '计划已办结' })
  message.success('审批通过，已自动转为「已完成计划」')
  load()
}
async function sync() {
  await planApi.sync()
  message.success('已从 CMOS 系统同步最新计划数据')
  load()
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">计划管理</h2>
    <div class="page-desc">
      自动导入实时同步 CMOS 中已发布的预研项目计划、专项计划及计划完成情况，区分待办/已完成计划并按规则展示四色状态。
      待办计划完成后经管理团队审批自动进入已完成计划，<b>全程无总部审批</b>。
    </div>
    <WorkDutyBar code="plan" :project="project" />

    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6"><div class="stat-card"><div class="label">待办计划</div><div class="value">{{ stats.todo }}</div></div></a-col>
      <a-col :span="6"><div class="stat-card warn"><div class="label">超期待办</div><div class="value">{{ stats.overdue }}</div></div></a-col>
    </a-row>

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <a-space>
          <span>选择项目：</span>
          <ProjectSelect v-model="projectId" @change="load" />
        </a-space>
        <a-space>
          <a-button @click="sync"><SyncOutlined />同步 CMOS</a-button>
        </a-space>
      </div>

      <a-tabs v-model:activeKey="tab" @change="load">
        <a-tab-pane key="TODO" tab="待办计划" />
        <a-tab-pane key="DONE" tab="已完成计划" />
      </a-tabs>

      <a-table :columns="columns" :data-source="rows" :loading="loading" row-key="id" :scroll="{ x: 1300 }" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="['dueDate', 'finishDate'].includes(column.dataIndex)">{{ fmtDate(record[column.dataIndex]) }}</template>
          <template v-else-if="column.key === 'remain'">
            <span :style="{ color: record.colorStatus === 'RED' ? '#f5222d' : record.colorStatus === 'YELLOW' ? '#faad14' : '#8a919f' }">
              {{ record.planType === 'DONE' ? '已完成' : dueText(record.dueDate) }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'applyStatus'">
            <a-tag :color="record.applyStatus === 'APPROVED' ? 'green' : record.applyStatus === 'PENDING' ? 'processing' : record.applyStatus === 'REJECTED' ? 'red' : 'default'">
              {{ APPLY_TEXT[record.applyStatus] || '-' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'colorStatus'"><StatusTag :color="record.colorStatus" /></template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="2">
              <a-button type="link" size="small" :disabled="record.planType === 'DONE' || record.applyStatus === 'PENDING'" @click="apply(record)">
                办结申请
              </a-button>
              <a-divider type="vertical" />
              <a-button type="link" size="small" :disabled="record.applyStatus !== 'PENDING' || !can.audit" @click="audit(record)">终审</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>
