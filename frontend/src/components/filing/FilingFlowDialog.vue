<script setup lang="ts">
import { computed } from 'vue'
import { buildFilingFlowOpts, type FilingFlowOpts } from '@/utils/filingFlow'
import { READONLY_FLOW_NOTICE } from '@/utils/flowEntryPolicy'
import type { ProjDeclaration } from '@/api/types'
import FilingFlowChart from '@/components/filing/FilingFlowChart.vue'

const props = defineProps<{
  open: boolean
  declaration?: Partial<ProjDeclaration> & { filingPhase?: string } | null
  opts?: FilingFlowOpts | null
}>()

const emit = defineEmits<{ 'update:open': [boolean]; 'view-materials': [] }>()

const flow = computed<FilingFlowOpts>(() => {
  if (props.opts) return props.opts
  return buildFilingFlowOpts(props.declaration || { name: '未命名项目', status: 'APPROVED' })
})

const subtitle = computed(() =>
  [flow.value.code, flow.value.name, flow.value.projectStatusLabel, flow.value.channelName]
    .filter(Boolean)
    .join(' · '),
)

const hasActive = computed(() => flow.value.nodes.some((n) => n.status === 'current'))

function close() {
  emit('update:open', false)
}

</script>

<template>
  <a-modal
    :open="open"
    :title="null"
    :footer="null"
    :width="560"
    :destroy-on-close="true"
    centered
    class="ff-modal"
    @cancel="close"
  >
    <div class="ff-hd">
      <h2>立项备案流转图</h2>
      <p>{{ subtitle }}</p>
    </div>

    <div class="ff-body">
      <!-- 当前节点条 -->
      <div class="srpm-now" :class="{ empty: !hasActive }">
        <div class="now-hd">
          <b>当前节点</b>
          <span class="tip">{{ flow.currentTip }}</span>
        </div>
      </div>

      <a-alert class="ff-readonly-tip" type="info" show-icon :message="READONLY_FLOW_NOTICE" />

      <FilingFlowChart :flow="flow" />

      <div class="ff-actions">
        <a-tag color="blue">请从左侧任务栏进入“立项阶段 / 立项备案”办理</a-tag>
      </div>

      <div class="ff-mat">
        <b>立项支撑材料（本渠道必传）</b>
        <div v-if="flow.attachments.length" class="ff-mat-list">
          <span v-for="a in flow.attachments" :key="a.code" class="ff-chip" :class="{ ok: a.uploaded }">
            {{ a.name }} · {{ a.uploaded ? (a.fileName || '已传') : '未传' }}
          </span>
        </div>
        <small v-else-if="flow.channelMaterials.length">{{ flow.channelMaterials.join('、') }}</small>
        <small v-else>本渠道暂无必传材料配置</small>
      </div>
    </div>
  </a-modal>
</template>

<style scoped>
.ff-hd {
  padding: 0 0 12px;
  border-bottom: 1px solid #e8e8e8;
  margin: -8px 0 14px;
}
.ff-hd h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
  line-height: 24px;
}
.ff-hd p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 20px;
}
.ff-body {
  max-height: min(72vh, 760px);
  overflow: auto;
  padding-right: 4px;
}

.srpm-now {
  margin: 0 0 14px;
  padding: 10px 12px;
  background: #e6f4ff;
  border: 1px solid #91caff;
  border-radius: 4px;
}
.srpm-now.empty {
  background: #f5f7fa;
  border-color: #e8e8e8;
}
.now-hd {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.now-hd b {
  font-size: 13px;
  color: #0048a0;
  font-weight: 600;
}
.srpm-now.empty .now-hd b {
  color: #595959;
}
.now-hd .tip {
  font-size: 13px;
  color: #262626;
  font-weight: 500;
}
.srpm-now.empty .now-hd .tip {
  color: #8c8c8c;
  font-weight: 400;
}

.ff-actions {
  display: flex;
  justify-content: center;
  margin: 12px 0 4px;
}
.ff-readonly-tip {
  margin: 0 0 12px;
}
.ff-mat {
  margin-top: 14px;
  width: min(440px, 100%);
  box-sizing: border-box;
  padding: 10px 12px;
  text-align: center;
  background: #fff2f0;
  border: 2px solid #f5222d;
  border-radius: 4px;
}
.ff-mat b {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #cf1322;
  line-height: 20px;
}
.ff-mat small {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #595959;
  line-height: 18px;
}
.ff-mat-list {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}
.ff-chip {
  height: 20px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 20px;
  background: #fff7e6;
  color: #d46b08;
  border: 1px solid #ffd591;
}
.ff-chip.ok {
  background: #f6ffed;
  color: #389e0d;
  border-color: #b7eb8f;
}
</style>
