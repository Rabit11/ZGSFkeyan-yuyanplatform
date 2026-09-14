<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { evalApi, projectApi } from '@/api/modules'
import { isSilentAuthError } from '@/api/request'
import ProjectSelect from '@/components/ProjectSelect.vue'
import ImplementFlowDialog from '@/components/implement/ImplementFlowDialog.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'
import { fmtDate } from '@/utils/format'

const route = useRoute()
const projectId = ref<number>()
const rows = ref<any[]>([])
const loading = ref(false)
const open = ref(false)
const flowOpen = ref(false)
const overviewData = ref<any>(null)
const project = ref<any>(null)
const form = reactive<any>({ evalType: 'MID', name: '', dueDate: '', result: 'PASS' })
const { can, guard } = useWorkDuty('evaluation', project)

const EVAL_TEXT: Record<string, string> = { MID: '中期评估', QUARTER: '季度评估', YEAR: '年度评估', STAGE: '阶段性检查', SUPERVISE: '现场督导' }

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const [res, p] = await Promise.all([evalApi.list(projectId.value), projectApi.detail(projectId.value)])
    rows.value = (res.data as any[]) || []
    project.value = p.data
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  const res = await projectApi.page({ page: 1, size: 1 })
  projectId.value = Number(route.query.projectId) || (res.data as any)?.records?.[0]?.id
  load()
})

const viewOpen = ref(false)
const viewing = ref<any>(null)
function onView(record: any) {
  viewing.value = record
  viewOpen.value = true
}

async function save() {
  if (!guard('fill')) return
  await evalApi.create({ ...form, projectId: projectId.value })
  message.success('评估检查材料已上传')
  open.value = false
  load()
}
async function remove(row: any) {
  await evalApi.remove(row.id)
  message.success('已删除')
  load()
}

async function openFlow() {
  if (!projectId.value) {
    message.info('请先选择项目')
    return
  }
  try {
    const res = await projectApi.overview(projectId.value)
    overviewData.value = res.data
    flowOpen.value = true
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '加载实施流转失败')
  }
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">评估检查</h2>
    <div class="page-desc">
      各渠道项目按需上传 / 总部平台抓取中期评估、季度报告、督导材料；评估结束后上传评审结论与检查佐证材料至平台存档。
      <b>评估不合格需启动整改流程，整改完成后方可继续推进项目</b>。审批流：项目团队填报 → 二级单位主管部门初审 → 总部对应管理部门逐级终审。
    </div>
    <WorkDutyBar code="evaluation" :project="project" />

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <a-space><span>选择项目：</span><ProjectSelect v-model="projectId" @change="load" /></a-space>
        <a-space>
          <a-button @click="openFlow">查看实施流转</a-button>
          <a-button type="primary" :disabled="!can.fill" @click="open = true"><PlusOutlined />登记评估检查</a-button>
        </a-space>
      </div>

      <a-table :loading="loading" row-key="id" :pagination="false" :data-source="rows"
        :columns="[
          { title: '评估名称', dataIndex: 'name', width: 240 },
          { title: '评估类型', dataIndex: 'evalType', width: 120 },
          { title: '到期日', dataIndex: 'dueDate', width: 130 },
          { title: '结论', dataIndex: 'result', width: 110 },
          { title: '状态', dataIndex: 'status', width: 110 },
          { title: '操作', key: 'act', width: 140 },
        ]">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'evalType'">{{ EVAL_TEXT[record.evalType] || '-' }}</template>
          <template v-else-if="column.dataIndex === 'dueDate'">{{ fmtDate(record.dueDate) }}</template>
          <template v-else-if="column.dataIndex === 'result'">
            <a-tag v-if="record.result" :color="record.result === 'PASS' ? 'green' : 'red'">{{ record.result === 'PASS' ? '合格' : '不合格' }}</a-tag>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 'DONE' ? 'green' : 'processing'">{{ record.status === 'DONE' ? '已完成' : '待开展' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'act'">
            <a-button type="link" size="small" @click="onView(record)">查看</a-button>
            <a-button type="link" size="small" danger @click="remove(record)">删除</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="viewOpen" title="查看评估检查" :footer="null">
      <a-descriptions v-if="viewing" bordered size="small" :column="1">
        <a-descriptions-item label="评估名称">{{ viewing.name || '—' }}</a-descriptions-item>
        <a-descriptions-item label="评估类型">{{ EVAL_TEXT[viewing.evalType] || viewing.evalType || '—' }}</a-descriptions-item>
        <a-descriptions-item label="到期日">{{ fmtDate(viewing.dueDate) }}</a-descriptions-item>
        <a-descriptions-item label="结论">{{ viewing.result === 'PASS' ? '合格' : viewing.result === 'FAIL' ? '不合格' : '—' }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ viewing.status === 'DONE' ? '已完成' : '待开展' }}</a-descriptions-item>
      </a-descriptions>
      <div style="text-align: right; margin-top: 16px">
        <a-button type="primary" @click="viewOpen = false">关闭</a-button>
      </div>
    </a-modal>
    <a-modal v-model:open="open" title="登记评估检查" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="评估类型">
          <a-select v-model:value="form.evalType">
            <a-select-option v-for="(v, k) in EVAL_TEXT" :key="k" :value="k">{{ v }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="评估名称"><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="到期日"><a-date-picker v-model:value="form.dueDate" style="width: 100%" value-format="YYYY-MM-DD" /></a-form-item>
        <a-form-item label="结论">
          <a-radio-group v-model:value="form.result">
            <a-radio value="PASS">合格</a-radio>
            <a-radio value="FAIL">不合格（需整改）</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
    <ImplementFlowDialog v-model:open="flowOpen" :overview="overviewData" />
  </div>
</template>
