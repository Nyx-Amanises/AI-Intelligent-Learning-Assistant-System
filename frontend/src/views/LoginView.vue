<template>
  <div class="login-page">
    <header class="top-nav">
      <div class="logo-container">
        <div class="logo-badge">AI</div>
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
            <div class="social-card">
              <span>资料管理</span>
              <strong>支持文本与文件上传</strong>
              <em>课堂笔记、教材摘录、章节复习资料都能统一整理。</em>
            </div>
            <div class="social-card">
              <span>AI 总结</span>
              <strong>自动沉淀复习内容</strong>
              <em>直接进入总结列表，查看历史、再生成、预览资料都在同一处完成。</em>
            </div>
            <div class="social-card">
              <span>练习记录</span>
              <strong>AI 判分 + 人工参考答案</strong>
              <em>简答题支持双展示，练习提交后可继续查看评语与解析。</em>
            </div>
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
  background: #fff;
  overflow-x: hidden;
}

.top-nav {
  height: 72px;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0 48px;
  border-bottom: 1px solid #eee;
  position: relative;
}

.logo-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-badge {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #1677ff, #44b3ff);
  color: #fff;
  font-weight: 700;
}

.logo-text {
  font-weight: 700;
  font-size: 19px;
  color: #333;
}

.top-right-icon {
  position: absolute;
  right: 48px;
  color: #9aa3af;
  font-size: 15px;
}

.slogan-bar {
  background-color: #e7f3ff;
  color: #0070d2;
  padding: 14px 48px;
  font-size: 16px;
  border-bottom: 1px solid #d1e6f9;
}

.main-container {
  padding: 96px 24px;
  max-width: 1320px;
  margin: 0 auto;
}

.content-row {
  display: flex;
  align-items: center;
}

.welcome-title {
  font-size: 38px;
  margin-bottom: 16px;
  font-weight: 600;
  text-align: left;
}

.login-subtitle {
  margin: 0 0 28px;
  color: #6b7280;
  line-height: 1.7;
  font-size: 15px;
}

.custom-input :deep(.el-input__wrapper) {
  background-color: #f4f6f8;
  padding: 10px 14px;
  box-shadow: none;
  border: 1px solid #e1e4e8;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  border-color: #008cd1;
}

.form-footer {
  margin-bottom: 30px;
  text-align: left;
  font-size: 17px;
}

.action-btn {
  width: 100%;
  height: 53px;
  font-size: 18px;
  font-weight: 700;
  border: none;
}

.social-section {
  border-left: 1px solid #eee;
  padding-left: 72px !important;
  margin-top: 120px;
}

.social-wrapper {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.social-card {
  width: 100%;
  padding: 18px 20px;
  border: 1px solid #e1e4e8;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.social-card span,
.social-card em {
  display: block;
}

.social-card span {
  color: #0070d2;
  font-size: 13px;
  font-weight: 700;
}

.social-card strong {
  display: block;
  margin-top: 10px;
  font-size: 18px;
  color: #111827;
}

.social-card em {
  margin-top: 10px;
  color: #6b7280;
  font-style: normal;
  line-height: 1.75;
  font-size: 14px;
}

@media (max-width: 768px) {
  .social-section {
    border-left: none;
    padding-left: 0 !important;
    margin-top: 48px;
    border-top: 1px solid #eee;
    padding-top: 36px;
  }
}
</style>
