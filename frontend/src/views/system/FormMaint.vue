<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  SaveOutlined,
  SettingOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'
import { projectApi } from '@/api/modules'
import { isSilentAuthError } from '@/api/request'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { FORM_MAINT_OPTIONS } from '@/constants/permission'
import { LEVEL_TEXT, PROJECT_STATUS_TEXT, type ProjInfo } from '@/api/types'
import { fmtAmount } from '@/utils/format'
import {
  ACTION_TEXT,
  FORM_TEMPLATE_COLS,
  FORM_TEMPLATE_DEFAULT_VISIBLE,
  buildFormMaintWorkbook,
  downloadArrayBuffer,
  formatTemplateNum,
  formatTemplateYm,
  projectToFormRow,
  runImportPreview,
  toProjectPayload,
  STATUS_TO_TEXT,
  type FormMaintRow,
  type ImportPreviewResult,
  type PreviewAction,
} from '@/utils/formMaintExcel'
import { withAllOption } from '@/utils/filterOptions'

const userStore = useUserStore()
const dictStore = useDictStore()
const activeTab = ref('master')
const loading = ref(false)
const rows = ref<FormMaintRow[]>([])
const selectedKeys = ref<(string | number)[]>([])
const uploading = ref(false)
const importPreview = ref<FormMaintRow[]>([])
const previewResult = ref<ImportPreviewResult | null>(null)
const previewOpen = ref(false)
const colCfgOpen = ref(false)
const editorOpen = ref(false)
const editorLoading = ref(false)
const editorSaving = ref(false)
const pendingBatches = ref(0)
const importMode = ref<'merge' | 'replace'>('merge')
const forceDespiteIssues = ref(false)
const confirmNewChannels = ref(true)
const issueFilter = ref<string>('')
const editForm = reactive<Partial<FormMaintRow>>({})

const scopeLabel =
  FORM_MAINT_OPTIONS.find((o) => o.value === 'hq')?.label || '总部全部台账'

const query = reactive({
  keyword: '',
  levelCode: undefined as string | undefined,
  channelName: undefined as string | undefined,
  projectType: undefined as string | undefined,
  orgName: undefined as string | undefined,
  status: undefined as string | undefined,
})

/** 列表列 = 官方总表 37 列 + 平台「来源」 */
const ALL_COLS = [
  ...FORM_TEMPLATE_COLS,
  { key: 'dataSource', title: '来源', width: 120, kind: 'text' as const },
]

const visibleColKeys = ref<string[]>([...FORM_TEMPLATE_DEFAULT_VISIBLE, 'dataSource'])
const colMeta = Object.fromEntries(ALL_COLS.map((c) => [c.key, c]))

const filteredRows = computed(() =>
  rows.value.filter((r) => {
    const key = query.keyword.trim().toLowerCase()
    if (
      key &&
      !`${r.seqNo || ''} ${r.name || ''} ${r.projectNo || ''}`.toLowerCase().includes(key)
    ) {
      return false
    }
    if (query.levelCode && r.levelCode !== query.levelCode) return false
    if (query.channelName && r.channelName !== query.channelName) return false
    if (query.projectType && r.projectType !== query.projectType) return false
    if (query.orgName && r.leadOrgName !== query.orgName && r.orgName !== query.orgName) return false
    if (query.status && r.status !== query.status) return false
    return true
  }),
)

const stats = computed(() => {
  const list = filteredRows.value
  const sum = (fn: (r: FormMaintRow) => number) =>
    Math.round(list.reduce((s, r) => s + fn(r), 0) * 100) / 100
  return {
    count: list.length,
    totalFund: sum((r) => Number(r.totalFund || 0)),
    national: sum((r) => Number(r.nationalFund || 0)),
    self: sum((r) => Number(r.selfFund || 0)),
    running: list.filter((r) => ['IMPLEMENTING', 'DELAYED', 'DECLARING', 'FILING'].includes(r.status || ''))
      .length,
    finished: list.filter((r) =>
      ['FINISHED', 'COMPANY_ACCEPTED', 'GOV_ACCEPTED'].includes(r.status || ''),
    ).length,
    overdue: list.filter((r) => r.warnColor === 'RED' || r.status === 'DELAYED').length,
    invalid: list.filter((r) => r.validateOk === false).length,
  }
})

const channelOptions = computed(() =>
  [...new Set(rows.value.map((r) => r.channelName).filter(Boolean) as string[])].map((v) => ({
    value: v,
    label: v,
  })),
)
const typeOptions = computed(() =>
  [...new Set(rows.value.map((r) => r.projectType).filter(Boolean) as string[])].map((v) => ({
    value: v,
    label: v,
  })),
)
const orgOptions = computed(() =>
  [...new Set(rows.value.map((r) => r.leadOrgName || r.orgName).filter(Boolean) as string[])].map(
    (v) => ({ value: v, label: v }),
  ),
)

