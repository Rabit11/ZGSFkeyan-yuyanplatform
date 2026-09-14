/** 通过平台会话下载附件；普通超链接不会携带 JWT。 */
export async function downloadAuthenticatedFile(fileUrl?: string, fileName?: string) {
  if (!fileUrl) throw new Error('附件地址不存在')
  if (!fileUrl.startsWith('/api/')) {
    window.open(fileUrl, '_blank', 'noopener,noreferrer')
    return
  }

  const token = localStorage.getItem('rpm_token')
  const response = await fetch(fileUrl, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    throw new Error(response.status === 401 ? '登录已失效，请重新登录' : `附件下载失败（${response.status}）`)
  }
  // 后端对业务错误也返回 200 + JSON（如对象不存在），要先识别，不能当成文件保存
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    let msg = '附件不存在或已失效'
    try {
      const body = await response.json()
      if (body && typeof body === 'object' && body.code !== 0) msg = body.msg || msg
      else if (body && body.code === 0) msg = '附件服务返回了非文件内容'
    } catch {
      /* ignore */
    }
    throw new Error(msg)
  }

  const objectUrl = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName || '附件'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 30_000)
}

