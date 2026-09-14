<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { declarationApi } from '@/api/modules'
import { LEVEL_TEXT } from '@/api/types'
import { fmtDate } from '@/utils/format'
import FilingFlowDialog from '@/components/filing/FilingFlowDialog.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { usePendingStore } from '@/stores/pending'

const router = useRouter()
const pendingStore = usePendingStore()
const loading = ref(false)
const rows = ref<any[]>([])
const approved = ref<any[]>([])
const done = ref<any[]>([])

const flowOpen = ref(false)
const flowDecl = ref<any>(null)

const STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  REVOKED: '已撤销',
  REPORTED: '已归档',
}

async function load() {
  loading.value = true
  try {
    const [a, b] = await Promise.all([
      declarationApi.page({ page: 1, size: 100, status: 'APPROVED' }),
      declarationApi.page({ page: 1, size: 100 }),
    ])
    approved.value = [
      ...((a.data as any)?.records || []),
      ...(((b.data as any)?.records || []).filter((r: any) => r.status === 'REPORTED' && r.flowNode === '线上报备归档')),
    ]
    rows.value = (b.data as any)?.records || []
    done.value = ((b.data as any)?.records || []).filter((r: any) => r.status === 'REPORTED' && r.flowNode !== '线上报备归档')
    pendingStore.setFilingPendingCount(Number((a.data as any)?.total || approved.value.length))
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function openLiveFlow(row: any) {
  const q: Record<string, string> = { declarationId: String(row.id) }
  if (row.projectId) q.projectId = String(row.projectId)
  router.push({ path: '/initiation/filing-materials', query: q })
}

/** 无业务单时按截图口径演示流转图（办结态） */
function openDemoFlow() {
  flowDecl.value = {
    applyNo: 'KY-2026-004',
    name: '11111',
    status: 'REPORTED',
    filingPhase: 'DONE',
    channelName: 'MJKY',
    projectStatusLabel: '实施中',
    filingMaterial: '立项通知,任务书,任务流程,合同,立项批复',
    posts: {
      leader: '系统管理员（100001）',
      hqDirector: '王建国（100003）',
    },
  }
  flowOpen.value = true
}

function doFiling(row: any) {
  openLiveFlow(row)
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">立项备案</h2>
    <div class="page-desc">
      申报审签/线上报备办结后，项目负责人上传本渠道必传支撑材料，总部科技部科研项目处审核通过后台账转为实施中；驳回则退回负责人补正。
      <a-button type="link" size="small" @click="openDemoFlow">查看立项备案流转图（演示）</a-button>
    </div>
    <WorkDutyBar code="filing" :project="null" />

    <a-alert
      type="info"
      show-icon
      message="申报审批与立项备案是两个连续环节"
      description="当前仍在项目负责人等审签节点的申报，请前往“项目申报”办理；全部审签通过后，才会进入本页“待备案项目”。"
      style="margin-bottom: 16px"
    >
      <template #action>
        <a-button type="primary" size="small" @click="router.push('/initiation/declaration')">前往项目申报审核</a-button>
      </template>
    </a-alert>

    <a-spin :spinning="loading">
      <a-row :gutter="16" style="margin-bottom: 16px">
        <a-col :span="8">
          <div class="stat-card">
            <div class="label">待备案（审批通过）</div>
            <div class="value">{{ approved.length }}</div>
          </div>
        </a-col>
        <a-col :span="8">
          <div class="stat-card ok">
            <div class="label">已备案归档</div>
            <div class="value">{{ done.length }}</div>
          </div>
        </a-col>
        <a-col :span="8">
          <div class="stat-card">
            <div class="label">申报总数</div>
            <div class="value">{{ rows.length }}</div>
          </div>
        </a-col>
      </a-row>

      <a-card :body-style="{ padding: '16px 20px' }" title="待备案项目">
        <a-table
          :data-source="approved"
          row-key="id"
          :pagination="false"
          :columns="[
            { title: '申报单号', dataIndex: 'applyNo', width: 140 },
            { title: '项目名称', dataIndex: 'name' },
            { title: '层级', dataIndex: 'levelCode', width: 90 },
            { title: '渠道类别', dataIndex: 'channelName', width: 180 },
            { title: '单位', dataIndex: 'orgName', width: 180 },
            { title: '状态', dataIndex: 'status', width: 100 },
            { title: '操作', key: 'act', width: 220, fixed: 'right' },
          ]"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'levelCode'">
              <a-tag color="blue">{{ LEVEL_TEXT[record.levelCode] }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-tag color="success">{{ STATUS_TEXT[record.status] }}</a-tag>
            </template>
            <template v-else-if="column.key === 'act'">
              <a-button type="link" size="small" @click="openLiveFlow(record)">查看流转</a-button>
              <a-button type="link" size="small" @click="doFiling(record)">填报支撑材料</a-button>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-card :body-style="{ padding: '16px 20px' }" title="全量申报记录" style="margin-top: 16px">
        <a-table
          :data-source="rows"
          row-key="id"
          :pagination="{ pageSize: 8 }"
          :columns="[
            { title: '申报单号', dataIndex: 'applyNo', width: 140 },
            { title: '项目名称', dataIndex: 'name' },
            { title: '渠道类别', dataIndex: 'channelName', width: 180 },
            { title: '负责人', dataIndex: 'applicant', width: 120 },
            { title: '申报日期', dataIndex: 'applyAt', width: 120 },
            { title: '状态', dataIndex: 'status', width: 100 },
            { title: '当前节点', dataIndex: 'flowNode', width: 160 },
            { title: '操作', key: 'act', width: 110, fixed: 'right' },
          ]"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'applyAt'">{{ fmtDate(record.applyAt) }}</template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-tag>{{ STATUS_TEXT[record.status] }}</a-tag>
            </template>
            <template v-else-if="column.key === 'act'">
              <a-button
                type="link"
                size="small"
                :disabled="!['APPROVED', 'REPORTED', 'REJECTED'].includes(record.status)"
                @click="openLiveFlow(record)"
              >
                查看流转
              </a-button>
            </template>
          </template>
        </a-table>
      </a-card>
    </a-spin>

    <FilingFlowDialog
      v-model:open="flowOpen"
      :declaration="flowDecl"
      @view-materials="router.push('/initiation/filing-materials')"
    />
  </div>
</template>
