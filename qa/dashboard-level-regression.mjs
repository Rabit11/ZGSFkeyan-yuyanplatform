import assert from 'node:assert/strict'
import { test } from 'node:test'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
const root = path.resolve(import.meta.dirname, '../frontend')
const require = createRequire(path.join(root, 'package.json'))
const ts = require('typescript')
const cache = new Map()
function loadTs(file) {
  if (cache.has(file)) return cache.get(file)
  const module = { exports: {} }
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true },
  }).outputText
  vm.runInNewContext(code, { module, exports: module.exports, require: name => name.startsWith('@/')
    ? loadTs(path.join(root, 'src', name.slice(2) + '.ts')) : require(name) }, { filename: file })
  cache.set(file, module.exports)
  return module.exports
}
const { aggregateDashboard } = loadTs(path.join(root, 'src/utils/dashboardAgg.ts'))
const sample = levels => levels.map((levelCode, i) => ({ id: i + 1, name: '项目' + i, levelCode,
  orgName: i % 2 ? '单位甲' : '单位乙', status: 'IMPLEMENTING', totalFund: 10,
  startDate: '2026-01-01', endDate: '2026-12-31' }))
function checkConsistency(projects, query = {}) {
  const d = aggregateDashboard(query, { projects })
  assert.equal(d.byLevel.reduce((sum, row) => sum + row.count, 0), d.kpis.projectCount, '层级合计必须等于在库项目数')
  assert.equal(d.unitLevelMatrix.series.reduce((sum, s) => sum + s.data.reduce((a, n) => a + n, 0), 0), d.kpis.projectCount, '单位×层级矩阵不能漏项目')
  assert.equal(d.byLevel.reduce((sum, row) => sum + row.fund, 0), d.kpis.totalFund, '层级经费不能遗漏')
  return d
}
test('15项中1项缺层级时，饼图与单位矩阵均统计15项', () => {
  const d = checkConsistency(sample([...Array(8).fill('NATIONAL'), ...Array(3).fill('LOCAL'), ...Array(3).fill('COMPANY'), null]))
  assert.equal(d.kpis.projectCount, 15)
  assert.equal(d.byLevel.find(x => x.level === 'UNSPECIFIED')?.count, 1)
})
test('兼容中文层级并明确统计空白及未知层级', () => {
  const rows = sample(['国家级', ' LOCAL ', '公司级', '', 'CUSTOM'])
  const d = checkConsistency(rows)
  assert.equal(d.byLevel.find(x => x.level === 'UNSPECIFIED')?.count, 2)
  assert.equal(checkConsistency(rows, { level: 'NATIONAL' }).kpis.projectCount, 1)
  assert.equal(checkConsistency(rows, { unit: '单位甲' }).kpis.projectCount, 2)
})
test('标准层级和空筛选结果不会产生多余分类', () => {
  assert.equal(checkConsistency(sample(['NATIONAL', 'LOCAL', 'COMPANY'])).byLevel.length, 3)
  assert.equal(checkConsistency([], { year: 2026 }).kpis.projectCount, 0)
})

if (process.argv.includes('--live')) test('线上项目台账统计一致性', async () => {
  if (process.env.RPM_INSECURE_TLS === '1') process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'
  const base = process.env.RPM_BASE_URL || 'https://u476948-r3ys-6923790a.westd.seetacloud.com:8443'
  const username = process.env.RPM_ADMIN_USER || '100001'
  const login = await fetch(base + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: process.env.RPM_TEST_PASSWORD || username }), signal: AbortSignal.timeout(30000) }).then(r => r.json())
  assert.equal(login.code, 0)
  const res = await fetch(base + '/api/projects?page=1&size=500', { headers: { Authorization: 'Bearer ' + login.data.token }, signal: AbortSignal.timeout(30000) }).then(r => r.json())
  assert.equal(res.code, 0)
  const projects = res.data.records
  assert.equal(projects.length, Number(res.data.total), '本次检查须包含完整项目列表')
  const invalid = projects.filter(p => !['NATIONAL', 'LOCAL', 'COMPANY', '国家级', '地方级', '公司级'].includes(String(p.levelCode || '').trim()))
  console.log('LEVEL_DIAGNOSIS ' + JSON.stringify({ total: projects.length, unclassified: invalid.map(p => ({ id: p.id, projectNo: p.projectNo, name: p.name, levelCode: p.levelCode })) }))
  const d = checkConsistency(projects, { year: 2026 })
  console.log('LEVEL_VERIFIED ' + JSON.stringify({ total: d.kpis.projectCount, byLevel: d.byLevel }))
})
