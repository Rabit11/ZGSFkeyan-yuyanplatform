<script setup lang="ts">
import {ref,watch} from 'vue'
import http from '@/api/request'
import SupplementSummary from './SupplementSummary.vue'
import {publishedProject,supplementStatusText as text,supplementStatusColor as color} from '@/utils/supplementPublication'
const props=defineProps<{projectId?:number}>()
const emit=defineEmits<{close:[]}>()
const info=ref<any>(null),loading=ref(false),error=ref('')
let request=0
async function load(){const serial=++request;info.value=null;error.value='';if(!props.projectId)return;loading.value=true;try{const res=await http.get(`/api/supplement/${props.projectId}/publication`);if(serial===request)info.value=res.data}catch(e:any){if(serial===request)error.value=e.message||'读取失败'}finally{if(serial===request)loading.value=false}}
watch(()=>props.projectId,load,{immediate:true})
const columns=[{title:'栏目',dataIndex:'sectionKey'},{title:'批次',dataIndex:'batch'},{title:'办理结果',dataIndex:'status'},{title:'办理人',dataIndex:'actorName'},{title:'时间',dataIndex:'createdAt'},{title:'审核意见',dataIndex:'opinion'}]
</script>
<template>
 <a-drawer :open="!!projectId" title="补录信息与审核情况" :width="'min(100vw, 1050px)'" @close="emit('close')">
  <a-spin :spinning="loading">
   <a-alert v-if="error" type="error" :message="error"><template #action><a-button @click="load">重试</a-button></template></a-alert>
   <template v-if="info">
    <h3>{{publishedProject({...info.project,supplement:info.summary}).name}} <a-tag :color="color[info.summary.status]">{{text[info.summary.status]}}</a-tag></h3>
    <a-button size="small" @click="load">刷新审核情况</a-button>
    <p>当前显示已提交信息；待审核内容不计入已审核统计。后续修改草稿仅负责人及审核人员可见。</p>
    <a-tabs>
     <a-tab-pane key="information" tab="已提交信息与材料"><SupplementSummary :project-id="projectId!" :provided-sections="info.sections" /></a-tab-pane>
     <a-tab-pane key="history" tab="审核情况">
      <a-table row-key="id" size="small" :data-source="info.history" :columns="columns" :pagination="{pageSize:10}" :scroll="{x:850}">
       <template #bodyCell="{column,record,text: value}">
        <template v-if="column.dataIndex==='status'"><a-tag :color="color[value]">{{text[value]||value}}</a-tag></template>
        <template v-else-if="column.dataIndex==='sectionKey'">{{info.sections.find((s:any)=>s.key===value)?.title||value}}</template>
        <template v-else-if="column.dataIndex==='opinion'">{{record.action==='SUBMIT'?'负责人提交':value||'—'}}</template>
       </template>
      </a-table>
     </a-tab-pane>
     <a-tab-pane key="accepted" tab="已审核版本"><SupplementSummary :project-id="projectId!" /></a-tab-pane>
    </a-tabs>
   </template>
  </a-spin>
 </a-drawer>
</template>
