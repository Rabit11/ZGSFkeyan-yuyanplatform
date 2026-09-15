<script setup lang="ts">
import { computed } from 'vue'
import { peopleMapFrom, type DeclareFlowStep, type SwimPeopleKey } from '@/utils/declareFlow'

const props = defineProps<{
  nodes: DeclareFlowStep[]
  title?: string
}>()

const emit = defineEmits<{ close: [] }>()

const people = computed(() => peopleMapFrom(props.nodes || []))

function withWho(lines: string[], key: SwimPeopleKey) {
  const name = people.value[key]
  return name ? [...lines, name] : lines
}

function esc(v: string) {
  return String(v ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function tspans(cx: number, cy: number, lines: string[], size: number) {
  const lh = size + 4
  const y0 = cy - ((lines.length - 1) * lh) / 2 + 1
  return lines
    .map((line, i) => `<tspan x="${cx}" y="${y0 + i * lh}">${esc(line)}</tspan>`)
    .join('')
}

const svgHtml = computed(() => {
  const id = 'dfs'
  const L = [210, 490, 770, 1050]
  const roles = ['项目团队', '责任总师', '财务团队', '管理团队']
  const gx = [70, 350, 630, 910, 1190]
  const dw = 98
  const dh = 58

  const diamond = (cx: number, cy: number, lines: string[]) => {
    const pts = `${cx},${cy - dh} ${cx + dw},${cy} ${cx},${cy + dh} ${cx - dw},${cy}`
    return `<g>
      <polygon points="${pts}" fill="#FFF7E6" stroke="#D48806" stroke-width="1.5"/>
      <text text-anchor="middle" font-size="11" fill="#1F1F1F" font-weight="600">${tspans(cx, cy, lines, 11)}</text>
    </g>`
  }
  const rect = (cx: number, cy: number, w: number, h: number, lines: string[]) => `<g>
    <rect x="${cx - w / 2}" y="${cy - h / 2}" width="${w}" height="${h}" rx="4" fill="#E6F4FF" stroke="#0064EF" stroke-width="1.5"/>
    <text text-anchor="middle" font-size="12" fill="#0048A0" font-weight="600">${tspans(cx, cy, lines, 12)}</text>
  </g>`
  const lab = (x: number, y: number, t: string, fill = '#0064EF') =>
    `<text x="${x}" y="${y}" font-size="11" fill="${fill}" font-weight="600">${esc(t)}</text>`
  const arrowP = (d: string, back?: boolean) =>
    `<path d="${d}" fill="none" stroke="${back ? '#8C8C8C' : '#262626'}" stroke-width="${back ? 1.15 : 1.5}" ${
      back ? 'stroke-dasharray="5 4"' : ''
    } marker-end="url(#${id}-${back ? 'bk' : 'fw'})"/>`

  const n1 = { x: L[0], y: 148 }
  const n2 = { x: L[0], y: 276 }
  const n3 = { x: L[0], y: 454 }
  const n4 = { x: L[1], y: 454 }
  const n5 = { x: L[2], y: 454 }
  const n6 = { x: L[3], y: 454 }
  const n7 = { x: L[3], y: 600 }
  const n8 = { x: L[1], y: 768 }
  const n9 = { x: L[3], y: 768 }
  const n10 = { x: L[3], y: 888 }
  const rejects = [n2, n3, n4, n5, n6, n7, n8, n9]
  const backX = 88
  const splitY = 688

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 940" role="img" aria-label="项目申报审批核心审签流" style="font-family:'Microsoft YaHei','PingFang SC',sans-serif">
    <defs>
      <marker id="${id}-fw" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="#262626"/></marker>
      <marker id="${id}-bk" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="#8C8C8C"/></marker>
    </defs>
    <rect x="0" y="0" width="1280" height="940" fill="#FFFFFF"/>
    <text x="630" y="26" text-anchor="middle" font-size="15" font-weight="600" fill="#1F1F1F">${esc(
      props.title || '项目申报审批流程核心审签流，以 MJKY/04 专项接续国家级项目为例',
    )}</text>
    <rect x="70" y="40" width="1120" height="36" fill="#F5F7FA"/>
    <rect x="70" y="76" width="1120" height="580" fill="#F4F9EE"/>
    <rect x="70" y="656" width="1120" height="256" fill="#FFFFFF"/>
    ${gx.map((x) => `<line x1="${x}" y1="40" x2="${x}" y2="912" stroke="#91CAFF" stroke-dasharray="6 4"/>`).join('')}
    <line x1="70" y1="76" x2="1190" y2="76" stroke="#91CAFF" stroke-dasharray="6 4"/>
    <line x1="70" y1="656" x2="1190" y2="656" stroke="#0064EF" stroke-dasharray="7 5" stroke-width="1.4"/>
    <line x1="70" y1="912" x2="1190" y2="912" stroke="#91CAFF" stroke-dasharray="6 4"/>
    ${roles.map((r, i) => `<text x="${L[i]}" y="64" text-anchor="middle" font-size="14" font-weight="600" fill="#0048A0">${esc(r)}</text>`).join('')}
    <text transform="translate(1238 366) rotate(90)" text-anchor="middle" font-size="14" font-weight="600" fill="#0048A0">二级单位</text>
    <text transform="translate(1238 784) rotate(90)" text-anchor="middle" font-size="14" font-weight="600" fill="#0048A0">总部</text>
    ${rect(n1.x, n1.y, 196, 56, withWho(['项目联系人', '发起审批 + 上传材料'], 'contact'))}
    ${diamond(n2.x, n2.y, withWho(['项目负责人', '审核项目材料'], 'owner'))}
    ${diamond(n3.x, n3.y, withWho(['项目承担部门负责人', '审核项目材料'], 'deptHead'))}
    ${diamond(n4.x, n4.y, withWho(['二级总师', '技术方案把关'], 'chief2'))}
    ${diamond(n5.x, n5.y, withWho(['单位财务部门负责人', '审核申报预算'], 'finHead'))}
    ${diamond(n6.x, n6.y, withWho(['单位科技部门负责人', '审核合规性'], 'sciHead'))}
    ${diamond(n7.x, n7.y, withWho(['单位分管领导', '单位综合初审'], 'unitLeader'))}
    ${diamond(n8.x, n8.y, withWho(['一级总师', '公司级技术统筹'], 'chief1'))}
    ${diamond(n9.x, n9.y, withWho(['总部科研项目处', '总部归口审批'], 'hqOffice'))}
    ${rect(n10.x, n10.y, 220, 50, ['线上流程结束，启动线下报批'])}
    ${arrowP(`M${n1.x} ${n1.y + 28} V${n2.y - dh}`)}
    ${lab(n1.x + 10, (n1.y + n2.y) / 2 + 4, '通过')}
    ${arrowP(`M${n2.x} ${n2.y + dh} V${n3.y - dh}`)}
    ${lab(n2.x + 10, (n2.y + n3.y) / 2 + 4, '通过')}
    ${arrowP(`M${n3.x + dw} ${n3.y} H${n4.x - dw}`)}
    ${lab((n3.x + n4.x) / 2 - 12, n3.y - 10, '通过')}
    ${arrowP(`M${n4.x + dw} ${n4.y} H${n5.x - dw}`)}
    ${lab((n4.x + n5.x) / 2 - 12, n4.y - 10, '通过')}
    ${arrowP(`M${n5.x + dw} ${n5.y} H${n6.x - dw}`)}
    ${lab((n5.x + n6.x) / 2 - 12, n5.y - 10, '通过')}
    ${arrowP(`M${n6.x} ${n6.y + dh} V${n7.y - dh}`)}
    ${lab(n6.x + 10, (n6.y + n7.y) / 2 + 4, '通过')}
    ${arrowP(`M${n7.x} ${n7.y + dh} V${splitY} H${n8.x} V${n8.y - dh}`)}
    ${lab(n7.x - 28, splitY - 8, '通过')}
    ${arrowP(`M${n8.x + dw} ${n8.y} H${n9.x - dw}`)}
    ${lab((n8.x + n9.x) / 2 - 12, n8.y - 10, '通过')}
    ${arrowP(`M${n9.x} ${n9.y + dh} V${n10.y - 25}`)}
    ${lab(n9.x + 10, (n9.y + n10.y) / 2, '通过')}
    ${rejects.map((n) => arrowP(`M${n.x - dw} ${n.y} H${backX} V${n1.y} H${n1.x - 98}`, true)).join('')}
    ${lab(backX + 8, n2.y - 12, '驳回', '#8C8C8C')}
    ${lab(backX + 8, n3.y - 12, '驳回', '#8C8C8C')}
    ${lab(backX + 8, n7.y - 12, '驳回', '#8C8C8C')}
    ${lab(backX + 8, n8.y - 12, '驳回', '#8C8C8C')}
  </svg>`
})
</script>

<template>
  <div class="swim-mask" @click.self="emit('close')">
    <div class="swim-inner">
      <div class="swim-bar">
        <h3>{{ title || '项目申报审批流程核心审签流，以 MJKY/04 专项接续国家级项目为例' }}</h3>
        <a-button @click="emit('close')">关闭</a-button>
      </div>
      <div class="swim-wrap" v-html="svgHtml" />
    </div>
  </div>
</template>

<style scoped>
.swim-mask {
  position: fixed;
  inset: 0;
  z-index: 1100;
  background: rgba(15, 23, 42, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  overflow: auto;
}
.swim-inner {
  width: min(1280px, 100%);
  background: var(--zgsf-card);
  border-radius: var(--zgsf-radius-card);
  padding: 16px 16px 20px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.2);
}
.swim-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  gap: 12px;
}
.swim-bar h3 {
  margin: 0;
  font-size: 14px;
  color: var(--zgsf-text);
  line-height: 22px;
  font-weight: 600;
}
.swim-wrap {
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius);
  overflow: auto;
  background: var(--zgsf-card);
}
.swim-wrap :deep(svg) {
  width: 100%;
  height: auto;
  display: block;
  min-width: 980px;
}
</style>
