<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { projectApi } from '@/api/modules'
import { isSilentAuthError } from '@/api/request'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import ProjectSelect from '@/components/ProjectSelect.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'
import { PERSONNEL_ROSTER } from '@/constants/personnel'
import { PROJECT_STATUS_TEXT, type BasicDraft, type BasicDraftStatus } from '@/api/types'
import { fmtDate } from '@/utils/format'

const route = useRoute()
const dictStore = useDictStore()
const user = useUserStore()
const projectId = ref<number>()
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const auditing = ref(false)
const form = reactive<any>({})
const { can, guard } = useWorkDuty('basic', form)
const viewOnly = computed(() => String(route.query.view || '') === '1')

/* ------------------------------ 草稿 / 审批流 ------------------------------ */
function emptyDraft(): BasicDraft {
  return { status: 'NONE', flowNodes: [], auditTrail: [], payload: null, canEdit: false, canSubmit: false, canAudit: false }
}
const draft = ref<BasicDraft>(emptyDraft())
const DRAFT_STATUS_TEXT: Record<BasicDraftStatus, string> = {
  NONE: '无草稿',
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已退回',
}
const DRAFT_STATUS_COLOR: Record<BasicDraftStatus, string> = {
  NONE: 'default',
  DRAFT: 'default',
  APPROVING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
}
const MGMT_IDENTITIES = ['unitHead', 'unitStaff', 'hqHead', 'hqStaff', 'admin']
/** 管理团队身份：可直接编辑台账（PUT /projects/{id}） */
const isMgmt = computed(() => user.isAdmin || MGMT_IDENTITIES.includes(String(user.identityCode || '')))
const hasDraft = computed(() => ['DRAFT', 'APPROVING', 'REJECTED'].includes(draft.value.status))
const approving = computed(() => draft.value.status === 'APPROVING')
const showSaveDraft = computed(() => !viewOnly.value && !approving.value && !!draft.value.canEdit)
const showSubmit = computed(() => !viewOnly.value && !approving.value && !!draft.value.canSubmit)
const showAudit = computed(() => !viewOnly.value && approving.value && !!draft.value.canAudit)
/** 需求 V19.1：本页仅项目团队填报并走四级审批；管理团队/管理员不再提供“保存至台账”直写入口（台账维护走项目台账页） */
const showDirectSave = computed(() => false)
const canEditForm = computed(() => showSaveDraft.value || showDirectSave.value)
const formReadonly = computed(() => !canEditForm.value)
/** 项目状态 / 验收状态 / 预警：项目团队只读，仅管理团队直接维护台账时可改 */
const statusReadonly = computed(() => formReadonly.value || !showDirectSave.value)

const stepItems = computed(() => {
  const nodes = draft.value.flowNodes || []
  const trail = draft.value.auditTrail || []
  return nodes.map((n) => {
    const records = trail.filter((t) => t.node === n.code)
    const last = records[records.length - 1]
    let status: 'wait' | 'process' | 'finish' | 'error' = 'wait'
    let description = ''
    if (n.skipped) {
      status = 'wait'
      description = '自动跳过（团队未配置该岗位）'
    } else if (last && last.pass === false && draft.value.status === 'REJECTED') {
      status = 'error'
      description = `${last.actor || ''} 退回`
    } else if (last && last.pass) {
      status = 'finish'
      description = `${last.actor || ''} 通过`
    } else if (draft.value.flowNode === n.code && approving.value) {
      status = 'process'
      description = '审核中'
    }
    return { title: n.name, status, description }
  })
})

const auditOpen = ref(false)
const auditPass = ref(true)
const auditOpinion = ref('')

