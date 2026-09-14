<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { postEvalApi, projectApi } from '@/api/modules'
import StatusTag from '@/components/StatusTag.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { fmtDate } from '@/utils/format'
import { useWorkDuty } from '@/composables/useWorkDuty'

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ page: 1, size: 10 })
const projects = ref<any[]>([])

const open = ref(false)
const editing = ref<any>(null)
const form = reactive<any>({
  projectId: undefined, dueDate: '', goalAchieve: '', progressCtrl: '',
  fundExec: '', achievementOutput: '', partnerPerform: '', riskCtrl: '', score: undefined, conclusion: '',
})
const selectedProject = computed(() => projects.value.find((p) => p.id === form.projectId) || projects.value[0] || null)
const { can, guard } = useWorkDuty('post_eval', selectedProject)

const STATUS_TEXT: Record<string, string> = { PENDING: '待开展', DOING: '进行中', DONE: '已完成' }

async function load() {
  loading.value = true
  try {
    const res = await postEvalApi.page(query)
    rows.value = (res.data as any)?.records || []
    total.value = (res.data as any)?.total || 0
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  const p = await projectApi.page({ page: 1, size: 200 })
  projects.value = (p.data as any)?.records || []
  load()
})

async function save() {
  if (!guard('fill')) return
  if (editing.value) await postEvalApi.update(editing.value.id, form)
  else await postEvalApi.create(form)
  message.success('保存成功')
  open.value = false
  load()
}
async function submit(row: any) {
  if (!guard('submit')) return
  Modal.confirm({
    title: '确认提交后评价？',
    content: '提交后评价结果将自动归集至项目台账与可视化看板，作为后续立项、资源调配、协作单位遴选的参考依据。',
    onOk: async () => { await postEvalApi.submit(row.id); message.success('后评价已提交'); load() },
  })
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">后评价</h2>
    <div class="page-desc">
      后评价是科研项目全生命周期的最终闭环环节。前期仅选取超 1 亿项目开展后评价，<b>项目完成最终公司验收 3 年内办理，由二级单位组织，逾期系统自动触发预警</b>。
      由管理团队牵头组织、项目团队填报基础信息，涵盖整体目标达成、进度管控、经费执行、科研成果、协作履约、风险管控等内容；
      执行二级单位、总部分级审核，评价结果自动归集至台账与看板。
    </div>
    <WorkDutyBar code="post_eval" :project="selectedProject" />

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <span />
        <a-button type="primary" :disabled="!can.fill" @click="(editing = null, Object.assign(form, { projectId: undefined, dueDate: '', goalAchieve: '', progressCtrl: '', fundExec: '', achievementOutput: '', partnerPerform: '', riskCtrl: '', score: undefined, conclusion: '' }), open = true)">
          <PlusOutlined />发起后评价
        </a-button>
      </div>

      <a-table :loading="loading" row-key="id" :data-source="rows"
        :pagination="{ current: query.page, pageSize: query.size, total, showTotal: (t: number) => `共 ${t} 条` }"
        @change="(p: any) => { query.page = p.current; load() }"
        :columns="[
          { title: '项目', dataIndex: 'projectName', width: 320 },
          { title: '到期日', dataIndex: 'dueDate', width: 130 },
          { title: '评分', dataIndex: 'score', width: 90 },
          { title: '状态', dataIndex: 'status', width: 100 },
          { title: '预警', dataIndex: 'colorStatus', width: 110 },
          { title: '操作', key: 'act', width: 170, fixed: 'right' },
        ]">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'dueDate'">{{ fmtDate(record.dueDate) }}</template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 'DONE' ? 'green' : 'processing'">{{ STATUS_TEXT[record.status] }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'colorStatus'"><StatusTag :color="record.colorStatus" /></template>
          <template v-else-if="column.key === 'act'">
            <a-space :size="2">
              <a-button type="link" size="small" @click="(editing = record, Object.assign(form, record), open = true)">编辑</a-button>
              <a-divider type="vertical" />
              <a-button type="link" size="small" :disabled="record.status === 'DONE'" @click="submit(record)">提交</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="open" title="后评价填报" :width="'60%'">
      <a-form layout="vertical">
        <a-form-item label="项目" required>
          <a-select v-model:value="form.projectId" show-search placeholder="选择项目" :filter-option="(i: string, o: any) => String(o.label).includes(i)">
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name">{{ p.projectNo }} · {{ p.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="后评价到期日"><a-date-picker v-model:value="form.dueDate" style="width: 100%" value-format="YYYY-MM-DD" /></a-form-item>
        <a-form-item label="整体目标达成情况"><a-textarea v-model:value="form.goalAchieve" :rows="2" /></a-form-item>
        <a-form-item label="实施进度管控"><a-textarea v-model:value="form.progressCtrl" :rows="2" /></a-form-item>
        <a-form-item label="经费预算执行"><a-textarea v-model:value="form.fundExec" :rows="2" /></a-form-item>
        <a-form-item label="科研成果产出"><a-textarea v-model:value="form.achievementOutput" :rows="2" /></a-form-item>
        <a-form-item label="协作履约"><a-textarea v-model:value="form.partnerPerform" :rows="2" /></a-form-item>
        <a-form-item label="风险管控"><a-textarea v-model:value="form.riskCtrl" :rows="2" /></a-form-item>
        <a-form-item label="综合评分"><a-input-number v-model:value="form.score" :min="0" :max="100" style="width: 100%" /></a-form-item>
        <a-form-item label="评价结论"><a-textarea v-model:value="form.conclusion" :rows="2" /></a-form-item>
      </a-form>
      <template #footer>
        <div style="text-align: right">
          <a-button style="margin-right: 8px" @click="open = false">取消</a-button>
          <a-button type="primary" @click="save">保存</a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>
