<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { downloadAuthenticatedFile } from '@/utils/authFile'
import { materialRows, materialStateText } from '@/utils/projectDetailPresentation'
import ReadonlyMaterialFile from './ReadonlyMaterialFile.vue'
const props = defineProps<{ sections:any[]; status?:string; stage:number; resolved:boolean; imported:boolean; projectId:number }>()
const filter = ref('all')
const rows = computed(() => materialRows(props.sections, props.status).filter(r => r.stage === props.stage).filter(r =>
  filter.value === 'all' || filter.value === 'uploaded' && r.files.length || filter.value === 'missing' && ['missing','returned'].includes(r.state) || filter.value === 'future' && r.state === 'future'))
async function download(file:any) {
  try { await downloadAuthenticatedFile(file.url || file.fileUrl, file.fileName) } catch(e:any) { message.error(e.message) }
}
</script>
<template>
  <section class="stage-materials" aria-label="阶段材料清单">
    <div class="materials-heading"><strong>阶段材料与要求</strong><span>办理位置：左侧【{{ imported ? '导入项目补录 → 我的补录' : ['立项阶段','实施阶段','验收阶段 / 成果转化'][stage] }}】</span></div>
    <a-alert v-if="!resolved" type="warning" show-icon message="渠道材料要求待确认，请核对项目来源渠道及材料配置。" />
    <a-alert v-else-if="sections.some(s=>s.configurationPending)" type="warning" show-icon message="部分栏目材料要求待配置，不能认定材料齐备。" />
    <a-radio-group v-model:value="filter" size="small" class="material-filter" :options="[{label:'全部',value:'all'},{label:'已上传',value:'uploaded'},{label:'待补充',value:'missing'},{label:'后续需提供',value:'future'}]" option-type="button" />
    <a-table size="small" row-key="key" :data-source="rows" :pagination="{pageSize:8,hideOnSinglePage:true}" :scroll="{x:760}"
      :columns="[{title:'环节 / 材料',key:'name',width:190},{title:'要求 / 提交时机',key:'timing',width:230},{title:'状态',key:'state',width:130},{title:'文件 / 版本 / 上传信息',key:'files'}]">
      <template #bodyCell="{column,record}">
        <template v-if="column.key==='name'"><strong>{{record.name}}</strong><small>{{record.sectionName}} · 规则 {{record.version}}</small></template>
        <template v-else-if="column.key==='timing'"><span>{{record.required?'必交':record.condition?'条件适用':'按需提供'}}</span><small>{{record.timing}}</small><small v-if="record.condition">{{record.condition}}</small></template>
        <template v-else-if="column.key==='state'"><a-tag :color="record.state==='complete'?'success':['missing','returned'].includes(record.state)?'warning':record.state==='review'?'processing':'default'">{{materialStateText[record.state]}}</a-tag></template>
        <template v-else-if="column.key==='files'"><div v-for="f in record.files" :key="f.id || f.fileUrl"><ReadonlyMaterialFile :file="f" /><small>v{{f.version || 1}} · {{f.uploadedBy || '上传人未记录'}} · {{f.uploadedAt || f.createdAt || '时间未记录'}}</small></div><span v-if="!record.files.length">—</span></template>
      </template>
      <template #emptyText>{{resolved?'暂无匹配材料；请同时留意栏目配置提示':'材料要求待确认'}}</template>
    </a-table>
  </section>
</template>
<style scoped>
.stage-materials{border-top:1px solid #e5eaf1;margin-top:16px;padding-top:16px}.materials-heading{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-bottom:12px}.materials-heading span,small{font-size:12px;color:#697586}small{display:block;overflow-wrap:anywhere;margin-top:4px}.material-filter{margin:12px 0}
</style>
