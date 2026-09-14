/** Run only local, isolated checks. No live flag or implicit server credentials. */
import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile, readdir, unlink } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { validateCatalog, writeReports } from './report.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../..')
const startedAt = new Date().toISOString()
const outputIndex = process.argv.indexOf('--output-dir')
if (outputIndex >= 0 && !process.argv[outputIndex + 1]) throw new Error('--output-dir requires a directory')
const output = outputIndex >= 0 ? path.resolve(process.argv[outputIndex + 1]) : path.join(here, 'output', startedAt.replace(/[:.]/g, '-'))
const catalog = JSON.parse(await readFile(path.join(here, 'requirements.json'), 'utf8'))
validateCatalog(catalog)
await mkdir(output, { recursive: true })

async function run(commandArgs, timeoutMs = 120000) {
  return new Promise(resolve => {
    let log = '', errorText = '', timedOut = false
    const child = spawn(process.execPath, commandArgs, { cwd: root, env: { ...process.env, RPM_SKIP_LIVE: '1' }, shell: false, windowsHide: true })
    const timer = setTimeout(() => { timedOut = true; child.kill(); }, timeoutMs)
    child.stdout.on('data', chunk => { log += chunk.toString() })
    child.stderr.on('data', chunk => { log += chunk.toString() })
    child.on('error', error => { errorText = String(error) })
    child.on('close', code => { clearTimeout(timer); resolve({ code, log: log + errorText, timedOut, errorText }) })
  })
}
const suites = []
const integrity = []
for (const source of catalog.sources) {
  try {
    const actual = createHash('sha256').update(await readFile(source.path)).digest('hex')
    integrity.push({ id: 'SRC-' + source.id, title: '原始需求文档版本校验 ' + source.id, status: actual === source.sha256 ? 'PASS' : 'BLOCKED', evidence: actual === source.sha256 ? `SHA-256 ${actual}` : '原始文档已变化，需要重新提取需求，禁止沿用过期验收口径' })
  } catch (error) {
    integrity.push({ id: 'SRC-' + source.id, title: '原始需求文档版本校验 ' + source.id, status: 'BLOCKED', evidence: String(error.message) })
  }
}
suites.push({ suite: 'source-integrity', layer: '需求证据完整性（非业务测试）', cases: integrity })

for (const [suite, script, resultName] of [
  ['functions', 'function-checks.mjs', 'function-results.json'],
  ['flow-view', 'flow-view-checks.mjs', 'flow-view-results.json'],
  ['workflow', 'workflow-checks.mjs', 'workflow-results.json'],
]) {
  console.log('RUN ' + suite)
  const resultFile = path.join(output, resultName)
  await unlink(resultFile).catch(error => { if (error.code !== 'ENOENT') throw error })
  const result = await run(['qa/v19/' + script, '--output', resultFile])
  await writeFile(path.join(output, suite + '.log'), result.log)
  try {
    const data = JSON.parse(await readFile(resultFile, 'utf8'))
    if (!Array.isArray(data.cases) || !data.cases.length) throw new Error('套件无测试结果')
    if (result.timedOut || result.errorText || ![0, 1, 2].includes(result.code)) throw new Error('执行异常或超时: ' + result.code)
    suites.push(data)
    console.log(`${suite}: ${data.cases.filter(c => c.status === 'PASS').length} PASS, ${data.cases.filter(c => c.status === 'FAIL').length} FAIL, ${data.cases.filter(c => c.status === 'BLOCKED').length} BLOCKED`)
  } catch (error) {
    suites.push({ suite, layer: '测试基础设施', cases: [{ id: suite.toUpperCase() + '-RUN', title: script + ' 执行', status: 'BLOCKED', evidence: String(error.message) + '; 见 ' + suite + '.log' }] })
  }
}

