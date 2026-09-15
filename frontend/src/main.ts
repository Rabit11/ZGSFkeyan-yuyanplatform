import { createApp } from 'vue'

// 发布新版本后，旧标签页按旧文件名加载页面分片会失败；此时自动刷新一次拿到新版本，避免停在登录页
window.addEventListener('vite:preloadError', (event) => {
  event.preventDefault()
  const key = 'rpm_reload_on_preload_error'
  if (sessionStorage.getItem(key) !== '1') {
    sessionStorage.setItem(key, '1')
    window.location.reload()
  }
})
window.addEventListener('load', () => sessionStorage.removeItem('rpm_reload_on_preload_error'))
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(Antd)
app.mount('#app')

void import('./stores/user').then(({ useUserStore }) => {
  void useUserStore().hydrateProfile()
})
