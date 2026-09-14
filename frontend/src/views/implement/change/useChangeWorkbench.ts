import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { message, Modal } from "ant-design-vue";
import { changes } from "@/api/change";
import { isSilentAuthError } from "@/api/request";
import {
  majorCategories,
  routePreview,
  validateDraft,
  validateFile,
} from "./policy";

export function useChangeWorkbench() {
  const rows = ref<any[]>([]),
    total = ref(0),
    loading = ref(false),
    busy = ref(false),
    contextLoading = ref(false);
  const testMode = ref(false);
  const error = ref(""),
    formError = ref(""),
    open = ref(false),
    mode = ref<"edit" | "view">("view"),
    detail = ref<any>(null);
  const projects = ref<any[]>([]),
    targets = ref<any[]>([]),
    channels = ref<any[]>([]),
    legalReviewers = ref<any[]>([]),
    channelCode = ref("");
  const query = reactive({
    page: 1,
    size: 10,
    changeType: undefined as string | undefined,
    status: undefined as string | undefined,
    keyword: "",
    mine: false,
  });
  const form = reactive<any>({}),
    selectedToken = ref<string>(),
    saved = ref("");
  let listSeq = 0,
    contextSeq = 0,
    detailSeq = 0,
    alive = true;
  const selectedProject = computed(
    () => projects.value.find((p) => p.id === form.projectId) || null,
  );
  const availableTargets = computed(() =>
    targets.value.filter((t) => t.changeType === form.changeType),
  );
  const selectedTarget = computed(() =>
    availableTargets.value.find(
      (t) => `${t.key}:${t.id}` === selectedToken.value,
    ),
  );
  const selectedItems = computed<any[]>(() => form.items || []);
  const attemptedSave = ref(false);
  const isMajor = computed(() => [form.category, ...selectedItems.value.map(i => i.category)].some(c => majorCategories.includes(c)));
  const preview = computed(() =>
    routePreview(form.changeType, isMajor.value ? "FUND" : form.category, channelCode.value),
  );
  const dirty = computed(
    () => mode.value === "edit" && JSON.stringify(form) !== saved.value,
  );
  const canCreate = computed(() => projects.value.some((p) => p.canCreate));
  const report = (e: any) => {
    if (!isSilentAuthError(e)) message.error(e?.message || "操作失败，请重试");
  };
  async function load(reset = false) {
    if (reset) query.page = 1;
    const seq = ++listSeq;
    loading.value = true;
    error.value = "";
    try {
      const { data } = await changes.page({ ...query });
      if (!alive || seq !== listSeq) return;
      rows.value = data.records;
      total.value = data.total;
      if (!rows.value.length && total.value > 0 && query.page > 1) {
        query.page--;
        await load();
      }
    } catch (e: any) {
      if (seq === listSeq) {
        error.value = e.message;
        rows.value = [];
        total.value = 0;
      }
    } finally {
      if (seq === listSeq) loading.value = false;
    }
  }
  async function context(projectId?: number) {
    const seq = ++contextSeq;
    contextLoading.value = true;
    targets.value = [];
    formError.value = "";
    try {
      const { data } = await changes.context(projectId);
      if (!alive || seq !== contextSeq) return;
      testMode.value = data.testMode === true;
      projects.value = data.projects;
      targets.value = data.targets;
      channels.value = data.channels;
      legalReviewers.value = data.legalReviewers;
      channelCode.value = data.channelCode || "";
    } catch (e: any) {
      if (seq === contextSeq) {
        formError.value = e.message;
        report(e);
      }
    } finally {
      if (seq === contextSeq) contextLoading.value = false;
    }
  }
  function reset(values: any) {
    for (const key of Object.keys(form)) delete form[key];
    Object.assign(form, JSON.parse(JSON.stringify(values)));
    form.items ||= [];
    selectedToken.value = form.targetKey
      ? `${form.targetKey}:${form.targetId}`
      : undefined;
    if (form.items.length) clearEditor();
    attemptedSave.value = false;
    saved.value = JSON.stringify(form);
    formError.value = "";
  }
  function create() {
    ++detailSeq;
    detail.value = null;
    mode.value = "edit";
    targets.value = [];
    selectedToken.value = undefined;
    reset({
      changeType: "PROJECT",
      title: "",
      reason: "",
      afterValue: "",
      items: [],
      requestKey: crypto.randomUUID(),
    });
    open.value = true;
  }
  async function view(row: any, edit = false) {
    const seq = ++detailSeq;
    busy.value = true;
    formError.value = "";
    try {
      const { data } = await changes.detail(row.id);
      if (!alive || seq !== detailSeq) return;
      detail.value = data;
      reset(data);
      mode.value = edit && data.canEdit ? "edit" : "view";
      open.value = true;
      if (mode.value === "edit") await context(data.projectId);
    } catch (e) {
      report(e);
    } finally {
      if (seq === detailSeq) busy.value = false;
    }
  }
  function close() {
    if (busy.value) return;
    const finish = () => {
      ++detailSeq;
      ++contextSeq;
      contextLoading.value = false;
      open.value = false;
      formError.value = "";
    };
    if (dirty.value)
      Modal.confirm({
        title: "放弃未保存的修改？",
        content: "已经保存的草稿和附件会保留。",
        okText: "放弃修改",
        cancelText: "继续编辑",
        onOk: finish,
      });
    else finish();
  }
  async function projectChanged() {
    form.items = [];
    selectedToken.value = undefined;
    form.targetKey = undefined;
    form.targetId = undefined;
    form.category = undefined;
    form.afterValue = "";
    await context(form.projectId);
  }
  function typeChanged() {
    form.items = [];
    selectedToken.value = undefined;
    form.targetKey = undefined;
    form.targetId = undefined;
    form.category = undefined;
    form.afterValue = "";
    form.legalReviewerId = undefined;
  }
  function clearEditor() {
    selectedToken.value = undefined;
    form.targetKey = undefined;
    form.targetId = undefined;
    form.category = undefined;
    form.beforeValue = undefined;
    form.afterValue = "";
    formError.value = "";
  }
  function targetChanged() {
    const target = selectedTarget.value;
    if (form.targetKey && String(form.afterValue || "").trim()
        && `${form.targetKey}:${form.targetId}` !== selectedToken.value) {
      const stored = selectedItems.value.find(i => i.targetKey === form.targetKey && i.targetId === form.targetId);
      if (!stored || stored.afterValue !== form.afterValue) {
        selectedToken.value = `${form.targetKey}:${form.targetId}`;
        formError.value = "当前项尚未加入清单，请先点击“加入已选项”或“更新已选项”，也可取消当前编辑。";
        return;
      }
    }
    const stored = selectedItems.value.find(i => `${i.targetKey}:${i.targetId}` === selectedToken.value);
    form.targetKey = target?.key;
    form.targetId = target?.id;
    form.category = target?.category;
    form.beforeValue = target?.beforeValue;
    form.afterValue = stored?.afterValue || "";
    formError.value = "";
  }
  function addItem() {
    const target = selectedTarget.value;
    formError.value = validateDraft({ ...form, title: "单项", reason: "单项" }, target);
    if (formError.value) return false;
    const channel = target.valueType === "channel" ? channels.value.find(c => String(c.id) === String(form.afterValue)) : null;
    const afterDisplay = channel ? `${channel.levelCode} / ${channel.label}`
      : String(form.afterValue).trim() + (target.valueType === "money" ? " 万元" : "");
    const item = { targetKey: target.key, targetId: target.id, category: target.category,
      afterValue: String(form.afterValue).trim(), beforeValue: target.beforeValue,
      beforeDisplay: target.beforeDisplay, afterDisplay,
      targetLabel: target.label, fieldLabel: target.fieldLabel };
    const index = selectedItems.value.findIndex(i => i.targetKey === item.targetKey && i.targetId === item.targetId);
    if (index < 0 && selectedItems.value.length >= 50) { formError.value = "每份申请最多选择 50 项"; return false; }
    if (index < 0) form.items.push(item); else form.items.splice(index, 1, item);
    clearEditor();
    return true;
  }
  function editItem(item: any) {
    selectedToken.value = `${item.targetKey}:${item.targetId}`;
    targetChanged();
  }
  function cancelItem() {
    if (String(form.afterValue || "").trim()) Modal.confirm({ title: "取消当前项的编辑？", content: "清单中已加入的内容会保留。", okText: "取消当前编辑", onOk: clearEditor });
    else clearEditor();
  }
  function removeItem(item: any) {
    Modal.confirm({ title: "移除此变更项？", content: item.targetLabel || item.fieldLabel, okText: "移除变更项", onOk: () => {
      form.items = selectedItems.value.filter(i => !(i.targetKey === item.targetKey && i.targetId === item.targetId));
      if (form.targetKey === item.targetKey && form.targetId === item.targetId) clearEditor();
      if (!isMajor.value) form.legalReviewerId = undefined;
    } });
  }
  function itemTarget(item: any) {
    return availableTargets.value.find(t => t.key === item.targetKey && t.id === item.targetId);
  }
  function itemProblem(item: any) {
    return validateDraft({ ...form, title: '单项', reason: '单项', afterValue: item.afterValue }, itemTarget(item));
  }
  function selectObjects(tokens: string[]) {
    const unique = [...new Set(tokens)];
    if (unique.length > 50) { formError.value = "最多选择 50 个变更对象"; return false; }
    const options = unique.map(key => availableTargets.value.find(t => `${t.key}:${t.id}` === key));
    if (options.some(t => !t)) { formError.value = "存在已不可调整的对象，请重新核对选择"; return false; }
    form.items = options.map(target => {
      const existing = selectedItems.value.find(i => i.targetKey === target.key && i.targetId === target.id);
      return existing || { targetKey: target.key, targetId: target.id, category: target.category, beforeValue: target.beforeValue,
        beforeDisplay: target.beforeDisplay, afterValue: '', fieldLabel: target.fieldLabel, targetLabel: target.label };
    });
    clearEditor();
    if (!isMajor.value) form.legalReviewerId = undefined;
    return true;
  }
  function accept(data: any) {
    detail.value = data;
    reset(data);
  }
  async function save() {
    if (busy.value || contextLoading.value) return;
    attemptedSave.value = true;
    if (!form.projectId) { formError.value = "请选择关联项目"; return; }
    if (!String(form.title || "").trim()) { formError.value = "请填写变更标题"; return; }
    if (!String(form.reason || "").trim()) { formError.value = "请填写变更缘由"; return; }
    if (form.targetKey && !addItem()) return;
    if (!selectedItems.value.length) { formError.value = "请选择具体变更对象，加入至少一个变更项"; return; }
    for (let index = 0; index < selectedItems.value.length; index++) {
      const item = selectedItems.value[index], problem = itemProblem(item);
      if (problem) { formError.value = `第 ${index + 1} 项（${item.fieldLabel}）：${problem}`; return; }
    }
    formError.value = "";
    busy.value = true;
    try {
      const { data } = await changes.save({ ...form });
      accept(data);
      message.success("草稿已保存，可上传支撑材料后提交审批");
      await load();
    } catch (e: any) {
      formError.value = e.message;
      report(e);
    } finally {
      busy.value = false;
    }
  }
  async function mutate(action: () => Promise<any>, success: string) {
    if (busy.value) return;
    busy.value = true;
    formError.value = "";
    try {
      const { data } = await action();
      if (data && typeof data === "object") {
        accept(data);
        mode.value = "view";
      }
      message.success(success);
      await load();
      return true;
    } catch (e: any) {
      formError.value = e.message;
      report(e);
      return false;
    } finally {
      busy.value = false;
    }
  }
  function submit() {
    const d = detail.value;
    if (!d?.canSubmit || dirty.value || busy.value) return;
    Modal.confirm({
      title: "提交此变更申请？",
      content: `共 ${d.items?.length || 1} 项变更将统一提交。提交后交给：${d.nextNode}（${(d.nextHandlers || []).map((u: any) => u.name + " / " + u.employeeNo).join("、") || "请核对办理人"}）。内容和支撑材料将锁定。`,
      okText: "确认提交",
      cancelText: "取消",
      onOk: async () => {
        if (
          !(await mutate(() => changes.submit(d.id, d.revision), "已提交审批"))
        )
          throw new Error("提交未完成");
      },
    });
  }
  function remove(row: any) {
    Modal.confirm({
      title: "删除此草稿？",
      content: `将删除“${row.title}”，操作记录会保留。`,
      okText: "删除草稿",
      okType: "danger",
      cancelText: "取消",
      onOk: async () => {
        if (
          await mutate(() => changes.remove(row.id, row.revision), "草稿已删除")
        ) {
          if (detail.value?.id === row.id) open.value = false;
        } else throw new Error("删除未完成");
      },
    });
  }
  async function upload(file: File) {
    const problem = validateFile(file);
    if (problem) {
      message.warning(problem);
      return false;
    }
    if (busy.value || dirty.value || !detail.value?.id) return false;
    busy.value = true;
    try {
      const d = detail.value;
      const { data } = await changes.upload(
        d.id,
        d.revision,
        d.canArchive ? "EXTERNAL" : "SUPPORT",
        file,
      );
      accept(data);
      message.success("附件上传成功");
      await load();
    } catch (e) {
      report(e);
    } finally {
      busy.value = false;
    }
    return false;
  }
  async function removeFile(file: any) {
    if (busy.value || dirty.value) return;
    busy.value = true;
    try {
      const d = detail.value;
      const { data } = await changes.removeFile(d.id, file.id, d.revision);
      accept(data);
      message.success("附件已移除");
      await load();
    } catch (e) {
      report(e);
    } finally {
      busy.value = false;
    }
  }
  async function download(file: any) {
    try {
      await changes.download(detail.value.id, file);
    } catch (e) {
      report(e);
    }
  }
  const reviewOpen = ref(false),
    review = reactive({ pass: true, opinion: "", reference: "" });
  function beginReview() {
    Object.assign(review, { pass: true, opinion: "", reference: "" });
    reviewOpen.value = true;
  }
  async function finishReview() {
    if (!review.opinion.trim()) {
      message.warning("请填写办理意见");
      return;
    }
    const d = detail.value;
    if (d.canArchive && !review.reference.trim()) {
      message.warning("请填写线下上报文号 / 回执号");
      return;
    }
    const done = await mutate(
      () =>
        d.canArchive
          ? changes.archive(d.id, { ...review, revision: d.revision })
          : changes.review(d.id, { ...review, revision: d.revision }),
      d.canArchive
        ? "归档完成，变更已生效"
        : review.pass
          ? "本节点审核通过"
          : "已退回发起人补正",
    );
    if (done) reviewOpen.value = false;
  }
  onMounted(() => {
    load();
    context();
  });
  onBeforeUnmount(() => {
    alive = false;
    ++listSeq;
    ++contextSeq;
    ++detailSeq;
  });
  return {
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
    selectedItems, addItem, editItem, removeItem, cancelItem,
    selectObjects, itemTarget, itemProblem, attemptedSave,
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
  };
}
