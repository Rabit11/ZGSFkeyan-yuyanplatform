import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from '../frontend/node_modules/esbuild/lib/main.js'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const bundle = await build({
  stdin: { contents: `export * from './frontend/src/utils/declarationWorkflow.ts'`, resolveDir: root },
  alias: { '@': path.join(root, 'frontend/src') }, bundle: true, format: 'esm', platform: 'node', target: 'node20', write: false,
})
const mod = await import(`data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString('base64')}`)
const titles = (code, name) => mod.declarationNodes({ channelCode: code, channelName: name }).map((n) => n.title)

test('渠道模板符合需求文档差异化审签链', () => {
  const common = ['MJKY', '04ZXJX', 'ZDYFJH', 'ZRJJ', 'SHJBGS', 'SHKJCX', 'YYGD', 'XJQX']
  for (const code of common) {
    assert.deepEqual(titles(code), [
      '项目联系人', '项目负责人', '项目承担部门负责人', '二级总师', '单位财务部门负责人',
      '单位科技部门负责人', '单位分管领导', '一级总师', '总部科技部科研项目处',
    ], `${code} 通用审签链不一致`)
  }
  const xx25 = titles('XX25', 'XX25专项').join('→')
  assert.equal(xx25.includes('单位财务部门负责人'), false)
  assert.match(xx25, /项目联系人→项目负责人→项目承担部门负责人→二级总师→单位科技部门负责人→单位分管领导→一级总师→总部科技部科研项目处/)

  const week = titles('KJZ', '科技周')
  assert.deepEqual(week.slice(0, 4), ['项目负责人', '三级专业总师', '二级专业总师', '单位科技部'])
  assert.equal(week.at(-1), '发布拟立项项目清单')

  const institute = titles('DFJYJY', '大飞机研究院')
  assert.equal(institute.filter((x) => x.includes('学术委员会')).length, 2)
  assert.equal(institute.at(-1), '理事会审议')

  assert.deepEqual(titles('CLM', '大飞机先进材料创新联盟'), ['联盟成员单位提交申请书', '联盟专委会审查意见/纪要', '联盟理事会审查意见/纪要', '公司科技部报批'])
  assert.deepEqual(titles('BOKH', '“中国商飞-波音”可持续航空技术研究中心项目'), ['项目负责人', '北研中心科技部主管', '北研中心科技部部长', '北研中心分管领导', '总部科技部主管'])
})
