<template>
  <section class="ai-settings" aria-labelledby="ai-settings-title">
    <header class="page-header ai-settings__header">
      <div>
        <h1 id="ai-settings-title" class="page-title">AI 配置</h1>
        <p class="page-desc">连接常用模型，让总结、出题和资料检索更顺手。</p>
      </div>
      <div v-if="canManageGlobal" class="config-scope" role="group" aria-label="配置范围">
        <button type="button" :aria-pressed="saveScope === 'USER'" :disabled="busy" @click="changeScope('USER')">我的配置</button>
        <button type="button" :aria-pressed="saveScope === 'GLOBAL'" :disabled="busy" @click="changeScope('GLOBAL')">共享配置</button>
      </div>
    </header>

    <div v-if="loading" class="page-card config-state" role="status" aria-live="polite">
      <AppIcon name="config" :size="28" />
      <p>正在读取模型配置…</p>
    </div>
    <div v-else-if="loadError" class="page-card config-state" role="alert">
      <h2>配置暂时无法加载</h2>
      <p>{{ loadError }}</p>
      <el-button @click="loadConfig">重新加载</el-button>
    </div>

    <template v-else-if="loaded">
      <div class="config-context">
        <span class="config-context__source"><i aria-hidden="true" />{{ sourceLabel }} · {{ savedModeLabel }}</span>
        <p>{{ scopeDescription }}</p>
      </div>

      <el-form class="config-form" :model="form" label-position="top" :disabled="saving" :aria-busy="saving" @submit.prevent="saveConfig">
        <fieldset :disabled="saving">
          <legend class="visually-hidden">模型配置</legend>
          <section class="config-mode" aria-labelledby="config-mode-title">
            <div>
              <h2 id="config-mode-title">使用方式</h2>
              <p id="config-mode-help">{{ modeDescription }}</p>
            </div>
            <div class="config-mode__choices" role="radiogroup" aria-label="AI 使用方式" aria-describedby="config-mode-help">
              <label v-for="mode in usageModes" :key="mode.value" :class="{ 'is-selected': usageMode === mode.value }">
                <input v-model="usageMode" type="radio" name="ai-usage-mode" :value="mode.value">
                <span>{{ mode.label }}</span>
              </label>
            </div>
          </section>

          <section class="page-card config-section" aria-labelledby="chat-model-title">
            <div class="config-section__heading">
              <span class="config-section__icon"><AppIcon name="assistant" :size="22" /></span>
              <div>
                <h2 id="chat-model-title">对话模型</h2>
                <p>用于 AI 总结、出题和学习助手。</p>
              </div>
            </div>

            <div>
              <div class="config-fields">
                <el-form-item label="对话服务" for="chat-provider">
                  <el-select id="chat-provider" v-model="form.chatProviderType" aria-label="对话服务" @change="applyChatPreset">
                    <el-option label="OpenAI / 兼容服务" value="OPENAI_COMPATIBLE" />
                    <el-option label="DeepSeek" value="DEEPSEEK" />
                    <el-option label="豆包（火山方舟）" value="DOUBAO_ARK" />
                  </el-select>
                </el-form-item>
                <el-form-item label="对话模型" for="chat-model" :error="errors.defaultModel">
                  <el-input id="chat-model" v-model="form.defaultModel" :placeholder="chatModelPlaceholder" autocomplete="off" />
                </el-form-item>
                <el-form-item v-if="form.chatProviderType === 'OPENAI_COMPATIBLE'" class="config-fields__wide" label="服务地址" for="chat-base-url" :error="errors.baseUrl">
                  <el-input id="chat-base-url" v-model="form.baseUrl" placeholder="例如：https://api.openai.com" spellcheck="false" />
                  <p class="config-field-help">使用中转或自建服务时，填写服务商提供的地址。</p>
                </el-form-item>
                <el-form-item class="config-fields__wide" label="对话 API Key" for="chat-api-key" :error="errors.apiKey">
                  <el-input id="chat-api-key" v-model="form.apiKey" type="password" show-password autocomplete="new-password" :placeholder="configInfo.apiKeyConfigured ? '留空保留现有密钥' : '粘贴服务商提供的 API Key'" />
                  <p class="config-field-help" :class="{ 'config-field-help--ready': configInfo.apiKeyConfigured }">
                    <AppIcon v-if="configInfo.apiKeyConfigured" name="check" :size="14" />
                    {{ configInfo.apiKeyConfigured ? '已配置密钥，留空即可沿用。' : '尚未配置密钥。演示体验无需填写。' }}
                  </p>
                </el-form-item>
              </div>

              <details class="config-advanced" :open="chatAdvancedOpen" @toggle="chatAdvancedOpen = ($event.target as HTMLDetailsElement).open">
                <summary>高级设置 <AppIcon name="chevron-down" :size="16" /></summary>
                <div class="config-advanced__body">
                  <p>默认接口已自动填写，需要自定义时再修改。</p>
                  <el-form-item v-if="form.chatProviderType !== 'OPENAI_COMPATIBLE'" label="服务地址" for="chat-custom-url" :error="errors.baseUrl">
                    <el-input id="chat-custom-url" v-model="form.baseUrl" :placeholder="activeChatPreset.baseUrl" spellcheck="false" />
                  </el-form-item>
                  <el-form-item label="对话接口路径" for="chat-path" :error="errors.chatPath">
                    <el-input id="chat-path" v-model="form.chatPath" :placeholder="activeChatPreset.path" spellcheck="false" />
                  </el-form-item>
                  <button type="button" class="config-text-button" @click="restoreChatEndpoint">恢复默认接口</button>
                </div>
              </details>
            </div>
          </section>

          <details id="vector-model" ref="vectorSection" class="page-card config-section config-vector" :open="vectorExpanded" @toggle="vectorExpanded = ($event.target as HTMLDetailsElement).open">
            <summary class="config-vector__summary">
              <span class="config-section__icon"><AppIcon name="search" :size="22" /></span>
              <span class="config-vector__intro">
                <span class="config-vector__title">向量模型<span class="config-optional">资料检索时使用</span></span>
                <span class="config-vector__description">让 AI 按含义找到资料中的相关段落。</span>
              </span>
              <span class="config-vector__toggle">{{ vectorExpanded ? '收起' : '配置' }}<AppIcon name="chevron-down" :size="17" /></span>
            </summary>

            <div class="config-vector__body">
              <div class="config-vector__guide">
                <p>配置后，到资料库「生成向量」，再用「检索预览」查找内容。</p>
                <RouterLink to="/materials">去资料库<AppIcon name="arrow-right" :size="15" /></RouterLink>
              </div>
              <div>
                <div class="config-fields">
                  <el-form-item label="向量服务" for="embedding-provider">
                    <el-select id="embedding-provider" v-model="form.embeddingProviderType" aria-label="向量服务" @change="applyEmbeddingPreset">
                      <el-option label="OpenAI / 兼容服务" value="OPENAI_COMPATIBLE" />
                      <el-option label="豆包多模态向量" value="ARK_MULTIMODAL_TEXT" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="向量模型名称" for="embedding-model" :error="errors.defaultEmbeddingModel">
                    <el-input id="embedding-model" v-model="form.defaultEmbeddingModel" :placeholder="embeddingModelPlaceholder" autocomplete="off" />
                  </el-form-item>
                  <el-form-item v-if="form.embeddingProviderType === 'OPENAI_COMPATIBLE'" class="config-fields__wide" label="向量服务地址" for="embedding-base-url" :error="errors.embeddingBaseUrl">
                    <el-input id="embedding-base-url" v-model="form.embeddingBaseUrl" placeholder="例如：https://api.openai.com" spellcheck="false" />
                  </el-form-item>
                  <el-form-item class="config-fields__wide" label="向量 API Key" for="embedding-api-key">
                    <el-input id="embedding-api-key" v-model="form.embeddingApiKey" type="password" show-password autocomplete="new-password" :placeholder="configInfo.embeddingApiKeyConfigured ? '留空保留现有密钥' : '填写向量服务的 API Key'" />
                    <p class="config-field-help">
                      {{ configInfo.embeddingApiKeyConfigured ? '已配置密钥；更换为其他服务时，请填写对应密钥。' : '使用不同的模型服务时，请单独填写该服务的密钥。' }}
                    </p>
                  </el-form-item>
                </div>
                <p v-if="form.chatProviderType === 'DEEPSEEK'" class="config-note">DeepSeek 用于对话；向量检索需选择支持向量模型的服务。</p>

                <details class="config-advanced" :open="embeddingAdvancedOpen" @toggle="embeddingAdvancedOpen = ($event.target as HTMLDetailsElement).open">
                  <summary>高级设置 <AppIcon name="chevron-down" :size="16" /></summary>
                  <div class="config-advanced__body">
                    <p>接口路径会随所选服务自动填写，也可以手动调整。</p>
                    <el-form-item v-if="form.embeddingProviderType !== 'OPENAI_COMPATIBLE'" label="向量服务地址" for="embedding-custom-url" :error="errors.embeddingBaseUrl">
                      <el-input id="embedding-custom-url" v-model="form.embeddingBaseUrl" :placeholder="activeEmbeddingPreset.baseUrl" spellcheck="false" />
                    </el-form-item>
                    <el-form-item label="向量接口路径" for="embedding-path" :error="errors.embeddingPath">
                      <el-input id="embedding-path" v-model="form.embeddingPath" :placeholder="activeEmbeddingPreset.path" spellcheck="false" />
                    </el-form-item>
                    <button type="button" class="config-text-button" @click="restoreEmbeddingEndpoint">恢复默认接口</button>
                  </div>
                </details>
                <p class="config-vector__rebuild-note">更换向量模型后，需要在资料库重新生成向量。</p>
              </div>
            </div>
          </details>
        </fieldset>

        <p v-if="saveError" class="config-error" role="alert">{{ saveError }}</p>
        <footer class="config-save-bar">
          <p role="status" aria-live="polite">
            <AppIcon v-if="!dirty && !saving" name="check" :size="16" />
            {{ saving ? '正在保存…' : dirty ? '有未保存的更改' : savedFeedback || '配置已同步' }}
          </p>
          <div>
            <el-button native-type="button" :disabled="!dirty || busy" @click="resetChanges">撤销修改</el-button>
            <el-button type="primary" native-type="submit" :loading="saving" :disabled="!dirty || busy">{{ saveScope === 'GLOBAL' ? '保存共享配置' : '保存配置' }}</el-button>
          </div>
        </footer>
      </el-form>

      <div class="config-secondary-actions">
        <button type="button" class="config-text-button" :disabled="busy" @click="reloadConfig">重新加载</button>
        <button v-if="saveScope === 'USER' && personalConfigured" type="button" class="config-text-button" :disabled="busy" @click="clearPersonalConfig">恢复使用默认配置</button>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import {
  clearAiConfigApi,
  getAiConfigApi,
  getGlobalAiConfigApi,
  updateAiConfigApi,
  updateGlobalAiConfigApi,
  type AiConfigPayload,
  type AiConfigResponse
} from '@/api/modules/ai'

