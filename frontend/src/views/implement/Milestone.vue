<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { fileApi, milestoneApi, projectApi } from '@/api/modules'
import type { MilestoneProjectBoard, MilestoneTodo } from '@/api/types'
import { isSilentAuthError } from '@/api/request'
import StatusTag from '@/components/StatusTag.vue'
import ImplementFlowDialog from '@/components/implement/ImplementFlowDialog.vue'
import { useRouter } from 'vue-router'
import { dueText, fmtAmount, fmtDate } from '@/utils/format'
import { implClosePath, implCompilePath } from '@/utils/implementFlow'
import { downloadAuthenticatedFile } from '@/utils/authFile'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const user = useUserStore()

const annualYear = ref(new Date().getFullYear())
const loading = ref(false)
const summary = ref<any>({ todo: 0, compile: 0, close: 0, yellow: 0, red: 0, total: 0, done: 0 })
const todos = ref<MilestoneTodo[]>([])
const boards = ref<MilestoneProjectBoard[]>([])
const todoTab = ref<'ALL' | 'COMPILE' | 'COMPILE_AUDIT' | 'CLOSE_AUDIT' | 'CLOSE'>('ALL')
const keyword = ref('')
const focusProjectId = ref<number>()

const open = ref(false)
const editing = ref<any>(null)
const form = reactive<any>({ projectId: undefined, name: '', year: annualYear.value, planDate: '', budget: 0 })
const flowOpen = ref(false)
const overviewData = ref<any>(null)
const detailOpen = ref(false)
const detail = ref<any>(null)
const annualSaving = ref(false)
const dutyProject = computed(() => {
  const b = boards.value[0]
  return b ? { ownerName: b.ownerName, id: b.projectId } : null
})
const { can, guard } = useWorkDuty('milestone_compile', dutyProject)

const columns = [
  { title: '#', key: 'idx', width: 56 },
  { title: '里程碑', dataIndex: 'name', width: 240 },
  { title: '计划完成', dataIndex: 'planDate', width: 150 },
  { title: '状态', dataIndex: 'colorStatus', width: 130 },
  { title: '完成/剩余', key: 'remain', width: 140 },
  { title: '佐证/交付物', dataIndex: 'evidence', width: 260 },
  { title: '操作', key: 'action', width: 220, fixed: 'right' as const },
]

const filteredTodos = computed(() => {
  const list = todos.value.filter((t) => todoTab.value === 'ALL' || t.taskType === todoTab.value)
  const q = keyword.value.trim()
  if (!q) return list
  return list.filter((t) => `${t.projectNo}${t.projectName}${t.milestoneName}`.includes(q))
})

const visibleBoards = computed(() => {
  if (!focusProjectId.value) return boards.value
  const hit = boards.value.filter((b) => b.projectId === focusProjectId.value)
  return hit.length ? hit : boards.value
})

function digitsOnly(value?: string | number) {
  return String(value || '').replace(/\D/g, '')
}

function currentUserMatchesText(value?: string | number) {
  const text = String(value || '').trim()
  const myNo = digitsOnly(user.employeeNo)
  const myName = String(user.realName || '').trim()
  return (!!myNo && digitsOnly(text).includes(myNo)) || (!!myName && text.includes(myName))
}

function isProjectOwnerMember(m: any) {
  const code = String(m?.roleCode || '')
  const role = String(m?.roleName || '')
  return code === 'PROJECT_LEADER' || role === '项目负责人' || role.includes('项目负责人')
}

function currentUserOwns(target?: any) {
  if (!target) return false
  const members = target.teamMembers || target.members || []
  if (members.some((m: any) => isProjectOwnerMember(m) && (currentUserMatchesText(m.employeeNo) || currentUserMatchesText(m.userName || m.realName || m.name || m.label)))) {
    return true
  }
  return currentUserMatchesText(target.ownerName || target.owner)
}

function canCloseRow(row: any, board?: any) {
  return currentUserOwns(row) || currentUserOwns(board)
}

function requireRowOwner(row: any) {
  if (canCloseRow(row)) return true
  message.warning('仅项目团队负责人可上传销项材料')
  return false
}
async function loadBoard() {
  loading.value = true
  try {
    const res = await milestoneApi.board({ year: annualYear.value })
    const data = (res.data || {}) as any
    summary.value = data.summary || {}
    todos.value = data.todos || []
    boards.value = data.projects || []
  } finally {
    loading.value = false
  }
}

