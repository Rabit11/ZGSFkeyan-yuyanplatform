import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { createRequire } from "node:module";
import { randomUUID } from "node:crypto";
const require = createRequire(
    new URL("../../frontend/package.json", import.meta.url),
  ),
  vue = require("vue"),
  ts = require("typescript");
const code = ts.transpileModule(
  fs.readFileSync(
    new URL(
      "../../frontend/src/views/implement/change/useChangeWorkbench.ts",
      import.meta.url,
    ),
    "utf8",
  ),
  {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  },
).outputText;
const policyCode = ts.transpileModule(
  fs.readFileSync(
    new URL(
      "../../frontend/src/views/implement/change/policy.ts",
      import.meta.url,
    ),
    "utf8",
  ),
  {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  },
).outputText;
const policyModule = { exports: {} };
vm.runInNewContext(policyCode, {
  exports: policyModule.exports,
  module: policyModule,
});
const defer = () => {
  let resolve, reject;
  const promise = new Promise((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
};
function setup(overrides = {}) {
  let start, stop;
  const modals = [],
    messages = [];
  const api = {
    page: async () => ({ data: { records: [], total: 0 } }),
    context: async () => ({
      data: { projects: [], targets: [], channels: [], legalReviewers: [] },
    }),
    ...overrides,
  };
  const module = { exports: {} };
  vm.runInNewContext(code, {
    module,
    exports: module.exports,
    crypto: { randomUUID },
    require: (name) =>
      name === "vue"
        ? {
            ...vue,
            onMounted: (fn) => (start = fn),
            onBeforeUnmount: (fn) => (stop = fn),
          }
        : name === "@/api/change"
          ? { changes: api }
          : name === "@/api/request"
            ? { isSilentAuthError: () => false }
            : name === "./policy"
              ? policyModule.exports
              : {
                  message: {
                    error: (v) => messages.push(v),
                    success: (v) => messages.push(v),
                    warning: (v) => messages.push(v),
                  },
                  Modal: { confirm: (opts) => modals.push(opts) },
                },
  });
  const state = module.exports.useChangeWorkbench();
  return { state, start, stop, modals, messages };
}
test("Out-of-order list responses never replace latest search results", async () => {
  const a = defer(),
    b = defer();
  let n = 0;
  const { state: s } = setup({
    page: () => (++n === 1 ? a.promise : b.promise),
  });
  s.query.keyword = "old";
  const one = s.load();
  s.query.keyword = "new";
  const two = s.load();
  b.resolve({ data: { records: [{ id: 2 }], total: 1 } });
  await two;
  a.resolve({ data: { records: [{ id: 1 }], total: 1 } });
  await one;
  assert.equal(s.rows.value[0].id, 2);
  assert.equal(s.loading.value, false);
});
test("Switching project clears targets immediately and ignores stale context", async () => {
  const a = defer(),
    b = defer();
  let n = 0;
  const { state: s } = setup({
    context: () => (++n === 1 ? a.promise : b.promise),
  });
  s.form.projectId = 1;
  const one = s.projectChanged();
  assert.equal(s.availableTargets.value.length, 0);
  s.form.projectId = 2;
  const two = s.projectChanged();
  const data = (id) => ({
    data: {
      projects: [{ id }],
      targets: [{ id, changeType: "PROJECT" }],
      channels: [],
      legalReviewers: [],
    },
  });
  b.resolve(data(2));
  await two;
  a.resolve(data(1));
  await one;
  assert.equal(s.projects.value[0].id, 2);
  assert.equal(s.contextLoading.value, false);
  assert.equal(s.form.targetId, undefined);
});
test("Failed list shows retry state and successful retry clears error", async () => {
  let fail = true;
  const { state: s } = setup({
    page: async () => {
      if (fail) throw Error("offline");
      return { data: { records: [{ id: 1 }], total: 1 } };
    },
  });
  await s.load();
  assert.equal(s.error.value, "offline");
  assert.equal(s.rows.value.length, 0);
  fail = false;
  await s.load();
  assert.equal(s.error.value, "");
  assert.equal(s.total.value, 1);
});
test("Draft begins with no inferred project and closing dirty draft asks to discard", () => {
  const { state: s, modals } = setup();
  s.projects.value = [{ id: 1, canCreate: true }];
  s.create();
  assert.equal(s.selectedProject.value, null);
  assert.equal(s.dirty.value, false);
  s.form.title = "unsaved";
  s.close();
  assert.equal(s.open.value, true);
  assert.equal(modals.length, 1);
  modals[0].onOk();
  assert.equal(s.open.value, false);
});
test("Switching type clears incompatible target and legal reviewer", () => {
  const { state: s } = setup();
  s.form.targetKey = "totalFund";
  s.form.targetId = 1;
  s.form.legalReviewerId = 13;
  s.form.afterValue = "200";
  s.typeChanged();
  assert.equal(s.form.targetId, undefined);
  assert.equal(s.form.legalReviewerId, undefined);
  assert.equal(s.form.afterValue, "");
});
test("Unmounted workbench ignores pending requests", async () => {
  const d = defer();
  const { state: s, stop } = setup({ page: () => d.promise });
  const pending = s.load();
  stop();
  d.resolve({ data: { records: [{ id: 9 }], total: 1 } });
  await pending;
  assert.equal(s.rows.value.length, 0);
});
test("Double save is serialized and preserves one request key", async () => {
  const d = defer();
  let calls = 0;
  const { state: s } = setup({
    save: async (body) => {
      calls++;
      assert.ok(body.requestKey);
      return d.promise;
    },
  });
  s.create();
  Object.assign(s.form, {
    projectId: 1,
    title: "title",
    reason: "reason",
    afterValue: "new",
    category: "INDICATOR",
    targetKey: "projectGoal",
    targetId: 1,
  });
  s.selectedToken.value = "projectGoal:1";
  await s.context(); // no target: cannot save an arbitrary field
  await s.save();
  assert.equal(calls, 0);
  // Use a real context payload for the selected target.
  const another = setup({
    context: async () => ({
      data: {
        projects: [{ id: 1 }],
        targets: [
          {
            key: "projectGoal",
            id: 1,
            changeType: "PROJECT",
            beforeValue: "old",
            valueType: "text",
          },
        ],
        channels: [],
        legalReviewers: [],
      },
    }),
    save: async () => {
      calls++;
      return d.promise;
    },
  }).state;
  another.create();
  Object.assign(another.form, s.form);
  another.selectedToken.value = "projectGoal:1";
  await another.context(1);
  const first = another.save();
  await another.save();
  assert.equal(calls, 1);
  d.resolve({ data: { ...another.form, id: 5, revision: 0 } });
  await first;
  assert.equal(another.detail.value.id, 5);
  assert.equal(another.dirty.value, false);
});
test("Latest detail wins when user opens another application quickly", async () => {
  const a = defer(),
    b = defer();
  let n = 0;
  const { state: s } = setup({
    detail: () => (++n === 1 ? a.promise : b.promise),
  });
  const first = s.view({ id: 1 }),
    second = s.view({ id: 2 });
  b.resolve({ data: { id: 2, title: "second" } });
  await second;
  a.resolve({ data: { id: 1, title: "first" } });
  await first;
  assert.equal(s.detail.value.id, 2);
});

function batchSetup() {
  const targets = [
    { key:'projectGoal',id:1,changeType:'PROJECT',category:'INDICATOR',beforeValue:'old',beforeDisplay:'old',valueType:'text',label:'指标',fieldLabel:'指标' },
    { key:'totalFund',id:1,changeType:'PROJECT',category:'FUND',beforeValue:'200',beforeDisplay:'200 万元',valueType:'money',label:'经费',fieldLabel:'总经费' },
  ];
  const saved=[];
  const env=setup({context:async()=>({data:{projects:[{id:1}],targets,channels:[],legalReviewers:[]}}),save:async(body)=>{saved.push(JSON.parse(JSON.stringify(body)));return {data:{...body,id:9,revision:0,items:body.items,canEdit:true}};}});
  return {...env,saved};
}
test('Batch selection retains previous items, edits without duplicating, and route keeps legal',async()=>{
  const {state:s}=batchSetup();s.create();s.form.projectId=1;await s.context(1);
  s.selectedToken.value='totalFund:1';s.targetChanged();s.form.afterValue='300';assert.equal(s.addItem(),true);
  s.selectedToken.value='projectGoal:1';s.targetChanged();s.form.afterValue='new';s.addItem();
  assert.equal(s.selectedItems.value.length,2);assert.equal(s.isMajor.value,true);assert.ok(s.preview.value.includes('法务审核'));
  s.editItem(s.selectedItems.value[1]);s.form.afterValue='new round';s.addItem();
  assert.equal(s.selectedItems.value.length,2);assert.equal(s.selectedItems.value[1].afterValue,'new round');
});
test('Switching objects cannot silently discard an unfinished item',async()=>{
  const {state:s}=batchSetup();s.create();s.form.projectId=1;await s.context(1);
  s.selectedToken.value='projectGoal:1';s.targetChanged();s.form.afterValue='pending';
  s.selectedToken.value='totalFund:1';s.targetChanged();assert.equal(s.selectedToken.value,'projectGoal:1');assert.equal(s.form.afterValue,'pending');assert.match(s.formError.value,/尚未加入清单/);
});
test('Batch save sends all items and response is detached from immutable detail snapshots',async()=>{
  const {state:s,saved}=batchSetup();s.create();Object.assign(s.form,{projectId:1,title:'batch',reason:'reason'});await s.context(1);
  s.selectedToken.value='projectGoal:1';s.targetChanged();s.form.afterValue='new';s.addItem();
  s.selectedToken.value='totalFund:1';s.targetChanged();s.form.afterValue='300';await s.save();
  assert.equal(saved[0].items.length,2);assert.equal(s.dirty.value,false);
  s.form.items[0].afterValue='unsaved';assert.equal(s.detail.value.items[0].afterValue,'new');assert.equal(s.dirty.value,true);
});
test('Removing one selected item preserves others and removing last major clears legal',async()=>{
  const {state:s,modals}=batchSetup();s.create();s.form.projectId=1;await s.context(1);
  for(const [key,value] of [['projectGoal','new'],['totalFund','300']]){s.selectedToken.value=key+':1';s.targetChanged();s.form.afterValue=value;s.addItem();}
  s.form.legalReviewerId=13;s.removeItem(s.selectedItems.value[1]);assert.equal(s.selectedItems.value.length,2);modals.at(-1).onOk();
  assert.equal(s.selectedItems.value.length,1);assert.equal(s.selectedItems.value[0].targetKey,'projectGoal');assert.equal(s.isMajor.value,false);assert.equal(s.form.legalReviewerId,undefined);
});
test('Selected channel item displays the channel label before saving, not its internal ID',async()=>{
  const {state:s}=setup({context:async()=>({data:{projects:[{id:1}],targets:[{key:'levelChannel',id:1,category:'LEVEL',changeType:'DATA',valueType:'channel',beforeValue:'2',beforeDisplay:'旧渠道'}],channels:[{id:8,levelCode:'COMPANY',label:'上海创新'}],legalReviewers:[]}})});
  s.create();Object.assign(s.form,{projectId:1,changeType:'DATA'});await s.context(1);s.selectedToken.value='levelChannel:1';s.targetChanged();s.form.afterValue='8';s.addItem();
  assert.equal(s.selectedItems.value[0].afterValue,'8');assert.equal(s.selectedItems.value[0].afterDisplay,'COMPANY / 上海创新');
});
test('Multi-select accepts different objects sharing one field and preserves existing input',async()=>{
  const targets=[1,2].map(id=>({key:'milestoneDate',id,category:'MILESTONE_DELAY',changeType:'PROJECT',beforeValue:'2027-06-01',beforeDisplay:'2027-06-01',valueType:'date',fieldLabel:'里程碑日期',label:'里程碑'+id}));
  const {state:s}=setup({context:async()=>({data:{projects:[{id:1}],targets,channels:[],legalReviewers:[]}})});
  s.create();s.form.projectId=1;await s.context(1);assert.equal(s.selectObjects(['milestoneDate:1','milestoneDate:2']),true);assert.equal(s.selectedItems.value.length,2);
  s.form.items[0].afterValue='2027-07-01';s.selectObjects(['milestoneDate:1']);assert.equal(s.form.items[0].afterValue,'2027-07-01');
  s.selectObjects(['milestoneDate:1','milestoneDate:2']);assert.equal(s.form.items[0].afterValue,'2027-07-01');assert.equal(s.form.items[1].afterValue,'');
});
test('Every selected object requires content and no request is sent for incomplete selection',async()=>{
  const {state:s,saved}=batchSetup();s.create();Object.assign(s.form,{projectId:1,title:'批量',reason:'依据'});await s.context(1);
  s.selectObjects(['projectGoal:1','totalFund:1']);s.form.items[0].afterValue='新指标';await s.save();assert.equal(saved.length,0);assert.match(s.formError.value,/第 2 项/);
  s.form.items[1].afterValue='280';await s.save();assert.equal(saved.length,1);assert.equal(saved[0].items.length,2);
});
test('Editing loaded multi-object form never overwrites its first item with the legacy scalar value',async()=>{
  const {state:s,saved}=batchSetup();s.create();Object.assign(s.form,{projectId:1,title:'批量',reason:'依据'});await s.context(1);s.selectObjects(['projectGoal:1','totalFund:1']);
  s.form.items[0].afterValue='第一版';s.form.items[1].afterValue='280';await s.save();s.form.items[0].afterValue='第二版';await s.save();
  assert.equal(saved[1].items[0].afterValue,'第二版');assert.equal(saved[1].items[1].afterValue,'280');
});
test('Clearing a multi-selection unlocks type choice but an empty application cannot be saved',async()=>{
  const {state:s,saved}=batchSetup();s.create();Object.assign(s.form,{projectId:1,title:'清空',reason:'依据'});await s.context(1);s.selectObjects(['projectGoal:1','totalFund:1']);s.form.legalReviewerId=13;
  assert.equal(s.selectObjects([]),true);assert.equal(s.selectedItems.value.length,0);assert.equal(s.form.legalReviewerId,undefined);await s.save();assert.equal(saved.length,0);
});
