<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { warningApi } from '@/api/modules'

const router = useRouter()
const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ page: 1, size: 10 })
const level = ref<string>()

async function load() {
  loading.value = true
  try {
    const res = await warningApi.page({ ...query, size: 200 })
    rows.value = (res.data as any)?.records || []
    total.value = (res.data as any)?.total || 0
  } finally {
    loading.value = false
  }
}
onMounted(load)

const filtered = computed(() => (level.value ? rows.value.filter((x) => x.warnLevel === level.value) : rows.value))
const stats = computed(() => ({
  red: rows.value.filter((x) => x.warnLevel === 'RED').length,
  yellow: rows.value.filter((x) => x.warnLevel === 'YELLOW').length,
  unread: rows.value.filter((x) => !x.isRead).length,
}))

const BIZ_TEXT: Record<string, string> = {
  MILESTONE: '里程碑', PLAN: '计划', DELIVERABLE: '交付物',
  TRANSFORM: '成果转化', POST_EVAL: '后评价', ACCEPTANCE: '验收', FUND: '经费',
}

async function read(row: any) {
  await warningApi.read(row.id)
  message.success('已标记已读')
  load()
}
async function scan() {
  await warningApi.scan()
  message.success('已按四色规则完成全量预警扫描（到期前30天黄色、超期红色）')
  load()
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">预警中心</h2>
    <div class="page-desc">
      预警推送方式：平台站内消息 + 企业邮箱 + 蓝信，推送对象为项目团队与对应管理团队。
      <b>风险预警（黄色）：节点到期前 30 天自动触发；逾期告警（红色）：节点到期当日自动触发，台账与可视化看板高亮展示</b>。
    </div>

    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6"><div class="stat-card warn"><div class="label">逾期告警（红）</div><div class="value">{{ stats.red }}</div></div></a-col>
      <a-col :span="6"><div class="stat-card" style="border-left-color: #faad14"><div class="label">临期预警（黄）</div><div class="value">{{ stats.yellow }}</div></div></a-col>
      <a-col :span="6"><div class="stat-card"><div class="label">未读消息</div><div class="value">{{ stats.unread }}</div></div></a-col>
      <a-col :span="6" style="display: flex; align-items: flex-end">
        <a-button @click="scan"><ReloadOutlined />手动触发扫描</a-button>
      </a-col>
    </a-row>

    <a-card :body-style="{ padding: '16px 20px' }">
      <div class="toolbar">
        <a-radio-group v-model:value="level" button-style="solid">
          <a-radio-button value="">全部</a-radio-button>
          <a-radio-button value="RED">逾期告警</a-radio-button>
          <a-radio-button value="YELLOW">临期预警</a-radio-button>
        </a-radio-group>
        <span>共 {{ filtered.length }} 条</span>
      </div>

      <a-table :loading="loading" row-key="id" :data-source="filtered" :pagination="{ pageSize: 10 }"
        :columns="[
          { title: '级别', dataIndex: 'warnLevel', width: 100 },
          { title: '业务类型', dataIndex: 'bizType', width: 110 },
          { title: '项目', dataIndex: 'projectName', width: 300 },
          { title: '预警标题', dataIndex: 'title', width: 260 },
          { title: '内容', dataIndex: 'content' },
          { title: '状态', dataIndex: 'isRead', width: 90 },
          { title: '操作', key: 'act', width: 130, fixed: 'right' },
        ]">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'warnLevel'">
            <a-tag :color="record.warnLevel === 'RED' ? 'red' : 'orange'">{{ record.warnLevel === 'RED' ? '逾期' : '临期' }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'bizType'">{{ BIZ_TEXT[record.bizType] || record.bizType }}</template>
          <template v-else-if="column.dataIndex === 'projectName'">
            <a style="color: #0a5cad" @click="router.push(`/overview/detail/${record.projectId}`)">{{ record.projectName }}</a>
          </template>
          <template v-else-if="column.dataIndex === 'isRead'">
            <a-tag :color="record.isRead ? 'default' : 'blue'">{{ record.isRead ? '已读' : '未读' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'act'">
            <a-space :size="2">
              <a-button type="link" size="small" :disabled="!!record.isRead" @click="read(record)">标记已读</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>
