import {selectOneObject} from './select-objects.mjs';
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  fixture,
  clean,
  sql,
  good,
  draft,
  upload,
  root,
  base,
} from "./helpers.mjs";
const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE ||
    "/home/dev/.cache/aeroduct-browser/node_modules/playwright/index.mjs"
);
const browser = await chromium.launch({
  headless: true,
  args: ["--no-sandbox"],
});
const fixtures = [],
  results = [],
  errors = [];
const f = fixture("04ZXJX", "变更模块浏览器测试"),
  other = fixture("04ZXJX", "切换项目测试");
fixtures.push(f, other);
let page;
async function check(name, fn) {
  await fn();
  results.push({ name, pass: true });
  console.log("PASS", name);
}
async function session(user) {
  const p = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  p.on("pageerror", (e) => errors.push(e.message));
  await p.addLocatorHandler(p.getByRole("button", { name: "稍后处理" }), async (button) => { await button.click(); });
  await p.goto(base + "/#/login");
  await p.getByPlaceholder("请输入工号").fill(user);
  await p.getByPlaceholder("请输入密码").fill(user);
  await p.getByRole("button", { name: /^登\s*录$/ }).click();
  await p.waitForURL("**/#/dashboard");
  await p.waitForLoadState("networkidle");
  await p.goto(base + "/?changeqa=" + Date.now() + "#/implement/change");
  await p.waitForLoadState("networkidle");
  await p.getByRole("heading", { name: "项目变更", exact: true }).click();
  return p;
}
async function select(label, text, p = page) {
  if(label === "变更对象") return selectOneObject(p, text);
  await p.getByLabel(label, { exact: true }).click();
  await p.getByLabel(label, { exact: true }).locator("input").fill(text);
  const options = p
    .locator(".ant-select-dropdown:visible .ant-select-item-option")
    .filter({ hasText: text });
  await options.first().click();
}
async function filter(text, p = page) {
  await p.getByLabel("搜索变更", { exact: true }).fill(text);
  await p.getByRole("button", { name: /^查\s*询$/ }).click();
  await p.waitForLoadState("networkidle");
}
async function openRow(title, p = page) {
  await filter(title, p);
  await p
    .locator(".application-link")
    .filter({ hasText: title })
    .first()
    .click();
  await p.locator(".ant-drawer-title").filter({ hasText: "变更申请详情" }).waitFor();
}
const drawer = () => page.locator(".ant-drawer-content:visible");
async function review(p, pass, opinion) {
  await p.getByRole("button", { name: "办理审核", exact: true }).click();
  if (!pass) await p.getByText("驳回，退回补正", { exact: true }).click();
  await p.getByLabel("办理意见", { exact: true }).fill(opinion);
  await p.getByRole("button", { name: "确认办理", exact: true }).click();
  await p.locator(".ant-modal:visible").waitFor({ state: "hidden" });
  await p.waitForLoadState("networkidle");
}
try {
  page = await session("100012");
  await check(
    "B01 original shell and integrated change route render",
    async () => {
      assert.equal(new URL(page.url()).hash, "#/implement/change");
      await page
        .getByRole("heading", { name: "项目变更", exact: true })
        .waitFor();
      assert.ok(await page.locator(".workbench-guide").isVisible());
      await page.screenshot({
        path: root + "deploy/change/01-workbench.png",
        fullPage: true,
      });
    },
  );
  await check(
    "B02 no fallback project; blank draft shows actionable validation",
    async () => {
      await page.getByRole("button", { name: /发起变更$/ }).click();
      await drawer().getByRole("button", { name: "保存草稿" }).click();
      assert.ok(
        await drawer().getByText("请选择关联项目", { exact: true }).isVisible(),
      );
      assert.ok(
        await page
          .getByRole("button", { name: /多选变更类别与对象$/ })
          .isDisabled(),
      );
    },
  );
  await check(
    "B03 project/type/target selection shows read-only actual before value",
    async () => {
      await select("关联项目", f.key);
      await page.waitForLoadState("networkidle");
      await select("变更对象", "项目目标 / 核心指标");
      assert.equal(
        await page.locator(".selected-item .item-values > div:first-child p").first().innerText(),
        "原核心指标",
      );
      assert.ok(
        await drawer()
          .getByText("总部管理部门终审", { exact: true })
          .isVisible(),
      );
    },
  );
  const title = f.key + " 浏览器核心指标调整";
  await check(
    "B04 save draft persists without submitting and enables upload",
    async () => {
      await page.getByLabel("变更标题", { exact: true }).fill(title);
      await page
        .getByLabel("第1项调整后内容", { exact: true })
        .fill("浏览器审核后的核心指标");
      await page
        .getByLabel("变更缘由", { exact: true })
        .fill("依据现场验证结果补充测量指标。");
      await drawer().getByRole("button", { name: "保存草稿" }).click();
      await page
        .getByText("草稿已保存，可上传支撑材料后提交审批", { exact: true })
        .waitFor();
      await page.waitForLoadState("networkidle");
      await drawer().locator("#change-materials button").filter({hasText:"上传支撑材料"}).click({trial:true});
      assert.ok(
        await drawer()
          .locator("#change-materials button")
          .filter({ hasText: "上传支撑材料" })
          .isEnabled(),
      );
      assert.equal(
        sql(
          `SELECT status FROM proj_change WHERE project_id=${f.id} ORDER BY id DESC LIMIT 1`,
        ),
        "DRAFT",
      );
    },
  );
  await check("B05 actual attachment upload and browser download", async () => {
    await drawer()
      .locator("input[type=file]")
      .setInputFiles({
        name: "指标调整依据.txt",
        mimeType: "text/plain",
        buffer: Buffer.from("浏览器支撑材料内容"),
      });
    await drawer().getByRole("button", { name: "指标调整依据.txt", exact: true }).waitFor();
    const waiting = page.waitForEvent("download");
    await drawer().getByRole("button", { name: "指标调整依据.txt", exact: true }).click();
    const download = await waiting;
    assert.equal(download.suggestedFilename(), "指标调整依据.txt");
    assert.equal(await download.failure(), null);
  });
  await check(
    "B06 explicit submit confirmation locks draft and shows unit node",
    async () => {
      await drawer().getByRole("button", { name: "一次提交 1 项" }).click();
      await page.getByRole("button", { name: "确认提交" }).click();
      await page
        .locator(".ant-drawer-title").filter({ hasText: "变更申请详情" })
        .waitFor();
      await page.waitForLoadState("networkidle");
      assert.equal(
        await drawer().getByRole("button", { name: "编辑草稿" }).count(),
        0,
      );
      assert.equal(
        sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),
        "原核心指标",
      );
      await page.screenshot({
        path: root + "deploy/change/02-comparison-and-route.png",
        fullPage: true,
      });
    },
  );
  const unit = await session("100005");
  await check(
    "B07 unit sees mine and must enter actual review opinion",
    async () => {
      await unit.getByRole("tab", { name: "待我办理" }).click();
      await openRow(title, unit);
      await unit.getByRole("button", { name: "办理审核", exact: true }).click();
      await unit.getByRole("button", { name: "确认办理", exact: true }).click();
      await unit.getByText("请填写办理意见", { exact: true }).waitFor();
      await unit.getByRole("button", { name: /^取\s*消$/ }).click();
    },
  );
  await check("B08 reject through UI, author edits and resubmits", async () => {
    await review(unit, false, "请补充验证试验编号");
    await drawer().getByRole("button", { name: /^关\s*闭$/, exact: true }).click();
    await openRow(title);
    assert.ok(
      await drawer()
        .getByText("请补充验证试验编号", { exact: true })
        .isVisible(),
    );
    await drawer().getByRole("button", { name: "编辑草稿" }).click();
    await page
      .getByLabel("变更缘由", { exact: true })
      .fill("已补充试验编号 QA-2026-0914。");
    await drawer().getByRole("button", { name: "保存草稿" }).click();
    await page
      .getByText("草稿已保存，可上传支撑材料后提交审批", { exact: true })
      .waitFor();
    await drawer().getByRole("button", { name: "一次提交 1 项" }).click();
    await page.getByRole("button", { name: "确认提交" }).click();
    await page
      .locator(".ant-drawer-title").filter({ hasText: "变更申请详情" })
      .waitFor();
    await page.waitForLoadState("networkidle");
  });
  await check(
    "B09 two real reviewers finalize and UI shows one writeback event",
    async () => {
      await unit
        .locator(".ant-drawer-content:visible")
        .getByRole("button", { name: /^关\s*闭$/, exact: true })
        .click();
      await openRow(title, unit);
      await review(unit, true, "单位审核通过");
      assert.equal(
        sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),
        "原核心指标",
      );
      const hq = await session("100004");
      await openRow(title, hq);
      await review(hq, true, "总部核对指标后通过");
      assert.equal(
        sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),
        "浏览器审核后的核心指标",
      );
      await hq.screenshot({
        path: root + "deploy/change/03-approved-history.png",
        fullPage: true,
      });
      await hq.close();
    },
  );
  await drawer().getByRole("button", { name: /^关\s*闭$/, exact: true }).click();
  await check(
    "B10 discard confirmation preserves original saved draft",
    async () => {
      await page.getByRole("button", { name: /发起变更$/ }).click();
      await page.getByLabel("变更标题", { exact: true }).fill("未保存");
      await drawer().getByRole("button", { name: /^关\s*闭$/, exact: true }).click();
      await page.getByText("放弃未保存的修改？", { exact: true }).waitFor();
      await page.getByRole("button", { name: "继续编辑" }).click();
      assert.equal(
        await page.getByLabel("变更标题", { exact: true }).inputValue(),
        "未保存",
      );
      await drawer().getByRole("button", { name: /^关\s*闭$/, exact: true }).click();
      await page.getByRole("button", { name: "放弃修改" }).click();
      await drawer().waitFor({ state: "hidden" });
    },
  );
  await check(
    "B11 major draft saves without legal but readiness blocks submission",
    async () => {
      await page.getByRole("button", { name: /发起变更$/ }).click();
      await select("关联项目", f.key);
      await page.waitForLoadState("networkidle");
      await select("变更对象", "总经费（万元）");
      await page.getByLabel("变更标题", { exact: true }).fill("重大经费调整");
      await page.getByLabel("第1项调整后金额", { exact: true }).fill("300.25");
      await page.getByLabel("变更缘由", { exact: true }).fill("新增实验工作量");
      await drawer().getByRole("button", { name: "保存草稿" }).click();
      await drawer().getByText('提交前需要处理', {exact:true}).waitFor();
      assert.equal(await drawer().getByRole('button', {name:'一次提交 1 项'}).isDisabled(), true);
      await select("法务办理人", "100009");
      await page.screenshot({
        path: root + "deploy/change/04-major-change-form.png",
        fullPage: true,
      });
      await drawer().getByRole("button", { name: /^关\s*闭$/, exact: true }).click();
      await page.getByRole("button", { name: "放弃修改" }).click();
    },
  );
  await check(
    "B12 data type resets selected field and displays HQ confirmation",
    async () => {
      await page.getByRole("button", { name: /发起变更$/ }).click();
      await select("关联项目", f.key);
      await page.waitForLoadState("networkidle");
      await drawer().getByText("数据变更 · 纠正登记", { exact: true }).click();
      await select("变更对象", "项目名称纠错");
      assert.ok(
        await drawer()
          .getByText("总部科技主管确认", { exact: true })
          .isVisible(),
      );
      assert.equal(
        await page.getByLabel("法务办理人", { exact: true }).count(),
        0,
      );
      await drawer().getByRole("button", { name: /^关\s*闭$/, exact: true }).click();
      await page.getByRole("button", { name: "放弃修改" }).click();
    },
  );
  await check("B13 empty results and reset filters", async () => {
    await filter("NO_MATCH_" + f.key);
    await page.getByText("暂无符合条件的变更申请", { exact: true }).waitFor();
    await page.getByRole("button", { name: "重置筛选" }).click();
    await page.waitForLoadState("networkidle");
    await page.locator(".application-link").first().waitFor();
    assert.ok(await page.locator(".application-link").count());
  });
  await check(
    "B14 list request failure exposes retry and recovers",
    async () => {
      let once = true;
      await page.route("**/api/changes?**", async (route) => {
        if (once) {
          once = false;
          await route.fulfill({
            status: 503,
            contentType: "application/json",
            body: JSON.stringify({ msg: "测试网络暂不可用" }),
          });
        } else await route.continue();
      });
      await page.getByRole("button", { name: /^查\s*询$/ }).click();
      await page.getByText("测试网络暂不可用", { exact: true }).waitFor();
      await page.getByRole("button", { name: /^重\s*试$/ }).click();
      await page.waitForLoadState("networkidle");
      assert.equal(
        await page.getByText("测试网络暂不可用", { exact: true }).count(),
        0,
      );
      await page.unroute("**/api/changes?**");
    },
  );
  await check("B15 admin may view but cannot initiate or approve", async () => {
    const admin = await session("100001");
    assert.ok(
      await admin.getByRole("button", { name: /发起变更$/ }).isDisabled(),
    );
    await openRow(title, admin);
    assert.equal(
      await admin
        .getByRole("button", { name: "办理审核", exact: true })
        .count(),
      0,
    );
    await admin.close();
  });
  await check(
    "B16 narrow viewport drawer fits and footer stays usable",
    async () => {
      await page.setViewportSize({ width: 768, height: 900 });
      await openRow(title);
      await drawer().getByRole("button", { name: /^关\s*闭$/, exact: true }).click({ trial: true });
      const bounds = await drawer().boundingBox();
      assert.ok(bounds.width <= 768.5 && bounds.x >= -0.5 && bounds.x + bounds.width <= 768.5, JSON.stringify(bounds));
      assert.ok(
        await drawer()
          .getByRole("button", { name: /^关\s*闭$/, exact: true })
          .isVisible(),
      );
      await page.screenshot({
        path: root + "deploy/change/05-narrow-drawer.png",
        fullPage: true,
      });
      await drawer().getByRole("button", { name: /^关\s*闭$/, exact: true }).click();
      await page.setViewportSize({ width: 1440, height: 1000 });
    },
  );
  await check("B17 pagination loads distinct rows", async () => {
    for (let i = 0; i < 11; i++)
      await good(
        "100012",
        "",
        "POST",
        draft(f, "projectGoal", {
          title: f.key + " 分页 " + i,
          afterValue: "分页值 " + i,
        }),
      );
    await filter(f.key);
    const first = await page.locator(".application-link").first().innerText();
    await page.locator(".ant-pagination-item-2").click();
    await page.waitForFunction(previous => { const element = document.querySelector(".application-link"); return element && element.textContent.trim() !== previous; }, first);
    await page.waitForLoadState("networkidle");
    assert.notEqual(
      await page.locator(".application-link").first().innerText(),
      first,
    );
  });
  await check("B18 assigned legal reviewer handles independent node in browser", async () => {
    let r = await good("100012", "", "POST", draft(f, "totalFund", { title: f.key + " 法务页面验证", afterValue: "350" }));
    r = await upload("100012", r);
    r = await good("100012", `/${r.id}/submit`, "POST", { revision: r.revision });
    r = await good("100005", `/${r.id}/audit`, "POST", { revision: r.revision, pass: true, opinion: "单位通过，送法务" });
    const legalPage = await session("100009");
    await openRow(r.title, legalPage); await review(legalPage, true, "法务审核依据及结论已确认");
    r = await good("100004", `/${r.id}`);
    assert.equal(r.route[r.stepIndex].code, "HQ_REVIEW");
    assert.equal(sql(`SELECT total_fund FROM proj_info WHERE id=${f.id}`), "200.00");
    await legalPage.close();
  });
  await check("B19 MJKY offline archive uploads evidence and writes only on completion", async () => {
    const mjky = fixture("MJKY", "线下归档浏览器测试"); fixtures.push(mjky);
    let r = await good("100012", "", "POST", draft(mjky, "projectGoal", { title: mjky.key + " GXB归档" }));
    r = await upload("100012", r);
    r = await good("100012", `/${r.id}/submit`, "POST", { revision: r.revision });
    for (const who of ["100005", "100004"]) r = await good(who, `/${r.id}/audit`, "POST", { revision: r.revision, pass: true, opinion: "审核通过，线下归档" });
    await openRow(r.title);
    await drawer().locator('input[type=file]').setInputFiles({ name: "GXB回执.txt", mimeType: "text/plain", buffer: Buffer.from("测试线下回执") });
    await drawer().getByRole("button", { name: "GXB回执.txt", exact: true }).waitFor();
    await page.waitForLoadState("networkidle");
    assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${mjky.id}`), "原核心指标");
    await drawer().getByRole("button", { name: "完成线下归档", exact: true }).click();
    await page.getByLabel("线下上报文号", { exact: true }).fill("GXB-BROWSER-2026");
    await page.getByLabel("办理意见", { exact: true }).fill("已核对上报回执并完成归档");
    await page.getByRole("button", { name: "确认办理", exact: true }).click();
    await page.locator(".ant-modal:visible").waitFor({ state: "hidden" });
    await page.waitForLoadState("networkidle");
    assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${mjky.id}`), "新核心指标");
    await page.screenshot({ path: root + "deploy/change/06-gxb-archive.png", fullPage: true });
  });
  await check("B20 no browser runtime exception", async () =>
    assert.deepEqual(errors, []),
  );
} catch (e) {
  results.push({
    name: "Browser flow interrupted",
    pass: false,
    error: e.stack,
  });
  console.error(e);
  if (page)
    await page.screenshot({
      path: root + "deploy/change/browser-failure.png",
      fullPage: true,
    });
  process.exitCode = 1;
} finally {
  fs.writeFileSync(
    root + "deploy/change/browser-results.json",
    JSON.stringify(
      { date: new Date().toISOString(), results, errors },
      null,
      2,
    ),
  );
  await browser.close();
  clean(fixtures);
}
