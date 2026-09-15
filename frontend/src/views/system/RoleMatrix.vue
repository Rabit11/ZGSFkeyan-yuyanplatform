<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, SaveOutlined, UndoOutlined } from '@ant-design/icons-vue'
import { permissionApi } from '@/api/modules'
import {
  PROJECT_PERM_DEFS,
  PROJECT_POST_DEFS,
  cloneDefaultMatrix,
  type PostPermMatrix,
  type ProjectPermCode,
  type ProjectPostCode,
} from '@/constants/permission'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const matrix = reactive<PostPermMatrix>(cloneDefaultMatrix())
const dirty = ref(false)

const columns = computed(() => [
  {
    title: '办理权限 \\ 项目岗位',
    dataIndex: 'perm',
    fixed: 'left' as const,
    width: 200,
  },
  ...PROJECT_POST_DEFS.map((p) => ({
    title: p.label,
    dataIndex: p.code,
    align: 'center' as const,
    width: 88,
  })),
])

const rows = computed(() =>
  PROJECT_PERM_DEFS.map((perm) => ({
    key: perm.code,
    perm: perm.label,
    group: perm.group,
    code: perm.code as ProjectPermCode,
  })),
)

function hasPerm(post: ProjectPostCode, perm: ProjectPermCode) {
  return (matrix[post] || []).includes(perm)
}

function toggle(post: ProjectPostCode, perm: ProjectPermCode, checked: boolean) {
  const list = new Set(matrix[post] || [])
  if (checked) list.add(perm)
  else list.delete(perm)
  matrix[post] = Array.from(list) as ProjectPermCode[]
  dirty.value = true
}

function setAllForPost(post: ProjectPostCode, checked: boolean) {
  matrix[post] = checked ? (PROJECT_PERM_DEFS.map((p) => p.code) as ProjectPermCode[]) : []
  dirty.value = true
}

function setAllForPerm(perm: ProjectPermCode, checked: boolean) {
  for (const post of PROJECT_POST_DEFS) {
    const list = new Set(matrix[post.code] || [])
    if (checked) list.add(perm)
    else list.delete(perm)
    matrix[post.code] = Array.from(list) as ProjectPermCode[]
  }
  dirty.value = true
}

async function load() {
  loading.value = true
  try {
    const res = await permissionApi.getMatrix()
    const data = (res.data || {}) as PostPermMatrix
    for (const p of PROJECT_POST_DEFS) {
      matrix[p.code] = [...(data[p.code] || [])]
    }
    dirty.value = false
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!userStore.isAdmin) {
    message.error('仅系统管理员可修改项目岗位办理权限矩阵')
    return
  }
  saving.value = true
  try {
    const payload: PostPermMatrix = {} as PostPermMatrix
    for (const p of PROJECT_POST_DEFS) {
      payload[p.code] = [...(matrix[p.code] || [])]
    }
    await permissionApi.saveMatrix(payload)
    dirty.value = false
    message.success('权限矩阵已保存，全平台项目立即按新矩阵判断')
  } finally {
    saving.value = false
  }
}