const TEAM_GROUPS = [
  { code: 'TECH', name: '技术团队', roles: ['项目负责人', '项目联系人', '技术负责人', '项目主管'] },
  { code: 'EXPERT', name: '责任专家', roles: ['一级总师', '二级总师'] },
  { code: 'MGMT', name: '管理团队', roles: ['项目承担部门负责人', '总部处室处长', '总部处室主管', '单位科技部长', '单位科技主管', '单位分管领导'] },
  { code: 'FIN', name: '财务团队', roles: ['总部财务主管', '单位财务部长', '单位财务主管'] },
]
const ROLE_CODE: Record<string, string> = {
  项目负责人: 'PROJECT_LEADER',
  项目联系人: 'PROJECT_CONTACT',
  技术负责人: 'TECH_LEADER',
  项目主管: 'PROJECT_SUPERVISOR',
  一级总师: 'L1_CHIEF',
  二级总师: 'L2_CHIEF',
  项目承担部门负责人: 'DEPT_HEAD',
  总部处室处长: 'HQ_DIRECTOR',
  总部处室主管: 'HQ_SUPERVISOR',
  单位科技部长: 'UNIT_MINISTER',
  单位科技主管: 'UNIT_SUPERVISOR',
  单位分管领导: 'UNIT_LEADER',
  总部财务主管: 'HQ_FINANCE',
  单位财务部长: 'UNIT_FIN_MINISTER',
  单位财务主管: 'UNIT_FIN_SUPERVISOR',
}
const personOptions = PERSONNEL_ROSTER.map((p) => ({ value: p.employeeNo, label: `${p.realName}（${p.employeeNo}）` }))
function personFilter(input: string, option: any) {
  return String(option?.label || '').includes(input.trim())
}

/** 草稿 payload 字段（ProjInfo 可维护字段 + participants + teamMembers） */
const DRAFT_FIELDS = [
  'name', 'goal', 'startDate', 'endDate', 'levelCode', 'filingDept', 'channelId', 'leadOrgName', 'mainWork',
  'manageOrgName', 'bureauOffice', 'projectType', 'major1', 'major2', 'totalFund', 'ownerName',
]

function resetForm(src: any) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, src, {
    participants: (src.participants || []).map((x: any) => ({ ...x })),
    teamMembers: (src.teamMembers || []).map((x: any) => ({ ...x })),
    annualPlans: src.annualPlans || [],
  })
}
function applyPayload(p: any) {
  if (!p) return
  Object.assign(form, p, {
    participants: (p.participants || []).map((x: any) => ({ ...x })),
    teamMembers: (p.teamMembers || []).map((x: any) => ({ ...x })),
    annualPlans: form.annualPlans || [],
  })
}
function buildPayload() {
  const p: any = {}
  DRAFT_FIELDS.forEach((k) => {
    if (form[k] !== undefined) p[k] = form[k]
  })
  p.participants = (form.participants || []).filter((x: any) => x.orgName || x.workContent)
  p.teamMembers = (form.teamMembers || []).filter((x: any) => x.userName || x.employeeNo)
  return p
}

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const res = await projectApi.detail(projectId.value)
    resetForm(res.data || {})
    await loadDraft()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '加载项目信息失败')
  } finally {
    loading.value = false
  }
}
async function loadDraft() {
  if (!projectId.value) return
  try {
    const res = await projectApi.basicDraft(projectId.value)
    const dr = (res.data || {}) as Partial<BasicDraft>
    draft.value = {
      ...emptyDraft(),
      ...dr,
      status: (dr.status || 'NONE') as BasicDraftStatus,
      flowNodes: dr.flowNodes || [],
      auditTrail: dr.auditTrail || [],
      payload: dr.payload || null,
    }
    if (hasDraft.value) applyPayload(draft.value.payload)
  } catch (e: any) {
    // 草稿接口不可用时退回工作定责判断（旧后端兼容）
    if (!isSilentAuthError(e)) message.warning(e.message || '草稿状态加载失败，按工作定责判定可办权限')
    draft.value = { ...emptyDraft(), canEdit: can.value.fill || can.value.edit, canSubmit: can.value.submit }
  }
}
onMounted(async () => {
  await dictStore.loadChannels()
  await dictStore.load('PROJECT_LEVEL')
  const qid = Number(route.query.projectId)
  if (qid) {
    projectId.value = qid
  } else {
    const res = await projectApi.page({ page: 1, size: 1 })
    projectId.value = (res.data as any)?.records?.[0]?.id
  }
  load()
})

