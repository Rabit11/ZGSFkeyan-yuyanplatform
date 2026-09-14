/** Reproducible checks; --live adds API queries and denied-write checks. QA writes are separate. */
import { spawn } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const output = path.join(root, 'qa/output')
const live = process.argv.includes('--live')
const tasks = [
  ['前端类型检查', 'node', ['frontend/node_modules/vue-tsc/bin/vue-tsc.js', '--noEmit', '-p', 'frontend/tsconfig.json']],
  ['经费页面与项目选择器回归', 'node', ['--test', 'qa/fund-page-regression.mjs', 'qa/project-select-no-flicker.mjs']],
  ['看板层级统计一致性', 'node', ['qa/dashboard-level-regression.mjs']],
  ['备案权限与页面状态', 'node', ['--test', 'qa/filing-page-regression.mjs']],
  ['表单维护与台账回归（内存数据）', 'node', ['qa/form-maint-ledger-smoke.mjs']],
]
if (live) tasks.push(
  ['平台接口与角色巡检', 'node', ['qa/task-flow-smoke.mjs']],
  ['越权写入阻断', 'node', ['qa/write-permission-smoke.mjs']],
  ['我的已办归属', 'node', ['qa/my-done-smoke.mjs']],
  ['财务待办与核销流转', 'node', ['qa/fund-writeoff-sync-smoke.mjs']],
)
await mkdir(output, { recursive: true })
const results = []
for (const [name, command, args] of tasks) {
  console.log('\nRUN: ' + name)
  let log = ''
  const code = await new Promise(resolve => {
    const p = spawn(command, args, { cwd: root, env: process.env, shell: false, windowsHide: true })
    for (const stream of [p.stdout, p.stderr]) stream.on('data', chunk => {
      log += chunk.toString()
      process.stdout.write(chunk)
    })
    p.on('error', error => { log += String(error); resolve(1) })
    p.on('close', code => resolve(code ?? 1))
  })
  const logName = 'platform-check-' + results.length + '.log'
  await writeFile(path.join(output, logName), log, 'utf8')
  results.push({ name, code, logName })
}
const failed = results.filter(r => r.code !== 0)
await writeFile(path.join(output, 'platform-checks-latest.md'), [
  '# 平台回归检查', '', '时间：' + new Date().toISOString(), '',
  '范围：' + (live ? '本地回归 + 线上接口检查' : '本地类型与组件回归'), '',
  '| 检查 | 结果 | 日志 |', '| --- | --- | --- |',
  ...results.map(r => `| ${r.name} | ${r.code === 0 ? '通过' : '失败'} | [日志](${r.logName}) |`), '',
  '接口巡检中的 WARN 代表未覆盖或样本缺失，须查看日志。通过结果不代表平台不存在其他缺陷。', '',
  '本脚本不审批或删除现有业务数据。真实附件与隔离流程写入验证需单独运行。',
].join('\n'), 'utf8')
console.log(`\nSUMMARY: ${results.length - failed.length} passed, ${failed.length} failed`)
process.exitCode = failed.length ? 1 : 0
