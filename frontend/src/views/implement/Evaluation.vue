<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { evalApi, fileApi, projectApi } from '@/api/modules'
import { isSilentAuthError } from '@/api/request'
import ProjectSelect from '@/components/ProjectSelect.vue'
import ImplementFlowDialog from '@/components/implement/ImplementFlowDialog.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'
import { useUserStore } from '@/stores/user'
import { fmtDate } from '@/utils/format'

const route = useRoute()
const user = useUserStore()
const projectId = ref<number>()
const rows = ref<any[]>([])
const loading = ref(false)
const open = ref(false)
const flowOpen = ref(false)
const overviewData = ref<any>(null)
const project = ref<any>(null)
const editingId = ref<number>()
const form = reactive<any>({ evalType: 'MID', name: '', dueDate: '', result: 'PASS', conclusion: '' })
const { can, guard } = useWorkDuty('evaluation', project)

const EVAL_TEXT: Record<string, string> = { MID: '中期评估', QUARTER: '季度评估', YEAR: '年度评估', STAGE: '阶段性检查', SUPERVISE: '现场督导' }

const STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待二级单位初审',
  UNIT_OK: '待总部终审',
  DONE: '已归档',
  RECTIFYING: '整改中',
  REJECTED: '已驳回',
}
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  SUBMITTED: 'processing',
  UNIT_OK: 'blue',
  DONE: 'green',
  RECTIFYING: 'red',
  REJECTED: 'orange',
}

const ident = computed(() => user.identityCode || '')
const isUnitAuditor = computed(() => ident.value === 'unitHead' || user.isAdmin)
const isHqAuditor = computed(() => ['hqHead', 'hqStaff'].includes(ident.value) || user.isAdmin)
const channelName = computed(() => project.value?.channelName || project.value?.channelCode || '—')
// 先进材料创新联盟：二级单位审查确认即终审，无总部终审环节
const unitFinalOnly = computed(() => {
  const c = String(channelName.value || '')
  return c.includes('材料创新联盟') || c.includes('先进材料')
})

function isTeamEditable(status: string) {
  return ['DRAFT', 'REJECTED', 'RECTIFYING'].includes(status)
}
function canSubmit(row: any) {
  return can.value.fill && ['DRAFT', 'REJECTED'].includes(row.status)
}
function canUnitAudit(row: any) {
  return isUnitAuditor.value && row.status === 'SUBMITTED'
}
function canHqAudit(row: any) {
  return isHqAuditor.value && row.status === 'UNIT_OK'
}
function canRectify(row: any) {
  return can.value.fill && row.status === 'RECTIFYING'
}
function rowClass(row: any) {
  return row.status === 'RECTIFYING' ? 'row-rectify' : ''
}

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

function openCreate() {
  editingId.value = undefined
  Object.assign(form, { evalType: 'MID', name: '', dueDate: '', result: 'PASS', conclusion: '' })
  open.value = true
}
function openEdit(row: any) {
  editingId.value = row.id
  Object.assign(form, {
    evalType: row.evalType || 'MID',
    name: row.name || '',
    dueDate: row.dueDate || '',
    result: row.result || 'PASS',
    conclusion: row.conclusion || '',
  })
  open.value = true
}

async function save() {
  if (!guard('fill')) return
  if (!form.name?.trim()) return message.warning('请填写评估名称')
  try {
    if (editingId.value) {
      await evalApi.update(editingId.value, { ...form, projectId: projectId.value })
      message.success('评估检查已更新')
    } else {
      await evalApi.create({ ...form, projectId: projectId.value })
      message.success('评估检查已登记（草稿），可上传佐证后提交')
    }
    open.value = false
    load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '保存失败')
  }
}

async function submit(row: any) {
  try {
    await evalApi.submit(row.id)
    message.success('已提交二级单位主管部门初审')
    load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '提交失败')
  }
}

function audit(row: any, pass: boolean) {
  const isUnit = row.status === 'SUBMITTED'
  const nodeText = isUnit ? '二级单位初审' : '总部终审'
  Modal.confirm({
    title: pass ? `${nodeText}通过` : `${nodeText}退回`,
    content: pass
      ? (isUnit && !unitFinalOnly.value ? '初审通过后进入总部对应管理部门终审。' : '审核通过后：合格项归档，不合格项进入整改流程。')
      : '退回后由项目团队修改重新提交。',
    okText: pass ? '通过' : '退回',
    okType: pass ? 'primary' : 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await evalApi.audit(row.id, { pass })
        message.success(pass ? `${nodeText}已通过` : '已退回项目团队')
        load()
      } catch (e: any) {
        if (!isSilentAuthError(e)) message.error(e.message || '审核失败')
      }
    },
  })
}

