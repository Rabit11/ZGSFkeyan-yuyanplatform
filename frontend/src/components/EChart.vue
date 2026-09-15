<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{ option: any; height?: number }>()
const emit = defineEmits<{ chartClick: [payload: any] }>()
const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

function onClick(params: any) {
  emit('chartClick', params)
}

function render() {
  if (!el.value) return
  if (!chart) {
    chart = echarts.init(el.value)
    chart.on('click', onClick)
  }
  chart.setOption(props.option || {}, true)
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})
watch(() => props.option, render, { deep: true })
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.off('click', onClick)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="el" class="echart-canvas" :style="{ height: `${props.height || 300}px` }"></div>
</template>

<style scoped>
.echart-canvas {
  width: 100%;
  min-width: 0;
}
</style>
