import { test } from 'node:test'
import assert from 'node:assert/strict'
import { requirementVerdict, validateCatalog } from './report.mjs'

test('没有测试不算通过', () => assert.equal(requirementVerdict({ testIds: [] }, []).code, 'UNVERIFIED'))
test('映射测试缺少本轮结果为阻塞', () => assert.equal(requirementVerdict({ testIds: ['A'] }, []).code, 'BLOCKED'))
test('局部单元通过不能升级整条需求满足', () => assert.equal(requirementVerdict({ testIds: ['A'] }, [{ id: 'A', status: 'PASS' }]).code, 'PARTIAL'))
test('失败优先于通过与阻塞', () => assert.equal(requirementVerdict({ testIds: ['A', 'B', 'C'] }, [{ id: 'A', status: 'PASS' }, { id: 'B', status: 'FAIL' }]).code, 'GAP'))
test('人工要求不会因无测试标成通过', () => assert.equal(requirementVerdict({ verification: 'manual', testIds: [] }, []).code, 'MANUAL'))
test('清单必须含非重复ID和来源及验收步骤', () => {
  const r = { id: 'A', title: '需求', expected: '预期', sourceRefs: [{ source: 'FLOW', line: 1 }], acceptanceSteps: ['执行步骤'] }
  assert.equal(validateCatalog({ sources: [{ id: 'FLOW' }], requirements: [r] }), true)
  assert.throws(() => validateCatalog({ sources: [{ id: 'FLOW' }], requirements: [r, r] }), /重复/)
})