const tableColumns = computed(() => {
  const cols = ALL_COLS.filter((c) => visibleColKeys.value.includes(c.key)).map((c) => ({
    title: c.title,
    dataIndex: c.key,
    key: c.key,
    width: c.width,
    ellipsis: true,
  }))
  return [...cols, { title: '操作', key: 'action', width: 140, fixed: 'right' as const }]
})

const tableScrollX = computed(
  () => ALL_COLS.filter((c) => visibleColKeys.value.includes(c.key)).reduce((s, c) => s + c.width, 140) + 48,
)

function cellText(record: FormMaintRow, key: string) {
  const meta = colMeta[key]
  const raw = (record as any)[key]
  if (key === 'seqNo') return record.seqNo ?? ''
  if (key === 'levelCode') {
    return LEVEL_TEXT[record.levelCode as keyof typeof LEVEL_TEXT] || record.levelCode || ''
  }
  if (key === 'status') return statusLabel(record.status)
  if (key === 'leadOrgName') return record.leadOrgName || record.orgName || ''
  if (key === 'ownerName') return record.ownerName || record.createByName || ''
  if (key === 'filingYm') return formatTemplateYm(record.filingYm || record.startYm)
  if (key === 'startYm') return formatTemplateYm(record.startYm || record.startDate)
  if (key === 'endYm') return formatTemplateYm(record.endYm || record.endDate)
  if (meta?.kind === 'ym') return formatTemplateYm(raw)
  if (meta?.kind === 'num') return formatTemplateNum(raw)
  return raw == null || raw === '' ? '' : String(raw)
}

async function load() {
  loading.value = true
  try {
    const res = await projectApi.page({ page: 1, size: 2000 })
    const list = ((res.data as any)?.records || []) as ProjInfo[]
    rows.value = list.map((p, i) => {
      const row = projectToFormRow(p)
      row.seqNo = i + 1
      return row
    })
  } catch (e: any) {
    if (!isSilentAuthError(e)) {
      message.error(e?.message || '加载失败')
    }
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.keyword = ''
  query.levelCode = undefined
  query.channelName = undefined
  query.projectType = undefined
  query.orgName = undefined
  query.status = undefined
}

function statusLabel(s?: string) {
  return STATUS_TO_TEXT[s || ''] || PROJECT_STATUS_TEXT[s || ''] || s || '—'
}

function onSelectChange(keys: (string | number)[]) {
  selectedKeys.value = keys
}

function assignEditForm(row?: Partial<FormMaintRow>) {
  Object.keys(editForm).forEach((key) => delete (editForm as any)[key])
  Object.assign(editForm, {
    id: row?.id,
    projectNo: row?.projectNo,
    name: row?.name || '',
    levelCode: row?.levelCode,
    channelName: row?.channelName || '',
    bureauOffice: row?.bureauOffice || '',
    projectType: row?.projectType || '',
    major1: row?.major1 || '',
    major2: row?.major2 || '',
    manageOrgName: row?.manageOrgName || '',
    leadOrgName: row?.leadOrgName || row?.orgName || '',
    orgName: row?.orgName || row?.leadOrgName || '',
    status: row?.status || 'IMPLEMENTING',
    acceptStatus: row?.acceptStatus || '未验收',
    ownerName: row?.ownerName || row?.createByName || '',
    startYm: row?.startYm || row?.startDate?.slice(0, 7) || '',
    endYm: row?.endYm || row?.endDate?.slice(0, 7) || '',
    totalFund: row?.totalFund ?? 0,
    nationalFund: row?.nationalFund ?? 0,
    selfFund: row?.selfFund ?? 0,
    expenseTotal: row?.expenseTotal ?? 0,
    yearBudget: row?.yearBudget ?? 0,
    dataSource: row?.dataSource || 'FORM_MAINT',
    warnColor: row?.warnColor || 'BLUE',
    createByName: row?.createByName,
  })
}

function ymToFirstDay(value?: string) {
  const match = String(value || '').trim().replace(/[年月.]/g, '-').match(/^(\d{4})-(\d{1,2})/)
  return match ? `${match[1]}-${match[2].padStart(2, '0')}-01` : undefined
}

async function openEditor(row: FormMaintRow) {
  editorOpen.value = true
  editorLoading.value = true
  assignEditForm(row)
  try {
    const res = await projectApi.detail(row.id)
    assignEditForm(projectToFormRow((res.data || row) as ProjInfo))
  } catch (e: any) {
    if (!isSilentAuthError(e)) {
      message.error(e?.message || '加载项目详情失败')
    }
  } finally {
    editorLoading.value = false
  }
}

async function saveEditor() {
  if (!editForm.id) return
  if (!editForm.name || !String(editForm.name).trim()) {
    message.warning('请填写项目名称')
    return
  }
  editorSaving.value = true
  try {
    const payload = toProjectPayload({
      ...(editForm as FormMaintRow),
      startDate: ymToFirstDay(editForm.startYm) || editForm.startDate,
      endDate: ymToFirstDay(editForm.endYm) || editForm.endDate,
    })
    await projectApi.updateFromFormMaint(editForm.id, { ...payload, projectNo: editForm.projectNo })
    message.success('已保存修改并写入审计日志')
    editorOpen.value = false
    await load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) {
      message.error(e?.message || '保存失败')
    }
  } finally {
    editorSaving.value = false
  }
}

