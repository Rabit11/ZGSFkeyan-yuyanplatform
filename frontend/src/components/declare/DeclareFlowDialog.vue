<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  buildDeclareFlowOpts,
  compactChain,
  currentItems,
  statusLabel,
  who,
  type DeclareFlowOpts,
  type DeclareFlowStep,
} from '@/utils/declareFlow'
import { READONLY_FLOW_NOTICE } from '@/utils/flowEntryPolicy'
import type { ProjDeclaration } from '@/api/types'

const props = defineProps<{
  open: boolean
  declaration?: Partial<ProjDeclaration> & { steps?: DeclareFlowStep[] } | null
  opts?: DeclareFlowOpts | null
}>()

const emit = defineEmits<{ 'update:open': [boolean] }>()

const flowExpanded = ref(false)

const flow = computed<DeclareFlowOpts>(() => {
  if (props.opts) {
    return {
      materials: [],
      materialSummary: '',
      ...props.opts,
    }
  }
  return buildDeclareFlowOpts(props.declaration || { name: '未命名申报', needApproval: 1 })
})

const title = computed(() =>
  flow.value.needApproval ? '项目申报流转图 · 需审批' : '项目申报流转图 · 无需审批',
)

const subtitle = computed(() =>
  [
    flow.value.code,
    flow.value.name,
    flow.value.channel,
    flow.value.needApproval ? '按渠道走线上审签' : '直接线上报备',
  ]
    .filter(Boolean)
    .join(' · '),
)

const sourceText = computed(() => {
  const parts = [flow.value.channelLevel, flow.value.channel].filter(Boolean)
  return parts.join(' · ') || '系统按渠道展示对应附件栏'
})

const contactNode = computed(
  () => flow.value.nodes.find((n) => /联系人/.test(n.title)) || flow.value.nodes[0] || null,
)

const nowList = computed(() => currentItems(flow.value.nodes))

const chainView = computed(() => compactChain(flow.value.nodes, 4))

const chainNodes = computed(() => (flowExpanded.value ? flow.value.nodes : chainView.value.show))

const walkedCount = computed(
  () => flow.value.nodes.filter((n) => ['approved', 'done', 'current', 'rejected'].includes(n.status)).length,
)

watch(
  () => props.open,
  (v) => {
    if (!v) flowExpanded.value = false
  },
)

function close() {
  emit('update:open', false)
}

