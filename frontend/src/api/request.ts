import axios, { type AxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'
import type { Res } from './types'
import { mockRequest } from '../mock'
import { repairMojibake, repairMojibakeDeep } from '@/utils/encoding'

const useMock = import.meta.env.VITE_USE_MOCK === 'true'
const apiBase = import.meta.env.VITE_API_BASE || '/api'

try {
  message.config({ maxCount: 2, duration: 2.5 })
} catch {
  /* ignore */
}

const service = axios.create({
  baseURL: apiBase,
  timeout: 30000,
})

/** 避免 baseURL 与接口路径同时包含 /api 时拼成 /api/api/... */
function normalizeApiUrl(url: string) {
  let basePath = apiBase
  try {
    basePath = new URL(apiBase, location.origin).pathname
  } catch {
    /* 保留相对 baseURL 原值 */
  }
  basePath = basePath.replace(/\/+$/, '')
  if (basePath && (url === basePath || url.startsWith(`${basePath}/`))) {
    return url.slice(basePath.length) || '/'
  }
  return url
}

/** 会话失效处理防抖：避免并行请求刷屏 */
let authHandling = false
let authHandledAt = 0

function isLoginPage() {
  return location.hash.startsWith('#/login') || location.hash === '#/' || location.hash === ''
}

function isAuthApi(url: string) {
  return url.includes('/auth/login') || url.includes('/auth/logout')
}

function clearSessionStorage() {
  ;[
    'rpm_token',
    'rpm_uid',
    'rpm_name',
    'rpm_emp',
    'rpm_roles',
    'rpm_org',
    'rpm_org_name',
    'rpm_identity',
    'rpm_identity_code',
    'rpm_data_scope',
  ].forEach((k) => localStorage.removeItem(k))
}

function handleAuthExpired() {
  const now = Date.now()
  const onLogin = isLoginPage()
  // 已在登录页：只清会话并吞掉提示，不再弹窗/跳转
  if (onLogin) {
    clearSessionStorage()
    try {
      message.destroy()
    } catch {
      /* ignore */
    }
    return
  }
  if (authHandling || now - authHandledAt < 4000) return
  authHandling = true
  authHandledAt = now
  clearSessionStorage()
  try {
    message.destroy()
  } catch {
    /* ignore */
  }
  message.warning('登录已失效，请重新登录')
  location.hash = '#/login'
  setTimeout(() => {
    authHandling = false
  }, 2000)
}

service.interceptors.request.use((config) => {
  const url = String(config.url || '')
  // 登录/登出不要附带旧 Authorization，避免干扰
  if (isAuthApi(url)) {
    if (config.headers) {
      delete (config.headers as any).Authorization
      delete (config.headers as any).authorization
    }
    return config
  }
  const token = localStorage.getItem('rpm_token')
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

service.interceptors.response.use(
  (resp) => {
    const body = repairMojibakeDeep(resp.data)
    // 后端业务失败常仍为 HTTP 200 + { code!=0, msg, data:null }，须转成 reject，避免上层读 data.token 崩掉
    if (body && typeof body === 'object' && 'code' in body && Number((body as Res<unknown>).code) !== 0) {
      const msg = repairMojibake((body as Res<unknown>).msg || '请求失败') || '请求失败'
      const code = Number((body as Res<unknown>).code)
      const url = String(resp?.config?.url || '')
      const authApi = isAuthApi(url)
      const sessionExpired =
        !authApi &&
        (code === 401 ||
          /登录已失效|登录已过期|无效的令牌|Token|token|未登录/.test(String(msg)))
      if (sessionExpired) {
        handleAuthExpired()
        const error = new Error('SESSION_EXPIRED') as Error & { status?: number; silent?: boolean }
        error.status = code
        error.silent = true
        return Promise.reject(error)
      }
      const error = new Error(msg) as Error & { status?: number; silent?: boolean }
      error.status = code
      error.silent = false
      return Promise.reject(error)
    }
    return body
  },
  (err) => {
    const status = err?.response?.status
    const url = String(err?.config?.url || '')
    const bodyMsg = repairMojibake(err?.response?.data?.msg || err?.response?.data?.message || '')
    let msg = bodyMsg

    if (!msg) {
      if (status === 401) msg = '未登录或登录已过期'
      else if (status === 403) msg = '无权访问或登录已失效，请重新登录后再试'
      else if (status === 422) msg = '存在校验问题，请修正后重试或勾选强制入库'
      else if (status === 502) msg = '后端服务暂不可用，请稍后重试'
      else msg = err.message || '网络异常'
    }

    const authApi = isAuthApi(url)
    const sessionExpired =
      !authApi &&
      (status === 401 ||
        /登录已失效|登录已过期|无效的令牌|Token|token|未登录/.test(String(msg)))

    if (sessionExpired) {
      handleAuthExpired()
      const error = new Error('SESSION_EXPIRED') as Error & { status?: number; silent?: boolean }
      error.status = status
      error.silent = true
      return Promise.reject(error)
    }

    const error = new Error(msg) as Error & { status?: number; silent?: boolean }
    error.status = status
    error.silent = false
    return Promise.reject(error)
  },
)

export function isSilentAuthError(e: any) {
  return !!(e?.silent || e?.message === 'SESSION_EXPIRED' || e?.status === 401)
}

export interface RequestOptions {
  url: string
  method?: 'get' | 'post' | 'put' | 'delete'
  params?: any
  data?: any
  timeout?: number
}

async function request<T = any>(opts: RequestOptions): Promise<Res<T>> {
  const raw = useMock
    ? await mockRequest<T>(opts)
    : await service.request<any, Res<T>>({
        url: normalizeApiUrl(opts.url),
        method: opts.method || 'get',
        params: opts.params,
        data: opts.data,
        timeout: opts.timeout,
      })
  const body = repairMojibakeDeep(raw) as Res<T>
  // mock 路径不走 axios 拦截器，这里统一处理业务 code
  if (useMock && body && typeof body === 'object' && 'code' in body && Number(body.code) !== 0) {
    throw new Error(repairMojibake(body.msg || '请求失败') || '请求失败')
  }
  return body
}

export default {
  get: <T = any>(url: string, params?: any, timeout?: number) =>
    request<T>({ url, method: 'get', params, timeout }),
  post: <T = any>(url: string, data?: any, timeout?: number) =>
    request<T>({ url, method: 'post', data, timeout }),
  put: <T = any>(url: string, data?: any) =>
    request<T>({ url, method: 'put', data }),
  del: <T = any>(url: string, params?: any) =>
    request<T>({ url, method: 'delete', params }),
}
