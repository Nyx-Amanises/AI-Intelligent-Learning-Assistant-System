<template>
  <main class="login-page">
    <div class="login-shell">
      <section class="login-story" aria-labelledby="login-story-title">
        <div class="login-brand">
          <span class="login-brand__mark"><AppIcon name="book-open" :size="26" /></span>
          <div><strong>AI 学习助手</strong><span>让知识，成为自己的</span></div>
        </div>

        <div class="login-story__copy">
          <span class="login-eyebrow">每一次学习，都更进一步</span>
          <h1 id="login-story-title">把知识，<br>学成自己的。</h1>
          <p>让资料有条理，让复习有方向。<br>在一个专注的空间里，理解、练习、慢慢掌握。</p>
        </div>

        <div class="learning-path" aria-label="学习路径">
          <div class="learning-path__heading"><span>从读懂，到会用</span><AppIcon name="spark" :size="18" /></div>
          <div v-for="(step, index) in learningSteps" :key="step.title" class="learning-path__step">
            <span class="learning-path__icon"><AppIcon :name="step.icon" :size="20" /></span>
            <div><strong>{{ step.title }}</strong><p>{{ step.description }}</p></div>
            <span class="learning-path__number">0{{ index + 1 }}</span>
          </div>
        </div>

        <p class="login-story__footnote">资料 <span aria-hidden="true">→</span> 总结 <span aria-hidden="true">→</span> 练习 <span aria-hidden="true">→</span> 复盘</p>
      </section>

      <section class="login-form-panel" aria-labelledby="login-form-title">
        <div class="login-form-content">
          <span class="login-form-eyebrow">你的个人学习空间</span>
          <h2 id="login-form-title">{{ isRegister ? '开启学习新一页' : '欢迎回来' }}</h2>
          <p class="login-form-description">{{ isRegister ? '创建账号，把学习资料和每一点进步留在这里。' : '登录后，继续上一次的思考与探索。' }}</p>

          <p v-if="registrationNotice" class="auth-notice auth-notice--success" role="status">{{ registrationNotice }}</p>
          <p v-if="submitError" class="auth-notice auth-notice--error" role="alert">{{ submitError }}</p>

          <form class="auth-form" novalidate :aria-busy="submitting" @submit.prevent="submit">
            <div class="auth-field">
              <label for="auth-username">用户名</label>
              <input
                id="auth-username"
                ref="usernameInput"
                v-model="form.username"
                name="username"
                type="text"
                autocomplete="username"
                autocapitalize="none"
                spellcheck="false"
                required
                :disabled="submitting"
                :maxlength="isRegister ? 20 : undefined"
                :placeholder="isRegister ? '设置 4–20 位用户名' : '输入你的用户名'"
                :aria-invalid="Boolean(errors.username)"
                :aria-describedby="errors.username ? 'auth-username-error' : undefined"
                @blur="validateField('username')"
              >
              <p v-if="errors.username" id="auth-username-error" class="auth-field-error">{{ errors.username }}</p>
            </div>

            <div v-if="isRegister" class="auth-field">
              <label for="auth-nickname">昵称</label>
              <input
                id="auth-nickname"
                v-model="form.nickname"
                name="nickname"
                type="text"
                autocomplete="nickname"
                maxlength="20"
                required
                :disabled="submitting"
                placeholder="希望我们如何称呼你"
                :aria-invalid="Boolean(errors.nickname)"
                :aria-describedby="errors.nickname ? 'auth-nickname-error' : undefined"
                @blur="validateField('nickname')"
              >
              <p v-if="errors.nickname" id="auth-nickname-error" class="auth-field-error">{{ errors.nickname }}</p>
            </div>

            <div v-if="isRegister" class="auth-field">
              <label for="auth-email">邮箱 <span>选填</span></label>
              <input
                id="auth-email"
                v-model="form.email"
                name="email"
                type="email"
                inputmode="email"
                autocomplete="email"
                :disabled="submitting"
                placeholder="输入你的邮箱地址"
                :aria-invalid="Boolean(errors.email)"
                :aria-describedby="errors.email ? 'auth-email-error' : undefined"
                @blur="validateField('email')"
              >
              <p v-if="errors.email" id="auth-email-error" class="auth-field-error">{{ errors.email }}</p>
            </div>

            <div class="auth-field">
              <label for="auth-password">密码</label>
              <div class="auth-password">
                <input
                  id="auth-password"
                  ref="passwordInput"
                  v-model="form.password"
                  name="password"
                  :type="passwordVisible ? 'text' : 'password'"
                  :autocomplete="isRegister ? 'new-password' : 'current-password'"
                  :maxlength="isRegister ? 20 : undefined"
                  :disabled="submitting"
                  :placeholder="isRegister ? '设置 6–20 位密码' : '输入你的密码'"
                  :aria-invalid="Boolean(errors.password)"
                  :aria-describedby="errors.password ? 'auth-password-error' : undefined"
                  required
                  @blur="validateField('password')"
                >
                <button
                  type="button"
                  class="auth-password__toggle"
                  :disabled="submitting"
                  :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                  :aria-pressed="passwordVisible"
                  @click="passwordVisible = !passwordVisible"
                >{{ passwordVisible ? '隐藏' : '显示' }}</button>
              </div>
              <p v-if="errors.password" id="auth-password-error" class="auth-field-error">{{ errors.password }}</p>
            </div>

            <button class="auth-submit" type="submit" :disabled="submitting">
              <span v-if="submitting" class="auth-submit__spinner" aria-hidden="true" />
              <span>{{ submitting ? (isRegister ? '正在创建账号…' : '正在登录…') : (isRegister ? '创建账号' : '登录学习空间') }}</span>
              <AppIcon v-if="!submitting" name="chevron-right" :size="18" />
            </button>
          </form>

          <div class="auth-switch">
            <span>{{ isRegister ? '已经有账号？' : '第一次来到这里？' }}</span>
            <button type="button" :disabled="submitting" @click="toggleMode(!isRegister)">{{ isRegister ? '返回登录' : '创建账号' }}</button>
          </div>
        </div>
        <p class="login-form-footnote">从一份资料开始，积累属于你的知识。</p>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { loginApi, registerApi } from '@/api/modules/auth'
