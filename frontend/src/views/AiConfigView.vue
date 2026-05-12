<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 配置</h1>
        <p class="page-desc">
          支持个人 API Key 和管理员共享 API Key。个人配置优先生效；未配置个人 Key 时，系统会自动使用管理员共享配置。
        </p>
      </div>
      <div class="toolbar toolbar--config">
        <el-radio-group
          v-if="canManageGlobal"
          v-model="saveScope"
          size="large"
          @change="loadConfig"
        >
          <el-radio-button label="USER">我的配置</el-radio-button>
          <el-radio-button label="GLOBAL">共享配置</el-radio-button>
        </el-radio-group>
        <el-button :loading="loading" @click="loadConfig">重新加载</el-button>
        <el-button
          v-if="saveScope === 'USER' && personalConfigured"
          plain
          type="danger"
          :loading="saving"
          @click="clearPersonalConfig"
        >
          删除个人配置
        </el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">
          {{ saveButtonText }}
        </el-button>
      </div>
    </div>

    <el-alert
      class="scope-alert"
      :closable="false"
      type="info"
      show-icon
    >
      <template #title>
        当前生效来源：{{ sourceLabel }}
        <el-tag v-if="globalConfigured" size="small" type="success" effect="plain">已有共享配置</el-tag>
        <el-tag v-if="personalConfigured" size="small" type="warning" effect="plain">已有个人配置</el-tag>
      </template>
      <template #default>
        {{ scopeDescription }}
      </template>
    </el-alert>

    <div class="content-grid">
      <div class="content-grid content-grid--2">
        <div class="page-card">
          <div class="panel-title-row">
            <h3>聊天模型</h3>
            <span class="soft-text">AI 总结、AI 出题和 Agent 回复会使用这组配置</span>
          </div>
          <el-form label-position="top">
            <div class="workspace-form-grid workspace-form-grid--compact">
              <el-form-item label="启用 AI">
                <el-switch v-model="form.enabled" />
              </el-form-item>
              <el-form-item label="Mock 模式">
                <el-switch v-model="form.mockMode" />
              </el-form-item>
            </div>
            <el-form-item label="Chat Provider">
              <div class="provider-row">
                <el-select v-model="form.chatProviderType" style="width: 100%" @change="applyChatPreset">
                  <el-option label="OpenAI 兼容接口" value="OPENAI_COMPATIBLE" />
                  <el-option label="DeepSeek 官方接口" value="DEEPSEEK" />
                  <el-option label="豆包 Ark 聊天接口" value="DOUBAO_ARK" />
                </el-select>
                <el-button plain @click="applyChatPreset">套用预设</el-button>
              </div>
            </el-form-item>
            <el-form-item label="Chat Base URL">
              <el-input v-model="form.baseUrl" :placeholder="chatBaseUrlPlaceholder" />
            </el-form-item>
            <el-form-item label="Chat Path">
              <el-input v-model="form.chatPath" :placeholder="chatPathPlaceholder" />
            </el-form-item>
            <el-form-item label="默认聊天模型">
              <el-input v-model="form.defaultModel" :placeholder="chatModelPlaceholder" />
            </el-form-item>
            <el-form-item label="聊天 API Key">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                :placeholder="apiKeyPlaceholder"
              />
              <div class="soft-text key-status">
                当前状态：{{ configInfo.apiKeyConfigured ? configInfo.apiKeyPreview : '未配置' }}
              </div>
            </el-form-item>
          </el-form>
        </div>

        <div class="page-card">
          <div class="panel-title-row">
            <h3>向量模型</h3>
            <span class="soft-text">Embedding / RAG 检索会使用这组配置</span>
          </div>
          <el-form label-position="top">
            <el-form-item label="Embedding Provider">
              <el-select v-model="form.embeddingProviderType" style="width: 100%">
                <el-option label="OpenAI 兼容 Embedding" value="OPENAI_COMPATIBLE" />
                <el-option label="豆包 Ark 多模态向量" value="ARK_MULTIMODAL_TEXT" />
              </el-select>
            </el-form-item>
            <el-form-item label="Embedding Base URL">
              <el-input
                v-model="form.embeddingBaseUrl"
                placeholder="例如：https://ark.cn-beijing.volces.com"
              />
            </el-form-item>
            <el-form-item label="Embedding Path">
              <el-input v-model="form.embeddingPath" :placeholder="embeddingPathPlaceholder" />
            </el-form-item>
            <el-form-item label="默认向量模型">
              <el-input v-model="form.defaultEmbeddingModel" :placeholder="embeddingModelPlaceholder" />
            </el-form-item>
            <el-form-item label="Embedding API Key">
              <el-input
                v-model="form.embeddingApiKey"
                type="password"
                show-password
                :placeholder="embeddingApiKeyPlaceholder"
              />
              <div class="soft-text key-status">
                当前状态：{{
                  configInfo.embeddingApiKeyConfigured ? configInfo.embeddingApiKeyPreview : '未配置'
                }}
              </div>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div class="content-grid content-grid--2">
        <div class="page-card">
          <div class="panel-title-row">
            <h3>配置规则</h3>
          </div>
          <div class="tips-stack">
            <div class="tip-card">
              <div class="tip-card__title">个人配置优先</div>
              <div class="tip-card__desc">
                普通用户保存的是自己的配置，不会覆盖管理员共享 Key；没有个人配置时自动回退到共享配置。
              </div>
            </div>
            <div class="tip-card">
              <div class="tip-card__title">共享配置只给管理员维护</div>
              <div class="tip-card__desc">
                用户名 admin 或角色为 ADMIN 的账号可以维护共享配置，适合给体验用户提供默认可用的模型能力。
              </div>
            </div>
            <div class="tip-card">
              <div class="tip-card__title">个人配置不会偷用共享 Key</div>
              <div class="tip-card__desc">
                普通用户不填个人 Key 时，只有接口地址、路径和模型与共享配置一致，才会安全复用共享 Key；自定义地址时必须填写自己的 Key。
              </div>
            </div>
          </div>
        </div>

        <div class="page-card">
          <div class="panel-title-row">
            <h3>推荐配置</h3>
          </div>
          <div class="tips-stack">
            <div class="tip-card">
              <div class="tip-card__title">聊天接口</div>
              <div class="tip-card__desc">
                DeepSeek 和豆包 Ark 聊天都可以套用预设；OpenAI 兼容接口适合中转站或自建网关。
              </div>
            </div>
            <div class="tip-card">
              <div class="tip-card__title">向量接口</div>
              <div class="tip-card__desc">
                OpenAI 兼容向量通常使用 /v1/embeddings；豆包多模态向量通常使用 /api/v3/embeddings/multimodal。
              </div>
            </div>
            <div class="tip-card">
              <div class="tip-card__title">旧配置兼容</div>
              <div class="tip-card__desc">
                数据库没有配置时，后端仍会读取旧的 runtime/ai-config.json 和环境变量，线上迁移更平滑。
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  clearAiConfigApi,
  getAiConfigApi,
  getGlobalAiConfigApi,
  updateAiConfigApi,
  updateGlobalAiConfigApi,
  type AiConfigResponse
} from '@/api/modules/ai'

