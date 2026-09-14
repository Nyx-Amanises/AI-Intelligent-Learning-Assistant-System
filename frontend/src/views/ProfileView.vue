<template>
  <section class="profile-page" aria-labelledby="profile-title">
    <header class="page-header">
      <div>
        <h1 id="profile-title" class="page-title">个人信息</h1>
        <p class="page-desc">管理你的头像、昵称和联系邮箱。</p>
      </div>
    </header>

    <div class="page-card profile-card">
      <div v-if="loading" class="profile-state" role="status" aria-live="polite">
        <AppIcon name="user" :size="28" />
        <p>正在加载个人信息…</p>
      </div>
      <div v-else-if="loadError" class="profile-state" role="alert">
        <AppIcon name="user" :size="28" />
        <h2>个人信息暂时无法加载</h2>
        <p>{{ loadError }}</p>
        <el-button native-type="button" @click="loadProfile">重新加载</el-button>
      </div>
      <template v-else-if="loaded">
        <section class="profile-avatar-row" aria-labelledby="profile-avatar-title" :aria-busy="avatarUploading">
          <div class="profile-avatar" aria-hidden="true">
            <img v-if="avatarSrc && !avatarLoadFailed" :src="avatarSrc" alt="" width="88" height="88" @error="avatarLoadFailed = true">
            <span v-else>{{ avatarText }}</span>
          </div>
          <div class="profile-avatar-content">
            <h2 id="profile-avatar-title">头像</h2>
            <p id="profile-avatar-help">支持 JPG、PNG、WebP、GIF，图片不超过 5 MB。</p>
            <el-button native-type="button" :loading="avatarUploading" :disabled="busy" aria-describedby="profile-avatar-help" @click="avatarInputRef?.click()">
              <AppIcon v-if="!avatarUploading" name="camera" :size="17" />
              {{ avatarUploading ? '正在上传…' : '更换头像' }}
            </el-button>
            <p v-if="avatarFeedback" class="profile-feedback" :class="{ 'profile-feedback--error': avatarFeedback.type === 'error' }" :role="avatarFeedback.type === 'error' ? 'alert' : 'status'">
              {{ avatarFeedback.message }}
            </p>
          </div>
          <input ref="avatarInputRef" type="file" accept="image/jpeg,image/png,image/webp,image/gif" class="visually-hidden" tabindex="-1" aria-hidden="true" :disabled="busy" @change="handleAvatarChange">
        </section>

        <form class="profile-form" novalidate :aria-busy="submitting" @submit.prevent="saveProfile">
          <h2 class="profile-section-title">基本资料</h2>
          <fieldset :disabled="busy">
            <legend class="visually-hidden">账号与个人信息</legend>
            <div class="profile-field">
              <label for="profile-username">账号<span class="profile-label-detail">不可修改</span></label>
              <input id="profile-username" :value="userStore.profile?.username || ''" type="text" readonly autocomplete="username" aria-describedby="profile-username-help">
              <p id="profile-username-help" class="profile-field-help">用于登录的账号。</p>
            </div>
            <div class="profile-field">
              <label for="profile-nickname">昵称<span class="profile-label-detail">必填</span></label>
              <input id="profile-nickname" ref="nicknameInputRef" v-model="form.nickname" type="text" autocomplete="nickname" maxlength="20" required :aria-invalid="Boolean(fieldErrors.nickname)" :aria-describedby="'profile-nickname-help' + (fieldErrors.nickname ? ' profile-nickname-error' : '')" @blur="validateField('nickname')" @input="handleFieldInput('nickname')">
              <p id="profile-nickname-help" class="profile-field-help">1–20 个字符。</p>
              <p v-if="fieldErrors.nickname" id="profile-nickname-error" class="profile-field-error" role="alert">{{ fieldErrors.nickname }}</p>
            </div>
            <div class="profile-field">
              <label for="profile-email">邮箱<span class="profile-label-detail">选填</span></label>
              <input id="profile-email" ref="emailInputRef" v-model="form.email" type="email" autocomplete="email" inputmode="email" maxlength="100" spellcheck="false" :aria-invalid="Boolean(fieldErrors.email)" :aria-describedby="'profile-email-help' + (fieldErrors.email ? ' profile-email-error' : '')" @blur="validateField('email')" @input="handleFieldInput('email')">
              <p id="profile-email-help" class="profile-field-help">留空保存，可清除已填写的邮箱。</p>
              <p v-if="fieldErrors.email" id="profile-email-error" class="profile-field-error" role="alert">{{ fieldErrors.email }}</p>
            </div>

            <p v-if="saveFeedback?.type === 'error'" class="profile-feedback profile-feedback--error" role="alert">{{ saveFeedback.message }} 请重试。</p>
            <div class="profile-form-actions">
              <p class="profile-save-status" role="status" aria-live="polite">
                <AppIcon v-if="!dirty && !submitting" name="check" :size="16" />
                {{ submitting ? '正在保存…' : saveFeedback?.type === 'success' ? saveFeedback.message : dirty ? '有未保存的更改' : '信息已保存' }}
              </p>
              <div class="profile-form-buttons">
                <el-button native-type="button" :disabled="!dirty || busy" @click="resetForm">撤销修改</el-button>
                <el-button type="primary" native-type="submit" :loading="submitting" :disabled="!dirty || busy">{{ submitting ? '正在保存…' : '保存修改' }}</el-button>
              </div>
            </div>
          </fieldset>
        </form>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { getProfileApi, updateProfileApi, uploadAvatarApi } from '@/api/modules/auth'
