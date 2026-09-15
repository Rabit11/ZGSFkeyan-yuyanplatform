<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { downloadAuthenticatedFile } from '@/utils/authFile'
import { READONLY_FLOW_NOTICE } from '@/utils/flowEntryPolicy'
import {
  buildImplementFlowOpts,
  buildImplementFlowOptsFromOverview,
  currentImplNodes,
  typeLabel,
  who,
  type ImplFlowNode,
  type ImplFlowOpts,
  type ImplLane,
  type ImplLaneSegment,
  type ImplMilestoneFlow,
} from '@/utils/implementFlow'

const props = defineProps<{
  open: boolean
  opts?: ImplFlowOpts | null
  overview?: any
}>()

const emit = defineEmits<{ 'update:open': [boolean] }>()

const view = ref<'milestones' | 'lanes'>('milestones')
const detailsOpen = ref(false)

async function openMaterial(material: any) {
  try {
    await downloadAuthenticatedFile(material?.fileUrl, material?.fileName)
  } catch (e: any) {
    message.error(e?.message || '附件下载失败')
  }
}

const flow = computed<ImplFlowOpts>(() => {
  if (props.opts) return props.opts
  if (props.overview) return buildImplementFlowOptsFromOverview(props.overview)
  return buildImplementFlowOpts({ name: '未选择项目', status: 'IMPLEMENTING' })
})

const subtitle = computed(() =>
  [flow.value.code, flow.value.name, flow.value.ownerLabel ? `负责人：${flow.value.ownerLabel}` : '']
    .filter(Boolean)
    .join(' · '),
)

const nowList = computed(() => currentImplNodes(flow.value))
const msSetup = computed(
  () => flow.value.lanes.find((l) => l.laneCode === 'IMPL_MANAGE')?.segments?.find((s) => s.kind === 'setup')?.nodes || [],
)
const laneSegments = (lane: ImplLane) =>
  lane.segments && lane.segments.length ? lane.segments : [{ kind: 'setup' as const, nodes: lane.nodes }]

function isViewAction(node?: ImplFlowNode | null) {
  return Boolean(node?.actionLabel?.startsWith('查看'))
}

function showHandle(node?: ImplFlowNode | null) {
  if (!node?.actionPath || !node.actionLabel || isViewAction(node)) return false
  return false
}

function showView(node?: ImplFlowNode | null) {
  return Boolean(node?.actionPath) && (node?.nodeType === 'ACTION' || node?.nodeType === 'AUDIT' || node?.nodeType === 'CLOSED')
}

function viewBtnLabel(node?: ImplFlowNode | null) {
  if (node?.actionLabel?.startsWith('查看')) return node.actionLabel
  return '查看'
}

function goView(node: ImplFlowNode) {
  if (!node.actionPath) return
}

function goNode(node: ImplFlowNode) {
  if (showView(node)) {
    goView(node)
    return
  }
  message.info(READONLY_FLOW_NOTICE)
}

function close() {
  emit('update:open', false)
}

function roleLine(node: ImplFlowNode) {
  return node.roleLine || typeLabel(node.nodeType)
}

function boxClass(node: ImplFlowNode) {
  return {
    done: node.status === 'done',
    current: node.status === 'current',
    pending: node.status === 'pending',
    ret: node.status === 'return',
    overdue: node.status === 'overdue',
    't-action': node.nodeType === 'ACTION',
    't-audit': node.nodeType === 'AUDIT',
    't-system': node.nodeType === 'SYSTEM',
    't-closed': node.nodeType === 'CLOSED',
  }
}

const collapseOverride = ref<Record<string, boolean>>({})

watch(
  () => `${flow.value.projectId || ''}:${flow.value.code || ''}`,
  () => {
    collapseOverride.value = {}
  },
)

function hasActiveNodes(nodes: ImplFlowNode[]) {
  return nodes.some((n) => n.status === 'current' || n.status === 'overdue' || n.status === 'return')
}

function defaultCollapsed(nodes: ImplFlowNode[]) {
  return nodes.length > 0 && !hasActiveNodes(nodes)
}

function collapseKey(id: number | string | undefined, fallback: string) {
  return String(id ?? fallback)
}

function isSegCollapsed(seg: ImplLaneSegment) {
  if (seg.kind !== 'milestone') return false
  const key = collapseKey(seg.id, `seg-${seg.seq}-${seg.name}`)
  if (key in collapseOverride.value) return collapseOverride.value[key]
  return defaultCollapsed(seg.nodes)
}

