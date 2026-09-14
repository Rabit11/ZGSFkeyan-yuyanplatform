<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auditApi, declarationApi } from '@/api/modules'

const router = useRouter()
const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const detailOpen = ref(false)
const detailLoading = ref(false)
const selected = ref<any>({})
const declaration = ref<any>(null)
const materials = ref<any[]>([])
const query = reactive({ page: 1, size: 10, module: undefined as string | undefined })

const MODULE_TEXT: Record<string, string> = {
  DECLARATION: '项目申报',
  PROJECT: '待维护材料',
  CHANGE: '项目变更',
  ACCEPTANCE: '项目验收',
  PLAN: '计划管理',
  FUND: '项目经费',
  HQ_FUND: '总部经费',
  '申报立项': '项目申报',
  '项目申报': '项目申报',
  '项目台账': '待维护材料',
  '项目变更': '项目变更',
  '验收': '项目验收',
  '计划': '计划管理',
  '经费': '项目经费',
}

const MODULE_OPTIONS = [
  { value: 'DECLARATION', label: '项目申报' },
  { value: 'PROJECT', label: '待维护材料' },
  { value: 'CHANGE', label: '项目变更' },
  { value: 'ACCEPTANCE', label: '项目验收' },
  { value: 'PLAN', label: '计划管理' },
  { value: 'FUND', label: '项目经费' },
  { value: 'HQ_FUND', label: '总部经费' },
]

function moduleLabel(module: string) {
  return MODULE_TEXT[module] || module || '其他审批'
}

function isDeclaration(row: any) {
  return ['DECLARATION', '申报立项', '项目申报'].includes(String(row?.module || ''))
}

function isMaintenance(row: any) {
  const module = String(row?.module || '')
  const content = String(row?.content || '')
  return module === 'PROJECT' && /维护|单位科技管理部|总部主管/.test(content)
}

function rowModuleLabel(row: any) {
  return isMaintenance(row) ? '待维护材料' : moduleLabel(row?.module)
}

async function load() {
  loading.value = true
  try {
    const res = await auditApi.mineDone(query)
    rows.value = (res.data as any)?.records || []
    total.value = Number((res.data as any)?.total || 0)
  } finally {
    loading.value = false
  }
}

async function showDetail(row: any) {
  selected.value = row
  declaration.value = null
  materials.value = []
  detailOpen.value = true
  if (!isDeclaration(row) || !row.bizId) return
  detailLoading.value = true
  try {
    const [detailRes, materialRes] = await Promise.all([
      declarationApi.detail(row.bizId),
      declarationApi.materials(row.bizId),
    ])
    declaration.value = detailRes.data || null
    materials.value = (materialRes.data as any[]) || []
  } finally {
    detailLoading.value = false
  }
}

function enterBusiness(row: any) {
  const id = row.bizId ? String(row.bizId) : undefined
  const routes: Record<string, { path: string; query?: Record<string, string> }> = {
    DECLARATION: { path: '/initiation/declaration', query: id ? { doneId: id } : undefined },
    '申报立项': { path: '/initiation/declaration', query: id ? { doneId: id } : undefined },
    '项目申报': { path: '/initiation/declaration', query: id ? { doneId: id } : undefined },
    PROJECT: { path: id ? `/overview/detail/${id}` : '/overview/ledger' },
    '项目台账': { path: id ? `/overview/detail/${id}` : '/overview/ledger' },
    CHANGE: { path: '/implement/change', query: id ? { doneId: id } : undefined },
    '项目变更': { path: '/implement/change', query: id ? { doneId: id } : undefined },
    ACCEPTANCE: { path: '/acceptance/accept', query: id ? { projectId: id } : undefined },
    '验收': { path: '/acceptance/accept', query: id ? { projectId: id } : undefined },
    PLAN: { path: '/implement/plan', query: id ? { doneId: id } : undefined },
    '计划': { path: '/implement/plan', query: id ? { doneId: id } : undefined },
    FUND: { path: '/implement/fund', query: { view: '1' } },
    HQ_FUND: { path: '/implement/fund', query: { view: '1' } },
    '经费': { path: '/implement/fund', query: { view: '1' } },
  }
  router.push(routes[String(row.module || '')] || '/overview/ledger')
}

