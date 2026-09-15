<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { milestoneApi, projectApi } from '@/api/modules'
import type { MilestoneProjectBoard, MilestoneTodo } from '@/api/types'
import { isSilentAuthError } from '@/api/request'
import StatusTag from '@/components/StatusTag.vue'
import ImplementFlowDialog from '@/components/implement/ImplementFlowDialog.vue'
import { useRouter } from 'vue-router'
import { dueText, fmtAmount, fmtDate } from '@/utils/format'
import { implClosePath, implCompilePath } from '@/utils/implementFlow'
import { downloadAuthenticatedFile } from '@/utils/authFile'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { resolveWorkDuty } from '@/utils/workDuty'
import { WORK_ACTION_LABEL, type WorkAction } from '@/constants/workDuty'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const user = useUserStore()

const annualYear = ref(new Date().getFullYear())
const loading = ref(false)
const summary = ref<any>({ todo: 0, compile: 0, close: 0, audit: 0, closeAudit: 0, yellow: 0, red: 0, total: 0, done: 0 })
const todos = ref<MilestoneTodo[]>([])
const boards = ref<MilestoneProjectBoard[]>([])
const todoTab = ref<'ALL' | 'COMPILE' | 'COMPILE_AUDIT' | 'CLOSE_AUDIT' | 'CLOSE'>('ALL')
const keyword = ref('')
const focusProjectId = ref<number>()

/* 新增 / 编辑节点 */
const open = ref(false)
const saving = ref(false)
const editing = ref<any>(null)
const form = reactive<any>({ projectId: undefined, name: '', year: annualYear.value, planDate: '', budget: 0 })
const editLocked = computed(() => !!editing.value?.dateLocked)

/* 延期申请 */
const delayOpen = ref(false)
const delaySaving = ref(false)
const delayRow = ref<any>(null)
const delayForm = reactive({ newPlanDate: '', reason: '' })

/* 滞后原因 / 逾期销项 */
const lagOpen = ref(false)
const lagSaving = ref(false)
const lagMode = ref<'lag' | 'close'>('lag')
const lagRow = ref<any>(null)
const lagForm = reactive({ lagReason: '', lagMeasure: '' })

const flowOpen = ref(false)
const overviewData = ref<any>(null)
const detailOpen = ref(false)
const detail = ref<any>(null)
const annualSaving = ref<Record<number, boolean>>({})
const annualSubmitting = ref<Record<number, boolean>>({})

/* ------------------------------ 按项目分别判定办理权限 ------------------------------ */
function boardProject(board?: MilestoneProjectBoard | null) {
  return board ? { id: board.projectId, ownerName: board.ownerName } : null
}
function boardDuty(board?: MilestoneProjectBoard | null) {
  return resolveWorkDuty('milestone_compile', boardProject(board), {
    employeeNo: user.employeeNo,
    realName: user.realName,
    identityCode: user.identityCode,
    roles: user.roles,
  })
}
function boardCan(board: MilestoneProjectBoard | null | undefined, action: WorkAction) {
  return boardDuty(board)[action].can
}
function guardBoard(board: MilestoneProjectBoard | null | undefined, action: WorkAction) {
  const cell = boardDuty(board)[action]
  if (cell.can) return true
  message.warning(cell.reason || `当前账号不能${WORK_ACTION_LABEL[action]}`)
  return false
}
function boardOf(projectId?: number) {
  return boards.value.find((b) => b.projectId === Number(projectId))
}
/** 顶部定责条仅展示第一个项目，各项目操作时按各自 board 判定 */
const dutyProject = computed(() => boardProject(visibleBoards.value[0] || boards.value[0]))