type SaveScope = 'USER' | 'GLOBAL'

const loading = ref(false)
const saving = ref(false)
const saveScope = ref<SaveScope>('USER')
const canManageGlobal = ref(false)
const personalConfigured = ref(false)
const globalConfigured = ref(false)
const configSource = ref<'USER' | 'GLOBAL' | 'LEGACY' | 'ENV'>('ENV')
const configInfo = ref({
  apiKeyConfigured: false,
  apiKeyPreview: '',
  embeddingApiKeyConfigured: false,
  embeddingApiKeyPreview: ''
})

const form = reactive({
  enabled: true,
  mockMode: true,
  chatProviderType: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  chatPath: '',
  defaultModel: '',
  apiKey: '',
  embeddingProviderType: 'OPENAI_COMPATIBLE',
  embeddingBaseUrl: '',
  embeddingPath: '',
  defaultEmbeddingModel: '',
  embeddingApiKey: ''
})

const chatProviderPresets: Record<
  string,
  { baseUrl: string; chatPath: string; defaultModel: string; basePlaceholder: string; pathPlaceholder: string; modelPlaceholder: string }
> = {
  OPENAI_COMPATIBLE: {
    baseUrl: 'https://api.openai.com',
    chatPath: '/v1/chat/completions',
    defaultModel: '',
    basePlaceholder: '例如：https://newapi.example.com 或 https://api.openai.com',
    pathPlaceholder: '例如：/v1/chat/completions',
    modelPlaceholder: '例如：gpt-4o-mini，或你的中转站模型名'
  },
  DEEPSEEK: {
    baseUrl: 'https://api.deepseek.com',
    chatPath: '/chat/completions',
    defaultModel: 'deepseek-chat',
    basePlaceholder: '例如：https://api.deepseek.com',
    pathPlaceholder: '例如：/chat/completions',
    modelPlaceholder: '例如：deepseek-chat'
  },
  DOUBAO_ARK: {
    baseUrl: 'https://ark.cn-beijing.volces.com',
    chatPath: '/api/v3/responses',
    defaultModel: '',
    basePlaceholder: '例如：https://ark.cn-beijing.volces.com',
    pathPlaceholder: '例如：/api/v3/responses',
    modelPlaceholder: '填写火山方舟控制台中的推理接入点 ID 或模型名'
  }
}

