<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { auditApi } from '@/api/modules'
import { withAllOption } from '@/utils/filterOptions'

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ page: 1, size: 10, module: undefined, action: undefined, userName: undefined })

const ACTION_TEXT: Record<string, string> = {
  CREATE: '新增', UPDATE: '修改', DELETE: '删除', SUBMIT: '提交', APPROVE: '审批',
  EXPORT: '导出', LOGIN: '登录', LOGOUT: '退出', REVOKE: '撤回', FILING: '备案',
  CLOSE: '关闭', DELAY: '延期', WRITEOFF: '核销', LOCK: '锁定', BIND: '绑定',
  RESET_PWD: '重置密码', ASSIGN_ROLE: '分配角色', AUTH_GRANT: '授权', MATERIAL: '维护材料',
  REFRESH: '刷新', SCAN: '扫描', READ: '已读', DOWNLOAD: '下载', UPLOAD: '上传',
  SYNC: '同步', RESET: '重置', CHECK: '检查', BACKUP: '备份', RESTORE: '回滚',
}

const MODULE_TEXT: Record<string, string> = {
  AUTH: '认证', MEMBER: '成员管理', PERMISSION: '权限矩阵', PROJECT: '项目台账',
  MILESTONE: '里程碑', DECLARATION: '申报立项', FILE: '附件', WARNING: '预警',
  POST_EVAL: '后评价', TRANSFORM: '成果转化', PARTNER: '合作评价', DELIVERABLE: '交付物',
  ACCEPTANCE: '验收', CHANGE: '变更', EVALUATION: '过程评价', HQ_FUND: '总部经费',
  FUND: '经费', PLAN: '计划', DICT: '字典', BACKUP: '备份回滚', SYSTEM: '系统',
}

const ACTION_OPTIONS = Object.entries(ACTION_TEXT).map(([value, label]) => ({ value, label }))
const MODULE_OPTIONS = Object.entries(MODULE_TEXT).map(([value, label]) => ({ value, label }))

async function load() {
  loading.value = true
  try {
    const res = await auditApi.page(query)
    rows.value = (res.data as any)?.records || []
    total.value = (res.data as any)?.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  query.module = undefined
  query.action = undefined
  query.userName = undefined
  query.page = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">审计日志</h2>
    <div class="page-desc">全平台业务操作全程留痕：登录退出、新增修改删除、提交审批、导入导出、附件上下载均由后台自动记录，支持按模块、动作、操作人追溯。</div>

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar" style="margin-bottom: 16px; display: flex; gap: 8px; flex-wrap: wrap">
        <a-input
          v-model:value="query.userName"
          allow-clear
          placeholder="操作人"
          style="width: 160px"
          @pressEnter="search"
        />
        <a-select
          v-model:value="query.module"
          allow-clear
          placeholder="全部模块"
          style="width: 160px"
          :options="withAllOption(MODULE_OPTIONS)"
        />
        <a-select
          v-model:value="query.action"
          allow-clear
          placeholder="全部动作"
          style="width: 140px"
          :options="withAllOption(ACTION_OPTIONS)"
        />
        <a-button type="primary" @click="search">查询</a-button>
        <a-button @click="reset">重置</a-button>
      </div>

      <a-table :loading="loading" row-key="id" :data-source="rows"
        :pagination="{ current: query.page, pageSize: query.size, total, showTotal: (t: number) => `共 ${t} 条` }"
        @change="(p: any) => { query.page = p.current; query.size = p.pageSize; load() }"
        :columns="[
          { title: '操作人', dataIndex: 'userName', width: 120 },
          { title: '业务模块', dataIndex: 'module', width: 140 },
          { title: '动作', dataIndex: 'action', width: 110 },
          { title: '操作内容', dataIndex: 'content' },
          { title: 'IP', dataIndex: 'ip', width: 140 },
          { title: '时间', dataIndex: 'createdAt', width: 180 },
        ]">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'module'">
            {{ MODULE_TEXT[record.module] || record.module }}
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-tag>{{ ACTION_TEXT[record.action] || record.action }}</a-tag>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>