onMounted(loadBoard)

function todoTagText(t: MilestoneTodo) {
  if (t.taskType === 'COMPILE') return '待编制'
  if (t.taskType === 'COMPILE_AUDIT') return '清单待审'
  if (t.taskType === 'CLOSE_AUDIT') return '销项待审'
  return warnText(t.colorStatus)
}

function todoTagColor(t: MilestoneTodo) {
  if (t.taskType === 'COMPILE') return 'blue'
  if (t.taskType === 'COMPILE_AUDIT') return 'orange'
  if (t.taskType === 'CLOSE_AUDIT') return 'processing'
  return t.status !== 'DONE' && !!t.planDate && dayjs(t.planDate).isBefore(dayjs(), 'day') ? 'red' : t.colorStatus === 'YELLOW' ? 'orange' : 'processing'
}

function warnText(color?: string) {
  if (color === 'GREEN') return '已完成'
  if (color === 'YELLOW') return '临期预警'
  if (color === 'RED') return '逾期告警'
  return '正常推进'
}

function jumpTo(todo: MilestoneTodo, view = false) {
  focusProjectId.value = todo.projectId
  const path = todo.taskType === 'COMPILE' || todo.taskType === 'COMPILE_AUDIT'
    ? implCompilePath(todo.projectId, todo.milestoneId)
    : implClosePath(todo.projectId, todo.milestoneId)
  router.push(view ? `${path}${path.includes('?') ? '&' : '?'}view=1` : path)
}

async function loadDetail(id: number) {
  try {
    const res = await milestoneApi.detail(id)
    detail.value = res.data
    detailOpen.value = true
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '加载详情失败')
  }
}

async function openMaterial(material: any) {
  try {
    await downloadAuthenticatedFile(material?.fileUrl, material?.fileName)
  } catch (e: any) {
    message.error(e?.message || '附件下载失败')
  }
}

function onCreate(projectId?: number) {
  editing.value = null
  Object.assign(form, {
    projectId: projectId || boards.value[0]?.projectId,
    name: '',
    year: annualYear.value,
    planDate: '',
    budget: 0,
  })
  open.value = true
}

function onEdit(row: any) {
  editing.value = row
  Object.assign(form, {
    projectId: row.projectId,
    name: row.name,
    year: row.year,
    planDate: row.planDate,
    budget: row.budget,
  })
  open.value = true
}

async function onSave() {
  if (!form.projectId || !form.name) {
    message.warning('请填写项目与里程碑名称')
    return
  }
  if (editing.value) await milestoneApi.update(editing.value.id, form)
  else await milestoneApi.create(form)
  message.success('保存成功')
  open.value = false
  loadBoard()
}

async function saveAnnual(board: MilestoneProjectBoard) {
  if (!guard('fill')) return
  annualSaving.value = true
  try {
    await projectApi.saveAnnualPlan(board.projectId, {
      year: annualYear.value,
      annualGoal: board.annualGoal,
      planContent: board.planContent,
    })
    message.success('年度目标已保存')
    loadBoard()
  } finally {
    annualSaving.value = false
  }
}

async function uploadEvidence(options: any, row: any) {
  if (!requireRowOwner(row)) return
  try {
    const data = new FormData()
    data.append('file', options.file)
    const uploaded = (await fileApi.upload(data, 'evidence')).data as any
    await milestoneApi.saveMaterial(row.id || row.milestoneId, {
      fieldCode: 'EVIDENCE',
      fieldName: '节点完成佐证材料',
      fileName: uploaded.fileName,
      fileUrl: uploaded.fileUrl,
      fileSize: uploaded.fileSize,
    })
    message.success(`已上传：${uploaded.fileName}`)
    options.onSuccess?.(uploaded)
    await loadBoard()
  } catch (e: any) {
    options.onError?.(e)
    if (!isSilentAuthError(e)) message.error(e.message || '材料上传失败')
  }
}