type SaveScope = 'USER' | 'GLOBAL'
type UsageMode = 'live' | 'demo' | 'off'
type ConfigForm = Required<AiConfigPayload>
type ErrorField = 'baseUrl' | 'chatPath' | 'defaultModel' | 'apiKey' | 'embeddingBaseUrl' | 'embeddingPath' | 'defaultEmbeddingModel'
type ProviderPreset = { baseUrl: string; path: string; model: string; placeholder: string }

const route = useRoute()
const loading = ref(false)
const loaded = ref(false)
const saving = ref(false)
const busy = computed(() => loading.value || saving.value)
const loadError = ref('')
const saveError = ref('')
const savedFeedback = ref('')
const saveScope = ref<SaveScope>('USER')
const canManageGlobal = ref(false)
const personalConfigured = ref(false)
const globalConfigured = ref(false)
const configSource = ref<AiConfigResponse['configSource']>('ENV')
const configInfo = reactive({ apiKeyConfigured: false, embeddingApiKeyConfigured: false })
const chatAdvancedOpen = ref(false)
const embeddingAdvancedOpen = ref(false)
const vectorExpanded = ref(false)
const vectorSection = ref<HTMLDetailsElement | null>(null)
const errors = reactive<Partial<Record<ErrorField, string>>>({})
const savedForm = ref<ConfigForm | null>(null)
let loadSequence = 0

