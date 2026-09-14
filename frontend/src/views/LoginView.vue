<template>
  <main class="login-page">
    <div class="login-shell" :class="{ 'login-shell--register': isRegister }">
      <section class="login-form-panel" aria-labelledby="login-form-title">
        <div class="login-brand">
          <BrandLogo class="login-brand__logo" />
        </div>

        <div class="login-form-content">
          <header class="login-form-heading">
            <p v-if="!isRegister" class="login-form-eyebrow">WELCOME BACK</p>
            <h1 id="login-form-title">{{ isRegister ? '注册' : '欢迎回来' }}</h1>
          </header>

          <p v-if="registrationNotice" class="auth-notice auth-notice--success" role="status">{{ registrationNotice }}</p>
          <p v-if="submitError" class="auth-notice auth-notice--error" role="alert">{{ submitError }}</p>

          <form class="auth-form" novalidate :aria-busy="submitting" @submit.prevent="submit">
            <div class="auth-name-row" :class="{ 'auth-name-row--register': isRegister }">
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
                  :placeholder="isRegister ? '4–20 位用户名' : '请输入你的用户名'"
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
                  placeholder="怎么称呼你"
                  :aria-invalid="Boolean(errors.nickname)"
                  :aria-describedby="errors.nickname ? 'auth-nickname-error' : undefined"
                  @blur="validateField('nickname')"
                >
                <p v-if="errors.nickname" id="auth-nickname-error" class="auth-field-error">{{ errors.nickname }}</p>
              </div>
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
                  :minlength="isRegister ? 8 : undefined"
                  :maxlength="isRegister ? 20 : undefined"
                  :disabled="submitting"
                  :placeholder="isRegister ? '设置密码' : '请输入你的密码'"
                  :aria-invalid="Boolean(errors.password)"
                  :aria-describedby="passwordDescription"
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
                >
                  <AppIcon :name="passwordVisible ? 'eye-off' : 'eye'" :size="19" />
                </button>
              </div>
              <p v-if="isRegister" id="auth-password-hint" class="auth-field-hint">8–20 位，至少包含字母和数字，可使用符号。</p>
              <p v-if="errors.password" id="auth-password-error" class="auth-field-error">{{ errors.password }}</p>
            </div>

            <div v-if="isRegister" class="auth-field">
              <label for="auth-confirmPassword">确认密码</label>
              <div class="auth-password">
                <input
                  id="auth-confirmPassword"
                  v-model="form.confirmPassword"
                  name="confirmPassword"
                  :type="confirmPasswordVisible ? 'text' : 'password'"
                  autocomplete="new-password"
                  minlength="8"
                  maxlength="20"
                  :disabled="submitting"
                  placeholder="再次输入密码"
                  :aria-invalid="Boolean(errors.confirmPassword)"
                  :aria-describedby="errors.confirmPassword ? 'auth-confirmPassword-error' : undefined"
                  required
                  @blur="validateField('confirmPassword')"
                >
                <button
                  type="button"
                  class="auth-password__toggle"
                  :disabled="submitting"
                  :aria-label="confirmPasswordVisible ? '隐藏确认密码' : '显示确认密码'"
                  :aria-pressed="confirmPasswordVisible"
                  @click="confirmPasswordVisible = !confirmPasswordVisible"
                >
                  <AppIcon :name="confirmPasswordVisible ? 'eye-off' : 'eye'" :size="19" />
                </button>
              </div>
              <p v-if="errors.confirmPassword" id="auth-confirmPassword-error" class="auth-field-error">{{ errors.confirmPassword }}</p>
            </div>

            <button class="auth-submit" type="submit" :disabled="submitting">
              <span v-if="submitting" class="auth-submit__spinner" aria-hidden="true" />
              <span>{{ submitting ? (isRegister ? '正在创建账号…' : '正在登录…') : (isRegister ? '创建账号' : '登录') }}</span>
              <AppIcon v-if="!submitting" name="arrow-right" :size="18" />
            </button>
          </form>

          <div class="auth-switch">
            <span>{{ isRegister ? '已经有账号？' : '还没有账号？' }}</span>
            <button type="button" :disabled="submitting" @click="toggleMode(!isRegister)">
              {{ isRegister ? '返回登录' : '创建账号' }}
              <AppIcon name="arrow-up-right" :size="13" />
            </button>
          </div>
        </div>
      </section>

      <aside class="login-visual" aria-labelledby="login-visual-title">
        <img
          class="login-visual__image"
          :src="botanicalPhoto"
          alt=""
          width="1400"
          height="1051"
          fetchpriority="high"
          decoding="async"
          draggable="false"
        >
        <figure class="login-visual__copy" aria-live="polite" aria-atomic="true">
          <blockquote class="login-visual__quote">
            <p id="login-visual-title">{{ dailyQuote.text }}</p>
          </blockquote>
          <figcaption class="login-visual__attribution">
            <span>— {{ dailyQuote.author }}</span>
            <cite>《{{ dailyQuote.source }}》</cite>
          </figcaption>
        </figure>
      </aside>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { loginApi, registerApi } from '@/api/modules/auth'
