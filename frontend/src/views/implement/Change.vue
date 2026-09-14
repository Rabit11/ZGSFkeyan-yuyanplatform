<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { changeApi, projectApi } from '@/api/modules'
import { useDictStore } from '@/stores/dict'
import { fmtDate } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { canAuditByIdentity } from '@/utils/flowActor'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'

const dictStore = useDictStore()
const user = useUserStore()
const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ page: 1, size: 10, changeType: undefined, status: undefined })
const projects = ref<any[]>([])

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
const selectedProject = computed(() => projects.value.find((p) => p.id === form.projectId) || projects.value[0] || null)
const { can, guard } = useWorkDuty('change', selectedProject)

const STATUS_TEXT: Record<string, string> = { DRAFT: '草稿', APPROVING: '审批中', APPROVED: '已通过', REJECTED: '已驳回' }

async function load() {
  loading.value = true
  try {
    const res = await changeApi.page(query)
    rows.value = (res.data as any)?.records || []
    total.value = (res.data as any)?.total || 0
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await dictStore.load('CHANGE_CATEGORY')
  const p = await projectApi.page({ page: 1, size: 200 })
  projects.value = (p.data as any)?.records || []
  load()
})

function onCreate() {
  viewing.value = null
  formMode.value = 'create'
  Object.assign(form, {
    projectId: undefined,
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
  await changeApi.create(form)
  message.success(LEGAL.includes(form.category) ? '变更申请已创建（重大变更，将强制联动法务部门审核）' : '变更申请已创建')
  open.value = false
  load()
}
async function submit(row: any) {
  if (!guard('submit')) return
  await changeApi.submit(row.id)
  message.success('已提交审批')
  load()
}
async function audit(row: any) {
  if (!canAuditByIdentity(row.flowNode, user.identityCode)) {
    message.warning(`仅当前节点办理人可审批（${row.flowNode || '待指定'}）`)
    return
  }
  await changeApi.audit(row.id, { pass: true, opinion: '同意变更' })
  message.success('审批通过')
  load()
}
async function remove(row: any) {
  Modal.confirm({ title: '确认删除？', content: '删除后不可恢复。', onOk: async () => { await changeApi.remove(row.id); load() } })
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
          <a-select v-model:value="query.changeType" placeholder="全部类型" style="width: 140px" allow-clear>
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="PROJECT">项目变更</a-select-option>
            <a-select-option value="DATA">数据变更</a-select-option>
          </a-select>
          <a-select v-model:value="query.status" placeholder="全部状态" style="width: 130px" allow-clear>
            <a-select-option value="">全部</a-select-option>
            <a-select-option v-for="(v, k) in STATUS_TEXT" :key="k" :value="k">{{ v }}</a-select-option>
          </a-select>
          <a-button type="primary" @click="load"><SearchOutlined />查询</a-button>
        </div>
        <a-button type="primary" :disabled="!can.fill" @click="onCreate"><PlusOutlined />发起变更</a-button>
      </div>

      <a-table :loading="loading" row-key="id" :data-source="rows" :scroll="{ x: 1500 }"
        :pagination="{ current: query.page, pageSize: query.size, total, showTotal: (t: number) => `共 ${t} 条` }"
        @change="(p: any) => { query.page = p.current; load() }"
        :columns="[
          { title: '变更单号', dataIndex: 'changeNo', width: 130 },
          { title: '项目', dataIndex: 'projectName', width: 280 },
          { title: '类型', dataIndex: 'changeType', width: 100 },
          { title: '变更事项', dataIndex: 'category', width: 130 },
          { title: '标题', dataIndex: 'title', width: 220 },
          { title: '调整前', dataIndex: 'beforeValue', width: 120 },
          { title: '调整后', dataIndex: 'afterValue', width: 120 },
          { title: '法务', dataIndex: 'legalReview', width: 80 },
          { title: '状态', dataIndex: 'status', width: 100 },
          { title: '当前节点', dataIndex: 'flowNode', width: 180 },
          { title: '操作', key: 'act', width: 170, fixed: 'right' },
        ]">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'changeType'">
            <a-tag :color="record.changeType === 'PROJECT' ? 'blue' : 'cyan'">{{ record.changeType === 'PROJECT' ? '项目变更' : '数据变更' }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'category'">
            {{ dictStore.label('CHANGE_CATEGORY', record.category) }}
          </template>
          <template v-else-if="column.dataIndex === 'legalReview'">
            <a-tag v-if="record.legalReview" color="red">需审核</a-tag><span v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 'APPROVED' ? 'green' : record.status === 'REJECTED' ? 'red' : record.status === 'APPROVING' ? 'processing' : 'default'">
              {{ STATUS_TEXT[record.status] }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'act'">
            <a-space :size="2">
              <a-button type="link" size="small" @click="onView(record)">查看</a-button>
              <a-button type="link" size="small" :disabled="record.status !== 'DRAFT'" @click="submit(record)">提交</a-button>
              <a-divider type="vertical" />
              <a-dropdown>
                <a-button type="link" size="small">更多</a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item key="audit" :disabled="record.status !== 'APPROVING' || !canAuditByIdentity(record.flowNode, user.identityCode)" @click="audit(record)">审批通过</a-menu-item>
                    <a-menu-item key="del" @click="remove(record)">删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="open" :title="formMode === 'view' ? '查看变更申请' : '发起变更申请'" :width="'40%'">
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
  </div>
</template>
