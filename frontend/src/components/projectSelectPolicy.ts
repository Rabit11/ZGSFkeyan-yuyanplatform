export type ProjectChangeSource = 'sync' | 'default' | 'user'

export function shouldNotifyProjectChange(input: {
  source: ProjectChangeSource
  modelValue?: number
  optionId?: number
}) {
  if (!input.optionId || input.source === 'sync') return false
  if (input.source === 'default') return !input.modelValue
  return Number(input.optionId) !== Number(input.modelValue)
}