function search() {
  query.page = 1
  load()
}

function reset() {
  query.module = undefined
  query.page = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">我的已办</h2>
    <div class="page-desc">保留本人已经完成的审批记录，可追溯审批时间、意见、业务信息和相关材料。</div>

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <a-select v-model:value="query.module" allow-clear placeholder="全部审批模块" style="width: 200px" :options="MODULE_OPTIONS" />
        <a-button type="primary" @click="search">查询</a-button>
        <a-button @click="reset">重置</a-button>
      </div>

      <a-table
        row-key="id"
        :loading="loading"
        :data-source="rows"
        :pagination="{ current: query.page, pageSize: query.size, total, showTotal: (t: number) => `共 ${t} 条` }"
        @change="(p: any) => { query.page = p.current; query.size = p.pageSize; load() }"
      >
        <a-table-column title="审批模块" data-index="module" :width="150">
          <template #default="{ record }"><a-tag :color="isMaintenance(record) ? 'orange' : 'blue'">{{ rowModuleLabel(record) }}</a-tag></template>
        </a-table-column>
        <a-table-column title="业务编号" data-index="bizId" :width="120" />
        <a-table-column title="审批记录" data-index="content" />
        <a-table-column title="办理时间" data-index="createdAt" :width="190" />
        <a-table-column title="状态" :width="100">
          <template #default><a-tag color="green">已办</a-tag></template>
        </a-table-column>
        <a-table-column title="操作" :width="180" fixed="right">
          <template #default="{ record }">
            <a-space>
              <a-button type="link" size="small" @click="showDetail(record)">查看详情</a-button>
              <a-button type="link" size="small" @click="enterBusiness(record)">进入业务</a-button>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-card>

    <a-drawer v-model:open="detailOpen" title="已办审批详情" width="720" :destroy-on-close="true">
      <a-spin :spinning="detailLoading">
        <a-descriptions bordered :column="2" size="small">
          <a-descriptions-item label="审批模块">{{ rowModuleLabel(selected) }}</a-descriptions-item>
          <a-descriptions-item label="状态"><a-tag color="green">已办</a-tag></a-descriptions-item>
          <a-descriptions-item label="业务编号">{{ selected.bizId || '—' }}</a-descriptions-item>
          <a-descriptions-item label="办理时间">{{ selected.createdAt || '—' }}</a-descriptions-item>
          <a-descriptions-item label="审批记录" :span="2">{{ selected.content || '—' }}</a-descriptions-item>
        </a-descriptions>

        <template v-if="declaration">
          <a-divider orientation="left">项目申报信息</a-divider>
          <a-descriptions bordered :column="2" size="small">
            <a-descriptions-item label="申报单号">{{ declaration.applyNo || '—' }}</a-descriptions-item>
            <a-descriptions-item label="项目名称">{{ declaration.name || '—' }}</a-descriptions-item>
            <a-descriptions-item label="当前状态">{{ declaration.status || '—' }}</a-descriptions-item>
            <a-descriptions-item label="当前节点">{{ declaration.flowNode || '—' }}</a-descriptions-item>
            <a-descriptions-item label="审批意见" :span="2">{{ declaration.opinion || selected.content || '—' }}</a-descriptions-item>
          </a-descriptions>
          <a-divider orientation="left">申报材料</a-divider>
          <a-table row-key="id" size="small" :pagination="false" :data-source="materials">
            <a-table-column title="材料名称" data-index="fieldName" />
            <a-table-column title="文件" data-index="fileName">
              <template #default="{ record }">
                <a v-if="record.fileUrl" :href="record.fileUrl" target="_blank">{{ record.fileName || '查看材料' }}</a>
                <span v-else>{{ record.fileName || '未上传' }}</span>
              </template>
            </a-table-column>
          </a-table>
        </template>
      </a-spin>
      <template #extra><a-button type="primary" @click="enterBusiness(selected)">进入业务模块</a-button></template>
    </a-drawer>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
</style>