import AppIcon from '@/components/AppIcon.vue'
import { useUserStore, type UserProfile } from '@/stores/user'
import { resolveAvatarUrl } from '@/utils/avatar'

type FieldName = 'nickname' | 'email'
type Feedback = { type: 'success' | 'error'; message: string }

const userStore = useUserStore()
const form = reactive({ nickname: '', email: '' })
const savedForm = reactive({ nickname: '', email: '' })
const fieldErrors = reactive({ nickname: '', email: '' })
const loading = ref(false)
const loaded = ref(false)
const submitting = ref(false)
const avatarUploading = ref(false)
const loadError = ref('')
const saveFeedback = ref<Feedback | null>(null)
const avatarFeedback = ref<Feedback | null>(null)
const avatarLoadFailed = ref(false)
const avatarInputRef = ref<HTMLInputElement | null>(null)
const nicknameInputRef = ref<HTMLInputElement | null>(null)
const emailInputRef = ref<HTMLInputElement | null>(null)
const busy = computed(() => loading.value || submitting.value || avatarUploading.value)
const dirty = computed(() => form.nickname.trim() !== savedForm.nickname || form.email.trim() !== savedForm.email)
const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '学习者')
const avatarText = computed(() => Array.from(displayName.value)[0]?.toUpperCase() || '学')
const avatarSrc = computed(() => resolveAvatarUrl(userStore.profile?.avatarUrl))
let active = true

function updateSavedProfile(profile: UserProfile) {
  userStore.setProfile(profile)
  savedForm.nickname = profile.nickname?.trim() || ''
  savedForm.email = profile.email?.trim() || ''
}

function resetForm() {
  form.nickname = savedForm.nickname
  form.email = savedForm.email
  fieldErrors.nickname = ''
  fieldErrors.email = ''
  saveFeedback.value = null
}

function validateField(field: FieldName) {
  const value = form[field].trim()
  if (field === 'nickname') {
    fieldErrors.nickname = !value ? '请填写昵称' : value.length > 20 ? '昵称不能超过 20 个字符' : ''
  } else {
    fieldErrors.email = value.length > 100
      ? '邮箱不能超过 100 个字符'
      : value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) ? '请填写有效的邮箱地址' : ''
  }
  return !fieldErrors[field]
}

function handleFieldInput(field: FieldName) {
  saveFeedback.value = null
  if (fieldErrors[field]) validateField(field)
}

async function loadProfile() {
  if (busy.value) return
  loading.value = true
  loadError.value = ''
  const requestToken = userStore.token
  try {
    const response = await getProfileApi()
    if (!active || !requestToken || userStore.token !== requestToken) return
    const profile = response.data.data as UserProfile | undefined
    if (!profile) throw new Error('未能读取个人信息，请重新加载。')
    updateSavedProfile(profile)
    resetForm()
    loaded.value = true
  } catch (error) {
    if (active && userStore.token === requestToken) {
      loadError.value = error instanceof Error ? error.message : '请检查网络后重试。'
    }
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (busy.value || !loaded.value || !dirty.value) return
  const nicknameValid = validateField('nickname')
  const emailValid = validateField('email')
  if (!nicknameValid || !emailValid) {
    const invalidInput = !nicknameValid ? nicknameInputRef.value : emailInputRef.value
    invalidInput?.focus()
    return
  }
  submitting.value = true
  saveFeedback.value = null
  const requestToken = userStore.token
  try {
    const response = await updateProfileApi({ nickname: form.nickname.trim(), email: form.email.trim() || null })
    if (!requestToken || userStore.token !== requestToken) return
    const profile = response.data.data as UserProfile | undefined
    if (!profile) throw new Error('未能保存个人信息。')
    updateSavedProfile(profile)
    resetForm()
    saveFeedback.value = { type: 'success', message: '个人信息已保存' }
  } catch (error) {
    if (active && userStore.token === requestToken) {
      saveFeedback.value = { type: 'error', message: error instanceof Error ? error.message : '保存失败。' }
    }
  } finally {
    submitting.value = false
  }
}

async function handleAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || busy.value || !loaded.value) return
  avatarFeedback.value = null
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    avatarFeedback.value = { type: 'error', message: '请选择 JPG、PNG、WebP 或 GIF 图片。' }
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    avatarFeedback.value = { type: 'error', message: '图片不能超过 5 MB，请选择较小的图片。' }
    return
  }
  if (!file.size) {
    avatarFeedback.value = { type: 'error', message: '图片为空，请重新选择。' }
    return
  }
  avatarUploading.value = true
  const requestToken = userStore.token
  try {
    const response = await uploadAvatarApi(file)
    if (!requestToken || userStore.token !== requestToken) return
    const profile = response.data.data as UserProfile | undefined
    if (!profile) throw new Error('未能更新头像。')
    updateSavedProfile(profile)
    avatarFeedback.value = { type: 'success', message: '头像已更新' }
  } catch (error) {
    if (active && userStore.token === requestToken) {
      avatarFeedback.value = { type: 'error', message: (error instanceof Error ? error.message : '头像上传失败。') + ' 请重新选择图片后重试。' }
    }
  } finally {
    avatarUploading.value = false
  }
}

