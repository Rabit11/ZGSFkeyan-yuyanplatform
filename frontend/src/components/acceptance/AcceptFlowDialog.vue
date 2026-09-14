<script setup lang="ts">
import { computed, ref } from 'vue'
import { CheckCircleFilled, CloseCircleFilled } from '@ant-design/icons-vue'
import { fmtDate } from '@/utils/format'
import { READONLY_FLOW_NOTICE } from '@/utils/flowEntryPolicy'
import {
  buildAcceptFlowOptsFromOverview,
  currentAcceptNodes,
  who,
  type AcceptFlowNode,
  type AcceptFlowOpts,
} from '@/utils/acceptFlow'

const props = defineProps<{
  open: boolean
  overview?: any
  opts?: AcceptFlowOpts | null
}>()

const emit = defineEmits<{ 'update:open': [boolean] }>()
const chainOpen = ref(false)

const flow = computed<AcceptFlowOpts>(() => {
  if (props.opts) return props.opts
  return buildAcceptFlowOptsFromOverview(props.overview || {})
})

const subtitle = computed(() => [flow.value.code, flow.value.name].filter(Boolean).join(' · '))
const nowList = computed(() => currentAcceptNodes(flow.value))
const recentChain = computed(() => {
  const all = flow.value.timeline || []
  if (chainOpen.value) return all
  return all.slice(-2)
})
const hiddenChain = computed(() => Math.max(0, (flow.value.timeline || []).length - recentChain.value.length))

function close() {
  emit('update:open', false)
}

