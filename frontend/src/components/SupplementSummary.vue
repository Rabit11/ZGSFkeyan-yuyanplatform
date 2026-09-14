<script setup lang="ts">
import {ref,watch} from 'vue'
import http from '@/api/request'
import {downloadAuthenticatedFile} from '@/utils/authFile'
import {message} from 'ant-design-vue'
const props=defineProps<{projectId:number}>()
const sections=ref<any[]>([]),loading=ref(false),error=ref('')
async function load(){loading.value=true;error.value='';try{sections.value=(await http.get<any[]>(`/api/supplement/${props.projectId}/approved`)).data}catch(e:any){error.value=e.message||'读取补录归集结果失败'}finally{loading.value=false}}
watch(()=>props.projectId,load,{immediate:true})
async function download(f:any){try{await downloadAuthenticatedFile(`/api/supplement/${props.projectId}/files/${f.id}`,f.fileName)}catch(e:any){message.error(e.message)}}
</script>
<template>
  <a-spin :spinning="loading">
    <a-alert type="info" show-icon message="已审核补录信息" description="展示各栏目的最近通过版本。新批次补录期间保留已通过内容；填写与上传请从左侧导入项目补录进入。" />
    <a-alert v-if="error" type="warning" :message="error" style="margin-top:12px"><template #action><a-button @click="load">重试</a-button></template></a-alert>
    <a-empty v-else-if="!loading&&!sections.length" description="暂无审核通过的补录信息" />
    <a-collapse v-else style="margin-top:12px">
      <a-collapse-panel v-for="section in sections" :key="section.key" :header="`${section.title} · 批次 ${section.batch||1}`">
        <a-table v-if="section.repeatable" size="small" :data-source="section.rows" :columns="section.fields.map((f:any)=>({title:f.label,dataIndex:f.key,width:150}))" :scroll="{x:'max-content'}" :pagination="{pageSize:10}" row-key="_rowId" />
        <a-descriptions v-else size="small" :column="2" bordered>
          <a-descriptions-item v-for="field in section.fields" :key="field.key" :label="field.label">{{section.values?.[field.key]??'—'}}</a-descriptions-item>
        </a-descriptions>
        <div v-for="material in section.materials" :key="material.code" class="summary-files"><strong>{{material.name}}</strong><a-button v-for="file in material.files" :key="file.id" type="link" size="small" @click="download(file)">{{file.fileName}} · v{{file.version}}</a-button><span v-if="!material.files?.length">无附件</span></div>
      </a-collapse-panel>
    </a-collapse>
  </a-spin>
</template>
<style scoped>.summary-files{display:flex;align-items:center;flex-wrap:wrap;gap:8px;border-bottom:1px solid #eef1f5;padding:10px 0}.summary-files strong{font-size:13px}.summary-files span{color:#7d8795}</style>
