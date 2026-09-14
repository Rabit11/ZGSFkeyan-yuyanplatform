<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { categories, majorCategories } from './policy';
const props = defineProps<{ open: boolean; targets: any[]; items: any[] }>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'confirm', tokens: string[]): void }>();
const selectedCategories = ref<string[]>([]), selected = ref<string[]>([]), search = ref(''), error = ref('');
const token = (t: any) => `${t.key}:${t.id}`;
const choices = computed(() => Object.keys(categories).filter(c => props.targets.some(t => t.category === c)));
const groups = computed(() => selectedCategories.value.map(category => ({ category, targets: props.targets.filter(t => t.category === category && `${t.label} ${categories[category]}`.toLowerCase().includes(search.value.trim().toLowerCase())) })));
const selectedTargets = computed(() => selected.value.map(key => props.targets.find(t => token(t) === key) || { key, label: props.items.find(i => `${i.targetKey}:${i.targetId}` === key)?.targetLabel || key }));
watch(() => props.open, open => { if (open) {
  selected.value = props.items.map(i => `${i.targetKey}:${i.targetId}`);
  selectedCategories.value = [...new Set(props.items.map(i => i.category))]; search.value = ''; error.value = '';
} });
function categoriesChanged(values: any[]) {
  selectedCategories.value = values; error.value = '';
  selected.value = selected.value.filter(key => props.targets.some(t => token(t) === key && values.includes(t.category)));
}
function toggle(tokens: string[], checked: boolean) {
  const next = checked ? [...new Set([...selected.value, ...tokens])] : selected.value.filter(t => !tokens.includes(t));
  if (next.length > 50) { error.value = '一份申请最多选择 50 个变更项，请减少选择。'; return; }
  selected.value = next; error.value = '';
}
function confirm() { if (!selected.value.length && !props.items.length) { error.value = '请至少勾选一个变更对象。'; return; } emit('confirm', [...selected.value]); }
</script>
<template>
  <a-modal :open="open" title="多选变更类别与对象" width="min(1000px, 100vw)" :style="{ top: '24px' }" :body-style="{ maxHeight: 'calc(100vh - 180px)', overflowY: 'auto' }" :mask-closable="false" :ok-text="!selected.length && items.length ? '清空已选对象' : `确认选择 ${selected.length} 项`" cancel-text="取消" @ok="confirm" @cancel="emit('update:open', false)">
    <div class="change-object-picker">
      <a-alert v-if="error" :message="error" type="error" show-icon />
      <h3>1. 勾选变更类别 <small>可同时选择多个类别</small></h3>
      <a-checkbox-group :value="selectedCategories" @change="categoriesChanged" class="category-checkboxes" aria-label="多选变更类别">
        <a-checkbox v-for="category in choices" :key="category" :value="category"><b>{{ categories[category] }}</b><small>{{ targets.filter(t => t.category === category).length }} 项</small><span v-if="majorCategories.includes(category)" class="legal-note">需法务</span></a-checkbox>
      </a-checkbox-group>
      <div class="picker-grid">
        <div>
          <h3>2. 勾选具体对象 <small>同类下也可多选</small></h3>
          <a-input v-model:value="search" aria-label="搜索可选变更对象" placeholder="搜索对象名称或字段" allow-clear />
          <p v-if="!selectedCategories.length" class="picker-empty">请先勾选上方类别，再选择对应的具体对象。</p>
          <section v-for="group in groups" :key="group.category" class="object-group">
            <div class="object-group-title"><b>{{ categories[group.category] }}</b><a-checkbox :disabled="!group.targets.length" :checked="!!group.targets.length && group.targets.every(t => selected.includes(token(t)))" :indeterminate="group.targets.some(t => selected.includes(token(t))) && !group.targets.every(t => selected.includes(token(t)))" @change="e => toggle(group.targets.map(token), e.target.checked)">全选当前显示</a-checkbox></div>
            <p v-if="!group.targets.length" class="picker-empty">当前搜索条件下没有匹配对象。</p>
            <label v-for="target in group.targets" :key="token(target)" class="object-choice" :class="{ chosen: selected.includes(token(target)) }">
              <a-checkbox :checked="selected.includes(token(target))" :aria-label="`选择对象：${target.label}`" @change="e => toggle([token(target)], e.target.checked)" />
              <span><b>{{ target.label }}</b><small>当前值：{{ target.beforeDisplay || '—' }}</small></span>
            </label>
          </section>
        </div>
        <aside class="picker-selected" aria-label="待确认对象">
          <h3>已勾选 {{ selected.length }} / 50 项</h3>
          <p>确认后，主页面会为每项展开独立填写区。</p>
          <div v-for="(target, index) in selectedTargets" :key="selected[index]"><span>{{ index + 1 }}. {{ target.label }}</span><a-button type="link" size="small" :aria-label="`取消选择 ${index + 1}`" @click="toggle([selected[index]], false)">移除</a-button></div>
        </aside>
      </div>
      <p class="picker-tip">取消勾选类别会同时取消该类别的对象；点击“取消”不改变申请中原有的选择和填写内容。</p>
    </div>
  </a-modal>
</template>
<style scoped>
.change-object-picker { color:#284358; }
h3 { font-size:15px; margin:18px 0 12px; }
h3 small { margin-left:8px; color:#637b8c; font-weight:400; font-size:12px; }
.category-checkboxes { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; width:100%; }
.category-checkboxes :deep(.ant-checkbox-wrapper) { margin:0; padding:10px; border:1px solid #b8cbd8; background:#f1f6f9; align-items:flex-start; }
.category-checkboxes small { margin-left:6px; color:#526d7e; }
.legal-note { display:inline-block; margin-left:6px; font-size:11px; color:#955709; }
.picker-grid { display:grid; grid-template-columns:minmax(0,1fr) 255px; gap:20px; }
.object-group { border:1px solid #b8cbd8; margin:12px 0; }
.object-group-title { display:flex; flex-wrap:wrap; justify-content:space-between; gap:8px; background:#e8f0f5; padding:10px; }
.object-choice { display:flex; gap:12px; padding:12px; border-top:1px solid #d7e2e9; cursor:pointer; overflow-wrap:anywhere; }
.object-choice.chosen { background:#e9f6fc; box-shadow:inset 3px 0 #0086b3; }
.object-choice small { display:block; color:#657b8a; margin-top:5px; white-space:pre-wrap; }
.picker-selected { background:#edf4f8; padding:12px; margin-top:18px; align-self:start; }
.picker-selected > div { border-top:1px solid #c7d8e2; padding:10px 0; overflow-wrap:anywhere; }
.picker-selected p,.picker-tip,.picker-empty { color:#61798b; font-size:12px; }
.picker-empty { padding:12px; }
@media(max-width:640px) { .category-checkboxes { grid-template-columns:1fr 1fr; } .picker-grid { grid-template-columns:1fr; } }
</style>
