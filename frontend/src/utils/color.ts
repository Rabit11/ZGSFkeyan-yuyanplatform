import dayjs from 'dayjs'
import type { ColorStatus } from '@/api/types'
import { COLOR_WEIGHT } from '@/api/types'

/**
 * 全局四色状态计算规则（优先级：红 > 黄 > 蓝 > 绿）
 * 绿色：节点/项目全部完成，审核通过
 * 蓝色：节点正常推进，距离到期时间 > 30 天
 * 黄色：节点临期，距离到期时间 <= 30 天（风险预警）
 * 红色：节点已超期未完成（逾期告警）
 */
export function calcColor(dueDate?: string, finished?: boolean): ColorStatus {
  if (finished) return 'GREEN'
  if (!dueDate) return 'BLUE'
  const days = dayjs(dueDate).startOf('day').diff(dayjs().startOf('day'), 'day')
  if (days < 0) return 'RED'
  if (days <= 30) return 'YELLOW'
  return 'BLUE'
}

/** 聚合多个状态，取最严重的一个 */
export function mergeColor(list: (ColorStatus | undefined)[]): ColorStatus {
  let result: ColorStatus = 'GREEN'
  list.forEach((c) => {
    if (c && COLOR_WEIGHT[c] > COLOR_WEIGHT[result]) result = c
  })
  return result
}

export const COLOR_HEX: Record<ColorStatus, string> = {
  GREEN: '#52c41a',
  BLUE: '#0A5CAD',
  YELLOW: '#faad14',
  RED: '#f5222d',
}

/** 剩余天数（负数代表超期） */
export function remainDays(dueDate?: string): number | null {
  if (!dueDate) return null
  return dayjs(dueDate).startOf('day').diff(dayjs().startOf('day'), 'day')
}