async function onUploadMaster(file: File, mode: 'merge' | 'replace' = 'merge') {
  if (mode === 'replace' && !userStore.isAdmin) {
    message.error('无权覆盖总表，请改用合并导入（上传分表）')
    return false
  }
  uploading.value = true
  importMode.value = mode
  forceDespiteIssues.value = false
  try {
    const buf = await file.arrayBuffer()
    const result = runImportPreview(buf, rows.value, {
      mode,
      fileName: file.name,
      fileSize: file.size,
      channelNames: dictStore.channels.map((c) => c.channelName),
      knownPeople: [], // 成员名录未全量拉取时不强制待确认人员
    })
    previewResult.value = result
    importPreview.value = result.rows
    previewOpen.value = true
    pendingBatches.value = result.stats.skipped
    activeTab.value = result.stats.issueCount > 0 ? 'report' : 'upload'
    message.success(
      `预校验完成：${result.batchStatus} · 解析 ${result.stats.parsed} · 新增 ${result.stats.added} · 更新 ${result.stats.updated} · 跳过 ${result.stats.skipped}`,
    )
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e?.message || '解析 Excel 失败')
  } finally {
    uploading.value = false
  }
  return false
}

const previewRowsFiltered = computed(() => {
  let list = importPreview.value
  if (issueFilter.value === 'issue') list = list.filter((r) => !r.validateOk || r.action === 'skip')
  if (issueFilter.value === 'add') list = list.filter((r) => r.action === 'add')
  if (issueFilter.value === 'update') list = list.filter((r) => r.action === 'update')
  if (issueFilter.value === 'skip') list = list.filter((r) => r.action === 'skip')
  if (issueFilter.value === 'delete') list = list.filter((r) => r.action === 'delete')
  return list
})

function actionColor(a?: PreviewAction) {
  if (a === 'add') return 'green'
  if (a === 'update') return 'blue'
  if (a === 'keep') return 'default'
  if (a === 'delete') return 'red'
  return 'orange'
}

function rowsToCommit() {
  const list = importPreview.value
  if (forceDespiteIssues.value) {
    return list.filter((r) => {
      if (r.action === 'keep') return false
      if (r.action === 'delete') return importMode.value === 'replace'
      if (r.action === 'add' || r.action === 'update') return true
      // skip：仅可强制且非唯一性/无标识问题
      return !!r.forceable && r.action === 'skip'
    })
  }
  return list.filter((r) => r.action === 'add' || r.action === 'update' || (importMode.value === 'replace' && r.action === 'delete'))
}

