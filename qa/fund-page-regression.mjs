import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mountSource, vue, flush, deferred } from './vue-harness.mjs'

function fixture() {
  const route = vue.reactive({ query: { projectId: '160' } })
  const writes = []
  const budget = id => ({ data: [{ id: id + 1, milestoneId: id * 10, amount: id, status: 'APPROVED' }] })
  const api = {
    fundApi: { budgets: async id => budget(id), payments: async () => ({ data: [] }), createPayment: async row => { writes.push(row); return { data: 1 } } },
    milestoneApi: { list: async id => ({ data: [{ id: id * 10, name: '节点' + id, status: 'DONE' }] }) },
    projectApi: { detail: async id => ({ data: { id, name: '项目' + id } }) }, fileApi: {},
  }
  const deps = {
    '@/api/modules': api,
    'vue-router': { useRoute: () => route, useRouter: () => ({ replace: async () => {} }) },
    'ant-design-vue': { message: { warning: () => {}, success: () => {}, error: () => {} } },
    '@ant-design/icons-vue': {}, '@/api/request': { isSilentAuthError: () => false },
    '@/composables/useWorkDuty': { useWorkDuty: () => ({ can: vue.ref({ fill: true, submit: true, audit: true }), guard: () => true }) },
    '@/stores/user': { useUserStore: () => ({ identityCode: 'finHead', realName: '测试财务' }) },
    '@/stores/pending': { usePendingStore: () => ({ fundReviews: [], loadDeclarationReviews: async () => {} }) },
    '@/utils/format': { fmtAmount: String, fmtDate: String },
  }
  return { ...mountSource('views/implement/Fund.vue', deps), api, writes, route, budget }
}

test('快速切换项目后，晚返回的旧请求不得覆盖新项目', async () => {
  const f = fixture()
  try {
    await flush()
    const old = deferred()
    f.api.fundApi.budgets = id => id === 160 ? old.promise : Promise.resolve(f.budget(id))
    const first = f.state.loadExec()
    f.state.projectId = 161
    await f.state.loadExec()
    old.resolve(f.budget(160))
    await first
    assert.equal(f.state.project.id, 161)
    assert.equal(f.state.budgets[0].amount, 161)
  } finally { f.unmount() }
})

test('预算填报切换项目时同步节点表格', async () => {
  const f = fixture()
  try {
    await flush()
    f.state.openBudget()
    f.state.projectId = 161
    await f.state.loadExec()
    assert.equal(f.state.bLines[0].milestoneId, 1610)
  } finally { f.unmount() }
})

test('批量核销后行缺材料时，不得先保存前行', async () => {
  const f = fixture()
  try {
    await flush()
    f.state.openWriteoff()
    f.state.wItems = [
      { occurDate: '2026-09-13', amount: 1, name: '第一行', fileName: 'a.txt', fileUrl: '/a' },
      { occurDate: '2026-09-13', amount: 1, name: '第二行', fileName: '', fileUrl: '' },
    ]
    await f.state.saveWriteoff(false)
    assert.equal(f.writes.length, 0)
  } finally { f.unmount() }
})

test('核销负金额不能提交', async () => {
  const f = fixture()
  try {
    await flush()
    f.state.openWriteoff()
    f.state.wItems = [{ occurDate: '2026-09-13', amount: -1, name: '错误金额', fileName: 'a', fileUrl: '/a' }]
    await f.state.saveWriteoff(false)
    assert.equal(f.writes.length, 0)
  } finally { f.unmount() }
})

test('连续点击核销提交只能产生一次请求', async () => {
  const f = fixture()
  try {
    await flush()
    f.state.openWriteoff()
    f.state.wItems = [{ occurDate: '2026-09-13', amount: 1, name: '凭证', fileName: 'a', fileUrl: '/a' }]
    const held = deferred()
    f.api.fundApi.createPayment = async row => { f.writes.push(row); return held.promise }
    const first = f.state.saveWriteoff(false)
    const second = f.state.saveWriteoff(false)
    held.resolve({ data: 1 })
    await Promise.all([first, second])
    assert.equal(f.writes.length, 1)
  } finally { f.unmount() }
})

test('批量保存中途失败后，重试不重复发送已确认成功的行', async () => {
  const f = fixture()
  try {
    await flush()
    f.state.openWriteoff()
    f.state.wItems = ['第一行', '第二行'].map(name => ({ occurDate: '2026-09-13', amount: 1, name, fileName: 'a', fileUrl: '/a' }))
    let fail = true
    f.api.fundApi.createPayment = async row => {
      if (row.remark.startsWith('第二行') && fail) throw new Error('service rejected')
      f.writes.push(row)
      return { data: f.writes.length }
    }
    await f.state.saveWriteoff(false)
    assert.equal(f.state.wItems.length, 1)
    assert.equal(f.state.wItems[0].name, '第二行')
    fail = false
    await f.state.saveWriteoff(false)
    assert.equal(f.writes.length, 2)
    assert.equal(f.writes.filter(r => r.remark.startsWith('第一行')).length, 1)
  } finally { f.unmount() }
})

test('加载新项目失败时不展示上一项目的可提交数据', async () => {
  const f = fixture()
  try {
    await flush()
    f.state.projectId = 161
    f.api.projectApi.detail = async () => { throw new Error('unavailable') }
    await f.state.loadExec()
    assert.equal(f.state.project, null)
    assert.equal(f.state.milestones.length, 0)
    assert.equal(f.state.budgets.length, 0)
    assert.equal(f.state.loading, false)
  } finally { f.unmount() }
})

test('仍有草稿或未核销节点时，流程图不得标记核销全部完成', async () => {
  const f = fixture()
  try {
    await flush()
    f.state.payments = [
      { id: 1, budgetId: 1600, amount: 1, writeoffStatus: 'WRITTEN' },
      { id: 2, budgetId: 1600, amount: 0, writeoffStatus: 'DRAFT' },
    ]
    assert.equal(f.state.nodeWrittenAll, false)
    assert.equal(f.state.financeExecFlow.find(s => s.no === '5').state, 'current')
    f.state.payments = [f.state.payments[0]]
    assert.equal(f.state.financeExecFlow.find(s => s.no === '5').state, 'done')
    f.state.milestones = [...f.state.milestones, { id: 1601, name: '未核销节点', status: 'DONE' }]
    assert.equal(f.state.financeExecFlow.find(s => s.no === '5').state, 'current')
  } finally { f.unmount() }
})
