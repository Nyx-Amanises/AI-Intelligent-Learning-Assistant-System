<template>
  <div class="login-page">
    <header class="top-nav">
      <div class="logo-container">
        <div class="logo-badge">
          <AppIcon name="brand" :size="22" />
        </div>
        <span class="logo-text">AI LEARNING</span>
      </div>
      <div class="top-right-icon">Workspace</div>
    </header>

    <section class="mobile-login-hero">
      <div class="mobile-login-hero__top">
        <span class="mobile-login-hero__menu" aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
        <strong>AI Learning</strong>
        <div class="mobile-login-hero__avatar">AI</div>
      </div>
      <div class="mobile-login-hero__copy">
        <p>{{ isRegister ? '创建你的学习空间' : '同学，你好' }}</p>
        <h1>{{ isRegister ? '注册后继续学习' : '今天想学点什么？' }}</h1>
      </div>
      <div class="mobile-login-hero__prompts">
        <span>整理学习资料</span>
        <span>生成复习总结</span>
        <span>开始练习刷题</span>
      </div>
    </section>

    <div class="slogan-bar">
      <span>上传资料、生成总结、完成练习，把学习闭环做成一条顺手的工作流。</span>
      <el-link type="primary" :underline="false" style="font-size: 16px; margin-left: 6px;">
        《学习助手使用说明》
      </el-link>
    </div>

    <div class="main-container">
      <el-row :gutter="96" justify="center" align="middle" class="content-row">
        <el-col :xs="24" :sm="12" :md="9" class="form-section">
          <div v-if="!isRegister">
            <h1 class="welcome-title">欢迎回来</h1>
            <p class="login-subtitle">使用已经注册的账号进入后台工作台。</p>
            <el-form :model="form" @submit.prevent>
              <el-form-item>
                <el-input v-model="form.username" placeholder="用户名" class="custom-input" />
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="form.password"
                  type="password"
                  show-password
                  placeholder="密码"
                  class="custom-input"
                />
              </el-form-item>
              <div class="form-footer">
                <el-link type="primary" :underline="false" @click="toggleMode(true)">还没有账户？点击注册</el-link>
              </div>
              <el-button type="primary" class="action-btn" :loading="submitting" @click="submit">登录</el-button>
            </el-form>
          </div>

          <div v-else>
            <h1 class="welcome-title">创建新账户</h1>
            <p class="login-subtitle">注册一个演示账号，登录后即可继续联调整个系统。</p>
            <el-form :model="form" @submit.prevent>
              <el-form-item>
                <el-input v-model="form.username" placeholder="用户名" class="custom-input" />
              </el-form-item>
              <el-form-item>
                <el-input v-model="form.nickname" placeholder="昵称" class="custom-input" />
              </el-form-item>
              <el-form-item>
                <el-input v-model="form.email" placeholder="邮箱（可选）" class="custom-input" />
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="form.password"
                  type="password"
                  show-password
                  placeholder="密码"
                  class="custom-input"
                />
              </el-form-item>
              <div class="form-footer">
                <el-link type="primary" :underline="false" @click="toggleMode(false)">已有账户？立即登录</el-link>
              </div>
              <el-button type="success" class="action-btn" :loading="submitting" @click="submit">注册</el-button>
            </el-form>
          </div>
        </el-col>

        <el-col :xs="24" :sm="10" :md="8" class="social-section">
          <div class="social-wrapper">
            <div class="social-login-title">第三方登录</div>
            <el-button class="social-btn" disabled>
              <span class="social-btn__icon social-btn__icon--google">
                <AppIcon name="google" :size="20" />
              </span>
              <span>使用 Google 登录</span>
            </el-button>
            <el-button class="social-btn" disabled>
              <span class="social-btn__icon social-btn__icon--github">
                <AppIcon name="github" :size="20" />
              </span>
              <span>使用 GitHub 登录</span>
            </el-button>
            <el-button class="social-btn" disabled>
              <span class="social-btn__icon social-btn__icon--discord">
                <AppIcon name="discord" :size="20" />
              </span>
              <span>使用 Discord 登录</span>
            </el-button>
            <el-button class="social-btn" disabled>
              <span class="social-btn__icon social-btn__icon--qq">
                <AppIcon name="qq" :size="20" />
              </span>
              <span>使用 QQ 登录</span>
            </el-button>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { loginApi, registerApi } from '@/api/modules/auth'
