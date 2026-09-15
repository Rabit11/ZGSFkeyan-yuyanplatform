<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AppstoreOutlined,
  BellOutlined,
  CheckCircleOutlined,
  DeploymentUnitOutlined,
  FileAddOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  QuestionCircleOutlined,
  RocketOutlined,
  SearchOutlined,
  SettingOutlined,
  TrophyOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { usePendingStore } from '@/stores/pending'
import { ROLE_TEXT } from '@/api/types'
import { warningApi } from '@/api/modules'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const pendingStore = usePendingStore()

const collapsed = ref(window.innerWidth < 760)
const unread = ref(0)
const searchKw = ref('')
const auditPopupOpen = ref(false)
const auditPopupAck = ref('')
let pendingRefreshTimer: ReturnType<typeof setInterval> | undefined

const financeIdentity = computed(() => userStore.identityCode || '')
const isUnitFinance = computed(() => ['finHead', 'finStaff'].includes(financeIdentity.value))
const isHqFinance = computed(() => financeIdentity.value === 'finHq')
const auditTasks = computed(() => pendingStore.auditTasks)
const pendingAuditTotal = computed(() => pendingStore.totalAuditCount)

const iconMap: Record<string, any> = {
  HomeOutlined,
  AppstoreOutlined,
  FileAddOutlined,
  DeploymentUnitOutlined,
  CheckCircleOutlined,
  RocketOutlined,
  TrophyOutlined,
  SettingOutlined,
}

/** 由路由树生成侧边菜单（配置类菜单仅系统管理员可见） */
const menus = computed(() =>
  (router.options.routes.find((r) => r.path === '/')?.children || [])
    .filter((r: any) => !r.meta?.hidden)
    .map((r: any) => {
      const children = (r.children || [])
        .filter((c: any) => {
          if (c.meta?.hidden) return false
          if (c.meta?.adminOnly && !userStore.isAdmin) return false
          if (c.meta?.hqOnly && !userStore.canViewBoard) return false
          return true
        })
        .map((c: any) => ({
          key: `/${r.path}/${c.path}`,
          label: c.meta?.title,
          pendingFiling: `/${r.path}/${c.path}` === '/initiation/filing',
          pendingImplementReview: `/${r.path}/${c.path}` === '/implement/review',
          pendingFundReview: `/${r.path}/${c.path}` === '/implement/fund' ? 'all' : undefined,
        }))
      if (r.path === 'initiation') {
        const declarationIndex = children.findIndex((c: any) => c.key === '/initiation/declaration')
        if (declarationIndex >= 0) {
          children.splice(declarationIndex + 1, 0, {
            key: '/initiation/declaration?review=mine',
            label: '待我审核',
            pendingReview: true,
          })
        }
      }
      if (r.path === 'implement') {
        const financeMenus: any[] = []
        if (isUnitFinance.value || isHqFinance.value) {
          financeMenus.push({ key: '/implement/fund?desk=finance', label: '财务待办', pendingFundReview: 'all' })
        }
        if (isUnitFinance.value) {
          financeMenus.push(
            { key: '/implement/fund?mode=budget&desk=unit-budget', label: '经费预算审核', pendingFundReview: 'budget' },
            { key: '/implement/fund?mode=writeoff&desk=writeoff', label: '经费核销办理', pendingFundReview: 'writeoff' },
          )
        }
        if (isUnitFinance.value || isHqFinance.value) {
          financeMenus.push({ key: '/implement/fund?mode=final&desk=final', label: '经费总核', pendingFundReview: 'final' })
        }
        if (isHqFinance.value) {
          financeMenus.push(
            { key: '/implement/fund?mode=budget&desk=hq-budget', label: '预算复核备案', pendingFundReview: 'budget' },
            { key: '/implement/fund?tab=hq&desk=hq-budget-control', label: '总部经费管控' },
          )
        }
        if (financeMenus.length) {
          const fundIndex = children.findIndex((c: any) => c.key === '/implement/fund')
          const insertAt = fundIndex >= 0 ? fundIndex + 1 : children.length
          children.splice(insertAt, 0, ...financeMenus.filter((fm) => !children.some((c: any) => c.key === fm.key)))
        }
      }
      // 有子菜单时，若过滤后为空则隐藏父级
      if ((r.children || []).length && children.length === 0) return null
      return {
        key: r.path === 'dashboard' ? '/dashboard' : `/${r.path}`,
        label: r.meta?.title,
        icon: r.meta?.icon ? iconMap[r.meta.icon] : undefined,
        children: children.length ? children : undefined,
      }
    })
    .filter(Boolean),
)

