<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { isSilentAuthError } from '@/api/request'
import { PERSONNEL_GROUPS, PERSONNEL_ROSTER } from '@/constants/personnel'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const entering = ref('')
const form = reactive({ username: '100001', password: '100001' })

const groupedAccounts = computed(() =>
  PERSONNEL_GROUPS.map((group) => ({
    group,
    people: PERSONNEL_ROSTER.filter((p) => p.group === group),
  })),
)

/** 进入登录页先清掉失效会话，避免旧 Token 继续打业务接口刷红条 */
onMounted(() => {
  try {
    message.destroy()
  } catch {
    /* ignore */
  }
  userStore.logout()
})

async function onSubmit() {
  if (loading.value) return
  loading.value = true
  try {
    userStore.logout()
    const d = await userStore.login(form.username, form.password)
    message.success(`欢迎回来，${d.realName}`)
    router.replace('/dashboard')
  } catch (e: any) {
    if (!isSilentAuthError(e)) {
      message.error(e.message || '登录失败')
    }
  } finally {
    loading.value = false
    entering.value = ''
  }
}

/** 点击人员卡片：填充账号并直接登录 */
async function enterAs(employeeNo: string) {
  if (loading.value) return
  form.username = employeeNo
  form.password = employeeNo
  entering.value = employeeNo
  await onSubmit()
}
</script>

<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-form-col">
        <div class="brand">
          <div class="brand-logo">C</div>
          <div>
            <h1>科研项目信息化管理平台</h1>
            <p>Research Project Management Platform · COMAC</p>
          </div>
        </div>

        <a-form :model="form" layout="vertical" @finish="onSubmit">
          <a-form-item label="账号" name="username" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input v-model:value="form.username" size="large" placeholder="请输入工号">
              <template #prefix><UserOutlined /></template>
            </a-input>
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <a-input-password v-model:value="form.password" size="large" placeholder="请输入密码">
              <template #prefix><LockOutlined /></template>
            </a-input-password>
          </a-form-item>
          <a-button type="primary" size="large" block :loading="loading && !entering" html-type="submit" @click.prevent="onSubmit">
            登录
          </a-button>
        </a-form>
        <p class="pwd-hint">演示账号与密码均为工号，右侧点击人员即可自动登录</p>
      </div>

      <div class="login-accounts-col">
        <div class="accounts-head">
          <h2>人员账户</h2>
          <span>共 {{ PERSONNEL_ROSTER.length }} 人，点击直接进入</span>
        </div>
        <div v-for="g in groupedAccounts" :key="g.group" class="account-group">
          <div class="group-title">{{ g.group }}</div>
          <div class="account-grid">
            <button
              v-for="p in g.people"
              :key="p.employeeNo"
              type="button"
              class="account-card"
              :disabled="loading"
              :class="{ active: entering === p.employeeNo }"
              @click="enterAs(p.employeeNo)"
            >
              <span class="avatar">{{ p.realName.slice(0, 1) }}</span>
              <span class="meta">
                <span class="name">{{ p.realName }}</span>
                <span class="role">{{ p.identity }} · {{ p.employeeNo }}</span>
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  isolation: isolate;
  height: 100%;
  min-height: 640px;
  display: grid;
  place-items: center;
  padding: 20px;
  background: var(--zgsf-bg);
  overflow: auto;
}
.login-page::before {
  position: fixed;
  z-index: -1;
  inset: 0 0 auto;
  height: 38%;
  min-height: 240px;
  content: '';
  background: var(--zgsf-header);
}
.login-box {
  width: min(960px, 100%);
  min-height: 520px;
  display: grid;
  grid-template-columns: 380px 1fr;
  background: var(--zgsf-card);
  border-radius: var(--zgsf-radius-card);
  border: 1px solid var(--zgsf-border);
  box-shadow: 0 12px 32px rgba(0, 40, 90, 0.16);
  overflow: hidden;
}
.login-form-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px 36px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 32px;
}
.brand-logo {
  width: 44px;
  height: 44px;
  border: 1px solid #0d65bd;
  border-radius: var(--zgsf-radius);
  background: var(--zgsf-header);
  color: var(--zgsf-card);
  display: grid;
  place-items: center;
  font-size: 22px;
  font-weight: 700;
  flex-shrink: 0;
}
.brand h1 {
  font-size: 18px;
  margin: 0;
  line-height: 26px;
  color: var(--zgsf-text);
}
.brand p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--zgsf-text-secondary);
}
.pwd-hint {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--zgsf-text-secondary);
  line-height: 1.6;
}
.login-accounts-col {
  background: var(--zgsf-fill);
  padding: 24px 24px 20px;
  border-left: 1px solid var(--zgsf-border);
  max-height: 640px;
  overflow-y: auto;
}
.accounts-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}
.accounts-head h2 {
  margin: 0;
  font-size: 16px;
  color: var(--zgsf-text);
  font-weight: 600;
}
.accounts-head span {
  font-size: 12px;
  color: var(--zgsf-text-secondary);
}
.account-group + .account-group {
  margin-top: 12px;
}
.group-title {
  font-size: 12px;
  color: var(--zgsf-text-secondary);
  margin-bottom: 8px;
}
.account-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.account-card {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  padding: 8px 10px;
  text-align: left;
  background: var(--zgsf-card);
  border: 1px solid var(--zgsf-border);
  border-radius: var(--zgsf-radius);
  cursor: pointer;
  color: inherit;
  box-shadow: 0 1px 2px rgba(31, 35, 41, 0.03);
  transition: border-color 0.16s ease, background-color 0.16s ease, box-shadow 0.16s ease;
}
.account-card:hover:not(:disabled) {
  border-color: var(--zgsf-brand);
  background: var(--zgsf-brand-softer);
  box-shadow: 0 2px 6px rgba(0, 100, 239, 0.08);
}
.account-card.active {
  border-color: var(--zgsf-brand);
  background: var(--zgsf-brand-soft);
}
.account-card:disabled {
  cursor: wait;
  opacity: 0.7;
}
.avatar {
  width: 28px;
  height: 28px;
  border-radius: var(--zgsf-radius);
  background: var(--zgsf-header);
  color: var(--zgsf-card);
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}
.meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.name {
  font-size: 13px;
  color: var(--zgsf-text);
  font-weight: 600;
  line-height: 1.3;
}
.role {
  font-size: 11px;
  color: var(--zgsf-text-secondary);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
@media (max-width: 800px) {
  .login-page {
    place-items: start center;
    padding: 16px;
  }
  .login-box {
    grid-template-columns: 1fr;
  }
  .login-form-col {
    padding: 28px 24px;
  }
  .login-accounts-col {
    border-left: none;
    border-top: 1px solid var(--zgsf-border);
    max-height: none;
  }
  .account-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .login-page {
    padding: 0;
    background: var(--zgsf-card);
  }
  .login-page::before {
    display: none;
  }
  .login-box {
    min-height: 100%;
    border: none;
    border-radius: 0;
    box-shadow: none;
  }
  .accounts-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