import { useUserStore } from '@/stores/user'
import { useDailyQuote } from '@/composables/useDailyQuote'
import { APP_NAME } from '@/config/brand'
import AppIcon from '@/components/AppIcon.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import botanicalPhoto from '@/assets/auth/botanical-study.webp'

type AuthField = 'username' | 'nickname' | 'password' | 'confirmPassword'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const dailyQuote = useDailyQuote()
const isRegister = ref(false)
const submitting = ref(false)
const passwordVisible = ref(false)
const confirmPasswordVisible = ref(false)
const submitError = ref('')
const registrationNotice = ref('')
const usernameInput = ref<HTMLInputElement | null>(null)
const passwordInput = ref<HTMLInputElement | null>(null)
const form = reactive({ username: '', password: '', confirmPassword: '', nickname: '' })
const errors = reactive<Record<AuthField, string>>({ username: '', nickname: '', password: '', confirmPassword: '' })
const passwordDescription = computed(() => [
  isRegister.value ? 'auth-password-hint' : '',
  errors.password ? 'auth-password-error' : ''
].filter(Boolean).join(' ') || undefined)
const validateField = (field: AuthField) => {
  let message = ''
  const value = form[field].trim()
  if (field === 'username') {
    if (!value) message = '请输入用户名。'
    else if (isRegister.value && (value.length < 4 || value.length > 20)) message = '用户名需要 4–20 个字符。'
  } else if (field === 'password') {
    if (!value) message = '请输入密码。'
    else if (isRegister.value && (form.password.length < 8 || form.password.length > 20)) message = '密码需要 8–20 个字符。'
    else if (isRegister.value && (!/[A-Za-z]/.test(form.password) || !/[0-9]/.test(form.password))) message = '密码需同时包含字母和数字。'
  } else if (field === 'confirmPassword' && isRegister.value) {
    if (!value) message = '请再次输入密码。'
    else if (form.confirmPassword !== form.password) message = '两次输入的密码不一致。'
  } else if (field === 'nickname' && isRegister.value) {
    if (!value) message = '请填写用于展示的昵称。'
    else if (value.length > 20) message = '昵称请保持在 20 个字符以内。'
  }
  errors[field] = message
  return !message
}

const toggleMode = async (value: boolean) => {
  if (submitting.value) return
  isRegister.value = value
  passwordVisible.value = false
  confirmPasswordVisible.value = false
  form.password = ''
  form.confirmPassword = ''
  submitError.value = ''
  registrationNotice.value = ''
  for (const field of Object.keys(errors) as AuthField[]) errors[field] = ''
  await nextTick()
  usernameInput.value?.focus()
}