function personBlock(node: DeclareFlowStep | null | undefined) {
  if (!node) return ''
  return who(node)
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
    class="df-modal"
    @cancel="close"
  >
    <div class="df-hd">
      <h2>{{ title }}</h2>
      <p>{{ subtitle }}</p>
    </div>

    <div class="df-body">
      <!-- 当前节点条 -->
      <div v-if="nowList.length" class="srpm-now">
        <div class="now-hd">
          <b>当前节点 · 项目申报</b>
          <span class="tip">仅展示需关注的办理中 / 可操作项</span>
        </div>
        <div class="now-list">
          <div v-for="(it, i) in nowList" :key="i" class="now-item">
            <span class="ttl">{{ it.title }}</span>
            <span class="who">{{ who(it) }}</span>
            <span class="st" :class="it.status">{{ statusLabel(it.status) || '当前' }}</span>
          </div>
        </div>
      </div>
      <div v-else class="srpm-now empty">
        <div class="now-hd"><b>当前节点</b><span class="tip">申报流程暂无进行中节点</span></div>
      </div>

      <a-alert class="df-readonly-tip" type="info" show-icon :message="READONLY_FLOW_NOTICE" />

      <div class="df-chart">
        <div class="df-box df-stage">
          <b>立项阶段</b>
          <small>项目申报与立项备案按业务路径分流</small>
        </div>
        <div class="df-arr">↓</div>
        <div class="df-fork stage-fork">
          <div class="col">
            <div class="df-box active-branch"><b>项目申报</b><small>当前查看流程</small></div>
          </div>
          <div class="col">
            <div class="df-box muted-branch"><b>立项备案</b><small>申报办结后进入</small></div>
          </div>
        </div>
        <div class="df-arr">↓</div>

        <div class="df-box">
          <b>选择项目来源</b>
          <small>{{ sourceText }}；系统自动展示对应附件栏，锁定无关项</small>
        </div>
        <div class="df-arr">↓</div>

        <div class="df-box">
          <b>填报上传申报全套材料</b>
          <small v-if="flow.materialSummary">{{ flow.materialSummary }}</small>
          <small v-else>本渠道暂无必传材料配置</small>
          <div v-if="flow.materials?.length" class="df-mats">
            <span v-for="m in flow.materials" :key="m.code" class="df-mat" :class="{ ok: m.uploaded }">
              {{ m.name }} · {{ m.uploaded ? (m.fileName || '已传') : '未传' }}
            </span>
          </div>
          <div class="df-person">
            <span class="df-who">{{ personBlock(contactNode) }}</span>
            <span v-if="contactNode" class="df-st" :class="contactNode.status">{{ statusLabel(contactNode.status) }}</span>
          </div>
        </div>
        <div class="df-arr">↓</div>

        <div class="df-dia df-dia-small"><span><b>是否需转办填报？</b></span></div>
        <div class="df-result transfer-result">
          <div class="item"><b>否</b>由项目联系人继续提交</div>
          <div class="item"><b>是</b>二级单位管理员转办指定人员</div>
        </div>
        <div class="df-box df-correction">
          <b>{{ flow.transferred ? '已转办：指定人员补充材料' : '转办后补充材料' }}</b>
          <small>补充完成后回到申报材料填报节点</small>
          <div class="df-person"><span class="df-who">{{ personBlock(contactNode) }}</span></div>
        </div>
        <div class="df-arr">↓</div>

        <div class="df-dia df-dia-small"><span><b>项目是否需要审批？</b></span></div>
        <div class="df-lab">{{ flow.needApproval ? '是 · 进入渠道审签' : '否 · 直接线上报备' }}</div>
        <div class="df-arr">↓</div>

        <!-- 需审批：红框审签 -->
        <template v-if="flow.needApproval">
          <div class="df-red">
            <b>按项目渠道走差异化线上审签</b>
            <small>
              {{ [flow.channelLevel, flow.channel].filter(Boolean).join(' / ') || '国家级 / 地方级 / 公司级' }}
              · 已走过 {{ walkedCount }} / {{ flow.nodes.length }} 个节点
              · {{ flowExpanded ? '已展开完整流程' : '下列为当前关注的审签节点' }}
            </small>
            <div v-if="!flowExpanded && chainView.hidden" class="done-fold">
              已折叠 {{ chainView.hidden }} 个审签节点（点击下方按钮查看所有走过的流程）
            </div>
            <div class="df-chain">
              <div
                v-for="(n, idx) in chainNodes"
                :key="n.title + idx"
                class="df-step"
                :class="n.status"
              >
                <span class="n">{{ flow.nodes.indexOf(n) + 1 }}</span>
                <div class="meta">
                  <div class="role">{{ n.title }}<template v-if="n.dept"> · {{ n.dept }}</template></div>
                  <div class="name">{{ who(n) }}</div>
                </div>
                <span class="df-st" :class="n.status">{{ statusLabel(n.status) || '待办' }}</span>
              </div>
            </div>
            <a-button type="primary" class="df-open" @click="flowExpanded = !flowExpanded">
              {{ flowExpanded ? '收起审批节点' : '展开所有走过流程' }}
            </a-button>
          </div>
          <div class="df-arr">↓</div>
          <div class="df-dia"><span><b>审批结果</b></span></div>
          <div class="df-result">
            <div class="item warn"><b>驳回</b>退回补正材料</div>
            <div class="item"><b>通过</b>同步台账继续</div>
            <div class="item warn"><b>不立项</b>申报终止</div>
          </div>
          <div class="df-lab">通过</div>
          <div class="df-arr">↓</div>
        </template>

        <!-- 无需审批 -->
        <template v-else>
          <div class="df-info">
            <b>本项目无需审批，直接线上报备</b>
            <small>不走多级审签，提交后即归档并同步台账</small>
            <div class="df-chain">
              <div v-for="(n, idx) in flow.nodes" :key="n.title + idx" class="df-step" :class="n.status">
                <span class="n">{{ idx + 1 }}</span>
                <div class="meta">
                  <div class="role">{{ n.title }}</div>
                  <div class="name">{{ who(n) }}</div>
                </div>
                <span class="df-st" :class="n.status">{{ statusLabel(n.status) || '待办' }}</span>
              </div>
            </div>
          </div>
          <div class="df-arr">↓</div>
        </template>

        <div class="df-box">
          <b>申报流程办结，数据同步台账</b>
          <div class="df-person">
            <span class="df-who">{{ personBlock(contactNode) }}</span>
          </div>
        </div>
        <div class="df-arr">↓</div>
        <div class="df-end">
          <b>申报办结，进入立项备案</b>
        </div>
      </div>
    </div>
  </a-modal>

</template>

<style scoped>
.df-hd {
  padding: 0 0 12px;
  border-bottom: 1px solid var(--zgsf-border);
  margin: -8px 0 16px;
}
.df-hd h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--zgsf-text);
  line-height: 24px;
}
.df-hd p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--zgsf-text-secondary);
  line-height: 20px;
}
.df-body {
  max-height: min(72vh, 780px);
  overflow: auto;
  padding-bottom: 8px;
}
.srpm-now {
  margin: 0 0 14px;
  padding: 12px 14px;
  background: var(--zgsf-brand-soft);
  border: 1px solid #91caff;
  border-radius: var(--zgsf-radius);
}
.df-readonly-tip {
  margin: 0 0 14px;
}
.srpm-now.empty {
  background: var(--zgsf-fill);
  border-color: var(--zgsf-border);
}
.now-hd {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  flex-wrap: wrap;
}
.now-hd b {
  font-size: 13px;
  color: var(--zgsf-header);
  font-weight: 600;
}
.now-hd .tip {
  font-size: 12px;
  color: var(--zgsf-text-secondary);
}
.now-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.now-item {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 12px;
  background: var(--zgsf-card);
  border: 1px solid #91caff;
  border-radius: var(--zgsf-radius);
}
.now-item .ttl {
  flex: 1;
  min-width: 120px;
  font-size: 14px;
  font-weight: 600;
  color: var(--zgsf-text);
}
.now-item .who {
  font-size: 12px;
  color: var(--zgsf-brand);
  font-weight: 600;
}
.now-item .st {
  height: 20px;
  padding: 0 8px;
  border-radius: var(--zgsf-radius);
  font-size: 12px;
  line-height: 20px;
  background: var(--zgsf-brand-soft);
  color: var(--zgsf-brand);
}

