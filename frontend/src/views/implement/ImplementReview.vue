<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { milestoneApi, projectApi } from '@/api/modules'
import type { MilestoneTodo, PendingBasicDraft } from '@/api/types'
import { usePendingStore } from '@/stores/pending'
import { useUserStore } from '@/stores/user'
import { implClosePath, implCompilePath } from '@/utils/implementFlow'
import { isSilentAuthError } from '@/api/request'
import { fmtDate } from '@/utils/format'

const router = useRouter()
const user = useUserStore()
const pendingStore = usePendingStore()
const loading = ref(false)
const keyword = ref('')
const annualYear = ref(new Date().getFullYear())
const rows = ref<MilestoneTodo[]>([])
const page = ref(1)
const PAGE_SIZE = 10

const columns = [
  { title: '任务类型', dataIndex: 'typeLabel', width: 150 },
  { title: '项目编号', dataIndex: 'projectNo', width: 180 },
  { title: '项目名称', dataIndex: 'projectName', width: 280 },
  { title: '当前节点', key: 'node', width: 240 },
  { title: '负责人', dataIndex: 'ownerName', width: 120 },
  { title: '年度', dataIndex: 'year', width: 90 },
  { title: '状态', dataIndex: 'status', width: 120 },
  { title: '操作', key: 'action', width: 160, fixed: 'right' as const },
]

const filteredRows = computed(() => {
  const q = keyword.value.trim()
  if (!q) return rows.value
  return rows.value.filter((row) =>
    `${row.typeLabel || ''}${row.projectNo || ''}${row.projectName || ''}${row.ownerName || ''}${row.year || ''}`.includes(q),
  )
})

function rowKey(record: MilestoneTodo) {
  return `${record.taskType}-${record.projectId}-${record.milestoneId || ''}-${record.draftId || ''}-${record.year || ''}`
}

function basicDraftRow(d: PendingBasicDraft): MilestoneTodo {
  return {
    taskType: 'BASIC_AUDIT',
    typeLabel: '基本信息审批',
    draftId: d.draftId,
    projectId: d.projectId,
    projectNo: d.projectNo,
    projectName: d.projectName,
    ownerName: d.ownerName,
    flowNode: d.flowNode,
    flowNodeName: d.flowNodeName,
    submittedBy: d.submittedBy,
    submittedAt: d.submittedAt,
    status: 'APPROVING',
  }
}

async function load() {
  loading.value = true
  try {
    const [boardRes, draftRes] = await Promise.allSettled([
      milestoneApi.board({ year: annualYear.value }),
      projectApi.pendingBasicDrafts(),
    ])
    const list: MilestoneTodo[] = []
    if (boardRes.status === 'fulfilled') {
      const todos = ((boardRes.value.data as any)?.todos || []) as MilestoneTodo[]
      list.push(...todos.filter((row) => row.taskType === 'COMPILE_AUDIT' || row.taskType === 'CLOSE_AUDIT'))
    } else if (!isSilentAuthError(boardRes.reason)) {
      message.error(boardRes.reason?.message || '加载里程碑审核任务失败')
    }
    if (draftRes.status === 'fulfilled') {
      const drafts = ((draftRes.value.data as any) || []) as PendingBasicDraft[]
      list.push(...drafts.map(basicDraftRow))
    } else if (!isSilentAuthError(draftRes.reason)) {
      message.error(draftRes.reason?.message || '加载基本信息审批任务失败')
    }
    rows.value = list
    page.value = 1
    pendingStore.setMilestoneReviewCount(rows.value.length)
  } finally {
    loading.value = false
  }
}

function goAudit(row: MilestoneTodo) {
  if (row.taskType === 'BASIC_AUDIT') {
    router.push(`/implement/basic?projectId=${row.projectId}`)
    return
  }
  const path = row.taskType === 'CLOSE_AUDIT'
    ? implClosePath(row.projectId, row.milestoneId)
    : implCompilePath(row.projectId, row.milestoneId)
  const withYear = `${path}${path.includes('?') ? '&' : '?'}year=${row.year || annualYear.value}`
  router.push(withYear)
}

function nodeTitle(row: MilestoneTodo) {
  if (row.taskType === 'BASIC_AUDIT') return row.flowNodeName || row.flowNode || '基本信息审批'
  if (row.taskType === 'CLOSE_AUDIT') return row.flowNode || '里程碑销项审核'
  return '二级单位科技部门审核存档'
}

function nodeSub(row: MilestoneTodo) {
  if (row.taskType === 'BASIC_AUDIT') {
    return row.submittedBy ? `${row.submittedBy} 提交于 ${fmtDate(row.submittedAt, 'YYYY-MM-DD HH:mm')}` : '项目基本信息变更草稿'
  }
  if (row.taskType === 'CLOSE_AUDIT') return row.milestoneName || '节点销项材料'
  return '里程碑节点与交付物清单'
}

function typeColor(row: MilestoneTodo) {
  if (row.taskType === 'BASIC_AUDIT') return 'purple'
  if (row.taskType === 'CLOSE_AUDIT') return 'blue'
  return 'orange'
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">实施阶段待我审核</h2>
        <div class="page-desc">
          汇总当前账号在实施阶段需要审核的任务：里程碑清单审核、节点销项审核、项目基本信息审批。任务提交后会流转到指定审核人并在这里显示。
        </div>
      </div>
      <a-space>
        <span>年度</span>
        <a-input-number v-model:value="annualYear" :min="2020" :max="2100" @change="load" />
        <a-button @click="load">刷新</a-button>
      </a-space>
    </div>

    <a-alert
      v-if="rows.length"
      type="warning"
      show-icon
      class="review-alert"
      :message="`待我审核：${rows.length} 条实施阶段审核任务`"
      :description="`当前账号 ${user.realName}（${user.employeeNo || '—'}）是下列流程节点办理人，请进入对应页面完成审核。`"
    />

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <a-input
          v-model:value="keyword"
          allow-clear
          placeholder="搜索项目编号 / 名称 / 负责人"
          style="width: 320px"
          @press-enter="load"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <a-button type="primary" @click="load">查询</a-button>
      </div>

      <a-table
        :columns="columns"
        :data-source="filteredRows"
        :loading="loading"
        :row-key="rowKey"
        :scroll="{ x: 1300 }"
        :pagination="{ current: page, pageSize: PAGE_SIZE, total: filteredRows.length, showTotal: (t: number) => `共 ${t} 条`, onChange: (p: number) => { page = p } }"
        :locale="{ emptyText: loading ? '加载中…' : '暂无实施阶段待审核任务' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'node'">
            <div class="node-title">{{ nodeTitle(record) }}</div>
            <div class="node-sub">{{ nodeSub(record) }}</div>
          </template>
          <template v-else-if="column.dataIndex === 'typeLabel'">
            <a-tag :color="typeColor(record)">{{ record.typeLabel || '里程碑清单审核' }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag color="processing">待审核</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'year'">
            {{ record.taskType === 'BASIC_AUDIT' ? '—' : record.year || annualYear }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a-button type="link" size="small" @click="goAudit(record)">查看</a-button>
              <a-button type="primary" size="small" @click="goAudit(record)">审核</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.review-alert {
  margin-bottom: 16px;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.node-title {
  color: #262626;
  font-weight: 600;
}
.node-sub {
  margin-top: 2px;
  color: #8c8c8c;
  font-size: 12px;
}
</style>
