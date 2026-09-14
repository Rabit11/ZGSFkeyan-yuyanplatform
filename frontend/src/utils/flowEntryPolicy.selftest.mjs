import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(fileURLToPath(import.meta.url))
const read = (relativePath) => readFileSync(join(root, relativePath), 'utf8')

const helperPath = join(root, 'flowEntryPolicy.ts')
assert.equal(existsSync(helperPath), true, 'flowEntryPolicy.ts should exist')

const helper = read('flowEntryPolicy.ts')
assert.match(helper, /READONLY_FLOW_NOTICE/)
assert.match(helper, /function readonlyFlowActionLabel/)
assert.match(helper, /function readonlyFlowPath/)
assert.match(helper, /去填报/)
assert.match(helper, /去上传/)
assert.match(helper, /去审核/)
assert.match(helper, /return '查看'/)
assert.match(helper, /return undefined/)

const implementFlow = read('implementFlow.ts')
assert.match(implementFlow, /flowEntryPolicy/)
assert.match(implementFlow, /readonlyFlowActionLabel/)
assert.match(implementFlow, /readonlyFlowPath/)

const implementDialog = read('../components/implement/ImplementFlowDialog.vue')
assert.match(implementDialog, /READONLY_FLOW_NOTICE/)
assert.doesNotMatch(implementDialog, /节点“去填报\/上传”入口提交/)

for (const relativePath of [
  '../components/acceptance/AcceptFlowDialog.vue',
  '../components/transform/TransformFlowDialog.vue',
  '../components/filing/FilingFlowDialog.vue',
  '../components/declare/DeclareFlowDialog.vue',
]) {
  const source = read(relativePath)
  assert.match(source, /READONLY_FLOW_NOTICE/, `${relativePath} should show the read-only visual-flow notice`)
}

for (const relativePath of [
  '../components/acceptance/AcceptFlowDialog.vue',
  '../components/transform/TransformFlowDialog.vue',
]) {
  const source = read(relativePath)
  assert.doesNotMatch(source, /router\.push/, `${relativePath} should not navigate from visual flow nodes`)
}

const projectDetail = read('../views/overview/ProjectDetail.vue')
assert.match(projectDetail, /READONLY_FLOW_NOTICE/)
assert.doesNotMatch(projectDetail, /@click="goHandle\(activeNode\)"/)
assert.doesNotMatch(projectDetail, /path: '\/initiation\/filing-materials'/)

const lifecycle = read('lifecycle.ts')
assert.match(lifecycle, /只读查看/)
assert.doesNotMatch(lifecycle, /支持不同步办理/)
