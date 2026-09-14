<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  TableOutlined,
  DownloadOutlined,
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { userApi } from '@/api/modules'
import {
  DATA_SCOPE_TEXT,
  ROLE_TEXT,
  type DataScope,
  type FormMaintScope,
  type RoleCode,
  type SysUser,
} from '@/api/types'
import {
  DATA_SCOPE_OPTIONS,
  FORM_MAINT_OPTIONS,
  IDENTITY_LABELS,
  MEMBER_POST_OPTIONS,
  identityByLabel,
} from '@/constants/permission'
import { useUserStore } from '@/stores/user'
import { withAllOption } from '@/utils/filterOptions'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const rows = ref<SysUser[]>([])
const total = ref(0)
const query = reactive<any>({
  page: 1,
  size: 20,
  keyword: undefined,
  identity: undefined,
  dataScope: undefined,
})

const RANK_OPTIONS = ['工程师', '高级工程师', '研究员', '专家', '财务']
const ORG_OPTIONS = [
  { org: '中国商飞总部', depts: ['科研项目处', '科技管理部（总部办公室）', '财务部'] },
  { org: '上飞院', depts: ['科技管理部', '科研项目处', '财务部', '总体气动部'] },
]

const roleOptions = (Object.keys(ROLE_TEXT) as RoleCode[]).map((k) => ({
  label: ROLE_TEXT[k],
  value: k,
}))
const scopeOptions = DATA_SCOPE_OPTIONS
const identityOptions = IDENTITY_LABELS.map((i) => ({ label: i, value: i }))

const emptyForm = (): any => ({
  username: '',
  realName: '',
  employeeNo: '',
  orgName: undefined,
  deptName: undefined,
  identity: undefined,
  identityCode: undefined,
  projectPost: '暂无项目角色',
  rankTitle: '工程师',
  dataScope: 'SELF' as DataScope,
  finishAuth: 0,
  formMaintScope: '' as FormMaintScope,
  formMaintChannels: '',
  formMaintTypes: '',
  declareResultAccess: 0,
  email: '',
  phone: '',
  roles: [] as RoleCode[],
  status: 1,
})

const openBasic = ref(false)
const openPost = ref(false)
const openSpecial = ref(false)
const openFinish = ref(false)
const editing = ref<SysUser | null>(null)
const form = reactive<any>(emptyForm())
const finishSelected = ref<number[]>([])
const declareSelected = ref<number[]>([])

const deptOptions = computed(() => {
  const hit = ORG_OPTIONS.find((o) => o.org === form.orgName)
  return hit?.depts || []
})

const needChannel = computed(() => form.formMaintScope === 'channel')
const needType = computed(() => form.formMaintScope === 'type')

