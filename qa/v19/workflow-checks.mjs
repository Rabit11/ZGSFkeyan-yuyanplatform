#!/usr/bin/env node
/** Offline Java controller integration tests. Never opens a network connection.
 * Actual production controllers and guards are compiled unchanged. Entity and
 * framework classes are reused from the repository's packaged runtime artifact.
 * Persistence is substituted with single-project, in-memory mapper fixtures;
 * SQL filtering, Spring Security/AOP, transactions and real storage are NOT tested.
 * Usage: node workflow-checks.mjs --output path.json [--java-home path] [--m2 path]
 */
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const args = process.argv.slice(2)
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback
const output = path.resolve(option('--output', path.join(here, 'output', 'workflow.json')))
const source = path.resolve(here, '../../backend/src/main/java')
const home = os.homedir()
const jdkCandidates = [option('--java-home', ''), process.env.JAVA_HOME,
  path.join(home, 'Desktop', '#3_0科研项目平台打包历史版本1', '.toolchain', 'jdk-21.0.8+9')].filter(Boolean)
const jdk = jdkCandidates.find(p => fs.existsSync(path.join(p, 'bin', process.platform === 'win32' ? 'javac.exe' : 'javac')))
const bin = name => jdk ? path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name
const walk = p => fs.existsSync(p) ? fs.readdirSync(p, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(p, e.name)) : [path.join(p, e.name)]) : []
const jars = walk(option('--m2', path.join(home, '.m2', 'repository'))).filter(p => p.endsWith('.jar'))
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'rpm-workflow-'))
const classes = path.join(temp, 'classes')
fs.mkdirSync(classes)
const quote = value => '"' + value.replaceAll('\\', '/').replaceAll('"', '\\"') + '"'
const result = { suite: 'workflow', layer: '真实Java控制器+FlowAuditGuard/内存mapper（不含SQL与Spring鉴权）', cases: [] }
try {
  const runtime = path.resolve(here, '../../../V7/project/runtime/backend/rpm-backend.jar')
  if (!fs.existsSync(runtime)) throw new Error('Missing repository packaged runtime: ' + runtime)
  const extraction = spawnSync(bin('jar'), ['xf', runtime], { cwd: temp, encoding: 'utf8', timeout: 60000, windowsHide: true })
  if (extraction.error || extraction.status !== 0) throw new Error('Cannot extract runtime dependencies: ' + (extraction.error?.message || extraction.stderr))
  const cp = [path.join(temp, 'BOOT-INF', 'classes'), ...walk(path.join(temp, 'BOOT-INF', 'lib')).filter(p => p.endsWith('.jar'))].join(path.delimiter)
  const selected = ['common/BusinessException.java', 'common/UserContext.java', 'common/permission/FlowAuditGuard.java',
    'modules/milestone/controller/MilestoneController.java', 'modules/acceptance/controller/AcceptanceController.java',
    'modules/fund/controller/FundController.java', 'modules/transform/controller/TransformController.java']
  const compileArgs = ['-J-Dfile.encoding=UTF-8', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes,
    ...selected.map(p => path.join(source, 'com/comac/rpm', p)), path.join(here, 'workflow-harness.java')]
  fs.writeFileSync(path.join(temp, 'compile.args'), compileArgs.map(quote).join('\n'))
  const compilation = spawnSync(bin('javac'), ['@' + path.join(temp, 'compile.args')], { encoding: 'utf8', timeout: 120000, maxBuffer: 8e6, windowsHide: true })
  if (compilation.error || compilation.status !== 0) throw new Error('Java compilation unavailable/failed: ' + (compilation.error?.message || compilation.stderr || compilation.stdout))
  fs.writeFileSync(path.join(temp, 'run.args'), ['-Dfile.encoding=UTF-8', '-cp', classes + path.delimiter + cp, 'WorkflowHarness', output].map(quote).join('\n'))
  fs.mkdirSync(path.dirname(output), { recursive: true })
  const execution = spawnSync(bin('java'), ['@' + path.join(temp, 'run.args')], { encoding: 'utf8', timeout: 90000, maxBuffer: 8e6, windowsHide: true })
  if (execution.error || execution.status !== 0) throw new Error('Java harness failed: ' + (execution.error?.message || execution.stderr || execution.stdout))
  const actual = JSON.parse(fs.readFileSync(output, 'utf8'))
  result.cases = actual.cases.map(c => ({ ...c, requirementIds: [],
    stage: Number(c.id.slice(3)) <= 16 || c.id === 'WF-035' ? '实施阶段' : Number(c.id.slice(3)) <= 26 || c.id === 'WF-034' ? '项目验收' : '成果转化',
    source: Number(c.id.slice(3)) <= 10 || c.id === 'WF-035' ? 'backend/src/main/java/com/comac/rpm/modules/milestone/controller/MilestoneController.java' : Number(c.id.slice(3)) <= 16 ? 'backend/src/main/java/com/comac/rpm/modules/fund/controller/FundController.java' : Number(c.id.slice(3)) <= 26 || c.id === 'WF-034' ? 'backend/src/main/java/com/comac/rpm/modules/acceptance/controller/AcceptanceController.java' : 'backend/src/main/java/com/comac/rpm/modules/transform/controller/TransformController.java'
  }))
} catch (error) {
  result.cases.push({ id: 'WF-000', title: '真实后端隔离运行环境', status: 'BLOCKED', evidence: String(error.message).slice(0, 12000), requirementIds: [] })
}
result.cases.push({ id: 'WF-090', stage: '跨阶段', source: 'FlowAuditGuard + mapper infrastructure', title: '真实数据库事务、SQL数据隔离及并发重复提交', status: 'BLOCKED', evidence: 'Mapper使用单项目内存夹具；selectList/selectOne直接返回夹具，不执行SQL过滤。身份测试仅验证控制器/guard对指定人员的判断，不能证明数据库行级范围、Spring鉴权、事务/并发语义。', requirementIds: [] })
result.cases.push({ id: 'WF-091', stage: '实施阶段', source: 'FundController + dashboard/storage integration', title: '核销后总部看板同步与真实附件持久化', status: 'BLOCKED', evidence: '本套验证控制器核销状态和待办生成；总部聚合查询、真实对象存储与完整前后端E2E需要隔离部署环境。', requirementIds: [] })
fs.mkdirSync(path.dirname(output), { recursive: true })
fs.writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
// Only remove the uniquely-created temporary workspace, never project files.
if (path.dirname(temp) === os.tmpdir() && path.basename(temp).startsWith('rpm-workflow-')) fs.rmSync(temp, { recursive: true, force: true })
const counts = result.cases.reduce((a, c) => ({ ...a, [c.status]: (a[c.status] || 0) + 1 }), {})
console.log(JSON.stringify({ suite: result.suite, output, counts }))
process.exitCode = counts.FAIL ? 1 : counts.BLOCKED && result.cases.some(c => c.id === 'WF-000') ? 2 : 0