function toggleSeg(seg: ImplLaneSegment) {
  if (seg.kind !== 'milestone') return
  const key = collapseKey(seg.id, `seg-${seg.seq}-${seg.name}`)
  collapseOverride.value = { ...collapseOverride.value, [key]: !isSegCollapsed(seg) }
}

function isMsCollapsed(ms: ImplMilestoneFlow) {
  const key = collapseKey(ms.id, `ms-${ms.seq}-${ms.name}`)
  if (key in collapseOverride.value) return collapseOverride.value[key]
  return defaultCollapsed(ms.nodes)
}

function toggleMs(ms: ImplMilestoneFlow) {
  const key = collapseKey(ms.id, `ms-${ms.seq}-${ms.name}`)
  collapseOverride.value = { ...collapseOverride.value, [key]: !isMsCollapsed(ms) }
}

const allMsCollapsed = computed(() => {
  if (!flow.value.milestoneFlows.length) return false
  return flow.value.milestoneFlows.every((ms) => isMsCollapsed(ms))
})

function setAllMsCollapsed(collapsed: boolean) {
  const next: Record<string, boolean> = { ...collapseOverride.value }
  for (const ms of flow.value.milestoneFlows) {
    next[collapseKey(ms.id, `ms-${ms.seq}-${ms.name}`)] = collapsed
  }
  for (const lane of flow.value.lanes) {
    for (const seg of laneSegments(lane)) {
      if (seg.kind !== 'milestone') continue
      next[collapseKey(seg.id, `seg-${seg.seq}-${seg.name}`)] = collapsed
    }
  }
  collapseOverride.value = next
}

function msTagColor(label: string) {
  if (label === '已完成') return 'success'
  if (label === '逾期') return 'error'
  if (label === '临期') return 'warning'
  return 'processing'
}

function nodeTime(node: ImplFlowNode, planDate?: string, actualDate?: string) {
  const code = String(node.nodeCode || '')
  if (code === 'MS_ANNUAL_LIST') return '填报时间：每年初 / 实施启动时'
  if (code === 'MS_UNIT_ARCHIVE') return '执行时间：节点清单提交后'
  if (code === 'MS_ADD_NODE') return '填报时间：实施期内按需增补'
  if (/-M1$/.test(code)) return `执行时间：启动日 — ${planDate || '计划完成日'}`
  if (/-M2$/.test(code)) return `填报时间：${actualDate || planDate || '计划完成日'}${actualDate ? '（已完成）' : '前'}`
  if (/-M3$/.test(code)) return '执行时间：交付物提交后 5 个工作日内'
  if (/-M4$/.test(code)) return '填报时间：超期或预计延期时'
  if (code.startsWith('FUND_')) return '填报 / 执行时间：全部里程碑节点完成全部审核后'
  if (code.startsWith('EVAL_')) return '填报 / 执行时间：日常进度评估时'
  if (code.startsWith('CHG_')) return '填报 / 执行时间：变更发生时'
  if (code.startsWith('BASIC_')) return '填报 / 执行时间：立项备案后'
  return '执行时间：实施期内'
}

function materialLabel(node: ImplFlowNode) {
  const code = String(node.nodeCode || '')
  if (/-M2$/.test(code)) return '材料：交付物及节点完成佐证'
  if (code.startsWith('EVAL_')) return '材料：评审结论与检查材料'
  if (code.startsWith('CHG_')) return '材料：变更申请及支撑材料'
  if (code.startsWith('FUND_W')) return '材料：核销信息及凭证材料'
  if (code.startsWith('FUND_')) return '材料：经费预算材料'
  if (code === 'MS_ANNUAL_LIST' || code === 'MS_ADD_NODE') return '材料：里程碑及交付物清单'
  return ''
}
</script>

