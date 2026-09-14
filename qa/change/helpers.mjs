import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
export const base = process.env.CHANGE_QA_BASE || "http://127.0.0.1:6006";
if ((new URL(base).port === "6026") !== (process.env.CHANGE_QA_DB === "rpm_change_lab"))
  throw new Error("CHANGE_QA_BASE and CHANGE_QA_DB must select the same environment");
export const root = fileURLToPath(new URL("../../", import.meta.url));
export const sql = (statement) =>
  execFileSync("python3", [root + "qa/change/local-db.py"], {
    input: statement,
    encoding: "utf8",
  }).trim();
export const quote = (s) =>
  "'" + String(s).replaceAll("\\", "\\\\").replaceAll("'", "''") + "'";
export const tokens = new Map();
export async function login(username) {
  if (tokens.has(username)) return tokens.get(username);
  const result = await (
    await fetch(base + "/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password: username }),
    })
  ).json();
  if (result.code !== 0) throw new Error(result.msg);
  tokens.set(username, result.data.token);
  return result.data.token;
}
export async function api(username, path, method = "GET", body) {
  const token = await login(username);
  const response = await fetch(base + "/api/changes" + path, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body instanceof FormData
        ? {}
        : { "Content-Type": "application/json" }),
    },
    body:
      body === undefined
        ? undefined
        : body instanceof FormData
          ? body
          : JSON.stringify(body),
  });
  return response.json();
}
export async function good(username, path, method = "GET", body) {
  const result = await api(username, path, method, body);
  if (result.code !== 0)
    throw new Error(`${method} ${path}: ${result.code} ${result.msg}`);
  return result.data;
}
export async function upload(
  username,
  row,
  kind = "SUPPORT",
  name = "变更依据.txt",
  content = "独立测试材料，仅用于模块验证。",
) {
  const form = new FormData();
  form.append("file", new Blob([content], { type: "text/plain" }), name);
  return good(
    username,
    `/${row.id}/files?revision=${row.revision}&kind=${kind}`,
    "POST",
    form,
  );
}
export function fixture(
  channel = "04ZXJX",
  title = "模块测试",
  outsider = false,
) {
  const key = "QA_CHANGE_" + randomUUID().slice(0, 12);
  const id = Number(
    sql(
      `INSERT INTO proj_info(project_no,name,goal,main_work,start_date,end_date,channel_id,channel_name,level_code,status,total_fund,national_fund,self_fund,org_id,org_name,owner_name) SELECT ${quote(key)},${quote(title)},'原核心指标','原工作内容','2026-01-01','2028-12-31',id,channel_name,level_code,'IMPLEMENTING',200,100,20,${outsider ? 20 : 10},'测试单位',${outsider ? "'其他负责人'" : "'林晚晴'"} FROM proj_channel WHERE channel_code=${quote(channel)}; SELECT LAST_INSERT_ID();`,
    ),
  );
  if (!outsider)
    sql(
      `INSERT INTO proj_team_member(project_id,group_code,role_code,employee_no,user_name) SELECT ${id},group_code,role_code,employee_no,user_name FROM proj_team_member WHERE project_id=1;`,
    );
  else
    sql(
      `INSERT INTO proj_team_member(project_id,group_code,role_code,employee_no,user_name) VALUES(${id},'PROJECT','PROJECT_LEADER','TEST_OTHER','其他负责人');`,
    );
  const milestone = Number(
    sql(
      `INSERT INTO proj_milestone(project_id,name,plan_date,status) VALUES(${id},'测试里程碑','2027-06-01','DOING'); SELECT LAST_INSERT_ID();`,
    ),
  );
  const plan = Number(
    sql(
      `INSERT INTO proj_plan(project_id,title,due_date,plan_type,status) VALUES(${id},'测试付款节点','2027-07-01','TODO','DOING'); SELECT LAST_INSERT_ID();`,
    ),
  );
  const deliverable = Number(
    sql(
      `INSERT INTO proj_deliverable(project_id,name,due_date,status) VALUES(${id},'测试交付物','2027-08-01','PENDING'); SELECT LAST_INSERT_ID();`,
    ),
  );
  const participant = Number(
    sql(
      `INSERT INTO proj_participant(project_id,org_name) VALUES(${id},'原外协单位'); SELECT LAST_INSERT_ID();`,
    ),
  );
  const annual = Number(
    sql(
      `INSERT INTO proj_annual_plan(project_id,year,annual_goal,plan_content) VALUES(${id},2027,'原年度目标','原年度工作'); SELECT LAST_INSERT_ID();`,
    ),
  );
  return {
    id,
    key,
    channel,
    milestone,
    plan,
    deliverable,
    participant,
    annual,
  };
}
export function clean(fixtures, {labScenarios = false} = {}) {
  if (labScenarios && process.env.CHANGE_QA_DB !== "rpm_change_lab") throw new Error("Scenario reset is lab-only");
  const ids = fixtures
    .map((f) => Number(f.id))
    .filter(Number.isSafeInteger)
    .join(",");
  if (!ids) return;
  const prefix = labScenarios ? "LAB_CHANGE_S%" : "QA_CHANGE_%";
  const count = Number(sql(`SELECT COUNT(*) FROM proj_info WHERE id IN (${ids}) AND project_no LIKE ${quote(prefix)}`));
  if (count !== new Set(fixtures.map(f => Number(f.id))).size) throw new Error("Cleanup project prefix does not match registered fixture IDs");
  // Test rows only. Attachment bytes are removed separately by their exact recorded keys.
  const objects = sql(
    `SELECT a.object_key FROM proj_change_attachment a JOIN proj_change c ON c.id=a.change_id WHERE c.project_id IN (${ids})`,
  )
    .split("\n")
    .filter(Boolean);
  if (objects.length)
    execFileSync("python3", [root + "qa/change/remove-objects.py"], {
      input: JSON.stringify(objects),
      encoding: "utf8",
    });
  for (const table of [
    "proj_change_attachment",
    "proj_change_history",
    "proj_change_item",
    "proj_change_round",
    "proj_change_control",
  ])
    sql(
      `DELETE x FROM ${table} x JOIN proj_change c ON c.id=x.change_id WHERE c.project_id IN (${ids})`,
    );
  for (const table of [
    "proj_change",
    "proj_team_member",
    "proj_milestone",
    "proj_plan",
    "proj_deliverable",
    "proj_participant",
    "proj_annual_plan",
  ])
    sql(`DELETE FROM ${table} WHERE project_id IN (${ids})`);
  sql(
    `DELETE FROM proj_info WHERE id IN (${ids}) AND project_no LIKE ${quote(prefix)}`,
  );
}
export function draft(f, targetKey = "projectGoal", overrides = {}) {
  const targets = {
    projectGoal: ["PROJECT", "INDICATOR", f.id, "新核心指标"],
    totalFund: ["PROJECT", "FUND", f.id, "250"],
    milestoneDate: ["PROJECT", "MILESTONE_DELAY", f.milestone, "2027-07-01"],
    paymentDate: ["PROJECT", "PAYMENT", f.plan, "2027-08-01"],
    partnerName: ["PROJECT", "OUTSOURCE", f.participant, "新外协单位"],
    projectEnd: ["PROJECT", "PERIOD", f.id, "2029-01-01"],
    projectName: ["DATA", "BASIC", f.id, "更新后的项目名"],
    mainWork: ["DATA", "BASIC", f.id, "新工作内容"],
    annualGoal: ["DATA", "ANNUAL", f.annual, "新年度目标"],
    annualContent: ["DATA", "ANNUAL", f.annual, "新年度工作"],
    deliverableName: ["PROJECT", "DELIVERABLE", f.deliverable, "新交付物"],
    deliverableDate: ["PROJECT", "DELIVERABLE", f.deliverable, "2027-09-01"],
    levelChannel: ["DATA", "LEVEL", f.id, "8"],
  };
  const [changeType, category, targetId, afterValue] = targets[targetKey];
  return {
    projectId: f.id,
    changeType,
    category,
    targetKey,
    targetId,
    afterValue,
    title: `${f.key} ${targetKey}`,
    reason: "测试项目调整原因及依据",
    requestKey: randomUUID(),
    legalReviewerId: 13,
    ...overrides,
  };
}