const form = reactive<ConfigForm>({
  enabled: true, mockMode: true,
  chatProviderType: 'OPENAI_COMPATIBLE', baseUrl: '', chatPath: '', defaultModel: '', apiKey: '',
  embeddingProviderType: 'OPENAI_COMPATIBLE', embeddingBaseUrl: '', embeddingPath: '', defaultEmbeddingModel: '', embeddingApiKey: ''
})

const usageModes: { value: UsageMode; label: string }[] = [
  { value: 'live', label: '真实模型' },
  { value: 'demo', label: '演示体验' },
  { value: 'off', label: '关闭 AI' }
]
const usageMode = computed<UsageMode>({
  get: () => !form.enabled ? 'off' : form.mockMode ? 'demo' : 'live',
  set: (mode) => {
    form.enabled = mode !== 'off'
    if (mode !== 'off') form.mockMode = mode === 'demo'
  }
})
const modeDescription = computed(() => ({
  live: '使用所配置的模型服务，保存后生效。',
  demo: '使用模拟结果体验流程，无需 API Key；真实向量检索需切换到真实模型。',
  off: '暂停 AI 调用，已填写的模型配置会保留。'
})[usageMode.value])
const savedModeLabel = computed(() => !savedForm.value?.enabled ? 'AI 已关闭' : savedForm.value.mockMode ? '演示模式' : '真实模型')
const sourceLabel = computed(() => ({
  USER: '我的个人配置', GLOBAL: '管理员共享配置', LEGACY: '系统配置', ENV: '系统默认'
})[configSource.value || 'ENV'])
const scopeDescription = computed(() => saveScope.value === 'GLOBAL'
  ? '供未设置个人配置的用户使用。'
  : personalConfigured.value
    ? '修改仅对你的账号生效。'
    : globalConfigured.value
      ? '当前沿用共享配置，修改后将保存为你的个人配置。'
      : '当前沿用系统默认，修改后将保存为你的个人配置。')

