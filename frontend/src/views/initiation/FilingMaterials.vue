<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { declarationApi, dictApi, fileApi, projectApi } from '@/api/modules'
import { PROJECT_STATUS_TEXT } from '@/api/types'
import { useDictStore } from '@/stores/dict'
import FilingFlowChart from '@/components/filing/FilingFlowChart.vue'
import { buildFilingFlowOpts, type FilingFlowOpts } from '@/utils/filingFlow'
import {
  channelRequiredMaterials,
  mergePosts,
  personLabelOf,
  postsFromTeamMembers,
  unwrapDeclarationDetail,
} from '@/utils/flowLive'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'

const dictStore = useDictStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const project = ref<any>({})
const channel = ref<any>({})
const declaration = ref<any>({})
const materials = ref<any[]>([])
const filing = ref<any>({})
const projectId = ref<number>(0)
const pending = ref<Record<string, string>>({})

const posts = computed(() =>
  mergePosts(
    postsFromTeamMembers(project.value.teamMembers, {
      contact: project.value.createByName,
      leader: project.value.ownerName,
    }),
    declaration.value.posts,
  ),
)
const dutyProject = computed(() => ({
  ...project.value,
  posts: posts.value,
  teamMembers: project.value.teamMembers,
  ownerName: project.value.ownerName,
}))
const { can, guard } = useWorkDuty('filing', dutyProject)

const flow = computed<FilingFlowOpts>(() => {
  const st = String(project.value.status || '')
  return buildFilingFlowOpts({
    applyNo: declaration.value.applyNo || project.value.projectNo,
    name: declaration.value.name || project.value.name,
    channelName: channel.value.channelName || project.value.channelName,
    filingMaterial: channelRequiredMaterials(channel.value, 'filing').join('、'),
    channelFilingMaterial: channelRequiredMaterials(channel.value, 'filing').join('、'),
    filingMaterials: materials.value,
    materials: materials.value,
    projectStatus: st,
    projectStatusLabel: PROJECT_STATUS_TEXT[st] || undefined,
    status: declaration.value.status,
    filingStatus: filing.value.status,
    posts: posts.value as any,
  } as any)
})

const statusLabel = computed(
  () => flow.value.projectStatusLabel || PROJECT_STATUS_TEXT[project.value.status] || '—',
)

const doneOrSkip = computed(
  () => flow.value.panelStatus === 'DONE' || ['IMPLEMENTING', 'DELAYED', 'ACCEPTING', 'COMPANY_ACCEPTED', 'GOV_ACCEPTED', 'FINISHED'].includes(String(project.value.status || '')),
)

const eligible = computed(() => ['APPROVED', 'REPORTED'].includes(declaration.value.status))
const canUpload = computed(() => eligible.value && !doneOrSkip.value && filing.value.status !== 'AUDIT' && !!declaration.value.id && can.value.fill)
const canReview = computed(() => eligible.value && filing.value.status === 'AUDIT' && can.value.audit)

const leaderLabel = computed(() => {
  const m = (project.value.teamMembers || []).find((x: any) => x.roleName === '项目负责人')
  return personLabelOf(m) || posts.value.leader || project.value.ownerName || flow.value.ownerLabel || '—'
})

const adminLabel = computed(() => {
  const m = (project.value.teamMembers || []).find(
    (x: any) => x.roleName === '总部处室处长' || x.roleCode === 'HQ_DIRECTOR',
  )
  return personLabelOf(m) || posts.value.hqDirector || flow.value.auditorLabel || '—'
})

const filingAuditLabel = computed(() => {
  const who = adminLabel.value && adminLabel.value !== '—' ? adminLabel.value : '待指定'
  return `总部科技部科研项目处 · ${who}`
})

const uploadRows = computed(() => {
  const list = flow.value.attachments.length
    ? flow.value.attachments
    : (flow.value.channelMaterials || []).map((name, i) => ({
        code: `F_${name}`,
        name,
        uploaded: false,
      }))
  return list
})

const alertText = computed(() => {
  if (!eligible.value) return '申报审批尚未办结，当前不能进入立项备案材料办理。'
  if (doneOrSkip.value) {
    return `当前状态「${statusLabel.value}」，本阶段已办结或尚未进入立项备案。`
  }
  if (flow.value.panelStatus === 'RETURNED') {
    return '材料已退回补正，请重新上传齐套后提交总部科技部科研项目处审核备案。'
  }
  if (flow.value.currentTip) {
    return `当前节点：${flow.value.currentTip}`
  }
  return '请按本渠道必传清单上传原件后提交审核。'
})

