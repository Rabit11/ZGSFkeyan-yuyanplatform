import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

export const root = fileURLToPath(new URL('../frontend/', import.meta.url))
const require = createRequire(path.join(root, 'package.json'))
export const vue = require('vue')
const { parse, compileScript } = require('@vue/compiler-sfc')
const ts = require('typescript')
const renderer = vue.createRenderer({
  createElement: () => ({}), createText: text => ({ text }), createComment: () => ({}),
  insert: () => {}, remove: () => {}, setText: () => {}, setElementText: () => {},
  parentNode: () => null, nextSibling: () => null, patchProp: () => {},
})
export function mountSource(file, dependencies, props = {}) {
  const filename = path.join(root, 'src', file)
  const descriptor = parse(fs.readFileSync(filename, 'utf8'), { filename }).descriptor
  const code = compileScript(descriptor, { id: 'qa-component' }).content
  const output = ts.transpileModule(code, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  const module = { exports: {} }
  const localRequire = name => {
    if (name === 'vue') return vue
    if (Object.hasOwn(dependencies, name)) return dependencies[name]
    if (name.endsWith('.vue')) return { default: {} }
    throw new Error('Missing test dependency: ' + name)
  }
  vm.runInNewContext(output, { module, exports: module.exports, require: localRequire, console, setTimeout, clearTimeout, URLSearchParams, FormData }, { filename })
  const component = module.exports.default
  component.render = () => null
  const app = renderer.createApp(component, props)
  const instance = app.mount({})
  return { state: instance.$.setupState, unmount: () => app.unmount() }
}
export const flush = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await vue.nextTick() } }
export function deferred() {
  let resolve
  const promise = new Promise(r => { resolve = r })
  return { promise, resolve }
}
