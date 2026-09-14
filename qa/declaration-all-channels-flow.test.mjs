import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from '../frontend/node_modules/esbuild/lib/main.js'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

class MemoryStorage {
  values = new Map()
  getItem(key) { return this.values.get(String(key)) ?? null }
  setItem(key, value) { this.values.set(String(key), String(value)) }
  removeItem(key) { this.values.delete(String(key)) }
  clear() { this.values.clear() }
}
globalThis.localStorage = new MemoryStorage()

const bundle = await build({
  stdin: {
    contents: `
      export { mockRequest } from './frontend/src/mock/index.ts'
      export { currentDeclarationNode } from './frontend/src/utils/declarationWorkflow.ts'
      export { channels, users, declarations, materials } from './frontend/src/mock/data.ts'
    `,
    resolveDir: root,
  },
  alias: { '@': path.join(root, 'frontend/src') },
  bundle: true,
  format: 'esm',
  platform: 'node',
  target: 'node20',
  write: false,
})
const moduleUrl = `data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString('base64')}`
const { mockRequest, currentDeclarationNode, channels, users, declarations, materials } = await import(moduleUrl)
const seedPosts = structuredClone(declarations[0].posts)

function employeeNo(label) {
  return String(label || '').match(/[（(]\s*([^）)]+)\s*[）)]/)?.[1]?.trim() || ''
}

async function request(method, url, data, expectedCode = 0) {
  const result = await mockRequest({ method, url, data })
  assert.equal(result.code, expectedCode, `${method.toUpperCase()} ${url}: ${result.msg}`)
  return result.data
}

async function login(no) {
  const user = users.find((item) => String(item.employeeNo) === String(no))
  assert.ok(user, `Mock 花名册缺少工号 ${no}`)
  const session = await request('post', '/api/auth/login', { username: no, password: no })
  localStorage.setItem('rpm_uid', session.userId)
  localStorage.setItem('rpm_token', session.token)
  return user
}

function unrelatedEmployeeNo(assignedNo) {
  return users.find((user) => user.identityCode !== 'admin' && String(user.employeeNo) !== String(assignedNo))?.employeeNo
}

test('Mock 全部项目渠道逐节点登录对应审批人并检查待办提醒', async (t) => {
  let checkedNodes = 0
  for (const channel of channels) {
    await t.test(`${channel.channelCode} ${channel.channelName}`, async () => {
      const contactNo = employeeNo(seedPosts.contact)
      await login(contactNo)
      const id = await request('post', '/api/declarations', {
        channelId: channel.id,
        name: `自动化-${channel.channelCode}`,
        posts: structuredClone(seedPosts),
        applicant: seedPosts.contact,
      })
      const declaration = declarations.find((item) => item.id === id)
      assert.ok(declaration)

      for (const material of materials.filter((item) => item.bizType === 'DECLARATION' && item.bizId === id && item.required === 1)) {
        material.fileName = `${material.fieldName}-自动测试.pdf`
      }
      await request('post', `/api/declarations/${id}/submit`, {})

      let guard = 0
      while (declaration.status === 'APPROVING' && guard++ < 30) {
        const node = currentDeclarationNode({ ...declaration, posts: declaration.posts })
        assert.ok(node, `${channel.channelCode} 无法识别当前节点 ${declaration.flowNode}`)
        const assignedLabel = node.roleKeys.map((key) => declaration.posts?.[key]).find(Boolean)
        const assignedNo = employeeNo(assignedLabel)
        assert.ok(assignedNo, `${channel.channelCode} 的 ${node.title} 未配置指定审批人`)

        const assignedUser = await login(assignedNo)
        const pending = await request('get', '/api/declarations/pending')
        assert.ok(pending.some((item) => item.id === id), `${channel.channelCode} 的 ${node.title} 未出现在 ${assignedUser.realName} 的待我审核`)

        const unrelatedNo = unrelatedEmployeeNo(assignedNo)
        await login(unrelatedNo)
        const unrelatedPending = await request('get', '/api/declarations/pending')
        assert.equal(unrelatedPending.some((item) => item.id === id), false, `${channel.channelCode} 的 ${node.title} 错发给无关账号 ${unrelatedNo}`)
        await request('post', `/api/declarations/${id}/audit`, { pass: true, opinion: '越权测试' }, 500)
        assert.equal(declaration.flowNode, node.title, '越权审批改变了当前节点')

        await login(assignedNo)
        await request('post', `/api/declarations/${id}/audit`, {
          pass: true,
          opinion: `自动测试：${assignedUser.realName}同意`,
          evidence: node.evidence ? `${channel.channelCode}-${node.code}-佐证.pdf` : undefined,
        })
        checkedNodes += 1
      }

      assert.ok(guard < 30, `${channel.channelCode} 流程超过节点上限`)
      assert.ok(['APPROVED', 'REPORTED'].includes(declaration.status), `${channel.channelCode} 未正常办结`)
    })
  }
  assert.equal(channels.length, 15)
  assert.ok(checkedNodes > 80, `实际只检查了 ${checkedNodes} 个审批节点`)
  t.diagnostic(`覆盖 ${channels.length} 个渠道、${checkedNodes} 个节点，逐节点验证指定账号待办、无关账号不收件及越权审批失败。`)
})