async function resolveDeclaration(overview: any) {
  const decl = overview.declaration
  const pid = project.value
  if (decl?.id) {
    const detail = await declarationApi.detail(decl.id)
    const unwrapped = unwrapDeclarationDetail(detail.data)
    declaration.value = { ...decl, ...unwrapped.declaration }
    filing.value = (detail.data as any)?.filing || filing.value
    materials.value = unwrapped.materials.length ? unwrapped.materials : overview.declarationMaterials || []
    if (!materials.value.length) {
      const mats = await declarationApi.materials(decl.id)
      materials.value = (mats.data as any[]) || []
    }
    return
  }
  const qDecl = Number(route.query.declarationId || 0)
  if (qDecl) {
    const detail = await declarationApi.detail(qDecl)
    const unwrapped = unwrapDeclarationDetail(detail.data)
    declaration.value = unwrapped.declaration || {}
    filing.value = (detail.data as any)?.filing || filing.value
    materials.value = unwrapped.materials
    return
  }
  if (pid.name) {
    const res = await declarationApi.page({ keyword: pid.name, page: 1, size: 20 })
    const records = (res.data as any)?.records || []
    const hit =
      records.find((r: any) => r.name === pid.name) ||
      records.find((r: any) => r.applyNo && r.applyNo === pid.projectNo)
    if (hit?.id) {
      const detail = await declarationApi.detail(hit.id)
      const unwrapped = unwrapDeclarationDetail(detail.data)
      declaration.value = { ...hit, ...unwrapped.declaration }
      materials.value = unwrapped.materials
    }
  }
}