<template>
  <a-modal
    :open="open"
    :title="null"
    :footer="null"
    :width="1280"
    :destroy-on-close="true"
    centered
    class="if-modal"
    wrap-class-name="if-modal"
    @cancel="close"
  >
    <div class="if-hd">
      <div>
        <h2>实施阶段可视化流程</h2>
        <p>{{ subtitle }}</p>
      </div>
      <div class="if-head-actions">
        <a-radio-group v-model:value="view" size="small" button-style="solid">
          <a-radio-button value="milestones">简明流程</a-radio-button>
          <a-radio-button value="lanes">岗位泳道</a-radio-button>
        </a-radio-group>
        <a-button size="small" @click="detailsOpen = !detailsOpen">
          {{ detailsOpen ? '收起项目详情' : '展开项目详情' }}
        </a-button>
      </div>
    </div>

    <div class="if-legend">
      <span class="lg t-action">确认 / 上传</span>
      <span class="lg t-audit">审核</span>
      <span class="lg t-system">系统</span>
      <span class="lg t-closed">流程已结束且合规</span>
    </div>
    <a-alert class="if-readonly-tip" type="info" show-icon :message="READONLY_FLOW_NOTICE" />

    <div class="if-layout" :class="{ 'details-collapsed': !detailsOpen }">
      <div class="if-chart-wrap">
        <div v-if="nowList.length" class="srpm-now">
          <div class="now-hd">
            <b>当前节点 · 实施阶段</b>
            <span class="tip">岗位定责，姓名取平台工号花名册</span>
          </div>
          <div class="now-list">
            <div v-for="(it, i) in nowList" :key="i" class="now-item">
              <span class="ttl">{{ it.title }}</span>
              <span class="who">{{ who(it) }}</span>
              <span class="st" :class="it.status">{{ it.statusLabel }}</span>
              <a-button v-if="showView(it)" size="small" @click="goView(it)">{{ viewBtnLabel(it) }}</a-button>
            </div>
          </div>
        </div>

        <!-- 层 B：按里程碑竖向 -->
        <div v-if="view === 'milestones'" class="if-ms-view">
          <div v-if="flow.milestoneFlows.length" class="if-ms-toolbar">
            <span>按编制清单 · {{ flow.msTotal }} 个节点</span>
            <a-button type="link" size="small" @click="setAllMsCollapsed(!allMsCollapsed)">
              {{ allMsCollapsed ? '全部展开' : '全部收起' }}
            </a-button>
          </div>
          <div v-if="msSetup.length" class="if-ms-block">
            <div class="if-ms-head">
              <b>里程碑管理</b>
              <span class="dt">节点编制与增补</span>
            </div>
            <div class="if-ms-body single">
              <div class="if-ms-main">
                <template v-for="(n, idx) in msSetup" :key="n.nodeCode">
                  <div class="if-box" :class="[boxClass(n), { clickable: showView(n) }]" @click="showView(n) && goView(n)">
                    <b>{{ n.title }}</b>
                    <span class="if-role">{{ roleLine(n) }}</span>
                    <span class="if-time">{{ nodeTime(n) }}</span>
                    <span v-if="materialLabel(n)" class="if-material">{{ materialLabel(n) }}</span>
                    <small v-if="n.desc">{{ n.desc }}</small>
                    <div class="if-person">
                      <span class="if-who">{{ who(n) }}</span>
                      <span class="if-st" :class="n.status">{{ n.statusLabel }}</span>
                    </div>
                    <a-button v-if="showView(n)" size="small" @click.stop="goView(n)">{{ viewBtnLabel(n) }}</a-button>
                  </div>
                  <div v-if="idx < msSetup.length - 1" class="if-arr">↓</div>
                </template>
              </div>
            </div>
          </div>
          <div v-for="ms in flow.milestoneFlows" :key="ms.id" class="if-ms-block">
            <div
              class="ms-seq-bar"
              :class="{ collapsed: isMsCollapsed(ms), active: hasActiveNodes(ms.nodes) }"
              role="button"
              tabindex="0"
              @click="toggleMs(ms)"
              @keydown.enter.prevent="toggleMs(ms)"
            >
              <span class="seq-dot" />
              <b>{{ ms.seq || '' }}</b>
              <span class="ms-name" :title="ms.name">{{ ms.name || `节点${ms.seq}` }}</span>
              <span class="dt">{{ ms.planDate || '—' }}</span>
              <a-tag :color="msTagColor(ms.progressLabel)" class="ms-bar-tag">{{ ms.progressLabel }}</a-tag>
              <span class="ms-fold">{{ isMsCollapsed(ms) ? '展开' : '收起' }}</span>
            </div>
            <div v-show="!isMsCollapsed(ms)" class="if-ms-body single">
              <div class="if-ms-main">
                <template v-for="(n, idx) in ms.nodes" :key="n.nodeCode">
                  <div class="if-box" :class="[boxClass(n), { clickable: showView(n) }]" @click="showView(n) && goView(n)">
                    <b>{{ n.title }}</b>
                    <span class="if-role">{{ roleLine(n) }}</span>
                    <span class="if-time">{{ nodeTime(n, ms.planDate, ms.actualDate) }}</span>
                    <span v-if="materialLabel(n)" class="if-material">{{ materialLabel(n) }}</span>
                    <small v-if="n.desc">{{ n.desc }}</small>
                    <div class="if-person">
                      <span class="if-who">{{ who(n) }}</span>
                      <span class="if-st" :class="n.status">{{ n.statusLabel }}</span>
                    </div>
                    <a-button v-if="showView(n)" size="small" @click.stop="goView(n)">{{ viewBtnLabel(n) }}</a-button>
                  </div>
                  <div v-if="idx < ms.nodes.length - 1" class="if-arr">↓</div>
                </template>
              </div>
            </div>
            <div v-if="!isMsCollapsed(ms)" class="if-ms-materials">
              <span class="mat-label">节点材料</span>
              <template v-if="ms.materials.length">
                <a v-for="a in ms.materials" :key="a.code" href="#" @click.prevent="openMaterial(a)">
                  {{ a.fileName || a.name }}（{{ a.uploaded ? '查看' : '待上传' }}）
                </a>
              </template>
              <span v-else class="mat-empty">暂无已上传材料，请从左侧任务栏进入“实施阶段 / 里程碑填报”办理</span>
            </div>
          </div>
          <a-empty v-if="!flow.milestoneFlows.length" description="暂无里程碑，请先编制里程碑节点" />

          <div v-if="flow.fundNodes?.length" class="if-ms-block fund-chain">
            <div class="if-ms-head">
              <b>项目经费 · 预算填报与核销</b>
              <a-tag color="blue" class="ms-tag">全部节点闭环后启动</a-tag>
            </div>
            <div class="if-ms-body">
              <div class="if-ms-main">
                <template v-for="(n, idx) in flow.fundNodes" :key="n.nodeCode">
                  <div class="if-box" :class="[boxClass(n), { clickable: showView(n) }]" @click="showView(n) && goView(n)">
                    <b>{{ n.title }}</b>
                    <span class="if-time">{{ nodeTime(n) }}</span>
                    <span v-if="materialLabel(n)" class="if-material">{{ materialLabel(n) }}</span>
                    <small v-if="n.desc">{{ n.desc }}</small>
                    <div class="if-person">
                      <span class="if-who">{{ who(n) }}</span>
                      <span class="if-st" :class="n.status">{{ n.statusLabel }}</span>
                    </div>
                    <a-button v-if="showView(n)" type="link" size="small" @click.stop="goView(n)">{{ viewBtnLabel(n) }}</a-button>
                  </div>
                  <div v-if="idx < flow.fundNodes.length - 1" class="if-arr">↓</div>
                </template>
              </div>
            </div>
          </div>
        </div>

        <!-- 层 A：五列泳道 -->
        <div v-else class="if-lanes">
            <div v-for="lane in flow.lanes" :key="lane.laneCode" class="if-lane">
            <div class="if-lane-hd">
              <div>{{ lane.title }}</div>
              <small v-if="lane.laneCode === 'IMPL_MANAGE'">
                {{ flow.msTotal }} 个节点（按编制清单）
                <a
                  v-if="flow.milestoneFlows.length"
                  class="lane-fold-all"
                  @click.stop="setAllMsCollapsed(!allMsCollapsed)"
                >{{ allMsCollapsed ? '全部展开' : '全部收起' }}</a>
              </small>
              <small v-else-if="lane.ownerLabel">负责 · {{ lane.ownerLabel }}</small>
            </div>
            <template v-for="(seg, sIdx) in laneSegments(lane)" :key="`${lane.laneCode}-${sIdx}`">
              <div
                v-if="seg.kind === 'milestone'"
                class="ms-seq-bar compact"
                :class="{ collapsed: isSegCollapsed(seg), active: hasActiveNodes(seg.nodes) }"
                role="button"
                tabindex="0"
                @click="toggleSeg(seg)"
                @keydown.enter.prevent="toggleSeg(seg)"
              >
                <span class="seq-dot" />
                <b>{{ seg.seq }}</b>
                <span class="ms-name" :title="seg.name">{{ seg.name || `节点${seg.seq}` }}</span>
                <span class="dt">{{ seg.date || '—' }}</span>
                <a-tag v-if="seg.progressLabel" :color="msTagColor(seg.progressLabel)" class="ms-bar-tag">{{ seg.progressLabel }}</a-tag>
                <span class="ms-fold">{{ isSegCollapsed(seg) ? '展开' : '收起' }}</span>
              </div>
              <template v-if="!isSegCollapsed(seg)">
                <template v-for="(n, idx) in seg.nodes" :key="n.nodeCode">
                  <div class="if-box compact" :class="[boxClass(n), { clickable: showView(n) }]" @click="showView(n) && goView(n)">
                    <b>{{ n.title }}</b>
                    <span class="if-role">{{ roleLine(n) }}</span>
                    <span class="if-time">{{ nodeTime(n, seg.date) }}</span>
                    <span v-if="materialLabel(n)" class="if-material">{{ materialLabel(n) }}</span>
                    <small v-if="n.desc && n.nodeType === 'ACTION'">{{ n.desc }}</small>
                    <div class="if-person">
                      <span class="if-who">{{ who(n) }}</span>
                      <span class="if-st" :class="n.status">{{ n.statusLabel }}</span>
                    </div>
                    <a-button v-if="showView(n)" size="small" @click.stop="goView(n)">{{ viewBtnLabel(n) }}</a-button>
                  </div>
                  <div v-if="idx < seg.nodes.length - 1" class="if-arr">↓</div>
                </template>
              </template>
              <div v-if="sIdx < laneSegments(lane).length - 1" class="if-arr">↓</div>
            </template>
          </div>
        </div>
      </div>

      <aside v-if="detailsOpen" class="if-panel">
        <div class="fp-hd">
          <div class="fp-title">实施阶段 · 详情与附件</div>
          <a-tag :color="flow.panelStatus === 'DONE' ? 'success' : 'processing'">{{ flow.panelStatusLabel }}</a-tag>
        </div>

        <div class="fp-meta">
          <div><span class="k">项目编号</span>{{ flow.code || '—' }}</div>
          <div><span class="k">项目名称</span>{{ flow.name || '—' }}</div>
          <div><span class="k">项目负责人</span>{{ flow.ownerLabel }}</div>
          <div><span class="k">所在部门</span>{{ flow.dept || '—' }}</div>
          <div><span class="k">当前阶段</span>实施阶段</div>
          <div>
            <span class="k">里程碑进度</span>{{ flow.msDone }}/{{ flow.msTotal }} 节点完成
          </div>
        </div>

        <div class="fp-actions">
          <a-tag color="blue">{{ flow.nextAction }}</a-tag>
          <span class="fp-route-tip">请从左侧任务栏进入对应功能页面办理</span>
          <a-button size="small" @click="view = view === 'lanes' ? 'milestones' : 'lanes'">
            {{ view === 'lanes' ? '查看里程碑流转' : '查看泳道总览' }}
          </a-button>
        </div>

        <div class="fp-block">
          <div class="fp-label">办理说明</div>
          <p>{{ flow.processHint }}</p>
        </div>

        <div class="fp-block">
          <div class="fp-label">年度目标</div>
          <p>{{ flow.annualGoal }}</p>
        </div>

        <div class="fp-block">
          <div class="fp-label">里程碑通过线</div>
          <ul class="fp-ms">
            <li v-for="ms in flow.milestoneFlows" :key="ms.id">
              <span class="n">{{ ms.seq }} · {{ ms.name }}</span>
              <span class="d">{{ ms.planDate || '—' }}</span>
              <a-tag :color="msTagColor(ms.progressLabel)" class="tag">{{ ms.progressLabel }}</a-tag>
            </li>
            <li v-if="!flow.milestoneFlows.length" class="fp-empty">暂无编制节点</li>
          </ul>
        </div>

        <div class="fp-block">
          <div class="fp-label">关键岗位</div>
          <ul class="fp-posts">
            <li v-for="p in flow.posts" :key="p.role"><span>{{ p.role }}</span>{{ p.name }}</li>
          </ul>
        </div>

        <div class="fp-block">
          <div class="fp-label">实施人员</div>
          <div class="fp-staff">
            <div v-for="s in flow.staff" :key="s.role + s.label" class="staff-card">
              <b>{{ s.name || s.label }}</b>
              <span>{{ s.role }}</span>
            </div>
          </div>
        </div>

        <div class="fp-block">
          <div class="fp-label">对应功能页面</div>
          <ul class="fp-mods">
            <li v-for="m in flow.modules" :key="m.code">
              <span>{{ m.title }}</span>
              <em>{{ m.entryType }}</em>
              <em>{{ m.path }}</em>
            </li>
          </ul>
        </div>

        <div class="fp-block">
          <div class="fp-label">附件资料</div>
          <div v-for="a in flow.attachments" :key="a.code" class="fp-att">
            <div class="name">
              {{ a.name }}
              <a-tag :color="a.uploaded ? 'success' : 'default'" class="tag">{{ a.uploaded ? '已传' : '未传' }}</a-tag>
            </div>
            <div v-if="a.uploaded" class="file">
              <a href="#" @click.prevent="openMaterial(a)">{{ a.fileName }}</a>
              <span class="t">{{ a.uploadedAt }}</span>
            </div>
          </div>
        </div>

        <div class="fp-block">
          <div class="fp-label">审批 / 办理进度</div>
          <a-timeline v-if="flow.timeline.length">
            <a-timeline-item v-for="(t, i) in flow.timeline" :key="i" color="blue">
              <div class="tl-title">{{ t.title }}</div>
              <div class="tl-meta">{{ t.operator }} · {{ t.at }}</div>
              <div v-if="t.result || t.comment" class="tl-cmt">
                {{ t.result }}<template v-if="t.comment"> · {{ t.comment }}</template>
              </div>
            </a-timeline-item>
          </a-timeline>
          <div v-else class="fp-empty">暂无办理记录</div>
        </div>
      </aside>
    </div>
  </a-modal>
