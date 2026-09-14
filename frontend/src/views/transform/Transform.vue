<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { projectApi, deliverableApi, transformApi } from '@/api/modules'
import { isSilentAuthError } from '@/api/request'
import { useDictStore } from '@/stores/dict'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'

const dictStore = useDictStore()
const loading = ref(false)
const rows = ref<any[]>([])
const query = reactive<any>({ page: 1, size: 8, status: undefined, transformWay: undefined, keyword: '', dutyOrg: undefined })
const projects = ref<any[]>([])

const open = ref(false)
const form = reactive<any>({
  name: '', projectId: undefined, intro: '', transformWay: 'MODEL',
  transformForm: 'INSTALLED', planDate: '', dutyOrg: '',
})
const selectedProject = computed(() => projects.value.find((p) => p.id === form.projectId) || projects.value[0] || null)
const { can, guard } = useWorkDuty('transform', selectedProject)

const bindOpen = ref(false)
const cur = ref<any>(null)
const pool = ref<any[]>([])
const bindIds = ref<number[]>([])

const STATUS_TEXT: Record<string, string> = { NOT_STARTED: '未启动', NEGOTIATING: '洽谈中', SIGNED: '已签协议', DONE: '已完成' }
const WAY_TEXT: Record<string, string> = { MODEL: '向型号转化', MARKET: '向市场转化' }
const FORM_TEXT: Record<string, string> = {
  INSTALLED: '装机', UNINSTALLED: '未装机', TRANSFER: '转让', LICENSE: '许可', JOINT: '联合实施', INVEST: '作价投资', OTHER: '其他',
}

const MODEL_FORMS = [
  { value: 'INSTALLED', label: '装机' },
  { value: 'UNINSTALLED', label: '未装机' },
]
const MARKET_FORMS = [
  { value: 'TRANSFER', label: '转让' },
  { value: 'LICENSE', label: '许可' },
  { value: 'JOINT', label: '联合实施' },
  { value: 'INVEST', label: '作价投资' },
  { value: 'OTHER', label: '其他' },
]
const formOptions = computed(() => (form.transformWay === 'MODEL' ? MODEL_FORMS : MARKET_FORMS))

async function load() {
  loading.value = true
  try {
    const res = await transformApi.page({ ...query, page: 1, size: 200 })
    rows.value = (res.data as any)?.records || []
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await dictStore.load('TRANSFORM_WAY')
  await dictStore.load('TRANSFORM_FORM')
  const p = await projectApi.page({ page: 1, size: 200 })
  projects.value = (p.data as any)?.records || []
  load()
})

const stats = computed(() => ({
  total: rows.value.length,
  model: rows.value.filter((x) => x.transformWay === 'MODEL').length,
  market: rows.value.filter((x) => x.transformWay === 'MARKET').length,
  done: rows.value.filter((x) => x.status === 'DONE').length,
  overdue: rows.value.filter((x) => x.colorStatus === 'RED' && x.status !== 'DONE').length,
}))

const modelBars = computed(() => {
  const map = new Map<string, number>()
  for (const r of rows.value.filter((x) => x.transformWay === 'MODEL')) {
    const name = String(r.intro || r.transformForm || '技术储备').slice(0, 8)
    const key = FORM_TEXT[r.transformForm] ? `${r.projectNo || name}` : name
    const label = (r.intro && /C\d|C[A-Z]/.test(r.intro) ? r.intro.match(/C[A-Z0-9]+/)?.[0] : null) || r.projectNo || FORM_TEXT[r.transformForm] || '型号'
    map.set(label, (map.get(label) || 0) + 1)
  }
  const max = Math.max(1, ...map.values())
  return [...map.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8)
    .map(([name, count]) => ({ name, count, pct: Math.round((count / max) * 100) }))
})

const orgs = computed(() => [...new Set(rows.value.map((x) => x.dutyOrg).filter(Boolean))])
const pageRows = computed(() => {
  const start = (query.page - 1) * query.size
  return rows.value.slice(start, start + query.size)
})

function reset() {
  query.keyword = ''
  query.status = undefined
  query.transformWay = undefined
  query.dutyOrg = undefined
  query.page = 1
  load()
}

function pairText(row: any) {
  if (row.transformWay === 'MODEL') return `${FORM_TEXT[row.transformForm] || row.transformForm || '—'} · ${row.dutyOrg || '技术储备'}`
  return `${WAY_TEXT[row.transformWay] || ''} - ${FORM_TEXT[row.transformForm] || row.transformForm || '其他'}`
}

function statusClass(row: any) {
  if (row.status === 'DONE') return 'ok'
  if (row.colorStatus === 'RED') return 'bad'
  if (row.status === 'NEGOTIATING' || row.status === 'SIGNED') return 'idle'
  return 'idle'
}

