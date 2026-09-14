<script setup lang="ts">
import type { ColorStatus } from '@/api/types'

const props = defineProps<{ color?: ColorStatus; text?: string }>()

const META: Record<ColorStatus, { short: string; label: string; cls: string }> = {
  GREEN: { short: '绿', label: '已完成', cls: 'success' },
  BLUE: { short: '蓝', label: '正常推进', cls: 'processing' },
  YELLOW: { short: '黄', label: '临期预警', cls: 'warning' },
  RED: { short: '红', label: '逾期告警', cls: 'error' },
}

function color(): ColorStatus {
  return props.color || 'BLUE'
}
function display() {
  const m = META[color()]
  return props.text || `${m.short}：${m.label}`
}
</script>

<template>
  <a-tag :color="META[color()].cls" style="margin: 0">
    <span class="color-dot" :class="`color-${color()}`" style="background: #fff; opacity: 0.9" />
    {{ display() }}
  </a-tag>
</template>
