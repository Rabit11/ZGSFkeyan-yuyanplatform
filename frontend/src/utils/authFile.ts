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

  const objectUrl = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName || '附件'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 30_000)
}