const rectifyOpen = ref(false)
const rectifyRow = ref<any>(null)
const rectifyNote = ref('')
function openRectify(row: any) {
  rectifyRow.value = row
  rectifyNote.value = row.rectifyNote || ''
  rectifyOpen.value = true
}
async function submitRectify() {
  if (!rectifyNote.value.trim()) return message.warning('请填写整改说明')
  try {
    await evalApi.rectify(rectifyRow.value.id, { rectifyNote: rectifyNote.value.trim() })
    message.success('整改已提交，进入复核')
    rectifyOpen.value = false
    load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '整改提交失败')
  }
}

async function remove(row: any) {
  Modal.confirm({
    title: '删除评估检查',
    content: '仅草稿/已驳回可删除，删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await evalApi.remove(row.id)
        message.success('已删除')
        load()
      } catch (e: any) {
        if (!isSilentAuthError(e)) message.error(e.message || '删除失败')
      }
    },
  })
}

/* 佐证材料 */
const matOpen = ref(false)
const matRow = ref<any>(null)
const materials = ref<any[]>([])
const matUploading = ref(false)
async function openMaterials(row: any) {
  matRow.value = row
  matOpen.value = true
  await loadMaterials()
}
async function loadMaterials() {
  if (!matRow.value) return
  const res = await evalApi.materials(matRow.value.id)
  materials.value = (res.data as any[]) || []
}
async function uploadMat(file: File) {
  matUploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await fileApi.upload(fd, 'evaluation')
    const d: any = res.data
    await evalApi.uploadMaterial(matRow.value.id, {
      fileName: d?.fileName || file.name,
      fileUrl: d?.fileUrl || d?.url || '',
      fieldName: '评估佐证材料',
    })
    message.success('材料已上传')
    await loadMaterials()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '上传失败')
  } finally {
    matUploading.value = false
  }
  return false
}
async function removeMat(m: any) {
  await evalApi.removeMaterial(matRow.value.id, m.id)
  message.success('已删除')
  loadMaterials()
}

