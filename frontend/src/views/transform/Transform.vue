<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, ArrowRightOutlined, ReloadOutlined, SearchOutlined, UploadOutlined, FileTextOutlined, ApartmentOutlined, DeploymentUnitOutlined } from '@ant-design/icons-vue'
import { projectApi, deliverableApi, transformApi } from '@/api/modules'
import { transformWorkflowApi, type TransformPackage } from '@/api/transform'
import type { ProjDeliverable } from '@/api/types'
import { useDictStore } from '@/stores/dict'
import { calcColor, remainDays } from '@/utils/color'
import { downloadAuthenticatedFile } from '@/utils/authFile'
import { WORKFLOW_TEXT, STATUS_TEXT, WAY_TEXT, ACTION_TEXT, FORM_PARENT, confirmedStatus, confirmedActualDate, bindingReason, parseArray, validatePackage } from '@/utils/transformPackage'

const route = useRoute(), router = useRouter(), dict = useDictStore()
const loading = ref(false), saving = ref(false), uploading = ref(false), error = ref('')
const rows = ref<TransformPackage[]>([]), projects = ref<any[]>([])
const query = reactive({ keyword: '', way: '', status: undefined as string | undefined, workflow: undefined as string | undefined, risk: '', projectId: route.query.projectId ? Number(route.query.projectId) : undefined as number | undefined, page: 1, size: 10 })
const activeTab = ref('all')
const drawerOpen = ref(false), mode = ref<'view' | 'edit'>('view'), detailLoading = ref(false), detailError = ref('')
const form = reactive<Partial<TransformPackage>>({})
const pool = ref<ProjDeliverable[]>([]), evidence = ref<any[]>([])
const selectedProject = computed(() => projects.value.find(p => p.id === form.projectId) || null)
const writable = computed(() => !form.orphanedProject && !!form.projectId && !!form.allowedActions?.includes('fill'))
const editable = computed(() => ['DRAFT', 'RETURNED'].includes(form.workflowStatus || 'DRAFT'))
const history = computed(() => parseArray(form.historyJson).slice().reverse())
const formOptions = computed(() => (dict.dicts.TRANSFORM_FORM || []).filter(d => d.status !== 0 && (d.parentCode || FORM_PARENT[d.dictCode]) === form.transformWay).map(d => ({value: d.dictCode, label: d.dictName})))
const wayOptions = computed(() => dict.options('TRANSFORM_WAY'))
const statusOptions = computed(() => dict.options('TRANSFORM_STATUS'))
const color = (r: Partial<TransformPackage>) => calcColor(r.planDate, confirmedStatus(r) === 'DONE')
const colorLabel: Record<string, string> = { GREEN: '已完成', BLUE: '正常推进', YELLOW: '30天内到期', RED: '已逾期' }
const tagColor: Record<string, string> = { GREEN: 'success', BLUE: 'processing', YELLOW: 'warning', RED: 'error' }
const stats = computed(() => ({ model: rows.value.filter(r => r.transformWay === 'MODEL').length, market: rows.value.filter(r => r.transformWay === 'MARKET').length, done: rows.value.filter(r => confirmedStatus(r) === 'DONE').length, risk: rows.value.filter(r => ['RED','YELLOW'].includes(color(r))).length }))
const needsMe = (r: TransformPackage) => (r.workflowStatus === 'UNIT_REVIEW' && r.allowedActions?.includes('audit')) || (r.workflowStatus === 'HQ_RECORD' && r.allowedActions?.includes('record'))
const pendingCount = computed(() => rows.value.filter(needsMe).length)
const filtered = computed(() => rows.value.filter(r => {
  const kw = query.keyword.trim().toLowerCase()
  return (!kw || [r.name, r.achievementNo, r.projectNo, r.projectName, r.dutyOrg].some(v => String(v || '').toLowerCase().includes(kw)))
    && (!query.way || r.transformWay === query.way) && (!query.status || confirmedStatus(r) === query.status)
    && (!query.workflow || r.workflowStatus === query.workflow) && (!query.projectId || r.projectId === query.projectId)
    && (!query.risk || (query.risk === 'RISK' ? ['RED','YELLOW'].includes(color(r)) : color(r) === query.risk))
    && (activeTab.value !== 'pending' || needsMe(r))
}).sort((a,b) => ({RED:0,YELLOW:1,BLUE:2,GREEN:3}[color(a)] - {RED:0,YELLOW:1,BLUE:2,GREEN:3}[color(b)])))
watch(() => [query.keyword, query.way, query.status, query.workflow, query.risk, query.projectId, activeTab.value], () => { query.page = 1 })
const columns = [
  { title: '成果包 / 所属项目', key: 'name', width: 320 }, { title: '转化路径', key: 'way', width: 150 },
  { title: '转化进度', key: 'status', width: 110 }, { title: '审核状态', key: 'workflow', width: 160 },
  { title: '计划转化 / 预警', key: 'date', width: 155 }, { title: '交付物', dataIndex: 'itemCount', width: 80, align: 'right' as const },
  { title: '操作', key: 'action', width: 90, fixed: 'right' as const },
]
async function allPages(api: (params: any) => Promise<any>) {
  const result: any[] = []
  for (let page = 1; ; page++) {
    const response = await api({ page, size: 200 })
    const data = response.data || {}, records = data.records || []
    result.push(...records)
    if (result.length >= Number(data.total || 0) || !records.length) return result
  }
}
let loadSeq = 0
async function load() {
  const seq = ++loadSeq; loading.value = true; error.value = ''
  try {
    const data = await allPages(transformApi.page)
    if (seq === loadSeq) rows.value = data
  } catch (e: any) { if (seq === loadSeq) error.value = e.message || '成果台账加载失败，请重试' }
  finally { if (seq === loadSeq) loading.value = false }
}
async function initialize() {
  try {
    await Promise.all(['TRANSFORM_WAY','TRANSFORM_FORM','TRANSFORM_STATUS','DELIVERABLE_TYPE'].map(t => dict.load(t)))
    projects.value = await allPages(projectApi.page)
    await load()
    if (route.query.achievementNo) {
      const match = rows.value.find(r => r.achievementNo === route.query.achievementNo)
      if (match) await openDetail(match)
    }
  } catch (e: any) { error.value = e.message || '项目或字典加载失败，请重试' }
}
onMounted(initialize)
function resetFilters() { Object.assign(query,{keyword:'',way:'',status:undefined,workflow:undefined,risk:'',projectId:undefined,page:1}); activeTab.value='all' }
function newPackage() {
  for (const key of Object.keys(form)) delete (form as any)[key]
  Object.assign(form,{name:'',intro:'',introDetail:'',projectId:query.projectId,transformWay:'MODEL',transformForm:'INSTALLED',status:'NOT_STARTED',workflowStatus:'DRAFT',revision:0,deliverableIds:[]})
  pool.value=[]; evidence.value=[]; mode.value='edit'; detailError.value=''; drawerOpen.value=true
  if(form.projectId) changeProject()
}
let detailSeq=0
async function openDetail(row: TransformPackage) {
  const seq=++detailSeq; drawerOpen.value=true; mode.value='view'; detailLoading.value=true; detailError.value=''
  try {
    const res = await transformApi.detail(row.id)
    if(seq!==detailSeq) return
    const dvs = res.data.orphanedProject ? {data:res.data.deliverables || []} : await deliverableApi.list(res.data.projectId || row.projectId!)
    if(seq!==detailSeq) return
    for(const key of Object.keys(form)) delete (form as any)[key]
    Object.assign(form,res.data)
    pool.value=(dvs.data as ProjDeliverable[]) || []
    form.deliverableIds = pool.value.filter(d => d.achievementNo===form.achievementNo && d.status==='DELIVERED').map(d=>d.id)
    evidence.value=parseArray(form.evidenceJson)
  } catch(e:any) { if(seq===detailSeq) detailError.value=e.message || '详情加载失败' }
  finally {if(seq===detailSeq) detailLoading.value=false}
}
let projectSeq=0
async function changeProject() {
  const seq=++projectSeq; pool.value=[]; form.deliverableIds=[]; form.allowedActions=[]; detailError.value=''
  const p=selectedProject.value; form.projectNo=p?.projectNo; form.dutyOrg=p?.orgName || p?.leadOrg || ''
  if(!form.projectId) return
  detailLoading.value=true
  try {const [res,permissions]=await Promise.all([deliverableApi.list(form.projectId),transformWorkflowApi.permissions(form.projectId)]); if(seq===projectSeq) {pool.value=(res.data as ProjDeliverable[]) || []; form.allowedActions=permissions.data?.allowedActions || []}}
  catch(e:any) {if(seq===projectSeq) detailError.value=e.message || '交付物加载失败'}
  finally {if(seq===projectSeq) detailLoading.value=false}
}
async function save() {
  if(saving.value || uploading.value || !writable.value) return
  const invalid=validatePackage(form,evidence.value)
  if(invalid) { message.warning(invalid); return }
  saving.value=true
  try {
    const payload={...form,evidenceJson:JSON.stringify(evidence.value)}
    const result=form.id ? await transformApi.update(form.id,payload) : await transformApi.create(payload)
    message.success('成果包已保存')
    await load()
    const id=form.id || Number(result.data)
    await openDetail({id,projectId:form.projectId} as TransformPackage)
  } catch(e:any) {message.error(e.message || '保存失败，请重试')}
  finally {saving.value=false}
}
async function upload(options:any) {
  if(!writable.value || !editable.value || mode.value!=='edit' || uploading.value) return
  if(evidence.value.length>=20) {message.warning('最多上传20份材料'); return}
  uploading.value=true
  try {
    const data=new FormData(); data.append('file',options.file); data.append('projectId',String(form.projectId))
    const res=await transformWorkflowApi.upload(data)
    if(!(res.data as any)?.fileUrl) throw new Error('文件上传未返回有效地址')
    const uploaded = res.data as any
    evidence.value.push(uploaded); options.onSuccess?.(uploaded)
    message.success('文件已上传，请保存成果包')
  } catch(e:any) {options.onError?.(e); message.error(e.message || '上传失败')}
  finally {uploading.value=false}
}
async function download(file:any) { try {await downloadAuthenticatedFile(file.fileUrl,file.fileName)} catch(e:any) {message.error(e.message || '下载失败')} }
const actionOpen=ref(false), action=ref(''), note=ref('')
function askAction(value:string) {action.value=value;note.value='';actionOpen.value=true}
async function act() {
  if(saving.value || !form.id) return
  if(action.value==='REJECT' && !note.value.trim()) {message.warning('请填写退回原因');return}
  saving.value=true
  try {
    await transformWorkflowApi.act(form.id,form.revision!,action.value,note.value)
    actionOpen.value=false;message.success(`${ACTION_TEXT[action.value]}成功`)
    await load();await openDetail(form as TransformPackage)
    if(action.value==='REOPEN') mode.value='edit'
  } catch(e:any) {message.error(e.message || '办理失败，请重试')}
  finally {saving.value=false}
}
function deadline(r:Partial<TransformPackage>) {
  const days=remainDays(r.planDate)
  if(confirmedStatus(r)==='DONE') return confirmedActualDate(r) ? `实际 ${confirmedActualDate(r)}` : '已完成'
  return days===null ? '未设置计划' : days<0 ? `逾期 ${-days} 天` : days===0 ? '今天到期' : `距计划 ${days} 天`
}
</script>

