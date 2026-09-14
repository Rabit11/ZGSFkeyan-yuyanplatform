<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { projectApi } from '@/api/modules'
import { WORK_ACTION_LABEL, WORK_DUTY_DEFS, type WorkAction, type WorkDutyCode } from '@/constants/workDuty'
import { useUserStore } from '@/stores/user'
import { myDutyHint, resolveWorkDuty } from '@/utils/workDuty'
import ProjectSelect from '@/components/ProjectSelect.vue'

const route = useRoute()
const user = useUserStore()
const projectId = ref<number>()
const project = ref<any>(null)
const stage = ref<string>('全部')
const loading = ref(false)

const stages = computed(() => ['全部', ...Array.from(new Set(WORK_DUTY_DEFS.map((d) => d.stage)))])
const actions: WorkAction[] = ['fill', 'submit', 'audit', 'view', 'edit']

async function loadProject() {
  if (!projectId.value) {
    project.value = null
    return
  }
  loading.value = true
  try {
    const res = await projectApi.detail(projectId.value)
    project.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const q = Number(route.query.projectId || 0)
  if (q) projectId.value = q
  loadProject()
})
watch(projectId, loadProject)

const rows = computed(() => {
  const actor = {
    employeeNo: user.employeeNo,
    realName: user.realName,
    identityCode: user.identityCode,
    roles: user.roles,
  }
  return WORK_DUTY_DEFS.filter((d) => stage.value === '全部' || d.stage === stage.value).map((d) => {
    const resolved = resolveWorkDuty(d.code as WorkDutyCode, project.value, actor)
    return {
      key: d.code,
      stage: d.stage,
      title: d.title,
      path: d.path,
      hint: myDutyHint(resolved),
      fill: resolved.fill.labels,
      submit: resolved.submit.labels,
      audit: resolved.audit.labels,
      view: resolved.view.labels,
      edit: resolved.edit.labels,
      canFill: resolved.fill.can,
      canSubmit: resolved.submit.can,
      canAudit: resolved.audit.can,
      canEdit: resolved.edit.can,
    }
  })
})

const columns = [
  { title: '阶段', dataIndex: 'stage', width: 80, fixed: 'left' as const },
  { title: '工作内容', dataIndex: 'title', width: 180, fixed: 'left' as const },
  { title: '填写', dataIndex: 'fill', width: 220 },
  { title: '提交', dataIndex: 'submit', width: 200 },
  { title: '审核', dataIndex: 'audit', width: 280 },
  { title: '查看', dataIndex: 'view', width: 180 },
  { title: '编辑', dataIndex: 'edit', width: 200 },
  { title: '您可办理', dataIndex: 'hint', width: 160, fixed: 'right' as const },
]
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">工作定责一览</h2>
    <div class="page-desc">
      每项工作内容指定到岗位，进入具体项目后解析为团队实名：谁填写、谁提交、谁审核、谁查看、谁编辑。
      公司领导只读；已点名的岗位仅该办理人可写；未点名时暂按任职身份办理，并提示待指定到人。
    </div>

    <div class="page-card" style="margin-bottom: 16px">
      <a-space wrap>
        <span>对照项目：</span>
        <ProjectSelect v-model="projectId" placeholder="选择后显示实名办理人" />
        <a-radio-group v-model:value="stage" button-style="solid">
          <a-radio-button v-for="s in stages" :key="s" :value="s">{{ s }}</a-radio-button>
        </a-radio-group>
        <a-tag v-if="project">{{ project.projectNo }} {{ project.name }}</a-tag>
        <a-tag v-else>未选项目：按下表岗位口径</a-tag>
      </a-space>
    </div>

    <a-spin :spinning="loading">
      <a-table
        size="middle"
        row-key="key"
        :columns="columns"
        :data-source="rows"
        :pagination="false"
        :scroll="{ x: 1400 }"
        :row-class-name="(r: any) => (r.canFill || r.canSubmit || r.canAudit || r.canEdit ? 'duty-row-mine' : '')"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="actions.includes(column.dataIndex as WorkAction)">
            <span :class="{ 'duty-mine': record['can' + (column.dataIndex as string).replace(/^./, (c: string) => c.toUpperCase())] && column.dataIndex !== 'view' }">
              {{ record[column.dataIndex as string] }}
            </span>
          </template>
        </template>
      </a-table>
    </a-spin>
  </div>
</template>

<style scoped>
:deep(.duty-row-mine) td {
  background: #f6faff;
}
.duty-mine {
  color: var(--zgsf-brand);
  font-weight: 600;
}
</style>