const columns = [
  { title: '#', key: 'idx', width: 48 },
  { title: '里程碑', dataIndex: 'name', width: 210 },
  { title: '计划完成', dataIndex: 'planDate', width: 110 },
  { title: '预算（万元）', dataIndex: 'budget', width: 116, align: 'right' as const },
  { title: '状态', dataIndex: 'colorStatus', width: 112 },
  { title: '完成/剩余', key: 'remain', width: 150 },
  { title: '佐证/交付物', dataIndex: 'evidence', width: 190 },
  { title: '操作', key: 'action', width: 164, fixed: 'right' as const },
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

/* ------------------------------ 项目负责人判定（销项类操作） ------------------------------ */
function digitsOnly(value?: string | number) {
  return String(value || '').replace(/\D/g, '')
}
function nameOnly(value?: string | number) {
  return String(value || '').replace(/[（(].*$/, '').trim()
}
function currentUserMatchesText(value?: string | number) {
  const myNo = digitsOnly(user.employeeNo)
  const myName = String(user.realName || '').trim()
  const no = digitsOnly(value)
  const name = nameOnly(value)
  return (!!myNo && !!no && no === myNo) || (!!myName && !!name && name === myName)
}
function isProjectOwnerMember(m: any) {
  const code = String(m?.roleCode || '')
  const role = String(m?.roleName || '')
  return code === 'PROJECT_LEADER' || role === '项目负责人'
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
  if (user.isAdmin) return false
  return currentUserOwns(row) || currentUserOwns(board)
}
function requireRowOwner(row: any, board?: any) {
  if (canCloseRow(row, board)) return true
  message.warning('仅项目负责人可办理该节点的销项类操作')
  return false
}

/* ------------------------------ 行状态辅助 ------------------------------ */
function inAudit(row: any) {
  return row?.status === 'CLOSE_DEPT_AUDIT' || row?.status === 'CLOSE_UNIT_AUDIT'
}
function isOverdue(row: any) {
  if (!row || row.status === 'DONE') return false
  if (row.colorStatus === 'RED' || row.status === 'OVERDUE') return true
  return !!row.planDate && dayjs(row.planDate).isBefore(dayjs(), 'day')
}
function evidenceMaterials(row: any) {
  return ((row?.materials || []) as any[]).filter((m) => m.fieldCode !== 'PLAN_TEMPLATE')
}
/** 编辑：仅填报人、节点未完成/未在审核、且未进入清单基线（进基线后名称/预算走数据变更，日期走延期申请） */
function canEditRow(row: any, board?: any) {
  if (row.status === 'DONE' || inAudit(row) || row.baselinePlanDate) return false
  return board ? boardCan(board, 'fill') : true
}
function remainText(row: any) {
  if (row.status === 'DONE') return `完成 ${fmtDate(row.actualDate)}`
  if (row.status === 'CLOSE_DEPT_AUDIT') return '待项目承担部门负责人审核'
  if (row.status === 'CLOSE_UNIT_AUDIT') return '待单位科研管理部门负责人审核'
  return dueText(row.planDate)
}
/** 仅清单审核中锁定；存档后允许增补节点，增补的节点未进基线，需再次提交清单审核 */
function annualLocked(board: MilestoneProjectBoard) {
  return board.annualStatus === 'PENDING_AUDIT' || !!board.annualAuditPending
}
function hasUnbaselined(board: MilestoneProjectBoard) {
  return (board.milestones || []).some((m: any) => !m.baselinePlanDate && m.status !== 'DONE')
}
function canSubmitAnnual(board: MilestoneProjectBoard) {
  if (annualLocked(board) || !(board.milestones || []).length) return false
  const archived = board.annualStatus === 'DONE' || !!board.annualArchived
  return !archived || hasUnbaselined(board)
}

async function loadBoard() {
  loading.value = true
  try {
    const res = await milestoneApi.board({ year: annualYear.value })
    const data = (res.data || {}) as any
    summary.value = data.summary || {}
    todos.value = data.todos || []
    boards.value = data.projects || []
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '加载里程碑看板失败')
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

/* ------------------------------ 新增 / 编辑 ------------------------------ */
function onCreate(board: MilestoneProjectBoard) {
  if (!guardBoard(board, 'fill')) return
  if (annualLocked(board)) {
    message.warning(board.annualStatus === 'DONE' ? '本年度清单已存档，增补节点请走【项目变更】' : '清单审核中，暂不能新增节点')
    return
  }
  editing.value = null
  Object.assign(form, { projectId: board.projectId, name: '', year: annualYear.value, planDate: '', budget: 0 })
  open.value = true
}

function onEdit(row: any, board: MilestoneProjectBoard) {
  if (!guardBoard(board, 'fill')) return
  editing.value = row
  Object.assign(form, {
    projectId: row.projectId || board.projectId,
    name: row.name,
    year: row.year,
    planDate: row.planDate,
    budget: row.budget,
  })
  open.value = true
}

async function onSave() {
  if (!form.projectId || !String(form.name || '').trim()) {
    message.warning('请填写项目与里程碑名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      // 契约：PUT 只接受 { name, budget, year, planDate? }；dateLocked 时不传 planDate
      const payload: any = { name: String(form.name).trim(), budget: form.budget ?? 0, year: form.year }
      if (!editLocked.value && form.planDate) payload.planDate = form.planDate
      await milestoneApi.update(editing.value.id, payload)
    } else {
      if (!form.planDate) {
        message.warning('请填写计划完成时间')
        return
      }
      await milestoneApi.create({
        projectId: form.projectId,
        name: String(form.name).trim(),
        year: form.year,
        planDate: form.planDate,
        budget: form.budget ?? 0,
      })
    }
    message.success('保存成功')
    open.value = false
    loadBoard()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ------------------------------ 年度目标 / 清单提交 ------------------------------ */
async function saveAnnual(board: MilestoneProjectBoard) {
  if (!guardBoard(board, 'fill')) return
  annualSaving.value = { ...annualSaving.value, [board.projectId]: true }
  try {
    await projectApi.saveAnnualPlan(board.projectId, {
      year: annualYear.value,
      annualGoal: board.annualGoal,
      planContent: board.planContent,
    })
    message.success('年度目标已保存')
    loadBoard()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '保存年度目标失败')
  } finally {
    annualSaving.value = { ...annualSaving.value, [board.projectId]: false }
  }
}

function submitAnnual(board: MilestoneProjectBoard) {
  if (!guardBoard(board, 'submit')) return
  if (!(board.milestones || []).length) {
    message.warning('本年度尚无里程碑节点，请先编制')
    return
  }
  Modal.confirm({
    title: '提交本年度里程碑清单审核？',
    content: '提交后清单进入单位科研管理部门审核，审核期间节点与年度目标锁定；审核通过后节点计划日期固化为基线，后续只能通过延期变更调整。',
    onOk: async () => {
      annualSubmitting.value = { ...annualSubmitting.value, [board.projectId]: true }
      try {
        await projectApi.submitAnnualPlan(board.projectId, { year: annualYear.value })
        message.success('清单已提交审核，等待单位科研管理部门审核存档')
        loadBoard()
      } catch (e: any) {
        if (!isSilentAuthError(e)) message.error(e.message || '提交清单审核失败')
      } finally {
        annualSubmitting.value = { ...annualSubmitting.value, [board.projectId]: false }
      }
    },
  })
}

/* ------------------------------ 闭环销项 ------------------------------ */
function onClose(row: any, board: MilestoneProjectBoard) {
  if (!requireRowOwner(row, board)) return
  if (inAudit(row)) {
    message.info('该节点销项已提交审核，请等待审核结果')
    return
  }
  if (!evidenceMaterials(row).length) {
    message.warning('请先上传节点佐证材料（计划模板不计入佐证），再提交销项')
    return
  }
  if (isOverdue(row)) {
    openLag(row, 'close')
    return
  }
  Modal.confirm({
    title: '确认提交节点销项审核？',
    content: `将里程碑「${row.name}」提交项目承担部门负责人审核，通过后继续流转单位科研管理部门负责人。`,
    onOk: () => doClose(row, {}),
  })
}

async function doClose(row: any, data: { lagReason?: string; lagMeasure?: string }) {
  try {
    await milestoneApi.close(row.id, data)
    message.success('已提交销项审核（待项目承担部门负责人审核）')
    loadBoard()
    return true
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '提交销项失败')
    return false
  }
}

/* ------------------------------ 滞后原因 ------------------------------ */
function onLag(row: any, board: MilestoneProjectBoard) {
  if (!requireRowOwner(row, board)) return
  openLag(row, 'lag')
}
function openLag(row: any, mode: 'lag' | 'close') {
  lagRow.value = row
  lagMode.value = mode
  lagForm.lagReason = row.lagReason || ''
  lagForm.lagMeasure = row.lagMeasure || ''
  lagOpen.value = true
}
async function submitLag() {
  const row = lagRow.value
  if (!row) return
  if (!lagForm.lagReason.trim() || !lagForm.lagMeasure.trim()) {
    message.warning('请填写滞后原因与处理措施')
    return
  }
  lagSaving.value = true
  try {
    const data = { lagReason: lagForm.lagReason.trim(), lagMeasure: lagForm.lagMeasure.trim() }
    if (lagMode.value === 'close') {
      if (await doClose(row, data)) lagOpen.value = false
      return
    }
    await milestoneApi.lag(row.id, data)
    message.success('滞后原因已登记')
    lagOpen.value = false
    loadBoard()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '登记滞后原因失败')
  } finally {
    lagSaving.value = false
  }
}

/* ------------------------------ 延期申请 ------------------------------ */
function onDelay(row: any, board: MilestoneProjectBoard) {
  if (!guardBoard(board, 'fill')) return
  if (row.status === 'DONE') {
    message.info('已完成节点无需延期')
    return
  }
  delayRow.value = row
  delayForm.newPlanDate = ''
  delayForm.reason = ''
  delayOpen.value = true
}
function delayDisabledDate(d: Dayjs) {
  const base = delayRow.value?.planDate ? dayjs(delayRow.value.planDate) : dayjs()
  return !d.isAfter(base, 'day')
}
async function submitDelay() {
  const row = delayRow.value
  if (!row) return
  if (!delayForm.newPlanDate) {
    message.warning('请选择新的计划完成日期')
    return
  }
  if (row.planDate && !dayjs(delayForm.newPlanDate).isAfter(dayjs(row.planDate), 'day')) {
    message.warning('新计划日期必须晚于当前计划完成时间')
    return
  }
  if (!delayForm.reason.trim()) {
    message.warning('请填写延期理由')
    return
  }
  delaySaving.value = true
  try {
    const res = await milestoneApi.delay(row.id, { newPlanDate: delayForm.newPlanDate, reason: delayForm.reason.trim() })
    const data = (res.data || {}) as any
    delayOpen.value = false
    message.success(`延期变更单${data.changeNo ? ` ${data.changeNo}` : ''}已生成，请前往项目变更提交审批`)
    const pid = row.projectId || boardOf(row.projectId)?.projectId || ''
    router.push(`/implement/change?projectId=${pid}&changeId=${data.changeId || ''}`)
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '发起延期申请失败')
  } finally {
    delaySaving.value = false
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
  try {
    await milestoneApi.remove(row.id)
    message.success('已删除')
    loadBoard()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '删除失败')
  }
}
</script>

<template>
  <div class="page-container milestone-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">里程碑填报</h2>
        <div class="page-desc">
          按自然年度填报：年初编制里程碑节点（数量、名称、交付物类型名称、节点日期）；节点到期后上传佐证闭环销项。
          到期前 30 天黄色预警，超期转红；清单存档后计划日期进入基线，须走【延期申请】生成项目变更审批。
        </div>
      </div>
      <a-space>
        <span>填报年度</span>
        <a-input-number v-model:value="annualYear" :min="2020" :max="2100" @change="loadBoard" />
        <a-button @click="loadBoard">刷新</a-button>
      </a-space>
    </div>
    <details class="duty-disclosure"><summary>岗位职责与办理规则<span>展开查看</span></summary><WorkDutyBar code="milestone_compile" :project="dutyProject" /><div class="duty-note">各项目的保存、编制、审核与销项权限按项目分别判定。</div></details>

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
      <a-spin v-if="loading && !todos.length" style="display: block; padding: 24px 0" />
      <a-empty v-else-if="!loading && !filteredTodos.length" description="当前筛选下暂无待办" />
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
          <div class="proj-title">
            {{ board.projectName }} <span>{{ board.projectNo }}</span>
            <a-tag v-if="board.annualStatus === 'PENDING_AUDIT' || board.annualAuditPending" color="orange" style="margin-left: 8px">清单审核中</a-tag>
            <a-tag v-else-if="board.annualStatus === 'DONE' || board.annualArchived" color="green" style="margin-left: 8px">清单已存档</a-tag>
            <a-tag v-else-if="board.annualStatus === 'RETURN'" color="red" style="margin-left: 8px">清单已驳回</a-tag>
          </div>
          <div class="proj-sub">年度目标：{{ board.annualGoal || '尚未填写' }} · 节点 {{ board.msDone || 0 }}/{{ board.msTotal || 0 }}</div>
        </div>
        <a-space wrap>
          <StatusTag :color="board.warnColor" :text="warnText(board.warnColor)" />
          <a-button @click="openFlow(board.projectId)">流程图</a-button>
          <a-button @click="router.push(`${implCompilePath(board.projectId)}&view=1`)">查看清单</a-button>
          <a-button :disabled="annualLocked(board)" @click="router.push(implCompilePath(board.projectId))">编制里程碑节点</a-button>
          <a-button type="primary" :disabled="annualLocked(board) || !boardCan(board, 'fill')" @click="onCreate(board)"><PlusOutlined />新增节点</a-button>
        </a-space>
      </div>
      <div class="annual-editor">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="本年度目标">
              <a-textarea v-model:value="board.annualGoal" :rows="2" :disabled="annualLocked(board) || !!board.annualArchived || !boardCan(board, 'fill')" placeholder="填写本年度总体目标" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="年度计划说明">
              <a-textarea v-model:value="board.planContent" :rows="2" :disabled="annualLocked(board) || !!board.annualArchived || !boardCan(board, 'fill')" placeholder="填写年度任务、考核指标及工作安排" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-space>
          <a-button v-if="boardCan(board, 'fill') && !board.annualArchived" type="link" :disabled="annualLocked(board)" :loading="!!annualSaving[board.projectId]" @click="saveAnnual(board)">保存年度目标</a-button>
          <span v-if="board.annualArchived && boardCan(board, 'fill')" class="empty-mat">年度目标已存档，修改请走「数据变更」；可增补节点后再次提交清单审核</span>
          <a-button
            v-if="canSubmitAnnual(board) && boardCan(board, 'fill')"
            type="primary"
            size="small"
            :loading="!!annualSubmitting[board.projectId]"
            @click="submitAnnual(board)"
          >提交清单审核</a-button>
          <span v-else-if="!annualLocked(board) && boardCan(board, 'fill')" class="empty-mat">编制里程碑节点后可提交清单审核</span>
        </a-space>
      </div>
      <a-table
        :columns="columns"
        :data-source="board.milestones || []"
        :loading="loading"
        row-key="id"
        :scroll="{ x: 1100 }"
        :pagination="false"
        :locale="{ emptyText: '暂无里程碑，请先编制' }"
      >
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'idx'">{{ index + 1 }}</template>
          <template v-else-if="column.dataIndex === 'name'">
            <div class="ms-name">{{ record.name }}</div>
            <div v-if="record.dateLocked" class="ms-plan">基线 {{ fmtDate(record.baselinePlanDate || record.planDate) }}<template v-if="record.delayCount"> · 已延期 {{ record.delayCount }} 次</template></div>
          </template>
          <template v-else-if="column.dataIndex === 'planDate'">{{ fmtDate(record.planDate) }}</template>
          <template v-else-if="column.dataIndex === 'budget'">{{ fmtAmount(record.budget) }}</template>
          <template v-else-if="column.dataIndex === 'colorStatus'"><StatusTag :color="record.colorStatus" /></template>
          <template v-else-if="column.key === 'remain'">
            <span :style="{ color: record.colorStatus === 'RED' ? '#f5222d' : record.colorStatus === 'YELLOW' ? '#faad14' : '#8c8c8c' }">
              {{ remainText(record) }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'evidence'">
            <div class="material-cell">
              <a-tooltip v-for="m in record.materials || []" :key="m.id" :title="m.fileName">
                <a href="#" @click.prevent="openMaterial(m)">{{ m.fileName }}</a>
              </a-tooltip>
              <span v-if="!(record.materials || []).length" class="empty-mat">未上传</span>
            </div>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="0">
              <a-button type="link" size="small" @click="loadDetail(record.id)">查看</a-button>
              <a-button v-if="canEditRow(record, board)" type="link" size="small" @click="onEdit(record, board)">编辑</a-button>
              <a-dropdown v-if="record.status !== 'DONE'" :trigger="['click']">
                <a-button type="link" size="small">更多</a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item key="upload" @click="router.push(implClosePath(record.projectId || board.projectId, record.id))">上传材料</a-menu-item>
                    <a-menu-item key="close" :disabled="inAudit(record)" @click="onClose(record, board)">闭环销项</a-menu-item>
                    <a-menu-item key="delay" @click="onDelay(record, board)">延期申请</a-menu-item>
                    <a-menu-item key="lag" @click="onLag(record, board)">滞后原因</a-menu-item>
                    <a-menu-item v-if="record.canDelete" key="del" danger>
                      <a-popconfirm title="删除该节点？删除后不可恢复。" ok-text="删除" cancel-text="取消" @confirm="removeRow(record)">
                        <span class="menu-pop" @click.stop>删除</span>
                      </a-popconfirm>
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
              <span v-else class="archived">佐证已归档</span>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
    <a-empty v-if="!loading && !visibleBoards.length" description="当前年度暂无可见项目" />

    <a-modal v-model:open="open" :title="editing ? '编辑节点' : '新增节点'" :confirm-loading="saving" @ok="onSave">
      <a-form layout="vertical">
        <a-form-item label="里程碑名称" required><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="年度"><a-input-number v-model:value="form.year" style="width: 100%" /></a-form-item>
        <a-form-item
          label="计划完成时间"
          :required="!editing"
          :extra="editLocked ? '日期已进入基线，如需调整请走【延期申请】' : ''"
        >
          <a-date-picker v-model:value="form.planDate" :disabled="editLocked" style="width: 100%" value-format="YYYY-MM-DD" />
        </a-form-item>
        <a-form-item label="节点预算（万元）"><a-input-number v-model:value="form.budget" :min="0" style="width: 100%" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="delayOpen" title="延期申请" :confirm-loading="delaySaving" ok-text="生成延期变更单" @ok="submitDelay">
      <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        :message="`节点「${delayRow?.name || ''}」当前计划完成 ${fmtDate(delayRow?.planDate)}`"
        description="提交后系统生成一条「里程碑延期」项目变更草稿，需在项目变更页提交审批；审批通过后计划日期自动更新。"
      />
      <a-form layout="vertical">
        <a-form-item label="新计划完成日期" required>
          <a-date-picker v-model:value="delayForm.newPlanDate" :disabled-date="delayDisabledDate" style="width: 100%" value-format="YYYY-MM-DD" />
        </a-form-item>
        <a-form-item label="延期理由" required>
          <a-textarea v-model:value="delayForm.reason" :rows="3" placeholder="说明延期原因、影响及后续安排" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="lagOpen"
      :title="lagMode === 'close' ? '逾期节点销项 · 填写滞后原因' : '登记滞后原因'"
      :confirm-loading="lagSaving"
      :ok-text="lagMode === 'close' ? '提交销项审核' : '保存'"
      @ok="submitLag"
    >
      <a-alert
        v-if="lagMode === 'close'"
        type="warning"
        show-icon
        style="margin-bottom: 12px"
        :message="`节点「${lagRow?.name || ''}」已逾期，提交销项前须填写滞后原因与处理措施`"
      />
      <a-form layout="vertical">
        <a-form-item label="滞后原因" required>
          <a-textarea v-model:value="lagForm.lagReason" :rows="3" placeholder="说明节点滞后的主要原因" />
        </a-form-item>
        <a-form-item label="处理措施" required>
          <a-textarea v-model:value="lagForm.lagMeasure" :rows="3" placeholder="已采取或拟采取的追赶措施" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-drawer v-model:open="detailOpen" title="里程碑详情" width="40%">
      <a-descriptions v-if="detail" :column="1" bordered size="small">
        <a-descriptions-item label="节点">{{ detail.name }}</a-descriptions-item>
        <a-descriptions-item label="项目">{{ detail.projectName }}（{{ detail.projectNo }}）</a-descriptions-item>
        <a-descriptions-item label="负责人">{{ detail.ownerName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="年度">{{ detail.year }}</a-descriptions-item>
        <a-descriptions-item label="计划完成">{{ fmtDate(detail.planDate) }}</a-descriptions-item>
        <a-descriptions-item label="基线日期">{{ fmtDate(detail.baselinePlanDate) }}<a-tag v-if="detail.dateLocked" color="orange" style="margin-left: 8px">已锁定</a-tag></a-descriptions-item>
        <a-descriptions-item label="延期次数">{{ detail.delayCount || 0 }}</a-descriptions-item>
        <a-descriptions-item label="实际完成">{{ fmtDate(detail.actualDate) }}</a-descriptions-item>
        <a-descriptions-item label="节点预算">{{ fmtAmount(detail.budget) }} 万元</a-descriptions-item>
        <a-descriptions-item label="状态"><StatusTag :color="detail.colorStatus" /> <span class="empty-mat">{{ remainText(detail) }}</span></a-descriptions-item>
        <a-descriptions-item label="滞后原因">{{ detail.lagReason || '—' }}</a-descriptions-item>
        <a-descriptions-item label="处理措施">{{ detail.lagMeasure || '—' }}</a-descriptions-item>
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
.duty-note { margin: -8px 0 16px; color: #8c8c8c; font-size: 12px; }
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
.annual-editor { margin-bottom: 8px; padding: 8px 12px 8px; border: 1px solid #d6e4ff; border-radius: 4px; background: #f5f8ff; }
.ms-name { font-weight: 500; }
.ms-plan { color: #8c8c8c; font-size: 12px; }
.material-cell { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; max-width: 100%; }
.material-cell a { display: block; max-width: 210px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #0064ef; }
.empty-mat, .archived { color: #8c8c8c; font-size: 12px; }
.menu-pop { display: block; }
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