function boxClass(node: AcceptFlowNode) {
  return {
    done: node.status === 'done' || node.status === 'closed',
    current: node.status === 'current',
    pending: node.status === 'pending',
    ret: node.status === 'return',
    't-action': node.nodeType === 'ACTION',
    't-audit': node.nodeType === 'AUDIT',
    't-system': node.nodeType === 'SYSTEM',
    't-closed': node.nodeType === 'CLOSED',
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
    class="af-modal"
    @cancel="close"
  >
    <div class="af-hd">
      <div>
        <div class="af-title">项目验收 · 详情与附件</div>
        <p>{{ subtitle }}</p>
      </div>
      <a-tag :color="flow.panelStatus === 'DONE' ? 'success' : flow.panelStatus === 'HANDLING' ? 'processing' : 'default'">
        {{ flow.panelStatusLabel }}
      </a-tag>
    </div>

    <div class="af-body">
      <div class="af-meta">
        <div><span class="k">项目负责人</span>{{ flow.ownerLabel }}</div>
        <div><span class="k">当前内容</span>{{ flow.currentContent }}</div>
        <div><span class="k">完成率</span>{{ flow.doneCount }}/{{ flow.totalCount }} 已完成</div>
        <div><span class="k">最新流程</span>{{ flow.latestProcess }}</div>
      </div>

      <a-alert class="af-readonly-tip" type="info" show-icon :message="READONLY_FLOW_NOTICE" />

      <div class="af-actions">
        <a-tag color="blue">{{ flow.nextAction }}</a-tag>
        <span class="af-route-tip">请从左侧任务栏进入“验收阶段 / 项目验收”办理</span>
      </div>

      <div v-if="flow.panelStatus === 'DONE'" class="af-block">
        <div class="af-label">办结信息</div>
        <div class="af-kv"><span>验收办结日期</span>{{ flow.finishAt || '—' }}</div>
        <p class="hint">请于 30 日内完成协作单位评价（到期日 {{ fmtDate(flow.partnerDueDate) }}）。</p>
      </div>

      <div class="af-block">
        <div class="af-label">发起前置校验</div>
        <div v-for="c in flow.checks" :key="c.key" class="ck">
          <CheckCircleFilled v-if="c.passed" class="ok" />
          <CloseCircleFilled v-else class="bad" />
          <div>
            <b>{{ c.label }}</b>
            <span>{{ c.message }}</span>
          </div>
        </div>
      </div>

      <div class="af-block">
        <div class="af-label">材料附件</div>
        <div v-for="g in flow.groups" :key="g.code" class="mat-g">
          <div class="mat-h">
            <span>{{ g.name }}</span>
            <a-tag v-if="g.locked" color="default">本项目不适用 · 已锁定</a-tag>
            <a-tag v-else :color="g.uploaded ? 'success' : 'warning'">{{ g.uploaded ? '已传' : '未传' }}</a-tag>
          </div>
          <div v-for="(f, i) in g.files" :key="g.code + i" class="mat-f">
            <div class="fn">{{ f.fileName || f.name }}</div>
            <div class="fm">
              {{ f.uploader || '—' }}
              <template v-if="f.uploadedAt"> · {{ fmtDate(f.uploadedAt) }}</template>
              <template v-if="f.fileSize"> · {{ f.fileSize }}</template>
            </div>
            <a-button v-if="f.uploaded" type="link" size="small" :href="f.fileUrl || 'javascript:;'">下载</a-button>
          </div>
        </div>
      </div>

      <div class="af-block">
        <div class="af-label">验收审批流</div>
        <div v-if="nowList.length" class="srpm-now">
          <div class="now-hd"><b>当前节点</b><span>{{ nowList[0].lane }} · {{ nowList[0].title }}</span></div>
        </div>
        <div v-else class="srpm-now empty">
          <div class="now-hd"><b>当前节点</b><span>项目验收暂无进行中节点</span></div>
        </div>
        <div class="af-chart">
          <template v-for="(n, idx) in flow.nodes" :key="n.nodeCode">
            <div class="af-box" :class="boxClass(n)">
              <em>{{ n.lane }}</em>
              <b>{{ n.title }}</b>
              <small v-if="n.desc">{{ n.desc }}</small>
              <div class="af-person">
                <span class="act">{{ n.actionLine }}</span>
                <span class="who">{{ who(n) }}</span>
                <span class="st" :class="n.status">{{ n.statusLabel }}</span>
              </div>
              <div v-if="n.diamond" class="diamond">
                <span class="pass">通过</span>
                <span class="rej">退回</span>
              </div>
            </div>
            <div v-if="idx < flow.nodes.length - 1" class="af-arr">↓</div>
          </template>
        </div>
      </div>

      <div class="af-block">
        <div class="af-label">审查 / 办理链条</div>
        <div class="flow-now">当前流向 · {{ flow.panelStatus === 'DONE' ? '已办结 - 待归档' : flow.currentContent }}</div>
        <div v-for="(t, i) in recentChain" :key="i" class="chain-card">
          <div class="ct">{{ t.title }}</div>
          <div class="cm">{{ t.operator }} · {{ t.status === 'done' || t.status === 'closed' ? '已办' : t.result }}</div>
          <div v-if="t.at && t.at !== '—'" class="cm">{{ t.at }}</div>
          <div v-if="t.comment" class="op">意见：{{ t.comment }}</div>
        </div>
        <a-button v-if="hiddenChain > 0 || chainOpen" type="link" size="small" @click="chainOpen = !chainOpen">
          {{ chainOpen ? '收起链条' : `展开完整链条（${hiddenChain}）` }}
        </a-button>
      </div>
    </div>

    <div class="af-foot">
      <a-button @click="close">关闭</a-button>
    </div>
  </a-modal>
</template>

<style scoped>
.af-hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 0 0 12px;
  border-bottom: 1px solid #e8e8e8;
  margin: -8px 0 16px;
}
.af-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
  line-height: 24px;
}
.af-hd p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 20px;
}
.af-body {
  max-height: min(68vh, 720px);
  overflow: auto;
  padding-bottom: 8px;
}
.af-foot {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
.af-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #262626;
  line-height: 22px;
}
.af-meta .k {
  display: inline-block;
  width: 72px;
  color: #8c8c8c;
}
.af-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.af-readonly-tip {
  margin: 0 0 12px;
}
.af-route-tip {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 22px;
}
.af-block { margin-bottom: 16px; }
.af-label {
  font-weight: 600;
  font-size: 13px;
  color: #1f1f1f;
  margin-bottom: 8px;
}
.af-kv {
  font-size: 13px;
  display: flex;
  gap: 12px;
}
.hint { margin: 6px 0 0; font-size: 12px; color: #8c8c8c; }
.ck {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 6px 0;
  font-size: 13px;
  line-height: 20px;
}
.ck .ok { color: #52c41a; margin-top: 3px; }
.ck .bad { color: #f5222d; margin-top: 3px; }
.ck b { margin-right: 6px; }
.ck span { color: #8c8c8c; }
.mat-g { margin-bottom: 10px; }
.mat-h {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
}
.mat-f {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #fafafa;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  margin-bottom: 6px;
}
.mat-f .fn { flex: 1; font-size: 12px; color: #262626; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mat-f .fm { font-size: 12px; color: #8c8c8c; white-space: nowrap; }

.srpm-now {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #e6f4ff;
  border: 1px solid #91caff;
  border-radius: 4px;
}
.srpm-now.empty { background: #f5f7fa; border-color: #e8e8e8; }
.now-hd { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.now-hd b { font-size: 13px; color: #0048a0; }
.srpm-now.empty .now-hd b { color: #595959; }

.af-chart { display: flex; flex-direction: column; align-items: center; }
.af-box {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  text-align: center;
}
.af-box.done { border-color: #52c41a; background: #f6ffed; }
.af-box.current { border-color: #0064ef; border-width: 2px; background: #e6f4ff; }
.af-box.pending { color: #8c8c8c; background: #fafafa; }
.af-box.ret { border-color: #f5222d; background: #fff2f0; }
.af-box em { display: block; font-size: 11px; color: #8c8c8c; font-style: normal; }
.af-box b { display: block; font-size: 13px; color: #1f1f1f; }
.af-box small { display: block; margin-top: 2px; font-size: 11px; color: #8c8c8c; }
.af-person {
  margin-top: 6px;
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
}
.af-person .who { color: #0064ef; font-weight: 600; }
.st {
  height: 18px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 18px;
  background: #f5f7fa;
}
.st.done, .st.closed { background: #f6ffed; color: #389e0d; }
.st.current { background: #e6f4ff; color: #0064ef; }
.st.return { background: #fff2f0; color: #f5222d; }
.diamond { margin-top: 6px; display: flex; justify-content: center; gap: 12px; font-size: 11px; }
.diamond .pass { color: #389e0d; }
.diamond .rej { color: #f5222d; }
.af-arr { color: #8c8c8c; line-height: 18px; padding: 2px 0; }

.flow-now {
  padding: 8px 12px;
  background: #e6f4ff;
  border-radius: 4px;
  font-size: 13px;
  color: #0048a0;
  margin-bottom: 8px;
}
.chain-card {
  padding: 10px 12px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  margin-bottom: 8px;
  background: #fff;
}
.ct { font-weight: 600; font-size: 13px; }
.cm { font-size: 12px; color: #8c8c8c; }
.op { font-size: 12px; color: #262626; margin-top: 4px; }
</style>
