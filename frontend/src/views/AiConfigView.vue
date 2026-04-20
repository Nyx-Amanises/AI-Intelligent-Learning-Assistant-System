<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 配置</h1>
        <p class="page-desc">
          聊天模型和向量模型分开配置。聊天支持 OpenAI 兼容、DeepSeek、豆包 Ark，Embedding 可单独切到豆包。
        </p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button :loading="loading" @click="loadConfig">重新加载</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button>
      </div>
    </div>

    <div class="content-grid">
      <div class="content-grid content-grid--2">
        <div class="page-card">
          <div class="panel-title-row">
            <h3>聊天模型</h3>
            <span class="soft-text">AI 总结、AI 出题继续走这里</span>
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
                placeholder="留空表示沿用当前已保存的 Key"
              />
              <div class="soft-text" style="margin-top: 8px">
                当前状态：{{ configInfo.apiKeyConfigured ? configInfo.apiKeyPreview : '未配置' }}
              </div>
            </el-form-item>
          </el-form>
        </div>

        <div class="page-card">
          <div class="panel-title-row">
            <h3>向量模型</h3>
            <span class="soft-text">Embedding / RAG 检索走这里</span>
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
              <el-input
                v-model="form.embeddingPath"
                :placeholder="embeddingPathPlaceholder"
              />
            </el-form-item>
            <el-form-item label="默认向量模型">
              <el-input
                v-model="form.defaultEmbeddingModel"
                :placeholder="embeddingModelPlaceholder"
              />
            </el-form-item>
            <el-form-item label="Embedding API Key">
              <el-input
                v-model="form.embeddingApiKey"
                type="password"
                show-password
                placeholder="留空表示沿用当前已保存的 Key"
              />
              <div class="soft-text" style="margin-top: 8px">
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
            <h3>推荐配置</h3>
          </div>
          <div class="tips-stack">
            <div class="tip-card">
              <div class="tip-card__title">聊天接口</div>
              <div class="tip-card__desc">
                DeepSeek 和豆包 Ark 聊天都走 OpenAI 兼容格式，选择 Provider 后可以套用推荐 Base URL 和 Path。
              </div>
            </div>
            <div class="tip-card">
              <div class="tip-card__title">豆包向量接口</div>
              <div class="tip-card__desc">
                Provider 选 `豆包 Ark 多模态向量`，Base URL 一般是 `https://ark.cn-beijing.volces.com`，
                Path 一般是 `/api/v3/embeddings/multimodal`。
              </div>
            </div>
            <div class="tip-card">
              <div class="tip-card__title">聊天模型示例</div>
              <div class="tip-card__desc">
                DeepSeek 可填 `deepseek-chat`。豆包 Ark 通常填写控制台里的推理接入点 ID 或模型名。
              </div>
            </div>
          </div>
        </div>

        <div class="page-card">
          <div class="panel-title-row">
            <h3>运行说明</h3>
          </div>
          <div class="tips-stack">
            <div class="tip-card">
              <div class="tip-card__title">配置保存位置</div>
              <div class="tip-card__desc">后端会把运行时配置保存到 `backend/runtime/ai-config.json`。</div>
            </div>
            <div class="tip-card">
              <div class="tip-card__title">什么时候会用到向量配置</div>
              <div class="tip-card__desc">
                点击“生成 Embedding”、调用检索预览，以及后续 RAG 总结/出题时，都会优先用这组向量配置。
              </div>
            </div>
            <div class="tip-card">
              <div class="tip-card__title">没配向量接口也不影响聊天</div>
              <div class="tip-card__desc">
                聊天链路和向量链路已拆开，向量接口配错不会影响 AI 总结 / AI 出题的普通聊天调用。
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
import { ElMessage } from 'element-plus'
import { getAiConfigApi, updateAiConfigApi } from '@/api/modules/ai'

const loading = ref(false)
const saving = ref(false)
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
    basePlaceholder: '例如：https://newapi.hjlyywp.com 或 https://api.openai.com',
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
    ElMessage.info('豆包 Ark 的模型名通常需要填写你在火山方舟控制台创建的推理接入点 ID。')
  }
}

const loadConfig = async () => {
  loading.value = true
  try {
    const res = await getAiConfigApi()
    const data = res.data.data
    form.enabled = data.enabled
    form.mockMode = data.mockMode
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
      apiKeyConfigured: data.apiKeyConfigured,
      apiKeyPreview: data.apiKeyPreview,
      embeddingApiKeyConfigured: data.embeddingApiKeyConfigured,
      embeddingApiKeyPreview: data.embeddingApiKeyPreview
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载 AI 配置失败')
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  saving.value = true
  try {
    const res = await updateAiConfigApi({
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
    const data = res.data.data
    configInfo.value = {
      apiKeyConfigured: data.apiKeyConfigured,
      apiKeyPreview: data.apiKeyPreview,
      embeddingApiKeyConfigured: data.embeddingApiKeyConfigured,
      embeddingApiKeyPreview: data.embeddingApiKeyPreview
    }
    form.apiKey = ''
    form.embeddingApiKey = ''
    ElMessage.success('AI 配置保存成功')
  } catch (error: any) {
    ElMessage.error(error.message || '保存 AI 配置失败')
  } finally {
    saving.value = false
  }
}

loadConfig()
</script>

<style scoped>
.provider-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}

@media (max-width: 720px) {
  .provider-row {
    grid-template-columns: 1fr;
  }
}
</style>
