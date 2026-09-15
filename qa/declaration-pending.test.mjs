import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const backend = readFileSync(new URL('../backend/src/main/java/com/comac/rpm/modules/declaration/controller/DeclarationController.java', import.meta.url), 'utf8')
const globalGuard = readFileSync(new URL('../backend/src/main/java/com/comac/rpm/common/permission/FlowAuditGuard.java', import.meta.url), 'utf8')
const pending = readFileSync(new URL('../frontend/src/stores/pending.ts', import.meta.url), 'utf8')
const layout = readFileSync(new URL('../frontend/src/layouts/BasicLayout.vue', import.meta.url), 'utf8')
const mock = readFileSync(new URL('../frontend/src/mock/index.ts', import.meta.url), 'utf8')

test('后端提供不受 200 条分页限制的当前用户申报待办接口', () => {
  assert.match(backend, /@GetMapping\("\/pending"\)/)
  assert.match(backend, /\.in\(ProjDeclaration::getStatus, Arrays\.asList\("SUBMITTED", "APPROVING"\)\)/)
  assert.match(backend, /loadPosts\(declaration\)/)
})

test('单位分管领导同时匹配部长和主管岗位', () => {
  assert.match(backend, /if \(t\.contains\("分管"\)\) \{\s*return new String\[\]\{\"unitTechDirector\", \"unitTechSupervisor\"\}/s)
  assert.match(backend, /return new String\[\]\{\"unitHead\", \"unitStaff\"\}/)
  assert.doesNotMatch(globalGuard, /return new String\[\]\{\"unitHead\", \"unitStaff\"\}/)
  assert.match(pending, /declarationApi\.pending\(\)/)
})

test('待办轮询会在组件卸载时清理', () => {
  assert.match(layout, /setInterval\(\(\) => \{\s*void pendingStore\.loadDeclarationReviews/s)
  assert.match(layout, /onUnmounted\(\(\) => \{\s*if \(pendingRefreshTimer\) clearInterval/s)
})

test('Mock 支持按登录用户和岗位返回申报待办', () => {
  assert.match(mock, /m\('GET', '\/declarations\/pending'/)
  assert.match(mock, /isMockDeclarationApprover\(/)
  assert.match(mock, /unitTechSupervisor/)
})

test('经费待办异常不会触发申报待办总清零', () => {
  assert.match(pending, /经费接口异常不能影响申报、维护和里程碑待办/)
  assert.match(pending, /fundReviews = await loadFundReviewRows\(user\)/)
})
