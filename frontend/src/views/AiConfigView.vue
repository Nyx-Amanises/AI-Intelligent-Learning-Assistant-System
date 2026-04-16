<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 配置</h1>
        <p class="page-desc">
          在这里配置真实 OpenAI 兼容接口。保存后，AI 总结和 AI 出题都会优先使用这里的运行时配置。
        </p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-button :loading="loading" @click="loadConfig">重新加载</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button>
      </div>
    </div>

    <div class="content-grid content-grid--2">
      <div class="page-card">
        <div class="panel-title-row">
          <h3>接口参数</h3>
          <span class="soft-text">支持 OpenAI / DeepSeek / 通义千问兼容网关</span>
        </div>
        <el-form label-position="top">
          <el-form-item label="启用 AI">
            <el-switch v-model="form.enabled" />
          </el-form-item>
          <el-form-item label="Mock 模式">
            <el-switch v-model="form.mockMode" />
          </el-form-item>
          <el-form-item label="Base URL">
            <el-input v-model="form.baseUrl" placeholder="例如：https://api.openai.com" />
          </el-form-item>
          <el-form-item label="Chat Path">
            <el-input v-model="form.chatPath" placeholder="例如：/v1/chat/completions" />
          </el-form-item>
          <el-form-item label="默认模型">
            <el-input v-model="form.defaultModel" placeholder="例如：gpt-4o-mini / deepseek-chat" />
          </el-form-item>
          <el-form-item label="API Key">
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
          <h3>使用说明</h3>
        </div>
        <div class="tips-stack">
          <div class="tip-card">
            <div class="tip-card__title">什么时候用真实接口</div>
            <div class="tip-card__desc">关闭 Mock 模式后，AI 总结和 AI 出题都会调用真实模型。</div>
          </div>
          <div class="tip-card">
            <div class="tip-card__title">最常用配置</div>
            <div class="tip-card__desc">
              OpenAI 官方一般使用 `https://api.openai.com` 和 `/v1/chat/completions`。
            </div>
          </div>
          <div class="tip-card">
            <div class="tip-card__title">兼容平台怎么配</div>
            <div class="tip-card__desc">
              如果是 DeepSeek、通义等兼容平台，通常只需要替换 Base URL、API Key 和模型名称。
            </div>
          </div>
          <div class="tip-card">
            <div class="tip-card__title">配置保存到哪里</div>
            <div class="tip-card__desc">后端会把运行时配置保存到 `backend/runtime/ai-config.json`。</div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiConfigApi, updateAiConfigApi } from '@/api/modules/ai'

const loading = ref(false)
const saving = ref(false)
const configInfo = ref({
  apiKeyConfigured: false,
  apiKeyPreview: ''
})

const form = reactive({
  enabled: true,
  mockMode: true,
  baseUrl: '',
  chatPath: '',
  defaultModel: '',
  apiKey: ''
})

const loadConfig = async () => {
  loading.value = true
  try {
    const res = await getAiConfigApi()
    const data = res.data.data
    form.enabled = data.enabled
    form.mockMode = data.mockMode
    form.baseUrl = data.baseUrl || ''
    form.chatPath = data.chatPath || ''
    form.defaultModel = data.defaultModel || ''
    form.apiKey = ''
    configInfo.value = {
      apiKeyConfigured: data.apiKeyConfigured,
      apiKeyPreview: data.apiKeyPreview
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
      baseUrl: form.baseUrl.trim(),
      chatPath: form.chatPath.trim(),
      defaultModel: form.defaultModel.trim(),
      apiKey: form.apiKey.trim() || undefined
    })
    const data = res.data.data
    configInfo.value = {
      apiKeyConfigured: data.apiKeyConfigured,
      apiKeyPreview: data.apiKeyPreview
    }
    form.apiKey = ''
    ElMessage.success('AI 配置保存成功')
  } catch (error: any) {
    ElMessage.error(error.message || '保存 AI 配置失败')
  } finally {
    saving.value = false
  }
}

loadConfig()
</script>
