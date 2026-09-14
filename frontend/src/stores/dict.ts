import { defineStore } from 'pinia'
import { dictApi } from '@/api/modules'
import type { ProjChannel, SysDict } from '@/api/types'

interface DictState {
  dicts: Record<string, SysDict[]>
  channels: ProjChannel[]
}

export const useDictStore = defineStore('dict', {
  state: (): DictState => ({ dicts: {}, channels: [] }),
  actions: {
    async load(type: string) {
      if (this.dicts[type]) return this.dicts[type]
      const res = await dictApi.byType(type)
      this.dicts[type] = (res.data as unknown as SysDict[]) || []
      return this.dicts[type]
    },
    async loadChannels() {
      if (this.channels.length) return this.channels
      const res = await dictApi.channels()
      this.channels = (res.data as unknown as ProjChannel[]) || []
      return this.channels
    },
    options(type: string) {
      return (this.dicts[type] || []).map((d) => ({ label: d.dictName, value: d.dictCode }))
    },
    label(type: string, code?: string) {
      if (!code) return '-'
      return this.dicts[type]?.find((d) => d.dictCode === code)?.dictName || code
    },
  },
})
