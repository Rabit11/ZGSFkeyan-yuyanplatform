<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  CloudUploadOutlined,
  FileDoneOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons-vue'
import { acceptanceApi, fileApi, projectApi } from '@/api/modules'
import ProjectSelect from '@/components/ProjectSelect.vue'
import AcceptFlowDialog from '@/components/acceptance/AcceptFlowDialog.vue'
import { buildAcceptFlowOptsFromOverview } from '@/utils/acceptFlow'
import { fmtDate } from '@/utils/format'
import { useWorkDuty } from '@/composables/useWorkDuty'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { downloadAuthenticatedFile } from '@/utils/authFile'

const projectId = ref<number>()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
const checks = ref<any[]>([])
const checking = ref(false)
const overview = ref<any>({})
const flowOpen = ref(false)
const uploadRow = ref<any>(null)
const uploading = ref(false)
const auditOpen = ref(false)
const auditOpinion = ref('同意')
const dutyProject = computed(() => overview.value.project || { id: projectId.value, teamMembers: overview.value.teamMembers })
const { can, guard } = useWorkDuty('accept', dutyProject)

const LEVEL_NAME: Record<string, string> = { UNIT: '单位级验收', COMPANY: '公司级验收', NATIONAL: '国家级验收', LOCAL: '属地主管部门验收' }
const STATUS_TEXT: Record<string, string> = {
  NOT_STARTED: '未启动', CHECKING: '校验中', APPLYING: '申请中', ACCEPTING: '验收中', DONE: '已办结',
}

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const [res, ov] = await Promise.all([
      acceptanceApi.detail(projectId.value),
      projectApi.overview(projectId.value).catch(() => ({ data: {} })),
    ])
    detail.value = res.data || {}
    items.value = detail.value.items || []
    overview.value = ov.data || {}
    checks.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const res = await projectApi.page({ page: 1, size: 1 })
  projectId.value = Number(route.query.projectId) || (res.data as any)?.records?.[0]?.id
  load()
})

const grouped = computed(() => {
  const map: Record<string, any[]> = {}
  items.value.forEach((i) => {
    ;(map[i.levelCode] ||= []).push(i)
  })
  return Object.entries(map).map(([code, list]) => ({ code, name: LEVEL_NAME[code] || code, list }))
})

const allPassed = computed(() => checks.value.length > 0 && checks.value.every((c) => c.passed))
const requiredItems = computed(() => items.value.filter((i) => Number(i.required) === 1 && Number(i.locked) !== 1))
const uploadedItems = computed(() => requiredItems.value.filter((i) => i.status === 'UPLOADED' || i.status === 'APPROVED' || i.fileUrl))
const materialReady = computed(() => requiredItems.value.length > 0 && uploadedItems.value.length === requiredItems.value.length)
const missingItems = computed(() => requiredItems.value.filter((i) => !(i.status === 'UPLOADED' || i.status === 'APPROVED' || i.fileUrl)))
const finished = computed(() => detail.value.status === 'DONE')
const currentProject = computed(() => overview.value.project || {})
const progressPercent = computed(() => {
  const total = flowOpts.value.totalCount || 1
  return Math.round((flowOpts.value.doneCount / total) * 100)
})

const flowOpts = computed(() =>
  buildAcceptFlowOptsFromOverview(
    {
      ...overview.value,
      project: overview.value.project || { id: projectId.value },
      acceptance: { ...detail.value, items: items.value },
    },
    { items: items.value },
  ),
)

const currentFlowNode = computed(() => flowOpts.value.nodes.find((n) => n.status === 'current' || n.status === 'return'))
const canAuditCurrent = computed(() => !!currentFlowNode.value && currentFlowNode.value.nodeType === 'AUDIT' && detail.value.canAudit === true && !finished.value)
const needsChiefReview = computed(() => !!detail.value.expertReview)

