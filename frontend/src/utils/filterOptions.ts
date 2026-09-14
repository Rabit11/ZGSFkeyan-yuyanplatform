/** 列表筛选下拉的「全部」项：选中后等价于未筛选 */
export const ALL_FILTER_OPTION = { label: '全部', value: '' as const }

export function withAllOption<T extends { label: string; value?: unknown }>(options: T[] = []) {
  return [ALL_FILTER_OPTION, ...options]
}