</template>

<style scoped>
.if-hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--zgsf-border-light);
}
.if-hd h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--zgsf-text);
  line-height: 24px;
}
.if-hd p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--zgsf-text-secondary);
  line-height: 20px;
}
.if-head-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
.if-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 12px;
}
.if-legend .lg {
  font-size: 12px;
  color: var(--zgsf-text-subtle);
  padding: 2px 8px;
  border-radius: var(--zgsf-radius);
  border: 1px solid var(--zgsf-border);
  line-height: 20px;
}
.if-legend .t-action { border-color: var(--zgsf-brand); color: var(--zgsf-brand); background: var(--zgsf-brand-soft); }
.if-legend .t-audit { border-color: var(--c-green); color: #389e0d; background: #f6ffed; }
.if-legend .t-system { border-color: #d48806; color: #d48806; background: #fff7e6; }
.if-legend .t-closed { border-color: #722ed1; color: #531dab; background: #f9f0ff; }
.if-readonly-tip {
  margin: 0 0 12px;
}

.if-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  min-height: 480px;
}
.if-layout.details-collapsed {
  grid-template-columns: minmax(0, 1fr);
}
.if-chart-wrap {
  overflow: auto;
  padding-right: 4px;
}
.if-panel {
  overflow: auto;
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius);
  background: var(--zgsf-fill);
  padding: 12px 14px;
}
.fp-route-tip {
  font-size: 12px;
  color: var(--zgsf-text-secondary);
  line-height: 24px;
}

.srpm-now {
  margin: 0 0 14px;
  padding: 12px 14px;
  background: var(--zgsf-brand-soft);
  border: 1px solid #91caff;
  border-radius: var(--zgsf-radius);
}
.now-hd {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  flex-wrap: wrap;
}
.now-hd b { font-size: 13px; color: var(--zgsf-header); font-weight: 600; }
.now-hd .tip { font-size: 12px; color: var(--zgsf-text-secondary); }
.now-list { display: flex; flex-direction: column; gap: 8px; }
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
.now-item .ttl { flex: 1; min-width: 120px; font-size: 14px; font-weight: 600; color: var(--zgsf-text); }
.now-item .who { font-size: 12px; color: var(--zgsf-brand); font-weight: 600; }
.now-item .st {
  height: 20px;
  padding: 0 8px;
  border-radius: var(--zgsf-radius);
  font-size: 12px;
  line-height: 20px;
  background: var(--zgsf-brand-soft);
  color: var(--zgsf-brand);
}
.now-item .st.overdue,
.now-item .st.return { background: #fff1f0; color: #cf1322; }

.if-ms-block.fund-chain {
  border: 1px solid #ffccc7;
  border-radius: var(--zgsf-radius-card);
  padding: 12px 12px 4px;
  background: #fff7f6;
}
.if-ms-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.if-ms-head b { font-size: 14px; color: var(--zgsf-header); }
.if-ms-head .dt { font-size: 12px; color: var(--zgsf-text-secondary); }
.ms-tag { margin: 0; }
.if-ms-body.single { grid-template-columns: minmax(0, 1fr); }
.if-ms-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--zgsf-text-secondary);
}
.if-ms-toolbar .ant-btn { padding: 0 4px; height: 22px; }
.ms-seq-bar {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0 6px;
  padding: 6px 10px;
  background: var(--zgsf-bg);
  border: 1px solid transparent;
  border-radius: var(--zgsf-radius);
  box-sizing: border-box;
  cursor: pointer;
  user-select: none;
}
.ms-seq-bar:hover {
  background: var(--zgsf-brand-soft);
  border-color: #91caff;
}
.ms-seq-bar.collapsed {
  background: var(--zgsf-fill);
  border-color: var(--zgsf-border);
}
.ms-seq-bar.active {
  background: var(--zgsf-brand-soft);
  border-color: #91caff;
}
.ms-seq-bar.compact { margin: 10px 0 6px; padding: 6px 8px; flex-wrap: wrap; row-gap: 2px; }
.ms-seq-bar .seq-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fa8c16;
  flex-shrink: 0;
}
.ms-seq-bar b { color: var(--zgsf-brand); font-size: 16px; line-height: 22px; flex-shrink: 0; }
.ms-seq-bar .dt { color: var(--zgsf-text-secondary); font-size: 12px; flex-shrink: 0; }
.ms-seq-bar .ms-name {
  flex: 1;
  min-width: 0;
  color: var(--zgsf-text);
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ms-seq-bar.compact .ms-name {
  flex: 1 0 100%;
  order: 10;
  margin-left: 16px;
}
.ms-seq-bar .ms-bar-tag { margin: 0; flex-shrink: 0; }
.ms-seq-bar.compact .ms-bar-tag { display: none; }
.ms-seq-bar .ms-fold {
  flex-shrink: 0;
  color: var(--zgsf-brand);
  font-size: 12px;
  line-height: 20px;
}
.lane-fold-all {
  display: inline-block;
  margin-left: 6px;
  color: var(--zgsf-brand);
  cursor: pointer;
}
.if-role {
  display: inline-block;
  margin-top: 4px;
  padding: 0 8px;
  height: 20px;
  line-height: 20px;
  border-radius: var(--zgsf-radius);
  background: var(--zgsf-brand-soft);
  color: var(--zgsf-brand);
  font-size: 11px;
}
.if-time,
.if-material {
  display: block;
  margin-top: 5px;
  font-size: 11px;
  line-height: 17px;
}
.if-time {
  color: var(--zgsf-text-subtle);
}
.if-material {
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: var(--zgsf-radius);
  padding: 2px 6px;
}
.if-ms-materials {
  margin-top: 10px;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius);
  background: var(--zgsf-fill);
  font-size: 12px;
}
.if-ms-materials .mat-label { color: var(--zgsf-text-subtle); font-weight: 600; }
.if-ms-materials a { color: var(--zgsf-brand); }
.if-ms-materials .mat-empty { color: var(--zgsf-text-disabled); }
.if-ms-main,
.if-ms-side,
.if-lane {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.if-lanes {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
}
.if-lane-hd {
  width: 100%;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--zgsf-header);
  background: var(--zgsf-brand-soft);
  border: 1px solid #91caff;
  border-radius: var(--zgsf-radius);
  padding: 6px 8px;
  margin-bottom: 8px;
}
.if-lane-hd small {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  font-weight: 400;
  color: var(--zgsf-text-secondary);
}

