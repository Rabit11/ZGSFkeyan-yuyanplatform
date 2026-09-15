<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  CloudDownloadOutlined,
  RedoOutlined,
  SaveOutlined,
  UndoOutlined,
} from '@ant-design/icons-vue'
import { backupApi } from '@/api/modules'
import { withAllOption } from '@/utils/filterOptions'

const loading = ref(false)
const creating = ref(false)
const restoring = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const policy = ref<any>({})
const remark = ref('手动全量备份')
const query = reactive<any>({ page: 1, size: 10, triggerType: undefined, status: undefined })

const restoreOpen = ref(false)
const restoreRow = ref<any>(null)
const confirmNo = ref('')

const TRIGGER_TEXT: Record<string, string> = {
  MANUAL: '手动',
  SCHEDULED: '定时',
  PRE_RESTORE: '回滚前安全点',
  PRE_RISK: '高风险操作前置',
}
const STATUS_COLOR: Record<string, string> = {
  SUCCESS: 'success',
  RUNNING: 'processing',
  FAILED: 'error',
}
const STATUS_TEXT: Record<string, string> = {
  SUCCESS: '成功',
  RUNNING: '进行中',
  FAILED: '失败',
}

async function load() {
  loading.value = true
  try {
    const [listRes, policyRes] = await Promise.all([
      backupApi.page(query),
      backupApi.policy().catch(() => ({ data: {} })),
    ])
    rows.value = (listRes.data as any)?.records || []
    total.value = (listRes.data as any)?.total || 0
    policy.value = policyRes.data || {}
  } finally {
    loading.value = false
  }
}

async function createNow() {
  creating.value = true
  try {
    const res = await backupApi.create({ triggerType: 'MANUAL', remark: remark.value || '手动全量备份' })
    message.success(`备份完成：${(res.data as any)?.backupNo || ''}`)
    await load()
  } catch (e: any) {
    message.error(e?.message || '备份失败')
  } finally {
    creating.value = false
  }
}

function openRestore(row: any) {
  if (row.status !== 'SUCCESS') {
    message.warning('仅成功的备份可用于回滚')
    return
  }
  restoreRow.value = row
  confirmNo.value = ''
  restoreOpen.value = true
}

async function doRestore() {
  const row = restoreRow.value
  if (!row) return
  if (confirmNo.value.trim() !== row.backupNo) {
    message.error(`请输入完整备份编号 ${row.backupNo}`)
    return
  }
  restoring.value = true
  try {
    await backupApi.restore(row.id, confirmNo.value.trim())
    message.success('回滚完成。如需撤销本次回滚，请使用刚刚自动生成的「回滚前安全点」。')
    restoreOpen.value = false
    await load()
  } catch (e: any) {
    message.error(e?.message || '回滚失败')
  } finally {
    restoring.value = false
  }
}

