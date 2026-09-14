<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'
import { downloadAuthenticatedFile } from '@/utils/authFile'
const props = defineProps<{file:any}>()
const opened=ref(false), url=ref(''), text=ref(''), kind=ref(''), busy=ref(false)
const address=()=>props.file.url || props.file.fileUrl
async function download(){try{await downloadAuthenticatedFile(address(),props.file.fileName)}catch(e:any){message.error(e.message)}}
function release(){if(url.value)URL.revokeObjectURL(url.value);url.value='';text.value=''}
async function preview(){
  if(!address()?.startsWith('/api/')){message.info('此历史附件请下载后查看');return}
  busy.value=true
  try{
    const token=localStorage.getItem('rpm_token')
    const response=await fetch(address(),{headers:token?{Authorization:`Bearer ${token}`}:{}})
    if(!response.ok)throw new Error('附件预览失败，请确认访问权限')
    const blob=await response.blob();release()
    kind.value=/\.pdf$/i.test(props.file.fileName)?'pdf':/\.txt$/i.test(props.file.fileName)?'text':'image'
    if(kind.value==='text')text.value=await blob.text()
    else url.value=URL.createObjectURL(new Blob([blob],{type:kind.value==='pdf'?'application/pdf':({png:'image/png',jpg:'image/jpeg',jpeg:'image/jpeg',gif:'image/gif',webp:'image/webp'} as any)[props.file.fileName.split('.').pop().toLowerCase()]}))
    opened.value=true
  }catch(e:any){message.error(e.message)}finally{busy.value=false}
}
onBeforeUnmount(release)
</script>
<template>
  <span class="readonly-file"><span>{{file.fileName || '附件'}}</span><a-button v-if="address()" type="link" size="small" @click="download">下载</a-button><a-button v-if="address()?.startsWith('/api/') && /\.(pdf|png|jpg|jpeg|gif|webp|txt)$/i.test(file.fileName||'')" type="link" size="small" :loading="busy" @click="preview">预览</a-button><small v-if="!address()">暂无下载地址</small></span>
  <a-modal v-model:open="opened" title="附件预览" :footer="null" width="85%" @after-close="release"><iframe v-if="kind==='pdf'" :src="url" title="PDF 附件预览" style="width:100%;height:70vh;border:0"/><pre v-else-if="kind==='text'" style="white-space:pre-wrap">{{text}}</pre><img v-else :src="url" alt="附件预览" style="max-width:100%;max-height:70vh"/></a-modal>
</template>
<style scoped>.readonly-file{display:inline-flex;flex-wrap:wrap;align-items:center;gap:4px;overflow-wrap:anywhere}.readonly-file small{color:#8592a3}</style>
