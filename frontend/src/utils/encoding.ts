/**
 * 修复「UTF-8 中文被当成 Latin-1 展示」的乱码（如 ç³»ç»Ÿç®¡理员 → 系统管理员）。
 * 已是正常汉字的字符串不会改动。
 */
const MOJI_HINT = /[à-ÿÀ-ßæçœÿÃÂ]/

export function repairMojibake(input: unknown): string {
  const s = input == null ? '' : String(input)
  if (!s || !MOJI_HINT.test(s)) return s
  try {
    const bytes = new Uint8Array(s.length)
    for (let i = 0; i < s.length; i++) {
      const code = s.charCodeAt(i)
      if (code > 255) return s
      bytes[i] = code
    }
    const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
    if (/[\u4e00-\u9fff]/.test(text)) return text
  } catch {
    /* keep original */
  }
  return s
}

export function repairMojibakeDeep<T>(value: T, depth = 0): T {
  if (value == null || depth > 8) return value
  if (typeof value === 'string') return repairMojibake(value) as T
  if (Array.isArray(value)) {
    return value.map((item) => repairMojibakeDeep(item, depth + 1)) as T
  }
  if (typeof value === 'object') {
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      out[k] = repairMojibakeDeep(v, depth + 1)
    }
    return out as T
  }
  return value
}