const viewOpen = ref(false)
const viewing = ref<any>(null)
function onView(record: any) {
  viewing.value = record
  viewOpen.value = true
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
        <a-space>
          <span>选择项目：</span><ProjectSelect v-model="projectId" @change="load" />
          <a-tag v-if="project" color="blue">渠道：{{ channelName }}</a-tag>
          <a-tag v-if="project" :color="unitFinalOnly ? 'purple' : 'geekblue'">
            {{ unitFinalOnly ? '二级单位审查确认即终审' : '总部对应管理部门终审' }}
          </a-tag>
        </a-space>
        <a-space>
          <a-button @click="openFlow">查看实施流转</a-button>
          <a-button type="primary" :disabled="!can.fill" @click="openCreate"><PlusOutlined />登记评估检查</a-button>
        </a-space>
      </div>

      <a-table :loading="loading" row-key="id" :pagination="false" :data-source="rows" :row-class-name="rowClass"
        :columns="[
          { title: '评估名称', dataIndex: 'name', width: 220 },
          { title: '评估类型', dataIndex: 'evalType', width: 110 },
          { title: '到期日', dataIndex: 'dueDate', width: 120 },
          { title: '结论', dataIndex: 'result', width: 100 },
          { title: '状态', dataIndex: 'status', width: 130 },
          { title: '操作', key: 'act', width: 300 },
        ]">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'evalType'">{{ EVAL_TEXT[record.evalType] || '-' }}</template>
          <template v-else-if="column.dataIndex === 'dueDate'">{{ fmtDate(record.dueDate) }}</template>
          <template v-else-if="column.dataIndex === 'result'">
            <a-tag v-if="record.result" :color="record.result === 'PASS' ? 'green' : 'red'">{{ record.result === 'PASS' ? '合格' : '不合格' }}</a-tag>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="STATUS_COLOR[record.status] || 'default'">{{ STATUS_TEXT[record.status] || record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'act'">
            <a-button type="link" size="small" @click="onView(record)">查看</a-button>
            <a-button type="link" size="small" @click="openMaterials(record)">佐证材料</a-button>
            <a-button v-if="can.fill && isTeamEditable(record.status)" type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button v-if="canSubmit(record)" type="link" size="small" @click="submit(record)">提交</a-button>
            <a-button v-if="canRectify(record)" type="link" size="small" @click="openRectify(record)">提交整改</a-button>
            <template v-if="canUnitAudit(record)">
              <a-button type="link" size="small" @click="audit(record, true)">初审通过</a-button>
              <a-button type="link" size="small" danger @click="audit(record, false)">退回</a-button>
            </template>
            <template v-if="canHqAudit(record)">
              <a-button type="link" size="small" @click="audit(record, true)">终审通过</a-button>
              <a-button type="link" size="small" danger @click="audit(record, false)">退回</a-button>
            </template>
            <a-button v-if="can.fill && ['DRAFT', 'REJECTED'].includes(record.status)" type="link" size="small" danger @click="remove(record)">删除</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 查看 -->
    <a-modal v-model:open="viewOpen" title="查看评估检查" :footer="null">
      <a-descriptions v-if="viewing" bordered size="small" :column="1">
        <a-descriptions-item label="评估名称">{{ viewing.name || '—' }}</a-descriptions-item>
        <a-descriptions-item label="评估类型">{{ EVAL_TEXT[viewing.evalType] || viewing.evalType || '—' }}</a-descriptions-item>
        <a-descriptions-item label="渠道">{{ viewing.channelCode || channelName }}</a-descriptions-item>
        <a-descriptions-item label="到期日">{{ fmtDate(viewing.dueDate) }}</a-descriptions-item>
        <a-descriptions-item label="结论">{{ viewing.result === 'PASS' ? '合格' : viewing.result === 'FAIL' ? '不合格' : '—' }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ STATUS_TEXT[viewing.status] || viewing.status }}</a-descriptions-item>
        <a-descriptions-item label="评审结论">{{ viewing.conclusion || '—' }}</a-descriptions-item>
        <a-descriptions-item v-if="viewing.rectifyNote" label="整改说明">{{ viewing.rectifyNote }}</a-descriptions-item>
        <a-descriptions-item label="提交人">{{ viewing.submitBy || '—' }}</a-descriptions-item>
        <a-descriptions-item label="最近审核">{{ viewing.auditBy || '—' }}{{ viewing.auditAt ? ' · ' + fmtDate(viewing.auditAt) : '' }}</a-descriptions-item>
      </a-descriptions>
      <div style="text-align: right; margin-top: 16px">
        <a-button type="primary" @click="viewOpen = false">关闭</a-button>
      </div>
    </a-modal>

    <!-- 登记/编辑 -->
    <a-modal v-model:open="open" :title="editingId ? '编辑评估检查' : '登记评估检查'" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="项目渠道">
          <a-input :value="channelName" disabled />
        </a-form-item>
        <a-form-item label="评估类型">
          <a-select v-model:value="form.evalType">
            <a-select-option v-for="(v, k) in EVAL_TEXT" :key="k" :value="k">{{ v }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="评估名称" required><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="到期日"><a-date-picker v-model:value="form.dueDate" style="width: 100%" value-format="YYYY-MM-DD" /></a-form-item>
        <a-form-item label="评审结论">
          <a-radio-group v-model:value="form.result">
            <a-radio value="PASS">合格</a-radio>
            <a-radio value="FAIL">不合格（需整改）</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="结论说明">
          <a-textarea v-model:value="form.conclusion" :rows="3" placeholder="填写评审结论、专家意见等" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 整改 -->
    <a-modal v-model:open="rectifyOpen" title="提交整改" @ok="submitRectify">
      <a-alert type="warning" show-icon style="margin-bottom: 12px"
        message="评估不合格，请填写整改说明并上传整改佐证后提交，复核通过后方可归档。" />
      <a-form layout="vertical">
        <a-form-item label="整改说明" required>
          <a-textarea v-model:value="rectifyNote" :rows="4" placeholder="描述整改措施与结果" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 佐证材料 -->
    <a-modal v-model:open="matOpen" title="评估佐证材料" :footer="null" width="640px">
      <a-upload :before-upload="uploadMat" :show-upload-list="false" :disabled="matUploading || !can.fill">
        <a-button type="primary" :loading="matUploading" :disabled="!can.fill"><PlusOutlined />上传材料</a-button>
      </a-upload>
      <a-table style="margin-top: 12px" size="small" row-key="id" :pagination="false" :data-source="materials"
        :columns="[
          { title: '文件名', dataIndex: 'fileName' },
          { title: '上传人', dataIndex: 'uploadedBy', width: 120 },
          { title: '操作', key: 'act', width: 130 },
        ]">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'act'">
            <a-button type="link" size="small" :href="record.fileUrl" target="_blank">下载</a-button>
            <a-button v-if="can.fill" type="link" size="small" danger @click="removeMat(record)">删除</a-button>
          </template>
        </template>
        <template #emptyText>暂无佐证材料</template>
      </a-table>
    </a-modal>

    <ImplementFlowDialog v-model:open="flowOpen" :overview="overviewData" />
  </div>
</template>

<style scoped>
:deep(.row-rectify) {
  background: #fff1f0;
}
</style>