const chatPresets: Record<string, ProviderPreset> = {
  OPENAI_COMPATIBLE: { baseUrl: 'https://api.openai.com', path: '/v1/chat/completions', model: '', placeholder: '例如：gpt-4o-mini，或服务商提供的模型名' },
  DEEPSEEK: { baseUrl: 'https://api.deepseek.com', path: '/chat/completions', model: 'deepseek-chat', placeholder: '例如：deepseek-chat' },
  DOUBAO_ARK: { baseUrl: 'https://ark.cn-beijing.volces.com', path: '/api/v3/responses', model: '', placeholder: '填写火山方舟的模型名或接入点 ID' }
}
const embeddingPresets: Record<string, ProviderPreset> = {
  OPENAI_COMPATIBLE: { baseUrl: 'https://api.openai.com', path: '/v1/embeddings', model: 'text-embedding-3-small', placeholder: '例如：text-embedding-3-small' },
  ARK_MULTIMODAL_TEXT: { baseUrl: 'https://ark.cn-beijing.volces.com', path: '/api/v3/embeddings/multimodal', model: '', placeholder: '填写豆包向量模型名或接入点 ID' }
}
const activeChatPreset = computed(() => chatPresets[form.chatProviderType] || chatPresets.OPENAI_COMPATIBLE)
const activeEmbeddingPreset = computed(() => embeddingPresets[form.embeddingProviderType] || embeddingPresets.OPENAI_COMPATIBLE)
const chatModelPlaceholder = computed(() => activeChatPreset.value.placeholder)
const embeddingModelPlaceholder = computed(() => activeEmbeddingPreset.value.placeholder)