/** 扁平化可搜菜单（匹配功能名） */
const flatMenus = computed(() => {
  const list: { key: string; label: string }[] = []
  for (const m of menus.value) {
    if (m.children?.length) {
      m.children.forEach((c: any) => list.push({ key: c.key, label: `${m.label} / ${c.label}` }))
    } else {
      list.push({ key: m.key, label: String(m.label || '') })
    }
  }
  return list
})

const openKeys = ref<string[]>([])
function selectedMenuKey() {
  if (route.path === '/initiation/declaration' && route.query.review === 'mine') return '/initiation/declaration?review=mine'
  if (route.path === '/implement/fund') {
    const desk = String(route.query.desk || '')
    const mode = String(route.query.mode || '')
    const tabKey = String(route.query.tab || '')
    if (desk === 'finance') return '/implement/fund?desk=finance'
    if (desk === 'unit-budget') return '/implement/fund?mode=budget&desk=unit-budget'
    if (desk === 'writeoff') return '/implement/fund?mode=writeoff&desk=writeoff'
    if (desk === 'final') return '/implement/fund?mode=final&desk=final'
    if (desk === 'hq-budget') return '/implement/fund?mode=budget&desk=hq-budget'
    if (desk === 'hq-budget-control' || tabKey === 'hq') return '/implement/fund?tab=hq&desk=hq-budget-control'
    if (mode === 'budget' && isHqFinance.value) return '/implement/fund?mode=budget&desk=hq-budget'
    if (mode === 'budget' && isUnitFinance.value) return '/implement/fund?mode=budget&desk=unit-budget'
    if (mode === 'writeoff' && isUnitFinance.value) return '/implement/fund?mode=writeoff&desk=writeoff'
    if (mode === 'final' && (isUnitFinance.value || isHqFinance.value)) return '/implement/fund?mode=final&desk=final'
  }
  return (route.meta.activeMenu as string) || route.path
}
const selectedKeys = computed(() => [selectedMenuKey()])
const parentKey = computed(() => '/' + (route.path.split('/')[1] || ''))

watch(
  () => route.fullPath,
  () => {
    const pk = parentKey.value
    if (pk && !openKeys.value.includes(pk)) openKeys.value = [pk]
  },
  { immediate: true },
)

/** 面包屑 */
const crumbs = computed(() => {
  const custom = route.meta?.breadcrumb as string[] | undefined
  if (custom?.length) return custom
  const list: string[] = []
  const seg = route.path.replace(/^\//, '').split('/')
  let cur: any = router.options.routes.find((r) => r.path === '/')
  if (seg[0] === 'dashboard') return ['首页']
  for (const s of seg) {
    if (!cur?.children) break
    const next = cur.children.find((c: any) => c.path === s || c.path === seg.join('/'))
    const found = next || cur.children.find((c: any) => c.path === s)
    if (!found) break
    list.push(found.meta?.title || s)
    cur = found
  }
  return list.length ? list : ['首页']
})

async function loadUnread() {
  try {
    const res = await warningApi.page({ page: 1, size: 200 })
    unread.value = ((res.data as any)?.records || []).filter((w: any) => !w.isRead).length
  } catch {
    unread.value = 0
  }
}
loadUnread()

function signatureHash(text: string) {
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0
  }
  return hash.toString(36)
}

function auditPopupKey(signature: string) {
  const uid = userStore.employeeNo || userStore.userId || userStore.realName || 'guest'
  return `rpm_pending_audit_popup_${uid}_${signatureHash(signature)}`
}

function maybeOpenAuditPopup(signature = pendingStore.auditPopupSignature) {
  if (!userStore.token || !signature || pendingAuditTotal.value <= 0) return
  if (auditPopupAck.value === signature) return
  try {
    if (sessionStorage.getItem(auditPopupKey(signature)) === '1') return
  } catch {
    // 浏览器禁用 sessionStorage 时仍允许本次弹出。
  }
  auditPopupOpen.value = true
}

function closeAuditPopup() {
  const signature = pendingStore.auditPopupSignature
  if (signature) {
    auditPopupAck.value = signature
    try {
      sessionStorage.setItem(auditPopupKey(signature), '1')
    } catch {
      // 忽略会话存储异常，不影响办理入口。
    }
  }
  auditPopupOpen.value = false
}

function goAuditTask(task: { route: string }) {
  closeAuditPopup()
  router.push(task.route)
}