import { useUserStore } from '@/stores/user'
import AppIcon from '@/components/AppIcon.vue'

type AuthField = 'username' | 'nickname' | 'email' | 'password'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isRegister = ref(false)
const submitting = ref(false)
const passwordVisible = ref(false)
const submitError = ref('')
const registrationNotice = ref('')
const usernameInput = ref<HTMLInputElement | null>(null)
const passwordInput = ref<HTMLInputElement | null>(null)
const form = reactive({ username: '', password: '', nickname: '', email: '' })
const errors = reactive<Record<AuthField, string>>({ username: '', nickname: '', email: '', password: '' })
const learningSteps = [
  { icon: 'materials', title: '把资料读清楚', description: '整理课程资料，用 AI 提炼核心知识。' },
  { icon: 'practice', title: '让理解更扎实', description: '围绕学习内容出题，在练习中检验理解。' },
  { icon: 'mastery', title: '把薄弱点补起来', description: '回看错题与掌握度，找到下一步方向。' }
]

const validateField = (field: AuthField) => {
  let message = ''
  const value = form[field].trim()
  if (field === 'username') {
    if (!value) message = '请输入用户名。'
    else if (isRegister.value && (value.length < 4 || value.length > 20)) message = '用户名需要 4–20 个字符。'
  } else if (field === 'password') {
    if (!value) message = '请输入密码。'
    else if (isRegister.value && (form.password.length < 6 || form.password.length > 20)) message = '密码需要 6–20 个字符。'
  } else if (field === 'nickname' && isRegister.value) {
    if (!value) message = '请填写用于展示的昵称。'
    else if (value.length > 20) message = '昵称请保持在 20 个字符以内。'
  } else if (field === 'email' && isRegister.value && value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    message = '请检查邮箱格式，例如 name@example.com。'
  }
  errors[field] = message
  return !message
}

const toggleMode = async (value: boolean) => {
  if (submitting.value) return
  isRegister.value = value
  passwordVisible.value = false
  submitError.value = ''
  registrationNotice.value = ''
  for (const field of Object.keys(errors) as AuthField[]) errors[field] = ''
  await nextTick()
  usernameInput.value?.focus()
}

const submit = async () => {
  if (submitting.value) return
  submitError.value = ''
  const fields: AuthField[] = isRegister.value ? ['username', 'nickname', 'email', 'password'] : ['username', 'password']
  const invalidFields = fields.filter((field) => !validateField(field))
  if (invalidFields.length) {
    await nextTick()
    document.getElementById('auth-' + invalidFields[0])?.focus()
    return
  }

  submitting.value = true
  try {
    if (isRegister.value) {
      await registerApi({ username: form.username.trim(), password: form.password, nickname: form.nickname.trim(), email: form.email.trim() })
      isRegister.value = false
      passwordVisible.value = false
      form.password = ''
      registrationNotice.value = '账号已创建。输入密码，开始你的学习之旅。'
      return
    }

    const loginRes = await loginApi({ username: form.username.trim(), password: form.password })
    const loginData = loginRes.data?.data
    if (!loginData?.token) throw new Error('暂时无法完成登录，请重试。')
    userStore.setLogin(loginData.token, { id: loginData.userId, username: loginData.username, nickname: loginData.nickname })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//') && route.query.redirect !== '/login'
      ? route.query.redirect
      : '/dashboard'
    await router.replace(redirect)
  } catch (error) {
    submitError.value = error instanceof Error ? error.message : '操作失败，请稍后重试。'
  } finally {
    submitting.value = false
    if (registrationNotice.value) {
      await nextTick()
      passwordInput.value?.focus()
    }
  }
}
</script>

