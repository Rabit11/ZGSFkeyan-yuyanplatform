<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { CheckCircleOutlined, CloseCircleOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import { acceptanceApi, projectApi } from '@/api/modules'
import ProjectSelect from '@/components/ProjectSelect.vue'
import AcceptFlowDialog from '@/components/acceptance/AcceptFlowDialog.vue'
import { buildAcceptFlowOptsFromOverview } from '@/utils/acceptFlow'
import { fmtDate } from '@/utils/format'
import { useWorkDuty } from '@/composables/useWorkDuty'
import WorkDutyBar from '@/components/WorkDutyBar.vue'

const projectId = ref<number>()
const route = useRoute()
const loading = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
const checks = ref<any[]>([])
const checking = ref(false)
const overview = ref<any>({})
const flowOpen = ref(false)
const dutyProject = computed(() => overview.value.project || { id: projectId.value, teamMembers: overview.value.teamMembers })
const { can, guard } = useWorkDuty('accept', dutyProject)

const LEVEL_NAME: Record<string, string> = { UNIT: '单位级验收', COMPANY: '公司级验收', NATIONAL: '国家级验收', LOCAL: '属地主管部门验收' }
const STATUS_TEXT: Record<string, string> = {
  NOT_STARTED: '未启动', CHECKING: '校验中', APPLYING: '申请中', ACCEPTING: '验收中', DONE: '已办结',
}

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const [res, ov] = await Promise.all([
      acceptanceApi.detail(projectId.value),
      projectApi.overview(projectId.value).catch(() => ({ data: {} })),
    ])
    detail.value = res.data || {}
    items.value = detail.value.items || []
    overview.value = ov.data || {}
    checks.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const res = await projectApi.page({ page: 1, size: 1 })
  projectId.value = Number(route.query.projectId) || (res.data as any)?.records?.[0]?.id
  load()
})

const grouped = computed(() => {
  const map: Record<string, any[]> = {}
  items.value.forEach((i) => {
    ;(map[i.levelCode] ||= []).push(i)
  })
  return Object.entries(map).map(([code, list]) => ({ code, name: LEVEL_NAME[code] || code, list }))
})

const allPassed = computed(() => checks.value.length > 0 && checks.value.every((c) => c.passed))

const flowOpts = computed(() =>
  buildAcceptFlowOptsFromOverview(
    {
      ...overview.value,
      project: overview.value.project || { id: projectId.value },
      acceptance: { ...detail.value, items: items.value },
    },
    { items: items.value },
  ),
)

async function runCheck() {
  checking.value = true
  try {
    const res = await acceptanceApi.check(projectId.value!)
    checks.value = (res.data as any[]) || []
    if (checks.value.every((c: any) => c.passed)) message.success('前置条件校验通过，可提交验收申请')
    else message.warning('存在未满足的前置条件，无法提交验收申请')
  } finally {
    checking.value = false
  }
}

async function submit() {
  if (!guard('submit')) return
  if (!allPassed.value) {
    message.error('请先通过前置条件校验')
    return
  }
  await acceptanceApi.submit(projectId.value!)
  message.success('验收申请已提交')
  load()
}

async function upload(item: any) {
  if (item.locked) return
  await acceptanceApi.materials(projectId.value!, { fieldCode: item.fieldCode })
  message.success(`${item.materialName} 已上传`)
  load()
}

