<script setup lang="ts">
import {
  computed,
  nextTick,
  ref,
  watch,
  onMounted,
  onBeforeUnmount,
} from "vue";
import { onBeforeRouteLeave } from "vue-router";
import { Modal, message } from "ant-design-vue";
import { useUserStore } from "@/stores/user";
import ChangeLab from "./change/ChangeLab.vue";
import ChangeObjectPicker from "./change/ChangeObjectPicker.vue";
import { labSession, restoreChangeSession, loginChangeLab, clearChangeSession } from "@/api/changeSession";
restoreChangeSession();
import scenarios from "./change/scenarios.json";
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  UploadOutlined,
  FileTextOutlined,
  SwapOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons-vue";
import { useChangeWorkbench } from "./change/useChangeWorkbench";
import { actions, categories, formatSize, statuses } from "./change/policy";

const {
  testMode,
  rows,
  total,
  loading,
  busy,
  contextLoading,
  error,
  formError,
  open,
  mode,
  detail,
  projects,
  channels,
  legalReviewers,
  query,
  form,
  selectedToken,
  selectedProject,
  availableTargets,
  selectedTarget,
  selectedItems, removeItem, selectObjects, itemTarget, itemProblem, attemptedSave,
  isMajor,
  preview,
  dirty,
  canCreate,
  load,
  context,
  create,
  view,
  close,
  projectChanged,
  typeChanged,
  targetChanged,
  save,
  submit,
  remove,
  upload,
  removeFile,
  download,
  reviewOpen,
  review,
  beginReview,
  finishReview,
} = useChangeWorkbench();
const formalUser = useUserStore();
const user = computed(() => labSession.value || formalUser);
const enteringLab = ref(false), entryError = ref('');
async function enterLab() {
  enteringLab.value = true; entryError.value = '';
  try { await loginChangeLab(); location.reload(); }
  catch (e:any) { entryError.value = e.message; }
  finally { enteringLab.value = false; }
}
function exitLab() { clearChangeSession(); location.reload(); }
const activeSection = ref("content");
const pickerOpen = ref(false);
const handlerText = (people: any[] = []) => people.length ? people.map(u => `${u.name}（${u.employeeNo}）`).join("、") + (people.length > 1 ? "（任一办理）" : "") : "尚未配置办理人";
const selectedCategories = computed(() => [...new Set(selectedItems.value.map(i => i.category))]);
const completedItems = computed(() => selectedItems.value.filter(i => !itemProblem(i)).length);
function confirmObjects(tokens: string[]) {
  const removed = selectedItems.value.filter(i => !tokens.includes(`${i.targetKey}:${i.targetId}`) && String(i.afterValue || '').trim());
  const apply = () => { if (selectObjects(tokens)) pickerOpen.value = false; };
  if (removed.length) Modal.confirm({ title: '确认移除已填写的对象？', content: removed.map(i => i.targetLabel).join('；'), okText: '确认更新选择', cancelText: '返回选择', onOk: apply });
  else apply();
}
watch(open, value => { if (!value) pickerOpen.value = false; });
const materialGuides: Record<string, string> = {
        FUND: "建议提供：经费调整测算表、资金来源及影响说明。",
        PERIOD: "建议提供：新旧进度计划、延期原因及交付节点影响说明。",
        OUTSOURCE: "建议提供：合作方资质、外协范围及合同调整依据。",
        MILESTONE_DELAY: "建议提供：延期原因、纠偏计划及后续节点影响说明。",
        LEVEL: "建议提供：层级或渠道调整的批复、特殊调整依据。",
        BASIC: "建议提供：原始登记材料与正确内容的核对依据。",
        ANNUAL: "建议提供：年度任务原件及纠错依据。",
        PAYMENT: "建议提供：付款安排、合同约定及影响说明。",
        INDICATOR: "建议提供：指标调整论证与目标影响说明。",
        DELIVERABLE: "建议提供：交付清单、调整依据及验收影响说明。",
};
const materialGuide = computed(() => [...new Set([form.category, ...selectedItems.value.map(i => i.category)])]
  .map(c => materialGuides[c]).filter(Boolean).join(' ') || "选择变更对象后，查看对应材料建议。");