function resetDefault() {
  Modal.confirm({
    title: '恢复推荐默认矩阵？',
    content: '将覆盖当前未保存/已保存的勾选，恢复为文档推荐默认值。保存后才全平台生效。',
    okText: '恢复默认',
    onOk: () => {
      const d = cloneDefaultMatrix()
      for (const p of PROJECT_POST_DEFS) {
        matrix[p.code] = [...d[p.code]]
      }
      dirty.value = true
      message.info('已载入推荐默认矩阵，请点击保存生效')
    },
  })
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-card matrix-page">
      <div class="matrix-head">
        <div>
          <div class="cfg-tip">配置中心 · 仅系统管理员可维护 · 修改后全平台项目办理权限立即生效</div>
          <h2 class="page-title" style="margin-bottom: 4px">
            项目岗位办理权限
            <a-tag v-if="dirty" color="orange" style="margin-left: 8px">未保存</a-tag>
          </h2>
          <div class="page-desc">
            横向为 13 类项目岗位，纵向为 18 项办理权限。同一人在同一项目担任多岗位时取权限并集；
            能进入项目不等于能办理——写操作仍须按本矩阵与流程状态校验。
          </div>
        </div>
        <a-space wrap>
          <a-button @click="load" :loading="loading"><ReloadOutlined />刷新</a-button>
          <a-button @click="resetDefault"><UndoOutlined />恢复推荐默认</a-button>
          <a-button type="primary" :loading="saving" :disabled="!userStore.isAdmin" @click="save">
            <SaveOutlined />保存矩阵
          </a-button>
        </a-space>
      </div>

      <a-alert
        v-if="!userStore.isAdmin"
        type="warning"
        show-icon
        style="margin: 16px 0"
        message="当前账号非系统管理员，仅可查看矩阵，不可保存。"
      />

      <a-table
        class="matrix-table"
        :loading="loading"
        :columns="columns"
        :data-source="rows"
        :pagination="false"
        :scroll="{ x: 1400, y: 'calc(100vh - 320px)' }"
        size="middle"
        bordered
        row-key="key"
      >
        <template #headerCell="{ column }">
          <template v-if="column.dataIndex && column.dataIndex !== 'perm'">
            <div class="post-head">
              <div>{{ column.title }}</div>
              <a-checkbox
                :disabled="!userStore.isAdmin"
                :checked="
                  PROJECT_PERM_DEFS.every((p) =>
                    hasPerm(column.dataIndex as ProjectPostCode, p.code),
                  )
                "
                :indeterminate="
                  PROJECT_PERM_DEFS.some((p) =>
                    hasPerm(column.dataIndex as ProjectPostCode, p.code),
                  ) &&
                  !PROJECT_PERM_DEFS.every((p) =>
                    hasPerm(column.dataIndex as ProjectPostCode, p.code),
                  )
                "
                @change="
                  (e: any) => setAllForPost(column.dataIndex as ProjectPostCode, e.target.checked)
                "
              />
            </div>
          </template>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'perm'">
            <div class="perm-cell">
              <span>{{ record.perm }}</span>
              <a-checkbox
                :disabled="!userStore.isAdmin"
                :checked="
                  PROJECT_POST_DEFS.every((p) => hasPerm(p.code, record.code as ProjectPermCode))
                "
                :indeterminate="
                  PROJECT_POST_DEFS.some((p) => hasPerm(p.code, record.code as ProjectPermCode)) &&
                  !PROJECT_POST_DEFS.every((p) => hasPerm(p.code, record.code as ProjectPermCode))
                "
                @change="
                  (e: any) => setAllForPerm(record.code as ProjectPermCode, e.target.checked)
                "
              />
            </div>
          </template>
          <template v-else-if="column.dataIndex">
            <a-checkbox
              :disabled="!userStore.isAdmin"
              :checked="hasPerm(column.dataIndex as ProjectPostCode, record.code as ProjectPermCode)"
              @change="
                (e: any) =>
                  toggle(
                    column.dataIndex as ProjectPostCode,
                    record.code as ProjectPermCode,
                    e.target.checked,
                  )
              "
            />
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<style scoped>
.matrix-page {
  padding-top: 16px;
}
.matrix-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.cfg-tip {
  display: inline-block;
  font-size: 12px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: var(--zgsf-radius);
  padding: 2px 8px;
  margin-bottom: 8px;
}
.page-desc {
  color: var(--zgsf-text-secondary);
  font-size: 13px;
  max-width: 780px;
  line-height: 1.6;
}
.post-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  line-height: 1.3;
  white-space: normal;
}
.perm-cell {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.matrix-table :deep(.ant-table-thead > tr > th) {
  background: var(--zgsf-bg);
  vertical-align: middle;
}
.matrix-table :deep(.ant-table-cell) {
  padding: 8px 6px !important;
}
</style>