function fundReviewCount(scope?: string) {
  if (scope === 'budget') return pendingStore.fundBudgetReviewCount
  if (scope === 'writeoff') return pendingStore.fundWriteoffReviewCount
  if (scope === 'final') return pendingStore.fundFinalReviewCount
  if (scope === 'all') return pendingStore.fundReviewCount
  return 0
}

function fundReviewMatchesScope(row: any, scope?: string) {
  if (!scope || scope === 'all') return true
  return row?.mode === scope
}

function fundReviewRoute(row: any) {
  const params = new URLSearchParams()
  params.set('projectId', String(row.projectId))
  params.set('mode', String(row.mode))
  params.set('desk', String(row.desk))
  return `/implement/fund?${params.toString()}`
}

function openMenuItem(item: any) {
  const scope = typeof item?.pendingFundReview === 'string' ? item.pendingFundReview : ''
  if (scope) {
    const target = pendingStore.fundReviews.find((row: any) => fundReviewMatchesScope(row, scope))
    if (target) {
      router.push(fundReviewRoute(target))
      return
    }
  }
  router.push(item.key)
}

function goAllAuditTasks() {
  closeAuditPopup()
  const hasImplementOnly = auditTasks.value.some((task) => task.kind === 'milestone') && auditTasks.value.every((task) => task.kind === 'milestone')
  const hasFundOnly = auditTasks.value.some((task) => task.kind === 'fund') && auditTasks.value.every((task) => task.kind === 'fund')
  if (hasFundOnly) {
    router.push(auditTasks.value[0]?.route || '/implement/fund?desk=finance')
    return
  }
  router.push(hasImplementOnly ? '/implement/review' : '/initiation/declaration?review=mine')
}