const submit = async () => {
  if (submitting.value) return
  submitError.value = ''
  const fields: AuthField[] = isRegister.value ? ['username', 'nickname', 'password', 'confirmPassword'] : ['username', 'password']
  const invalidFields = fields.filter((field) => !validateField(field))
  if (invalidFields.length) {
    await nextTick()
    document.getElementById('auth-' + invalidFields[0])?.focus()
    return
  }

  submitting.value = true
  try {
    if (isRegister.value) {
      await registerApi({ username: form.username.trim(), password: form.password, confirmPassword: form.confirmPassword, nickname: form.nickname.trim() })
      isRegister.value = false
      passwordVisible.value = false
      confirmPasswordVisible.value = false
      form.password = ''
      form.confirmPassword = ''
      registrationNotice.value = '注册成功，请登录。'
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

watch(() => form.password, () => {
  if (isRegister.value && form.confirmPassword) validateField('confirmPassword')
})

watch(isRegister, (registering) => {
  document.title = `${registering ? '注册' : '登录'} · ${APP_NAME}`
}, { immediate: true })
</script>

<style scoped>
.login-page {
  --auth-ink: #263d30;
  --auth-muted: #737c73;
  --auth-green: #315a3e;
  --auth-line: #dfe4dc;
  min-height: 100svh;
  display: grid;
  place-items: center;
  padding: 40px 32px;
  background: #f3f4ef;
  color: var(--auth-ink);
  font-family: "Microsoft YaHei", "PingFang SC", "Segoe UI", sans-serif;
}

.login-shell {
  display: grid;
  grid-template-columns: 1.02fr 1fr;
  width: min(1180px, 100%);
  min-height: 704px;
  padding: 12px;
  border: 1px solid #e4e7de;
  border-radius: 28px;
  background: #fffefa;
  box-shadow: 0 24px 70px -28px rgb(41 58 35 / 15%), 0 2px 8px rgb(41 58 35 / 2%);
  animation: auth-reveal .5s ease-out both;
}

.login-form-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 32px 52px 25px;
}

.login-brand {
  display: flex;
  align-items: center;
  width: min(352px, 100%);
  margin-inline: auto;
}
.login-brand__logo { width: 184px; max-width: 100%; }
.login-form-content { width: min(352px, 100%); margin: auto; padding: 48px 0; }
.login-form-heading { margin-bottom: 32px; }
.login-form-eyebrow {
  margin: 0 0 12px;
  color: #798775;
  font: 500 10px/1.5 "Segoe UI", sans-serif;
  letter-spacing: 2.1px;
}
.login-form-heading h1 { margin: 0; font-size: 32px; font-weight: 600; line-height: 1.45; letter-spacing: -1px; }

.auth-form { display: grid; grid-template-columns: minmax(0, 1fr); gap: 23px; padding: 0; }
.auth-name-row { display: grid; grid-template-columns: minmax(0, 1fr); gap: 16px; }
.auth-name-row--register { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.auth-field { min-width: 0; }
.auth-field label { display: block; margin-bottom: 9px; color: #354438; font-size: 13px; font-weight: 500; }
.auth-field label > span { margin-left: 5px; color: var(--auth-muted); font-size: 11px; font-weight: 400; }
.auth-field input {
  width: 100%;
  height: 50px;
  padding: 0 15px;
  border: 1px solid var(--auth-line);
  border-radius: 8px;
  outline: none;
  background: #fffefa;
  color: var(--auth-ink);
  font: inherit;
  font-size: 13px;
  transition: border-color .18s ease, box-shadow .18s ease, background-color .18s ease;
}
.auth-field input::placeholder { color: #959b91; }
.auth-field input:hover { border-color: #adb9a7; }
.auth-field input:focus { border-color: var(--auth-green); background: #fff; box-shadow: 0 0 0 3px #e9efe4; }
.auth-field input[aria-invalid="true"] { border-color: #b45449; }
.auth-field input[aria-invalid="true"]:focus { box-shadow: 0 0 0 3px #faeeea; }
.auth-field input:disabled { cursor: wait; background: #f1f3ed; opacity: .75; }
.auth-password { position: relative; }
.auth-password input { padding-right: 50px; }
.auth-password__toggle {
  position: absolute;
  top: 3px;
  right: 3px;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #7d877a;
  cursor: pointer;
  transition: color .18s ease, background-color .18s ease;
}
.auth-password__toggle svg { width: 19px; height: 19px; }
.auth-password__toggle:hover { color: var(--auth-green); background: #f0f3ea; }
.auth-field-error { margin: 7px 0 0; color: #a13c35; font-size: 12px; line-height: 1.65; }
.auth-field-hint { margin: 8px 0 0; color: var(--auth-muted); font-size: 12px; line-height: 1.65; }
.auth-notice { margin: 0 0 22px; padding: 12px 14px; border-radius: 8px; font-size: 13px; line-height: 1.75; }
.auth-notice--error { color: #a13c35; background: #faeeea; }
.auth-notice--success { color: #28503a; background: #edf3e8; }
.auth-submit {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 50px;
  margin-top: 5px;
  padding: 12px 45px;
  border: 1px solid var(--auth-green);
  border-radius: 8px;
  background: var(--auth-green);
  color: #fff;
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 3px 7px rgb(35 66 39 / 8%);
  transition: background-color .18s ease, border-color .18s ease, box-shadow .18s ease;
}
.auth-submit > .app-icon { position: absolute; right: 18px; transition: transform .18s ease; }
.auth-submit:hover:not(:disabled) { background: #254b32; border-color: #254b32; box-shadow: 0 4px 12px rgb(35 66 39 / 16%); }
.auth-submit:hover:not(:disabled) > .app-icon { transform: translateX(3px); }
.auth-submit:disabled { cursor: wait; opacity: .72; }
.auth-submit__spinner { width: 16px; height: 16px; border: 2px solid rgb(255 255 255 / 35%); border-top-color: currentColor; border-radius: 50%; animation: auth-spin .7s linear infinite; }
.auth-switch { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 1px; margin-top: 24px; color: var(--auth-muted); font-size: 12px; }
.auth-switch button { display: inline-flex; align-items: center; gap: 4px; min-height: 44px; padding: 0 7px; border: 0; border-radius: 6px; background: transparent; color: var(--auth-green); font: inherit; font-weight: 600; cursor: pointer; }
.auth-switch button:hover { background: #edf3e8; }
.auth-switch button:disabled, .auth-password__toggle:disabled { cursor: wait; opacity: .6; }
.auth-switch button:focus-visible, .auth-password__toggle:focus-visible, .auth-submit:focus-visible { outline: 3px solid #7e9d79; outline-offset: 3px; }

.login-visual {
  position: relative;
  isolation: isolate;
  min-width: 0;
  overflow: hidden;
  border-radius: 18px;
  background: #e9ecdf;
}
.login-visual__image { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: 52% center; mix-blend-mode: multiply; filter: saturate(.82); }
.login-visual::after { position: absolute; z-index: -1; inset: 0; background: linear-gradient(180deg, rgb(239 241 230 / 30%), transparent 42%, transparent 78%, rgb(236 239 226 / 24%)); content: ""; }
.login-visual__image { z-index: -2; }
.login-visual__copy { position: relative; margin: 0; padding: 47px 42px; }
.login-visual__quote { margin: 0; }
.login-visual__quote p { margin: 0; color: #2d4e36; font-family: "Noto Serif SC", "Songti SC", "STSong", "SimSun", serif; font-size: clamp(27px, 2.6vw, 36px); font-weight: 400; line-height: 1.7; letter-spacing: .7px; text-wrap: balance; overflow-wrap: anywhere; }
.login-visual__attribution { display: flex; flex-wrap: wrap; gap: 4px 8px; margin-top: 20px; color: #4e644a; font-size: 12px; line-height: 1.8; }
.login-visual__attribution cite { font-style: normal; }
.login-shell--register .login-form-content { padding-block: 30px; }
.login-shell--register .login-form-heading { margin-bottom: 25px; }
.login-shell--register .auth-form { gap: 18px; }
.login-shell--register .auth-switch { margin-top: 17px; }

@keyframes auth-reveal { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes auth-spin { to { transform: rotate(360deg); } }

@media (max-width: 1040px) {
  .login-page { padding: 28px; }
  .login-form-panel { padding: 32px 30px 25px; }
  .login-visual__copy { padding: 45px 32px; }
  .login-visual__quote p { font-size: 30px; }
}

@media (max-width: 760px) {
  .login-page { padding: 24px; }
  .login-shell { grid-template-columns: minmax(0, 1fr); width: min(480px, 100%); min-height: min(720px, calc(100svh - 48px)); padding: 0; border-radius: 22px; }
  .login-form-panel { padding: 36px 38px 28px; }
  .login-visual { display: none; }
  .login-form-content { padding-block: 48px; }
  .auth-field input { font-size: 16px; }
  .auth-name-row--register input { font-size: 14px; }
}

@media (max-width: 480px) {
  .login-page { padding: 16px; }
  .login-shell { min-height: min(720px, calc(100svh - 32px)); border-radius: 20px; }
  .login-form-panel { padding: 30px 25px 25px; }
  .login-brand__logo { width: 168px; }
  .login-form-heading h1 { font-size: 30px; }
  .auth-name-row { gap: 13px; }
}

@media (max-width: 360px) {
  .login-page { padding: 10px; }
  .login-shell { min-height: calc(100svh - 20px); }
  .login-form-panel { padding: 26px 20px 22px; }
  .login-form-heading h1 { font-size: 27px; }
  .auth-name-row--register { grid-template-columns: minmax(0, 1fr); }
  .auth-name-row--register input { font-size: 16px; }
}

@media (max-height: 760px) and (min-width: 761px) {
  .login-page { padding-block: 24px; }
  .login-shell { min-height: 650px; }
  .login-form-content { padding-block: 30px; }
}

@media (prefers-reduced-motion: reduce) {
  .login-shell, .auth-submit__spinner { animation: none; }
  .auth-field input, .auth-submit, .auth-submit > .app-icon, .auth-password__toggle { transition: none; }
}
</style>