async function confirmImport(mode: 'merge' | 'replace') {
  importMode.value = mode
  if (mode === 'replace' && !userStore.isAdmin) {
    message.error('无权覆盖总表，请改用合并入库')
    return
  }
  const issueRows = importPreview.value.filter((r) => r.action === 'skip' || !r.validateOk)
  if (issueRows.length && !forceDespiteIssues.value) {
    message.warning('存在校验问题行。请在线修正后重试，或勾选「已知晓问题仍确认入库」后强制入库')
    return
  }
  if (previewResult.value?.pendingChannels.length && !confirmNewChannels.value) {
    message.warning('存在待新增渠道/类型，请勾选确认写入字典后再入库')
    return
  }

  const targets = rowsToCommit()
  if (!targets.length) {
    message.warning('没有可入库的有效行（新增/更新）')
    return
  }

  // 不可强制的问题即使勾选也不写入
  const blocked = targets.filter(
    (r) =>
      r.action === 'skip' &&
      (r.issues || []).some((i) => i.forceable === false),
  )
  if (blocked.length) {
    message.error(`有 ${blocked.length} 行存在不可强制入库的问题（唯一标识/唯一性冲突），已排除`)
  }

  const commitList = targets.filter(
    (r) => !(r.action === 'skip' && (r.issues || []).some((i) => i.forceable === false)),
  )

  loading.value = true
  try {
    if (mode === 'replace') {
      for (const r of commitList.filter((x) => x.action === 'delete' && x.id)) {
        await projectApi.remove(r.id)
      }
    }
    let created = 0
    let updated = 0
    let forced = 0
    for (const row of commitList) {
      if (row.action === 'delete' || row.action === 'keep') continue
      const payload: any = toProjectPayload(row)
      if (forceDespiteIssues.value && row.action === 'skip') {
        payload.remark = [row.remark, `强制入库:${row.validateMsg || ''}`].filter(Boolean).join('；')
        forced++
      }
      try {
        if ((row.action === 'update' || (row.action === 'skip' && row.id)) && row.id) {
          await projectApi.update(row.id, { ...payload, projectNo: row.projectNo })
          updated++
        } else {
          // 合并命中兜底
          const hit = rows.value.find(
            (x) =>
              x.name === row.name &&
              (x.leadOrgName || x.orgName) === (row.leadOrgName || row.orgName),
          )
          if (mode === 'merge' && hit?.id) {
            await projectApi.update(hit.id, { ...payload, projectNo: hit.projectNo })
            updated++
          } else {
            await projectApi.create(payload)
            created++
          }
        }
      } catch (e: any) {
        // 单行失败：会话类错误立即中止，避免刷屏
        const msg = e?.message || '未知错误'
        if (e?.silent || msg === 'SESSION_EXPIRED' || e?.status === 401 || e?.status === 403) {
          if (!e?.silent && msg !== 'SESSION_EXPIRED') message.error(msg)
          throw e
        }
        message.error(`${row.name || '某行'} 入库失败：${msg}`)
        throw e
      }
    }
    message.success(
      `入库完成：新增 ${created}，更新 ${updated}${forced ? `，强制 ${forced}` : ''}（已同步项目台账）`,
    )
    previewOpen.value = false
    importPreview.value = []
    previewResult.value = null
    pendingBatches.value = 0
    await load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) {
      message.error(e?.message || '入库失败')
    }
  } finally {
    loading.value = false
  }
}

async function exportExcel(filteredOnly: boolean) {
  try {
    const list = (filteredOnly ? filteredRows.value : rows.value).map(projectToFormRow)
    const buf = await buildFormMaintWorkbook(list)
    const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '')
    downloadArrayBuffer(
      buf,
      filteredOnly
        ? `预研项目_总表_筛选_${stamp}_${list.length}条.xlsx`
        : `预研项目_总表_${stamp}_${list.length}条.xlsx`,
    )
    message.success('已按官方总表模板导出')
  } catch (e: any) {
    message.error(e?.message || '导出失败')
  }
}

function deleteSelected() {
  if (!selectedKeys.value.length) return message.warning('请先勾选项目')
  Modal.confirm({
    title: '删除所选',
    content: `确认删除选中的 ${selectedKeys.value.length} 条项目？将同步从项目台账移除。`,
    okType: 'danger',
    async onOk() {
      for (const id of selectedKeys.value) {
        await projectApi.removeFromFormMaint(Number(id))
      }
      selectedKeys.value = []
      message.success('已删除并同步台账')
      await load()
    },
  })
}

function clearFormMaint() {
  Modal.confirm({
    title: '清空表单维护导入项目',
    content: '仅清空「表单维护导入」来源的项目，平台同步项目不受影响。确认继续？',
    okType: 'danger',
    async onOk() {
      const list = rows.value.filter((r) => r.dataSource === 'FORM_MAINT')
      for (const r of list) await projectApi.removeFromFormMaint(r.id)
      message.success(`已清空 ${list.length} 条表单维护项目`)
      await load()
    },
  })
}

async function removeOne(row: FormMaintRow) {
  Modal.confirm({
    title: '删除项目',
    content: `确认删除「${row.name}」？将同步从项目台账移除。`,
    okType: 'danger',
    async onOk() {
      await projectApi.removeFromFormMaint(row.id)
      message.success('已删除')
      await load()
    },
  })
}

onMounted(async () => {
  await dictStore.loadChannels()
  await load()
})
</script>