async function load() {
  loading.value = true
  try {
    const res = await userApi.page(query)
    rows.value = (res.data as any)?.records || []
    total.value = (res.data as any)?.total || 0
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function syncRoles() {
  if (!ensureAdmin()) return
  loading.value = true
  try {
    const res: any = await userApi.syncIdentityRoles()
    const fixed = res?.data?.fixed ?? 0
    message.success(fixed ? `已按任职身份同步 ${fixed} 人的功能角色` : '全部成员权限已与任职身份一致')
    load()
  } finally {
    loading.value = false
  }
}

async function exportPersonnel() {
  if (!ensureAdmin()) return
  loading.value = true
  try {
    const res = await userApi.page({
      ...query,
      page: 1,
      size: 500,
    })
    const list: SysUser[] = (res.data as any)?.records || []
    if (!list.length) {
      message.warning('没有可导出的人员数据')
      return
    }
    const header = [
      '工号',
      '姓名',
      '主单位',
      '主部门',
      '任职身份',
      '项目岗位',
      '职称',
      '数据权限',
      '表单维护',
      '立项结果查看',
      '办结授权',
      '状态',
      '邮箱',
    ]
    const body = list.map((r) => [
      r.employeeNo,
      r.realName,
      r.orgName,
      r.deptName,
      r.identity,
      r.projectPost,
      r.rankTitle,
      DATA_SCOPE_TEXT[r.dataScope as DataScope] || r.dataScope || '',
      formMaintLabel(r.formMaintScope),
      (r.declareResultAccess || r.identity === '系统管理员') ? '有' : '无',
      r.finishAuth ? '有' : '无',
      r.status === 1 ? '启用' : '停用',
      r.email || '',
    ])
    const csv =
      '\uFEFF' +
      [header, ...body]
        .map((line) => line.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
        .join('\n')
    const link = document.createElement('a')
    link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    link.download = `人员信息_${list.length}人_${dayjs().format('YYYYMMDD-HHmmss')}.csv`
    link.click()
    URL.revokeObjectURL(link.href)
    message.success(`已导出 ${list.length} 人`)
  } finally {
    loading.value = false
  }
}

function ensureAdmin() {
  if (!userStore.isAdmin) {
    message.error('仅系统管理员可进行人员权限配置')
    return false
  }
  return true
}

function openCreate() {
  if (!ensureAdmin()) return
  editing.value = null
  Object.assign(form, emptyForm())
  openBasic.value = true
}

function openBasicEdit(row: SysUser) {
  if (!ensureAdmin()) return
  editing.value = row
  Object.assign(form, emptyForm(), row, {
    roles: [...(row.roles || [])],
    formMaintScope: row.formMaintScope || '',
    declareResultAccess: row.declareResultAccess ?? 0,
  })
  openBasic.value = true
}

function openPostEdit(row: SysUser) {
  if (!ensureAdmin()) return
  editing.value = row
  Object.assign(form, emptyForm(), row, {
    roles: [...(row.roles || [])],
    formMaintScope: row.formMaintScope || '',
  })
  openPost.value = true
}

function openSpecialEdit(row: SysUser) {
  if (!ensureAdmin()) return
  editing.value = row
  Object.assign(form, emptyForm(), row, {
    formMaintScope: row.formMaintScope || '',
    formMaintChannels: row.formMaintChannels || '',
    formMaintTypes: row.formMaintTypes || '',
    declareResultAccess: row.declareResultAccess ?? 0,
    finishAuth: row.finishAuth ?? 0,
  })
  openSpecial.value = true
}

function onIdentityChange(label: string) {
  const def = identityByLabel(label)
  if (!def) return
  form.identityCode = def.code
  form.dataScope = def.dataScope
  form.roles = [...def.roles]
  if (def.formMaintScope) {
    form.formMaintScope = def.formMaintScope
  } else if (form.formMaintScope === 'hq' && def.code !== 'admin' && def.code !== 'hqHead') {
    form.formMaintScope = ''
  }
  if (def.code === 'admin') {
    form.declareResultAccess = 1
    form.finishAuth = 1
  }
}

async function saveBasic() {
  if (!form.realName || !form.employeeNo) {
    message.warning('请填写姓名与工号')
    return
  }
  if (!form.identity) {
    message.warning('请选择任职身份，系统将据此分配功能权限')
    return
  }
  const def = identityByLabel(form.identity)
  if (!def) {
    message.warning('任职身份无法匹配权限模板，请从下拉列表选择')
    return
  }
  // 任职身份驱动角色 / 数据范围，确保新增成员具备相应功能权限
  onIdentityChange(form.identity)
  const payload = { ...form, identityCode: def.code, roles: [...def.roles], dataScope: def.dataScope }
  if (!payload.username) payload.username = payload.employeeNo
  if (editing.value) {
    await userApi.update(editing.value.id, payload)
    await userApi.assignRoles(editing.value.id, payload.roles)
  } else {
    const res: any = await userApi.create(payload)
    const newId = res?.data
    if (newId) await userApi.assignRoles(newId, payload.roles)
  }
  message.success(`保存成功，已分配角色：${payload.roles.join('、')}`)
  openBasic.value = false
  load()
}

async function savePost() {
  if (!editing.value) return
  if (!form.identity) {
    message.warning('请选择任职身份，系统将据此分配功能权限')
    return
  }
  onIdentityChange(form.identity)
  const def = identityByLabel(form.identity)!
  await userApi.update(editing.value.id, {
    identity: form.identity,
    identityCode: def.code,
    projectPost: form.projectPost,
    rankTitle: form.rankTitle,
    dataScope: form.dataScope || def.dataScope,
    orgName: form.orgName,
    deptName: form.deptName,
    roles: [...def.roles],
    formMaintScope: form.formMaintScope,
    declareResultAccess: form.declareResultAccess,
    finishAuth: form.finishAuth,
  })
  await userApi.assignRoles(editing.value.id, [...def.roles])
  message.success(`任职信息已更新，功能角色：${def.roles.join('、')}`)
  openPost.value = false
  load()
}

async function saveSpecial() {
  if (!editing.value) return
  if (needChannel.value && !String(form.formMaintChannels || '').trim()) {
    message.warning('指定渠道维护须填写授权渠道编码')
    return
  }
  if (needType.value && !String(form.formMaintTypes || '').trim()) {
    message.warning('指定项目类型维护须填写授权项目类型')
    return
  }
  const isAdminUser =
    form.identity === '系统管理员' || editing.value.roles?.includes('ADMIN')
  await userApi.update(editing.value.id, {
    formMaintScope: form.formMaintScope || '',
    formMaintChannels: form.formMaintChannels || '',
    formMaintTypes: form.formMaintTypes || '',
    declareResultAccess: isAdminUser ? 1 : form.declareResultAccess ? 1 : 0,
    finishAuth: form.finishAuth ? 1 : 0,
  })
  message.success('专项授权已更新')
  openSpecial.value = false
  load()
}

function resetPwd(row: SysUser) {
  if (!ensureAdmin()) return
  Modal.confirm({
    title: '重置密码',
    content: `确认将【${row.realName} / ${row.employeeNo}】密码重置为工号 ${row.employeeNo}？旧会话将全部失效。`,
    okText: '确认重置',
    onOk: async () => {
      await userApi.resetPassword(row.id)
      message.success(`密码已重置为工号 ${row.employeeNo}`)
    },
  })
}

function remove(row: SysUser) {
  if (!ensureAdmin()) return
  if (row.id === userStore.userId) {
    message.warning('当前管理员不能删除自己')
    return
  }
  if (row.employeeNo === '100001' || row.roles?.includes('ADMIN')) {
    const adminCount = rows.value.filter((u) => u.roles?.includes('ADMIN')).length
    if (adminCount <= 1) {
      message.warning('系统中唯一管理员不能被删除')
      return
    }
  }
  Modal.confirm({
    title: '确认删除成员？',
    content: `删除后不可恢复：${row.realName}（${row.employeeNo}）`,
    okType: 'danger',
    onOk: async () => {
      await userApi.remove(row.id)
      message.success('已删除')
      load()
    },
  })
}

function openFinishAuth() {
  if (!ensureAdmin()) return
  finishSelected.value = rows.value.filter((u) => u.finishAuth).map((u) => u.id)
  declareSelected.value = rows.value
    .filter((u) => u.declareResultAccess || u.roles?.includes('ADMIN'))
    .map((u) => u.id)
  openFinish.value = true
}

async function saveFinishAuth() {
  const finishSet = new Set(finishSelected.value)
  const declareSet = new Set(declareSelected.value)
  await Promise.all(
    rows.value.map(async (u) => {
      const forceDeclare = u.roles?.includes('ADMIN')
      await userApi.update(u.id, {
        finishAuth: finishSet.has(u.id) ? 1 : 0,
        declareResultAccess: forceDeclare || declareSet.has(u.id) ? 1 : 0,
      })
    }),
  )
  message.success('专项权限批量授权已更新')
  openFinish.value = false
  load()
}

function formMaintLabel(scope?: string) {
  return FORM_MAINT_OPTIONS.find((o) => o.value === (scope || ''))?.label || '无'
}

const columns = [
  { title: '工号', dataIndex: 'employeeNo', width: 96, fixed: 'left' },
  { title: '姓名', dataIndex: 'realName', width: 96, fixed: 'left' },
  { title: '主单位 / 主部门', key: 'orgDept', width: 240 },
  { title: '任职身份', dataIndex: 'identity', width: 180 },
  { title: '数据权限', dataIndex: 'dataScope', width: 110 },
  { title: '表单维护', key: 'formMaint', width: 120 },
  { title: '立项结果', key: 'declareResult', width: 90 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '操作', key: 'act', width: 320, fixed: 'right' },
]
</script>

<template>
  <div class="page-container">
    <div class="page-card member-page">
      <div class="member-head">
        <div>
          <div class="cfg-tip">配置中心 · 成员任职与专项授权 · 仅系统管理员可配置</div>
          <h2 class="page-title" style="margin-bottom: 0">
            成员管理
            <span class="sub">（任职身份 / 数据范围 / 专项授权）</span>
            <a-tag color="blue" style="margin-left: 8px">{{ total }} 人</a-tag>
          </h2>
        </div>
        <a-space wrap>
          <a-button @click="load"><ReloadOutlined />刷新</a-button>
          <a-button @click="exportPersonnel"><DownloadOutlined />人员信息导出</a-button>
          <a-button @click="router.push('/system/role-matrix')">
            <TableOutlined />项目岗位权限矩阵
          </a-button>
          <a-button @click="syncRoles"><SafetyCertificateOutlined />按任职同步权限</a-button>
          <a-button @click="openFinishAuth"><SafetyCertificateOutlined />批量专项授权</a-button>
          <a-button type="primary" @click="openCreate"><PlusOutlined />新增成员</a-button>
        </a-space>
      </div>

      <div class="toolbar filter-bar" style="margin-top: 16px">
        <a-input
          v-model:value="query.keyword"
          allow-clear
          placeholder="工号 / 姓名 / 单位"
          style="width: 220px"
          @pressEnter="((query.page = 1), load())"
        />
        <a-select
          v-model:value="query.identity"
          allow-clear
          placeholder="全部任职身份"
          style="width: 220px"
          :options="withAllOption(identityOptions)"
          @change="((query.page = 1), load())"
        />
        <a-select
          v-model:value="query.dataScope"
          allow-clear
          placeholder="全部数据权限"
          style="width: 160px"
          :options="withAllOption(scopeOptions)"
          @change="((query.page = 1), load())"
        />
        <a-button type="primary" @click="((query.page = 1), load())">查询</a-button>
      </div>

      <a-table
        :loading="loading"
        row-key="id"
        :data-source="rows"
        :columns="columns"
        :scroll="{ x: 1480 }"
        :pagination="{
          current: query.page,
          pageSize: query.size,
          total,
          showTotal: (t: number) => `共 ${t} 人`,
          showSizeChanger: false,
        }"
        @change="(p: any) => { query.page = p.current; load() }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'orgDept'">
            <div class="text-ellipsis-2">
              {{ record.orgName || '—' }}
              <span v-if="record.deptName" style="color: #8c8c8c"> / {{ record.deptName }}</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'identity'">
            <span class="identity-cell highlight">{{ record.identity || '—' }} · 主</span>
          </template>
          <template v-else-if="column.dataIndex === 'dataScope'">
            {{ DATA_SCOPE_TEXT[record.dataScope as DataScope] || record.dataScope || '—' }}
          </template>
          <template v-else-if="column.key === 'formMaint'">
            <a-tag :color="record.formMaintScope ? 'blue' : 'default'">
              {{ formMaintLabel(record.formMaintScope) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'declareResult'">
            <a-tag
              :color="
                record.declareResultAccess || record.roles?.includes('ADMIN') ? 'green' : 'default'
              "
            >
              {{ record.declareResultAccess || record.roles?.includes('ADMIN') ? '具备' : '无' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <span class="status-on">
              <i class="dot" :class="{ off: record.status !== 1 }" />
              {{ record.status === 1 ? '在岗' : '已离岗' }}
            </span>
          </template>
          <template v-else-if="column.key === 'act'">
            <a-space :size="0">
              <a-button type="link" size="small" @click="openPostEdit(record)">任职管理</a-button>
              <a-button type="link" size="small" @click="openSpecialEdit(record)">专项授权</a-button>
              <a-button type="link" size="small" @click="openBasicEdit(record)">基本信息</a-button>
              <a-dropdown>
                <a-button type="link" size="small">更多</a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item @click="resetPwd(record)">重置密码</a-menu-item>
                    <a-menu-item danger @click="remove(record)">删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="openBasic"
      :title="editing ? '基本信息' : '新增成员'"
      :width="640"
      destroy-on-close
      @ok="saveBasic"
    >
      <a-form layout="vertical" style="margin-top: 8px">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="工号" required>
              <a-input v-model:value="form.employeeNo" placeholder="如 100016" :disabled="!!editing" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="姓名" required>
              <a-input v-model:value="form.realName" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="主单位">
              <a-select
                v-model:value="form.orgName"
                :options="ORG_OPTIONS.map((o) => ({ label: o.org, value: o.org }))"
                @change="form.deptName = undefined"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="主部门">
              <a-select
                v-model:value="form.deptName"
                :options="deptOptions.map((d) => ({ label: d, value: d }))"
                :disabled="!form.orgName"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="任职身份" required>
              <a-select
                v-model:value="form.identity"
                :options="identityOptions"
                show-search
                placeholder="必选，决定功能角色与数据范围"
                @change="onIdentityChange"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="职称">
              <a-select
                v-model:value="form.rankTitle"
                :options="RANK_OPTIONS.map((i) => ({ label: i, value: i }))"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-radio-group
                v-model:value="form.status"
                :disabled="editing?.id === userStore.userId"
              >
                <a-radio :value="1">在岗</a-radio>
                <a-radio :value="0">已离岗</a-radio>
              </a-radio-group>
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="邮箱">
              <a-input v-model:value="form.email" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal v-model:open="openPost" title="任职管理" :width="680" destroy-on-close @ok="savePost">
      <div style="margin-bottom: 12px; color: #8c8c8c">
        {{ editing?.realName }}（{{ editing?.employeeNo }}）· 登录身份 ≠ 项目岗位
      </div>
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="任职身份（登录身份）">
              <a-select
                v-model:value="form.identity"
                :options="identityOptions"
                show-search
                @change="onIdentityChange"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="常用项目岗位（展示）">
              <a-select
                v-model:value="form.projectPost"
                :options="MEMBER_POST_OPTIONS.map((i) => ({ label: i, value: i }))"
                show-search
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="职称">
              <a-select
                v-model:value="form.rankTitle"
                :options="RANK_OPTIONS.map((i) => ({ label: i, value: i }))"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="数据权限">
              <a-select v-model:value="form.dataScope" :options="scopeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="主单位">
              <a-select
                v-model:value="form.orgName"
                :options="ORG_OPTIONS.map((o) => ({ label: o.org, value: o.org }))"
                @change="form.deptName = undefined"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="主部门">
              <a-select
                v-model:value="form.deptName"
                :options="deptOptions.map((d) => ({ label: d, value: d }))"
              />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="系统角色包（菜单鉴权）">
              <a-select v-model:value="form.roles" mode="multiple" :options="roleOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-alert
              type="info"
              show-icon
              message="切换任职身份会自动带出推荐数据范围与角色包，仍可手工调整。项目内 18 项办理权请在「项目岗位权限矩阵」配置。"
            />
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="openSpecial"
      title="专项授权"
      :width="640"
      destroy-on-close
      @ok="saveSpecial"
    >
      <div style="margin-bottom: 12px; color: #8c8c8c">
        {{ editing?.realName }}（{{ editing?.employeeNo }}）· 表单维护 / 立项审批结果 / 办结
      </div>
      <a-form layout="vertical">
        <a-form-item label="表单维护范围">
          <a-select
            v-model:value="form.formMaintScope"
            :options="FORM_MAINT_OPTIONS"
            :disabled="form.identity === '系统管理员'"
          />
        </a-form-item>
        <a-form-item v-if="needChannel" label="授权渠道编码" required>
          <a-input
            v-model:value="form.formMaintChannels"
            placeholder="多个渠道用英文逗号分隔，如 YYGD,ZDZX"
          />
        </a-form-item>
        <a-form-item v-if="needType" label="授权项目类型" required>
          <a-input
            v-model:value="form.formMaintTypes"
            placeholder="多个类型用英文逗号分隔"
          />
        </a-form-item>
        <a-form-item label="立项审批结果权限">
          <a-switch
            :checked="!!form.declareResultAccess || form.identity === '系统管理员'"
            :disabled="form.identity === '系统管理员'"
            checked-children="具备"
            un-checked-children="无"
            @change="(v: any) => (form.declareResultAccess = v ? 1 : 0)"
          />
          <div class="field-hint">系统管理员强制具备；其他人员由管理员单独分配。</div>
        </a-form-item>
        <a-form-item label="项目内办结权限">
          <a-switch
            :checked="!!form.finishAuth"
            checked-children="具备"
            un-checked-children="无"
            @change="(v: any) => (form.finishAuth = v ? 1 : 0)"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="openFinish" title="批量专项授权" :width="720" @ok="saveFinishAuth">
      <a-row :gutter="24">
        <a-col :span="12">
          <div class="batch-title">项目内办结权限</div>
          <div class="page-desc" style="margin-bottom: 8px">勾选后可执行计划办结等动作。</div>
          <a-checkbox-group v-model:value="finishSelected" style="width: 100%">
            <a-row>
              <a-col :span="24" v-for="u in rows" :key="'f' + u.id" style="margin-bottom: 8px">
                <a-checkbox :value="u.id">{{ u.realName }}（{{ u.employeeNo }}）</a-checkbox>
              </a-col>
            </a-row>
          </a-checkbox-group>
        </a-col>
        <a-col :span="12">
          <div class="batch-title">立项审批结果权限</div>
          <div class="page-desc" style="margin-bottom: 8px">管理员强制具备，不可取消。</div>
          <a-checkbox-group v-model:value="declareSelected" style="width: 100%">
            <a-row>
              <a-col :span="24" v-for="u in rows" :key="'d' + u.id" style="margin-bottom: 8px">
                <a-checkbox :value="u.id" :disabled="u.roles?.includes('ADMIN')">
                  {{ u.realName }}（{{ u.employeeNo }}）
                </a-checkbox>
              </a-col>
            </a-row>
          </a-checkbox-group>
        </a-col>
      </a-row>
    </a-modal>
  </div>
</template>

<style scoped>
.member-page {
  padding-top: 16px;
}
.member-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}
.cfg-tip {
  display: inline-block;
  font-size: 12px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 4px;
  padding: 2px 8px;
  margin-bottom: 8px;
}
.page-title .sub {
  font-size: 14px;
  font-weight: 400;
  color: #8c8c8c;
}
.identity-cell {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  line-height: 1.5;
}
.identity-cell.highlight {
  background: #fff1e6;
  color: #d46b08;
}
.status-on {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #262626;
}
.status-on .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #52c41a;
  display: inline-block;
}
.status-on .dot.off {
  background: #bfbfbf;
}
.field-hint {
  margin-top: 6px;
  color: #8c8c8c;
  font-size: 12px;
}
.batch-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.page-desc {
  color: #8c8c8c;
  font-size: 12px;
}
</style>
