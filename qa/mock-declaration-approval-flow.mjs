import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { test } from 'node:test'
import { build } from '../frontend/node_modules/esbuild/lib/main.js'

const qaDir = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(qaDir, '..')

class MemoryStorage {
  #values = new Map()

  getItem(key) {
    return this.#values.has(String(key)) ? this.#values.get(String(key)) : null
  }

  setItem(key, value) {
    this.#values.set(String(key), String(value))
  }

  removeItem(key) {
    this.#values.delete(String(key))
  }

  clear() {
    this.#values.clear()
  }
}

globalThis.localStorage = new MemoryStorage()

const bundle = await build({
  stdin: {
    contents: `
      export { mockRequest } from './frontend/src/mock/index.ts'
      export { channels, declarations, materials, users } from './frontend/src/mock/data.ts'
    `,
    resolveDir: rootDir,
    sourcefile: 'mock-declaration-flow-entry.ts',
  },
  alias: { '@': path.join(rootDir, 'frontend/src') },
  bundle: true,
  format: 'esm',
  platform: 'node',
  target: 'node20',
  write: false,
})

const bundledSource = bundle.outputFiles[0].text
const moduleUrl = `data:text/javascript;base64,${Buffer.from(bundledSource).toString('base64')}`
const { channels, declarations, materials, mockRequest, users } = await import(moduleUrl)

const APPROVAL_STEPS = [
  ['项目负责人', 'leader'],
  ['项目承担部门负责人', 'deptHead'],
  ['二级总师', 'chief2'],
  ['单位财务部门负责人', 'unitFinanceDirector'],
  ['单位科技部门负责人', 'unitTechDirector'],
  ['单位分管领导', 'unitTechDirector'],
  ['一级总师', 'chief1'],
  ['总部科研项目处', 'hqDirector'],
]

function clone(value) {
  return structuredClone(value)
}

function restoreObject(target, snapshot) {
  for (const key of Object.keys(target)) delete target[key]
  Object.assign(target, clone(snapshot))
}

function employeeNoFromLabel(label) {
  const match = String(label || '').match(/[（(]\s*([^）)]+)\s*[）)]/)
  return match?.[1]?.trim() || ''
}

async function request(method, url, data) {
  const response = await mockRequest({ method, url, data })
  assert.equal(response.code, 0, `${method.toUpperCase()} ${url} 失败：${response.msg}`)
  return response.data
}

async function login(employeeNo) {
  const user = users.find((item) => String(item.employeeNo) === String(employeeNo))
  assert.ok(user, `Mock 花名册缺少工号 ${employeeNo}`)
  const session = await request('post', '/api/auth/login', {
    username: employeeNo,
    password: employeeNo,
  })
  localStorage.setItem('rpm_uid', String(session.userId))
  localStorage.setItem('rpm_token', String(session.token))
  return user
}

function findUnrelatedEmployee(declaration, assignedEmployee) {
  const assigned = new Set(
    Object.values(declaration.posts || {})
      .map(employeeNoFromLabel)
      .filter(Boolean),
  )
  return users.find(
    (user) =>
      user.identityCode !== 'admin' &&
      String(user.employeeNo) !== String(assignedEmployee) &&
      !assigned.has(String(user.employeeNo)),
  )?.employeeNo
}

function prepareRequiredMaterials(declarationId) {
  for (const item of materials) {
    if (
      item.bizType === 'DECLARATION' &&
      Number(item.bizId) === Number(declarationId) &&
      item.required === 1 &&
      item.locked !== 1
    ) {
      item.fileName ||= `${item.fieldName}-自动测试.pdf`
      item.fileUrl ||= `/mock-test/${declarationId}/${encodeURIComponent(item.fieldName)}.pdf`
    }
  }
}

test('Mock 全部项目申报逐账号待我审核提醒与完整审签流转', async (t) => {
  assert.ok(declarations.length > 0, 'Mock 中没有项目申报数据')

  const declarationSnapshots = new Map(declarations.map((item) => [item.id, clone(item)]))
  const materialSnapshots = new Map(materials.map((item) => [item.id, clone(item)]))
  const coveredChannels = new Set()
  let checkedNodes = 0

  try {
    for (const declaration of declarations) {
      await t.test(`${declaration.applyNo} ${declaration.name}`, async () => {
        const channel = channels.find((item) => Number(item.id) === Number(declaration.channelId))
        coveredChannels.add(declaration.channelName || channel?.channelName || String(declaration.channelId))

        declaration.status = 'DRAFT'
        declaration.flowNode = undefined
        declaration.needApproval = 1
        delete declaration.opinion
        prepareRequiredMaterials(declaration.id)

        const contactEmployee = employeeNoFromLabel(declaration.posts?.contact)
        await login(contactEmployee)
        await request('post', `/api/declarations/${declaration.id}/submit`, {})
        assert.equal(declaration.status, 'APPROVING')
        assert.equal(declaration.flowNode, APPROVAL_STEPS[0][0])

        for (let index = 0; index < APPROVAL_STEPS.length; index += 1) {
          const [node, postKey] = APPROVAL_STEPS[index]
          assert.equal(declaration.flowNode, node, `${declaration.applyNo} 未流转到预期节点`)

          const assignedLabel = declaration.posts?.[postKey]
          const assignedEmployee = employeeNoFromLabel(assignedLabel)
          assert.ok(assignedEmployee, `${declaration.applyNo} 的 ${node} 未配置审批人`)

          const assignedUser = await login(assignedEmployee)
          const pending = await request('get', '/api/declarations/pending')
          assert.ok(
            pending.some((item) => Number(item.id) === Number(declaration.id)),
            `${declaration.applyNo} 流转至 ${node} 后，${assignedLabel} 的待我审核未收到提醒`,
          )

          const unrelatedEmployee = findUnrelatedEmployee(declaration, assignedEmployee)
          if (unrelatedEmployee) {
            await login(unrelatedEmployee)
            const unrelatedPending = await request('get', '/api/declarations/pending')
            assert.equal(
              unrelatedPending.some((item) => Number(item.id) === Number(declaration.id)),
              false,
              `${declaration.applyNo} 在 ${node} 被无关账号 ${unrelatedEmployee} 收到`,
            )
          }

          await login(assignedEmployee)
          await request('post', `/api/declarations/${declaration.id}/audit`, {
            pass: true,
            opinion: `自动测试通过：${assignedUser.realName}（${assignedEmployee}）`,
          })
          checkedNodes += 1

          const nextNode = APPROVAL_STEPS[index + 1]?.[0]
          if (nextNode) {
            assert.equal(declaration.status, 'APPROVING')
            assert.equal(declaration.flowNode, nextNode)
          } else {
            assert.equal(declaration.status, 'APPROVED')
            assert.equal(declaration.flowNode, '归档')
          }

          t.diagnostic(
            `${declaration.applyNo} | ${node} | ${assignedUser.realName}（${assignedEmployee}） | 待办命中 | ` +
              `下一节点：${nextNode || '归档'}`,
          )
        }
      })
    }

    t.diagnostic(
      `覆盖 ${declarations.length} 条申报、${coveredChannels.size} 个渠道、${checkedNodes} 个审批节点账号检查。`,
    )
  } finally {
    for (const declaration of declarations) {
      restoreObject(declaration, declarationSnapshots.get(declaration.id))
    }
    for (const material of materials) {
      restoreObject(material, materialSnapshots.get(material.id))
    }
    localStorage.clear()
  }
})

// Keep the script directly runnable from any current directory.
void pathToFileURL(path.join(rootDir, 'qa/mock-declaration-approval-flow.mjs'))