function normalize(values: ConfigForm): ConfigForm {
  return Object.fromEntries(Object.entries(values).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value])) as ConfigForm
}
const dirty = computed(() => Boolean(savedForm.value) && JSON.stringify(normalize(form)) !== JSON.stringify(normalize(savedForm.value!)))
const vectorDirty = computed(() => Boolean(savedForm.value) && (['embeddingProviderType', 'embeddingBaseUrl', 'embeddingPath', 'defaultEmbeddingModel', 'embeddingApiKey'] as const)
  .some((key) => form[key].trim() !== savedForm.value![key].trim()))

function clearErrors() {
  for (const field of Object.keys(errors) as ErrorField[]) delete errors[field]
  saveError.value = ''
}
function restoreChatEndpoint() {
  form.baseUrl = activeChatPreset.value.baseUrl
  form.chatPath = activeChatPreset.value.path
}
function applyChatPreset() {
  restoreChatEndpoint()
  form.defaultModel = activeChatPreset.value.model
  clearErrors()
}
function restoreEmbeddingEndpoint() {
  form.embeddingBaseUrl = activeEmbeddingPreset.value.baseUrl
  form.embeddingPath = activeEmbeddingPreset.value.path
}
function applyEmbeddingPreset() {
  restoreEmbeddingEndpoint()
  form.defaultEmbeddingModel = activeEmbeddingPreset.value.model
  clearErrors()
}

function applyLoadedConfig(data: AiConfigResponse) {
  Object.assign(form, {
    enabled: Boolean(data.enabled), mockMode: Boolean(data.mockMode),
    chatProviderType: data.chatProviderType || 'OPENAI_COMPATIBLE',
    baseUrl: data.baseUrl || '', chatPath: data.chatPath || '', defaultModel: data.defaultModel || '', apiKey: '',
    embeddingProviderType: data.embeddingProviderType || 'OPENAI_COMPATIBLE',
    embeddingBaseUrl: data.embeddingBaseUrl || '', embeddingPath: data.embeddingPath || '',
    defaultEmbeddingModel: data.defaultEmbeddingModel || '', embeddingApiKey: ''
  })
  savedForm.value = { ...form }
  configInfo.apiKeyConfigured = Boolean(data.apiKeyConfigured)
  configInfo.embeddingApiKeyConfigured = Boolean(data.embeddingApiKeyConfigured)
  canManageGlobal.value = Boolean(data.canManageGlobal)
  personalConfigured.value = Boolean(data.personalConfigured)
  globalConfigured.value = Boolean(data.globalConfigured)
  configSource.value = data.configSource || 'ENV'
  loaded.value = true
  clearErrors()
}

async function focusVectorSection() {
  if (route.hash !== '#vector-model' || !loaded.value || loading.value) return
  vectorExpanded.value = true
  await nextTick()
  vectorSection.value?.scrollIntoView({ block: 'start', behavior: 'auto' })
}
watch(() => route.hash, focusVectorSection)

async function loadConfig() {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  savedFeedback.value = ''
  try {
    const response = saveScope.value === 'GLOBAL' ? await getGlobalAiConfigApi() : await getAiConfigApi()
    if (sequence !== loadSequence) return
    if (!response.data.data) throw new Error('未能读取配置，请重试。')
    applyLoadedConfig(response.data.data)
  } catch (error) {
    if (sequence === loadSequence) {
      loaded.value = false
      loadError.value = error instanceof Error ? error.message : '请检查网络后重试。'
    }
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
      await focusVectorSection()
    }
  }
}

async function confirmDiscard() {
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('有未保存的修改，离开后将不会保留。', '放弃当前修改？', {
      confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning'
    })
    return true
  } catch {
    return false
  }
}
async function changeScope(scope: SaveScope) {
  if (scope === saveScope.value || busy.value || !(await confirmDiscard())) return
  saveScope.value = scope
  await loadConfig()
}
async function reloadConfig() {
  if (!busy.value && await confirmDiscard()) await loadConfig()
}
function resetChanges() {
  if (!savedForm.value || busy.value) return
  Object.assign(form, savedForm.value)
  clearErrors()
  savedFeedback.value = ''
}