watch(
  () => userStore.identityCode,
  async (identityCode) => {
    if (pendingRefreshTimer) {
      clearInterval(pendingRefreshTimer)
      pendingRefreshTimer = undefined
    }
    await pendingStore.loadDeclarationReviews(identityCode)
    maybeOpenAuditPopup()
    if (identityCode) {
      pendingRefreshTimer = setInterval(() => {
        void pendingStore.loadDeclarationReviews(userStore.identityCode)
      }, 30_000)
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  if (pendingRefreshTimer) clearInterval(pendingRefreshTimer)
  pendingRefreshTimer = undefined
  window.removeEventListener('resize', syncResponsiveSider)
})

function syncResponsiveSider() {
  if (window.innerWidth < 760) collapsed.value = true
}

onMounted(() => {
  window.addEventListener('resize', syncResponsiveSider)
})

watch(
  () => pendingStore.auditPopupSignature,
  (signature) => maybeOpenAuditPopup(signature),
)

function onSearch() {
  const kw = searchKw.value.trim()
  if (!kw) return
  const hit = flatMenus.value.find((m) => String(m.label).includes(kw))
  if (hit) {
    router.push(hit.key)
    searchKw.value = ''
  } else {
    message.info('未找到匹配功能，请换个关键词')
  }
}

function onLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <a-layout style="height: 100%">
    <!-- 标题栏：商飞蓝 #0048A0 · 64px -->
    <a-layout-header class="app-header">
      <div class="header-left">
        <div class="logo">C</div>
        <span class="sys-name">科研项目信息化管理平台</span>
        <a-tag class="header-tag">COMAC RPM</a-tag>
      </div>

      <div class="header-search">
        <a-input
          v-model:value="searchKw"
          allow-clear
          placeholder="搜索功能名称"
          @pressEnter="onSearch"
        >
          <template #prefix><SearchOutlined /></template>
          <template #suffix>
            <a-button type="link" size="small" style="color: #fff; padding: 0" @click="onSearch">
              搜索
            </a-button>
          </template>
        </a-input>
      </div>

      <a-space :size="18" class="header-right">
        <a-tooltip title="操作指引">
          <QuestionCircleOutlined class="header-icon" />
        </a-tooltip>
        <a-badge :count="unread" :offset="[2, -2]">
          <a-tooltip title="预警中心">
            <BellOutlined class="header-icon" @click="router.push('/system/warning')" />
          </a-tooltip>
        </a-badge>
        <a-dropdown>
          <a-space :size="6" class="header-user">
            <UserOutlined />
            <span>{{ userStore.realName || '未登录' }}</span>
            <span class="header-role">
              {{ (userStore.roles[0] && ROLE_TEXT[userStore.roles[0]]) || '' }}
            </span>
          </a-space>
          <template #overlay>
            <a-menu>
              <a-menu-item key="org" disabled>
                {{ userStore.orgName || '—' }}
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout" @click="onLogout">
                <LogoutOutlined /> 退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </a-space>
    </a-layout-header>

    <a-layout>
      <!-- 侧边导航约 250px，可缩进 -->
      <a-layout-sider
        v-model:collapsed="collapsed"
        class="app-sider"
        :width="250"
        collapsible
        :trigger="null"
      >
        <div class="sider-trigger">
          <a-button type="text" @click="collapsed = !collapsed">
            <component :is="collapsed ? MenuUnfoldOutlined : MenuFoldOutlined" />
          </a-button>
        </div>
        <a-menu
          mode="inline"
          :selected-keys="selectedKeys"
          :open-keys="openKeys"
          @open-change="(keys: string[]) => (openKeys = keys)"
        >
          <template v-for="m in menus" :key="m.key">
            <a-sub-menu v-if="m.children && m.children.length" :key="m.key">
              <template #icon><component :is="m.icon" /></template>
              <template #title>{{ m.label }}</template>
              <a-menu-item v-for="c in m.children" :key="c.key" @click="openMenuItem(c)">
                <span class="menu-child-label">
                  <span>{{ c.label }}</span>
                  <a-badge
                    v-if="c.pendingReview"
                    :count="pendingStore.totalReviewCount"
                    :overflow-count="99"
                  />
                  <a-badge
                    v-else-if="c.pendingFiling"
                    :count="pendingStore.filingPendingCount"
                    :overflow-count="99"
                  />
                  <a-badge
                    v-else-if="c.pendingImplementReview"
                    :count="pendingStore.milestoneReviewCount"
                    :overflow-count="99"
                  />
                  <a-badge
                    v-else-if="c.pendingFundReview"
                    :count="fundReviewCount(c.pendingFundReview)"
                    :overflow-count="99"
                  />
                </span>
              </a-menu-item>
            </a-sub-menu>
            <a-menu-item v-else :key="m.key" @click="router.push(m.key)">
              <template #icon><component :is="m.icon" /></template>
              {{ m.label }}
            </a-menu-item>
          </template>
        </a-menu>
      </a-layout-sider>

      <a-layout-content class="app-content">
        <div class="app-breadcrumb-bar">
          <a-breadcrumb>
            <a-breadcrumb-item v-for="(c, i) in crumbs" :key="i">{{ c }}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <router-view />
      </a-layout-content>
    </a-layout>
    <a-modal
      v-model:open="auditPopupOpen"
      title="待审核消息"
      :width="760"
      :footer="null"
      @cancel="closeAuditPopup"
    >
      <a-alert
        type="warning"
        show-icon
        :message="`当前账号 ${userStore.realName || '—'} 有 ${pendingAuditTotal} 条待审核任务`"
        description="这些任务已流转到您负责的审核节点，请进入对应页面办理。"
      />
      <div class="audit-popup-list">
        <div v-for="task in auditTasks" :key="task.key" class="audit-popup-item">
          <div class="audit-popup-main">
            <a-tag :color="task.color">{{ task.tag }}</a-tag>
            <div class="audit-popup-text">
              <div class="audit-popup-title">{{ task.title }}</div>
              <div class="audit-popup-sub">{{ task.subtitle }}</div>
              <div class="audit-popup-node">当前节点：{{ task.node }}</div>
            </div>
          </div>
          <a-button type="primary" size="small" @click="goAuditTask(task)">立即办理</a-button>
        </div>
      </div>
      <div class="audit-popup-footer">
        <a-button @click="closeAuditPopup">稍后处理</a-button>
        <a-button type="primary" @click="goAllAuditTasks">查看待我审核</a-button>
      </div>
    </a-modal>
  </a-layout>
</template>

<style scoped>
.app-header {
  position: relative;
  z-index: 20;
  height: var(--header-h) !important;
  line-height: normal !important;
  padding: 0 var(--gap-md) !important;
  display: flex;
  align-items: center;
  gap: var(--gap-md);
  background: var(--zgsf-header) !important;
  color: var(--zgsf-card);
  box-shadow: 0 2px 8px rgba(0, 36, 80, 0.18);
}
.header-left {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.logo {
  width: 32px;
  height: 32px;
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: var(--zgsf-radius);
  background: rgba(255, 255, 255, 0.18);
  display: grid;
  place-items: center;
  font-weight: 700;
  margin-right: 12px;
}
.sys-name {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0;
  white-space: nowrap;
}
.header-tag {
  margin-left: 12px;
  color: var(--zgsf-card) !important;
  border: none !important;
  background: rgba(255, 255, 255, 0.14) !important;
  border-radius: var(--zgsf-radius) !important;
}
.header-search {
  flex: 1;
  max-width: 400px;
  margin: 0 auto;
}
.header-search :deep(.ant-input-affix-wrapper) {
  height: 36px;
  background: rgba(255, 255, 255, 0.14);
  border-color: rgba(255, 255, 255, 0.3);
  color: var(--zgsf-card);
  border-radius: var(--zgsf-radius);
  box-shadow: none !important;
}
.header-search :deep(.ant-input-affix-wrapper:hover),
.header-search :deep(.ant-input-affix-wrapper-focused) {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.62) !important;
}
.header-search :deep(.ant-input) {
  background: transparent;
  color: var(--zgsf-card);
}
.header-search :deep(.ant-input::placeholder) {
  color: rgba(255, 255, 255, 0.65);
}
.header-search :deep(.anticon) {
  color: rgba(255, 255, 255, 0.85);
}
.header-right {
  color: var(--zgsf-card);
  flex-shrink: 0;
  margin-left: auto;
}
.header-icon {
  width: 32px;
  height: 32px;
  display: inline-grid;
  place-items: center;
  border-radius: var(--zgsf-radius);
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.16s ease;
}
.header-icon:hover {
  background: rgba(255, 255, 255, 0.14);
}
.header-user {
  min-height: 36px;
  padding: 0 8px;
  cursor: pointer;
  color: var(--zgsf-card);
  border-radius: var(--zgsf-radius);
  transition: background-color 0.16s ease;
}
.header-user:hover {
  background: rgba(255, 255, 255, 0.14);
}
.header-role {
  opacity: 0.75;
  font-size: 12px;
}
.sider-trigger {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 8px 12px;
  border-bottom: 1px solid var(--zgsf-border-light);
}
.sider-trigger :deep(.ant-btn) {
  width: 32px;
  height: 32px;
  color: var(--zgsf-text-subtle);
}
.app-sider :deep(.ant-menu) {
  padding: 8px 0 16px;
  background: transparent;
}
.app-sider :deep(.ant-menu-item),
.app-sider :deep(.ant-menu-submenu-title) {
  min-height: 40px;
  margin-block: 2px;
  border-radius: var(--zgsf-radius);
}
.app-sider :deep(.ant-menu-submenu-title) {
  font-weight: 500;
}
.app-sider :deep(.ant-menu-item-selected) {
  font-weight: 600;
}
.app-sider :deep(.ant-menu-sub.ant-menu-inline) {
  background: var(--zgsf-fill);
}
.menu-child-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding-right: 8px;
}
.audit-popup-list {
  max-height: 420px;
  margin-top: 16px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.audit-popup-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  min-height: 72px;
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius-card);
  background: var(--zgsf-card);
  transition: border-color 0.16s ease, background-color 0.16s ease;
}
.audit-popup-item:hover {
  border-color: #b7d3fb;
  background: var(--zgsf-brand-softer);
}
.audit-popup-main {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.audit-popup-text {
  min-width: 0;
}
.audit-popup-title {
  color: var(--zgsf-text);
  font-weight: 600;
}
.audit-popup-sub,
.audit-popup-node {
  margin-top: 2px;
  color: var(--zgsf-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.audit-popup-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .app-header {
    gap: 12px;
  }
  .header-tag,
  .header-role {
    display: none;
  }
  .header-search {
    max-width: 300px;
  }
}

@media (max-width: 760px) {
  .app-header {
    padding-inline: 12px !important;
  }
  .sys-name {
    max-width: 152px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 16px;
  }
  .header-search {
    min-width: 120px;
  }
  .header-right {
    gap: 4px !important;
  }
  .header-right > :first-child,
  .header-user > span:not(:first-of-type) {
    display: none;
  }
  .audit-popup-list {
    max-height: 52vh;
  }
  .audit-popup-item {
    align-items: stretch;
    flex-direction: column;
  }
  .audit-popup-main {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
  .audit-popup-title,
  .audit-popup-sub,
  .audit-popup-node {
    overflow-wrap: anywhere;
  }
  .audit-popup-item > .ant-btn {
    align-self: flex-end;
  }
  .audit-popup-footer {
    flex-wrap: wrap;
  }
}

@media (max-width: 560px) {
  .logo {
    margin-right: 0;
  }
  .sys-name {
    display: none;
  }
  .header-search {
    max-width: none;
  }
}
</style>
