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
              <el-button type="primary" class="action-btn" @click="submit">登录</el-button>
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
              <el-button type="success" class="action-btn" @click="submit">注册</el-button>
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
import { useRouter } from 'vue-router'
import { getProfileApi, loginApi, registerApi } from '@/api/modules/auth'
import { useUserStore } from '@/stores/user'
import AppIcon from '@/components/AppIcon.vue'

const router = useRouter()
const userStore = useUserStore()
const isRegister = ref(false)

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
  try {
    if (!form.username || !form.password) {
      ElMessage.warning('请先填写用户名和密码')
      return
    }

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

    const profileRes = await getProfileApi()
    userStore.setLogin(loginData.token, profileRes.data.data)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    const message = error instanceof Error ? error.message : '操作失败，请稍后重试'
    ElMessage.error(message)
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
  .social-section {
    border-left: none;
    padding-left: 0 !important;
    margin-top: 48px;
    border-top: 1px solid var(--line);
    padding-top: 36px;
  }
}
</style>
