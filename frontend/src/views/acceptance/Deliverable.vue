<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { deliverableApi, projectApi } from '@/api/modules'
import { useDictStore } from '@/stores/dict'
import { dueText, fmtDate } from '@/utils/format'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'

const dictStore = useDictStore()
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const projects = ref<any[]>([])
const query = reactive({
  page: 1,
  size: 8,
  keyword: '',
  status: undefined as string | undefined,
  deliverType: undefined as string | undefined,
  ownerOrg: undefined as string | undefined,
})

const open = ref(false)
const editing = ref<any>(null)
const form = reactive<any>({ name: '', deliverType: 'PATENT', dueDate: '', ownerOrgs: ['公司'], projectId: undefined })
const selectedProject = computed(() => projects.value.find((p) => p.id === form.projectId) || projects.value[0] || null)
const { can, guard } = useWorkDuty('deliverable', selectedProject)
const OWNERS = ['公司', '各单位', '参研单位', '外协单位']
const STATUS_TEXT: Record<string, string> = { PENDING: '未交付', DELIVERED: '已交付', OVERDUE: '已逾期' }

const bindOpen = ref(false)
const bindNo = ref('')
const bindRow = ref<any>(null)

async function load() {
  loading.value = true
  try {
    try {
      const res = await deliverableApi.page({ ...query, size: 200, page: 1 })
      const all = ((res.data as any)?.records || []) as any[]
      total.value = (res.data as any)?.total || all.length
      rows.value = all
      return
    } catch {
      /* 旧后端无全局分页接口时，按项目汇总 */
    }
    const plist = projects.value.length
      ? projects.value
      : ((await projectApi.page({ page: 1, size: 200 })).data as any)?.records || []
    projects.value = plist
    const chunks = await Promise.all(plist.slice(0, 80).map((p: any) => deliverableApi.list(p.id).then((r) => {
      const list = (r.data as any[]) || []
      return list.map((x) => ({ ...x, projectName: p.name, projectNo: p.projectNo }))
    }).catch(() => [])))
    let all = chunks.flat()
    const kw = query.keyword.trim()
    if (kw) {
      all = all.filter((x) => [x.name, x.achievementNo, x.projectNo, x.projectName].some((v) => String(v || '').includes(kw)))
    }
    if (query.status) all = all.filter((x) => x.status === query.status)
    if (query.deliverType) all = all.filter((x) => x.deliverType === query.deliverType)
    if (query.ownerOrg) all = all.filter((x) => String(x.ownerOrgs || '').includes(query.ownerOrg!))
    rows.value = all
    total.value = all.length
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await dictStore.load('DELIVERABLE_TYPE')
  const p = await projectApi.page({ page: 1, size: 200 })
  projects.value = (p.data as any)?.records || []
  load()
})

const stats = computed(() => {
  const list = rows.value
  return {
    total: list.length,
    delivered: list.filter((x) => x.status === 'DELIVERED').length,
    pending: list.filter((x) => x.status === 'PENDING').length,
    yellow: list.filter((x) => x.colorStatus === 'YELLOW' && x.status !== 'DELIVERED').length,
    overdue: list.filter((x) => x.status === 'OVERDUE' || x.colorStatus === 'RED').length,
  }
})

const typeBars = computed(() => {
  const map = new Map<string, number>()
  for (const r of rows.value) {
    const key = r.deliverType || 'OTHER'
    map.set(key, (map.get(key) || 0) + 1)
  }
  const max = Math.max(1, ...map.values())
  return [...map.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([code, count]) => ({
      code,
      name: dictStore.label('DELIVERABLE_TYPE', code) || code,
      count,
      pct: Math.round((count / max) * 100),
    }))
})

const pageRows = computed(() => {
  const start = (query.page - 1) * query.size
  return rows.value.slice(start, start + query.size)
})

function reset() {
  query.keyword = ''
  query.status = undefined
  query.deliverType = undefined
  query.ownerOrg = undefined
  query.page = 1
  load()
}

function dvNo(row: any) {
  return row.deliverNo || `JF-${String(row.id).padStart(3, '0')}`
}

function onCreate() {
  editing.value = null
  Object.assign(form, {
    name: '',
    deliverType: 'PATENT',
    dueDate: '',
    ownerOrgs: ['公司'],
    projectId: projects.value[0]?.id,
  })
  open.value = true
}
function onEdit(row: any) {
  editing.value = row
  Object.assign(form, row, { ownerOrgs: (row.ownerOrgs || '').split(',').filter(Boolean) })
  open.value = true
}
async function save() {
  if (!guard('fill')) return
  const payload = { ...form, ownerOrgs: (form.ownerOrgs || []).join(',') }
  if (editing.value) await deliverableApi.update(editing.value.id, payload)
  else await deliverableApi.create(payload)
  message.success('保存成功')
  open.value = false
  load()
}
async function remove(row: any) {
  Modal.confirm({ title: '确认删除？', onOk: async () => { await deliverableApi.remove(row.id); load() } })
}
function bind(row: any) {
  bindRow.value = row
  bindNo.value = row.achievementNo || ''
  bindOpen.value = true
}
async function doBind() {
  await deliverableApi.bind(bindRow.value.id, { achievementNo: bindNo.value })
  message.success('绑定成功')
  bindOpen.value = false
  load()
}