function flowState(code: string) {
  if (code === 'ACCEPT_GATE') {
    if (allPassed.value) return 'done'
    if (checks.value.length) return 'return'
    return detail.value.status === 'NOT_STARTED' || !detail.value.status ? 'current' : 'done'
  }
  if (code === 'ACCEPT_MATERIAL') {
    if (materialReady.value) return 'done'
    if (detail.value.currentNode === 'ACCEPT_APPLY') return 'return'
    return allPassed.value ? 'current' : 'pending'
  }
  const node = flowOpts.value.nodes.find((n) => n.nodeCode === code)
  if (node) return node.status
  if (code === 'ACCEPT_CHIEF_REVIEW' && !needsChiefReview.value) return 'skip'
  return 'pending'
}

function chartClass(code: string, extra = '') {
  return [flowState(code), extra]
}

function uploaded(row: any) {
  return row.status === 'UPLOADED' || row.status === 'APPROVED' || !!row.fileUrl
}

function canOperateMaterial(row: any) {
  return !row.locked && detail.value.canEditMaterials === true
}

function materialFileName(row: any) {
  return row.fileName || (row.fileUrl ? row.materialName + '.pdf' : '')
}

async function openMaterial(row: any) {
  try {
    await downloadAuthenticatedFile(row.fileUrl, materialFileName(row))
  } catch (e: any) {
    message.error(e?.message || '附件下载失败')
  }
}

async function runCheck() {
  checking.value = true
  try {
    const res = await acceptanceApi.check(projectId.value!)
    checks.value = (res.data as any[]) || []
    if (checks.value.every((c: any) => c.passed)) message.success('前置条件校验通过，可提交验收申请')
    else message.warning('存在未满足的前置条件，无法提交验收申请')
  } finally {
    checking.value = false
  }
}

async function submit() {
  if (detail.value.canSubmit !== true) return
  if (!allPassed.value) {
    message.error('请先通过前置条件校验')
    return
  }
  if (!materialReady.value) {
    message.error(`请先补齐必传验收材料：${missingItems.value.map((i) => i.materialName).join('、')}`)
    return
  }
  await acceptanceApi.submit(projectId.value!)
  message.success('验收申请已提交')
  load()
}

function openUpload(item: any) {
  if (!canOperateMaterial(item)) return
  uploadRow.value = item
}

async function uploadFile(file: File) {
  if (!uploadRow.value || !projectId.value) return false
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    const uploaded = (await fileApi.upload(data, 'acceptance')).data as any
    await acceptanceApi.materials(projectId.value, {
      fieldCode: uploadRow.value.fieldCode,
      fileName: uploaded.fileName || file.name,
      fileUrl: uploaded.fileUrl,
      fileSize: uploaded.fileSize || file.size,
    })
    message.success(`已上传：${uploaded.fileName || file.name}`)
    uploadRow.value = null
    load()
  } catch (e: any) {
    message.error(e?.message || '材料上传失败')
  } finally {
    uploading.value = false
  }
  return false
}

function openAudit() {
  if (!canAuditCurrent.value) return
  auditOpinion.value = '同意'
  auditOpen.value = true
}

async function audit(pass: boolean) {
  if (!pass && !auditOpinion.value.trim()) {
    message.error('退回时请填写意见')
    return
  }
  Modal.confirm({
    title: pass ? '确认通过当前节点？' : '确认退回补正？',
    onOk: async () => {
      await acceptanceApi.audit(projectId.value!, {
        nodeCode: currentFlowNode.value?.nodeCode,
        pass,
        opinion: auditOpinion.value,
      })
      message.success(pass ? '已通过当前节点' : '已退回项目团队补正')
      auditOpen.value = false
      load()
    },
  })
}

function goPartnerEval() {
  router.push({ path: '/acceptance/partner', query: projectId.value ? { projectId: projectId.value } : {} })
}
</script>