for (const [prefix, script, stage] of [
  ['RG-FUND', 'qa/fund-page-regression.mjs', '实施阶段'],
  ['RG-SELECT', 'qa/project-select-no-flicker.mjs', '跨阶段'],
  ['QA-REPORT', 'qa/v19/report.test.mjs', '测试基础设施'],
]) {
  console.log('RUN ' + prefix)
  const result = await run(['--test', '--test-reporter=tap', script])
  await writeFile(path.join(output, prefix + '.log'), result.log)
  const matches = [...result.log.matchAll(/^(ok|not ok) (\d+) - (.+)$/gm)]
  const cases = matches.map((m, index) => ({
    id: prefix + String(index + 1).padStart(2, '0'), title: m[3], stage,
    status: m[1] === 'ok' ? 'PASS' : 'FAIL',
    evidence: `${path.basename(script)}: ${m[0]}; 完整断言及错误见 ${prefix}.log`,
  }))
  if (!cases.length || result.timedOut || result.errorText || (result.code !== 0 && !cases.some(c => c.status === 'FAIL'))) {
    cases.push({ id: prefix + '-RUN', title: script, stage, status: 'BLOCKED', evidence: '无完整 TAP 结果或执行异常，见 ' + prefix + '.log' })
  }
  suites.push({ suite: prefix, layer: prefix === 'QA-REPORT' ? '报告生成器自测（非业务测试）' : '真实Vue组件逻辑；模拟API', cases })
}
console.log('RUN typecheck')
const types = await run(['frontend/node_modules/vue-tsc/bin/vue-tsc.js', '--noEmit', '-p', 'frontend/tsconfig.json'])
await writeFile(path.join(output, 'typecheck.log'), types.log)
suites.push({ suite: 'typecheck', layer: '前端静态类型检查（非运行时功能证明）', cases: [{ id: 'RG-TYPECHECK', title: '前端 TypeScript 类型检查', status: types.timedOut || types.errorText ? 'BLOCKED' : types.code === 0 ? 'PASS' : 'FAIL', evidence: `exit=${types.code}; 见 typecheck.log` }] })

const fingerprints = []
async function hashTree(directory) {
  for (const entry of (await readdir(directory, { withFileTypes: true })).sort((a, b) => a.name.localeCompare(b.name))) {
    if (['node_modules', 'target', 'output'].includes(entry.name)) continue
    const filename = path.join(directory, entry.name)
    if (entry.isDirectory()) await hashTree(filename)
    else fingerprints.push({ path: path.relative(root, filename).replaceAll('\\', '/'), sha256: createHash('sha256').update(await readFile(filename)).digest('hex') })
  }
}
for (const directory of ['frontend/src', 'backend/src', 'qa/v19']) {
  try { await hashTree(path.join(root, directory)) } catch (error) { fingerprints.push({ path: directory, error: error.message }) }
}
await writeFile(path.join(output, 'source-fingerprints.json'), JSON.stringify(fingerprints, null, 2) + '\n')
const metadata = {
  startedAt, finishedAt: new Date().toISOString(), nodeVersion: process.version, platform: process.platform,
  root, output, mode: 'local-isolated', productionWrites: false, requestedModel: 'GPT-5.3 Spark', requestedModelAvailable: false,
  sourceDigest: createHash('sha256').update(JSON.stringify(fingerprints)).digest('hex'),
}
const summary = await writeReports(catalog, suites, metadata, output)
await writeFile(path.join(here, 'output', 'latest.json'), JSON.stringify({ output, startedAt, counts: summary.counts, coverage: summary.coverage }, null, 2) + '\n').catch(async error => {
  if (error.code !== 'ENOENT') throw error
  await mkdir(path.join(here, 'output'), { recursive: true })
  await writeFile(path.join(here, 'output', 'latest.json'), JSON.stringify({ output, startedAt, counts: summary.counts, coverage: summary.coverage }, null, 2) + '\n')
})
console.log(JSON.stringify({ output, counts: summary.counts, coverage: summary.coverage }, null, 2))
// Nonzero for any gap or incomplete acceptance; green cannot mean all requirements met.
process.exitCode = summary.counts.FAIL ? 1 : summary.counts.BLOCKED || summary.requirements.some(r => r.verdict.code !== 'SATISFIED') ? 2 : 0