async function save() {
  if (!guard('fill')) return
  const p = projects.value.find((x) => x.id === form.projectId)
  await transformApi.create({ ...form, projectNo: p?.projectNo })
  message.success('成果包已创建，成果编号由系统自动生成')
  open.value = false
  load()
}

async function openBind(row: any) {
  cur.value = row
  const res = await deliverableApi.list(row.projectId)
  pool.value = (res.data as any[]) || []
  bindIds.value = pool.value.filter((d: any) => d.achievementNo === row.achievementNo).map((d: any) => d.id)
  bindOpen.value = true
}

async function doBind() {
  try {
    await transformApi.bind(cur.value.id, { deliverableIds: bindIds.value })
    message.success('交付物绑定成功，已回写成果编号')
    bindOpen.value = false
    load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message)
  }
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">成果转化独立台账</h2>
    <div class="page-desc">
      成果包为最小管理单元，与交付物、项目台账和可视化看板双向绑定
    </div>
    <WorkDutyBar code="transform" :project="selectedProject" />

    <div class="kpi-row">
      <div class="kpi">
        <div class="label">成果包总数</div>
        <div class="value brand">{{ stats.total }} <span>项</span></div>
      </div>
      <div class="kpi">
        <div class="label">型号转化</div>
        <div class="value">{{ stats.model }} <span>项</span></div>
        <div class="sub">装机/未装机联动字典</div>
      </div>
      <div class="kpi">
        <div class="label">向市场转化</div>
        <div class="value">{{ stats.market }} <span>项</span></div>
        <div class="sub">转让/许可/联合实施等</div>
      </div>
      <div class="kpi">
        <div class="label">已完成转化</div>
        <div class="value ok">{{ stats.done }} <span>项</span></div>
      </div>
      <div class="kpi">
        <div class="label">逾期成果</div>
        <div class="value" :class="stats.overdue ? 'bad' : 'ok'">{{ stats.overdue }} <span>项</span></div>
      </div>
    </div>

    <div class="filter-row">
      <a-input v-model:value="query.keyword" allow-clear placeholder="成果包 / 项目 / 编号" style="width: 220px" @pressEnter="load" />
      <a-select v-model:value="query.status" allow-clear placeholder="全部状态" style="width: 140px" @change="load">
        <a-select-option v-for="(v, k) in STATUS_TEXT" :key="k" :value="k">{{ v }}</a-select-option>
      </a-select>
      <a-select v-model:value="query.transformWay" allow-clear placeholder="全部方式" style="width: 150px" @change="load">
        <a-select-option v-for="o in dictStore.options('TRANSFORM_WAY')" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
      </a-select>
      <a-select v-model:value="query.dutyOrg" allow-clear placeholder="全部单位" style="width: 180px" @change="load">
        <a-select-option v-for="o in orgs" :key="o" :value="o">{{ o }}</a-select-option>
      </a-select>
      <a-button @click="reset">重置</a-button>
      <a-button type="primary" style="margin-left: auto" :disabled="!can.fill" @click="open = true"><PlusOutlined />新建成果包</a-button>
    </div>

    <div class="split">
      <div class="panel">
        <div class="panel-title">型号转化分布</div>
        <div v-if="!modelBars.length" class="empty">暂无型号转化数据</div>
        <div v-else class="bars">
          <div v-for="b in modelBars" :key="b.name" class="bar-row">
            <div class="bar-name">{{ b.name }}</div>
            <div class="bar-track"><div class="bar-fill" :style="{ width: b.pct + '%' }" /></div>
            <div class="bar-n">{{ b.count }}</div>
          </div>
        </div>
      </div>
      <div class="panel">
        <div class="panel-title">成果包明细</div>
        <a-table
          :loading="loading"
          row-key="id"
          :pagination="{ current: query.page, pageSize: query.size, total: rows.length, showSizeChanger: false }"
          :data-source="pageRows"
          :columns="[
            { title: '成果包', key: 'pkg', width: 200 },
            { title: '关联项目', key: 'proj', width: 180 },
            { title: '转化方式', key: 'way', width: 140 },
            { title: '状态', key: 'st', width: 90 },
            { title: '型号/交易对', key: 'pair', width: 160 },
          ]"
          @change="(p: any) => { query.page = p.current }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'pkg'">
              <div class="cell-title">{{ record.name }}</div>
              <a class="cell-id" @click="openBind(record)">{{ record.achievementNo }}</a>
            </template>
            <template v-else-if="column.key === 'proj'">
              <div class="cell-title">{{ record.projectName || '—' }}</div>
              <div class="cell-sub">{{ record.projectNo || '—' }}</div>
            </template>
            <template v-else-if="column.key === 'way'">
              {{ WAY_TEXT[record.transformWay] || dictStore.label('TRANSFORM_WAY', record.transformWay) }}
              <span v-if="record.transformForm"> - {{ FORM_TEXT[record.transformForm] || record.transformForm }}</span>
            </template>
            <template v-else-if="column.key === 'st'">
              <span class="dot" :class="statusClass(record)" />
              {{ STATUS_TEXT[record.status] || record.status }}
            </template>
            <template v-else-if="column.key === 'pair'">{{ pairText(record) }}</template>
          </template>
        </a-table>
      </div>
    </div>

    <a-drawer v-model:open="open" title="新建成果包" :width="'40%'">
      <a-form layout="vertical">
        <a-form-item label="成果名称" required><a-input v-model:value="form.name" placeholder="打包交付物的统一名称" /></a-form-item>
        <a-form-item label="所属项目" required>
          <a-select v-model:value="form.projectId" show-search placeholder="选择项目" :filter-option="(i: string, o: any) => String(o.label).includes(i)">
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name">{{ p.projectNo }} · {{ p.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="成果简介"><a-textarea v-model:value="form.intro" :rows="2" :maxlength="100" show-count placeholder="100 字以内精简描述" /></a-form-item>
        <a-form-item label="转化方式">
          <a-select v-model:value="form.transformWay" @change="form.transformForm = formOptions[0].value">
            <a-select-option v-for="o in dictStore.options('TRANSFORM_WAY')" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="转化形式（与转化方式联动）">
          <a-select v-model:value="form.transformForm">
            <a-select-option v-for="o in formOptions" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="计划转化时间"><a-date-picker v-model:value="form.planDate" style="width: 100%" value-format="YYYY-MM-DD" /></a-form-item>
        <a-form-item label="责任单位"><a-input v-model:value="form.dutyOrg" /></a-form-item>
      </a-form>
      <template #footer>
        <div style="text-align: right">
          <a-button style="margin-right: 8px" @click="open = false">取消</a-button>
          <a-button type="primary" @click="save">保存</a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer v-model:open="bindOpen" :title="`绑定交付物 — ${cur?.achievementNo || ''}`" :width="'50%'">
      <a-alert type="info" show-icon message="仅状态为「已交付」的交付物可纳入成果转化包；多项交付物可绑定同一成果编号实现打包转化。" style="margin-bottom: 12px" />
      <a-table size="small" row-key="id" :pagination="false" :data-source="pool"
        :row-selection="{ selectedRowKeys: bindIds, onChange: (k: any) => (bindIds = k), getCheckboxProps: (r: any) => ({ disabled: r.status !== 'DELIVERED' }) }"
        :columns="[
          { title: '交付物名称', dataIndex: 'name' },
          { title: '类型', dataIndex: 'deliverType', width: 120 },
          { title: '状态', dataIndex: 'status', width: 100 },
          { title: '已绑成果编号', dataIndex: 'achievementNo', width: 140 },
        ]" />
      <template #footer>
        <div style="text-align: right">
          <a-button style="margin-right: 8px" @click="bindOpen = false">取消</a-button>
          <a-button type="primary" @click="doBind">确认绑定</a-button>
        </div>
      </template>
    </a-drawer>
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
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.kpi .label { color: #8c8c8c; font-size: 13px; }
.kpi .value { font-size: 28px; font-weight: 600; color: #262626; line-height: 1.3; }
.kpi .value span { font-size: 13px; font-weight: 400; color: #8c8c8c; }
.kpi .value.brand { color: #0064ef; }
.kpi .value.ok { color: #52c41a; }
.kpi .value.bad { color: #f5222d; }
.kpi .sub { font-size: 12px; color: #8c8c8c; margin-top: 4px; }
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.split { display: grid; grid-template-columns: 360px minmax(0, 1fr); gap: 16px; }
.panel {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 16px 20px;
  min-height: 360px;
}
.panel-title { font-weight: 600; margin-bottom: 16px; color: #262626; }
.bars { display: flex; flex-direction: column; gap: 14px; }
.bar-row { display: grid; grid-template-columns: 88px 1fr 28px; gap: 8px; align-items: center; }
.bar-name { font-size: 13px; color: #262626; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-track { height: 10px; background: #f0f2f5; border-radius: 6px; overflow: hidden; }
.bar-fill { height: 100%; background: #0064ef; border-radius: 6px; }
.bar-n { text-align: right; font-size: 13px; color: #262626; }
.cell-title { color: #262626; }
.cell-id { color: #0064ef; font-size: 12px; }
.cell-sub { color: #8c8c8c; font-size: 12px; }
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  background: #0064ef;
}
.dot.ok { background: #52c41a; }
.dot.bad { background: #f5222d; }
.empty { color: #8c8c8c; }
@media (max-width: 1100px) {
  .kpi-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .split { grid-template-columns: 1fr; }
}
</style>
