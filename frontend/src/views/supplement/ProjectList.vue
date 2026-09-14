<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {message} from 'ant-design-vue'
import {supplementApi,supplementBusinessStatus} from '@/api/supplement'
const route=useRoute(),router=useRouter()
const rows=ref<Record<string,any>[]>([]),loading=ref(false),error=ref(''),keyword=ref(''),channel=ref(''),status=ref('')
const view=computed(()=>String(route.meta.supplementView||'mine'))
const title=computed(()=>({mine:'我的补录',review:'待我审核',history:'补录记录'}[view.value]))
const channels=computed(()=>Array.from(new Set(rows.value.map(p=>p.channelName||p.channel||p.channelCode).filter(Boolean))).map(value=>({value,label:value})))
const statusText=(s:string)=>({DRAFT:'待补录',PENDING:'审核中',SUBMITTED:'审核中',IN_REVIEW:'审核中',UNIT_REVIEW:'单位审核中',HQ_REVIEW:'总部审核中',APPROVED:'已完成',RETURNED:'已退回',REJECTED:'已退回'}[s]||s||'待补录')
const businessText=supplementBusinessStatus
const filtered=computed(()=>rows.value.filter(p=>(!keyword.value||[p.name,p.projectName,p.code,p.projectCode,p.projectNo].join(' ').includes(keyword.value))&&(!channel.value||[p.channelName,p.channel,p.channelCode].includes(channel.value))&&(!status.value||(status.value==='审核中'?statusText(p.status).includes('审核中'):statusText(p.status)===status.value))))
async function load(){loading.value=true;error.value='';try{rows.value=(await supplementApi.list(view.value)).data||[]}catch(e:any){error.value=e.message;message.error(e.message)}finally{loading.value=false}}
watch(view,()=>{status.value='';load()},{immediate:true})
function completeness(v:any){if(v==null)return '待核对';if(typeof v==='object')return `${v.complete??v.completed??0}/${v.total??0}`;return String(v)}
const columns=[{title:'项目名称 / 编号',key:'name',width:340},{title:'来源渠道',key:'channel',width:160},{title:'业务阶段',key:'stage',width:130},{title:'信息完整度',key:'info',width:120},{title:'材料完整度',key:'material',width:120},{title:'补录状态',key:'status',width:110},{title:'操作',key:'action',width:110,fixed:'right' as const}]
</script>
<template>
  <div class="supplement-list">
    <header><div><span class="eyebrow">导入项目补录</span><h1>{{title}}</h1><p>依据项目来源渠道生成维护清单，补齐项目信息及分阶段材料。</p></div><a-button :loading="loading" @click="load">刷新</a-button></header>
    <a-alert v-if="error" :message="error" type="error" show-icon style="margin-bottom:16px" />
    <section class="list-panel">
      <div class="status-tabs"><button v-for="s in ['','待补录','已退回','审核中','已完成']" :key="s" :class="{selected:status===s}" @click="status=s">{{s||'全部'}}<span v-if="!s">{{rows.length}}</span></button></div>
      <div class="filters"><a-input-search v-model:value="keyword" placeholder="搜索项目名称 / 编号" allow-clear style="max-width:340px" /><a-select v-model:value="channel" placeholder="全部来源渠道" :options="[{label:'全部来源渠道',value:''},...channels]" style="width:220px" /><a-button type="link" @click="keyword='';channel='';status=''">重置</a-button></div>
      <a-table :columns="columns" :data-source="filtered" :loading="loading" :row-key="p=>p.id||p.projectId" :scroll="{x:1100}" :pagination="{pageSize:10,showTotal:total=>`共 ${total} 个项目`}">
        <template #emptyText><a-empty :description="view==='review'?'暂无待审核的补录批次':'暂无符合条件的导入项目'" /></template>
        <template #bodyCell="{column,record}">
          <template v-if="column.key==='name'"><strong>{{record.name||record.projectName}}</strong><div class="muted">{{record.projectNo||record.code||record.projectCode||'未编号'}}</div></template>
          <template v-else-if="column.key==='channel'"><a-tag color="blue">{{record.channelName||record.channel||record.channelCode||'渠道待核对'}}</a-tag></template>
          <template v-else-if="column.key==='stage'">{{businessText(record.stageName||record.stage||record.businessStatus||record.projectStatus)}}</template>
          <template v-else-if="column.key==='info'">{{completeness(record.infoComplete)}} / {{record.infoTotal??'—'}}</template>
          <template v-else-if="column.key==='material'">{{record.materialPending?'待完善清单':completeness(record.materialComplete)+' / '+(record.materialTotal??'—')}}</template>
          <template v-else-if="column.key==='status'"><a-tag :color="statusText(record.status)==='已完成'?'green':statusText(record.status)==='已退回'?'red':'blue'">{{statusText(record.status)}}</a-tag></template>
          <template v-else-if="column.key==='action'"><a-button type="link" @click="router.push({path:`/supplement/project/${record.id||record.projectId}`,query:{view}})">{{view==='mine'?'进入补录':view==='review'?'查看审核':'查看记录'}}</a-button></template>
        </template>
      </a-table>
    </section>
  </div>
</template>
<style scoped>
.supplement-list{padding:20px}.supplement-list header{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.eyebrow{color:#62788b;font-size:12px}h1{font-size:24px;margin:4px 0 6px}p,.muted{color:#7b8795;font-size:13px;margin:0}.list-panel{background:white;border:1px solid #e4e9ef;border-radius:8px}.status-tabs{display:flex;gap:24px;padding:0 20px;border-bottom:1px solid #e4e9ef}.status-tabs button{padding:15px 0;background:none;border:0;border-bottom:2px solid transparent;color:#64748b;cursor:pointer}.status-tabs .selected{border-bottom-color:#0065d8;color:#0065d8;font-weight:600}.status-tabs span{margin-left:8px;background:#edf3f9;border-radius:10px;padding:1px 6px}.filters{display:flex;gap:12px;padding:16px 20px}.muted{margin-top:4px}
</style>