watch(avatarSrc, () => { avatarLoadFailed.value = false })
onMounted(() => { void loadProfile() })
onUnmounted(() => { active = false })
</script>

<style scoped>
.profile-page { max-width: 820px; }
.profile-card { padding: 32px; }
.profile-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 340px; gap: 12px; color: var(--muted); text-align: center; }
.profile-state h2 { margin: 0; color: var(--text); font-size: 18px; font-weight: 600; }
.profile-state p { margin: 0 0 8px; overflow-wrap: anywhere; }
.profile-avatar-row { display: flex; align-items: flex-start; gap: 24px; padding-bottom: 28px; border-bottom: 1px solid var(--line); }
.profile-avatar { display: grid; place-items: center; flex: 0 0 88px; width: 88px; height: 88px; border: 1px solid var(--line); border-radius: 50%; background: var(--brand-soft); color: var(--brand); font-size: 32px; font-weight: 600; overflow: hidden; }
.profile-avatar img { display: block; width: 100%; height: 100%; object-fit: cover; }
.profile-avatar-content { min-width: 0; }
.profile-avatar-content h2 { margin: 0; color: var(--text); font-size: 16px; font-weight: 600; }
.profile-avatar-content > p { margin: 5px 0 14px; color: var(--muted); font-size: 13px; line-height: 1.7; }
.profile-form { margin-top: 28px; }
.profile-section-title { margin: 0 0 22px; color: var(--text); font-size: 17px; font-weight: 600; }
.profile-form fieldset { min-width: 0; margin: 0; padding: 0; border: 0; }
.profile-field { margin-bottom: 22px; }
.profile-field label { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; color: var(--text); font-weight: 500; }
.profile-label-detail { color: var(--muted); font-size: 12px; font-weight: 400; }
.profile-field input { box-sizing: border-box; width: 100%; min-height: 46px; padding: 10px 13px; border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); background: var(--panel); color: var(--text); font: inherit; line-height: 1.5; transition: border-color .18s, background-color .18s; }
.profile-field input:hover:not(:disabled):not(:read-only) { border-color: var(--muted); }
.profile-field input:focus { outline: 2px solid var(--brand); outline-offset: 2px; border-color: var(--brand); }
.profile-field input[readonly] { background: var(--bg-secondary); color: var(--text-secondary); }
.profile-field input:disabled { cursor: wait; color: var(--muted); background: var(--bg-secondary); }
.profile-field input[aria-invalid="true"] { border-color: var(--red); }
.profile-field-help, .profile-field-error { margin: 6px 0 0; font-size: 13px; line-height: 1.7; }
.profile-field-help { color: var(--muted); }
.profile-field-error { color: var(--red); }
.profile-feedback { margin: 14px 0 0; color: var(--brand); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.profile-avatar-content > .profile-feedback { margin-bottom: 0; color: var(--brand); }
.profile-feedback--error, .profile-avatar-content > .profile-feedback--error { color: var(--red); }
.profile-form-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 28px; padding-top: 24px; border-top: 1px solid var(--line); }
.profile-save-status { display: flex; align-items: center; gap: 6px; margin: 0; color: var(--muted); font-size: 13px; }
.profile-save-status .app-icon { flex-shrink: 0; color: var(--brand); }
.profile-form-buttons { display: flex; align-items: center; flex-shrink: 0; gap: 8px; }
.profile-page :deep(.el-button) { min-height: 44px; }
.profile-form-buttons :deep(.el-button + .el-button) { margin-left: 0; }
@media (max-width: 600px) {
  .profile-card { padding: 24px 20px; }
  .profile-avatar-row { gap: 16px; }
  .profile-avatar { width: 72px; height: 72px; flex-basis: 72px; font-size: 28px; }
  .profile-field input { font-size: 16px; }
  .profile-form-actions { align-items: stretch; flex-direction: column; }
  .profile-form-buttons { width: 100%; }
  .profile-form-buttons :deep(.el-button) { flex: 1; padding-inline: 10px; }
}
@media (prefers-reduced-motion: reduce) {
  .profile-field input { transition: none; }
}
</style>
