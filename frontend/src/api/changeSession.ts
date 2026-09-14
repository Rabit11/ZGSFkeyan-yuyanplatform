import { shallowRef } from 'vue';

const KEY = 'rpm_change_lab_session_v2';
export const labSession = shallowRef<any>(null);
export function restoreChangeSession() {
  try {
    const stored = JSON.parse(sessionStorage.getItem(KEY) || 'null');
    labSession.value = stored?.ownerId === localStorage.getItem('rpm_uid') && stored?.user?.token ? stored.user : null;
  } catch { labSession.value = null; }
}
export function clearChangeSession() {
  sessionStorage.removeItem(KEY);
  labSession.value = null;
}
const formalBase = () => new URL(import.meta.env.VITE_API_BASE || '/api', location.href).pathname.replace(/\/$/, '');
export const changeBase = () => labSession.value ? `${formalBase()}/changes/lab` : formalBase();
export const changeToken = () => labSession.value?.token || localStorage.getItem('rpm_token') || '';

export function changeHeaders(base = changeBase(), token = changeToken()): Record<string,string> {
  if (base.endsWith('/changes/lab')) return {
    Authorization: `Bearer ${localStorage.getItem('rpm_token') || ''}`,
    ...(token ? {'X-Change-Lab-Token':token} : {}),
  };
  return token ? {Authorization:`Bearer ${token}`} : {};
}

/** Always contacts a live backend. Never calls the platform's optional mock adapter. */
export async function liveChangeRequest(path: string, method = 'GET', body?: any, params?: object, base = changeBase(), token = changeToken()) {
  const query = new URLSearchParams();
  for (const [key,value] of Object.entries(params || {})) if(value !== undefined && value !== null) query.set(key,String(value));
  const url = base + path + (query.size ? '?' + query.toString() : '');
  let response: Response;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 30000);
  try {
    response = await fetch(url, {
      method, headers: {...changeHeaders(base, token), ...(body !== undefined && !(body instanceof FormData) ? {'Content-Type':'application/json'} : {})},
      body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch {
    throw new Error('无法连接变更服务，请检查网络后重试；本次操作未被确认成功');
  } finally { clearTimeout(timer); }
  const result = await response.json().catch(() => null);
  if (!response.ok || !result || result.code !== 0) {
    const e: any = new Error(result?.msg || `变更服务返回异常（HTTP ${response.status}），请重试`);
    e.code = result?.code || response.status;
    throw e;
  }
  return result;
}
export async function loginChangeLab(employee = '100012') {
  const base = `${formalBase()}/changes/lab`;
  const result = await liveChangeRequest('/auth/login', 'POST', {username:employee,password:employee}, undefined, base, '');
  if (!result.data?.token) throw new Error('演练登录未返回有效会话');
  const context = await liveChangeRequest('/changes/context', 'GET', undefined, undefined, base, result.data.token);
  if (context.data?.testMode !== true) throw new Error('目标服务未开启隔离测试模式，已拒绝切换');
  // Commit only after both real login and environment verification succeed.
  sessionStorage.setItem(KEY,JSON.stringify({ownerId:localStorage.getItem('rpm_uid'),user:result.data}));
  labSession.value = result.data;
}