function validateConfig() {
  clearErrors()
  if (usageMode.value !== 'live') return true
  const values = normalize(form)
  const checkUrl = (value: string, field: ErrorField) => {
    try {
      if (!['http:', 'https:'].includes(new URL(value).protocol)) throw new Error()
    } catch {
      errors[field] = '请填写以 http:// 或 https:// 开头的服务地址。'
    }
  }
  checkUrl(values.baseUrl, 'baseUrl')
  if (!values.chatPath.startsWith('/')) errors.chatPath = '接口路径应以 / 开头。'
  if (!values.defaultModel) errors.defaultModel = '请填写服务商提供的模型名称。'
  if (!values.apiKey && !configInfo.apiKeyConfigured) errors.apiKey = '请填写 API Key，或选择演示体验。'
  if (vectorDirty.value) {
    checkUrl(values.embeddingBaseUrl, 'embeddingBaseUrl')
    if (!values.embeddingPath.startsWith('/')) errors.embeddingPath = '接口路径应以 / 开头。'
    if (!values.defaultEmbeddingModel) errors.defaultEmbeddingModel = '请填写向量模型名称或接入点 ID。'
  }
  if (errors.chatPath || errors.baseUrl && form.chatProviderType !== 'OPENAI_COMPATIBLE') chatAdvancedOpen.value = true
  if (errors.embeddingPath || errors.embeddingBaseUrl || errors.defaultEmbeddingModel) vectorExpanded.value = true
  if (errors.embeddingPath || errors.embeddingBaseUrl && form.embeddingProviderType !== 'OPENAI_COMPATIBLE') embeddingAdvancedOpen.value = true
  return Object.keys(errors).length === 0
}

async function saveConfig() {
  if (busy.value || !loaded.value || !dirty.value) return
  if (!validateConfig()) {
    await nextTick()
    document.querySelector<HTMLElement>('.ai-settings .is-error input')?.focus()
    return
  }
  saving.value = true
  try {
    const values = normalize(form)
    const payload: AiConfigPayload = { ...values, apiKey: values.apiKey || undefined, embeddingApiKey: values.embeddingApiKey || undefined }
    const response = saveScope.value === 'GLOBAL' ? await updateGlobalAiConfigApi(payload) : await updateAiConfigApi(payload)
    if (!response.data.data) throw new Error('未能确认保存结果，请重新加载后检查。')
    applyLoadedConfig(response.data.data)
    savedFeedback.value = saveScope.value === 'GLOBAL' ? '共享配置已保存' : '配置已保存'
    ElMessage.success(savedFeedback.value)
  } catch (error) {
    saveError.value = error instanceof Error ? error.message : '保存失败，请重试。'
  } finally {
    saving.value = false
  }
}