<template>
  <div class="page-container transform-page">
    <header class="transform-heading">
      <div><div class="eyebrow">科研成果 · 应用与价值</div><h1>成果转化</h1><p>从已交付成果到型号应用与市场落地，全程追溯每一个成果包。</p></div>
      <a-button type="primary" @click="newPackage"><PlusOutlined />新建成果包</a-button>
    </header>
    <div class="path-grid" aria-label="转化路径筛选">
      <button class="path-card model" :class="{ selected: query.way==='MODEL' }" :aria-pressed="query.way==='MODEL'" @click="query.way=query.way==='MODEL'?'':'MODEL'">
        <div class="path-icon"><ApartmentOutlined /></div><div class="path-copy"><span class="eyebrow">型号应用</span><h2>向型号转化 <ArrowRightOutlined /></h2><p>装机应用 · 技术储备，承接公司成果转化工作计划</p></div><div class="path-number">{{ stats.model }}<span>成果包</span></div>
      </button>
      <button class="path-card market" :class="{ selected: query.way==='MARKET' }" :aria-pressed="query.way==='MARKET'" @click="query.way=query.way==='MARKET'?'':'MARKET'">
        <div class="path-icon"><DeploymentUnitOutlined /></div><div class="path-copy"><span class="eyebrow">市场合作</span><h2>向市场转化 <ArrowRightOutlined /></h2><p>转让 · 许可 · 联合实施 · 作价投资</p></div><div class="path-number">{{ stats.market }}<span>成果包</span></div>
      </button>
    </div>
    <section class="summary-strip" aria-label="权限范围内全部成果统计">
      <span>我的可见成果 <strong>{{ rows.length }}</strong></span><span>已完成转化 <strong class="success-text">{{ stats.done }}</strong></span>
      <button @click="query.risk=query.risk?'':'RISK'" :aria-pressed="!!query.risk">临期 / 逾期 <strong class="risk-text">{{ stats.risk }}</strong><ArrowRightOutlined /></button>
      <div class="process-caption">项目团队填报 <span>→</span> 二级单位审核 <span>→</span> 总部备案</div>
    </section>
    <a-alert v-if="error" type="error" show-icon :message="error" class="load-error"><template #action><a-button size="small" @click="initialize">重试</a-button></template></a-alert>
    <section class="ledger-panel">
      <div class="ledger-heading"><h2>成果包台账</h2><a-button type="text" :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></div>
      <a-tabs v-model:active-key="activeTab"><a-tab-pane key="all" tab="全部成果"/><a-tab-pane key="pending" :tab="`待我办理 (${pendingCount})`"/></a-tabs>
      <div class="filters">
        <a-input v-model:value="query.keyword" aria-label="搜索成果" placeholder="搜索成果名称、编号、项目或责任单位" allow-clear class="search"><template #prefix><SearchOutlined /></template></a-input>
        <a-select v-model:value="query.projectId" aria-label="所属项目筛选" placeholder="全部项目" allow-clear show-search :options="projects.map(p=>({value:p.id,label:p.projectNo+' · '+p.name}))" option-filter-prop="label" class="project-filter"/>
        <a-select v-model:value="query.status" aria-label="转化进度筛选" placeholder="转化进度" allow-clear :options="statusOptions" />
        <a-select v-model:value="query.workflow" aria-label="审核状态筛选" placeholder="审核状态" allow-clear :options="Object.entries(WORKFLOW_TEXT).map(([value,label])=>({value,label}))"/>
        <a-button @click="resetFilters">重置</a-button>
      </div>
      <div class="result-hint"><span>共 {{ filtered.length }} 个成果包<span v-if="query.way"> · {{ WAY_TEXT[query.way] }}</span><span v-if="query.risk"> · 临期与逾期</span></span><span>按风险优先排列 · 转化进度与审核状态独立展示</span></div>
      <a-table :columns="columns" :data-source="filtered" row-key="id" :loading="loading" :scroll="{x:1065}" :pagination="{current:query.page,pageSize:query.size,total:filtered.length,showSizeChanger:true,showTotal:(n:number)=>`共 ${n} 项`}" @change="(p:any)=>{query.page=p.current;query.size=p.pageSize}">
        <template #emptyText><a-empty description="暂无符合条件的成果包"><a-button @click="resetFilters">清除筛选</a-button></a-empty></template>
        <template #bodyCell="{column,record}">
          <template v-if="column.key==='name'"><button class="name-link" @click="openDetail(record)">{{ record.name }}</button><div class="record-code">{{ record.achievementNo }}</div><div class="record-project" :title="record.projectName">{{ record.projectNo }} · {{ record.orphanedProject ? '关联项目已不存在（历史记录）' : record.projectName || '—' }}</div></template>
          <template v-else-if="column.key==='way'"><span>{{ dict.label('TRANSFORM_WAY',record.transformWay) }}</span><div class="muted">{{ dict.label('TRANSFORM_FORM',record.transformForm) }}</div></template>
          <template v-else-if="column.key==='status'"><a-tag :color="confirmedStatus(record)==='DONE'?'success':'default'">{{ dict.label('TRANSFORM_STATUS',confirmedStatus(record)) }}</a-tag></template>
          <template v-else-if="column.key==='workflow'"><span class="workflow-label" :class="record.workflowStatus">{{ WORKFLOW_TEXT[record.workflowStatus] || '待核对' }}</span><div class="muted">{{ record.dutyOrg }}</div></template>
          <template v-else-if="column.key==='date'"><div class="date">{{ record.planDate || '—' }}</div><a-tag :color="tagColor[color(record)]">{{ deadline(record) }}</a-tag></template>
          <template v-else-if="column.key==='action'"><a-button type="link" size="small" @click="openDetail(record)">{{ needsMe(record)?'办理':'查看' }}</a-button></template>
        </template>
      </a-table>
    </section>

    <a-drawer v-model:open="drawerOpen" :title="mode==='view' ? '成果包详情' : form.id ? '编辑成果包' : '新建成果包'" width="min(920px, 94vw)" :mask-closable="false" :closable="!saving&&!uploading" :keyboard="!saving&&!uploading">
      <a-spin :spinning="detailLoading">
        <a-alert v-if="detailError" type="error" show-icon :message="detailError" />
        <template v-else>
          <div class="detail-heading"><h2>{{ form.name || '创建一个成果包' }}</h2><span class="record-code">{{ form.achievementNo || '保存后自动生成全局唯一成果编号' }}</span></div>
          <a-alert v-if="form.orphanedProject" type="warning" show-icon message="关联项目已不存在，当前仅供管理员核查历史记录。" class="detail-alert" />
          <a-alert v-if="!writable" type="info" show-icon message="仅项目负责人可填报和上传；当前账号只读查看材料，并按授权办理审核或备案。" class="detail-alert" />
          <a-alert v-if="form.confirmedStatus && form.workflowStatus!=='RECORDED'" type="info" :message="`正式转化进度：${STATUS_TEXT[form.confirmedStatus]}${form.confirmedActualDate ? ' · 实际日期 ' + form.confirmedActualDate : ''}。本轮填报将在备案后更新正式结果。`" class="detail-alert" />
          <a-alert v-if="mode==='view'" show-icon :type="form.workflowStatus==='RETURNED'?'warning':'info'" :message="WORKFLOW_TEXT[form.workflowStatus || 'DRAFT']" :description="form.workflowStatus==='HQ_RECORD'?'二级单位审核已通过，总部仅办理资料备案，无需二次审批。':form.workflowStatus==='RECORDED'?'本次资料已备案。后续进展通过“更新进展”发起下一轮填报，历史记录保留。':'核对成果构成、转化信息和佐证材料后，按当前节点办理。'" class="detail-alert" />
          <a-form layout="vertical" :disabled="mode==='view'||(!!form.projectId&&!writable)||saving">
            <h3 class="section-title">成果信息</h3>
            <div class="form-grid">
              <a-form-item label="成果名称" required><a-input v-model:value="form.name" :maxlength="255" placeholder="打包交付物的统一名称"/></a-form-item>
              <a-form-item label="所属项目" required><a-select v-model:value="form.projectId" :disabled="!!form.id||mode==='view'" show-search option-filter-prop="label" placeholder="选择成果所属项目" :options="projects.map(p=>({value:p.id,label:p.projectNo+' · '+p.name}))" @change="changeProject"/></a-form-item>
            </div>
            <a-form-item label="成果简介"><a-textarea v-model:value="form.intro" :maxlength="100" :rows="2" show-count placeholder="概述核心技术内容与应用价值（100字以内）"/></a-form-item>
            <h3 class="section-title">成果构成 <span>仅已交付且未被其他成果包占用的交付物可选</span></h3>
            <a-table size="small" row-key="id" :data-source="pool" :pagination="false" :row-selection="{selectedRowKeys:form.deliverableIds||[],onChange:(keys:any)=>form.deliverableIds=keys,getCheckboxProps:(d:ProjDeliverable)=>({disabled:mode==='view'||!writable||!!bindingReason(d,form.achievementNo)})}" :columns="[{title:'交付物',dataIndex:'name'},{title:'权属单位',dataIndex:'ownerOrgs'},{title:'交付 / 绑定状态',key:'binding'}]">
              <template #bodyCell="{column,record}"><template v-if="column.key==='binding'"><span :class="bindingReason(record,form.achievementNo)?'muted':'success-text'">{{ bindingReason(record,form.achievementNo) || '已交付 · 可纳入' }}</span></template></template>
              <template #emptyText><a-empty :description="form.projectId?'本项目暂无交付物，请先在验收交付物模块维护':'选择项目后加载交付物'"/></template>
            </a-table>
            <h3 class="section-title">转化安排</h3>
            <div class="form-grid">
              <a-form-item label="转化方式" required><a-select v-model:value="form.transformWay" :options="wayOptions" @change="form.transformForm=formOptions[0]?.value"/></a-form-item>
              <a-form-item label="转化形式" required><a-select v-model:value="form.transformForm" :options="formOptions"/></a-form-item>
              <a-form-item label="责任单位" required><a-input v-model:value="form.dutyOrg" :maxlength="128" placeholder="开展成果转化的责任主体"/></a-form-item>
              <a-form-item label="转化进度" required><a-select v-model:value="form.status" :options="statusOptions"/></a-form-item>
              <a-form-item label="计划转化时间" required><a-date-picker v-model:value="form.planDate" value-format="YYYY-MM-DD" style="width:100%"/></a-form-item>
              <a-form-item label="实际转化时间" :required="form.status==='DONE'"><a-date-picker v-model:value="form.actualDate" value-format="YYYY-MM-DD" style="width:100%"/></a-form-item>
            </div>
            <a-form-item label="转化简介" required :extra="form.transformWay==='MODEL'?'请包含转化任务、完成时间、应用对象、应用单位及纳入公司成果转化计划的年度。':'请包含交易对象、合作对象、合同金额、转化净收益及是否奖励分配。'"><a-textarea v-model:value="form.introDetail" :rows="4" :maxlength="2000" show-count /></a-form-item>
          </a-form>
          <h3 class="section-title">成效佐证 <span>完成转化时必填 · 最多20份</span></h3>
          <a-upload v-if="mode==='edit'&&writable" :show-upload-list="false" :custom-request="upload" :disabled="uploading||saving"><a-button :loading="uploading"><UploadOutlined/>上传佐证材料</a-button></a-upload>
          <div v-if="!evidence.length" class="muted evidence-empty">暂无成效佐证材料</div>
          <div v-for="(file,index) in evidence" :key="file.fileUrl" class="evidence-row"><FileTextOutlined/><button class="file-link" @click="download(file)">{{ file.fileName }}</button><a-button v-if="mode==='edit'&&writable" type="text" danger size="small" :disabled="saving||uploading" @click="evidence.splice(index,1)">移除</a-button></div>
          <template v-if="mode==='view'">
            <h3 class="section-title">办理履历</h3>
            <a-empty v-if="!history.length" description="暂无办理记录；历史成果须核对后提交审核" />
            <a-timeline><a-timeline-item v-for="(event,index) in history" :key="index" :color="event.action==='REJECT'?'red':'blue'"><b>{{ ACTION_TEXT[event.action] || event.action }}</b><div>{{ event.actor }} · {{ event.at?.replace('T',' ').slice(0,19) }}</div><div v-if="event.note" class="history-note">{{ event.note }}</div><a-collapse v-if="event.snapshot" ghost size="small"><a-collapse-panel key="snapshot" header="查看本次留档内容"><div>{{ STATUS_TEXT[event.snapshot.status] }} · 计划 {{ event.snapshot.planDate }}</div><p class="history-note">{{ event.snapshot.introDetail }}</p><div v-for="file in event.snapshot.evidence" :key="file.fileUrl"><button class="file-link" @click="download(file)">{{ file.fileName }}</button></div></a-collapse-panel></a-collapse></a-timeline-item></a-timeline>
            <a-button v-if="form.projectId&&!form.orphanedProject" @click="router.push('/overview/detail/'+form.projectId)">查看所属项目台账 <ArrowRightOutlined/></a-button>
          </template>
        </template>
      </a-spin>
      <template #footer>
        <div class="drawer-footer"><a-button :disabled="saving||uploading" @click="drawerOpen=false">关闭</a-button>
          <template v-if="!detailLoading&&!detailError">
            <a-button v-if="mode==='edit'" type="primary" :loading="saving" :disabled="!writable||uploading" @click="save">保存成果包</a-button>
            <template v-else>
              <a-button v-if="editable&&writable" @click="mode='edit'">编辑</a-button>
              <a-button v-if="editable&&form.allowedActions?.includes('submit')" type="primary" @click="askAction('SUBMIT')">提交审核</a-button>
              <template v-if="form.workflowStatus==='UNIT_REVIEW'&&form.allowedActions?.includes('audit')"><a-button danger @click="askAction('REJECT')">退回修改</a-button><a-button type="primary" @click="askAction('APPROVE')">审核通过</a-button></template>
              <a-button v-if="form.workflowStatus==='HQ_RECORD'&&form.allowedActions?.includes('record')" type="primary" @click="askAction('RECORD')">确认备案</a-button>
              <a-button v-if="form.workflowStatus==='RECORDED'&&writable" type="primary" @click="askAction('REOPEN')">更新进展</a-button>
            </template>
          </template>
        </div>
      </template>
    </a-drawer>
    <a-modal v-model:open="actionOpen" :title="ACTION_TEXT[action]" :confirm-loading="saving" :mask-closable="false" :ok-text="ACTION_TEXT[action]" cancel-text="取消" @ok="act">
      <p>{{ action==='RECORD'?'确认将二级单位审核通过的全部转化资料备案留存。':action==='REOPEN'?'将保留本次备案快照，开启新的进展填报。更新后需重新提交二级单位审核。':`为“${form.name}”办理${ACTION_TEXT[action]}。` }}</p>
      <a-textarea v-model:value="note" :rows="3" :maxlength="1000" show-count :placeholder="action==='REJECT'?'请填写退回原因（必填）':'办理意见（选填）'" :aria-label="action==='REJECT'?'退回原因':'办理意见'"/>
    </a-modal>
  </div>
