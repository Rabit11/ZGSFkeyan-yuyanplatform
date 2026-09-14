<script setup lang="ts">
import type { SupplementField } from '@/api/supplement'
import { supplementBusinessStatus } from '@/api/supplement'
import { LEVEL_TEXT } from '@/api/types'
defineProps<{field:SupplementField;modelValue:any;disabled?:boolean}>()
const emit=defineEmits(['update:modelValue'])
</script>
<template>
  <a-input v-if="field.readonly&&field.key==='status'" :value="supplementBusinessStatus(modelValue)" disabled />
  <a-input v-else-if="field.readonly&&field.key==='levelCode'" :value="LEVEL_TEXT[modelValue]||modelValue" disabled />
  <a-select v-else-if="field.options?.length" :value="modelValue" :disabled="disabled" allow-clear style="width:100%" :options="field.options.map(o=>typeof o==='string'?{label:o,value:o}:o)" @change="emit('update:modelValue',$event)" />
  <a-input-number v-else-if="field.type==='number'" :value="modelValue" :disabled="disabled" style="width:100%" @change="emit('update:modelValue',$event)" />
  <a-textarea v-else-if="field.type==='textarea'" :value="modelValue" :disabled="disabled" :auto-size="{minRows:2,maxRows:5}" @update:value="emit('update:modelValue',$event)" />
  <input v-else-if="field.type==='date'" class="date-input" type="date" :value="modelValue" :disabled="disabled" @input="emit('update:modelValue',($event.target as HTMLInputElement).value)" />
  <a-input v-else :value="modelValue" :disabled="disabled" @update:value="emit('update:modelValue',$event)" />
</template>
<style scoped>.date-input{width:100%;height:32px;border:1px solid #d9d9d9;border-radius:6px;padding:4px 10px}.date-input:disabled{background:#f5f5f5;color:#666}</style>
