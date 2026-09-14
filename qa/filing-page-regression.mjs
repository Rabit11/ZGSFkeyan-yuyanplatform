import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mountSource, vue, flush } from './vue-harness.mjs'

function fixture(status, filingStatus, audit = false) {
  const writes = []
  const data = {
    declaration: { id: 1, status, posts: {} }, materials: [],
    filing: { status: filingStatus },
    project: { id: 2, status: filingStatus === 'ARCHIVED' ? 'IMPLEMENTING' : 'FILING' },
  }
  const deps = {
    'vue-router': { useRoute: () => ({ query: { declarationId: '1' }, params: {} }), useRouter: () => ({ push() {} }) },
    'ant-design-vue': { message: { warning() {}, success() {}, error() {} }, Modal: { confirm: options => { writes.push(options) } } },
    '@/api/modules': { declarationApi: { detail: async () => ({ data }), submitFiling: async () => { writes.push('submit'); return { data: { projectId: 2 } } } }, projectApi: {}, dictApi: {}, fileApi: {} },
    '@/api/types': { PROJECT_STATUS_TEXT: {} },
    '@/stores/dict': { useDictStore: () => ({ loadChannels: async () => {}, channels: [] }) },
    '@/composables/useWorkDuty': { useWorkDuty: () => ({ can: vue.ref({ fill: !audit, submit: !audit, audit }), guard: () => true }) },
    '@/utils/filingFlow': { buildFilingFlowOpts: () => ({ attachments: [], channelMaterials: [], nodes: [], panelStatus: 'HANDLING' }) },
    '@/utils/flowLive': {
      mergePosts: (...rows) => Object.assign({}, ...rows), postsFromTeamMembers: () => ({}),
      personLabelOf: () => '', channelRequiredMaterials: () => [],
      unwrapDeclarationDetail: d => d,
    },
  }
  return { ...mountSource('views/initiation/FilingMaterials.vue', deps), writes }
}
test('审批未结束，即使直接打开材料地址也不能提交备案', async () => {
  const f = fixture('APPROVING', '')
  try { await flush(); assert.equal(f.state.canUpload, false); await f.state.submitAudit(); assert.equal(f.writes.length, 0) }
  finally { f.unmount() }
})
test('负责人提交备案后不能自审或重复上传', async () => {
  const f = fixture('APPROVED', 'AUDIT')
  try { await flush(); assert.equal(f.state.canUpload, false); assert.equal(f.state.canReview, false); f.state.reviewFiling(true); assert.equal(f.writes.length, 0) }
  finally { f.unmount() }
})
test('指定总部办理人有备案审核入口，已归档后关闭入口', async () => {
  const f = fixture('APPROVED', 'AUDIT', true)
  try { await flush(); assert.equal(f.state.canReview, true); f.state.reviewFiling(true); assert.equal(f.writes.length, 1); f.state.filing = { status: 'ARCHIVED' }; assert.equal(f.state.canReview, false) }
  finally { f.unmount() }
})