async function download(row: any) {
  try {
    const token = localStorage.getItem('rpm_token') || ''
    const res = await fetch(`/api/backups/${row.id}/file`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (!res.ok) {
      message.error('下载失败')
      return
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.backupNo}.json.gz`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    message.error('下载失败')
  }
}

function fmtSize(n?: number) {
  if (n == null) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">备份回滚</h2>
    <div class="page-desc">
      全库业务数据逻辑快照：每日 02:00 自动备份，高风险操作（表单维护入库、权限矩阵变更）前自动打点。
      回滚前再生成安全点，因此回滚可再次撤销。附件按对象清单对账，默认不重复拷贝文件本体。
    </div>

    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6">
        <a-card size="small">
          <div class="stat-label">保留策略</div>
          <div class="stat-value">{{ policy.keepDays || 30 }} 天 / {{ policy.maxCount || 20 }} 份</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small">
          <div class="stat-label">定时任务</div>
          <div class="stat-value">{{ policy.cronText || '每天 02:00' }}</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small">
          <div class="stat-label">快照目录</div>
          <div class="stat-value ellipsis" :title="policy.dir">{{ policy.dir || '—' }}</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small">
          <div class="stat-label">历史份数</div>
          <div class="stat-value">{{ total }}</div>
        </a-card>
      </a-col>
    </a-row>

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar" style="margin-bottom: 16px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center">
        <a-input v-model:value="remark" placeholder="备份说明" style="width: 240px" />
        <a-button type="primary" :loading="creating" @click="createNow">
          <template #icon><SaveOutlined /></template>
          立即备份
        </a-button>
        <a-button @click="load">
          <template #icon><RedoOutlined /></template>
          刷新
        </a-button>
        <a-select
          v-model:value="query.triggerType"
          allow-clear
          placeholder="全部来源"
          style="width: 160px"
          :options="withAllOption(Object.entries(TRIGGER_TEXT).map(([value, label]) => ({ value, label })))"
          @change="() => { query.page = 1; load() }"
        />
        <a-select
          v-model:value="query.status"
          allow-clear
          placeholder="全部状态"
          style="width: 120px"
          :options="withAllOption(Object.entries(STATUS_TEXT).map(([value, label]) => ({ value, label })))"
          @change="() => { query.page = 1; load() }"
        />
      </div>

      <a-table
        :loading="loading"
        row-key="id"
        :data-source="rows"
        :pagination="{ current: query.page, pageSize: query.size, total, showTotal: (t: number) => `共 ${t} 条` }"
        @change="(p: any) => { query.page = p.current; query.size = p.pageSize; load() }"
        :columns="[
          { title: '备份编号', dataIndex: 'backupNo', width: 180 },
          { title: '来源', dataIndex: 'triggerType', width: 140 },
          { title: '状态', dataIndex: 'status', width: 90 },
          { title: '说明', dataIndex: 'remark' },
          { title: '表/行', key: 'stat', width: 110 },
          { title: '大小', dataIndex: 'fileSize', width: 90 },
          { title: '操作人', dataIndex: 'createdBy', width: 110 },
          { title: '时间', dataIndex: 'createdAt', width: 180 },
          { title: '操作', key: 'op', width: 200, fixed: 'right' },
        ]"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'triggerType'">
            <a-tag>{{ TRIGGER_TEXT[record.triggerType] || record.triggerType }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="STATUS_COLOR[record.status]">{{ STATUS_TEXT[record.status] || record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'stat'">
            {{ record.tableCount || 0 }} / {{ record.rowCount || 0 }}
          </template>
          <template v-else-if="column.dataIndex === 'fileSize'">
            {{ fmtSize(record.fileSize) }}
          </template>
          <template v-else-if="column.key === 'op'">
            <a-button type="link" size="small" :disabled="record.status !== 'SUCCESS'" @click="openRestore(record)">
              <UndoOutlined /> 回滚
            </a-button>
            <a-button type="link" size="small" :disabled="record.status !== 'SUCCESS'" @click="download(record)">
              <CloudDownloadOutlined /> 下载
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="restoreOpen"
      title="确认回滚"
      ok-text="确认回滚"
      ok-type="danger"
      :confirm-loading="restoring"
      :ok-button-props="{ disabled: !restoreRow || confirmNo.trim() !== restoreRow.backupNo }"
      @ok="doRestore"
    >
      <p>将按该快照恢复业务数据（不含审计日志与预警）。回滚前会自动再打一份当前安全点，因此本次回滚可再次撤销。</p>
      <p class="hint">说明：{{ restoreRow?.remark || '—' }}；表/行 {{ restoreRow?.tableCount || 0 }} / {{ restoreRow?.rowCount || 0 }}。</p>
      <p>请输入备份编号以确认：<b>{{ restoreRow?.backupNo }}</b></p>
      <a-input v-model:value="confirmNo" :placeholder="restoreRow?.backupNo" />
    </a-modal>
  </div>
</template>

<style scoped>
.stat-label { color: var(--zgsf-text-secondary); font-size: 13px; }
.stat-value { color: var(--zgsf-text); font-size: 16px; margin-top: 4px; font-weight: 600; }
.ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hint { color: var(--zgsf-text-secondary); }
</style>