function statusClass(row: any) {
  if (row.status === 'DELIVERED') return 'ok'
  if (row.status === 'OVERDUE' || row.colorStatus === 'RED') return 'bad'
  if (row.colorStatus === 'YELLOW') return 'warn'
  return 'idle'
}
</script>

<template>
  <div class="page-container ledger-page">
    <h2 class="page-title">交付物独立台账</h2>
    <div class="page-desc">
      交付物为最小管理单元，与成果包、项目台账和可视化看板双向绑定
    </div>
    <WorkDutyBar code="deliverable" :project="selectedProject" />

    <div class="kpi-row">
      <div class="kpi">
        <div class="label">交付物总数</div>
        <div class="value brand">{{ stats.total }} <span>项</span></div>
      </div>
      <div class="kpi">
        <div class="label">已交付</div>
        <div class="value">{{ stats.delivered }} <span>项</span></div>
        <div class="sub">验收前置条件</div>
      </div>
      <div class="kpi">
        <div class="label">待交付</div>
        <div class="value">{{ stats.pending }} <span>项</span></div>
        <div class="sub">任务书约定未完成</div>
      </div>
      <div class="kpi">
        <div class="label">临期预警</div>
        <div class="value" :class="stats.yellow ? 'warn' : 'ok'">{{ stats.yellow }} <span>项</span></div>
      </div>
      <div class="kpi">
        <div class="label">逾期交付物</div>
        <div class="value" :class="stats.overdue ? 'bad' : 'ok'">{{ stats.overdue }} <span>项</span></div>
      </div>
    </div>

    <div class="filter-row">
      <a-input
        v-model:value="query.keyword"
        allow-clear
        placeholder="交付物 / 项目 / 编号"
        style="width: 220px"
        @pressEnter="load"
      />
      <a-select v-model:value="query.status" allow-clear placeholder="全部状态" style="width: 140px" @change="load">
        <a-select-option v-for="(v, k) in STATUS_TEXT" :key="k" :value="k">{{ v }}</a-select-option>
      </a-select>
      <a-select v-model:value="query.deliverType" allow-clear placeholder="全部类型" style="width: 150px" @change="load">
        <a-select-option v-for="o in dictStore.options('DELIVERABLE_TYPE')" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
      </a-select>
      <a-select v-model:value="query.ownerOrg" allow-clear placeholder="全部单位" style="width: 140px" @change="load">
        <a-select-option v-for="o in OWNERS" :key="o" :value="o">{{ o }}</a-select-option>
      </a-select>
      <a-button @click="reset">重置</a-button>
      <a-button type="primary" style="margin-left: auto" :disabled="!can.fill" @click="onCreate"><PlusOutlined />新增交付物</a-button>
    </div>

    <div class="split">
      <div class="panel">
        <div class="panel-title">交付物类型分布</div>
        <div v-if="!typeBars.length" class="empty">暂无数据</div>
        <div v-else class="bars">
          <div v-for="b in typeBars" :key="b.code" class="bar-row">
            <div class="bar-name" :title="b.name">{{ b.name }}</div>
            <div class="bar-track"><div class="bar-fill" :style="{ width: b.pct + '%' }" /></div>
            <div class="bar-n">{{ b.count }}</div>
          </div>
        </div>
      </div>
      <div class="panel list-panel">
        <div class="panel-title">交付物明细</div>
        <a-table
          :loading="loading"
          row-key="id"
          size="middle"
          :pagination="{ current: query.page, pageSize: query.size, total: rows.length, showSizeChanger: false }"
          :data-source="pageRows"
          :columns="[
            { title: '交付物', key: 'dv', width: 220 },
            { title: '关联项目', key: 'proj', width: 180 },
            { title: '类型', dataIndex: 'deliverType', width: 100 },
            { title: '状态', key: 'st', width: 90 },
            { title: '应交付 / 权属', key: 'meta', width: 160 },
          ]"
          @change="(p: any) => { query.page = p.current }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'dv'">
              <div class="cell-title">{{ record.name }}</div>
              <a class="cell-id" @click="onEdit(record)">{{ dvNo(record) }}</a>
            </template>
            <template v-else-if="column.key === 'proj'">
              <div class="cell-title">{{ record.projectName || '—' }}</div>
              <div class="cell-sub">{{ record.projectNo || '—' }}</div>
            </template>
            <template v-else-if="column.dataIndex === 'deliverType'">
              {{ dictStore.label('DELIVERABLE_TYPE', record.deliverType) }}
            </template>
            <template v-else-if="column.key === 'st'">
              <span class="dot" :class="statusClass(record)" />
              {{ STATUS_TEXT[record.status] || record.status }}
            </template>
            <template v-else-if="column.key === 'meta'">
              <div>{{ fmtDate(record.dueDate) || '—' }}</div>
              <div class="cell-sub">{{ record.status === 'DELIVERED' ? '已交付' : dueText(record.dueDate) }} · {{ record.ownerOrgs || '—' }}</div>
              <a-space :size="4">
                <a @click="onEdit(record)">编辑</a>
                <a @click="bind(record)">绑定成果</a>
                <a @click="remove(record)">删除</a>
              </a-space>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <a-modal v-model:open="open" :title="editing ? '编辑交付物' : '新增交付物'" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="所属项目" required>
          <a-select v-model:value="form.projectId" show-search :filter-option="(i: string, o: any) => String(o.label).includes(i)">
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name">{{ p.projectNo }} · {{ p.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="交付物名称" required><a-input v-model:value="form.name" placeholder="与任务书/合同约定的成果全称一致" /></a-form-item>
        <a-form-item label="交付物类型">
          <a-select v-model:value="form.deliverType">
            <a-select-option v-for="o in dictStore.options('DELIVERABLE_TYPE')" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="应交付时间"><a-date-picker v-model:value="form.dueDate" style="width: 100%" value-format="YYYY-MM-DD" /></a-form-item>
        <a-form-item label="交付物权属">
          <a-checkbox-group v-model:value="form.ownerOrgs">
            <a-checkbox v-for="o in OWNERS" :key="o" :value="o">{{ o }}</a-checkbox>
          </a-checkbox-group>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="bindOpen" title="绑定成果编号" @ok="doBind">
      <p style="color: #8a919f; font-size: 13px">
        交付物：<b>{{ bindRow?.name }}</b>。支持多项关联交付物绑定同一成果编号，实现打包转化。
      </p>
      <a-input v-model:value="bindNo" placeholder="请输入成果编号（如 CG20263000）" />
    </a-modal>
  </div>