</template>

<style scoped>
.transform-page { --tf-blue:#0048a0; --tf-action:#0064ef; --tf-ink:#1f1f1f; --tf-muted:#68788b; --tf-line:#e8e8e8; --tf-paper:#fff; font-family:'Microsoft YaHei','PingFang SC',sans-serif; }
.transform-heading { display:flex; justify-content:space-between; align-items:center; gap:20px; margin-bottom:24px; }
.eyebrow { font-size:12px; letter-spacing:1.5px; color:var(--tf-muted); }
h1 { font-size:30px; font-weight:600; color:var(--tf-blue); margin:5px 0 8px; letter-spacing:1px; }
.transform-heading p { margin:0; color:var(--tf-muted); font-size:14px; }
.path-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; }
.path-card { display:flex; text-align:left; align-items:center; gap:20px; padding:24px; min-height:158px; background:#fff; border:1px solid #dbe4ed; border-radius:6px; cursor:pointer; color:var(--tf-ink); transition:border-color .18s,box-shadow .18s; position:relative; overflow:hidden; }
.path-card::after { content:''; position:absolute; height:220px; width:220px; border:1px solid #e0eaf4; border-radius:50%; right:-130px; top:-80px; pointer-events:none; }
.path-card:hover,.path-card.selected { border-color:var(--tf-action); box-shadow:0 3px 14px #0048a00b; }
.path-card.selected { background:#f5f9ff; }
.path-icon { display:grid; place-items:center; width:58px; height:58px; flex-shrink:0; border-radius:50%; background:#edf4fd; color:var(--tf-blue); font-size:27px; }
.market .path-icon { color:#306a88; background:#edf5f7; }
.path-copy { flex:1; }
.path-copy h2 { font-size:22px; margin:6px 0 10px; color:var(--tf-blue); font-weight:500; }
.path-copy h2 .anticon { font-size:16px; margin-left:12px; }
.path-copy p { font-size:12px; color:var(--tf-muted); margin:0; line-height:1.8; }
.path-number { font-family:'Bahnschrift','DIN Alternate',sans-serif; font-size:38px; color:var(--tf-blue); min-width:58px; text-align:right; }
.path-number span { display:block; font:12px 'Microsoft YaHei',sans-serif; color:var(--tf-muted); margin-top:4px; }
.summary-strip { display:flex; gap:28px; align-items:center; padding:22px 0; font-size:13px; flex-wrap:wrap; }
.summary-strip strong { margin-left:12px; font-family:'Bahnschrift',sans-serif; font-size:22px; }
.summary-strip button { background:none; border:0; padding:0; color:inherit; cursor:pointer; display:flex; align-items:center; gap:8px; }
.summary-strip button strong { margin-left:4px; }.success-text { color:#389e0d; }.risk-text { color:#ad6800; }
.process-caption { margin-left:auto; color:var(--tf-muted); font-size:12px; }.process-caption span { margin:0 10px; color:#91acc9; }
.ledger-panel { background:#fff; border:1px solid var(--tf-line); border-radius:6px; padding:0 20px 8px; }
.ledger-heading { display:flex; justify-content:space-between; align-items:center; padding-top:18px; }.ledger-heading h2 { font-size:17px; margin:0; }
.filters { display:flex; gap:10px; flex-wrap:wrap; margin:2px 0 16px; }.filters .ant-select { min-width:140px; }.filters .project-filter { width:215px; }.filters .search { width:310px; }
.result-hint { display:flex; justify-content:space-between; flex-wrap:wrap; gap:8px; font-size:12px; color:var(--tf-muted); padding-bottom:12px; }
.name-link,.file-link { border:0; background:none; color:var(--tf-blue,#0048a0); cursor:pointer; text-align:left; padding:0; overflow-wrap:anywhere; }
.name-link { font-weight:600; font-size:14px; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }.name-link:hover,.file-link:hover { text-decoration:underline; }
.record-code { font-family:'Consolas',monospace; font-size:11px; color:#6f8297; margin:3px 0; overflow-wrap:anywhere; }.record-project { font-size:12px; color:#68788b; max-width:280px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.muted { font-size:12px; color:#68788b; margin-top:4px; }.date { font-family:'Bahnschrift',sans-serif; margin-bottom:5px; font-size:13px; }.workflow-label { font-size:13px; }.UNIT_REVIEW,.HQ_RECORD { color:#0064ef; }.RETURNED { color:#b54708; }.RECORDED { color:#389e0d; }
.load-error { margin-bottom:16px; }.detail-heading h2 { font-size:21px; color:#0048a0; margin:0 0 8px; }.detail-heading { margin-bottom:18px; }.detail-alert { margin-bottom:20px; }
.section-title { display:flex; align-items:center; flex-wrap:wrap; gap:12px; font-size:15px; color:#0048a0; margin:24px 0 16px; }.section-title span { font-size:12px; font-weight:400; color:#68788b; }
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:0 20px; }.evidence-row { display:flex; align-items:center; gap:10px; padding:10px 0; border-bottom:1px solid #eee; }.evidence-row .file-link { flex:1; }.evidence-empty { padding:16px 0; }.history-note { white-space:pre-wrap; overflow-wrap:anywhere; margin-top:6px; }.drawer-footer { display:flex; justify-content:flex-end; gap:8px; flex-wrap:wrap; }
:deep(.ant-table-tbody > tr > td) { padding-top:14px; padding-bottom:14px; }:deep(.ant-table-thead > tr > th) { background:#f6f8fb; font-weight:500; }
button:focus-visible { outline:2px solid #0064ef; outline-offset:4px; }
@media(max-width:1100px) { .path-card { padding:20px; gap:12px; }.path-icon { display:none; }.process-caption { margin-left:0; } }
@media(max-width:720px) { .path-grid,.form-grid { grid-template-columns:1fr; }.transform-heading { align-items:flex-start; }.transform-heading h1 {font-size:26px;}.transform-heading p {font-size:12px;}.summary-strip {gap:16px;}.filters .search,.filters .project-filter {width:100%;}.path-card {min-height:130px;}.ledger-panel {padding-left:12px;padding-right:12px;}.result-hint>span:last-child {display:none;} }
@media(prefers-reduced-motion:reduce) { .path-card {transition:none;} }
</style>
