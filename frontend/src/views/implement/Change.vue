<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { changeApi, projectApi } from '@/api/modules'
import { isSilentAuthError } from '@/api/request'
import { useDictStore } from '@/stores/dict'
import { fmtDate } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { canAuditByIdentity } from '@/utils/flowActor'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'

const route = useRoute()
const dictStore = useDictStore()
const user = useUserStore()
const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ page: 1, size: 10, changeType: undefined, status: undefined, projectId: undefined })
const projects = ref<any[]>([])
const highlightId = ref<number>()

const open = ref(false)
const formMode = ref<'create' | 'view'>('create')
const viewing = ref<any>(null)
const form = reactive<any>({
  projectId: undefined,
  changeType: 'PROJECT',
  category: 'MILESTONE_DELAY',
  title: '',
  reason: '',
  beforeValue: '',
  afterValue: '',
})
const selectedProject = computed(() => projects.value.find((p) => p.id === (form.projectId || query.projectId)) || projects.value[0] || null)
const { can, guard } = useWorkDuty('change', selectedProject)

const auditOpen = ref(false)
const auditing = ref(false)
const auditRow = ref<any>(null)
const auditPass = ref(true)
const auditOpinion = ref('')

const STATUS_TEXT: Record<string, string> = { DRAFT: '草稿', APPROVING: '审批中', APPROVED: '已通过', REJECTED: '已驳回' }

function canMutate(row: any) {
  return row.status === 'DRAFT' || row.status === 'REJECTED'
}

function rowClassName(record: any) {
  return Number(record.id) === Number(highlightId.value) ? 'row-highlight' : ''
}

async function load() {
  loading.value = true
  try {
    const params: any = { ...query }
    if (!params.projectId) delete params.projectId
    const res = await changeApi.page(params)
    rows.value = (res.data as any)?.records || []
    total.value = (res.data as any)?.total || 0
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '加载变更列表失败')
  } finally {
    loading.value = false
  }
}

