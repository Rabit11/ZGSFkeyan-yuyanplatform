<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { projectApi } from '@/api/modules'
import { useDictStore } from '@/stores/dict'
import ProjectSelect from '@/components/ProjectSelect.vue'
import WorkDutyBar from '@/components/WorkDutyBar.vue'
import { useWorkDuty } from '@/composables/useWorkDuty'
import { LEVEL_TEXT } from '@/api/types'

const route = useRoute()
const dictStore = useDictStore()
const projectId = ref<number>()
const loading = ref(false)
const saving = ref(false)
const form = reactive<any>({})
const { can, guard } = useWorkDuty('basic', form)
const viewOnly = computed(() => String(route.query.view || '') === '1')
const formReadonly = computed(() => viewOnly.value || (!can.value.edit && !can.value.submit && !can.value.fill))

const TEAM_GROUPS = [
  { code: 'TECH', name: '技术团队', roles: ['项目负责人', '技术负责人', '项目主管'] },
  { code: 'EXPERT', name: '责任专家', roles: ['一级总师', '二级总师'] },
  { code: 'MGMT', name: '管理团队', roles: ['总部处室处长', '总部处室主管', '单位科技部长', '单位科技主管'] },
  { code: 'FIN', name: '财务团队', roles: ['总部财务主管', '单位财务部长', '单位财务主管'] },
]

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const res = await projectApi.detail(projectId.value)
    const d: any = res.data || {}
    Object.assign(form, d, { participants: d.participants || [], teamMembers: d.teamMembers || [], annualPlans: d.annualPlans || [] })
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await dictStore.loadChannels()
  await dictStore.load('PROJECT_LEVEL')
  const res = await projectApi.page({ page: 1, size: 1 })
  projectId.value = Number(route.query.projectId) || (res.data as any)?.records?.[0]?.id
  load()
})

async function save() {
  if (!can.value.edit && !can.value.submit) {
    guard('submit')
    return
  }
  saving.value = true
  try {
    await projectApi.update(projectId.value!, form)
    message.success('保存成功，审批通过后同步更新至项目台账')
  } finally {
    saving.value = false
  }
}

function addParticipant() {
  form.participants.push({ orgName: '', workContent: '' })
}
function removeParticipant(i: number) {
  form.participants.splice(i, 1)
}

function teamValue(groupCode: string, roleName: string, key: 'userName' | 'employeeNo') {
  return (form.teamMembers || []).find((m: any) => m.groupCode === groupCode && m.roleName === roleName)?.[key] || ''
}

function setTeamValue(groupCode: string, roleName: string, key: 'userName' | 'employeeNo', value: string) {
  form.teamMembers = form.teamMembers || []
  let member = form.teamMembers.find((m: any) => m.groupCode === groupCode && m.roleName === roleName)
  if (!member) {
    member = { groupCode, roleName, roleCode: roleName, userName: '', employeeNo: '' }
    form.teamMembers.push(member)
  }
  member[key] = value
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">项目团队补充缺失字段信息填报</h2>
    <div class="page-desc">
      系统自动回显立项数据，缺失字段由二级单位补充；同步归集年度里程碑、评估计划节点信息，
      实现项目动态信息汇总建档。<b>项目基本信息审批通过后才会同步更新至项目台账</b>。
    </div>
    <WorkDutyBar code="basic" :project="form" />

    <a-card :body-style="{ padding: '16px 20px' }">
      <a-space style="margin-bottom: 16px">
        <span>选择项目：</span>
        <ProjectSelect v-model="projectId" @change="load" />
      </a-space>

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
                  <a-select-option v-for="c in dictStore.channels" :key="c.id" :value="c.id">{{ c.channelName }}</a-select-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="一级专业"><a-input v-model:value="form.major1" /></a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="二级专业"><a-input v-model:value="form.major2" /></a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="项目状态">
                <a-select v-model:value="form.status">
                  <a-select-option value="IMPLEMENTING">进行中</a-select-option>
                  <a-select-option value="DELAYED">已延期</a-select-option>
                  <a-select-option value="ACCEPTING">验收中</a-select-option>
                  <a-select-option value="FINISHED">已完成</a-select-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="验收状态"><a-input v-model:value="form.acceptStatus" /></a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="牵头单位"><a-input v-model:value="form.leadOrgName" /></a-form-item>
            </a-col>
            <a-col :span="16">
              <a-form-item label="主要工作内容"><a-input v-model:value="form.mainWork" /></a-form-item>
            </a-col>
          </a-row>

          <a-divider orientation="left">
            参研单位
            <a-button type="link" size="small" @click="addParticipant">+ 新增参研单位</a-button>
          </a-divider>
          <a-row v-for="(pt, i) in form.participants || []" :key="i" :gutter="16" style="margin-bottom: 8px">
            <a-col :span="8"><a-input v-model:value="pt.orgName" :placeholder="`参研单位${i + 1}`" /></a-col>
            <a-col :span="14"><a-input v-model:value="pt.workContent" :placeholder="`主要工作内容${i + 1}`" /></a-col>
            <a-col :span="2"><a-button danger type="text" @click="removeParticipant(i)">删除</a-button></a-col>
          </a-row>

          <a-divider orientation="left">团队</a-divider>
          <a-row :gutter="16">
            <a-col :span="12" v-for="g in TEAM_GROUPS" :key="g.code">
              <div style="font-weight: 600; margin-bottom: 8px">{{ g.name }}</div>
              <a-row v-for="r in g.roles" :key="r" :gutter="8" style="margin-bottom: 8px" align="middle">
                <a-col :span="8"><span style="color: #5a6272">{{ r }}</span></a-col>
                <a-col :span="8">
                  <a-input placeholder="姓名" :value="teamValue(g.code, r, 'userName')" @update:value="(v: string) => setTeamValue(g.code, r, 'userName', v)" />
                </a-col>
                <a-col :span="8">
                  <a-input placeholder="工号" :value="teamValue(g.code, r, 'employeeNo')" @update:value="(v: string) => setTeamValue(g.code, r, 'employeeNo', v)" />
                </a-col>
              </a-row>
            </a-col>
          </a-row>

          <a-divider orientation="left">科研年度目标及计划</a-divider>
          <a-table size="small" row-key="id" :pagination="false" :data-source="form.annualPlans || []"
            :columns="[
              { title: '年度', dataIndex: 'year', width: 90 },
              { title: '年度目标', dataIndex: 'annualGoal' },
              { title: '计划内容', dataIndex: 'planContent', width: 200 },
              { title: '完成时间', dataIndex: 'dueDate', width: 130 },
            ]" />

          <div style="margin-top: 16px; text-align: right">
            <template v-if="!formReadonly">
              <a-button style="margin-right: 8px" @click="load">重置</a-button>
              <a-button type="primary" :loading="saving" :disabled="!can.edit && !can.submit" @click="save">保存并提交审核</a-button>
            </template>
          </div>
        </a-form>
      </a-spin>
    </a-card>
  </div>
</template>