const supportCount = computed(
  () =>
    (detail.value?.attachments || []).filter((f: any) => f.kind === "SUPPORT")
      .length,
);
const nextTask = computed(() => {
  const d = detail.value;
  if (!d) return "选择项目与对象，填写调整内容并保存，再上传支撑材料。";
  if (d.legacy)
    return "此历史申请缺少对象关联，只能查阅；需继续调整时重新发起受控申请。";
  if (d.status === "APPROVED")
    return "变更已生效，可在内容对照和办理履历中核验结果。";
  if (d.canArchive) return "上传线下回执，在页底完成线下归档后才会回写。";
  if (d.canAudit) return "核对内容、材料及前序意见后，点击页底“办理审核”。";
  if (d.canSubmit)
    return supportCount.value
      ? "确认支撑材料和审批路径后，点击页底“一次提交”。"
      : "先上传至少一份支撑材料，再提交审批。";
  if (d.canEdit && user.value.identityCode === "techLead")
    return "保存并上传材料后，请本项目负责人在“待我办理”中确认提交。";
  if (d.canEdit) return "查看驳回意见并补正，保存当前修改后再提交。";
  return "申请正在流转，当前账号可查阅内容、材料及办理履历。";
});
const checklist = computed(() => [
  { title: "关联变更对象", done: !!form.targetKey || !!selectedItems.value.length, section: "content" },
  {
    title: "填写调整内容与依据",
    done:
      !!String(form.title || "").trim() &&
      (!!selectedItems.value.length && selectedItems.value.every(i => !!String(i.afterValue || "").trim())) &&
      !!String(form.reason || "").trim(),
    section: "content",
  },
  {
    title: "保存当前内容",
    done: !!detail.value?.id && !dirty.value,
    section: "content",
  },
  { title: "上传支撑材料", done: supportCount.value > 0, section: "materials" },
]);
function jump(section: string) {
  activeSection.value = section;
  document
    .getElementById(`change-${section}`)
    ?.scrollIntoView({ behavior: "smooth", block: "start" });
}
async function startScenario(s: (typeof scenarios)[number]) {
  const p = projects.value.find((p) => p.projectNo === `LAB_CHANGE_${s.id}`);
  if (!p?.canCreate) {
    message.warning(
      "演练项目未初始化或当前身份无权发起，请按场景说明选择身份。",
    );
    return;
  }
  create();
  form.projectId = p.id;
  await projectChanged();
  form.changeType = s.type;
  typeChanged();
  const t = availableTargets.value.find((t) => t.key === s.target);
  if (!t) {
    message.warning("场景对象当前不可调整，请查看该项目状态。");
    return;
  }
  const requested = [{ key: s.target, after: s.after }, ...((s as any).additionalTargets || [])];
  const selected = requested.map((item: any) => availableTargets.value.find(t => t.key === item.key));
  if (selected.some((item: any) => !item)) { message.warning("场景部分对象不可调整，请核对项目状态。"); return; }
  selectObjects(selected.map((item: any) => `${item.key}:${item.id}`));
  form.title = `${s.id} ${s.title}`;
  form.reason = `演练依据：${s.goal}`;
  form.items.forEach((item: any, index: number) => { item.afterValue = requested[index].after; });
  if (isMajor.value)
    form.legalReviewerId = legalReviewers.value.find(
      (u) => u.employeeNo === "100009",
    )?.id;
}
function confirmLeave() {
  if (busy.value) {
    message.warning("操作正在保存，请稍候再离开");
    return false;
  }
  if (!open.value || !dirty.value) return true;
  return new Promise<boolean>((resolve) =>
    Modal.confirm({
      title: "离开项目变更并放弃未保存内容？",
      content: "已保存的申请和附件会保留。未保存内容离开后无法恢复。",
      okText: "放弃并离开",
      cancelText: "继续编辑",
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    }),
  );
}
onBeforeRouteLeave(confirmLeave);
function beforeUnload(event: BeforeUnloadEvent) {
  if ((open.value && dirty.value) || busy.value) {
    event.preventDefault();
    event.returnValue = "";
  }
}
onMounted(() => window.addEventListener("beforeunload", beforeUnload));
onBeforeUnmount(() => window.removeEventListener("beforeunload", beforeUnload));
const drawerTop = ref<HTMLElement>();
watch([open, mode], async () => {
  activeSection.value = "content";
  await nextTick();
  if (open.value) drawerTop.value?.scrollIntoView({ block: "start" });
});
const createProjects = computed(() =>
  projects.value.filter((p) => p.canCreate),
);
const color = (status: string) =>
  ({
    APPROVED: "success",
    REJECTED: "error",
    APPROVING: "processing",
    AWAITING_ARCHIVE: "warning",
  })[status] || "default";
const filterOption = (value: string, option: any) =>
  String(option.label || "")
    .toLowerCase()
    .includes(value.toLowerCase());
const columns = [
  { title: "变更申请 / 单号", key: "application", width: 260 },
  { title: "关联项目", key: "project", width: 230 },
  { title: "类型 / 事项", key: "type", width: 160 },
  { title: "审批状态", key: "state", width: 140 },
  { title: "当前节点", dataIndex: "flowNode", width: 190 },
  { title: "操作", key: "act", width: 166, fixed: "right" as const },
];
</script>

