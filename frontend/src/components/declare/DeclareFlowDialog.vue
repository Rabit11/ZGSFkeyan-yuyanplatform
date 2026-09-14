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
          <small v-if="flow.offlineTail">{{ flow.offlineTail }}</small>
        </div>
      </div>
    </div>
  </a-modal>

</template>

<style scoped>
.df-hd {
  padding: 0 0 12px;
  border-bottom: 1px solid #e8e8e8;
  margin: -8px 0 16px;
}
.df-hd h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
  line-height: 24px;
}
.df-hd p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #8c8c8c;
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
  background: #e6f4ff;
  border: 1px solid #91caff;
  border-radius: 4px;
}
.df-readonly-tip {
  margin: 0 0 14px;
}
.srpm-now.empty {
  background: #fafafa;
  border-color: #e8e8e8;
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
  color: #0048a0;
  font-weight: 600;
}
.now-hd .tip {
  font-size: 12px;
  color: #8c8c8c;
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
  background: #fff;
  border: 1px solid #91caff;
  border-radius: 4px;
}
.now-item .ttl {
  flex: 1;
  min-width: 120px;
  font-size: 14px;
  font-weight: 600;
  color: #1f1f1f;
}
.now-item .who {
  font-size: 12px;
  color: #0064ef;
  font-weight: 600;
}
.now-item .st {
  height: 20px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 20px;
  background: #e6f4ff;
  color: #0064ef;
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
  background: #fff;
  border: 1px solid #91caff;
  border-radius: 4px;
  color: #262626;
  font-size: 13px;
  line-height: 20px;
}
.df-end {
  background: #f0f5ff;
  border-color: #0064ef;
}
.df-stage {
  background: #e6f4ff;
  border-color: #0064ef;
}
.stage-fork {
  margin-bottom: 0;
}
.active-branch {
  border-color: #0064ef;
  background: #e6f4ff;
}
.muted-branch {
  border-style: dashed;
  background: #fafafa;
  color: #8c8c8c;
}
.df-correction {
  border-style: dashed;
  border-color: #faad14;
  background: #fffbe6;
}
.df-box b,
.df-end b,
.df-red b,
.df-info b {
  display: block;
  font-weight: 600;
  color: #1f1f1f;
}
.df-box small,
.df-red small,
.df-info small {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #8c8c8c;
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
  border-radius: 4px;
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
  color: #0064ef;
  font-weight: 600;
  line-height: 18px;
}
.df-st {
  height: 18px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 18px;
  background: #f5f7fa;
  color: #8c8c8c;
}
.df-st.approved,
.df-st.done {
  background: #f6ffed;
  color: #389e0d;
}
.df-st.current {
  background: #e6f4ff;
  color: #0064ef;
}
.df-st.rejected {
  background: #fff1f0;
  color: #cf1322;
}
.df-arr {
  color: #8c8c8c;
  font-size: 14px;
  line-height: 18px;
  padding: 2px 0;
}
.df-dia {
  width: 104px;
  height: 104px;
  margin: 6px 0;
  background: #e6f4ff;
  border: 1px solid #0064ef;
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
  color: #0048a0;
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
  color: #0064ef;
  font-weight: 600;
  margin-bottom: 2px;
}
.df-red,
.df-info {
  width: min(460px, 100%);
  box-sizing: border-box;
  padding: 12px;
  text-align: center;
  border-radius: 4px;
  color: #262626;
  font-size: 13px;
  line-height: 20px;
}
.df-red {
  background: #fff2f0;
  border: 2px solid #f5222d;
}
.df-info {
  background: #f0f5ff;
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
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  background: #fff;
}
.df-step .n {
  width: 18px;
  height: 18px;
  border-radius: 9px;
  background: #f0f2f5;
  color: #8c8c8c;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.df-step.current .n {
  background: #0064ef;
  color: #fff;
}
.df-step.approved .n,
.df-step.done .n {
  background: #52c41a;
  color: #fff;
}
.df-step .role {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 16px;
}
.df-step .name {
  font-size: 13px;
  color: #1f1f1f;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.done-fold {
  margin: 8px 0 0;
  padding: 6px 10px;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
  background: #fafafa;
  font-size: 12px;
  color: #8c8c8c;
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
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  background: #fafafa;
  font-size: 12px;
  color: #262626;
  line-height: 18px;
}
.df-result .item b {
  display: block;
  font-size: 12px;
  color: #0064ef;
  margin-bottom: 2px;
}
.df-result .item.warn b {
  color: #cf1322;
}
</style>
