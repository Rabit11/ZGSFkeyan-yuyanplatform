import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

export function requirementVerdict(requirement, cases) {
  const ids = requirement.testIds || []
  const byId = new Map(cases.map(c => [c.id, c]))
  const linked = ids.map(id => byId.get(id)).filter(Boolean)
  const missing = ids.filter(id => !byId.has(id))
  if (linked.some(c => c.status === 'FAIL')) return { code: 'GAP', label: '发现不符', linked, missing }
  if (missing.length || linked.some(c => c.status === 'BLOCKED')) return { code: 'BLOCKED', label: '验证受阻', linked, missing }
  // A component/service test cannot by itself prove the entire requirement, API, persistence and UI.
  if (linked.some(c => c.status === 'PASS')) return { code: 'PARTIAL', label: '部分验证通过', linked, missing }
  if (requirement.verification === 'manual' || requirement.verification === 'process' || linked.some(c => c.status === 'MANUAL')) {
    return { code: 'MANUAL', label: '待人工验收', linked, missing }
  }
  return { code: 'UNVERIFIED', label: '待联调验证', linked, missing }
}

export function validateCatalog(catalog) {
  if (!Array.isArray(catalog.sources) || !Array.isArray(catalog.requirements) || !catalog.requirements.length) throw new Error('需求清单为空或结构无效')
  const ids = new Set()
  for (const r of catalog.requirements) {
    if (!r.id || ids.has(r.id)) throw new Error('需求ID缺失或重复: ' + r.id)
    ids.add(r.id)
    if (!r.title || !r.expected || !r.sourceRefs?.length || !r.acceptanceSteps?.length) throw new Error('需求缺少来源/预期/验收步骤: ' + r.id)
    for (const ref of r.sourceRefs) {
      if (!catalog.sources.some(s => s.id === ref.source) || !Number.isInteger(ref.line) || ref.line < 1) throw new Error('无效需求来源: ' + r.id)
    }
  }
  return true
}

const escape = value => String(value ?? '').replaceAll('|', '\\|').replace(/\r?\n/g, '<br>')
const json = value => JSON.stringify(value, null, 2) + '\n'
const asText = value => typeof value === 'string' ? value : JSON.stringify(value)