const sourceLabelMap = {
  USER: '我的个人配置',
  GLOBAL: '管理员共享配置',
  LEGACY: '旧运行时配置',
  ENV: '环境变量默认配置'
}

const sourceLabel = computed(() => sourceLabelMap[configSource.value] || '环境变量默认配置')

const saveButtonText = computed(() => {
  if (saveScope.value === 'GLOBAL') {
    return '保存共享配置'
  }
  return personalConfigured.value ? '保存个人配置' : '创建个人配置'
})

const scopeDescription = computed(() => {
  if (saveScope.value === 'GLOBAL') {
    return '你正在维护管理员共享配置。普通用户没有个人配置时，会自动使用这组配置。'
  }
  if (personalConfigured.value) {
    return '你当前有个人配置，AI 调用会优先使用这组个人配置。'
  }
  if (globalConfigured.value) {
    return '你还没有个人配置，AI 调用会自动使用管理员共享配置；如果只想关闭 Mock 并继续使用共享 Key，请保持接口地址、路径和模型与共享配置一致后创建个人配置。'
  }
  return '你还没有个人配置，AI 调用会自动回退到环境变量。'
})

const apiKeyPlaceholder = computed(() =>
  saveScope.value === 'GLOBAL'
    ? '留空表示沿用当前共享 Key'
    : '留空表示沿用当前账号已保存 Key；与共享配置端点一致时可复用共享 Key'
)

const embeddingApiKeyPlaceholder = computed(() =>
  saveScope.value === 'GLOBAL'
    ? '留空表示沿用当前共享 Embedding Key；未填时复用聊天 Key'
    : '留空表示沿用当前账号已保存 Embedding Key；端点一致时可复用共享 Embedding Key'
)

const activeChatPreset = computed(
  () => chatProviderPresets[form.chatProviderType] || chatProviderPresets.OPENAI_COMPATIBLE
)

const chatBaseUrlPlaceholder = computed(() => activeChatPreset.value.basePlaceholder)

const chatPathPlaceholder = computed(() => activeChatPreset.value.pathPlaceholder)

const chatModelPlaceholder = computed(() => activeChatPreset.value.modelPlaceholder)

const embeddingPathPlaceholder = computed(() =>
  form.embeddingProviderType === 'ARK_MULTIMODAL_TEXT'
    ? '例如：/api/v3/embeddings/multimodal'
    : '例如：/v1/embeddings'
)

const embeddingModelPlaceholder = computed(() =>
  form.embeddingProviderType === 'ARK_MULTIMODAL_TEXT'
    ? '例如：doubao-embedding-vision-250615'
    : '例如：text-embedding-3-small'
)

const applyChatPreset = () => {
  const preset = activeChatPreset.value
  form.baseUrl = preset.baseUrl
  form.chatPath = preset.chatPath
  form.defaultModel = preset.defaultModel
  if (form.chatProviderType === 'DOUBAO_ARK') {
    ElMessage.info('豆包 Ark 的模型名通常需要填写火山方舟控制台创建的推理接入点 ID。')
  }
}