<style scoped>
.login-page {
  --login-story-bg: var(--brand-light, #eef3ec);
  min-height: 100svh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: var(--bg);
}

.login-shell {
  display: grid;
  grid-template-columns: 1.06fr 1fr;
  width: min(1160px, 100%);
  min-height: min(780px, calc(100svh - 64px));
  border: 1px solid var(--line);
  border-radius: 24px;
  overflow: hidden;
  background: var(--panel);
  box-shadow: 0 20px 70px rgb(38 54 40 / 4%);
}

.login-story {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 44px 48px 32px;
  background: var(--login-story-bg);
  border-right: 1px solid var(--line);
}

.login-brand { display: flex; align-items: center; gap: 12px; }
.login-brand__mark { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 13px; color: #fff; background: var(--brand); }
.login-brand > div { display: flex; flex-direction: column; gap: 5px; }
.login-brand strong { color: var(--text); font-size: 18px; letter-spacing: -.3px; }
.login-brand div > span { color: var(--text-secondary); font-size: 11px; }
.login-story__copy { margin: 60px 0 34px; }
.login-eyebrow { color: var(--brand); font-size: 12px; font-weight: 600; letter-spacing: 2px; }
.login-story h1 { margin: 18px 0 22px; color: var(--text); font-size: clamp(36px, 3.8vw, 54px); line-height: 1.28; letter-spacing: -1.5px; font-weight: 650; }
.login-story__copy > p { margin: 0; color: var(--text-secondary); font-size: 14px; line-height: 1.9; }
.learning-path { padding: 18px 22px 6px; border: 1px solid var(--line); border-radius: 16px; background: var(--panel); }
.learning-path__heading { display: flex; align-items: center; justify-content: space-between; padding-bottom: 8px; color: var(--brand); font-size: 12px; font-weight: 600; }
.learning-path__step { display: grid; grid-template-columns: 36px minmax(0, 1fr) 22px; align-items: center; gap: 12px; padding: 15px 0; }
.learning-path__step + .learning-path__step { border-top: 1px solid var(--line); }
.learning-path__icon { display: grid; place-items: center; width: 36px; height: 36px; background: var(--brand-light); color: var(--brand); border-radius: 10px; }
.learning-path__step strong { color: var(--text); font-size: 13px; font-weight: 600; }
.learning-path__step p { margin: 5px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.65; }
.learning-path__number { color: var(--muted); font-size: 11px; font-variant-numeric: tabular-nums; }
.login-story__footnote { display: flex; align-items: center; gap: 16px; margin: auto 0 0; padding-top: 28px; color: var(--text-secondary); font-size: 12px; }
.login-story__footnote span { color: var(--brand); }

.login-form-panel { display: flex; flex-direction: column; justify-content: center; min-width: 0; padding: 66px 56px 28px; }
.login-form-content { width: min(360px, 100%); margin: auto; padding: 20px 0; }
.login-form-eyebrow { color: var(--brand); font-size: 12px; font-weight: 600; letter-spacing: 1px; }
.login-form-content h2 { margin: 14px 0 12px; color: var(--text); font-size: 30px; line-height: 1.35; font-weight: 650; letter-spacing: -.8px; }
.login-form-description { margin: 0 0 30px; color: var(--text-secondary); font-size: 14px; line-height: 1.8; }
.auth-form { display: grid; grid-template-columns: minmax(0,1fr); justify-content: stretch; gap: 20px; padding: 0; }
.auth-field { min-width: 0; }
.auth-field label { display: block; margin-bottom: 9px; color: var(--text); font-size: 13px; font-weight: 600; }
.auth-field label > span { margin-left: 5px; color: var(--muted); font-size: 12px; font-weight: 400; }
.auth-field input { width: 100%; height: 48px; padding: 0 14px; border: 1px solid var(--line); border-radius: 9px; outline: none; background: var(--panel); color: var(--text); font: inherit; font-size: 14px; transition: border-color .18s ease, box-shadow .18s ease; }
.auth-field input::placeholder { color: var(--muted); }
.auth-field input:hover { border-color: var(--muted); }
.auth-field input:focus { border-color: var(--brand); box-shadow: 0 0 0 3px var(--brand-soft); }
.auth-field input[aria-invalid="true"] { border-color: var(--red); }
.auth-field input:disabled { cursor: wait; background: var(--bg-secondary); opacity: .7; }
.auth-password { position: relative; }
.auth-password input { padding-right: 64px; }
.auth-password__toggle { position: absolute; top: 2px; right: 3px; min-width: 54px; min-height: 44px; padding: 0 10px; border: 0; border-radius: 7px; background: transparent; color: var(--text-secondary); font: inherit; font-size: 12px; cursor: pointer; }
.auth-password__toggle:hover { color: var(--brand); }
.auth-field-error { margin: 7px 0 0; color: var(--danger-text, #a13c35); font-size: 12px; line-height: 1.6; }
.auth-notice { margin: 0 0 22px; padding: 12px 14px; border-radius: 9px; font-size: 13px; line-height: 1.65; }
.auth-notice--error { color: var(--danger-text, #a13c35); background: var(--red-soft); }
.auth-notice--success { color: var(--brand); background: var(--brand-light); }
.auth-submit { display: flex; align-items: center; justify-content: center; gap: 10px; min-height: 48px; margin-top: 5px; padding: 12px 20px; border: 1px solid var(--brand); border-radius: 9px; background: var(--brand); color: #fff; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; transition: background .18s ease, border-color .18s ease; }
.auth-submit:hover { background: var(--brand-hover); border-color: var(--brand-hover); }
.auth-submit:disabled { cursor: wait; opacity: .7; }
.auth-submit__spinner { width: 16px; height: 16px; border: 2px solid rgb(255 255 255 / 35%); border-top-color: currentColor; border-radius: 50%; animation: auth-spin .7s linear infinite; }
.auth-switch { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 4px; margin-top: 22px; color: var(--text-secondary); font-size: 13px; }
.auth-switch button { min-height: 44px; padding: 0 8px; border: 0; border-radius: 7px; background: transparent; color: var(--brand); font: inherit; font-weight: 600; cursor: pointer; }
.auth-switch button:hover { background: var(--brand-light); }
.auth-switch button:disabled, .auth-password__toggle:disabled { cursor: wait; opacity: .6; }
.auth-switch button:focus-visible, .auth-password__toggle:focus-visible, .auth-submit:focus-visible { outline: 3px solid var(--brand); outline-offset: 3px; }
.login-form-footnote { margin: 24px 0 0; color: var(--muted); text-align: center; font-size: 12px; line-height: 1.7; }

@keyframes auth-spin { to { transform: rotate(360deg); } }

@media (max-width: 1000px) {
  .login-page { padding: 24px; }
  .login-story { padding: 36px 32px 28px; }
  .login-form-panel { padding: 44px 36px 28px; }
  .login-story__copy { margin-top: 48px; }
}

@media (max-width: 720px) {
  .login-page { display: block; padding: 0; }
  .login-shell { grid-template-columns: 1fr; min-height: 100svh; border: 0; border-radius: 0; box-shadow: none; }
  .login-story { padding: 24px 28px; border-right: 0; border-bottom: 1px solid var(--line); }
  .login-brand__mark { width: 36px; height: 36px; border-radius: 10px; }
  .login-brand strong { font-size: 17px; }
  .login-story__copy { margin: 28px 0 0; }
  .login-eyebrow { font-size: 11px; letter-spacing: 1px; }
  .login-story h1 { margin: 10px 0 12px; font-size: 32px; line-height: 1.3; letter-spacing: -.8px; }
  .login-story h1 br { display: none; }
  .login-story__copy > p { font-size: 13px; line-height: 1.75; }
  .learning-path, .login-story__footnote { display: none; }
  .login-form-panel { padding: 30px 28px calc(22px + env(safe-area-inset-bottom)); }
  .login-form-content { padding: 0; margin: 0 auto; }
  .login-form-content h2 { margin-top: 10px; font-size: 27px; }
  .login-form-description { margin-bottom: 25px; font-size: 13px; }
  .auth-field input { font-size: 16px; }
  .login-form-footnote { margin-top: 28px; font-size: 11px; }
}

@media (prefers-reduced-motion: reduce) {
  .auth-submit__spinner { animation: none; }
  .auth-field input, .auth-submit { transition: none; }
}
</style>