.df-chart {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}
.df-box,
.df-end {
  width: min(400px, 100%);
  box-sizing: border-box;
  padding: 8px 12px;
  text-align: center;
  background: var(--zgsf-card);
  border: 1px solid #91caff;
  border-radius: var(--zgsf-radius);
  color: var(--zgsf-text);
  font-size: 13px;
  line-height: 20px;
}
.df-end {
  background: var(--zgsf-brand-softer);
  border-color: var(--zgsf-brand);
}
.df-stage {
  background: var(--zgsf-brand-soft);
  border-color: var(--zgsf-brand);
}
.stage-fork {
  margin-bottom: 0;
}
.active-branch {
  border-color: var(--zgsf-brand);
  background: var(--zgsf-brand-soft);
}
.muted-branch {
  border-style: dashed;
  background: var(--zgsf-fill);
  color: var(--zgsf-text-secondary);
}
.df-correction {
  border-style: dashed;
  border-color: var(--c-yellow);
  background: #fffbe6;
}
.df-box b,
.df-end b,
.df-red b,
.df-info b {
  display: block;
  font-weight: 600;
  color: var(--zgsf-text);
}
.df-box small,
.df-red small,
.df-info small {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--zgsf-text-secondary);
  line-height: 16px;
  font-weight: 400;
}
.df-mats {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}
.df-mat {
  height: 20px;
  padding: 0 8px;
  border-radius: var(--zgsf-radius);
  font-size: 11px;
  line-height: 20px;
  background: #fff7e6;
  color: #d46b08;
  border: 1px solid #ffd591;
}
.df-mat.ok {
  background: #f6ffed;
  color: #389e0d;
  border-color: #b7eb8f;
}
.df-person {
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}
.df-who {
  font-size: 12px;
  color: var(--zgsf-brand);
  font-weight: 600;
  line-height: 18px;
}
.df-st {
  height: 18px;
  padding: 0 6px;
  border-radius: var(--zgsf-radius);
  font-size: 11px;
  line-height: 18px;
  background: var(--zgsf-bg);
  color: var(--zgsf-text-secondary);
}
.df-st.approved,
.df-st.done {
  background: #f6ffed;
  color: #389e0d;
}
.df-st.current {
  background: var(--zgsf-brand-soft);
  color: var(--zgsf-brand);
}
.df-st.rejected {
  background: #fff1f0;
  color: #cf1322;
}
.df-arr {
  color: var(--zgsf-text-secondary);
  font-size: 14px;
  line-height: 18px;
  padding: 2px 0;
}
.df-dia {
  width: 104px;
  height: 104px;
  margin: 6px 0;
  background: var(--zgsf-brand-soft);
  border: 1px solid var(--zgsf-brand);
  transform: rotate(45deg);
  display: flex;
  align-items: center;
  justify-content: center;
}
.df-dia > span {
  display: block;
  width: 88px;
  transform: rotate(-45deg);
  text-align: center;
  color: var(--zgsf-header);
  font-size: 12px;
  line-height: 16px;
}
.df-dia-small {
  width: 92px;
  height: 92px;
}
.df-fork {
  width: min(480px, 100%);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  margin: 2px 0 6px;
  align-items: start;
}
.df-fork .col {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.df-fork .df-box {
  width: 100%;
}
.df-lab {
  font-size: 12px;
  color: var(--zgsf-brand);
  font-weight: 600;
  margin-bottom: 2px;
}
.df-red,
.df-info {
  width: min(460px, 100%);
  box-sizing: border-box;
  padding: 12px;
  text-align: center;
  border-radius: var(--zgsf-radius);
  color: var(--zgsf-text);
  font-size: 13px;
  line-height: 20px;
}
.df-red {
  background: #fff2f0;
  border: 2px solid var(--c-red);
}
.df-info {
  background: var(--zgsf-brand-softer);
  border: 1px solid #91caff;
}
.df-chain {
  margin: 10px 0 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-align: left;
}
.df-step {
  display: grid;
  grid-template-columns: 22px 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 6px 10px;
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius);
  background: var(--zgsf-card);
}
.df-step .n {
  width: 18px;
  height: 18px;
  border-radius: 9px;
  background: var(--zgsf-bg);
  color: var(--zgsf-text-secondary);
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.df-step.current .n {
  background: var(--zgsf-brand);
  color: var(--zgsf-card);
}
.df-step.approved .n,
.df-step.done .n {
  background: var(--c-green);
  color: var(--zgsf-card);
}
.df-step .role {
  font-size: 12px;
  color: var(--zgsf-text-secondary);
  line-height: 16px;
}
.df-step .name {
  font-size: 13px;
  color: var(--zgsf-text);
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.done-fold {
  margin: 8px 0 0;
  padding: 6px 10px;
  border: 1px dashed var(--zgsf-border);
  border-radius: var(--zgsf-radius);
  background: var(--zgsf-fill);
  font-size: 12px;
  color: var(--zgsf-text-secondary);
  text-align: center;
}
.df-open {
  margin-top: 4px;
}
.df-result {
  width: min(460px, 100%);
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
  margin: 4px 0 6px;
  text-align: center;
}
.df-result.transfer-result {
  grid-template-columns: 1fr 1fr;
}
.df-result .item {
  padding: 8px 6px;
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius);
  background: var(--zgsf-fill);
  font-size: 12px;
  color: var(--zgsf-text);
  line-height: 18px;
}
.df-result .item b {
  display: block;
  font-size: 12px;
  color: var(--zgsf-brand);
  margin-bottom: 2px;
}
.df-result .item.warn b {
  color: #cf1322;
}
@media (max-width: 600px) {
  .df-fork,
  .df-result,
  .df-result.transfer-result {
    grid-template-columns: 1fr;
  }
  .df-dia {
    width: 88px;
    height: 88px;
  }
  .df-dia > span {
    width: 72px;
  }
  .df-step {
    grid-template-columns: 22px minmax(0, 1fr);
  }
  .df-step .st {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
