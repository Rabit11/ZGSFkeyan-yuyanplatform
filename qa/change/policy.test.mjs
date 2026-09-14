import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
const require = createRequire(
  new URL("../../frontend/package.json", import.meta.url),
);
const esbuild = require("esbuild");
const source = esbuild.transformSync(
  readFileSync(
    new URL(
      "../../frontend/src/views/implement/change/policy.ts",
      import.meta.url,
    ),
    "utf8",
  ),
  { loader: "ts", format: "esm" },
).code;
const { routePreview, validateDraft, validateFile, formatSize } = await import(
  "data:text/javascript;base64," + Buffer.from(source).toString("base64")
);
const target = { beforeValue: "old", valueType: "text" };
const valid = {
  projectId: 1,
  title: "申请",
  reason: "调整原因",
  category: "BASIC",
  afterValue: "new",
};
test("Valid draft does not invent or require a fallback project", () => {
  assert.equal(validateDraft(valid, target), "");
  assert.match(
    validateDraft({ ...valid, projectId: undefined }, target),
    /关联项目/,
  );
  assert.match(validateDraft(valid, undefined), /变更对象/);
});
test("Title, reason and changed value are mandatory", () => {
  for (const field of ["title", "reason", "afterValue"])
    assert.ok(validateDraft({ ...valid, [field]: "  " }, target));
  assert.match(validateDraft({ ...valid, afterValue: "old" }, target), /相同/);
});
test("Major draft may be saved before legal assignment; submission validates on server", () => {
  for (const category of ["FUND", "PERIOD", "OUTSOURCE"]) {
    assert.equal(validateDraft({ ...valid, category }, target), "");
    assert.equal(
      validateDraft({ ...valid, category, legalReviewerId: 13 }, target),
      "",
    );
  }
});
test("Money input preserves precision and rejects negative or scientific notation", () => {
  for (const value of ["-1", "1.234", "1e2", "10000000000000000"])
    assert.ok(
      validateDraft(
        { ...valid, afterValue: value },
        { ...target, valueType: "money" },
      ),
    );
  assert.equal(
    validateDraft(
      { ...valid, afterValue: "0.00" },
      { ...target, valueType: "money" },
    ),
    "",
  );
});
test("Date values are normalized at the component boundary", () => {
  assert.ok(
    validateDraft(
      { ...valid, afterValue: "2027/01/02" },
      { ...target, valueType: "date" },
    ),
  );
  assert.equal(
    validateDraft(
      { ...valid, afterValue: "2027-01-02" },
      { ...target, valueType: "date" },
    ),
    "",
  );
});
test("Every major channel preview contains legal and data requires HQ", () => {
  for (const channel of ["MJKY", "SHKJCX", "CLM", "BOKH", "04ZXJX"]) {
    assert.ok(routePreview("PROJECT", "FUND", channel).includes("法务审核"));
    assert.deepEqual(routePreview("DATA", "BASIC", channel), [
      "二级单位主管初审",
      "总部科技主管确认",
    ]);
  }
});
test("MJKY includes offline archive and Shanghai ordinary change is unit final", () => {
  assert.match(routePreview("PROJECT", "INDICATOR", "MJKY").at(-1), /GXB/);
  assert.equal(routePreview("PROJECT", "INDICATOR", "SHKJCX").length, 1);
});
test("Attachments reject empty, oversize and unsupported extensions", () => {
  assert.ok(validateFile({ name: "a.pdf", size: 0 }));
  assert.ok(validateFile({ name: "a.pdf", size: 20 * 1024 * 1024 + 1 }));
  assert.ok(validateFile({ name: "a.exe", size: 10 }));
  assert.equal(validateFile({ name: "依据.PDF", size: 20 * 1024 * 1024 }), "");
});
test("File size displays correct units", () => {
  assert.equal(formatSize(20), "20 B");
  assert.equal(formatSize(1024), "1.0 KB");
  assert.equal(formatSize(1048576), "1.0 MB");
});