const applyLoadedConfig = (data: AiConfigResponse) => {
  form.enabled = Boolean(data.enabled)
  form.mockMode = Boolean(data.mockMode)
  form.chatProviderType = data.chatProviderType || 'OPENAI_COMPATIBLE'
  form.baseUrl = data.baseUrl || ''
  form.chatPath = data.chatPath || ''
  form.defaultModel = data.defaultModel || ''
  form.apiKey = ''
  form.embeddingProviderType = data.embeddingProviderType || 'OPENAI_COMPATIBLE'
  form.embeddingBaseUrl = data.embeddingBaseUrl || ''
  form.embeddingPath = data.embeddingPath || ''
  form.defaultEmbeddingModel = data.defaultEmbeddingModel || ''
  form.embeddingApiKey = ''
  configInfo.value = {
    apiKeyConfigured: Boolean(data.apiKeyConfigured),
    apiKeyPreview: data.apiKeyPreview || '',
    embeddingApiKeyConfigured: Boolean(data.embeddingApiKeyConfigured),
    embeddingApiKeyPreview: data.embeddingApiKeyPreview || ''
  }
  canManageGlobal.value = Boolean(data.canManageGlobal)
  personalConfigured.value = Boolean(data.personalConfigured)
  globalConfigured.value = Boolean(data.globalConfigured)
  configSource.value = data.configSource || 'ENV'
}

const loadConfig = async () => {
  loading.value = true
  try {
    const res = saveScope.value === 'GLOBAL' && canManageGlobal.value
      ? await getGlobalAiConfigApi()
      : await getAiConfigApi()
    applyLoadedConfig(res.data.data)
  } catch (error: any) {
    ElMessage.error(error.message || '加载 AI 配置失败')
  } finally {
    loading.value = false
  }
}

const buildPayload = () => ({
  enabled: form.enabled,
  mockMode: form.mockMode,
  chatProviderType: form.chatProviderType,
  baseUrl: form.baseUrl.trim(),
  chatPath: form.chatPath.trim(),
  defaultModel: form.defaultModel.trim(),
  apiKey: form.apiKey.trim() || undefined,
  embeddingProviderType: form.embeddingProviderType,
  embeddingBaseUrl: form.embeddingBaseUrl.trim(),
  embeddingPath: form.embeddingPath.trim(),
  defaultEmbeddingModel: form.defaultEmbeddingModel.trim(),
  embeddingApiKey: form.embeddingApiKey.trim() || undefined
})

const saveConfig = async () => {
  saving.value = true
  try {
    const payload = buildPayload()
    const res = saveScope.value === 'GLOBAL'
      ? await updateGlobalAiConfigApi(payload)
      : await updateAiConfigApi(payload)
    applyLoadedConfig(res.data.data)
    ElMessage.success(`${saveScope.value === 'GLOBAL' ? '共享' : '个人'} AI 配置保存成功`)
  } catch (error: any) {
    ElMessage.error(error.message || '保存 AI 配置失败')
  } finally {
    saving.value = false
  }
}

const clearPersonalConfig = async () => {
  try {
    await ElMessageBox.confirm('删除个人配置后，将自动使用管理员共享配置。确定删除吗？', '删除个人配置', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  saving.value = true
  try {
    const res = await clearAiConfigApi()
    applyLoadedConfig(res.data.data)
    ElMessage.success('个人 AI 配置已删除')
  } catch (error: any) {
    ElMessage.error(error.message || '删除个人配置失败')
  } finally {
    saving.value = false
  }
}

loadConfig()
</script>

<style scoped>
.toolbar--config {
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 0;
}

.scope-alert {
  margin-bottom: 16px;
}

.scope-alert :deep(.el-alert__title) {
  display: flex;
  align-items: center;
  gap: 8px;
}

.provider-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}

.key-status {
  margin-top: 8px;
}

@media (max-width: 720px) {
  .toolbar--config,
  .provider-row {
    grid-template-columns: 1fr;
  }

  .toolbar--config > * {
    width: 100%;
  }

  .toolbar--config :deep(.el-radio-group) {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }

  .toolbar--config :deep(.el-radio-button__inner) {
    width: 100%;
  }
}
</style>