/** 从里程碑延期跳转过来：定位并高亮该变更单 */
async function focusFromRoute() {
  const cid = Number(route.query.changeId)
  const pid = Number(route.query.projectId)
  if (pid) query.projectId = pid
  if (!cid) {
    if (pid) await load()
    return
  }
  query.page = 1
  query.status = undefined
  await load()
  if (!rows.value.some((r) => Number(r.id) === cid)) {
    // 不在首页时放宽分页再找一次
    query.size = 100
    await load()
  }
  const hit = rows.value.find((r) => Number(r.id) === cid)
  if (!hit) {
    message.warning('未找到对应的延期变更单，请在列表中查找')
    return
  }
  highlightId.value = cid
  message.info('延期变更单已生成，请提交审批')
  await nextTick()
  document.querySelector(`[data-row-key="${cid}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

onMounted(async () => {
  await dictStore.load('CHANGE_CATEGORY')
  const p = await projectApi.page({ page: 1, size: 200 })
  projects.value = (p.data as any)?.records || []
  if (route.query.changeId || route.query.projectId) await focusFromRoute()
  else await load()
})
watch(() => [route.query.changeId, route.query.projectId], () => {
  if (route.query.changeId) focusFromRoute()
})

function onCreate() {
  viewing.value = null
  formMode.value = 'create'
  Object.assign(form, {
    projectId: query.projectId || undefined,
    changeType: 'PROJECT',
    category: 'MILESTONE_DELAY',
    title: '',
    reason: '',
    beforeValue: '',
    afterValue: '',
  })
  open.value = true
}

function onView(record: any) {
  viewing.value = record
  formMode.value = 'view'
  Object.assign(form, {
    projectId: record.projectId,
    changeType: record.changeType,
    category: record.category,
    title: record.title,
    reason: record.reason,
    beforeValue: record.beforeValue,
    afterValue: record.afterValue,
  })
  open.value = true
}

const LEGAL = ['OUTSOURCE', 'PERIOD', 'FUND']

async function save() {
  if (!guard('fill')) return
  if (!form.projectId || !String(form.title || '').trim()) {
    message.warning('请选择关联项目并填写变更标题')
    return
  }
  try {
    await changeApi.create(form)
    message.success(LEGAL.includes(form.category) ? '变更申请已创建（重大变更，将强制联动法务部门审核）' : '变更申请已创建')
    open.value = false
    load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '创建变更失败')
  }
}
async function submit(row: any) {
  if (!guard('submit')) return
  try {
    await changeApi.submit(row.id)
    message.success('已提交审批')
    if (Number(row.id) === Number(highlightId.value)) highlightId.value = undefined
    load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '提交失败')
  }
}
function openAudit(row: any) {
  if (!canAuditByIdentity(row.flowNode, user.identityCode)) {
    message.warning(`仅当前节点办理人可审批（${row.flowNode || '待指定'}）`)
    return
  }
  auditRow.value = row
  auditPass.value = true
  auditOpinion.value = '同意变更'
  auditOpen.value = true
}
async function submitAudit() {
  const row = auditRow.value
  if (!row) return
  if (!auditPass.value && !auditOpinion.value.trim()) {
    message.warning('退回请填写审批意见')
    return
  }
  auditing.value = true
  try {
    await changeApi.audit(row.id, { pass: auditPass.value, opinion: auditOpinion.value.trim() })
    message.success(auditPass.value ? '本节点审批通过' : '已退回申请人')
    auditOpen.value = false
    load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '审批失败')
  } finally {
    auditing.value = false
  }
}
async function remove(row: any) {
  try {
    await changeApi.remove(row.id)
    message.success('已删除')
    load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '删除失败')
  }
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">项目变更</h2>
    <div class="page-desc">
      本模块为实施阶段<b>唯一调整渠道</b>：里程碑延期、经费、外协方、付款节点、核心指标等所有内容修改均禁止直接后台改动，必须线上发起申请。
      分<b>项目变更</b>（重大调整，二级单位主管部门→总部逐级审批，重大变更强制联动法务审核）与<b>数据变更</b>（小幅纠错，二级单位内部审批后报总部科技主管确认）两类入口。
    </div>
    <WorkDutyBar code="change" :project="selectedProject" />

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <div class="filter-bar">
          <a-select
            v-model:value="query.projectId"
            placeholder="全部项目"
            style="width: 260px"
            allow-clear
            show-search
            :filter-option="(i: string, o: any) => String(o.label).includes(i)"
          >
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name">{{ p.name }}</a-select-option>
          </a-select>
          <a-select v-model:value="query.changeType" placeholder="全部类型" style="width: 140px" allow-clear>
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="PROJECT">项目变更</a-select-option>
            <a-select-option value="DATA">数据变更</a-select-option>
          </a-select>
          <a-select v-model:value="query.status" placeholder="全部状态" style="width: 130px" allow-clear>
            <a-select-option value="">全部</a-select-option>
            <a-select-option v-for="(v, k) in STATUS_TEXT" :key="k" :value="k">{{ v }}</a-select-option>
          </a-select>
          <a-button type="primary" @click="query.page = 1; load()"><SearchOutlined />查询</a-button>
        </div>
        <a-button type="primary" :disabled="!can.fill" @click="onCreate"><PlusOutlined />发起变更</a-button>
      </div>

      <a-table :loading="loading" row-key="id" :data-source="rows" :scroll="{ x: 1900 }"
        :row-class-name="rowClassName"
        :pagination="{ current: query.page, pageSize: query.size, total, showTotal: (t: number) => `共 ${t} 条` }"
        @change="(p: any) => { query.page = p.current; query.size = p.pageSize; load() }"
        :columns="[
          { title: '变更单号', dataIndex: 'changeNo', width: 130 },
          { title: '项目', dataIndex: 'projectName', width: 260 },
          { title: '类型', dataIndex: 'changeType', width: 100 },
          { title: '变更事项', dataIndex: 'category', width: 130 },
          { title: '标题', dataIndex: 'title', width: 220 },
          { title: '里程碑', dataIndex: 'milestoneId', width: 90 },
          { title: '调整前', dataIndex: 'beforeValue', width: 120 },
          { title: '调整后', dataIndex: 'afterValue', width: 120 },
          { title: '延期至', dataIndex: 'newPlanDate', width: 120 },
          { title: '法务', dataIndex: 'legalReview', width: 80 },
          { title: '状态', dataIndex: 'status', width: 100 },
          { title: '当前节点', dataIndex: 'flowNode', width: 180 },
          { title: '操作', key: 'act', width: 190, fixed: 'right' },
        ]">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'changeType'">
            <a-tag :color="record.changeType === 'PROJECT' ? 'blue' : 'cyan'">{{ record.changeType === 'PROJECT' ? '项目变更' : '数据变更' }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'category'">
            {{ dictStore.label('CHANGE_CATEGORY', record.category) }}
          </template>
          <template v-else-if="column.dataIndex === 'milestoneId'">
            {{ record.milestoneId ? `#${record.milestoneId}` : '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'newPlanDate'">
            {{ record.newPlanDate ? fmtDate(record.newPlanDate) : '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'legalReview'">
            <a-tag v-if="record.legalReview" color="red">需审核</a-tag><span v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 'APPROVED' ? 'green' : record.status === 'REJECTED' ? 'red' : record.status === 'APPROVING' ? 'processing' : 'default'">
              {{ STATUS_TEXT[record.status] || record.status }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'flowNode'">
            {{ record.flowNode || (record.status === 'APPROVED' ? '已归档' : '-') }}
          </template>
          <template v-else-if="column.key === 'act'">
            <a-space :size="2">
              <a-button type="link" size="small" @click="onView(record)">查看</a-button>
              <a-button type="link" size="small" :disabled="!canMutate(record)" @click="submit(record)">提交</a-button>
              <a-button
                type="link"
                size="small"
                :disabled="record.status !== 'APPROVING' || !canAuditByIdentity(record.flowNode, user.identityCode)"
                @click="openAudit(record)"
              >审批</a-button>
              <a-popconfirm v-if="canMutate(record)" title="确认删除该变更申请？删除后不可恢复。" ok-text="删除" cancel-text="取消" @confirm="remove(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="open" :title="formMode === 'view' ? '查看变更申请' : '发起变更申请'" :width="'40%'">
      <a-descriptions v-if="formMode === 'view' && viewing" :column="2" size="small" bordered style="margin-bottom: 16px">
        <a-descriptions-item label="变更单号">{{ viewing.changeNo || '-' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="viewing.status === 'APPROVED' ? 'green' : viewing.status === 'REJECTED' ? 'red' : viewing.status === 'APPROVING' ? 'processing' : 'default'">
            {{ STATUS_TEXT[viewing.status] || viewing.status }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="当前节点">{{ viewing.flowNode || '-' }}</a-descriptions-item>
        <a-descriptions-item label="申请人">{{ viewing.applicant || '-' }}</a-descriptions-item>
        <a-descriptions-item label="关联里程碑">{{ viewing.milestoneId ? `#${viewing.milestoneId}` : '-' }}</a-descriptions-item>
        <a-descriptions-item label="延期至">{{ viewing.newPlanDate ? fmtDate(viewing.newPlanDate) : '-' }}</a-descriptions-item>
        <a-descriptions-item label="申请日期" :span="2">{{ fmtDate(viewing.createdAt) }}</a-descriptions-item>
      </a-descriptions>
      <a-form layout="vertical" :disabled="formMode === 'view'">
        <a-form-item label="变更类型" required>
          <a-radio-group v-model:value="form.changeType">
            <a-radio value="PROJECT">项目变更（重大调整）</a-radio>
            <a-radio value="DATA">数据变更（小幅纠错）</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="关联项目" required>
          <a-select v-model:value="form.projectId" show-search placeholder="选择项目"
            :filter-option="(i: string, o: any) => String(o.label).includes(i)">
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name">{{ p.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="变更事项" required>
          <a-select v-model:value="form.category">
            <a-select-option v-for="o in dictStore.options('CHANGE_CATEGORY')" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="变更标题" required><a-input v-model:value="form.title" /></a-form-item>
        <a-form-item label="调整前"><a-input v-model:value="form.beforeValue" /></a-form-item>
        <a-form-item label="调整后"><a-input v-model:value="form.afterValue" /></a-form-item>
        <a-form-item label="变更缘由"><a-textarea v-model:value="form.reason" :rows="3" /></a-form-item>
        <a-alert v-if="LEGAL.includes(form.category)" type="warning" show-icon
          message="该变更属于重大变更，提交后将强制联动法务部门审核，严控合规风险。" />
      </a-form>
      <div v-if="formMode === 'view' && (viewing?.auditTrail || []).length" class="trail">
        <div class="trail__title">审批记录</div>
        <div v-for="(t, i) in viewing.auditTrail" :key="i" class="trail__row">
          <a-tag :color="t.pass ? 'success' : 'error'" style="margin: 0">{{ t.pass ? '通过' : '退回' }}</a-tag>
          <span class="trail__node">{{ t.nodeName || t.node }}</span>
          <span>{{ t.actor }}</span>
          <span class="trail__time">{{ fmtDate(t.time, 'YYYY-MM-DD HH:mm') }}</span>
          <span v-if="t.opinion" class="trail__time">{{ t.opinion }}</span>
        </div>
      </div>
      <template #footer>
        <div style="text-align: right">
          <a-button v-if="formMode === 'view'" type="primary" @click="open = false">关闭</a-button>
          <template v-else>
            <a-button style="margin-right: 8px" @click="open = false">取消</a-button>
            <a-button type="primary" @click="save">提交申请</a-button>
          </template>
        </div>
      </template>
    </a-drawer>

    <a-modal v-model:open="auditOpen" title="变更审批" :confirm-loading="auditing" @ok="submitAudit">
      <a-alert
        v-if="auditRow"
        type="info"
        show-icon
        style="margin-bottom: 12px"
        :message="`${auditRow.changeNo || ''} ${auditRow.title || ''}`"
        :description="`当前节点：${auditRow.flowNode || '-'}；通过后流转下一节点，最后一个节点通过才生效。`"
      />
      <a-form layout="vertical">
        <a-form-item label="审批结论" required>
          <a-radio-group v-model:value="auditPass">
            <a-radio :value="true">通过</a-radio>
            <a-radio :value="false">退回</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="审批意见" :required="!auditPass">
          <a-textarea v-model:value="auditOpinion" :rows="3" :placeholder="auditPass ? '可填写通过意见' : '请说明退回原因'" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.trail { margin-top: 16px; border-top: 1px dashed #e8e8e8; padding-top: 12px; }
.trail__title { font-weight: 600; margin-bottom: 6px; color: #262626; }
.trail__row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; font-size: 13px; line-height: 1.8; }
.trail__node { color: #0064ef; }
.trail__time { color: #8c8c8c; }
:deep(.row-highlight) > td { background: #fffbe6 !important; }
</style>
