# Five Stage Entry Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce the five-stage UI rule that business filling, upload, submit, and audit actions start from sidebar/function pages, while flowchart, swimlane, lifecycle, and node dialogs stay read-only.

**Architecture:** Keep existing business pages and APIs intact. Centralize read-only flow behavior in flow utility/component layers, then adjust each five-stage visual component so node actions cannot open editable workflows.

**Tech Stack:** Vue 3, TypeScript, Ant Design Vue, Vite.

**Spec:** `给会冉/UI设计规范/五阶段信息填报入口统一UI方案.md`

## Global Constraints

- All project declaration, filing, implementation, acceptance, and transformation filling entries must come from the sidebar or corresponding function menu.
- Flow nodes, swimlanes, lifecycle nodes, visual node details, and image-like visual pages must not provide upload, edit, submit, or audit entry points.
- Existing business function pages, fields, material lists, permissions, and approval chains must remain available.
- A concrete task keeps one primary business submit entry inside its functional page.
- Visual pages may show state, owners, requirements, records, and read-only attachments.

---

### Task 1: Read-Only Flow Action Helper

**Files:**
- Create: `给会冉/frontend/src/utils/flowEntryPolicy.ts`
- Test: `给会冉/frontend/src/utils/flowEntryPolicy.selftest.mjs`

**Interfaces:**
- Produces: `READONLY_FLOW_NOTICE`, `readonlyFlowActionLabel(label?: string): string`, `readonlyFlowPath(path?: string): undefined`, `isBusinessActionLabel(label?: string): boolean`

- [ ] **Step 1: Write a self-test**

```javascript
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./flowEntryPolicy.ts', import.meta.url), 'utf8')
assert.match(source, /READONLY_FLOW_NOTICE/)
assert.match(source, /function readonlyFlowActionLabel/)
assert.match(source, /function readonlyFlowPath/)
assert.match(source, /去填报/)
assert.match(source, /去上传/)
assert.match(source, /去审核/)
assert.match(source, /return '查看'/)
assert.match(source, /return undefined/)
```

- [ ] **Step 2: Run self-test to verify it fails before helper exists**

Run: `node src/utils/flowEntryPolicy.selftest.mjs`

Expected: fails because `flowEntryPolicy.ts` does not exist.

- [ ] **Step 3: Create helper**

Create helper with the interfaces above. Business labels such as `去填报`, `去上传`, `去审核`, `去办理`, `提交`, `审核`, `确认/上传`, `去复核`, `去总核`, `按清单上传交付物证明` return `查看`; passive labels such as `查看` and `查看清单` remain passive. Business paths return `undefined`.

- [ ] **Step 4: Run self-test and typecheck/build**

Run: `node src/utils/flowEntryPolicy.selftest.mjs`, then `npm.cmd run typecheck`, then `npm.cmd run build`.

### Task 2: Implementation Flow Read-Only Conversion

**Files:**
- Modify: `给会冉/frontend/src/utils/implementFlow.ts`
- Modify: `给会冉/frontend/src/components/implement/ImplementFlowDialog.vue`

**Interfaces:**
- Consumes: `READONLY_FLOW_NOTICE`, `readonlyFlowActionLabel`, `readonlyFlowPath`

- [ ] **Step 1: Add tests for implementation flow source**

Extend the self-test to assert implementation flow imports the helper, maps node labels through `readonlyFlowActionLabel`, maps paths through `readonlyFlowPath`, and includes the read-only notice in the dialog.

- [ ] **Step 2: Run self-test to verify it fails**

Run: `node src/utils/flowEntryPolicy.selftest.mjs`

Expected: fails because implementation flow has not been converted.

- [ ] **Step 3: Convert implementation flow**

Import the helper and apply it in the shared `nodeOf` factory so all implementation nodes lose editable business entry paths and show passive labels. Add an alert in the dialog explaining that visual nodes are read-only and users should enter function pages from the sidebar.

- [ ] **Step 4: Verify**

Run self-test, typecheck, and build.

### Task 3: Five-Stage Visual Components Read-Only Conversion

**Files:**
- Modify: declaration, filing, acceptance, transform, lifecycle visual utilities/components under `给会冉/frontend/src`

**Interfaces:**
- Consumes: read-only helper from Task 1.

- [ ] **Step 1: Extend self-test**

Assert that declaration, filing, acceptance, transform, and lifecycle flow sources contain the read-only notice or helper usage, and do not keep active node path labels for upload/submit/audit behavior.

- [ ] **Step 2: Run self-test to verify it fails**

Run: `node src/utils/flowEntryPolicy.selftest.mjs`

- [ ] **Step 3: Convert visual components**

Change visual-node actions to read-only labels, remove editable route pushes from visual nodes, and add the standard notice to dialogs/panels.

- [ ] **Step 4: Verify**

Run self-test, typecheck, and build.

### Task 4: Final Verification

**Files:**
- Review all files changed above.

- [ ] **Step 1: Search for forbidden visual entry labels**

Run `rg` for visual flow files with `去填报|去上传|去审核|提交|上传|审核|router.push`.

- [ ] **Step 2: Build**

Run `npm.cmd run build`.

- [ ] **Step 3: Report exact changed files, verification commands, and remaining limits**

Summarize what was changed and what still belongs to later UI page layout work.

