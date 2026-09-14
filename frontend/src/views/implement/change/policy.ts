export const statuses: Record<string, string> = {
  DRAFT: "草稿",
  APPROVING: "审批中",
  AWAITING_ARCHIVE: "待线下归档",
  APPROVED: "已办结",
  REJECTED: "已驳回",
};
export const categories: Record<string, string> = {
  MILESTONE_DELAY: "里程碑延期",
  FUND: "经费调整",
  OUTSOURCE: "合作 / 外协方",
  PAYMENT: "付款节点",
  INDICATOR: "核心指标",
  PERIOD: "项目周期",
  DELIVERABLE: "交付物",
  BASIC: "基本信息纠错",
  ANNUAL: "年度任务纠错",
  LEVEL: "层级 / 渠道特殊调整",
};
export const majorCategories = ["FUND", "OUTSOURCE", "PERIOD"];
export const actions: Record<string, string> = {
  CREATE: "创建申请",
  EDIT: "修订草稿",
  SUBMIT: "提交审批",
  APPROVE: "审核通过",
  REJECT: "驳回补正",
  APPLY: "数据回写",
  ARCHIVE: "线下归档",
  UPLOAD: "上传附件",
  REMOVE_FILE: "移除附件",
};
export function routePreview(
  type: string,
  category: string,
  channel: string,
): string[] {
  if (type === "DATA") return ["二级单位主管初审", "总部科技主管确认"];
  const legal = majorCategories.includes(category) ? ["法务审核"] : [];
  if (channel === "SHKJCX")
    return legal.length
      ? ["二级单位主管初审", ...legal, "二级单位主管终审"]
      : ["二级单位主管终审"];
  if (["CLM", "BOKH"].includes(channel))
    return ["二级单位主管初审", ...legal, "二级单位审查确认"];
  return [
    "二级单位主管初审",
    ...legal,
    "总部管理部门终审",
    ...(channel === "MJKY" ? ["GXB 线下上报材料归档"] : []),
  ];
}
export function validateDraft(form: any, target: any) {
  if (!form.projectId) return "请选择关联项目";
  if (!target) return "请选择具体变更对象";
  if (!String(form.title || "").trim()) return "请填写变更标题";
  if (!String(form.reason || "").trim()) return "请填写变更缘由";
  const value = String(form.afterValue ?? "").trim();
  if (!value) return "请填写调整后内容";
  if (value === String(target.beforeValue ?? ""))
    return "调整前后内容相同，无需发起变更";
  if (target.valueType === "money" && !/^\d{1,16}(\.\d{1,2})?$/.test(value))
    return "金额须为非负数，最多两位小数，单位为万元";
  if (target.valueType === "date" && !/^\d{4}-\d{2}-\d{2}$/.test(value))
    return "请选择有效日期";
  return "";
}
export function validateFile(file: { size: number; name: string }) {
  if (!file.size) return "不能上传空文件";
  if (file.size > 20 * 1024 * 1024) return "单份文件不能超过 20 MB";
  if (!/\.(pdf|docx?|xlsx?|png|jpe?g|txt|md)$/i.test(file.name))
    return "不支持此文件格式，请选择 PDF、Office、图片或文本";
  return "";
}
export function formatSize(size: number) {
  return size < 1024
    ? `${size} B`
    : size < 1048576
      ? `${(size / 1024).toFixed(1)} KB`
      : `${(size / 1048576).toFixed(1)} MB`;
}
