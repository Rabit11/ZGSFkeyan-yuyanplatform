import { defineStore } from 'pinia'
import { authApi } from '@/api/modules'
import type { LoginResult, RoleCode } from '@/api/types'
import { canViewVisualBoard } from '@/constants/permission'
import { repairMojibake } from '@/utils/encoding'

interface UserState {
  token: string
  userId: number
  realName: string
  employeeNo?: string
  roles: RoleCode[]
  orgId?: number
  orgName?: string
  identity?: string
  identityCode?: string
  dataScope?: string
}

function readCachedName(key: string) {
  const raw = localStorage.getItem(key) || ''
  const fixed = repairMojibake(raw)
  if (fixed && fixed !== raw) localStorage.setItem(key, fixed)
  return fixed
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem('rpm_token') || '',
    userId: Number(localStorage.getItem('rpm_uid') || 0),
    realName: readCachedName('rpm_name'),
    employeeNo: localStorage.getItem('rpm_emp') || '',
    roles: (JSON.parse(localStorage.getItem('rpm_roles') || '[]') as RoleCode[]) || [],
    orgId: Number(localStorage.getItem('rpm_org') || 0),
    orgName: readCachedName('rpm_org_name'),
    identity: readCachedName('rpm_identity'),
    identityCode: localStorage.getItem('rpm_identity_code') || '',
    dataScope: localStorage.getItem('rpm_data_scope') || '',
  }),
  getters: {
    isAdmin: (s) => s.roles.includes('ADMIN'),
    isManagement: (s) => s.roles.includes('MANAGEMENT') || s.roles.includes('ADMIN'),
    isFinance: (s) => s.roles.includes('FINANCE'),
    canViewBoard: (s) =>
      canViewVisualBoard({
        roles: s.roles,
        identityCode: s.identityCode,
        identity: s.identity,
        dataScope: s.dataScope,
      }),
  },
  actions: {
    async login(username: string, password: string) {
      const res = await authApi.login({ username, password })
      const d = res?.data as unknown as LoginResult | undefined
      if (!d?.token) {
        throw new Error((res as any)?.msg || '登录失败：未返回有效令牌，请检查账号密码或稍后重试')
      }
      this.setUser(d)
      return d
    },
    setUser(d: LoginResult) {
      if (!d?.token) {
        throw new Error('登录结果无效：缺少 token')
      }
      this.token = d.token
      this.userId = d.userId
      this.realName = repairMojibake(d.realName)
      this.employeeNo = d.employeeNo || d.username || ''
      this.roles = d.roles || []
      this.orgId = d.orgId
      this.orgName = repairMojibake(d.orgName || '')
      this.identity = repairMojibake(d.identity || '')
      this.identityCode = d.identityCode
      this.dataScope = d.dataScope
      localStorage.setItem('rpm_token', d.token)
      localStorage.setItem('rpm_uid', String(d.userId))
      localStorage.setItem('rpm_name', this.realName)
      localStorage.setItem('rpm_emp', this.employeeNo || '')
      localStorage.setItem('rpm_roles', JSON.stringify(d.roles || []))
      localStorage.setItem('rpm_org', String(d.orgId || 0))
      localStorage.setItem('rpm_org_name', this.orgName || '')
      localStorage.setItem('rpm_identity', this.identity || '')
      localStorage.setItem('rpm_identity_code', d.identityCode || '')
      localStorage.setItem('rpm_data_scope', d.dataScope || '')
    },
    /** 用接口最新姓名覆盖本地缓存，避免历史 Latin-1 乱码残留在顶栏 */
    async hydrateProfile() {
      if (!this.token) return
      try {
        const res = await authApi.profile()
        const d = (res.data || {}) as Partial<LoginResult>
        if (d.realName) {
          this.realName = repairMojibake(d.realName)
          localStorage.setItem('rpm_name', this.realName)
        }
        if (d.orgName) {
          this.orgName = repairMojibake(d.orgName)
          localStorage.setItem('rpm_org_name', this.orgName)
        }
        if (d.identity) {
          this.identity = repairMojibake(d.identity)
          localStorage.setItem('rpm_identity', this.identity)
        }
        if (d.roles?.length) {
          this.roles = d.roles
          localStorage.setItem('rpm_roles', JSON.stringify(d.roles))
        }
        if (d.employeeNo) {
          this.employeeNo = d.employeeNo
          localStorage.setItem('rpm_emp', d.employeeNo)
        }
        if (d.identityCode) {
          this.identityCode = d.identityCode
          localStorage.setItem('rpm_identity_code', d.identityCode)
        }
        if (d.dataScope) {
          this.dataScope = d.dataScope
          localStorage.setItem('rpm_data_scope', d.dataScope)
        }
      } catch {
        this.realName = repairMojibake(this.realName)
        this.orgName = repairMojibake(this.orgName || '')
        this.identity = repairMojibake(this.identity || '')
      }
    },
    logout() {
      this.token = ''
      this.roles = []
      ;[
        'rpm_token',
        'rpm_uid',
        'rpm_emp',
        'rpm_name',
        'rpm_roles',
        'rpm_org',
        'rpm_org_name',
        'rpm_identity',
        'rpm_identity_code',
        'rpm_data_scope',
      ].forEach((k) => localStorage.removeItem(k))
    },
  },
})
