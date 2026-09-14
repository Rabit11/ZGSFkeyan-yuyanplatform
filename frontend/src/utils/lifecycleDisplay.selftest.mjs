import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(fileURLToPath(import.meta.url))
const read = (relativePath) => readFileSync(join(root, relativePath), 'utf8')

const lifecycle = read('lifecycle.ts')
assert.doesNotMatch(lifecycle, /nodeCode:\s*'ARCHIVE'/, 'platform lifecycle should not include archive node')
assert.doesNotMatch(lifecycle, /项目完成归档/, 'platform lifecycle should not display project completion archive')
assert.match(lifecycle, /固定 5 节点/, 'lifecycle source should document the five visible business nodes')

const detail = read('../views/overview/ProjectDetail.vue')
assert.match(detail, /visibleLifecycle/, 'detail page should normalize visible lifecycle nodes')
assert.match(detail, /node\.nodeCode !== 'ARCHIVE'/, 'detail page should filter legacy ARCHIVE nodes from API data')
assert.doesNotMatch(detail, /项目完成归档/, 'detail page should not render project completion archive copy')
