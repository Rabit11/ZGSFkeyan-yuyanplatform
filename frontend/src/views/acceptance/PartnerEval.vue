<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { partnerApi, projectApi } from '@/api/modules'
import { useDictStore } from '@/stores/dict'
import ProjectSelect from '@/components/ProjectSelect.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { fmtDate } from '@/utils/format'
import { useWorkDuty } from '@/composables/useWorkDuty'

const dictStore = useDictStore()
const route = useRoute()
const projectId = ref<number>()
const project = ref<any>(null)
const rows = ref<any[]>([])
const blacklist = ref<any[]>([])
const loading = ref(false)
const open = ref(false)
const form = reactive<any>({
  partnerName: '',
  partnerType: 'PARTNER',
  techScore: 0, qualityScore: 0, progressScore: 0, serviceScore: 0, complianceScore: 0,
  dueDate: '',
})
const { can, guard } = useWorkDuty('partner_eval', project)

const DIMS = [
  { key: 'techScore', label: '技术能力', max: 20 },
  { key: 'qualityScore', label: '交付质量', max: 20 },
  { key: 'progressScore', label: '进度履约', max: 20 },
  { key: 'serviceScore', label: '服务配合', max: 20 },
  { key: 'complianceScore', label: '合规性', max: 20 },
]

const GRADE_TEXT: Record<string, string> = { EXCELLENT: '优秀', GOOD: '良好', PASS: '合格', FAIL: '不合格' }
const GRADE_COLOR: Record<string, string> = { EXCELLENT: 'green', GOOD: 'blue', PASS: 'orange', FAIL: 'red' }

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const [a, b, p] = await Promise.all([
      partnerApi.list(projectId.value),
      partnerApi.blacklist(),
      projectApi.detail(projectId.value),
    ])
    rows.value = (a.data as any[]) || []
    blacklist.value = (b.data as any[]) || []
    project.value = p.data
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await dictStore.load('PARTNER_TYPE')
  const res = await projectApi.page({ page: 1, size: 1 })
  projectId.value = Number(route.query.projectId) || (res.data as any)?.records?.[0]?.id
  load()
})

const total = computed(() => DIMS.reduce((s, d) => s + (Number(form[d.key]) || 0), 0))
const previewGrade = computed(() => (total.value >= 90 ? 'EXCELLENT' : total.value >= 80 ? 'GOOD' : total.value >= 60 ? 'PASS' : 'FAIL'))

async function save() {
  if (!guard('fill')) return
  await partnerApi.create({ ...form, projectId: projectId.value })
  message.success('协作单位已入库')
  open.value = false
  load()
}
async function submit(row: any) {
  if (!guard('submit')) return
  Modal.confirm({
    title: '确认提交评价？',
    content: `评价总分 ${row.score ?? total.value} 分，提交后不可修改；不合格单位将自动纳入黑名单。`,
    onOk: async () => {
      await partnerApi.submit(row.id)
      message.success('评价已提交')
      load()
    },
  })
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">协作单位评价</h2>
    <div class="page-desc">
      协作单位分为参研单位与外协单位。<b>针对参研单位，自项目验收完成后 30 日内完成评价；针对外协单位，自外协合同验收后 30 日内完成评价</b>。
      由项目团队与管理团队从技术能力、交付质量、进度履约、服务配合、合规性 5 个维度量化评分（各 20 分）；
      评级：优秀（≥90）、良好（≥80）、合格（≥60）、<b>不合格（&lt;60）纳入黑名单</b>。
    </div>
    <WorkDutyBar code="partner_eval" :project="project" />

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <a-space><span>选择项目：</span><ProjectSelect v-model="projectId" @change="load" /></a-space>
        <a-button type="primary" :disabled="!can.fill" @click="open = true"><PlusOutlined />协作单位入库</a-button>
      </div>

      <a-table :loading="loading" row-key="id" :pagination="false" :data-source="rows" :scroll="{ x: 1400 }"
        :columns="[
          { title: '协作单位名称', dataIndex: 'partnerName', width: 220 },
          { title: '协作类型', dataIndex: 'partnerType', width: 110 },
          { title: '技术能力', dataIndex: 'techScore', width: 90 },
          { title: '交付质量', dataIndex: 'qualityScore', width: 90 },
          { title: '进度履约', dataIndex: 'progressScore', width: 90 },
          { title: '服务配合', dataIndex: 'serviceScore', width: 90 },
          { title: '合规性', dataIndex: 'complianceScore', width: 90 },
          { title: '总分', dataIndex: 'score', width: 80 },
          { title: '等级', dataIndex: 'grade', width: 90 },
          { title: '评价到期', dataIndex: 'dueDate', width: 120 },
          { title: '状态', dataIndex: 'status', width: 100 },
          { title: '操作', key: 'act', width: 110, fixed: 'right' },
        ]">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'partnerType'">{{ dictStore.label('PARTNER_TYPE', record.partnerType) }}</template>
          <template v-else-if="column.dataIndex === 'dueDate'">{{ fmtDate(record.dueDate) }}</template>
          <template v-else-if="column.dataIndex === 'grade'">
            <a-tag v-if="record.grade" :color="GRADE_COLOR[record.grade]">{{ GRADE_TEXT[record.grade] }}</a-tag>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 'DONE' ? 'green' : 'processing'">{{ record.status === 'DONE' ? '已评价' : '待评价' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'act'">
            <a-button type="link" size="small" :disabled="record.status === 'DONE'" @click="submit(record)">提交评价</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card :body-style="{ padding: '16px 20px' }" title="协作单位黑名单" style="margin-top: 16px">
      <a-empty v-if="!blacklist.length" description="暂无黑名单记录" />
      <a-table v-else :data-source="blacklist" row-key="id" :pagination="false" size="small"
        :columns="[
          { title: '单位名称', dataIndex: 'partnerName', width: 240 },
          { title: '纳入原因', dataIndex: 'reason' },
          { title: '纳入日期', dataIndex: 'inDate', width: 120 },
        ]" />
    </a-card>

    <a-drawer v-model:open="open" title="协作单位入库" :width="'40%'">
      <a-form layout="vertical">
        <a-form-item label="协作单位名称" required><a-input v-model:value="form.partnerName" placeholder="与合同签署单位名称保持一致" /></a-form-item>
        <a-form-item label="协作类型">
          <a-select v-model:value="form.partnerType">
            <a-select-option v-for="o in dictStore.options('PARTNER_TYPE')" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="评价到期日"><a-date-picker v-model:value="form.dueDate" style="width: 100%" value-format="YYYY-MM-DD" /></a-form-item>
        <a-divider orientation="left">五维度量化评分（各 20 分）</a-divider>
        <a-form-item v-for="d in DIMS" :key="d.key" :label="`${d.label}（满分 ${d.max} 分）`">
          <a-slider v-model:value="form[d.key]" :min="0" :max="d.max" />
        </a-form-item>
        <a-alert :type="previewGrade === 'FAIL' ? 'error' : 'info'" show-icon
          :message="`当前总分 ${total} 分，预计评级：${GRADE_TEXT[previewGrade]}`" />
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