async function clearPersonalConfig() {
  if (busy.value) return
  try {
    await ElMessageBox.confirm('将删除你的个人模型配置，恢复使用管理员共享或系统默认配置。', '恢复默认配置？', {
      confirmButtonText: '恢复默认', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  saving.value = true
  try {
    const response = await clearAiConfigApi()
    applyLoadedConfig(response.data.data)
    savedFeedback.value = '已恢复默认配置'
    ElMessage.success(savedFeedback.value)
  } catch (error) {
    saveError.value = error instanceof Error ? error.message : '恢复默认配置失败，请重试。'
  } finally {
    saving.value = false
  }
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}
onBeforeRouteLeave(async () => !saving.value && await confirmDiscard())
onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  void loadConfig()
})
onBeforeUnmount(() => {
  loadSequence++
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style scoped>
.ai-settings { max-width: 980px; margin-inline: auto; }
.ai-settings__header { align-items: center; margin-bottom: 24px; }
.config-scope { display: flex; flex-shrink: 0; gap: 4px; padding: 4px; border: 1px solid var(--line); border-radius: 999px; }
.config-scope button { padding: 9px 18px; border: 0; border-radius: 999px; background: transparent; color: var(--muted); font: inherit; font-size: 13px; cursor: pointer; }
.config-scope button[aria-pressed="true"] { background: var(--brand-light); color: var(--brand); font-weight: 600; }
.config-context { display: flex; align-items: baseline; gap: 10px 18px; flex-wrap: wrap; margin-bottom: 30px; font-size: 13px; }
.config-context__source { display: inline-flex; align-items: center; gap: 8px; color: var(--brand); }
.config-context__source i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.config-context p { margin: 0; color: var(--muted); line-height: 1.7; }
.config-form > fieldset { display: grid; min-width: 0; gap: 22px; padding: 0; border: 0; margin: 0; }
.config-mode { display: flex; align-items: center; justify-content: space-between; gap: 18px 28px; padding: 0 2px 4px; }
.config-mode h2 { margin: 0 0 7px; font-size: 15px; font-weight: 600; color: var(--text); }
.config-mode p { max-width: 440px; margin: 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.config-mode__choices { display: flex; flex-shrink: 0; padding: 4px; gap: 2px; background: var(--panel); border-radius: 12px; }
.config-mode__choices label { position: relative; display: block; padding: 11px 15px; border-radius: 9px; color: var(--muted); font-size: 13px; cursor: pointer; white-space: nowrap; }
.config-mode__choices input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.config-mode__choices .is-selected { background: var(--brand-light); color: var(--brand); font-weight: 600; }
.config-mode__choices label:has(input:focus-visible) { outline: 2px solid var(--brand); outline-offset: 3px; }
.config-section { padding: 28px 32px; }
.config-section__heading { display: flex; align-items: center; gap: 14px; margin-bottom: 27px; }
.config-section__icon { flex-shrink: 0; display: grid; place-items: center; width: 42px; height: 42px; border-radius: 13px; background: var(--brand-light); color: var(--brand); }
.config-section__heading h2 { margin: 0; font-size: 19px; font-weight: 600; color: var(--text); }
.config-section__heading p { margin: 6px 0 0; font-size: 13px; color: var(--muted); line-height: 1.65; }
.config-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.config-fields__wide { grid-column: 1 / -1; }
.config-section :deep(.el-select) { width: 100%; }
.config-section :deep(.el-form-item) { margin-bottom: 24px; }
.config-section :deep(.el-form-item__label) { font-size: 13px; }
.config-section :deep(.el-form-item__content) { align-content: flex-start; }
.config-section :deep(.el-input__wrapper), .config-section :deep(.el-select__wrapper) { min-height: 44px; }
.config-section :deep(.el-form-item__error) { position: static; padding-top: 6px; line-height: 1.6; }
.config-section :deep(.el-input__inner), .config-section :deep(.el-select__placeholder) { font-size: 14px; }
.config-field-help { display: flex; align-items: flex-start; gap: 5px; margin: 8px 0 0; width: 100%; color: var(--muted); font-size: 12px; line-height: 1.7; }
.config-field-help .app-icon { margin-top: 3px; }
.config-field-help--ready { color: var(--brand); }
.config-advanced { border-top: 1px solid var(--line); }
.config-advanced > summary { display: flex; align-items: center; justify-content: space-between; padding: 17px 0 0; color: var(--muted); font-size: 13px; cursor: pointer; list-style: none; }
.config-advanced > summary::-webkit-details-marker, .config-vector > summary::-webkit-details-marker { display: none; }
.config-advanced[open] > summary { color: var(--brand); }
.config-advanced[open] > summary .app-icon, .config-vector[open] .config-vector__toggle .app-icon { transform: rotate(180deg); }
.config-advanced__body { padding-top: 16px; }
.config-advanced__body > p { margin: 0 0 18px; color: var(--muted); font-size: 12px; line-height: 1.7; }
.config-text-button { display: inline-flex; align-items: center; gap: 5px; padding: 2px 0; border: 0; color: var(--brand); background: transparent; font: inherit; font-size: 12px; cursor: pointer; }
.config-text-button:hover { text-decoration: underline; text-underline-offset: 3px; }
.config-vector { scroll-margin-top: 110px; }
.config-vector__summary { display: flex; align-items: center; gap: 14px; cursor: pointer; list-style: none; }
.config-vector__intro { flex: 1; min-width: 0; }
.config-vector__title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; font-size: 18px; font-weight: 600; color: var(--text); }
.config-optional { color: var(--muted); font-size: 11px; font-weight: 400; }
.config-vector__description { display: block; margin-top: 6px; color: var(--muted); font-size: 13px; line-height: 1.65; }
.config-vector__toggle { display: flex; align-items: center; gap: 6px; color: var(--brand); font-size: 12px; }
.config-vector__body { padding-top: 26px; }
.config-vector__guide { display: flex; align-items: baseline; justify-content: space-between; flex-wrap: wrap; gap: 8px 16px; padding: 13px 16px; margin-bottom: 24px; border-radius: 12px; background: var(--bg); font-size: 12px; line-height: 1.75; }
.config-vector__guide p { margin: 0; color: var(--text-secondary); }
.config-vector__guide a { display: inline-flex; align-items: center; gap: 5px; color: var(--brand); text-decoration: none; white-space: nowrap; }
.config-note { margin: -3px 0 20px; color: var(--muted); font-size: 12px; line-height: 1.8; }
.config-vector__rebuild-note { margin: 18px 0 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.config-error { margin: 18px 0 0; padding: 13px 17px; border-radius: 12px; color: var(--red); background: var(--red-light, #fdf0ed); font-size: 13px; line-height: 1.7; }
.config-save-bar { position: sticky; bottom: 18px; z-index: 5; display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 16px 22px; margin-top: 22px; border: 1px solid var(--line); border-radius: 16px; background: var(--panel); box-shadow: 0 6px 24px rgb(35 49 38 / 5%); }
.config-save-bar > p { display: flex; align-items: center; gap: 7px; margin: 0; color: var(--muted); font-size: 12px; }
.config-save-bar > div { display: flex; gap: 10px; }
.config-save-bar :deep(.el-button + .el-button) { margin-left: 0; }
.config-secondary-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 18px; padding: 18px 3px 0; }
.config-state { min-height: 220px; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 12px; color: var(--muted); text-align: center; }
.config-state h2 { margin: 0; color: var(--text); font-size: 18px; }
.config-state p { margin: 0; font-size: 14px; line-height: 1.8; }
.ai-settings button:disabled { cursor: not-allowed; opacity: .55; }
.ai-settings summary:focus-visible, .ai-settings button:focus-visible { outline: 2px solid var(--brand); outline-offset: 4px; border-radius: 5px; }

@media (max-width: 900px) {
  .config-mode { align-items: flex-start; flex-direction: column; }
  .config-mode p { max-width: none; }
}
@media (max-width: 600px) {
  .ai-settings__header { align-items: flex-start; gap: 18px; }
  .config-context { margin-bottom: 24px; gap: 6px; }
  .config-context p { width: 100%; }
  .config-scope { width: 100%; }
  .config-scope button { flex: 1; }
  .config-section { padding: 22px 18px; }
  .config-mode__choices { width: 100%; }
  .config-mode__choices label { flex: 1; text-align: center; padding-inline: 7px; }
  .config-fields { grid-template-columns: minmax(0, 1fr); }
  .config-vector__summary { gap: 10px; align-items: flex-start; }
  .config-vector__title { font-size: 17px; gap: 5px; }
  .config-optional { width: 100%; }
  .config-vector__toggle { margin-top: 5px; }
  .config-vector__description { font-size: 12px; }
  .config-section__icon { width: 35px; height: 35px; border-radius: 10px; }
  .config-save-bar { flex-wrap: wrap; padding: 14px 16px; bottom: 86px; }
  .config-save-bar > div { width: 100%; }
  .config-save-bar :deep(.el-button) { flex: 1; padding-inline: 14px; }
  .config-secondary-actions { justify-content: center; }
}
</style>