</template>

<style scoped>
.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.kpi {
  background: var(--zgsf-card);
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius-card);
  padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.kpi .label { color: var(--zgsf-text-secondary); font-size: 13px; }
.kpi .value { font-size: 28px; font-weight: 600; color: var(--zgsf-text); line-height: 1.3; }
.kpi .value span { font-size: 13px; font-weight: 400; color: var(--zgsf-text-secondary); }
.kpi .value.brand { color: var(--zgsf-brand); }
.kpi .value.ok { color: var(--c-green); }
.kpi .value.warn { color: var(--c-yellow); }
.kpi .value.bad { color: var(--c-red); }
.kpi .sub { font-size: 12px; color: var(--zgsf-text-secondary); margin-top: 4px; }
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  background: var(--zgsf-card);
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius-card);
  padding: 12px 16px;
  margin-bottom: 16px;
}
.split { display: grid; grid-template-columns: 360px minmax(0, 1fr); gap: 16px; }
.panel {
  background: var(--zgsf-card);
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius-card);
  padding: 16px 20px;
  min-height: 360px;
}
.panel-title { font-weight: 600; margin-bottom: 16px; color: var(--zgsf-text); }
.bars { display: flex; flex-direction: column; gap: 14px; }
.bar-row { display: grid; grid-template-columns: 88px 1fr 28px; gap: 8px; align-items: center; }
.bar-name { font-size: 13px; color: var(--zgsf-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-track { height: 10px; background: var(--zgsf-bg); border-radius: var(--zgsf-radius-card); overflow: hidden; }
.bar-fill { height: 100%; background: var(--zgsf-brand); border-radius: var(--zgsf-radius-card); }
.bar-n { text-align: right; font-size: 13px; color: var(--zgsf-text); }
.cell-title { color: var(--zgsf-text); }
.cell-id { color: var(--zgsf-brand); font-size: 12px; }
.cell-sub { color: var(--zgsf-text-secondary); font-size: 12px; }
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  background: var(--zgsf-brand);
}
.dot.ok { background: var(--c-green); }
.dot.warn { background: var(--c-yellow); }
.dot.bad { background: var(--c-red); }
.dot.idle { background: var(--zgsf-brand); }
.empty { color: var(--zgsf-text-secondary); }
@media (max-width: 1100px) {
  .kpi-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .split { grid-template-columns: 1fr; }
}
@media (max-width: 600px) {
  .kpi-row {
    grid-template-columns: 1fr;
  }
  .panel,
  .kpi {
    padding: 12px;
  }
}
</style>