.if-box.clickable { cursor: pointer; }
.if-box {
  width: min(420px, 100%);
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
.if-box.compact { width: 100%; padding: 8px; font-size: 12px; }
.if-box.compact b { font-size: 12px; }
.if-box.done { border-color: var(--c-green); background: #f6ffed; }
.if-box.current { border-color: var(--zgsf-brand); border-width: 2px; background: var(--zgsf-brand-soft); }
.if-box.pending { border-color: var(--zgsf-border); background: var(--zgsf-fill); color: var(--zgsf-text-secondary); }
.if-box.overdue,
.if-box.ret,
.if-box.ret-box { border-color: var(--c-red); border-width: 2px; background: #fff2f0; }
.if-box.ret-box.pending { border-style: dashed; opacity: 0.85; border-width: 1px; }
.if-box.t-system { border-color: var(--zgsf-border); background: #f5f5f5; }
.if-box.t-system.pending { border-color: var(--zgsf-border); background: #f5f5f5; color: var(--zgsf-text-secondary); }
.if-box.t-system.done { border-color: var(--zgsf-border); background: #f5f5f5; }
.if-box.t-audit { border-color: #d4b106; background: #fffbe6; }
.if-box.t-audit.pending { border-color: #ffe58f; background: #fffbe6; color: var(--zgsf-text); }
.if-box.t-audit.done { border-color: var(--c-green); background: #f6ffed; }
.if-box.t-action { border-color: #91caff; background: var(--zgsf-card); }
.if-box.t-action.pending { border-color: #91caff; background: var(--zgsf-card); color: var(--zgsf-text); }
.if-box.t-closed.done { border-color: #722ed1; background: #f9f0ff; }
.if-box b { display: block; font-weight: 600; color: var(--zgsf-text); }
.if-box small { display: block; margin-top: 2px; font-size: 11px; color: var(--zgsf-text-secondary); line-height: 16px; }
.if-person {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}
.if-who { font-size: 12px; color: var(--zgsf-brand); font-weight: 600; line-height: 18px; }
.if-st {
  height: 18px;
  padding: 0 6px;
  border-radius: var(--zgsf-radius);
  font-size: 11px;
  line-height: 18px;
  background: var(--zgsf-bg);
  color: var(--zgsf-text-secondary);
}
.if-st.done { background: #f6ffed; color: #389e0d; }
.if-st.current { background: var(--zgsf-brand-soft); color: var(--zgsf-brand); }
.if-st.return,
.if-st.overdue { background: #fff1f0; color: #cf1322; }
.if-type { display: block; margin-top: 4px; font-size: 11px; color: var(--zgsf-text-secondary); }
.if-arr { color: var(--zgsf-text-secondary); font-size: 14px; line-height: 18px; padding: 2px 0; }
.if-arr.muted { font-size: 12px; color: #cf1322; margin-top: 4px; }

.fp-hd { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
.fp-title { font-size: 14px; font-weight: 600; color: var(--zgsf-text); }
.fp-meta { font-size: 12px; color: var(--zgsf-text); line-height: 22px; margin-bottom: 10px; }
.fp-meta .k { display: inline-block; width: 72px; color: var(--zgsf-text-secondary); }
.fp-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.fp-block { margin-bottom: 14px; }
.fp-label { font-size: 13px; font-weight: 600; color: var(--zgsf-text); margin-bottom: 6px; }
.fp-block p { margin: 0; font-size: 12px; color: var(--zgsf-text-subtle); line-height: 20px; }
.fp-posts,
.fp-ms,
.fp-mods { list-style: none; margin: 0; padding: 0; }
.fp-posts li,
.fp-ms li,
.fp-mods li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  line-height: 24px;
  color: var(--zgsf-text);
  border-bottom: 1px dashed var(--zgsf-border-light);
}
.fp-posts li span,
.fp-ms li .n { color: var(--zgsf-text-secondary); flex-shrink: 0; }
.fp-ms li .n { color: var(--zgsf-text); flex: 1; min-width: 0; }
.fp-ms li .d { color: var(--zgsf-text-secondary); }
.fp-ms .tag,
.fp-att .tag { margin: 0; font-size: 11px; line-height: 18px; }
.fp-staff { display: flex; flex-wrap: wrap; gap: 8px; }
.staff-card {
  min-width: 88px;
  background: var(--zgsf-card);
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius);
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.staff-card b { font-size: 12px; color: var(--zgsf-text); }
.staff-card span { font-size: 11px; color: var(--zgsf-text-secondary); }
.fp-mods em { font-style: normal; font-size: 11px; color: var(--zgsf-text-secondary); }
.fp-att { padding: 8px 0; border-bottom: 1px solid var(--zgsf-border-light); }
.fp-att .name { font-size: 13px; color: var(--zgsf-text); font-weight: 500; display: flex; align-items: center; gap: 8px; }
.fp-att .file { margin-top: 4px; font-size: 12px; display: flex; justify-content: space-between; gap: 8px; }
.fp-att .file a { color: var(--zgsf-brand); }
.fp-att .t { color: var(--zgsf-text-secondary); flex-shrink: 0; }
.tl-title { font-size: 13px; font-weight: 600; color: var(--zgsf-text); }
.tl-meta { font-size: 12px; color: var(--zgsf-text-secondary); }
.tl-cmt { font-size: 12px; color: #389e0d; }
.fp-empty { font-size: 12px; color: var(--zgsf-text-secondary); }

@media (max-width: 1100px) {
  .if-layout { grid-template-columns: 1fr; }
  .if-lanes { grid-template-columns: 1fr 1fr; }
  .if-ms-body { grid-template-columns: 1fr; }
}
@media (max-width: 600px) {
  .if-lanes,
  .if-accept-grid,
  .if-tf-grid {
    grid-template-columns: 1fr;
  }
  .now-item,
  .if-ms-head,
  .ms-seq-bar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

<style>
.if-modal .ant-modal-body {
  padding: 16px 20px 20px;
  max-height: calc(100vh - 72px);
  overflow: auto;
}
.if-modal .ant-modal-content {
  padding: 0;
}
</style>