<template>
  <div class="page-container fm-page">
    <div class="fm-head">
      <div>
        <h2 class="page-title">表单维护</h2>
        <div class="page-desc">公司全部预研项目总表 / 分表。导入数据同步至项目台账，台账编辑/删除亦回写本表。</div>
      </div>
      <a-tag color="blue">{{ userStore.realName }} · {{ scopeLabel }}</a-tag>
    </div>

    <a-card :body-style="{ padding: '12px 16px 16px' }">
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="master" tab="总表管理">
          <div class="fm-toolbar">
            <a-space wrap>
              <a-upload
                :show-upload-list="false"
                :before-upload="(f: File) => onUploadMaster(f, 'replace')"
                accept=".xlsx,.xls"
              >
                <a-button type="primary" :loading="uploading">
                  <UploadOutlined />上传总表
                </a-button>
              </a-upload>
              <a-upload
                :show-upload-list="false"
                :before-upload="(f: File) => onUploadMaster(f, 'merge')"
                accept=".xlsx,.xls"
              >
                <a-button><UploadOutlined />上传分表</a-button>
              </a-upload>
              <a-button @click="exportExcel(false)"><DownloadOutlined />导出 Excel</a-button>
              <a-button @click="exportExcel(true)"><DownloadOutlined />导出筛选包</a-button>
            </a-space>
            <a-space>
              <a-button type="link" danger @click="deleteSelected">
                <DeleteOutlined />删除所选
              </a-button>
              <a-button danger @click="clearFormMaint">清空全部项目</a-button>
            </a-space>
          </div>

          <div class="fm-filters">
            <a-input
              v-model:value="query.keyword"
              allow-clear
              placeholder="搜索序号/名称/编号"
              style="width: 200px"
            />
            <a-select
              v-model:value="query.levelCode"
              allow-clear
              placeholder="全部层级"
              style="width: 120px"
              :options="withAllOption(Object.entries(LEVEL_TEXT).map(([value, label]) => ({ value, label })))"
            />
            <a-select
              v-model:value="query.channelName"
              allow-clear
              show-search
              placeholder="全部渠道"
              style="width: 160px"
              :options="withAllOption(channelOptions)"
            />
            <a-select
              v-model:value="query.projectType"
              allow-clear
              show-search
              placeholder="全部项目类型"
              style="width: 150px"
              :options="withAllOption(typeOptions)"
            />
            <a-select
              v-model:value="query.orgName"
              allow-clear
              show-search
              placeholder="全部单位"
              style="width: 160px"
              :options="withAllOption(orgOptions)"
            />
            <a-select
              v-model:value="query.status"
              allow-clear
              placeholder="全部状态"
              style="width: 120px"
              :options="withAllOption(Object.entries(STATUS_TO_TEXT).map(([value, label]) => ({ value, label })))"
            />
            <a-button type="link" @click="resetFilters">重置</a-button>
          </div>

          <div class="fm-banner">已加载 {{ stats.count }} 条预研项目</div>

          <div class="fm-stats">
            <div class="stat"><div class="n">{{ stats.count }}</div><div class="l">项目</div></div>
            <div class="stat"><div class="n">{{ fmtAmount(stats.totalFund) }}</div><div class="l">总经费（万元）</div></div>
            <div class="stat"><div class="n">{{ fmtAmount(stats.national) }}</div><div class="l">国拨经费（万元）</div></div>
            <div class="stat"><div class="n">{{ fmtAmount(stats.self) }}</div><div class="l">自筹经费（万元）</div></div>
            <div class="stat"><div class="n">{{ stats.running }}</div><div class="l">实施中</div></div>
            <div class="stat ok"><div class="n">{{ stats.finished }}</div><div class="l">已完成</div></div>
            <div class="stat danger"><div class="n">{{ stats.overdue }}</div><div class="l">逾期</div></div>
            <div class="stat danger"><div class="n">{{ stats.invalid }}</div><div class="l">校验未通过</div></div>
            <a-button class="col-btn" @click="colCfgOpen = true">
              <SettingOutlined />列配置 {{ visibleColKeys.length }}
            </a-button>
          </div>

          <a-table
            size="middle"
            row-key="id"
            :loading="loading"
            :data-source="filteredRows"
            :columns="tableColumns"
            :scroll="{ x: tableScrollX }"
            :pagination="{ pageSize: 20, showTotal: (t: number) => `共 ${t} 条` }"
            :row-selection="{ selectedRowKeys: selectedKeys, onChange: onSelectChange }"
          >
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.key === 'seqNo'">{{ record.seqNo || index + 1 }}</template>
              <template v-else-if="column.key === 'status'">
                <a-tag>{{ statusLabel(record.status) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'acceptStatus'">
                {{ record.acceptStatus || '未验收' }}
              </template>
              <template v-else-if="column.key === 'dataSource'">
                <a-tag :color="record.dataSource === 'FORM_MAINT' ? 'purple' : 'blue'">
                  {{ record.dataSource === 'FORM_MAINT' ? '表单维护导入' : '平台同步' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space>
                  <a @click="openEditor(record)">查看</a>
                  <a class="danger" @click="removeOne(record)">删除</a>
                </a-space>
              </template>
              <template v-else-if="column.key && column.key !== 'selection'">
                {{ cellText(record, column.key) || '—' }}
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="split" tab="分表拆分">
          <a-empty description="按项目类型自动形成专项分表；数据与总表、项目台账同源同步" />
        </a-tab-pane>
        <a-tab-pane key="upload">
          <template #tab>
            <span>批量上传<a-badge v-if="pendingBatches" :count="pendingBatches" :offset="[8, -2]" /></span>
          </template>
          <div class="upload-pane">
            <a-space style="margin-bottom: 16px">
              <a-upload
                :show-upload-list="false"
                :before-upload="(f: File) => onUploadMaster(f, 'replace')"
                accept=".xlsx,.xls"
              >
                <a-button type="primary" :loading="uploading">上传总表（覆盖预览）</a-button>
              </a-upload>
              <a-upload
                :show-upload-list="false"
                :before-upload="(f: File) => onUploadMaster(f, 'merge')"
                accept=".xlsx,.xls"
              >
                <a-button :loading="uploading">上传分表（合并预览）</a-button>
              </a-upload>
            </a-space>
            <a-upload-dragger
              :show-upload-list="false"
              :before-upload="(f: File) => onUploadMaster(f, 'merge')"
              accept=".xlsx,.xls"
            >
              <p class="ant-upload-text">点击或拖拽上传 Excel（默认合并模式）</p>
              <p class="ant-upload-hint">支持 .xlsx/.xls，≤40MB。上传后先预校验，确认前不写入项目台账。</p>
            </a-upload-dragger>
          </div>
        </a-tab-pane>
        <a-tab-pane key="report" tab="校验报告">
          <template v-if="!importPreview.length">
            <a-empty description="上传 Excel 后可在此查看校验结果、预览动作与问题分类" />
          </template>
          <template v-else>
            <div v-if="previewResult" class="report-summary">
              <a-tag color="processing">{{ previewResult.batchStatus }}</a-tag>
              <a-tag>{{ previewResult.mode === 'replace' ? '覆盖' : '合并' }}</a-tag>
              <span>解析 {{ previewResult.stats.parsed }}</span>
              <span>新增 {{ previewResult.stats.added }}</span>
              <span>更新 {{ previewResult.stats.updated }}</span>
              <span>保持 {{ previewResult.stats.kept }}</span>
              <span>跳过 {{ previewResult.stats.skipped }}</span>
              <span v-if="previewResult.stats.deleted">删除 {{ previewResult.stats.deleted }}</span>
              <span>问题 {{ previewResult.stats.issueCount }}</span>
              <a-button type="link" @click="previewOpen = true">打开预校验弹窗</a-button>
            </div>
            <a-table
              row-key="excelRow"
              size="small"
              :data-source="importPreview"
              :pagination="{ pageSize: 20 }"
              :columns="[
                { title: 'Excel行', dataIndex: 'excelRow', width: 80 },
                { title: '动作', dataIndex: 'action', width: 80 },
                { title: '项目名称', dataIndex: 'name', ellipsis: true },
                { title: '项目类型', dataIndex: 'projectType', width: 140, ellipsis: true },
                { title: '问题类别', key: 'cats', width: 140 },
                { title: '说明', dataIndex: 'validateMsg', ellipsis: true },
              ]"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'action'">
                  <a-tag :color="actionColor(record.action)">
                    {{ ACTION_TEXT[record.action as PreviewAction] || record.action }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'cats'">
                  <a-tag
                    v-for="c in [...new Set((record.issues || []).map((i: any) => i.category))]"
                    :key="c"
                    color="orange"
                  >
                    {{ c }}
                  </a-tag>
                  <span v-if="!(record.issues || []).length">—</span>
                </template>
                <template v-else-if="column.dataIndex === 'validateMsg'">
                  <a-tag :color="record.validateOk ? 'success' : 'error'">{{ record.validateMsg }}</a-tag>
                </template>
              </template>
            </a-table>
          </template>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <a-modal
      v-model:open="previewOpen"
      title="导入预校验"
      width="960px"
      :footer="null"
      destroy-on-close
    >
      <div v-if="previewResult" class="preview-meta">
        <div>
          文件：{{ previewResult.fileName || '—' }} · 工作表：{{ previewResult.sheetName }} · 模式：
          {{ previewResult.mode === 'replace' ? '覆盖' : '合并' }} · 状态：
          <a-tag color="processing">{{ previewResult.batchStatus }}</a-tag>
        </div>
        <div class="preview-stats">
          共 {{ previewResult.stats.parsed }} 行 · 新增 {{ previewResult.stats.added }} · 更新
          {{ previewResult.stats.updated }} · 保持 {{ previewResult.stats.kept }} · 跳过
          {{ previewResult.stats.skipped }}
          <span v-if="previewResult.stats.deleted"> · 删除 {{ previewResult.stats.deleted }}</span>
          · 问题 {{ previewResult.stats.issueCount }}。确认前不写入正式台账。
        </div>
      </div>
      <a-space style="margin-bottom: 8px" wrap>
        <a-radio-group v-model:value="issueFilter" size="small" button-style="solid">
          <a-radio-button value="">全部</a-radio-button>
          <a-radio-button value="issue">仅问题</a-radio-button>
          <a-radio-button value="add">新增</a-radio-button>
          <a-radio-button value="update">更新</a-radio-button>
          <a-radio-button value="skip">跳过</a-radio-button>
          <a-radio-button v-if="importMode === 'replace'" value="delete">删除</a-radio-button>
        </a-radio-group>
      </a-space>
      <a-table
        size="small"
        :row-key="(r: FormMaintRow) => String(r.excelRow || r.uniqueKey || r.name)"
        :data-source="previewRowsFiltered"
        :pagination="{ pageSize: 10, showTotal: (t: number) => `共 ${t} 条` }"
        :scroll="{ y: 360, x: 900 }"
        :columns="[
          { title: '行号', dataIndex: 'excelRow', width: 64 },
          { title: '动作', dataIndex: 'action', width: 72 },
          { title: '名称', dataIndex: 'name', ellipsis: true, width: 180 },
          { title: '级别', dataIndex: 'levelCode', width: 80 },
          { title: '责任单位', dataIndex: 'leadOrgName', width: 140, ellipsis: true },
          { title: '类别', key: 'cats', width: 120 },
          { title: '结果', dataIndex: 'validateMsg', width: 220 },
        ]"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'action'">
            <a-tag :color="actionColor(record.action)">
              {{ ACTION_TEXT[record.action as PreviewAction] || record.action }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'levelCode'">
            {{ LEVEL_TEXT[record.levelCode as keyof typeof LEVEL_TEXT] || record.levelCode || '—' }}
          </template>
          <template v-else-if="column.key === 'cats'">
            <a-tag
              v-for="c in [...new Set((record.issues || []).map((i: any) => i.category))]"
              :key="c"
              color="orange"
            >
              {{ c }}
            </a-tag>
            <span v-if="!(record.issues || []).length && record.validateOk">—</span>
          </template>
          <template v-else-if="column.dataIndex === 'validateMsg'">
            <a-tooltip v-if="record.fieldDiffs?.length" :title="record.fieldDiffs.map((d: any) => `${d.field}: ${d.before} → ${d.after}`).join('\n')">
              <a-tag :color="record.validateOk ? 'success' : 'error'">{{ record.validateMsg }}</a-tag>
            </a-tooltip>
            <a-tag v-else :color="record.validateOk ? 'success' : 'error'">{{ record.validateMsg }}</a-tag>
          </template>
        </template>
      </a-table>

      <div v-if="previewResult?.pendingChannels?.length" class="pending-box">
        <b>待新增渠道/类型（{{ previewResult.pendingChannels.length }}）</b>
        <div v-for="c in previewResult.pendingChannels.slice(0, 8)" :key="c.channelName + (c.projectType || '')">
          {{ c.channelName }}{{ c.projectType ? ' / ' + c.projectType : '' }}（行 {{ c.excelRows.join(',') }}）
        </div>
        <a-checkbox v-model:checked="confirmNewChannels">确认写入下拉字典</a-checkbox>
      </div>

      <div class="preview-options">
        <a-checkbox v-model:checked="forceDespiteIssues">
          已知晓问题仍确认入库（不可强制：缺项目名称/唯一性冲突）
        </a-checkbox>
      </div>
      <div class="preview-actions">
        <a-button @click="previewOpen = false">取消</a-button>
        <a-button type="primary" :loading="loading" @click="confirmImport('merge')">合并入库</a-button>
        <a-button danger type="primary" :loading="loading" @click="confirmImport('replace')">
          全量替换入库
        </a-button>
      </div>
    </a-modal>

    <a-drawer
      v-model:open="editorOpen"
      title="项目信息维护"
      width="720"
      :body-style="{ paddingBottom: '80px' }"
      destroy-on-close
    >
      <a-spin :spinning="editorLoading">
        <a-alert
          type="info"
          show-icon
          message="在表单维护页内查看和编辑，保存后同步项目台账并写入审计日志。"
          style="margin-bottom: 16px"
        />
        <a-form layout="vertical" :model="editForm">
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="项目编号">
                <a-input v-model:value="editForm.projectNo" disabled />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="来源">
                <a-input :value="editForm.dataSource === 'FORM_MAINT' ? '表单维护导入' : '平台同步'" disabled />
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="项目名称" required>
                <a-input v-model:value="editForm.name" placeholder="请输入项目名称" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="级别">
                <a-select
                  v-model:value="editForm.levelCode"
                  allow-clear
                  :options="Object.entries(LEVEL_TEXT).map(([value, label]) => ({ value, label }))"
                />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="项目来源/渠道">
                <a-input v-model:value="editForm.channelName" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="项目类型">
                <a-input v-model:value="editForm.projectType" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="一级专业">
                <a-input v-model:value="editForm.major1" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="二级专业">
                <a-input v-model:value="editForm.major2" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="项目状态">
                <a-select
                  v-model:value="editForm.status"
                  :options="Object.entries(STATUS_TO_TEXT).map(([value, label]) => ({ value, label }))"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="管理/需求单位">
                <a-input v-model:value="editForm.manageOrgName" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="责任单位">
                <a-input v-model:value="editForm.leadOrgName" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="负责人">
                <a-input v-model:value="editForm.ownerName" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="验收状态">
                <a-input v-model:value="editForm.acceptStatus" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="开始年月">
                <a-input v-model:value="editForm.startYm" placeholder="例如 2026.09" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="结束年月">
                <a-input v-model:value="editForm.endYm" placeholder="例如 2028.12" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="总经费（万元）">
                <a-input-number v-model:value="editForm.totalFund" :min="0" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="国拨经费（万元）">
                <a-input-number v-model:value="editForm.nationalFund" :min="0" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="自筹经费（万元）">
                <a-input-number v-model:value="editForm.selfFund" :min="0" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="累计支出（万元）">
                <a-input-number v-model:value="editForm.expenseTotal" :min="0" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="2026年预算（万元）">
                <a-input-number v-model:value="editForm.yearBudget" :min="0" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
        </a-form>
      </a-spin>
      <template #extra>
        <a-space>
          <a-button @click="editorOpen = false">关闭</a-button>
          <a-button type="primary" :loading="editorSaving" @click="saveEditor">
            <SaveOutlined />保存修改
          </a-button>
        </a-space>
      </template>
    </a-drawer>

    <a-drawer v-model:open="colCfgOpen" title="列配置（官方总表 37 列）" width="400">
      <a-space style="margin-bottom: 12px">
        <a-button size="small" @click="visibleColKeys = [...FORM_TEMPLATE_DEFAULT_VISIBLE, 'dataSource']">
          恢复默认
        </a-button>
        <a-button size="small" type="primary" ghost @click="visibleColKeys = ALL_COLS.map((c) => c.key)">
          显示全部模板列
        </a-button>
      </a-space>
      <a-checkbox-group v-model:value="visibleColKeys" style="display: flex; flex-direction: column; gap: 8px">
        <a-checkbox v-for="c in ALL_COLS" :key="c.key" :value="c.key">{{ c.title }}</a-checkbox>
      </a-checkbox-group>
    </a-drawer>
  </div>
</template>

<style scoped>
.fm-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 4px;
}
.fm-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.fm-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.fm-banner {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  color: #389e0d;
  padding: 8px 12px;
  border-radius: 4px;
  margin-bottom: 12px;
  font-size: 13px;
}
.fm-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: stretch;
}
.stat {
  min-width: 88px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  padding: 8px 12px;
  text-align: center;
}
.stat .n {
  font-size: 18px;
  font-weight: 600;
  color: #1f1f1f;
  line-height: 1.2;
}
.stat .l {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
}
.stat.ok .n {
  color: #389e0d;
}
.stat.danger .n {
  color: #cf1322;
}
.col-btn {
  margin-left: auto;
}
.danger {
  color: #cf1322;
}
.upload-pane {
  padding: 24px 8px;
}
.preview-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
.preview-meta {
  margin-bottom: 12px;
  font-size: 13px;
  color: #595959;
}
.preview-stats {
  margin-top: 6px;
}
.preview-options {
  margin-top: 12px;
}
.pending-box {
  margin-top: 12px;
  padding: 10px 12px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 4px;
  font-size: 13px;
}
.report-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
  font-size: 13px;
}
</style>