<template>
  <div class="page-container change-workbench" data-testid="change-workbench">
    <div class="change-heading">
      <div class="app-symbol"><SwapOutlined /></div>
      <div class="workspace-title">
        <div class="eyebrow">项目实施 / CHANGE MANAGEMENT</div>
        <h2 class="page-title">项目变更</h2>
      </div>
      <div class="workspace-actions">
        <span class="workspace-mark">变更工作台</span
        ><a-button
          v-if="!testMode"
          :loading="enteringLab"
          @click="enterLab"
          >进入测试模式</a-button
        >
      </div>
    </div>
    <div class="workspace-context">
      <span
        ><span class="context-dot" /> {{ user.realName }} ·
        {{ user.identity || "项目参与人" }}</span
      ><span>对象关联 / 内容对照 / 审批留痕</span>
    </div>
    <a-alert v-if="entryError" :message="entryError" type="error" show-icon class="section-alert"><template #action><a-button @click="enterLab" :loading="enteringLab">重试进入</a-button></template></a-alert>
    <div v-if="labSession" class="lab-connection"><span>演练身份仅在项目变更内生效；其他页面仍使用原平台账号。</span><a-button @click="enterLab" :loading="enteringLab">重新连接演练</a-button><a-button @click="exitLab">退出测试模式</a-button></div>
    <ChangeLab v-if="testMode" @start="startScenario" />
    <div class="workbench-guide">
      <div>
        <b>从变更对象开始</b>
        <p>
          项目计划调整选择“项目变更”；录入错误修正选择“数据变更”。最终审批或线下归档完成后生效。
        </p>
      </div>
      <div class="guide-sequence">
        <span>01 明确对象</span><i>›</i><span>02 内容与材料</span><i>›</i
        ><span>03 审批与生效</span>
      </div>
    </div>
    <a-alert
      v-if="formError && !open"
      class="section-alert"
      type="error"
      show-icon
      :message="formError"
      ><template #action
        ><a-button size="small" @click="context()"
          >重载项目权限</a-button
        ></template
      ></a-alert
    >

    <a-card class="change-panel" :body-style="{ padding: 0 }">
      <a-tabs
        :active-key="query.mine ? 'mine' : 'all'"
        class="change-tabs"
        @change="
          (key: string) => {
            query.mine = key === 'mine';
            load(true);
          }
        "
      >
        <a-tab-pane key="all" tab="全部变更" /><a-tab-pane
          key="mine"
          tab="待我办理"
        />
      </a-tabs>
      <div class="change-toolbar">
        <div class="change-filters">
          <a-input
            v-model:value="query.keyword"
            aria-label="搜索变更"
            placeholder="标题、单号或项目名称"
            allow-clear
            class="keyword"
            @press-enter="load(true)"
            ><template #prefix><SearchOutlined /></template
          ></a-input>
          <a-select
            v-model:value="query.changeType"
            aria-label="变更类型筛选"
            placeholder="全部类型"
            allow-clear
            :options="[
              { value: 'PROJECT', label: '项目变更' },
              { value: 'DATA', label: '数据变更' },
            ]"
          />
          <a-select
            v-model:value="query.status"
            aria-label="审批状态筛选"
            placeholder="全部状态"
            allow-clear
            :options="
              Object.entries(statuses).map(([value, label]) => ({
                value,
                label,
              }))
            "
          />
          <a-button type="primary" @click="load(true)">查询</a-button>
          <a-tooltip title="清空筛选并刷新"
            ><a-button
              aria-label="重置筛选"
              @click="
                Object.assign(query, {
                  keyword: '',
                  changeType: undefined,
                  status: undefined,
                });
                load(true);
              "
              ><ReloadOutlined /></a-button
          ></a-tooltip>
        </div>
        <a-tooltip
          :title="
            canCreate
              ? '为参与的实施中项目发起变更'
              : '仅项目团队可为参与的实施中项目发起变更'
          "
          ><a-button
            type="primary"
            :disabled="!canCreate || busy"
            @click="create"
            ><PlusOutlined />发起变更</a-button
          ></a-tooltip
        >
      </div>
      <div v-if="error" class="list-error">
        <a-alert type="error" show-icon :message="error"
          ><template #action
            ><a-button size="small" @click="load()">重试</a-button></template
          ></a-alert
        >
      </div>
      <a-table
        row-key="id"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :scroll="{ x: 1146 }"
        :pagination="{
          current: query.page,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (t: number) => `共 ${t} 条申请`,
        }"
        @change="
          (p: any) => {
            query.page = p.current;
            query.size = p.pageSize;
            load();
          }
        "
      >
        <template #emptyText
          ><a-empty
            :description="
              query.mine ? '暂无需要您办理的变更' : '暂无符合条件的变更申请'
            "
        /></template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'application'"
            ><button
              class="application-link"
              :title="record.title"
              @click="view(record)"
            >
              {{ record.title }}
            </button>
            <div class="secondary mono">{{ record.changeNo }}</div></template
          >
          <template v-else-if="column.key === 'project'"
            ><div class="clamp" :title="record.projectName">
              {{ record.projectName }}
            </div>
            <div class="secondary mono">{{ record.projectNo }}</div></template
          >
          <template v-else-if="column.key === 'type'"
            ><span
              class="type-dot"
              :class="{ data: record.changeType === 'DATA' }" />{{
              record.changeType === "DATA" ? "数据变更" : "项目变更"
            }}
            <div class="secondary">
              {{ (record.categories?.length ? record.categories : [record.category]).map((c: string) => categories[c] || c).join(" / ") }} · {{ record.itemCount || 1 }} 项
              <SafetyCertificateOutlined
                v-if="record.legalReview"
                title="需法务审核"
              /></div
          ></template>
          <template v-else-if="column.key === 'state'"
            ><a-tag :color="color(record.status)"
              >{{ record.legacy ? "历史 · " : ""
              }}{{ statuses[record.status] || record.status }}</a-tag
            ></template
          >
          <template v-else-if="column.dataIndex === 'flowNode'"
            ><span class="clamp" :title="record.flowNode">{{
              record.flowNode || "待提交"
            }}</span></template
          >
          <template v-else-if="column.key === 'act'"
            ><a-space :size="0">
              <a-button
                type="link"
                size="small"
                :disabled="busy"
                @click="view(record)"
                >查看</a-button
              >
              <a-button
                v-if="record.canEdit"
                type="link"
                size="small"
                :disabled="busy"
                @click="view(record, true)"
                >编辑</a-button
              >
              <a-button
                v-else-if="
                  record.canAudit || record.canArchive || record.canSubmit
                "
                type="link"
                size="small"
                :disabled="busy"
                @click="view(record)"
                >{{
                  record.canAudit ? "审核" : record.canArchive ? "归档" : "提交"
                }}</a-button
              >
              <a-dropdown v-if="record.canEdit"
                ><a-button type="link" size="small" :disabled="busy"
                  >更多</a-button
                ><template #overlay
                  ><a-menu
                    ><a-menu-item key="delete" danger @click="remove(record)"
                      >删除草稿</a-menu-item
                    ></a-menu
                  ></template
                ></a-dropdown
              >
            </a-space></template
          >
        </template>
      </a-table>
      <div class="panel-footnote">
        <SafetyCertificateOutlined />
        重大经费、周期及外协调整须经法务审核；具体审批路径按项目渠道确定。
      </div>
    </a-card>

    <a-drawer
      :open="open"
      :title="
        mode === 'edit'
          ? detail
            ? '编辑变更申请'
            : '发起变更申请'
          : '变更申请详情'
      "
      width="min(1180px, 100vw)"
      root-class-name="change-drawer"
      :header-style="{ background: '#23445b', color: '#fff' }"
      :body-style="{ padding: 0, background: '#eef1f4' }"
      :footer-style="{
        background: '#fff',
        borderTop: '1px solid #bfcbd4',
        padding: '12px 20px',
      }"
      :mask-closable="!busy"
      :closable="!busy"
      :keyboard="!busy"
      @close="close"
    >
      <div ref="drawerTop" />
      <div class="object-header">
        <div class="object-icon"><SwapOutlined /></div>
        <div>
          <div class="object-kind">
            CHANGE REQUEST <span v-if="testMode"> / 隔离演练</span>
          </div>
          <h2>{{ detail?.title || "新建变更申请" }}</h2>
          <p>
            {{ detail?.changeNo || "保存后生成申请编号" }} ·
            {{ selectedProject?.name || detail?.projectName || "尚未关联项目" }}
          </p>
        </div>
        <a-tag :color="color(detail?.status)">{{
          statuses[detail?.status] || "草稿"
        }}</a-tag>
      </div>
      <nav class="object-tabs" aria-label="申请内容导航">
        <button
          :class="{ active: activeSection === 'content' }"
          @click="jump('content')"
        >
          属性与内容</button
        ><button
          :class="{ active: activeSection === 'materials' }"
          @click="jump('materials')"
        >
          支撑材料 <span>{{ detail?.attachments?.length || 0 }}</span></button
        ><button
          :class="{ active: activeSection === 'history' }"
          @click="jump('history')"
        >
          版本与履历 <span>{{ detail?.roundNo ? `第 ${detail.roundNo} 版` : "未保存" }}</span>
        </button>
      </nav>
      <div class="inspector-layout">
        <main id="change-content" class="inspector-content">
          <a-alert
            v-if="formError"
            :message="formError"
            type="error"
            show-icon
            class="section-alert"
          />
          <template v-if="mode === 'edit'">
            <div class="drawer-intro">
              <span class="section-index">01</span>
              <div>
                <h3>申请信息</h3>
                <p>多选类别 → 多选对象 → 分别填写变更内容 → 一次提交整份申请。</p>
              </div>
              <a-tag>草稿</a-tag>
            </div>
            <a-form layout="vertical" :disabled="busy" class="change-form">
              <a-form-item label="关联项目" required
                ><a-select
                  v-model:value="form.projectId"
                  aria-label="关联项目"
                  :disabled="!!detail || !!selectedItems.length || !!form.afterValue"
                  show-search
                  :filter-option="filterOption"
                  :options="
                    createProjects.map((p) => ({
                      value: p.id,
                      label: `${p.projectNo} · ${p.name}`,
                    }))
                  "
                  placeholder="请选择您参与的实施中项目"
                  @change="projectChanged"
              /></a-form-item>
              <a-form-item label="变更类型" required
                ><a-radio-group
                  v-model:value="form.changeType"
                  :disabled="!!selectedItems.length || !!form.afterValue"
                  @change="typeChanged"
                  ><a-radio-button value="PROJECT">项目变更 · 调整业务</a-radio-button
                  ><a-radio-button value="DATA"
                    >数据变更 · 纠正登记</a-radio-button
                  ></a-radio-group
                >
                <div class="field-hint">
                  {{
                    form.changeType === "DATA"
                      ? "数据纠错：纠正项目名称、工作内容、年度任务等登记错误；层级渠道为特殊审批。"
                      : "业务调整：修改计划、经费、合作单位、指标或交付物。经费、周期、外协任一项均需法务审核。"
                  }}
                </div></a-form-item
              >
              <div class="multi-object-entry">
                <div><h3>变更类别与对象 <a-tag color="blue">支持多选</a-tag></h3><p>可同时选择多个类别、多个里程碑或交付物等对象；每个对象会展开独立填写区。</p></div>
                <a-button type="primary" :disabled="!form.projectId || contextLoading || busy" :loading="contextLoading" :aria-label="selectedItems.length ? '增减变更类别与对象' : '多选变更类别与对象'" @click="pickerOpen = true"><PlusOutlined />{{ selectedItems.length ? '增减变更类别与对象' : '多选变更类别与对象' }}</a-button>
                <div v-if="selectedItems.length" class="selection-summary"><a-tag v-for="category in selectedCategories" :key="category">{{ categories[category] }} · {{ selectedItems.filter(i => i.category === category).length }} 项</a-tag><b>共 {{ selectedCategories.length }} 类 / {{ selectedItems.length }} 项</b></div>
              </div>
              <a-form-item label="变更标题" required
                ><a-input
                  v-model:value="form.title"
                  aria-label="变更标题"
                  :maxlength="255"
                  show-count
                  placeholder="简要说明本次变更"
              /></a-form-item>
              <section class="selected-items" aria-label="多对象变更内容">
                <div class="section-heading"><h3>逐对象填写变更内容</h3><span class="secondary">已填 {{ completedItems }} / {{ selectedItems.length }} 项 · 一次提交</span></div>
                <p v-if="!selectedItems.length" class="field-hint">请点击上方“多选变更类别与对象”，勾选后这里会一次展开全部对应的填写区。</p>
                <article v-for="(item, index) in selectedItems" :key="`${item.targetKey}:${item.targetId}`" class="selected-item" :data-target-key="item.targetKey" :data-target-id="item.targetId">
                  <div class="selected-item-heading"><b>{{ index + 1 }}. {{ item.fieldLabel }}</b><a-tag>{{ categories[item.category] }}</a-tag><a-tag v-if="['FUND','PERIOD','OUTSOURCE'].includes(item.category)" color="orange">需法务</a-tag><a-tag :color="itemProblem(item) ? 'default' : 'green'">{{ itemProblem(item) ? '待完善' : '已填写' }}</a-tag></div>
                  <p class="secondary">{{ item.targetLabel }}</p>
                  <div class="item-values"><div><small>调整前 · 当前记录</small><p>{{ item.beforeDisplay || item.beforeValue || '—' }}</p></div>
                    <div><a-form-item :label="`第${index + 1}项调整后${itemTarget(item)?.valueType === 'date' ? '日期' : itemTarget(item)?.valueType === 'money' ? '金额' : itemTarget(item)?.valueType === 'channel' ? '渠道' : '内容'}`" :html-for="`change-item-${index}`" required :validate-status="attemptedSave && itemProblem(item) ? 'error' : undefined" :help="attemptedSave ? itemProblem(item) : undefined">
                      <a-date-picker v-if="itemTarget(item)?.valueType === 'date'" :id="`change-item-${index}`" v-model:value="item.afterValue" :aria-label="`第${index + 1}项调整后日期`" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width:100%" :disabled="contextLoading" />
                      <a-input v-else-if="itemTarget(item)?.valueType === 'money'" :id="`change-item-${index}`" v-model:value="item.afterValue" :aria-label="`第${index + 1}项调整后金额`" addon-after="万元" inputmode="decimal" :maxlength="20" placeholder="填写该项新金额" :disabled="contextLoading" />
                      <a-select v-else-if="itemTarget(item)?.valueType === 'channel'" :id="`change-item-${index}`" v-model:value="item.afterValue" :aria-label="`第${index + 1}项调整后渠道`" :options="channels.map(c => ({ value:String(c.id),label:`${c.levelCode} / ${c.label}` }))" placeholder="选择该项目标渠道" :disabled="contextLoading" />
                      <a-textarea v-else :id="`change-item-${index}`" v-model:value="item.afterValue" :aria-label="`第${index + 1}项调整后内容`" :rows="3" :maxlength="itemTarget(item)?.max || 2000" show-count placeholder="填写这个对象的调整后内容" :disabled="contextLoading" />
                    </a-form-item></div>
                  </div>
                  <div class="item-buttons"><a-button size="small" danger :disabled="busy" :aria-label="`移除已选项 ${index + 1}`" @click="removeItem(item)">移除此项</a-button></div>
                </article>
                <p v-if="selectedItems.length" class="field-hint">上方可继续增减类别与对象；保留对象已填写的内容不会丢失。全部填写后保存草稿，上传材料，一次提交 {{ selectedItems.length }} 项。</p>
              </section>
              <a-form-item label="变更缘由" required
                ><a-textarea
                  v-model:value="form.reason"
                  aria-label="变更缘由"
                  :rows="3"
                  :maxlength="2000"
                  show-count
                  placeholder="说明调整原因、依据及对项目的影响"
              /></a-form-item>
              <template v-if="isMajor"
                ><a-alert
                  type="warning"
                  show-icon
                  message="重大变更：提交前需指定名单内的法务办理人，法务通过后继续审批。"
                  class="section-alert"
                /><a-form-item label="法务办理人" required
                  ><a-select
                    v-model:value="form.legalReviewerId"
                    aria-label="法务办理人"
                    show-search
                    :filter-option="filterOption"
                    :options="
                      legalReviewers.map((u) => ({
                        value: u.id,
                        label: `${u.realName}（${u.employeeNo}）· ${u.identity}`,
                      }))
                    "
                    placeholder="选择承担本次法务审核的人员"
                  />
                  <div v-if="!legalReviewers.length" class="field-hint">
                    暂无可选法务。可先保存草稿；请管理员配置正式法务工号名单后再提交。
                  </div></a-form-item
                ></template
              >
            </a-form>
            <section v-if="selectedTarget || selectedItems.length" class="route-panel">
              <h3>预计审批路径</h3>
              <div class="route-strip">
                <template v-for="(node, index) in preview" :key="index"
                  ><span v-if="index" class="route-arrow">›</span
                  ><span>{{ node }}</span></template
                >
              </div>
              <p class="field-hint">
                路径覆盖全部已选项，任一重大事项都会增加法务审核。保存后下方显示各节点具体办理人。
              </p>
            </section>
          </template>

          <template v-else-if="detail">
            <div class="detail-summary">
              <div class="eyebrow">
                {{ detail.changeNo }}
              </div>
              <h2>{{ detail.title }}</h2>
              <div class="secondary">
                {{ detail.projectNo }} · {{ detail.projectName }}
              </div>
              <a-space class="summary-tags"
                ><a-tag :color="color(detail.status)">{{
                  statuses[detail.status] || detail.status
                }}</a-tag
                ><a-tag>{{
                  detail.changeType === "DATA" ? "数据变更" : "项目变更"
                }}</a-tag
                ><a-tag>{{
                  (detail.categories?.length ? detail.categories : [detail.category]).map((c: string) => categories[c] || c).join(" / ")
                }}</a-tag></a-space
              >
            </div>
            <a-alert
              v-if="detail.legacy"
              type="info"
              show-icon
              message="历史申请仅供查阅。历史记录没有完整的审批及对象关联信息，需要调整时请重新发起变更。"
              class="section-alert"
            />
            <a-alert
              v-if="detail.status === 'REJECTED'"
              type="warning"
              show-icon
              message="申请已驳回，请查看下方审核意见，编辑补正后可重新提交。"
              class="section-alert"
            />
            <a-alert
              v-if="detail.status === 'AWAITING_ARCHIVE'"
              type="warning"
              show-icon
              message="总部审核已完成，待发起人上传 GXB 线下上报材料并填写回执信息后生效。"
              class="section-alert"
            />
            <div class="drawer-intro compact">
              <span class="section-index">01</span>
              <div>
                <h3>变更内容</h3>
                <p>
                  {{
                    detail.targetLabel ||
                    detail.fieldLabel ||
                    categories[detail.category]
                  }}
                </p>
              </div>
            </div>
            <article v-for="(item, index) in (detail.items?.length ? detail.items : [detail])" :key="index" class="selected-item">
              <div class="selected-item-heading"><b>{{ index + 1 }}. {{ item.fieldLabel || '变更内容' }}</b><a-tag>{{ categories[item.category] }}</a-tag></div>
              <p class="secondary">{{ item.targetLabel }}</p>
              <div class="comparison"><div class="comparison-before"><div class="value-label">调整前</div><div class="value-text">{{ item.beforeDisplay || item.beforeValue || '—' }}</div></div><div class="comparison-after"><div class="value-label">调整后</div><div class="value-text">{{ item.afterDisplay || item.afterValue || '—' }}</div></div></div>
            </article>
            <div class="detail-reason">
              <div class="value-label">变更缘由</div>
              <div class="value-text">{{ detail.reason || "—" }}</div>
            </div>
            <section v-if="detail.route?.length" class="route-panel">
              <h3>审批路径</h3>
              <a-steps
                direction="vertical"
                size="small"
                :current="detail.stepIndex"
                :status="detail.status === 'REJECTED' ? 'error' : 'process'"
                :items="
                  detail.route.map((r: any) => ({
                    title: r.label,
                    description: handlerText(r.handlers),
                  }))
                "
              />
              <div v-if="detail.appliedAt" class="applied-note">
                已于
                {{ String(detail.appliedAt).replace("T", " ").slice(0, 19) }}
                完成数据回写。
              </div>
              <div v-if="detail.archiveRef" class="field-hint">
                线下上报文号 / 回执号：{{ detail.archiveRef }}
              </div>
            </section>
          </template>

          <section v-if="mode === 'edit' && detail?.route?.length" class="route-panel">
            <h3>已保存版本的周转方</h3><p v-if="dirty" class="field-hint">当前存在未保存修改，保存后会重新计算路径和办理人。</p>
            <ol class="handoff-list"><li v-for="node in detail.route" :key="node.code"><b>{{ node.label }}</b><p>{{ handlerText(node.handlers) }}</p></li></ol>
          </section>
          <section id="change-materials" class="attachment-section">
            <p class="material-guide">
              {{ materialGuide }} 上述为准备建议，提交至少需要一份支撑文件。
            </p>
            <div class="section-heading">
              <h3>
                <FileTextOutlined /> 支撑材料{{
                  detail?.canArchive ? "与线下归档" : ""
                }}
              </h3>
              <a-upload
                v-if="mode === 'edit' || detail?.canArchive"
                :show-upload-list="false"
                :before-upload="upload"
                accept=".pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.txt,.md"
                :disabled="!detail?.id || busy || dirty"
                ><a-button
                  :disabled="!detail?.id || busy || dirty"
                  :loading="busy"
                  ><UploadOutlined />{{
                    detail?.canArchive ? "上传线下材料" : "上传支撑材料"
                  }}</a-button
                ></a-upload
              >
            </div>
            <p v-if="mode === 'edit' || detail?.canArchive" class="field-hint">
              {{
                !detail?.id || dirty
                  ? "请先保存草稿及当前修改，再上传材料。"
                  : "支持 PDF、Office、图片与文本，单份不超过 20 MB。"
              }}
              提交审批前至少上传一份支撑材料。
            </p>
            <div
              v-for="file in detail?.attachments || []"
              :key="file.id"
              class="attachment-row"
            >
              <FileTextOutlined /><button
                class="file-link"
                :title="file.fileName"
                @click="download(file)"
              >
                {{ file.fileName }}</button
              ><a-tag v-if="file.kind === 'EXTERNAL'" color="blue"
                >线下归档</a-tag
              ><span class="secondary">{{ formatSize(file.fileSize) }}</span
              ><a-button
                v-if="
                  (mode === 'edit' && detail?.canEdit) ||
                  (detail?.canArchive && file.kind === 'EXTERNAL')
                "
                type="link"
                danger
                size="small"
                :disabled="busy || dirty"
                @click="removeFile(file)"
                >移除</a-button
              >
            </div>
            <div v-if="!detail?.attachments?.length" class="attachment-empty">
              暂无附件
            </div>
          </section>
          <section id="change-history" class="history-section">
            <p v-if="!detail?.history?.length" class="field-hint">
              保存申请后开始记录办理履历。
            </p>
            <h3>修改版本 <a-tag v-if="detail?.roundNo">当前第 {{ detail.roundNo }} 版</a-tag></h3>
            <p class="field-hint">每次保存保留标题、缘由及全部变更项；附件与审批操作单独记入办理履历。退回后可继续修改，重新提交从首节点审批。</p>
            <details v-for="version in detail?.rounds || []" :key="version.roundNo" class="change-version">
              <summary>第 {{ version.roundNo }} 版 · {{ version.snapshot.items.length }} 项 · {{ version.actorName }} · {{ String(version.createdAt).replace('T', ' ').slice(0,19) }}</summary>
              <h4>{{ version.snapshot.title }}</h4><p class="value-text">{{ version.snapshot.reason }}</p>
              <div v-for="(item, index) in version.snapshot.items" :key="index" class="version-item"><b>{{ index + 1 }}. {{ categories[item.category] }} · {{ item.targetLabel }}</b><div class="item-values"><div><small>调整前</small><p>{{ item.beforeDisplay || '—' }}</p></div><div><small>本版调整后</small><p>{{ item.afterDisplay }}</p></div></div></div>
            </details>
            <h3>办理履历</h3>
            <a-timeline
              ><a-timeline-item
                v-for="item in detail?.history || []"
                :key="item.id"
                :color="
                  item.action === 'REJECT'
                    ? 'red'
                    : item.action === 'APPLY'
                      ? 'green'
                      : 'blue'
                "
                ><div class="history-title">
                  {{ actions[item.action] || item.action }}
                  <span class="secondary">· {{ item.nodeName }}</span>
                </div>
                <div class="history-opinion">{{ item.opinion }}</div>
                <div class="secondary">
                  {{ item.actorName }} ·
                  {{ String(item.createdAt).replace("T", " ").slice(0, 19) }}
                </div></a-timeline-item
              ></a-timeline
            >
          </section>
        </main>
        <aside class="task-rail">
          <div class="task-label">当前任务</div>
          <h3>
            {{
              detail?.flowNode ||
              (detail?.status === "APPROVED" ? "办理完成" : "准备申请")
            }}
          </h3>
          <p>{{ nextTask }}</p>
          <template
            v-if="
              detail?.currentHandlers?.length &&
              !['DRAFT', 'REJECTED', 'APPROVED'].includes(detail?.status)
            "
            ><div class="task-label">本节点办理人</div>
            <div
              v-for="u in detail.currentHandlers"
              :key="u.employeeNo"
              class="handler"
            >
              <span>{{ u.name }}</span
              ><small>{{ u.employeeNo }}</small>
            </div></template
          >
          <section v-if="detail && !detail.legacy && detail.status !== 'APPROVED'" class="next-handoff" aria-label="下一步周转方">
            <template v-if="detail.submitHandlers?.length"><div class="task-label">提交确认交给</div><p>{{ handlerText(detail.submitHandlers) }}</p></template>
            <div class="task-label">{{ ['DRAFT','REJECTED'].includes(detail.status) ? '提交后交给' : '通过后交给' }}</div>
            <b>{{ detail.nextNode }}</b><p v-if="detail.nextHandlers?.length">{{ handlerText(detail.nextHandlers) }}</p>
            <template v-if="detail.status === 'APPROVING'"><div class="task-label">退回后交给发起人</div><p>{{ detail.returnTo }}，补正后重新提交。</p></template>
            <p v-if="dirty" class="field-hint">以保存后的最新路径为准。</p>
          </section>
          <div v-if="mode === 'edit' || detail?.canSubmit" class="readiness">
            <div class="task-label">提交准备</div>
            <button
              v-for="item in checklist"
              :key="item.title"
              :aria-label="`定位：${item.title}`"
              @click="jump(item.section)"
            >
              <span :class="{ done: item.done }">{{
                item.done ? "✓" : "○"
              }}</span
              >{{ item.title }}
            </button>
          </div>
          <div
            v-if="
              detail?.readinessIssues?.length &&
              ['DRAFT', 'REJECTED'].includes(detail?.status)
            "
            class="readiness-issues"
          >
            <b>提交前需要处理</b>
            <p v-for="issue in detail.readinessIssues" :key="issue">
              {{ issue }}
            </p>
          </div>
          <div class="task-label">生效规则</div>
          <p>
            一份申请可包含多个变更项。审批中保持原值；最后一个有效节点完成后全部一起回写，任一项冲突则整份不生效。
          </p>
          <p v-if="isMajor">经费、周期、外协调整包含法务审核。</p>
          <div class="task-note">
            页面关闭前请保存内容。保存后上传材料，附件变更会记录办理履历。
          </div>
        </aside>
      </div>
      <template #footer
        ><div class="drawer-footer">
          <span class="secondary">{{
            dirty
              ? "有未保存的修改"
              : detail?.revision != null
                ? `当前内容已保存 · 第 ${detail?.roundNo || 1} 版 · ${detail?.itemCount || 1} 项`
                : "新申请"
          }}</span
          ><a-space
            ><a-button :disabled="busy" @click="close">关闭</a-button
            ><a-button
              v-if="mode === 'view' && detail?.canEdit"
              :disabled="busy"
              @click="view(detail, true)"
              >编辑草稿</a-button
            ><a-button
              v-if="mode === 'edit'"
              type="primary"
              :loading="busy"
              :disabled="contextLoading"
              @click="save"
              >保存草稿</a-button
            ><a-button
              v-if="detail?.canSubmit"
              type="primary"
              :disabled="
                dirty ||
                busy ||
                !supportCount ||
                !!detail?.readinessIssues?.length
              "
              @click="submit"
              >一次提交 {{ detail?.itemCount || selectedItems.length }} 项</a-button
            ><a-button
              v-if="detail?.canAudit || detail?.canArchive"
              type="primary"
              :disabled="busy"
              @click="beginReview"
              >{{ detail.canArchive ? "完成线下归档" : "办理审核" }}</a-button
            ></a-space
          >
        </div></template
      >
    </a-drawer>
    <ChangeObjectPicker v-model:open="pickerOpen" :targets="availableTargets" :items="selectedItems" @confirm="confirmObjects" />
    <a-modal
      v-model:open="reviewOpen"
      :title="
        detail?.canArchive
          ? '完成 GXB 线下归档'
          : `办理审核 · ${detail?.flowNode || ''}`
      "
      :confirm-loading="busy"
      :mask-closable="!busy"
      :keyboard="!busy"
      :closable="!busy"
      :cancel-button-props="{ disabled: busy }"
      ok-text="确认办理"
      cancel-text="取消"
      @ok="finishReview"
    >
      <a-alert v-if="detail" class="section-alert" type="info" show-icon :message="review.pass || detail.canArchive ? `通过后：${detail.nextNode}` : `退回给：${detail.returnTo}`" :description="review.pass ? (detail.nextHandlers?.length ? handlerText(detail.nextHandlers) : '全部变更项校验成功后统一生效。') : '发起人修改后重新提交，从首节点重新审批。'" />
      <a-form layout="vertical" :disabled="busy"
        ><a-form-item v-if="!detail?.canArchive" label="审核结果" required
          ><a-radio-group v-model:value="review.pass"
            ><a-radio :value="true">通过，进入下一节点</a-radio
            ><a-radio :value="false">驳回，退回补正</a-radio></a-radio-group
          ></a-form-item
        ><a-form-item v-else label="线下上报文号 / 回执号" required
          ><a-input
            v-model:value="review.reference"
            aria-label="线下上报文号"
            :maxlength="255" /></a-form-item
        ><a-form-item label="办理意见" required
          ><a-textarea
            v-model:value="review.opinion"
            aria-label="办理意见"
            :rows="4"
            :maxlength="2000"
            show-count
            placeholder="填写审核依据及结论" /></a-form-item
      ></a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.change-workbench {
  --change-blue: #1b7aff;
  --change-navy: #173956;
  color: #24374b;
}
.change-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.eyebrow {
  font-size: 10px;
  letter-spacing: 1.5px;
  color: #718397;
  margin-bottom: 6px;
}
.change-heading .page-title {
  margin: 0;
  font-size: 22px;
  color: var(--change-navy);
}
.workspace-mark {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #56728e;
  border-left: 2px solid var(--change-blue);
  padding-left: 12px;
  font-size: 12px;
}
.change-workbench .page-desc {
  margin-bottom: 20px;
  color: #67798a;
}
.change-panel {
  border: 1px solid #dce4ed;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 3px 12px #18344d05;
}
.change-tabs {
  padding: 0 20px;
  background: #f8fafc;
  border-top: 3px solid #214e73;
}
.change-tabs :deep(.ant-tabs-nav) {
  margin: 0;
}
.change-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 20px;
  flex-wrap: wrap;
}
.change-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.keyword {
  width: 235px;
}
.change-filters .ant-select {
  width: 130px;
}
.change-panel :deep(.ant-table-thead > tr > th) {
  height: 55px;
  background: #f1f5f9;
  color: #43576d;
  font-weight: 500;
  font-size: 13px;
}
.change-panel :deep(.ant-table-tbody > tr:not(.ant-table-measure-row) > td) {
  padding: 12px 16px;
  min-height: 55px;
}
.change-panel :deep(.ant-table-pagination) {
  padding: 0 20px;
}
.application-link,
.file-link {
  background: none;
  border: 0;
  padding: 0;
  color: #1b67b5;
  cursor: pointer;
  text-align: left;
  font: inherit;
}
.application-link,
.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.application-link:hover,
.file-link:hover {
  text-decoration: underline;
}
.secondary {
  color: #718194;
  font-size: 12px;
  line-height: 20px;
}
.mono {
  font-variant-numeric: tabular-nums;
}
.type-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin: 0 7px 2px 0;
  background: #3678bf;
  border-radius: 50%;
}
.type-dot.data {
  background: #19958e;
}
.panel-footnote {
  padding: 12px 20px;
  border-top: 1px solid #e9edf2;
  color: #7d8c9a;
  font-size: 12px;
}
.list-error {
  padding: 0 20px 16px;
}
/* Drawer content keeps this component's scope even when teleported by Ant Design. */
.drawer-intro {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.drawer-intro > div {
  flex: 1;
}
.drawer-intro h3,
.section-heading h3,
.route-panel h3,
.history-section h3 {
  font-size: 15px;
  margin: 0 0 8px;
  color: #173956;
}
.drawer-intro p {
  font-size: 12px;
  color: #718194;
  margin: 0;
}
.drawer-intro.compact {
  margin: 24px 0 16px;
}
.section-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: #eaf2fb;
  color: #2c6396;
  font-size: 12px;
  border: 1px solid #d7e4f2;
  border-radius: 4px;
}
.field-hint {
  font-size: 12px;
  color: #738294;
  line-height: 20px;
  margin: 6px 0 0;
}
.comparison {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-bottom: 24px;
  border: 1px solid #dce5ef;
  border-radius: 4px;
  overflow: hidden;
}
.comparison-before,
.comparison-after {
  padding: 16px;
  min-width: 0;
}
.comparison-before {
  background: #f6f8fa;
}
.comparison-after {
  border-left: 1px solid #dce5ef;
  background: #f7fbff;
}
.comparison-after :deep(.ant-form-item) {
  margin-bottom: 12px;
}
.value-label {
  color: #6c7e91;
  font-size: 12px;
  margin-bottom: 12px;
}
.value-text {
  line-height: 24px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: #293f55;
}
.section-alert {
  margin-bottom: 16px;
}
.route-panel {
  padding: 16px;
  border: 1px solid #e0e7ee;
  border-radius: 4px;
  margin: 24px 0;
}
.route-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: #345978;
}
.route-arrow {
  color: #9bacbd;
  font-size: 20px;
}
.detail-summary {
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 16px;
  margin-bottom: 24px;
}
.detail-summary h2 {
  font-size: 20px;
  line-height: 30px;
  margin: 8px 0;
  color: #173956;
  overflow-wrap: anywhere;
}
.summary-tags {
  margin-top: 12px;
}
.detail-reason {
  margin: 0 0 24px;
}
.applied-note {
  color: #218454;
  font-size: 12px;
}
.attachment-section,
.history-section {
  margin: 28px 0;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.section-heading h3 {
  margin: 0;
}
.attachment-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid #edf0f4;
}
.file-link {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.attachment-empty {
  color: #9ba7b2;
  font-size: 12px;
  background: #f8fafc;
  border: 1px dashed #dce4ec;
  border-radius: 4px;
  padding: 20px;
  text-align: center;
  margin-top: 12px;
}
.history-section h3 {
  margin-bottom: 24px;
}
.history-title {
  font-size: 13px;
  font-weight: 500;
}
.history-opinion {
  margin: 6px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 13px;
  color: #405468;
}
.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
@media (max-width: 700px) {
  .workspace-mark {
    display: none;
  }
  .comparison {
    grid-template-columns: 1fr;
  }
  .comparison-after {
    border-left: 0;
    border-top: 1px solid #dce5ef;
  }
  .change-toolbar {
    padding: 12px;
  }
  .keyword {
    width: 100%;
  }
  .change-filters {
    width: 100%;
  }
  .drawer-footer > .secondary {
    display: none;
  }
}
.change-panel :deep(.ant-table-measure-row > td) {
  height: 0 !important;
  min-height: 0 !important;
  padding: 0 !important;
  border: 0 !important;
  line-height: 0 !important;
}
.change-panel :deep(.ant-table-thead > tr > th) {
  background: #f1f5f9 !important;
  color: #43576d !important;
  font-weight: 500 !important;
}
</style>

<style scoped src="./change/workspace.css"></style>
