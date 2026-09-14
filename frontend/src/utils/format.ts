import dayjs from 'dayjs'

function toNum(v?: number | string | null) {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

/** 金额格式化（万元），默认保留 2 位小数并带千分位 */
export function fmtAmount(v?: number | string, digits = 2): string {
  const n = toNum(v)
  if (n === null) return '-'
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
}

/**
 * 万元汇总换算为亿元展示。
 * 1 亿元 = 10000 万元，默认 4 位小数，与万元级精度对齐，避免大额被两位小数吃掉。
 */
export function fmtYi(wan?: number | string, digits = 4): string {
  const n = toNum(wan)
  if (n === null) return '-'
  return (n / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
}

/** 坐标轴金额：最多 2 位小数，大额带千分位 */
export function fmtAxisAmount(v: number): string {
  const n = Number(v)
  if (!Number.isFinite(n)) return ''
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

/** 日期格式化 */
export function fmtDate(v?: string, pattern = 'YYYY-MM-DD'): string {
  if (!v) return '-'
  const d = dayjs(v)
  return d.isValid() ? d.format(pattern) : '-'
}

/** 相对今天的天数文案 */
export function dueText(dueDate?: string): string {
  if (!dueDate) return '-'
  const days = dayjs(dueDate).startOf('day').diff(dayjs().startOf('day'), 'day')
  if (days < 0) return `超期 ${Math.abs(days)} 天`
  if (days === 0) return '今日到期'
  return `剩余 ${days} 天`
}

/** 数字百分比 */
export function percent(a?: number, b?: number): string {
  if (!a || !b) return '0.0%'
  return `${((a / b) * 100).toFixed(1)}%`
}
