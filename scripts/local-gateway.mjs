// Local production-build gateway. No npm dependencies.
import http from 'node:http'
import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(fileURLToPath(new URL('../frontend/dist/', import.meta.url)))
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.woff2': 'font/woff2' }
const server = http.createServer(async (req, res) => {
  if (req.url === '/api' || req.url.startsWith('/api/')) {
    const port = 8080;
    const upstreamPath = req.url;
    const upstream = http.request({ hostname: '127.0.0.1', port, path: upstreamPath, method: req.method,
      headers: { ...req.headers, host: `127.0.0.1:${port}` } }, response => {
      res.writeHead(response.statusCode, response.headers)
      response.pipe(res)
    })
    upstream.setTimeout(120000, () => upstream.destroy(new Error('Backend timeout')))
    upstream.on('error', () => {
      if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ code: 502, msg: '本地后端暂不可用' }))
    })
    req.on('aborted', () => upstream.destroy())
    req.pipe(upstream)
    return
  }
  if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405).end(); return }
  try {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname)
    let file = path.resolve(root, '.' + pathname)
    if (file !== root && !file.startsWith(root + path.sep)) { res.writeHead(403).end(); return }
    let info = await stat(file).catch(() => null)
    if (!info?.isFile()) {
      if (path.extname(pathname)) { res.writeHead(404).end(); return }
      file = path.join(root, 'index.html'); info = await stat(file)
    }
    res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream',
      'Content-Length': info.size, 'Cache-Control': path.basename(file) === 'index.html' ? 'no-cache' : 'public, max-age=3600' })
    if (req.method === 'HEAD') res.end()
    else createReadStream(file).on('error', () => res.destroy()).pipe(res)
  } catch { res.writeHead(400).end() }
})
server.listen(6006, '127.0.0.1', () => console.log('RPM: http://127.0.0.1:6006'))
process.on('SIGTERM', () => server.close(() => process.exit(0)))