import { useUserStore } from '@/stores/user'
import AppIcon from '@/components/AppIcon.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isRegister = ref(false)
const submitting = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: ''
})

const toggleMode = (value: boolean) => {
  isRegister.value = value
}

const submit = async () => {
  if (submitting.value) {
    return
  }

  try {
    if (!form.username || !form.password) {
      ElMessage.warning('请先填写用户名和密码')
      return
    }

    submitting.value = true

    if (isRegister.value) {
      if (!form.nickname) {
        ElMessage.warning('注册时请填写昵称')
        return
      }
      await registerApi({
        username: form.username,
        password: form.password,
        nickname: form.nickname,
        email: form.email
      })
      ElMessage.success('注册成功，请继续登录')
      isRegister.value = false
      return
    }

    const loginRes = await loginApi({
      username: form.username,
      password: form.password
    })
    const loginData = loginRes.data?.data
    if (!loginData?.token) {
      throw new Error('登录返回数据不完整')
    }

    userStore.setLogin(loginData.token, {
      id: loginData.userId,
      username: loginData.username,
      nickname: loginData.nickname
    })

    ElMessage.success('登录成功')
    const redirect =
      typeof route.query.redirect === 'string' &&
      route.query.redirect.startsWith('/') &&
      route.query.redirect !== '/login'
        ? route.query.redirect
        : '/dashboard'
    router.push(redirect)
  } catch (error) {
    const message = error instanceof Error ? error.message : '操作失败，请稍后重试'
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: var(--bg);
  overflow-x: hidden;
}

.top-nav {
  height: 72px;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0 48px;
  border-bottom: 1px solid var(--line);
  background: var(--panel);
  position: relative;
}

.logo-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-badge {
  width: 40px;
  height: 40px;
  border-radius: var(--radius);
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--brand), #16a34a);
  color: #fff;
  box-shadow: var(--shadow);
}

.logo-text {
  font-weight: 700;
  font-size: 18px;
  color: var(--text);
}

.top-right-icon {
  position: absolute;
  right: 48px;
  color: var(--muted);
  font-size: 14px;
}

.mobile-login-hero {
  display: none;
}

.slogan-bar {
  background: linear-gradient(135deg, var(--blue-soft), var(--brand-light));
  color: var(--brand);
  padding: 14px 48px;
  font-size: 15px;
  border-bottom: 1px solid var(--line);
}

.main-container {
  padding: 80px 24px;
  max-width: 1320px;
  margin: 0 auto;
}

.content-row {
  display: flex;
  align-items: center;
}

.welcome-title {
  font-size: 36px;
  margin-bottom: 16px;
  font-weight: 700;
  color: var(--text);
  text-align: left;
}

.login-subtitle {
  margin: 0 0 28px;
  color: var(--text-secondary);
  line-height: 1.7;
  font-size: 15px;
}

.custom-input :deep(.el-input__wrapper) {
  background: var(--bg);
  padding: 12px 16px;
  box-shadow: none;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  transition: all 0.2s ease;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.form-footer {
  margin-bottom: 24px;
  text-align: left;
  font-size: 14px;
}

.action-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border: none;
  border-radius: var(--radius-sm);
}

.social-section {
  border-left: 1px solid var(--line);
  padding-left: 72px !important;
  margin-top: 120px;
}

