<script setup lang="ts">
import { computed } from 'vue'
import { READONLY_FLOW_NOTICE } from '@/utils/flowEntryPolicy'
import {
  buildTransformFlowOptsFromOverview,
  currentTransformNodes,
  who,
  type TfFlowNode,
  type TfFlowOpts,
} from '@/utils/transformFlow'

const props = defineProps<{
  open: boolean
  overview?: any
  opts?: TfFlowOpts | null
}>()

const emit = defineEmits<{ 'update:open': [boolean] }>()

const flow = computed<TfFlowOpts>(() => {
  if (props.opts) return props.opts
  return buildTransformFlowOptsFromOverview(props.overview || {})
})

const subtitle = computed(() => [flow.value.code, flow.value.name].filter(Boolean).join(' · '))
const nowList = computed(() => currentTransformNodes(flow.value))

function close() {
  emit('update:open', false)
}

function boxClass(node: TfFlowNode) {
  return {
    done: node.status === 'done' || node.status === 'closed',
    current: node.status === 'current',
    pending: node.status === 'pending',
    ret: node.status === 'return',
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="null"
    :footer="null"
    :width="640"
    :destroy-on-close="true"
    centered
    class="tf-modal"
    @cancel="close"
  >
    <div class="tf-hd">
      <div>
        <div class="tf-title">成果转化 · 详情与附件</div>
        <p>{{ subtitle }}</p>
      </div>
      <a-tag :color="flow.panelStatus === 'DONE' ? 'success' : flow.panelStatus === 'HANDLING' ? 'processing' : 'default'">
        {{ flow.panelStatusLabel }}
      </a-tag>
    </div>

    <div class="tf-body">
      <div class="tf-meta">
        <div><span class="k">本阶段负责</span>项目负责人 · {{ flow.ownerLabel }}</div>
        <div><span class="k">当前流向</span>{{ flow.currentFlow }}</div>
        <div><span class="k">完成度</span>{{ flow.doneCount }}/{{ flow.totalCount }} 成果包</div>
        <div><span class="k">在途 / 最近流程</span>{{ flow.latestProcess }}</div>
      </div>

      <a-alert class="tf-readonly-tip" type="info" show-icon :message="READONLY_FLOW_NOTICE" />

      <div class="tf-actions">
        <a-tag color="blue">{{ flow.nextAction }}</a-tag>
        <span class="tf-route-tip">请从左侧任务栏进入“成果转化”办理</span>
      </div>

      <div class="tf-block">
        <div class="tf-label">成果包与交付物双向追溯</div>
        <div class="tf-trace">
          <span>已交付 {{ flow.trace.delivered }}</span>
          <span>已绑定 {{ flow.trace.bound }}</span>
          <span>成果包 {{ flow.trace.packages }}</span>
          <span>转化材料 {{ flow.trace.materials }}</span>
        </div>
      </div>

      <div class="tf-block">
        <div class="tf-label">成果包</div>
        <a-empty v-if="!flow.packages.length" description="暂无成果包，可在成果转化台账新建" />
        <div v-for="pkg in flow.packages" :key="pkg.id || pkg.achievementNo" class="pkg-row">
          <div>
            <b>{{ pkg.achievementNo }}</b>
            <span>{{ pkg.name }}</span>
          </div>
          <div class="pkg-m">
            {{ pkg.transformWay || '—' }} · 交付物 {{ pkg.itemCount || 0 }}
            <a-tag>{{ pkg.statusLabel }}</a-tag>
          </div>
        </div>
      </div>

      <div class="tf-block">
        <div class="tf-label">成果转化信息流程</div>
        <div v-if="nowList.length" class="srpm-now">
          <div class="now-hd"><b>当前节点</b><span>{{ nowList.map((n) => n.title).join(' · ') }}</span></div>
        </div>
        <div class="tf-chart">
          <template v-for="(n, idx) in flow.nodes" :key="n.nodeCode">
            <div class="tf-box" :class="boxClass(n)">
              <b>{{ n.title }}<template v-if="n.status === 'current'"> · 当前</template></b>
              <div v-if="who(n)" class="tf-who">{{ who(n) }}</div>
              <small v-if="n.desc">{{ n.desc }}</small>
            </div>
            <div v-if="idx < flow.nodes.length - 1" class="tf-arr">↓</div>
          </template>
        </div>
        <p class="hint">{{ flow.processHint }}</p>
      </div>
    </div>

    <div class="tf-foot">
      <a-button @click="close">关闭</a-button>
    </div>
  </a-modal>
</template>

<style scoped>
.tf-hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 0 0 12px;
  border-bottom: 1px solid #e8e8e8;
  margin: -8px 0 16px;
}
.tf-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
  line-height: 24px;
}
.tf-hd p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 20px;
}
.tf-body {
  max-height: min(68vh, 720px);
  overflow: auto;
  padding-bottom: 8px;
}
.tf-foot {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
.tf-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  margin-bottom: 12px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  color: #262626;
  line-height: 22px;
}
.tf-meta .k {
  display: block;
  color: #8c8c8c;
  font-size: 12px;
}
.tf-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.tf-readonly-tip {
  margin: 0 0 12px;
}
.tf-route-tip {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 22px;
}
.tf-block { margin-bottom: 16px; }
.tf-label {
  font-weight: 600;
  font-size: 13px;
  color: #1f1f1f;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #0048a0;
}
.tf-trace {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #262626;
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
}
.pkg-row {
  padding: 8px 12px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  margin-bottom: 8px;
  font-size: 13px;
}
.pkg-row b { margin-right: 8px; color: #0048a0; }
.pkg-m { margin-top: 4px; font-size: 12px; color: #8c8c8c; display: flex; gap: 8px; align-items: center; }
.hint { margin: 8px 0 0; font-size: 12px; color: #8c8c8c; }

.srpm-now {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #e6f4ff;
  border: 1px solid #91caff;
  border-radius: 4px;
}
.now-hd { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.now-hd b { font-size: 13px; color: #0048a0; }

.tf-chart { display: flex; flex-direction: column; align-items: stretch; }
.tf-box {
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
}
.tf-box.done { border-color: #52c41a; background: #f6ffed; }
.tf-box.current { border-color: #0064ef; border-width: 2px; background: #e6f4ff; }
.tf-box.pending { color: #8c8c8c; background: #fafafa; }
.tf-box.ret { border-color: #f5222d; background: #fff2f0; }
.tf-box b { display: block; font-size: 13px; color: #1f1f1f; }
.tf-box.pending b { color: #8c8c8c; }
.tf-who { margin-top: 4px; font-size: 12px; color: #0064ef; font-weight: 600; }
.tf-box small { display: block; margin-top: 4px; font-size: 12px; color: #8c8c8c; line-height: 18px; }
.tf-arr { color: #8c8c8c; line-height: 18px; padding: 2px 0; text-align: center; }
</style>
