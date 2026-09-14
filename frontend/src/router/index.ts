import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { canViewVisualBoard } from '@/constants/permission'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/',
    component: BasicLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Workbench.vue'),
        meta: { title: '首页', icon: 'HomeOutlined' },
      },
      /* ---------------- 项目总览 ---------------- */
      {
        path: 'overview',
        name: 'Overview',
        redirect: '/overview/ledger',
        meta: { title: '项目总览', icon: 'AppstoreOutlined' },
        children: [
          {
            path: 'ledger',
            name: 'Ledger',
            component: () => import('@/views/overview/Ledger.vue'),
            meta: { title: '项目台账' },
          },
          {
            path: 'board',
            name: 'Board',
            component: () => import('@/views/overview/Board.vue'),
            meta: { title: '可视化看板', hqOnly: true },
          },
          {
            path: 'pre-research',
            name: 'PreResearchBoard',
            component: () => import('@/views/overview/Board.vue'),
            meta: { title: '科研预研信息管理大屏', hidden: true, activeMenu: '/overview/board', hqOnly: true },
          },
          {
            path: 'detail/:id',
            name: 'ProjectDetail',
            component: () => import('@/views/overview/ProjectDetail.vue'),
            meta: { title: '项目全生命周期详情', hidden: true, activeMenu: '/overview/ledger' },
          },
        ],
      },
      {
        path: 'supplement',
        name: 'Supplement',
        redirect: '/supplement/mine',
        meta: { title: '导入项目补录', icon: 'FileAddOutlined' },
        children: [
          { path: 'mine', name: 'SupplementMine', component: () => import('@/views/supplement/ProjectList.vue'), meta: { title: '我的补录', supplementView: 'mine' } },
          { path: 'review', name: 'SupplementReview', component: () => import('@/views/supplement/ProjectList.vue'), meta: { title: '待我审核', supplementView: 'review' } },
          { path: 'history', name: 'SupplementHistory', component: () => import('@/views/supplement/ProjectList.vue'), meta: { title: '补录记录', supplementView: 'history' } },
          { path: 'project/:id', name: 'SupplementWorkspace', component: () => import('@/views/supplement/Workspace.vue'), meta: { title: '信息与材料补录', hidden: true, activeMenu: '/supplement/mine' } },
        ],
      },
      /* ---------------- 立项阶段 ---------------- */
      {
        path: 'initiation',
        name: 'Initiation',
        redirect: '/initiation/declaration',
        meta: { title: '立项阶段', icon: 'FileAddOutlined' },
        children: [
          {
            path: 'declaration',
            name: 'Declaration',
            component: () => import('@/views/initiation/Declaration.vue'),
            meta: { title: '项目申报' },
          },
          {
            path: 'done',
            name: 'MyDone',
            component: () => import('@/views/initiation/MyDone.vue'),
            meta: { title: '我的已办' },
          },
          {
            path: 'filing',
            name: 'Filing',
            component: () => import('@/views/initiation/Filing.vue'),
            meta: { title: '立项备案' },
          },
          {
            path: 'filing-materials',
            name: 'FilingMaterials',
            component: () => import('@/views/initiation/FilingMaterials.vue'),
            meta: {
              title: '立项支撑材料',
              hidden: true,
              activeMenu: '/initiation/filing',
              breadcrumb: ['项目台账', '立项备案', '立项支撑材料'],
            },
          },
        ],
      },
      /* ---------------- 实施阶段 ---------------- */
      {
        path: 'implement',
        name: 'Implement',
        redirect: '/implement/basic',
        meta: { title: '实施阶段', icon: 'DeploymentUnitOutlined' },
        children: [
          {
            path: 'basic',
            name: 'BasicInfo',
            component: () => import('@/views/implement/BasicInfo.vue'),
            meta: { title: '项目基本信息' },
          },
          {
            path: 'review',
            name: 'ImplementReview',
            component: () => import('@/views/implement/ImplementReview.vue'),
            meta: { title: '待我审核' },
          },
          {
            path: 'milestone',
            name: 'Milestone',
            component: () => import('@/views/implement/Milestone.vue'),
            meta: { title: '里程碑填报' },
          },
          {
            path: 'milestone-close',
            name: 'MilestoneClose',
            component: () => import('@/views/implement/MilestoneClose.vue'),
            meta: { title: '里程碑销项', hidden: true, activeMenu: '/implement/milestone' },
          },
          {
            path: 'plan',
            name: 'Plan',
            component: () => import('@/views/implement/Plan.vue'),
            meta: { title: '计划管理' },
          },
          {
            path: 'fund',
            name: 'Fund',
            component: () => import('@/views/implement/Fund.vue'),
            meta: { title: '项目经费' },
          },
          {
            path: 'evaluation',
            name: 'Evaluation',
            component: () => import('@/views/implement/Evaluation.vue'),
            meta: { title: '评估检查' },
          },
          {
            path: 'change',
            name: 'Change',
            component: () => import('@/views/implement/Change.vue'),
            meta: { title: '项目变更' },
          },
        ],
      },
      /* ---------------- 验收阶段 ---------------- */
      {
        path: 'acceptance',
        name: 'Acceptance',
        redirect: '/acceptance/accept',
        meta: { title: '验收阶段', icon: 'CheckCircleOutlined' },
        children: [
          {
            path: 'accept',
            name: 'Accept',
            component: () => import('@/views/acceptance/Acceptance.vue'),
            meta: { title: '项目验收' },
          },
          {
            path: 'deliverable',
            name: 'Deliverable',
            component: () => import('@/views/acceptance/Deliverable.vue'),
            meta: { title: '交付物' },
          },
          {
            path: 'partner',
            name: 'PartnerEval',
            component: () => import('@/views/acceptance/PartnerEval.vue'),
            meta: { title: '协作单位评价' },
          },
        ],
      },
      /* ---------------- 成果转化 ---------------- */
      {
        path: 'transform',
        name: 'Transform',
        component: () => import('@/views/transform/Transform.vue'),
        meta: { title: '成果转化', icon: 'RocketOutlined' },
      },
      /* ---------------- 后评价 ---------------- */
      {
        path: 'post-eval',
        name: 'PostEval',
        component: () => import('@/views/post/PostEval.vue'),
        meta: { title: '后评价', icon: 'TrophyOutlined' },
      },
      /* ---------------- 系统管理 ---------------- */
      {
        path: 'system',
        name: 'System',
        redirect: '/system/warning',
        meta: { title: '系统管理', icon: 'SettingOutlined' },
        children: [
          {
            path: 'user',
            name: 'SysUser',
            component: () => import('@/views/system/User.vue'),
            meta: { title: '成员管理', adminOnly: true },
          },
          {
            path: 'form-maint',
            name: 'FormMaint',
            component: () => import('@/views/system/FormMaint.vue'),
            meta: { title: '表单维护', adminOnly: true },
          },
          {
            path: 'work-duty',
            name: 'WorkDuty',
            component: () => import('@/views/system/WorkDuty.vue'),
            meta: { title: '工作定责一览' },
          },
          {
            path: 'role-matrix',
            name: 'RoleMatrix',
            component: () => import('@/views/system/RoleMatrix.vue'),
            meta: { title: '项目岗位权限矩阵', adminOnly: true },
          },
          {
            path: 'channel',
            name: 'SysChannel',
            component: () => import('@/views/system/Channel.vue'),
            meta: { title: '项目渠道字典', adminOnly: true },
          },
          {
            path: 'warning',
            name: 'SysWarning',
            component: () => import('@/views/system/Warning.vue'),
            meta: { title: '预警中心' },
          },
          {
            path: 'audit',
            name: 'SysAudit',
            component: () => import('@/views/system/AuditLog.vue'),
            meta: { title: '审计日志', adminOnly: true },
          },
        ],
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('rpm_token')
  if (to.path === '/login') {
    next()
    return
  }
  if (!token) {
    next('/login')
    return
  }
  const needAdmin = to.matched.some((r) => r.meta?.adminOnly)
  if (needAdmin) {
    const roles = JSON.parse(localStorage.getItem('rpm_roles') || '[]') as string[]
    if (!roles.includes('ADMIN')) {
      next('/dashboard')
      return
    }
  }
  const needHq = to.matched.some((r) => r.meta?.hqOnly)
  if (needHq) {
    let roles: string[] = []
    try {
      roles = JSON.parse(localStorage.getItem('rpm_roles') || '[]') as string[]
    } catch {
      roles = []
    }
    if (
      !canViewVisualBoard({
        roles,
        identityCode: localStorage.getItem('rpm_identity_code') || '',
        identity: localStorage.getItem('rpm_identity') || '',
        dataScope: localStorage.getItem('rpm_data_scope') || '',
      })
    ) {
      next('/dashboard')
      return
    }
  }
  next()
})

export default router
