#!/usr/bin/env node
/**
 * 6006 写接口越权回归测试。
 *
 * 使用“无项目可见范围”的普通账号 100007，只访问不存在的业务 ID，
 * 验证请求在进入业务处理前被统一拒绝，不改动真实业务数据。
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

if (process.env.RPM_INSECURE_TLS === '1') process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'

const BASE = String(
  process.env.RPM_BASE_URL || 'https://u476948-r3ys-6923790a.westd.seetacloud.com:8443',
).replace(/\/$/, '')
const USERNAME = process.env.RPM_PERMISSION_USER || '100007'
const PASSWORD = process.env.RPM_PERMISSION_PASSWORD || USERNAME
const MISSING_ID = 999999999
const root = path.dirname(fileURLToPath(import.meta.url))
const outputDir = path.join(root, 'output')
fs.mkdirSync(outputDir, { recursive: true })
const stamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').replace('Z', '')
const logFile = path.join(outputDir, `write-permission-test-${stamp}.log`)
const latestLog = path.join(outputDir, 'write-permission-test-latest.log')
const lines = []
let passed = 0
let failed = 0

function log(level, node, detail) {
  const line = `${new Date().toISOString()} [${level}] [${node}] ${detail}`
  lines.push(line)
  console.log(line)
}

async function request(method, url, token, body) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(`${BASE}${url}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  let json = null
  try { json = text ? JSON.parse(text) : null } catch { /* reported below */ }
  return { status: response.status, json, text }
}

function forbidden(result) {
  return result.status === 403 || Number(result.json?.code) === 403
}

async function main() {
  log('INFO', 'RUN', `base=${BASE}; user=${USERNAME}`)
  const login = await request('POST', '/api/auth/login', null, { username: USERNAME, password: PASSWORD })
  const token = login.json?.data?.token || login.json?.data?.accessToken
  if (!token) throw new Error(`登录失败：HTTP ${login.status}; ${login.json?.msg || login.text}`)

  const projectList = await request('GET', '/api/projects?page=1&size=3', token)
  const projects = projectList.json?.data?.records || projectList.json?.data?.list || projectList.json?.data || []
  if (!Array.isArray(projects) || projects.length !== 0) {
    throw new Error(`测试账号不再满足“无项目范围”前置条件，实际项目数=${Array.isArray(projects) ? projects.length : '未知'}`)
  }
  log('PASS', '前置条件', '普通账号项目列表为 0')

  const cases = [
    ['删除里程碑', 'DELETE', `/api/milestones/${MISSING_ID}`],
    ['删除计划', 'DELETE', `/api/plans/${MISSING_ID}`],
    ['计划办结申请', 'POST', `/api/plans/${MISSING_ID}/finish-apply`],
    ['删除交付物', 'DELETE', `/api/deliverables/${MISSING_ID}`],
    ['删除评估记录', 'DELETE', `/api/evaluations/${MISSING_ID}`],
    ['删除协作单位评价', 'DELETE', `/api/partner-evals/${MISSING_ID}`],
    ['提交协作单位评价', 'POST', `/api/partner-evals/${MISSING_ID}/submit`],
    ['删除成果转化', 'DELETE', `/api/transforms/${MISSING_ID}`],
    ['删除后评价', 'DELETE', `/api/post-evals/${MISSING_ID}`],
    ['提交后评价', 'POST', `/api/post-evals/${MISSING_ID}/submit`],
    ['标记无权预警已读', 'POST', `/api/warnings/${MISSING_ID}/read`],
    ['删除无权文件', 'DELETE', `/api/files?objectKey=permission-test/nonexistent-${MISSING_ID}`],
  ]

  for (const [node, method, url, body] of cases) {
    const result = await request(method, url, token, body)
    if (forbidden(result)) {
      passed++
      log('PASS', node, `被拒绝；HTTP=${result.status}; code=${result.json?.code}`)
    } else {
      failed++
      log('FAIL', node, `未在权限边界拒绝；HTTP=${result.status}; code=${result.json?.code}; msg=${result.json?.msg || result.text}`)
    }
  }
}

try {
  await main()
} catch (error) {
  failed++
  log('FAIL', 'RUN', String(error?.stack || error))
}

log('SUMMARY', 'RUN', `PASS=${passed}; FAIL=${failed}`)
fs.writeFileSync(logFile, `${lines.join('\n')}\n`, 'utf8')
fs.copyFileSync(logFile, latestLog)
console.log(`LOG_FILE=${logFile}`)
process.exitCode = failed ? 1 : 0
