<script setup lang="ts">
import { computed, type PropType } from 'vue'
import { useRouter } from 'vue-router'
import { WORK_ACTION_LABEL, type WorkAction, type WorkDutyCode } from '@/constants/workDuty'
import { useWorkDuty } from '@/composables/useWorkDuty'

const props = defineProps({
  code: { type: String as PropType<WorkDutyCode>, required: true },
  project: { type: Object, default: null },
})

const router = useRouter()
const { duty, hint } = useWorkDuty(
  computed(() => props.code),
  computed(() => props.project),
)

const actions: WorkAction[] = ['fill', 'submit', 'audit', 'view', 'edit']

function goMatrix() {
  const q: Record<string, string> = { duty: props.code }
  const id = props.project?.id || props.project?.projectId
  if (id) q.projectId = String(id)
  router.push({ path: '/system/work-duty', query: q })
}
</script>

<template>
  <div class="duty-bar">
    <div class="duty-bar__head">
      <span class="duty-bar__title">办理定责 · {{ duty.def.title }}</span>
      <span class="duty-bar__hint">{{ hint }}</span>
      <a-button type="link" size="small" @click="goMatrix">全部工作定责</a-button>
    </div>
    <div class="duty-bar__grid">
      <div v-for="act in actions" :key="act" class="duty-cell" :class="{ mine: duty[act].can && act !== 'view' }">
        <div class="duty-cell__k">{{ WORK_ACTION_LABEL[act] }}</div>
        <div class="duty-cell__v" :title="duty[act].labels">{{ duty[act].labels }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.duty-bar {
  background: #fff;
  border: 1px solid var(--zgsf-border);
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.duty-bar__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.duty-bar__title {
  font-weight: 600;
  color: var(--zgsf-text);
}
.duty-bar__hint {
  font-size: 12px;
  color: var(--zgsf-text-secondary);
  flex: 1;
}
.duty-bar__grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}
.duty-cell {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 8px 10px;
  min-height: 56px;
}
.duty-cell.mine {
  background: #e8f1ff;
  outline: 1px solid #91c3ff;
}
.duty-cell__k {
  font-size: 12px;
  color: var(--zgsf-brand);
  font-weight: 600;
  margin-bottom: 4px;
}
.duty-cell__v {
  font-size: 12px;
  color: var(--zgsf-text);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
@media (max-width: 900px) {
  .duty-bar__grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