async function finish() {
  if (!guard('audit')) return
  Modal.confirm({
    title: '确认验收办结？',
    content: '办结后系统开启 30 天倒计时，提醒开展参研单位评价。',
    onOk: async () => {
      await acceptanceApi.audit(projectId.value!, { pass: true, opinion: '验收合格，同意办结' })
      message.success('验收办结，已生成协作单位评价 30 天倒计时')
      load()
    },
  })
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">项目验收</h2>
    <div class="page-desc">
      系统根据项目来源<b>自动匹配验收表单层级</b>：国家级开放单位、公司、国家三级附件栏，地方级开放单位、属地主管部门两级，
      公司级仅开放单位、公司两级；<b>非本项目所需验收项页面锁定、不可编辑</b>。提交前系统强制校验前置条件。
      <a-button type="link" size="small" @click="flowOpen = true">查看项目验收流转</a-button>
    </div>
    <WorkDutyBar code="accept" :project="dutyProject" />

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <a-space><span>选择项目：</span><ProjectSelect v-model="projectId" @change="load" /></a-space>
        <a-space>
          <a-button :loading="checking" @click="runCheck"><SafetyCertificateOutlined />前置条件校验</a-button>
          <a-button @click="flowOpen = true">查看验收流转</a-button>
          <a-button type="primary" :disabled="!allPassed || !can.submit" @click="submit">提交验收申请</a-button>
          <a-button :disabled="detail.status === 'DONE' || !can.audit" @click="finish">验收办结</a-button>
        </a-space>
      </div>

      <a-spin :spinning="loading">
        <a-descriptions bordered size="small" :column="4" style="margin-bottom: 16px">
          <a-descriptions-item label="验收层级">{{ LEVEL_NAME[detail.acceptLevel] || detail.acceptLevel }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ STATUS_TEXT[detail.status] || detail.status }}</a-descriptions-item>
          <a-descriptions-item label="责任总师技术复核">
            <a-tag :color="detail.expertReview ? 'red' : 'default'">{{ detail.expertReview ? '需要' : '不需要' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="协作单位评价到期日">{{ fmtDate(detail.partnerDueDate) }}</a-descriptions-item>
        </a-descriptions>

        <!-- 前置校验结果 -->
        <a-alert
          v-if="checks.length"
          :type="allPassed ? 'success' : 'error'"
          show-icon
          style="margin-bottom: 16px"
          :message="allPassed ? '前置条件全部满足，可提交验收申请' : '存在未满足的前置条件，禁止提交验收申请'"
        >
          <template #description>
            <div v-for="c in checks" :key="c.key" style="line-height: 22px">
              <component :is="c.passed ? CheckCircleOutlined : CloseCircleOutlined"
                :style="{ color: c.passed ? '#52c41a' : '#f5222d', marginRight: '6px' }" />
              <b>{{ c.label }}</b>：{{ c.message }}
            </div>
          </template>
        </a-alert>

        <!-- 分级材料栏 -->
        <div v-for="g in grouped" :key="g.code" style="margin-bottom: 20px">
          <div style="font-weight: 600; margin-bottom: 8px">
            {{ g.name }}
            <a-tag v-if="g.list.some((i: any) => i.locked)" color="default" style="margin-left: 8px">
              本项目不适用 · 已锁定
            </a-tag>
            <a-tag v-else color="blue">本层级需上传</a-tag>
          </div>
          <a-table size="small" row-key="id" :pagination="false" :data-source="g.list"
            :columns="[
              { title: '材料名称', dataIndex: 'materialName', width: 280 },
              { title: '是否必需', dataIndex: 'locked', width: 130 },
              { title: '状态', dataIndex: 'status', width: 110 },
              { title: '操作', key: 'act', width: 100 },
            ]">
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'locked'">
                <a-tag :color="record.locked ? 'default' : 'blue'">{{ record.locked ? '锁定不可编辑' : '必需' }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'status'">
                <a-tag :color="record.status === 'UPLOADED' ? 'green' : 'orange'">{{ record.status === 'UPLOADED' ? '已上传' : '未上传' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'act'">
                <a-button type="link" size="small" :disabled="!!record.locked" @click="upload(record)">
                  {{ record.status === 'UPLOADED' ? '替换' : '上传' }}
                </a-button>
              </template>
            </template>
          </a-table>
        </div>
      </a-spin>
    </a-card>
    <AcceptFlowDialog v-model:open="flowOpen" :opts="flowOpts" :overview="overview" />
  </div>
</template>