<template>
  <div class="page-container accept-page">
    <div class="accept-head">
      <div>
        <h2 class="page-title">项目验收</h2>
        <div class="project-line">
          <span>{{ currentProject.projectNo || '请选择项目' }}</span>
          <b v-if="currentProject.name">{{ currentProject.name }}</b>
        </div>
      </div>
      <a-space>
        <a-button @click="flowOpen = true"><ProfileOutlined />查看详情与附件</a-button>
        <a-button v-if="finished" type="primary" @click="goPartnerEval">去协作单位评价</a-button>
      </a-space>
    </div>
    <WorkDutyBar code="accept" :project="dutyProject" />

    <a-card class="flow-card" :body-style="{ padding: '16px 20px' }">
      <div class="section-title">
        <span>验收流程</span>
        <a-space>
          <a-tag :color="finished ? 'green' : 'blue'">{{ STATUS_TEXT[detail.status] || detail.status || '未启动' }}</a-tag>
          <a-progress :percent="progressPercent" size="small" :show-info="false" style="width: 160px" />
        </a-space>
      </div>
      <div class="accept-flowchart">
        <div class="chart-node start done">项目验收准备</div>
        <div class="chart-line"></div>
        <div class="chart-node" :class="chartClass('ACCEPT_GATE')">发起前置条件校验</div>
        <div class="chart-line"></div>
        <div class="chart-node wide" :class="chartClass('ACCEPT_MATERIAL')">
          项目团队上传验收材料
          <small>验收申请 / 任务清单 / 佐证材料 / 合同等</small>
        </div>
        <div class="chart-line"></div>
        <div class="chart-row">
          <div class="chart-side">
            <span class="branch-label">否</span>
            <div class="chart-node small" :class="chartClass('ACCEPT_MATERIAL')">补齐材料</div>
          </div>
          <div class="chart-diamond" :class="chartClass('ACCEPT_MATERIAL', 'diamond')"><span>材料是否齐套？</span></div>
        </div>
        <div class="chart-line yes"></div>
        <div class="chart-node" :class="chartClass('ACCEPT_APPLY')">提交验收申请</div>
        <div class="chart-line"></div>
        <div class="chart-node" :class="chartClass('ACCEPT_UNIT_REVIEW')">二级单位管理部门审核</div>
        <div class="chart-line"></div>
        <div class="chart-row review-row">
          <div class="chart-diamond" :class="needsChiefReview ? chartClass('ACCEPT_CHIEF_REVIEW', 'diamond') : 'skip diamond'">
            <span>是否需要责任总师复核？</span>
          </div>
          <div v-if="needsChiefReview" class="chart-side right">
            <span class="branch-label">是</span>
            <div class="chart-node small" :class="chartClass('ACCEPT_CHIEF_REVIEW')">责任总师技术复核</div>
          </div>
        </div>
        <div class="chart-line yes"></div>
        <div class="chart-node" :class="chartClass('ACCEPT_HQ_TECH')">行业总部技术初审</div>
        <div class="chart-line"></div>
        <div class="chart-node" :class="chartClass('ACCEPT_HQ_FINAL')">总部管理团队终审</div>
        <div class="chart-line"></div>
        <div class="chart-row">
          <div class="chart-side">
            <span class="branch-label">不通过</span>
            <div class="chart-node small return">退回补正</div>
          </div>
          <div class="chart-diamond" :class="finished ? 'done diamond' : chartClass(detail.currentNode || 'ACCEPT_HQ_FINAL', 'diamond')">
            <span>审核结果</span>
          </div>
        </div>
        <div class="chart-line yes"></div>
        <div class="chart-node" :class="chartClass('ACCEPT_ARCHIVE')">提交总部科技部备案归档</div>
        <div class="chart-line"></div>
        <div class="chart-node end" :class="finished ? 'done' : 'pending'">验收办结，进入协作单位评价</div>
      </div>
    </a-card>

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <a-space><span>选择项目：</span><ProjectSelect v-model="projectId" @change="load" /></a-space>
        <a-space>
          <a-button :loading="checking" @click="runCheck"><SafetyCertificateOutlined />前置条件校验</a-button>
          <a-button @click="flowOpen = true">查看验收流转</a-button>
          <a-button type="primary" :disabled="!allPassed || !materialReady || detail.canSubmit !== true || finished" @click="submit">
            提交验收申请
          </a-button>
          <a-button :disabled="!canAuditCurrent" @click="openAudit">审核处理</a-button>
        </a-space>
      </div>

      <a-spin :spinning="loading">
        <a-descriptions bordered size="small" :column="4" style="margin-bottom: 16px">
          <a-descriptions-item label="验收层级">{{ LEVEL_NAME[detail.acceptLevel] || detail.acceptLevel }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ STATUS_TEXT[detail.status] || detail.status }}</a-descriptions-item>
          <a-descriptions-item label="当前节点">{{ currentFlowNode?.title || '—' }}</a-descriptions-item>
          <a-descriptions-item label="责任总师技术复核">
            <a-tag :color="detail.expertReview ? 'red' : 'default'">{{ detail.expertReview ? '需要' : '不需要' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="协作单位评价到期日">{{ fmtDate(detail.partnerDueDate) }}</a-descriptions-item>
        </a-descriptions>

        <!-- 前置校验结果 -->
        <a-alert
          v-if="checks.length"
          :type="allPassed ? 'success' : 'error'"
          show-icon
          style="margin-bottom: 16px"
          :message="allPassed ? '前置条件全部满足，可提交验收申请' : '存在未满足的前置条件，禁止提交验收申请'"
        >
          <template #description>
            <div v-for="c in checks" :key="c.key" style="line-height: 22px">
              <component :is="c.passed ? CheckCircleOutlined : CloseCircleOutlined"
                :style="{ color: c.passed ? '#52c41a' : '#f5222d', marginRight: '6px' }" />
              <b>{{ c.label }}</b>：{{ c.message }}
            </div>
          </template>
        </a-alert>

        <a-alert
          v-if="allPassed && !materialReady"
          type="warning"
          show-icon
          style="margin-bottom: 16px"
          :message="`前置条件已满足，还需补齐 ${missingItems.length} 项必传验收材料后才能提交申请`"
        />

        <!-- 分级材料栏 -->
        <div v-for="g in grouped" :key="g.code" style="margin-bottom: 20px">
          <div class="level-title">
            <span>{{ g.name }}</span>
            <a-tag v-if="g.list.some((i: any) => i.locked)" color="default" style="margin-left: 8px">
              本项目不适用 · 已锁定
            </a-tag>
            <a-tag v-else color="blue">本层级需上传</a-tag>
            <a-progress
              v-if="!g.list.some((i: any) => i.locked)"
              :percent="Math.round((g.list.filter((i: any) => uploaded(i)).length / Math.max(1, g.list.length)) * 100)"
              size="small"
              style="width: 160px"
            />
          </div>
          <a-table size="small" row-key="id" :pagination="false" :data-source="g.list"
            :columns="[
              { title: '材料名称', dataIndex: 'materialName', width: 280 },
              { title: '是否必需', dataIndex: 'locked', width: 130 },
              { title: '状态', dataIndex: 'status', width: 110 },
              { title: '附件', key: 'file', width: 220 },
              { title: '操作', key: 'act', width: 150 },
            ]">
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'locked'">
                <a-tag :color="record.locked ? 'default' : 'blue'">{{ record.locked ? '锁定不可编辑' : '必需' }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'status'">
                <a-tag :color="uploaded(record) ? 'green' : 'orange'">{{ uploaded(record) ? '已上传' : '未上传' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'file'">
                <a v-if="record.fileUrl" href="#" @click.prevent="openMaterial(record)">
                  <FileDoneOutlined /> {{ materialFileName(record) || '查看附件' }}
                </a>
                <span v-else class="muted">暂无附件</span>
              </template>
              <template v-else-if="column.key === 'act'">
                <a-space :size="2">
                  <a-button type="link" size="small" :disabled="!canOperateMaterial(record)" @click="openUpload(record)">
                    {{ uploaded(record) ? '替换' : '上传' }}
                  </a-button>
                  <a-button v-if="record.fileUrl" type="link" size="small" @click="openMaterial(record)">下载</a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </div>
      </a-spin>
    </a-card>

    <a-modal
      :open="!!uploadRow"
      :title="uploadRow ? `上传${uploadRow.materialName}` : '上传验收材料'"
      :footer="null"
      :destroy-on-close="true"
      @cancel="uploadRow = null"
    >
      <a-upload-dragger :show-upload-list="false" :disabled="uploading" :before-upload="uploadFile">
        <p class="ant-upload-drag-icon"><CloudUploadOutlined /></p>
        <p class="ant-upload-text">{{ uploading ? '正在上传...' : '点击或拖拽文件到此处上传' }}</p>
        <p class="ant-upload-hint">支持 PDF、Word、Excel 或扫描件。</p>
      </a-upload-dragger>
    </a-modal>
    <a-modal
      v-model:open="auditOpen"
      :title="currentFlowNode ? `${currentFlowNode.title}处理` : '审核处理'"
      ok-text="通过"
      cancel-text="关闭"
      @ok="audit(true)"
    >
      <a-form layout="vertical">
        <a-form-item label="处理意见">
          <a-textarea v-model:value="auditOpinion" :rows="4" placeholder="请输入处理意见" />
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button @click="auditOpen = false">关闭</a-button>
        <a-button danger @click="audit(false)">退回</a-button>
        <a-button type="primary" @click="audit(true)">通过</a-button>
      </template>
    </a-modal>
    <AcceptFlowDialog v-model:open="flowOpen" :opts="flowOpts" :overview="overview" />
  </div>
</template>

<style scoped>
.accept-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}
.project-line {
  display: flex;
  gap: 8px;
  align-items: center;
  color: #8c8c8c;
  font-size: 13px;
  line-height: 22px;
}
.project-line b {
  color: #262626;
  font-weight: 500;
}
.flow-card {
  margin-bottom: 16px;
}
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
  margin-bottom: 14px;
}
.accept-flowchart {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 720px;
  padding: 4px 0 10px;
  overflow-x: auto;
}
.chart-node,
.chart-diamond {
  border: 1px solid #8db8d4;
  background: linear-gradient(180deg, #f8fdff 0%, #eaf8ff 100%);
  color: #1f2d3d;
  box-shadow: 0 2px 8px rgba(45, 114, 162, 0.08);
}
.chart-node {
  width: 230px;
  min-height: 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 4px;
  border-radius: 2px;
  padding: 8px 16px;
  text-align: center;
  font-weight: 600;
  line-height: 20px;
}
.chart-node.wide {
  width: 330px;
  min-height: 58px;
}
.chart-node.small {
  width: 142px;
  min-height: 40px;
  font-size: 13px;
}
.chart-node small {
  display: block;
  color: #5f6f7c;
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
}
.chart-diamond {
  width: 132px;
  height: 132px;
  transform: rotate(45deg);
  display: flex;
  align-items: center;
  justify-content: center;
}
.chart-diamond span {
  transform: rotate(-45deg);
  display: block;
  width: 108px;
  text-align: center;
  font-weight: 600;
  line-height: 20px;
}
.chart-line {
  width: 1px;
  height: 34px;
  background: #8a8f96;
  position: relative;
}
.chart-line::after {
  content: '';
  position: absolute;
  left: -4px;
  bottom: -1px;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 7px solid #8a8f96;
}
.chart-line.yes::before {
  content: '是';
  position: absolute;
  left: 10px;
  top: 9px;
  color: #595959;
  font-size: 12px;
}
.chart-row {
  position: relative;
  width: 520px;
  min-height: 132px;
  display: flex;
  justify-content: center;
  align-items: center;
}
.chart-side {
  position: absolute;
  left: 0;
  top: 46px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.chart-side::after {
  content: '';
  width: 58px;
  height: 1px;
  background: #8a8f96;
}
.chart-side.right {
  left: auto;
  right: 0;
  flex-direction: row-reverse;
}
.chart-side.right::after {
  width: 50px;
}
.branch-label {
  color: #595959;
  font-size: 12px;
  white-space: nowrap;
}
.chart-node.done,
.chart-diamond.done,
.chart-node.closed,
.chart-diamond.closed {
  border-color: #68c28f;
  background: #f2fff7;
}
.chart-node.current,
.chart-diamond.current {
  border-color: #1677ff;
  border-width: 2px;
  background: #e8f3ff;
}
.chart-node.return,
.chart-diamond.return {
  border-color: #ff7875;
  background: #fff1f0;
}
.chart-node.pending,
.chart-diamond.pending,
.chart-node.skip,
.chart-diamond.skip {
  border-color: #d9d9d9;
  background: #fafafa;
  color: #8c8c8c;
  box-shadow: none;
}
.level-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 8px;
}
.muted {
  color: #8c8c8c;
}
@media (max-width: 1100px) {
  .accept-head {
    flex-direction: column;
  }
}
</style>
