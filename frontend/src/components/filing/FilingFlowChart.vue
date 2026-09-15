<script setup lang="ts">
import { who, type FilingFlowNode, type FilingFlowOpts } from '@/utils/filingFlow'

defineProps<{
  flow: FilingFlowOpts
  /** 对齐填报页：不展示「已办/当前」角标 */
  hideStatusChip?: boolean
  /** 填报页当前节点用绿框，弹窗用蓝框 */
  currentGreen?: boolean
}>()

function personBlock(node: FilingFlowNode | null | undefined) {
  if (!node?.handlers?.length) return ''
  if (node.handlers[0]?.label === '系统') return ''
  return who(node)
}

function boxClass(node: FilingFlowNode) {
  return {
    done: node.status === 'done',
    current: node.status === 'current',
    pending: node.status === 'pending',
    result: node.status === 'result',
  }
}
</script>

<template>
  <div class="ff-chart">
    <template v-for="(n, idx) in flow.nodes" :key="n.nodeCode">
      <div class="ff-box" :class="[boxClass(n), { 'current-green': currentGreen && n.status === 'current' }]">
        <b>{{ n.title }}</b>
        <small v-if="n.desc">{{ n.desc }}</small>
        <div v-if="n.actionLine || personBlock(n) || (!hideStatusChip && n.statusLabel)" class="ff-person">
          <span v-if="personBlock(n)" class="ff-who">{{ personBlock(n) }}</span>
          <span v-else-if="n.actionLine && n.actionLine !== '系统'" class="ff-act">{{ n.actionLine }}</span>
          <span v-if="!hideStatusChip && n.statusLabel" class="ff-st" :class="n.status">{{ n.statusLabel }}</span>
        </div>
      </div>
      <div v-if="idx < flow.nodes.length - 1" class="ff-arr">↓</div>
    </template>
  </div>
</template>

<style scoped>
.ff-chart {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}
.ff-box {
  width: min(520px, 100%);
  box-sizing: border-box;
  padding: 10px 12px;
  text-align: center;
  background: var(--zgsf-card);
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius);
  color: var(--zgsf-text);
  font-size: 13px;
  line-height: 20px;
}
.ff-box.done {
  border-color: var(--c-green);
  background: #f6ffed;
}
.ff-box.current {
  border-color: var(--zgsf-brand);
  border-width: 2px;
  background: var(--zgsf-brand-soft);
}
.ff-box.current-green {
  border-color: var(--c-green);
  background: #f6ffed;
}
.ff-box.pending {
  border-color: var(--zgsf-border);
  background: var(--zgsf-fill);
  color: var(--zgsf-text-secondary);
}
.ff-box.result {
  border-color: var(--zgsf-brand);
  border-width: 2px;
  background: var(--zgsf-brand-soft);
}
.ff-box b {
  display: block;
  font-weight: 600;
  color: var(--zgsf-text);
}
.ff-box small {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--zgsf-text-secondary);
  line-height: 16px;
}
.ff-person {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ff-act {
  font-size: 12px;
  color: var(--zgsf-text-subtle);
  line-height: 18px;
}
.ff-who {
  font-size: 12px;
  color: var(--zgsf-brand);
  font-weight: 600;
  line-height: 18px;
}
.ff-st {
  height: 18px;
  padding: 0 6px;
  border-radius: var(--zgsf-radius);
  font-size: 11px;
  line-height: 18px;
  background: var(--zgsf-bg);
  color: var(--zgsf-text-secondary);
}
.ff-st.done {
  background: #f6ffed;
  color: #389e0d;
}
.ff-st.current {
  background: var(--zgsf-brand-soft);
  color: var(--zgsf-brand);
}
.ff-arr {
  color: var(--zgsf-text-secondary);
  font-size: 14px;
  line-height: 18px;
  padding: 2px 0;
}
</style>