export async function writeReports(catalog, suites, metadata, output) {
  validateCatalog(catalog)
  const cases = suites.flatMap(suite => (suite.cases || []).map(c => ({ ...c, layer: c.layer || suite.layer || '见用例证据', suite: suite.suite })))
  const duplicates = cases.filter((c, i) => cases.findIndex(d => d.id === c.id) !== i)
  if (duplicates.length) throw new Error('测试ID重复: ' + duplicates.map(c => c.id).join(','))
  const invalid = cases.filter(c => !['PASS', 'FAIL', 'BLOCKED', 'MANUAL'].includes(c.status))
  if (invalid.length) throw new Error('无效测试状态: ' + invalid.map(c => c.id).join(','))
  const rows = catalog.requirements.map(r => {
    const testIds = [...new Set([...(r.testIds || []), ...cases.filter(c => c.requirementIds?.includes(r.id)).map(c => c.id)])]
    const requirement = { ...r, testIds }
    const verdict = requirementVerdict(requirement, cases)
    const changedSources = requirement.sourceRefs.filter(ref => cases.some(c => c.id === 'SRC-' + ref.source && c.status !== 'PASS'))
    if (changedSources.length && verdict.code !== 'GAP') { verdict.code = 'BLOCKED'; verdict.label = '来源变化/无法核对' }
    return { ...requirement, verdict }
  })
  const counts = Object.fromEntries(['PASS', 'FAIL', 'BLOCKED', 'MANUAL'].map(key => [key, cases.filter(c => c.status === key).length]))
  const businessCases = cases.filter(c => !['source-integrity', 'QA-REPORT', 'typecheck'].includes(c.suite))
  const businessCounts = Object.fromEntries(['PASS', 'FAIL', 'BLOCKED', 'MANUAL'].map(key => [key, businessCases.filter(c => c.status === key).length]))
  const coverage = Object.fromEntries(['GAP', 'PARTIAL', 'BLOCKED', 'MANUAL', 'UNVERIFIED'].map(key => [key, rows.filter(r => r.verdict.code === key).length]))
  const summary = { metadata, counts, businessCounts, coverage, requirementCount: rows.length, cases, requirements: rows }
  await mkdir(output, { recursive: true })
  await writeFile(path.join(output, 'results.json'), json(summary))
  const refText = r => r.sourceRefs.map(ref => `${ref.source} §${ref.section} L${ref.line}`).join('；')
  const lines = [
    '# 三阶段需求满足对照文件', '',
    `运行时间：${metadata.startedAt}；Node ${metadata.nodeVersion}；平台 ${metadata.platform}。`, '',
    '范围：实施阶段、项目验收、成果转化；项目完成归档仅为成果阶段结束边界。立项全流程、后评价独立模块和 UI 改版不在本轮范围。', '',
    '**当前不能判定三阶段全部满足需求。** 本次执行本地隔离测试，未连接正在运行的服务器。组件测试使用真实 Vue 逻辑和模拟 API；后端测试层级以逐例说明为准。未验证数据库持久化、真实浏览器按钮、跨账号在线待办、真实文件存储和外部系统联调的部分，不计为满足。', '',
    `测试结果：${cases.length} 项；PASS ${counts.PASS}，FAIL ${counts.FAIL}，BLOCKED ${counts.BLOCKED}，MANUAL ${counts.MANUAL}。`, '',
    `其中业务检查 ${businessCases.length} 项：PASS ${businessCounts.PASS}，FAIL ${businessCounts.FAIL}，BLOCKED ${businessCounts.BLOCKED}，MANUAL ${businessCounts.MANUAL}；其余为文档摘要校验、类型检查与报告生成器自测。`, '',
    `需求对照：${rows.length} 条；发现不符 ${coverage.GAP}，部分验证通过 ${coverage.PARTIAL}，验证受阻 ${coverage.BLOCKED}，待人工验收 ${coverage.MANUAL}，待联调验证 ${coverage.UNVERIFIED}。`, '',
    '“发现不符”表示当前测试层已复现缺陷，不自动推断为生产数据已损坏或后端可越权。“部分验证通过”仅说明列出的断言通过，仍需执行完整验收步骤。本报告不计算虚假的总体满足率。', '',
    '## 需求来源和规则优先级', '',
    ...catalog.sources.map(s => `- ${s.id}：${s.path}；SHA-256：\`${s.sha256}\``), '',
    '文档视为需求依据；后续用户明确的业务口径优先，差异列在下文。GPT-5.3 Spark 当前不可调用；主代理与可用子代理并行编写，未声称 Spark 执行。', '',
    '## 按阶段对照', '',
  ]
  const stages = [...new Set(rows.map(r => r.stage || r.category || '跨阶段'))]
  for (const stage of stages) {
    lines.push(`### ${stage}`, '', '| 需求ID | 功能/任务 | 原文定位 | 验收预期 | 测试ID | 当前结论 |', '|---|---|---|---|---|---|')
    for (const r of rows.filter(x => (x.stage || x.category || '跨阶段') === stage)) {
      lines.push(`| ${escape(r.id)} | ${escape(r.title)} | ${escape(refText(r))} | ${escape(asText(r.expected))} | ${escape(r.testIds.join(', ') || '尚无自动用例')} | ${r.verdict.label} |`)
    }
    lines.push('')
  }
  lines.push('## 文档与后续确认规则差异', '')
  const overrides = rows.filter(r => r.override)
  if (!overrides.length) lines.push('需求清单未记录本轮适用差异。')
  for (const r of overrides) lines.push(`- **${r.id} ${r.title}**：${asText(r.override)}`)
  lines.push('', '## 每项需求的验收步骤与证据', '')
  for (const r of rows) {
    lines.push(`### ${r.id} ${r.title}`, '', `当前结论：**${r.verdict.label}**；原文：${refText(r)}。`, '')
    for (const c of r.verdict.linked) lines.push(`- ${c.id} ${c.status}（${c.layer}）：${escape(asText(c.evidence))}`)
    for (const id of r.verdict.missing) lines.push(`- 缺失本次执行结果：${id}，不能沿用历史结果。`)
    if (!r.verdict.linked.length) lines.push('- 无自动化执行证据，以下步骤仍须实施。')
    lines.push('', '**完整验收步骤：**', '', ...r.acceptanceSteps.map((step, i) => `${i + 1}. ${asText(step)}`), '')
  }
  lines.push('## 证据文件', '', '- [逐用例测试明细](测试执行明细.md)', '- [失败与阻塞清单](缺陷与待验证清单.md)', '- [机器可读结果](results.json)', '- 各套件原始 JSON 与日志位于本报告同目录。', '')
  await writeFile(path.join(output, '需求满足对照.md'), lines.join('\n'))

  const details = ['# 三阶段测试执行明细', '', `时间：${metadata.startedAt}`, '', '| ID | 阶段 | 测试 | 层级 | 结果 | 证据 |', '|---|---|---|---|---|---|',
    ...cases.map(c => `| ${escape(c.id)} | ${escape(c.stage || '跨阶段')} | ${escape(c.title)} | ${escape(c.layer)} | ${c.status} | ${escape(asText(c.evidence))} |`), '',
    '测试调用的 API 替身仅用于记录输入和模拟响应；没有替代平台业务状态机来证明平台正确。API 层拒绝、持久化和真实消息投递须另外验证。', '']
  await writeFile(path.join(output, '测试执行明细.md'), details.join('\n'))
  const defects = ['# 缺陷与待验证清单', '', 'FAIL 是测试断言不符；BLOCKED 是环境、依赖或测试能力不足。所有条目需结合测试层级判断影响。', '']
  for (const c of cases.filter(c => c.status !== 'PASS')) {
    const linked = rows.filter(r => r.testIds.includes(c.id)).map(r => r.id)
    defects.push(`## ${c.id} ${c.title}`, '', `结果：${c.status}；层级：${c.layer}；需求：${linked.join(', ') || '技术检查/未映射'}。`, '',
      `对象：${c.source || c.suite}。`, '', `期望：${asText(c.expected || '按对应需求验收步骤执行')}。`, '', `实际证据：${asText(c.evidence)}。`, '')
  }
  defects.push('## 尚未完整验收的需求', '', ...rows.filter(r => ['UNVERIFIED', 'MANUAL', 'BLOCKED', 'PARTIAL'].includes(r.verdict.code)).map(r => `- ${r.id} ${r.title}：${r.verdict.label}；参见需求对照中的完整验收步骤。`), '')
  await writeFile(path.join(output, '缺陷与待验证清单.md'), defects.join('\n'))
  return summary
}