async function saveDraft(silent = false) {
  if (!projectId.value) return false
  if (!draft.value.canEdit) {
    guard('fill')
    return false
  }
  saving.value = true
  try {
    await projectApi.saveBasicDraft(projectId.value, buildPayload())
    if (!silent) {
      message.success('草稿已保存，可继续编辑或提交审批')
      await loadDraft()
    }
    return true
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '保存草稿失败')
    return false
  } finally {
    saving.value = false
  }
}
async function submitDraft() {
  if (!projectId.value) return
  if (!(await saveDraft(true))) return
  submitting.value = true
  try {
    await projectApi.submitBasicDraft(projectId.value)
    message.success('已提交审批，审批通过后同步至项目台账')
    await loadDraft()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '提交审批失败')
  } finally {
    submitting.value = false
  }
}
async function directSave() {
  if (!projectId.value) return
  saving.value = true
  try {
    await projectApi.update(projectId.value, {
      ...buildPayload(),
      status: form.status,
      acceptStatus: form.acceptStatus,
      warnColor: form.warnColor,
    })
    message.success('已保存至项目台账')
    load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}
function openAudit(pass: boolean) {
  auditPass.value = pass
  auditOpinion.value = pass ? '同意' : ''
  auditOpen.value = true
}
async function submitAudit() {
  if (!projectId.value) return
  if (!auditPass.value && !auditOpinion.value.trim()) {
    message.warning('退回请填写审批意见')
    return
  }
  auditing.value = true
  try {
    await projectApi.auditBasicDraft(projectId.value, { pass: auditPass.value, opinion: auditOpinion.value.trim() })
    message.success(auditPass.value ? '已审核通过' : '已退回项目团队修改')
    auditOpen.value = false
    await load()
  } catch (e: any) {
    if (!isSilentAuthError(e)) message.error(e.message || '审批失败')
  } finally {
    auditing.value = false
  }
}

function addParticipant() {
  form.participants = form.participants || []
  form.participants.push({ orgName: '', workContent: '' })
}
function removeParticipant(i: number) {
  form.participants.splice(i, 1)
}

function teamMember(groupCode: string, roleName: string) {
  return (form.teamMembers || []).find((m: any) => m.groupCode === groupCode && m.roleName === roleName)
}
function teamNo(groupCode: string, roleName: string): string | undefined {
  const m = teamMember(groupCode, roleName)
  if (!m) return undefined
  if (m.employeeNo) return String(m.employeeNo)
  return PERSONNEL_ROSTER.find((p) => p.realName === String(m.userName || '').trim())?.employeeNo
}
function setTeam(groupCode: string, roleName: string, employeeNo?: string) {
  form.teamMembers = form.teamMembers || []
  let member = teamMember(groupCode, roleName)
  if (!employeeNo) {
    if (member) {
      member.userName = ''
      member.employeeNo = ''
    }
    if (roleName === '项目负责人') form.ownerName = ''
    return
  }
  const hit = PERSONNEL_ROSTER.find((p) => p.employeeNo === employeeNo)
  if (!member) {
    member = { groupCode, roleName, roleCode: ROLE_CODE[roleName] || roleName, userName: '', employeeNo: '' }
    form.teamMembers.push(member)
  }
  member.userName = hit?.realName || ''
  member.employeeNo = employeeNo
  if (roleName === '项目负责人') form.ownerName = member.userName
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">项目基本信息</h2>
    <div class="page-desc">
      系统自动回显立项数据，缺失字段由二级单位补充；同步归集年度里程碑、评估计划节点信息，
      实现项目动态信息汇总建档。<b>项目团队修改先保存为草稿并提交审批，全部审批通过后才会同步更新至项目台账</b>。
    </div>
    <WorkDutyBar code="basic" :project="form" />

    <a-card :body-style="{ padding: '16px 20px' }">
      <a-space style="margin-bottom: 16px">
        <span>选择项目：</span>
        <ProjectSelect v-model="projectId" @change="load" />
      </a-space>

      <div v-if="hasDraft || draft.status === 'APPROVED'" class="draft-bar">
        <div class="draft-bar__head">
          <a-tag :color="DRAFT_STATUS_COLOR[draft.status]">{{ DRAFT_STATUS_TEXT[draft.status] }}</a-tag>
          <span v-if="draft.flowNodeName && approving">当前节点：{{ draft.flowNodeName }}</span>
          <span v-if="draft.submittedBy" class="draft-bar__meta">提交人 {{ draft.submittedBy }} · {{ fmtDate(draft.submittedAt, 'YYYY-MM-DD HH:mm') }}</span>
        </div>
        <a-steps v-if="stepItems.length" size="small" :items="stepItems" class="draft-steps" />
        <div v-if="draft.auditTrail.length" class="audit-trail">
          <div class="audit-trail__title">审批记录</div>
          <div v-for="(t, i) in draft.auditTrail" :key="i" class="audit-trail__row">
            <a-tag :color="t.pass ? 'success' : 'error'" style="margin: 0">{{ t.pass ? '通过' : '退回' }}</a-tag>
            <span class="audit-trail__node">{{ t.nodeName || t.node }}</span>
            <span>{{ t.actor }}<template v-if="t.actorNo">（{{ t.actorNo }}）</template></span>
            <span class="audit-trail__time">{{ fmtDate(t.time, 'YYYY-MM-DD HH:mm') }}</span>
            <span v-if="t.opinion" class="audit-trail__opinion">{{ t.opinion }}</span>
          </div>
        </div>
      </div>

      <a-spin :spinning="loading">
        <a-form layout="vertical" :model="form" :disabled="formReadonly">
          <a-divider orientation="left">项目所属信息</a-divider>
          <a-row :gutter="16">
            <a-col :span="6">
              <a-form-item label="项目编号"><a-input :value="form.projectNo" disabled /></a-form-item>
            </a-col>
            <a-col :span="18">
              <a-form-item label="项目名称"><a-input v-model:value="form.name" /></a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="立项部门"><a-input v-model:value="form.filingDept" /></a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="管理 / 需求单位"><a-input v-model:value="form.manageOrgName" /></a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="项目类型"><a-input v-model:value="form.projectType" /></a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="项目目标"><a-textarea v-model:value="form.goal" :rows="2" /></a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="开始时间"><a-date-picker v-model:value="form.startDate" style="width: 100%" value-format="YYYY-MM-DD" /></a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="结束时间"><a-date-picker v-model:value="form.endDate" style="width: 100%" value-format="YYYY-MM-DD" /></a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="项目层级">
                <a-select v-model:value="form.levelCode">
                  <a-select-option v-for="o in dictStore.options('PROJECT_LEVEL')" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="渠道类别">
                <a-select v-model:value="form.channelId" show-search :filter-option="(i: string, o: any) => String(o.label).includes(i)">
                  <a-select-option v-for="c in dictStore.channels" :key="c.id" :value="c.id" :label="c.channelName">{{ c.channelName }}</a-select-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="项目来源 / 渠道流程"><a-input v-model:value="form.bureauOffice" placeholder="司局 / 处室或渠道流程说明" /></a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="总经费（万元）"><a-input-number v-model:value="form.totalFund" :min="0" :precision="2" style="width: 100%" /></a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="一级专业"><a-input v-model:value="form.major1" /></a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="二级专业"><a-input v-model:value="form.major2" /></a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="项目状态">
                <a-select v-if="!statusReadonly" v-model:value="form.status">
                  <a-select-option value="IMPLEMENTING">进行中</a-select-option>
                  <a-select-option value="DELAYED">已延期</a-select-option>
                  <a-select-option value="ACCEPTING">验收中</a-select-option>
                  <a-select-option value="FINISHED">已完成</a-select-option>
                </a-select>
                <a-input v-else :value="PROJECT_STATUS_TEXT[form.status] || form.status || '—'" disabled />
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="验收状态"><a-input v-model:value="form.acceptStatus" :disabled="statusReadonly" /></a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="预警状态">
                <div class="readonly-cell"><StatusTag :color="form.warnColor" /></div>
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="牵头单位"><a-input v-model:value="form.leadOrgName" /></a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="主要工作内容"><a-input v-model:value="form.mainWork" /></a-form-item>
            </a-col>
          </a-row>

          <a-divider orientation="left">
            参研单位
            <a-button type="link" size="small" :disabled="formReadonly" @click="addParticipant">+ 新增参研单位</a-button>
          </a-divider>
          <a-row v-for="(pt, i) in form.participants || []" :key="i" :gutter="16" style="margin-bottom: 8px">
            <a-col :span="8"><a-input v-model:value="pt.orgName" :placeholder="`参研单位${i + 1}`" /></a-col>
            <a-col :span="14"><a-input v-model:value="pt.workContent" :placeholder="`主要工作内容${i + 1}`" /></a-col>
            <a-col :span="2">
              <a-popconfirm title="移除该参研单位？" ok-text="移除" cancel-text="取消" :disabled="formReadonly" @confirm="removeParticipant(i)">
                <a-button danger type="text" :disabled="formReadonly">删除</a-button>
              </a-popconfirm>
            </a-col>
          </a-row>
          <div v-if="!(form.participants || []).length" class="empty-hint">暂无参研单位</div>

          <a-divider orientation="left">团队</a-divider>
          <a-row :gutter="16">
            <a-col :span="12" v-for="g in TEAM_GROUPS" :key="g.code">
              <div style="font-weight: 600; margin-bottom: 8px">{{ g.name }}</div>
              <a-row v-for="r in g.roles" :key="r" :gutter="8" style="margin-bottom: 8px" align="middle">
                <a-col :span="8"><span style="color: #5a6272">{{ r }}</span></a-col>
                <a-col :span="16">
                  <a-select
                    :value="teamNo(g.code, r)"
                    :options="personOptions"
                    show-search
                    allow-clear
                    placeholder="选择人员（姓名 / 工号）"
                    :filter-option="personFilter"
                    style="width: 100%"
                    @change="(v: any) => setTeam(g.code, r, v ? String(v) : undefined)"
                  />
                </a-col>
              </a-row>
            </a-col>
          </a-row>

          <a-divider orientation="left">科研年度目标及计划</a-divider>
          <a-table size="small" row-key="id" :pagination="false" :data-source="form.annualPlans || []"
            :locale="{ emptyText: '暂无年度目标，请在里程碑填报页维护' }"
            :columns="[
              { title: '年度', dataIndex: 'year', width: 90 },
              { title: '年度目标', dataIndex: 'annualGoal' },
              { title: '计划内容', dataIndex: 'planContent', width: 200 },
              { title: '完成时间', dataIndex: 'dueDate', width: 130 },
            ]" />
        </a-form>
      </a-spin>

      <div class="action-bar">
        <div class="action-bar__status">
          <template v-if="viewOnly">只读查看</template>
          <template v-else-if="approving">审批中，表单锁定</template>
          <template v-else-if="draft.status === 'REJECTED'">草稿已退回，可修改后重新提交</template>
          <template v-else-if="draft.status === 'DRAFT'">草稿未提交</template>
          <template v-else-if="isMgmt">本页由项目团队填报并提交审批；管理团队在此只审核，台账维护请到“项目台账”</template>
          <template v-else-if="formReadonly">当前账号仅可查看</template>
        </div>
        <a-space>
          <a-button v-if="canEditForm" @click="load">重置</a-button>
          <a-button v-if="showSaveDraft" :loading="saving" @click="saveDraft()">保存草稿</a-button>
          <a-button v-if="showSubmit" type="primary" :loading="submitting" @click="submitDraft">提交审批</a-button>
          <a-button v-if="showDirectSave" type="primary" :loading="saving" @click="directSave">保存至台账</a-button>
          <a-button v-if="showAudit" danger @click="openAudit(false)">退回</a-button>
          <a-button v-if="showAudit" type="primary" @click="openAudit(true)">审核通过</a-button>
        </a-space>
      </div>
    </a-card>

    <a-modal v-model:open="auditOpen" :title="auditPass ? '审核通过' : '退回修改'" :confirm-loading="auditing" @ok="submitAudit">
      <a-form layout="vertical">
        <a-form-item label="审批意见" :required="!auditPass">
          <a-textarea v-model:value="auditOpinion" :rows="3" :placeholder="auditPass ? '可填写通过意见' : '请说明退回原因'" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.draft-bar {
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid #d6e4ff;
  border-radius: 4px;
  background: #f5f8ff;
}
.draft-bar__head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; color: #262626; }
.draft-bar__meta { color: #8c8c8c; font-size: 12px; }
.draft-steps { margin-bottom: 8px; }
.audit-trail { margin-top: 8px; border-top: 1px dashed #d6e4ff; padding-top: 8px; }
.audit-trail__title { font-weight: 600; margin-bottom: 6px; color: #262626; }
.audit-trail__row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; font-size: 13px; line-height: 1.8; }
.audit-trail__node { color: #0064ef; }
.audit-trail__time, .audit-trail__opinion { color: #8c8c8c; }
.readonly-cell { height: 32px; display: flex; align-items: center; }
.empty-hint { color: #8c8c8c; font-size: 12px; margin-bottom: 8px; }
.action-bar {
  position: sticky;
  bottom: 0;
  z-index: 5;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin: 16px -20px -16px;
  padding: 12px 20px;
  background: #fff;
  border-top: 1px solid var(--zgsf-border, #e8e8e8);
}
.action-bar__status { color: #8c8c8c; font-size: 13px; }
</style>