async function onClose(row: any) {
  if (!requireRowOwner(row)) return
  Modal.confirm({
    title: '确认闭环销项？',
    content: '销项前需已上传节点佐证材料，销项后节点自动转为绿色已完成状态。',
    onOk: async () => {
      try {
        await milestoneApi.close(row.id)
        message.success('节点已闭环销项')
        loadBoard()
      } catch (e: any) {
        if (!isSilentAuthError(e)) message.error(e.message)
      }
    },
  })
}

async function onDelay(row: any) {
  try {
    await milestoneApi.delay(row.id)
  } catch (e: any) {
    message.warning(e.message)
  }
}

async function openFlow(projectId: number) {
  try {
    const res = await projectApi.overview(projectId)
    overviewData.value = res.data
    flowOpen.value = true
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '加载实施流转失败')
  }
}

async function removeRow(row: any) {
  Modal.confirm({
    title: '删除该节点？',
    onOk: async () => {
      await milestoneApi.remove(row.id)
      message.success('已删除')
      loadBoard()
    },
  })
}
</script>

<template>
  <div class="page-container milestone-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">里程碑填报</h2>
        <div class="page-desc">
          按自然年度填报：年初编制里程碑节点（数量、名称、交付物类型名称、节点日期）；节点到期后上传佐证闭环销项。
          到期前 30 天黄色预警，超期转红；逾期禁止改日期，须走【项目变更】延期审批。
        </div>
      </div>
      <a-space>
        <span>填报年度</span>
        <a-input-number v-model:value="annualYear" :min="2020" :max="2100" @change="loadBoard" />
        <a-button @click="loadBoard">刷新</a-button>
      </a-space>
    </div>
    <details class="duty-disclosure"><summary>岗位职责与办理规则<span>展开查看</span></summary><WorkDutyBar code="milestone_compile" :project="dutyProject" /></details>

    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6"><div class="stat-card"><div class="label">我的待办</div><div class="value">{{ summary.todo || 0 }}</div></div></a-col>
      <a-col :span="6"><div class="stat-card"><div class="label">编制 / 增补</div><div class="value">{{ summary.compile || 0 }}</div></div></a-col>
      <a-col :span="6"><div class="stat-card"><div class="label">销项上传</div><div class="value">{{ summary.close || 0 }}</div></div></a-col>
      <a-col :span="6"><div class="stat-card warn"><div class="label">临期 / 逾期</div><div class="value">{{ (summary.yellow || 0) + (summary.red || 0) }}</div></div></a-col>
    </a-row>

    <a-card class="todo-workspace" title="我的待办" :body-style="{ padding: '8px 20px 16px' }" style="margin-bottom: 16px">
      <template #extra>
        <a-input-search v-model:value="keyword" allow-clear placeholder="项目编号 / 名称 / 节点" style="width: 280px" />
      </template>
      <a-tabs v-model:activeKey="todoTab">
        <a-tab-pane key="ALL" :tab="`全部 ${todos.length}`" />
        <a-tab-pane key="COMPILE" :tab="`编制/增补 ${summary.compile || 0}`" />
        <a-tab-pane key="COMPILE_AUDIT" :tab="`清单审核 ${summary.audit || 0}`" />
        <a-tab-pane key="CLOSE_AUDIT" :tab="`销项审核 ${summary.closeAudit || 0}`" />
        <a-tab-pane key="CLOSE" :tab="`销项上传 ${summary.close || 0}`" />
      </a-tabs>
      <a-empty v-if="!filteredTodos.length" description="当前筛选下暂无待办" />
      <div v-else class="todo-grid">
        <div v-for="(t, i) in filteredTodos" :key="`${t.taskType}-${t.projectId}-${t.milestoneId || i}`" class="todo-card" :class="{ overdue: t.status !== 'DONE' && !!t.planDate && dayjs(t.planDate).isBefore(dayjs(), 'day') }">
          <div class="todo-top">
            <a-tag :color="todoTagColor(t)">
              {{ todoTagText(t) }}
            </a-tag>
            <span class="todo-type">{{ t.typeLabel }}</span>
          </div>
          <div class="todo-identity"><div class="todo-name" :title="t.projectName">{{ t.projectName || t.projectNo || '未命名项目' }}</div><div class="todo-node">{{ t.milestoneName }}</div></div>
          <div class="todo-meta todo-number">项目编号 {{ t.projectNo || '—' }}</div>
          <div class="todo-meta todo-deadline">计划完成 {{ fmtDate(t.planDate) }} · {{ t.status === 'DONE' ? '已完成' : dueText(t.planDate) }}</div>
          <div class="todo-meta todo-owner">交付物 {{ t.materialCount || 0 }}/1 · 负责人 {{ t.ownerName || '—' }}</div>
          <div class="todo-actions">
            <a-button v-if="t.taskType === 'COMPILE'" size="small" @click="jumpTo(t, true)">查看</a-button>
            <a-button v-if="t.taskType === 'COMPILE'" type="primary" size="small" @click="jumpTo(t)">编制节点</a-button>
            <a-button v-if="t.taskType === 'COMPILE_AUDIT'" type="primary" size="small" @click="jumpTo(t)">审核清单</a-button>
            <a-button v-if="t.taskType === 'CLOSE_AUDIT'" type="primary" size="small" @click="jumpTo(t)">审核销项</a-button>
            <template v-else-if="t.taskType === 'CLOSE'">
              <a-button type="primary" size="small" @click="jumpTo(t)">进入销项上传</a-button>
              <a-button size="small" @click="loadDetail(t.milestoneId!)">查看详情</a-button>
            </template>
          </div>
        </div>
      </div>
    </a-card>

    <a-card
      v-for="board in visibleBoards"
      :key="board.projectId"
      :body-style="{ padding: '16px 20px' }"
      style="margin-bottom: 16px"
    >
      <div class="proj-head">
        <div>
          <div class="proj-title">{{ board.projectName }} <span>{{ board.projectNo }}</span></div>
          <div class="proj-sub">年度目标：{{ board.annualGoal || '尚未填写' }} · 节点 {{ board.msDone || 0 }}/{{ board.msTotal || 0 }}</div>
        </div>
        <a-space>
          <StatusTag :color="board.warnColor" :text="warnText(board.warnColor)" />
          <a-button @click="openFlow(board.projectId)">流程图</a-button>
          <a-button @click="router.push(`${implCompilePath(board.projectId)}&view=1`)">查看清单</a-button>
          <a-button @click="router.push(implCompilePath(board.projectId))">编制里程碑节点</a-button>
          <a-button type="primary" @click="onCreate(board.projectId)"><PlusOutlined />新增节点</a-button>
        </a-space>
      </div>
      <div class="annual-editor">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="本年度目标">
              <a-textarea v-model:value="board.annualGoal" :rows="2" placeholder="填写本年度总体目标" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="年度计划说明">
              <a-textarea v-model:value="board.planContent" :rows="2" placeholder="填写年度任务、考核指标及工作安排" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-button type="link" :loading="annualSaving" @click="saveAnnual(board)">保存年度目标</a-button>
      </div>
      <a-table
        :columns="columns"
        :data-source="board.milestones || []"
        :loading="loading"
        row-key="id"
        :scroll="{ x: 1200 }"
        :pagination="false"
      >
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'idx'">{{ index + 1 }}</template>
          <template v-else-if="column.dataIndex === 'name'">
            <div class="ms-name">{{ record.name }}</div>
            <div class="ms-plan">计划：节点预算 {{ fmtAmount(record.budget) }} 万元</div>
          </template>
          <template v-else-if="column.dataIndex === 'planDate'">{{ fmtDate(record.planDate) }}</template>
          <template v-else-if="column.dataIndex === 'colorStatus'"><StatusTag :color="record.colorStatus" /></template>
          <template v-else-if="column.key === 'remain'">
            <span :style="{ color: record.colorStatus === 'RED' ? '#f5222d' : record.colorStatus === 'YELLOW' ? '#faad14' : '#8c8c8c' }">
              {{ record.status === 'DONE' ? `完成 ${fmtDate(record.actualDate)}` : record.status === 'CLOSE_DEPT_AUDIT' ? '待项目承担部门负责人审核' : record.status === 'CLOSE_UNIT_AUDIT' ? '待单位科研管理部门负责人审核' : dueText(record.planDate) }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'evidence'">
            <div class="material-cell">
              <a
                v-for="m in record.materials || []"
                :key="m.id"
                href="#"
                @click.prevent="openMaterial(m)"
              >{{ m.fileName }}</a>
              <span v-if="!(record.materials || []).length" class="empty-mat">未上传</span>
            </div>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="2">
              <a-button type="link" size="small" @click="loadDetail(record.id)">查看详情</a-button>
              <a-divider type="vertical" />
              <template v-if="record.status === 'DONE'">
                <span class="archived">佐证已归档</span>
              </template>
              <template v-else-if="record.status === 'CLOSE_DEPT_AUDIT' || record.status === 'CLOSE_UNIT_AUDIT'">
                <a-tag color="processing">销项审核中</a-tag>
                <a-button type="link" size="small" @click="router.push(implClosePath(record.projectId || board.projectId, record.id))">查看审核</a-button>
              </template>
              <template v-else>
                <a-button v-if="canCloseRow(record, board)" type="link" size="small" @click="router.push(implClosePath(record.projectId || board.projectId, record.id))">上传材料</a-button>
                <a-dropdown>
                  <a-button type="link" size="small">更多</a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item key="edit" @click="onEdit(record)">编辑</a-menu-item>
                      <a-menu-item v-if="canCloseRow(record, board)" key="close" @click="onClose(record)">闭环销项</a-menu-item>
                      <a-menu-item key="delay" @click="onDelay(record)">延期申请</a-menu-item>
                      <a-menu-item key="del" @click="removeRow(record)">删除</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </template>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
    <a-empty v-if="!visibleBoards.length && !loading" description="当前年度暂无可见项目" />

    <a-modal v-model:open="open" :title="editing ? '编辑节点' : '新增节点'" @ok="onSave">
      <a-form layout="vertical">
        <a-form-item label="里程碑名称" required><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="年度"><a-input-number v-model:value="form.year" style="width: 100%" /></a-form-item>
        <a-form-item label="计划完成时间"><a-date-picker v-model:value="form.planDate" style="width: 100%" value-format="YYYY-MM-DD" /></a-form-item>
        <a-form-item label="节点预算（万元）"><a-input-number v-model:value="form.budget" style="width: 100%" /></a-form-item>
      </a-form>
    </a-modal>

    <a-drawer v-model:open="detailOpen" title="里程碑详情" width="40%">
      <a-descriptions v-if="detail" :column="1" bordered size="small">
        <a-descriptions-item label="节点">{{ detail.name }}</a-descriptions-item>
        <a-descriptions-item label="项目">{{ detail.projectName }}（{{ detail.projectNo }}）</a-descriptions-item>
        <a-descriptions-item label="负责人">{{ detail.ownerName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="年度">{{ detail.year }}</a-descriptions-item>
        <a-descriptions-item label="计划完成">{{ fmtDate(detail.planDate) }}</a-descriptions-item>
        <a-descriptions-item label="实际完成">{{ fmtDate(detail.actualDate) }}</a-descriptions-item>
        <a-descriptions-item label="节点预算">{{ fmtAmount(detail.budget) }} 万元</a-descriptions-item>
        <a-descriptions-item label="状态"><StatusTag :color="detail.colorStatus" /></a-descriptions-item>
        <a-descriptions-item label="滞后原因">{{ detail.lagReason || '—' }}</a-descriptions-item>
        <a-descriptions-item label="佐证材料">
          <div class="material-cell">
            <a v-for="m in detail.materials || []" :key="m.id" href="#" @click.prevent="openMaterial(m)">{{ m.fileName }}</a>
            <span v-if="!(detail.materials || []).length">未上传</span>
          </div>
        </a-descriptions-item>
      </a-descriptions>
    </a-drawer>
    <ImplementFlowDialog v-model:open="flowOpen" :overview="overviewData" />
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.todo-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.todo-card { border: 1px solid #e8e8e8; border-radius: 4px; padding: 16px; background: #fff; }
.todo-top { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.todo-type { color: #8c8c8c; font-size: 12px; }
.todo-name { font-weight: 600; margin-bottom: 8px; color: #262626; }
.todo-meta { color: #8c8c8c; font-size: 13px; line-height: 1.7; }
.todo-actions { margin-top: 12px; display: flex; gap: 8px; }
.proj-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 12px; }
.proj-title { font-size: 16px; font-weight: 600; color: #262626; }
.proj-title span { margin-left: 8px; color: #8c8c8c; font-weight: 400; font-size: 13px; }
.proj-sub { margin-top: 4px; color: #8c8c8c; font-size: 13px; }
.annual-editor { margin-bottom: 8px; padding: 8px 12px 0; border: 1px solid #d6e4ff; border-radius: 4px; background: #f5f8ff; }
.ms-name { font-weight: 500; }
.ms-plan { color: #8c8c8c; font-size: 12px; }
.material-cell { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
.material-cell a { max-width: 230px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #0064ef; }
.empty-mat, .archived { color: #8c8c8c; font-size: 12px; }
@media (max-width: 1100px) {
  .todo-grid { grid-template-columns: 1fr; }
  .page-head { flex-direction: column; }
}

/* Task rows align status, object identity and actions across projects. */
.milestone-page { color:#24364b; }
.page-head { margin-bottom:16px; }
.page-head > :first-child { min-width:0; }
.page-head > :deep(.ant-space) { flex-shrink:0;white-space:nowrap; }
.page-desc { max-width:850px;color:#687b8f;font-size:13px;line-height:1.7; }
.duty-disclosure { margin-bottom:14px;border:1px solid #e1e7ef;border-radius:6px;background:#fff; }
.duty-disclosure summary { padding:10px 14px;cursor:pointer;font-size:13px;color:#40546b; }
.duty-disclosure summary span { float:right;color:#75879a;font-size:12px; }
.duty-disclosure[open] summary { border-bottom:1px solid #edf1f6; }
.milestone-page :deep(.stat-card) { padding:14px 18px;border:1px solid #e1e7ef;border-radius:6px;box-shadow:none; }
.milestone-page :deep(.stat-card .value) { font-size:26px;line-height:1.3; }
.todo-workspace { border-color:#e1e7ef;border-radius:6px; }
.todo-workspace :deep(.ant-card-head) { min-height:52px; }
.todo-workspace :deep(.ant-tabs-nav) { margin-bottom:0; }
.todo-grid { display:flex;flex-direction:column;gap:0; }
.todo-card { display:grid;grid-template-columns:minmax(115px,.7fr) minmax(240px,2fr) minmax(190px,1.2fr) auto;column-gap:20px;row-gap:5px;align-items:center;padding:16px 10px;border:0;border-bottom:1px solid #e8edf3;border-radius:0; }
.todo-card:hover { background:#f6f9fd; }
.todo-card:last-child { border-bottom:0; }
.todo-top { grid-column:1;grid-row:1 / 3;flex-direction:column;align-items:flex-start;margin:0;gap:4px; }
.todo-identity { grid-column:2;grid-row:1;min-width:0; }
.todo-name { font-size:14px;color:#203e5c;line-height:1.5;margin:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
.todo-node { font-size:12px;color:#52677e;margin-top:3px; }
.todo-number { grid-column:2;grid-row:2;font-size:12px;color:#7b8b9d;overflow-wrap:anywhere; }
.todo-deadline { grid-column:3;grid-row:1;font-size:12px;color:#42566d; }
.todo-owner { grid-column:3;grid-row:2;font-size:12px;color:#687b8f; }
.todo-actions { grid-column:4;grid-row:1 / 3;margin:0;justify-content:flex-end;flex-wrap:wrap;max-width:190px; }
.todo-card.overdue .todo-deadline { color:#c73535; }
.todo-actions :deep(.ant-btn) { height:30px;padding:0 12px; }
@media(max-width:1200px){.todo-card{grid-template-columns:110px minmax(180px,1fr) 180px;column-gap:12px}.todo-deadline{grid-column:2;grid-row:3}.todo-owner{grid-column:2;grid-row:4}.todo-actions{grid-column:3;grid-row:1 / 5}.todo-top{grid-row:1 / 5}}
@media(max-width:700px){.todo-card{grid-template-columns:1fr;gap:8px}.todo-top,.todo-identity,.todo-number,.todo-deadline,.todo-owner,.todo-actions{grid-column:1;grid-row:auto}.todo-top{flex-direction:row}.todo-actions{justify-content:flex-start;max-width:none}.todo-workspace :deep(.ant-card-head-wrapper){flex-wrap:wrap;padding:10px 0;gap:8px}}

</style>

