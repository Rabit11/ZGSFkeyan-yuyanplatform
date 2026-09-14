<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { projectApi } from '@/api/modules'
import { shouldNotifyProjectChange } from './projectSelectPolicy'

const props = defineProps<{ modelValue?: number; placeholder?: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [number | undefined]; change: [any] }>()

const list = ref<any[]>([])
const loading = ref(false)
const selectedProject = ref<any>(null)

const selectOptions = computed(() => {
  const rows = [...list.value]
  if (selectedProject.value && !rows.some((x) => Number(x.id) === Number(selectedProject.value.id))) {
    rows.unshift(selectedProject.value)
  }
  return rows
})

function isInternalQaProject(row: any) {
  const no = String(row?.projectNo || '')
  const name = String(row?.name || '')
  return no.startsWith('QA_') || no.startsWith('QA_MS_AUDIT_') || name.startsWith('QA_')
}

async function load() {
  loading.value = true
  try {
    const res = await projectApi.page({ page: 1, size: 200 })
    list.value = (((res.data as any)?.records || []) as any[]).filter((row) => !isInternalQaProject(row))
    await ensureSelectedProject()
    const defaultProject = list.value[0]
    if (shouldNotifyProjectChange({ source: 'default', modelValue: props.modelValue, optionId: defaultProject?.id })) {
      emit('update:modelValue', defaultProject.id)
      emit('change', defaultProject)
    }
  } finally {
    loading.value = false
  }
}

async function ensureSelectedProject() {
  if (!props.modelValue) {
    selectedProject.value = null
    return
  }
  const selected = list.value.find((x) => Number(x.id) === Number(props.modelValue))
  if (selected) {
    selectedProject.value = selected
    return
  }
  try {
    const res = await projectApi.detail(Number(props.modelValue))
    const row = res.data
    selectedProject.value = row && !isInternalQaProject(row) ? row : null
  } catch {
    selectedProject.value = null
  }
}

onMounted(() => {
  load()
})
watch(
  () => props.modelValue,
  async () => {
    await ensureSelectedProject()
  },
)

function handleChange(value: number) {
  if (!shouldNotifyProjectChange({ source: 'user', modelValue: props.modelValue, optionId: value })) return
  emit('update:modelValue', value)
  emit('change', selectOptions.value.find((x) => Number(x.id) === Number(value)))
}
</script>

<template>
  <a-select
    :value="loading && !selectOptions.length ? undefined : modelValue"
    :disabled="disabled"
    :loading="loading"
    show-search
    :placeholder="loading ? '正在加载项目…' : (placeholder || '选择项目')"
    style="width: 420px"
    :filter-option="(i: string, o: any) => String(o.label || '').toLowerCase().includes(String(i).toLowerCase())"
    @change="handleChange"
  >
    <a-select-option v-for="p in selectOptions" :key="p.id" :value="p.id" :label="`${p.projectNo || ''} ${p.name || ''}`">
      {{ p.projectNo }} · {{ p.name }}
    </a-select-option>
  </a-select>
</template>