async function load() {
  loading.value = true
  try {
    const qPid = Number(route.query.projectId || route.params.id || 0)
    const qDecl = Number(route.query.declarationId || 0)
    if (qPid) {
      projectId.value = qPid
      const res = await projectApi.overview(qPid)
      const ov = res.data || {}
      project.value = ov.project || {}
      channel.value = ov.channel || {}
      filing.value = ov.filing || {}
      if (project.value.channelId && !(channel.value.filingMaterial || channel.value.declareMaterial)) {
        const fromStore = dictStore.channels.find((c) => c.id === project.value.channelId)
        if (fromStore) channel.value = { ...channel.value, ...fromStore }
        else {
          const ch = await dictApi.channel(project.value.channelId)
          channel.value = { ...channel.value, ...(ch.data || {}) }
        }
      }
      await resolveDeclaration(ov)
    } else if (qDecl) {
      const detail = await declarationApi.detail(qDecl)
      const unwrapped = unwrapDeclarationDetail(detail.data)
      declaration.value = unwrapped.declaration || {}
      materials.value = unwrapped.materials
      filing.value = (detail.data as any)?.filing || {}
      const channelId = declaration.value.channelId
      if (channelId) {
        try {
          const ch = await dictApi.channel(channelId)
          channel.value = ch.data || {}
        } catch {
          /* ignore */
        }
      }
      project.value = {
        name: declaration.value.name,
        projectNo: declaration.value.applyNo,
        status: eligible.value ? 'FILING' : 'DECLARING',
        channelName: declaration.value.channelName || channel.value.channelName,
        ownerName: declaration.value.applicant,
        ...((detail.data as any)?.project || {}),
      }
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await dictStore.loadChannels().catch(() => undefined)
  await load()
})

async function uploadFilingMaterial(options: any, row: { code: string; name: string }) {
  const file = options?.file
  if (!file || !declaration.value.id) return
  pending.value = { ...pending.value, [row.code]: file.name || '上传中' }
  try {
    const form = new FormData()
    form.append('file', file)
    const uploaded = (await fileApi.upload(form, 'filing')).data as any
    await declarationApi.uploadMaterial(declaration.value.id, {
      fieldCode: row.code,
      fieldName: row.name,
      fileName: uploaded.fileName || file.name,
      fileUrl: uploaded.fileUrl,
      fileSize: uploaded.fileSize || file.size,
    })
    message.success(`${row.name} 已上传`)
    options.onSuccess?.(uploaded)
    const mats = await declarationApi.materials(declaration.value.id)
    materials.value = (mats.data as any[]) || []
    pending.value = { ...pending.value, [row.code]: '' }
  } catch (e: any) {
    options.onError?.(e)
    message.error(e?.message || `${row.name} 上传失败`)
  }
}

function fileHint(row: { code: string; uploaded?: boolean; fileName?: string }) {
  const p = pending.value[row.code]
  if (p) return p
  if (row.fileName) return row.fileName
  if (row.uploaded) return '已上传'
  return '未选择文件'
}

function materialUrl(row: { code: string; name: string }) {
  return materials.value.find(m => m.fieldCode === row.code || m.fieldName === row.name)?.fileUrl || ''
}

async function submitAudit() {
  if (submitting.value || !canUpload.value) return
  if (!guard('submit')) return
  if (!declaration.value.id) {
    message.warning('未关联申报单，无法提交')
    return
  }
  const missing = uploadRows.value.filter((r) => !r.uploaded && !pending.value[r.code])
  if (missing.length) {
    message.warning(`请先选择：${missing.map((m) => m.name).join('、')}`)
    return
  }
  submitting.value = true
  try {
    const res = await declarationApi.submitFiling(declaration.value.id)
    const createdProjectId = Number((res.data as any)?.projectId || 0)
    if (createdProjectId) projectId.value = createdProjectId
    message.success('已提交总部科技部科研项目处审核备案，备案通过后台账转为实施中')
    await load()
  } finally {
    submitting.value = false
  }
}

function reviewFiling(pass: boolean) {
  if (!canReview.value || submitting.value) return
  Modal.confirm({
    title: pass ? '确认备案审核通过？' : '确认退回负责人补正？',
    content: pass ? '审核通过后项目进入实施阶段。' : '由负责人补充材料后重新提交。',
    onOk: async () => {
      submitting.value = true
      try {
        await declarationApi.filing(declaration.value.id, { pass })
        message.success(pass ? '备案审核通过，已进入实施阶段' : '已退回负责人补正')
        await load()
      } finally { submitting.value = false }
    },
  })
}

function back() {
  if (projectId.value) router.push(`/overview/detail/${projectId.value}`)
  else router.push('/initiation/filing')
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">立项支撑材料填报</h2>
    <div class="page-desc">
      申报审签/线上报备办结后，由项目负责人上传本渠道必传原件，提交总部科技部科研项目处审核备案。
    </div>
    <WorkDutyBar code="filing" :project="dutyProject" />

    <a-alert
      :message="alertText"
      :type="doneOrSkip ? 'success' : flow.panelStatus === 'RETURNED' ? 'warning' : 'info'"
      show-icon
      style="margin-bottom: 16px"
    />

    <a-spin :spinning="loading">
      <a-card title="项目信息" :body-style="{ padding: '16px 20px' }" style="margin-bottom: 16px">
        <a-descriptions :column="2" size="small" :label-style="{ width: '140px', color: '#8c8c8c' }">
          <a-descriptions-item label="项目名称">{{ project.name || declaration.name || '—' }}</a-descriptions-item>
          <a-descriptions-item label="项目编号">{{ project.projectNo || declaration.applyNo || '—' }}</a-descriptions-item>
          <a-descriptions-item label="当前状态">{{ statusLabel }}</a-descriptions-item>
          <a-descriptions-item label="立项渠道">{{ channel.channelName || project.channelName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="项目负责人">{{ leaderLabel }}</a-descriptions-item>
          <a-descriptions-item label="总部备案审核">{{ filingAuditLabel }}</a-descriptions-item>
        </a-descriptions>
      </a-card>

      <a-card title="本阶段流程图" :body-style="{ padding: '20px' }" style="margin-bottom: 16px">
        <FilingFlowChart :flow="flow" hide-status-chip current-green />
      </a-card>

      <a-card v-if="uploadRows.length && eligible" title="立项支撑材料（必传原件）" :body-style="{ padding: '16px 20px' }">
        <div class="mat-list">
          <div v-for="row in uploadRows" :key="row.code" class="mat-row">
            <div class="mat-name">
              <b>{{ row.name }}</b>
              <span class="mat-fmt">PDF / Word / 图片</span>
            </div>
            <a-upload
              v-if="canUpload"
              :show-upload-list="false"
              accept=".pdf,.doc,.docx,.png,.jpg,.jpeg"
              :custom-request="(options: any) => uploadFilingMaterial(options, row)"
            >
              <a-button>选择文件</a-button>
            </a-upload>
            <span class="mat-file" :class="{ empty: !row.uploaded && !pending[row.code] }">
              <a v-if="materialUrl(row)" :href="materialUrl(row)" target="_blank" rel="noopener">{{ fileHint(row) }}</a>
              <template v-else>{{ fileHint(row) }}</template>
            </span>
            <span class="mat-flag" :class="{ ok: row.uploaded }">{{ row.uploaded ? '已选择' : '未选择' }}</span>
          </div>
        </div>
      </a-card>

      <div class="page-actions">
        <a-button @click="back">返回项目详情</a-button>
        <a-button v-if="canReview" :disabled="submitting" danger @click="reviewFiling(false)">退回补正</a-button>
        <a-button v-if="canReview" :loading="submitting" type="primary" @click="reviewFiling(true)">审核通过并备案</a-button>
        <a-button
          v-if="canUpload"
          type="primary"
          :loading="submitting"
          :disabled="!can.submit"
          @click="submitAudit"
        >
          提交总部备案审核
        </a-button>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.mat-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mat-row {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 40px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.mat-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.mat-name {
  width: 220px;
  flex-shrink: 0;
}
.mat-name b {
  display: block;
  font-size: 14px;
  color: #1f1f1f;
  font-weight: 600;
}
.mat-fmt {
  font-size: 12px;
  color: #8c8c8c;
}
.mat-file {
  flex: 1;
  font-size: 13px;
  color: #262626;
}
.mat-file.empty {
  color: #8c8c8c;
}
.mat-flag {
  width: 64px;
  text-align: right;
  font-size: 13px;
  color: #ff4d4f;
}
.mat-flag.ok {
  color: #389e0d;
}
.page-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
</style>