.social-wrapper {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.social-login-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.social-btn {
  width: 100%;
  margin: 0 !important;
  height: 48px;
  justify-content: flex-start;
  border: 1px solid var(--line);
  color: var(--text);
  background: var(--panel);
  border-radius: var(--radius-sm);
  transition: all 0.2s ease;
}

.social-btn:not(.is-disabled):hover {
  border-color: var(--brand);
  box-shadow: var(--shadow);
  transform: translateY(-1px);
}

.social-btn__icon {
  width: 32px;
  height: 32px;
  display: inline-grid;
  place-items: center;
  margin-right: 12px;
  border-radius: 50%;
  background: var(--bg);
}

.social-btn__icon--google {
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--line);
}

.social-btn__icon--github {
  background: #111827;
  color: #fff;
}

.social-btn__icon--discord {
  background: rgba(88, 101, 242, 0.12);
}

.social-btn__icon--qq {
  background: var(--bg-secondary);
}

@media (max-width: 768px) {
  .login-page {
    min-height: 100svh;
    background: #eef3f9;
  }

  .top-nav,
  .slogan-bar {
    display: none;
  }

  .mobile-login-hero {
    display: block;
    padding: 18px 22px 8px;
  }

  .mobile-login-hero__top {
    display: grid;
    grid-template-columns: 44px 1fr 48px;
    align-items: center;
    min-height: 54px;
  }

  .mobile-login-hero__top strong {
    justify-self: center;
    color: #111827;
    font-size: 20px;
    letter-spacing: 0;
  }

  .mobile-login-hero__menu {
    display: inline-grid;
    gap: 5px;
    width: 42px;
    height: 42px;
    padding: 9px;
    border: 0;
    border-radius: 14px;
    background: transparent;
  }

  .mobile-login-hero__menu span {
    display: block;
    height: 3px;
    border-radius: 999px;
    background: #111827;
  }

  .mobile-login-hero__avatar {
    justify-self: end;
    width: 46px;
    height: 46px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background:
      linear-gradient(#0f8f73, #0f8f73) padding-box,
      conic-gradient(#4285f4, #34a853, #fbbc05, #ea4335, #4285f4) border-box;
    border: 4px solid transparent;
    color: #fff;
    font-size: 14px;
    font-weight: 800;
  }

  .mobile-login-hero__copy {
    margin-top: 68px;
  }

  .mobile-login-hero__copy p {
    margin: 0 0 6px;
    color: #111827;
    font-size: 26px;
    line-height: 1.25;
  }

  .mobile-login-hero__copy h1 {
    margin: 0;
    color: #111827;
    font-size: 38px;
    line-height: 1.14;
    font-weight: 700;
    letter-spacing: 0;
  }

  .mobile-login-hero__prompts {
    display: grid;
    gap: 18px;
    margin-top: 44px;
    color: #3f4857;
    font-size: 20px;
    line-height: 1.45;
  }

  .mobile-login-hero__prompts span {
    position: relative;
    display: block;
    padding-left: 42px;
  }

  .mobile-login-hero__prompts span::before {
    content: "";
    position: absolute;
    left: 0;
    top: 4px;
    width: 22px;
    height: 52px;
    border-radius: 999px;
    background: #c8d0ff;
  }

  .main-container {
    position: relative;
    z-index: 1;
    padding: 22px 0 0;
    max-width: none;
  }

  .content-row {
    display: block;
    margin: 0 !important;
  }

  .form-section {
    width: 100% !important;
    max-width: none !important;
    padding: 24px 24px calc(28px + env(safe-area-inset-bottom)) !important;
    border-radius: 30px 30px 0 0;
    background: #fff;
    box-shadow: 0 -18px 38px rgba(15, 23, 42, 0.1);
  }

  .welcome-title {
    display: none;
  }

  .login-subtitle {
    margin-bottom: 18px;
    color: #667085;
    font-size: 14px;
  }

  .custom-input :deep(.el-input__wrapper) {
    min-height: 50px;
    border-radius: 18px;
    background: #f8fafc;
  }

  .form-footer {
    margin-bottom: 18px;
  }

  .action-btn {
    height: 52px;
    border-radius: 999px;
    font-size: 16px;
  }

  .social-section {
    display: none;
  }
}
</style>
