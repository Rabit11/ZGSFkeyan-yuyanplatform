import assert from "node:assert/strict";
import fs from "node:fs";
import {
  api,
  good,
  upload,
  sql,
  fixture,
  clean,
  draft,
  base,
  login,
  root,
} from "./helpers.mjs";
const results = [],
  fixtures = [];
const owner = "100012",
  unit = "100005",
  staff = "100006",
  hq = "100004",
  head = "100003",
  legal = "100009",
  tech = "100014";
const make = (...args) => {
  const f = fixture(...args);
  fixtures.push(f);
  return f;
};
async function check(name, fn) {
  try {
    await fn();
    results.push({ name, pass: true });
    console.log("PASS", name);
  } catch (e) {
    results.push({ name, pass: false, error: e.message });
    console.error("FAIL", name, e.message);
  }
}
const expectCode = async (code, promise) =>
  assert.equal((await promise).code, code);
async function create(f, key = "projectGoal", overrides = {}) {
  return good(owner, "", "POST", draft(f, key, overrides));
}
async function submit(row, who = owner) {
  row = await upload(who, row);
  return good(who, `/${row.id}/submit`, "POST", { revision: row.revision });
}
async function approve(row, who) {
  return good(who, `/${row.id}/audit`, "POST", {
    revision: row.revision,
    pass: true,
    opinion: "按支撑依据审核通过",
  });
}
try {
  const f = make(),
    outsider = make("04ZXJX", "外部不可见测试项目", true);
  await check(
    "01 context: project scope and all typed target families",
    async () => {
      const c = await good(owner, `/context?projectId=${f.id}`);
      assert.ok(c.projects.some((p) => p.id === f.id && p.canCreate));
      assert.ok(!c.projects.some((p) => p.id === outsider.id));
      assert.equal(new Set(c.targets.map((t) => t.key)).size, 13);
    },
  );
  await check(
    "02 non-project team cannot create despite same organization",
    () => expectCode(403, api(unit, "", "POST", draft(f))),
  );
  await check("03 admin is business read-only", () =>
    expectCode(403, api("100001", "", "POST", draft(f))),
  );
  await check("04 foreign project cannot be viewed or changed", async () => {
    await expectCode(403, api(owner, `/context?projectId=${outsider.id}`));
    await expectCode(403, api(owner, "", "POST", draft(outsider)));
  });
  await check(
    "05 foreign target cannot be injected into selected project",
    () =>
      expectCode(
        422,
        api(
          owner,
          "",
          "POST",
          draft(f, "milestoneDate", { targetId: outsider.milestone }),
        ),
      ),
  );
  await check("06 major change cannot be downgraded to DATA", () =>
    expectCode(
      422,
      api(
        owner,
        "",
        "POST",
        draft(f, "totalFund", { changeType: "DATA", category: "BASIC" }),
      ),
    ),
  );
  await check("07 missing title and reason rejected", async () => {
    await expectCode(
      422,
      api(owner, "", "POST", draft(f, "projectGoal", { title: " " })),
    );
    await expectCode(
      422,
      api(owner, "", "POST", draft(f, "projectGoal", { reason: "" })),
    );
  });
  await check(
    "08 major draft allows unassigned legal but cannot submit or select unqualified users",
    async () => {
      let pending = await good(owner, "", "POST", draft(f, "totalFund", {legalReviewerId:null}));
      pending = await upload(owner, pending);
      await expectCode(422, api(owner, `/${pending.id}/submit`, "POST", {revision:pending.revision}));
      for (const legalReviewerId of [16, 1, 14])
        await expectCode(
          422,
          api(owner, "", "POST", draft(f, "totalFund", { legalReviewerId })),
        );
    },
  );
  await check("09 missing or invalid field rejected", () =>
    expectCode(
      422,
      api(
        owner,
        "",
        "POST",
        draft(f, "projectGoal", { targetKey: "name; DROP TABLE proj_info" }),
      ),
    ),
  );
  await check(
    "10 invalid date, earlier milestone and out-of-period date rejected",
    async () => {
      for (const afterValue of ["2027-02-30", "2027-05-01", "2030-01-01"])
        await expectCode(
          422,
          api(owner, "", "POST", draft(f, "milestoneDate", { afterValue })),
        );
    },
  );
  await check(
    "11 money cannot be negative, overprecision or below committed amount",
    async () => {
      for (const afterValue of ["-1", "250.001", "119.99"])
        await expectCode(
          422,
          api(owner, "", "POST", draft(f, "totalFund", { afterValue })),
        );
    },
  );
  await check(
    "12 project end cannot precede existing plan/deliverable/milestone",
    () =>
      expectCode(
        422,
        api(
          owner,
          "",
          "POST",
          draft(f, "projectEnd", { afterValue: "2027-01-01" }),
        ),
      ),
  );
  await check("13 non-implementation project rejects creation", async () => {
    sql(`UPDATE proj_info SET status='DRAFT' WHERE id=${f.id}`);
    try {
      await expectCode(403, api(owner, "", "POST", draft(f)));
    } finally {
      sql(`UPDATE proj_info SET status='IMPLEMENTING' WHERE id=${f.id}`);
    }
  });
  let row = await create(f);
  await check(
    "14 draft snapshots actual original value and persists two-level route",
    async () => {
      assert.equal(row.beforeValue, "原核心指标");
      assert.equal(row.status, "DRAFT");
      assert.deepEqual(
        row.route.map((n) => n.code),
        ["UNIT_REVIEW", "HQ_REVIEW"],
      );
      assert.equal(
        sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),
        "原核心指标",
      );
    },
  );
  await check("15 missing support material blocks submit", () =>
    expectCode(
      422,
      api(owner, `/${row.id}/submit`, "POST", { revision: row.revision }),
    ),
  );
  await check("16 non-author cannot edit/delete draft", async () => {
    await expectCode(
      403,
      api("100013", `/${row.id}`, "PUT", {
        ...draft(f),
        revision: row.revision,
      }),
    );
    await expectCode(
      403,
      api("100013", `/${row.id}?revision=${row.revision}`, "DELETE"),
    );
  });
  await check("17 missing or stale revision blocks save", async () => {
    await expectCode(409, api(owner, `/${row.id}`, "PUT", draft(f)));
    await expectCode(
      409,
      api(owner, `/${row.id}`, "PUT", { ...draft(f), revision: 9 }),
    );
  });
  await check("18 real file upload/download roundtrip", async () => {
    row = await upload(owner, row);
    assert.equal(row.attachments.length, 1);
    const response = await fetch(
      base + `/api/changes/${row.id}/files/${row.attachments[0].id}/download`,
      { headers: { Authorization: `Bearer ${await login(owner)}` } },
    );
    assert.equal(await response.text(), "独立测试材料，仅用于模块验证。");
  });
  await check("19 unsupported and empty file rejected", async () => {
    for (const [name, content] of [
      ["evil.exe", "bad"],
      ["empty.txt", ""],
    ]) {
      const data = new FormData();
      data.append("file", new Blob([content]), name);
      await expectCode(
        422,
        api(
          owner,
          `/${row.id}/files?revision=${row.revision}&kind=SUPPORT`,
          "POST",
          data,
        ),
      );
    }
  });
  await check(
    "20 remove support reference and prevent removed download",
    async () => {
      const fileId = row.attachments[0].id;
      row = await good(
        owner,
        `/${row.id}/files/${fileId}?revision=${row.revision}`,
        "DELETE",
      );
      assert.equal(row.attachments.length, 0);
      await expectCode(404, api(owner, `/${row.id}/files/${fileId}/download`));
      row = await upload(owner, row);
    },
  );
  await check(
    "21 submit locks record at unit review without writeback",
    async () => {
      row = await good(owner, `/${row.id}/submit`, "POST", {
        revision: row.revision,
      });
      assert.equal(row.status, "APPROVING");
      assert.equal(row.stepIndex, 0);
      assert.equal(row.canEdit, false);
      assert.equal(
        sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),
        "原核心指标",
      );
    },
  );
  await check(
    "22 submit, edit, delete and support upload locked in review",
    async () => {
      await expectCode(
        403,
        api(owner, `/${row.id}/submit`, "POST", { revision: row.revision }),
      );
      await expectCode(
        409,
        api(owner, `/${row.id}`, "PUT", {
          ...draft(f),
          revision: row.revision,
        }),
      );
      await expectCode(
        409,
        api(owner, `/${row.id}?revision=${row.revision}`, "DELETE"),
      );
      const data = new FormData();
      data.append("file", new Blob(["x"]), "x.txt");
      await expectCode(
        409,
        api(
          owner,
          `/${row.id}/files?revision=${row.revision}&kind=SUPPORT`,
          "POST",
          data,
        ),
      );
    },
  );
  await check(
    "23 cannot self-review, skip to HQ or let admin review",
    async () => {
      for (const who of [owner, hq, "100001"])
        await expectCode(
          403,
          api(who, `/${row.id}/audit`, "POST", {
            revision: row.revision,
            pass: true,
            opinion: "越权审核",
          }),
        );
    },
  );
  await check("24 review result and actual opinion mandatory", async () => {
    await expectCode(
      422,
      api(unit, `/${row.id}/audit`, "POST", {
        revision: row.revision,
        pass: true,
        opinion: " ",
      }),
    );
    await expectCode(
      422,
      api(unit, `/${row.id}/audit`, "POST", {
        revision: row.revision,
        opinion: "意见",
      }),
    );
  });
  await check("25 mine only contains current actor tasks", async () => {
    assert.ok(
      (await good(unit, `?mine=true&keyword=${f.key}`)).records.some(
        (r) => r.id === row.id,
      ),
    );
    assert.ok(
      !(await good(hq, `?mine=true&keyword=${f.key}`)).records.some(
        (r) => r.id === row.id,
      ),
    );
  });
  await check(
    "26 reject returns to author, preserving opinion and history",
    async () => {
      row = await good(unit, `/${row.id}/audit`, "POST", {
        revision: row.revision,
        pass: false,
        opinion: "请补充指标测量依据",
      });
      row = await good(owner, `/${row.id}`);
      assert.equal(row.status, "REJECTED");
      assert.ok(row.canEdit);
      assert.ok(
        row.history.some(
          (h) => h.action === "REJECT" && h.opinion === "请补充指标测量依据",
        ),
      );
    },
  );
  await check("27 edit and resubmit starts from first node", async () => {
    row = await good(owner, `/${row.id}`, "PUT", {
      ...draft(f, "projectGoal", { afterValue: "最终核心指标" }),
      revision: row.revision,
    });
    row = await good(owner, `/${row.id}/submit`, "POST", {
      revision: row.revision,
    });
    assert.equal(row.stepIndex, 0);
    assert.equal(row.beforeValue, "原核心指标");
  });
  await check(
    "28 unit approval does not finish or write prematurely",
    async () => {
      row = await approve(row, unit);
      assert.equal(row.status, "APPROVING");
      assert.equal(row.stepIndex, 1);
      assert.equal(
        sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),
        "原核心指标",
      );
    },
  );
  await check("29 double review stale version rejected", () =>
    expectCode(
      409,
      api(unit, `/${row.id}/audit`, "POST", {
        revision: row.revision - 1,
        pass: true,
        opinion: "重复",
      }),
    ),
  );
  await check(
    "30 final HQ review atomically writes value and audit history",
    async () => {
      row = await approve(row, hq);
      assert.equal(row.status, "APPROVED");
      assert.ok(row.appliedAt);
      assert.equal(
        sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),
        "最终核心指标",
      );
      assert.equal(row.history.filter((h) => h.action === "APPLY").length, 1);
    },
  );
  await check("31 closed request cannot be reviewed again", () =>
    expectCode(
      403,
      api(hq, `/${row.id}/audit`, "POST", {
        revision: row.revision,
        pass: true,
        opinion: "重复",
      }),
    ),
  );
  await check("32 major fund route requires legal before HQ", async () => {
    let r = await submit(await create(f, "totalFund"));
    assert.deepEqual(
      r.route.map((n) => n.code),
      ["UNIT_REVIEW", "LEGAL", "HQ_REVIEW"],
    );
    r = await approve(r, unit);
    await expectCode(
      403,
      api(head, `/${r.id}/audit`, "POST", {
        revision: r.revision,
        pass: true,
        opinion: "越级",
      }),
    );
    assert.ok(
      (await good(legal, "?mine=true")).records.some((x) => x.id === r.id),
    );
    r = await approve(r, legal);
    assert.equal(
      sql(`SELECT total_fund FROM proj_info WHERE id=${f.id}`),
      "200.00",
    );
    r = await approve(r, head);
    assert.equal(r.status, "APPROVED");
    assert.equal(
      sql(`SELECT total_fund FROM proj_info WHERE id=${f.id}`),
      "250.00",
    );
  });
  await check("33 Shanghai ordinary change finalizes at unit", async () => {
    const sh = make("SHKJCX");
    let r = await submit(await create(sh));
    r = await approve(r, unit);
    assert.equal(r.status, "APPROVED");
    assert.equal(
      sql(`SELECT goal FROM proj_info WHERE id=${sh.id}`),
      "新核心指标",
    );
  });
  for (const channel of ["CLM", "BOKH"])
    await check(
      `34 ${channel} staff initial review and unit head confirmation`,
      async () => {
        const p = make(channel);
        let r = await submit(await create(p));
        await expectCode(
          403,
          api(unit, `/${r.id}/audit`, "POST", {
            revision: r.revision,
            pass: true,
            opinion: "跳过初审",
          }),
        );
        r = await approve(r, staff);
        assert.equal(r.status, "APPROVING");
        await expectCode(
          403,
          api(staff, `/${r.id}/audit`, "POST", {
            revision: r.revision,
            pass: true,
            opinion: "不能自己终审",
          }),
        );
        r = await approve(r, unit);
        assert.equal(r.status, "APPROVED");
      },
    );
  await check(
    "35 MJKY offline evidence required before final writeback",
    async () => {
      const p = make("MJKY");
      let r = await submit(await create(p));
      r = await approve(r, unit);
      r = await approve(r, hq);
      assert.equal(r.status, "AWAITING_ARCHIVE");
      assert.equal(
        sql(`SELECT goal FROM proj_info WHERE id=${p.id}`),
        "原核心指标",
      );
      await expectCode(
        422,
        api(owner, `/${r.id}/archive`, "POST", {
          revision: r.revision,
          reference: "GXB-QA-2026",
        }),
      );
      r = await upload(owner, r, "EXTERNAL", "线下回执.txt");
      r = await good(owner, `/${r.id}/archive`, "POST", {
        revision: r.revision,
        reference: "GXB-QA-2026",
        opinion: "线下上报完成",
      });
      assert.equal(r.status, "APPROVED");
      assert.equal(r.archiveRef, "GXB-QA-2026");
      assert.equal(
        sql(`SELECT goal FROM proj_info WHERE id=${p.id}`),
        "新核心指标",
      );
    },
  );
  await check(
    "36 DATA requires HQ technology confirmation (head cannot substitute)",
    async () => {
      let r = await submit(await create(f, "mainWork"));
      r = await approve(r, unit);
      await expectCode(
        403,
        api(head, `/${r.id}/audit`, "POST", {
          revision: r.revision,
          pass: true,
          opinion: "代审",
        }),
      );
      r = await approve(r, hq);
      assert.equal(
        sql(`SELECT main_work FROM proj_info WHERE id=${f.id}`),
        "新工作内容",
      );
    },
  );
  await check(
    "37 concurrent drafts cannot overwrite changed source",
    async () => {
      let a = await submit(
        await create(f, "projectGoal", { afterValue: "A结果" }),
      );
      let b = await submit(
        await create(f, "projectGoal", { afterValue: "B结果" }),
      );
      a = await approve(a, unit);
      b = await approve(b, unit);
      await approve(a, hq);
      await expectCode(
        409,
        api(hq, `/${b.id}/audit`, "POST", {
          revision: b.revision,
          pass: true,
          opinion: "旧值冲突",
        }),
      );
      assert.equal((await good(hq, `/${b.id}`)).status, "APPROVING");
      assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${f.id}`), "A结果");
    },
  );
  await check(
    "38 same create request key produces one application",
    async () => {
      const body = draft(f, "projectGoal", { afterValue: "幂等结果" });
      const [a, b] = await Promise.all([
        good(owner, "", "POST", body),
        good(owner, "", "POST", body),
      ]);
      assert.equal(a.id, b.id);
    },
  );
  await check("39 simultaneous node approvals have one winner", async () => {
    let r = await submit(
      await create(f, "projectGoal", { afterValue: "并发结果" }),
    );
    const body = { revision: r.revision, pass: true, opinion: "并发审核" };
    const responses = await Promise.all([
      api(unit, `/${r.id}/audit`, "POST", body),
      api(unit, `/${r.id}/audit`, "POST", body),
    ]);
    assert.deepEqual(
      responses.map((x) => x.code).sort((a, b) => a - b),
      [0, 409],
    );
    assert.equal((await good(owner, `/${r.id}`)).stepIndex, 1);
  });
  for (const [targetKey, table, column, idKey, value] of [
    ["milestoneDate", "proj_milestone", "plan_date", "milestone", "2027-07-01"],
    ["paymentDate", "proj_plan", "due_date", "plan", "2027-08-01"],
    [
      "partnerName",
      "proj_participant",
      "org_name",
      "participant",
      "新外协单位",
    ],
    ["projectEnd", "proj_info", "end_date", "id", "2029-01-01"],
    ["deliverableName", "proj_deliverable", "name", "deliverable", "新交付物"],
    [
      "deliverableDate",
      "proj_deliverable",
      "due_date",
      "deliverable",
      "2027-09-01",
    ],
    ["annualGoal", "proj_annual_plan", "annual_goal", "annual", "新年度目标"],
    [
      "annualContent",
      "proj_annual_plan",
      "plan_content",
      "annual",
      "新年度工作",
    ],
    ["projectName", "proj_info", "name", "id", "更新后的项目名"],
    ["levelChannel", "proj_info", "channel_id", "id", "8"],
  ])
    await check(`40 typed writeback ${targetKey}`, async () => {
      let r = await submit(await create(f, targetKey));
      r = await approve(r, unit);
      if (r.route[r.stepIndex].code === "LEGAL") r = await approve(r, legal);
      r = await approve(r, hq);
      assert.equal(r.status, "APPROVED");
      assert.equal(
        sql(`SELECT ${column} FROM ${table} WHERE id=${f[idKey]}`),
        value,
      );
      if (targetKey === "levelChannel")
        assert.equal(
          sql(`SELECT level_code,channel_name FROM proj_info WHERE id=${f.id}`),
          "LOCAL\t上海市科技创新行动计划",
        );
    });
  await check(
    "41 completed milestone excluded and cannot be changed",
    async () => {
      sql(`UPDATE proj_milestone SET status='DONE' WHERE id=${f.milestone}`);
      assert.ok(
        !(await good(owner, `/context?projectId=${f.id}`)).targets.some(
          (t) => t.key === "milestoneDate",
        ),
      );
      await expectCode(
        409,
        api(
          owner,
          "",
          "POST",
          draft(f, "milestoneDate", { afterValue: "2027-10-01" }),
        ),
      );
    },
  );
  await check("42 payment in completion review cannot be changed", async () => {
    sql(`UPDATE proj_plan SET apply_status='PENDING' WHERE id=${f.plan}`);
    await expectCode(
      422,
      api(
        owner,
        "",
        "POST",
        draft(f, "paymentDate", { afterValue: "2027-10-01" }),
      ),
    );
  });
  await check(
    "43 technical lead draft handed off to named project owner",
    async () => {
      let r = await good(
        tech,
        "",
        "POST",
        draft(f, "projectGoal", { afterValue: "技术协同结果" }),
      );
      r = await upload(tech, r);
      await expectCode(
        403,
        api(tech, `/${r.id}/submit`, "POST", { revision: r.revision }),
      );
      assert.ok((await good(owner, `/${r.id}`)).canSubmit);
      r = await good(owner, `/${r.id}/submit`, "POST", {
        revision: r.revision,
      });
      assert.equal(r.status, "APPROVING");
    },
  );
  await check("44 pagination/filter/keyword and bounds", async () => {
    const all = await good(owner, `?keyword=${f.key}&size=2&page=1`);
    assert.equal(all.records.length, 2);
    assert.ok(all.total > 2);
    const next = await good(owner, `?keyword=${f.key}&size=2&page=2`);
    assert.notEqual(next.records[0].id, all.records[0].id);
    const filtered = await good(
      owner,
      `?keyword=${f.key}&status=APPROVED&changeType=DATA`,
    );
    assert.ok(
      filtered.records.every(
        (r) => r.status === "APPROVED" && r.changeType === "DATA",
      ),
    );
    assert.ok(Array.isArray((await good(owner, "?size=200")).records));
    await expectCode(422, api(owner, "?size=1000"));
    await expectCode(422, api(owner, "?page=0"));
  });
  await check(
    "45 foreign project changes and attachments invisible",
    async () => {
      sql(
        `INSERT INTO proj_change(project_id,change_type,title,status) VALUES(${outsider.id},'PROJECT','外部申请','DRAFT')`,
      );
      const id = Number(
        sql(`SELECT MAX(id) FROM proj_change WHERE project_id=${outsider.id}`),
      );
      await expectCode(403, api(owner, `/${id}`));
      assert.ok(
        !(await good(owner, `?projectId=${outsider.id}`)).records.length,
      );
    },
  );
  await check(
    "46 legacy requests are readable but mutation is blocked",
    async () => {
      sql(
        `INSERT INTO proj_change(project_id,change_type,title,status) VALUES(${f.id},'PROJECT','历史申请','DRAFT')`,
      );
      const id = Number(
        sql(`SELECT MAX(id) FROM proj_change WHERE project_id=${f.id}`),
      );
      const legacy = await good(owner, `/${id}`);
      assert.ok(legacy.legacy);
      assert.equal(legacy.canEdit, false);
      await expectCode(
        409,
        api(owner, `/${id}/submit`, "POST", { revision: 0 }),
      );
    },
  );
  await check("47 deleting a draft preserves an audit record", async () => {
    const r = await create(f, "projectGoal", { afterValue: "删除测试" });
    await good(owner, `/${r.id}?revision=${r.revision}`, "DELETE");
    await expectCode(404, api(owner, `/${r.id}`));
    assert.equal(
      sql(
        `SELECT COUNT(*) FROM proj_change_history WHERE change_id=${r.id} AND action='DELETE'`,
      ),
      "1",
    );
    sql(`DELETE FROM proj_change_history WHERE change_id=${r.id}`);
  });
} finally {
  fs.writeFileSync(
    root + "deploy/change/api-results.json",
    JSON.stringify({ date: new Date().toISOString(), results }, null, 2),
  );
  clean(fixtures);
}
console.log(
  `${results.filter((r) => r.pass).length}/${results.length} scenarios passed`,
);
if (results.some((r) => !r.pass)) process.exitCode = 1;
