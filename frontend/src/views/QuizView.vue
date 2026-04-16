<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 出题</h1>
        <p class="page-desc">
          基于已解析的学习资料自动生成题集，并支持立即开始练习，把 AI 能力真正落到学习场景里。
        </p>
      </div>
    </div>

    <div class="page-card" style="margin-bottom: 20px">
      <div class="panel-title-row">
        <h3>当前 AI 运行状态</h3>
        <el-button link type="primary" @click="router.push('/ai-config')">前往配置</el-button>
      </div>
      <div v-if="configLoading" class="state-block">正在加载 AI 配置...</div>
      <div v-else class="detail-meta-grid">
        <div class="detail-meta-item">
          <span>AI 开关</span>
          <strong>{{ aiConfig.enabled ? '已启用' : '已关闭' }}</strong>
        </div>
        <div class="detail-meta-item">
          <span>运行模式</span>
          <strong>{{ aiConfig.mockMode ? 'Mock 模式' : '真实接口模式' }}</strong>
        </div>
        <div class="detail-meta-item">
          <span>默认模型</span>
          <strong>{{ aiConfig.defaultModel || '未设置' }}</strong>
        </div>
        <div class="detail-meta-item">
          <span>Key 状态</span>
          <strong>{{ aiConfig.apiKeyConfigured ? aiConfig.apiKeyPreview : '未配置' }}</strong>
        </div>
      </div>
    </div>

    <div class="content-grid content-grid--2">
      <div class="page-card">
        <div class="panel-title-row">
          <h3>生成题集</h3>
          <span class="soft-text">建议从重点章节资料开始</span>
        </div>
        <el-form label-position="top">
          <el-form-item label="选择资料">
            <el-select
              v-model="form.materialId"
              filterable
              style="width: 100%"
              placeholder="请选择已解析资料"
              :loading="materialsLoading"
            >
              <el-option
                v-for="item in materials"
                :key="item.id"
                :label="`${item.title} · 难度${item.difficultyLevel || 3}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="模型名称">
            <el-input v-model="form.modelName" placeholder="留空则使用当前默认模型" />
          </el-form-item>
          <el-form-item label="题目数量">
            <el-input-number v-model="form.questionCount" :min="1" :max="20" style="width: 100%" />
          </el-form-item>
          <el-form-item label="难度等级">
            <el-slider v-model="form.difficultyLevel" :min="1" :max="5" show-stops />
          </el-form-item>
          <el-button type="primary" :loading="generating" @click="generateQuestionSet">生成题集</el-button>
        </el-form>
      </div>

      <div class="page-card">
        <div class="panel-title-row">
          <h3>题集预览</h3>
        </div>
        <div v-if="generatedSet" class="preview-stack">
          <div class="detail-meta-grid">
            <div class="detail-meta-item">
              <span>题集标题</span>
              <strong>{{ generatedSet.title }}</strong>
            </div>
            <div class="detail-meta-item">
              <span>题目数量</span>
              <strong>{{ generatedSet.questionCount }}</strong>
            </div>
            <div class="detail-meta-item">
              <span>总分</span>
              <strong>{{ generatedSet.totalScore }}</strong>
            </div>
            <div class="detail-meta-item">
              <span>难度</span>
              <strong>{{ generatedSet.difficultyLevel }}</strong>
            </div>
          </div>
          <div class="list-stack">
            <div v-for="question in generatedSet.questions" :key="question.id" class="section-card">
              <div class="section-card__title">{{ question.sortNo }}. {{ question.stemText }}</div>
              <div class="section-card__meta">{{ question.questionType }} · {{ question.score }} 分</div>
            </div>
          </div>
          <el-button type="success" @click="startPractice(generatedSet.id)">立即练习</el-button>
        </div>
        <div v-else class="state-block empty">生成成功后，这里会展示题集概况和题目预览。</div>
      </div>
    </div>

    <div class="page-card" style="margin-top: 20px">
      <div class="panel-title-row">
        <h3>已生成题集</h3>
        <el-button :loading="questionSetLoading" @click="loadQuestionSets">刷新</el-button>
      </div>
      <div v-if="questionSetLoading" class="state-block">正在加载题集列表...</div>
      <div v-else-if="!questionSets.length" class="state-block empty">暂时没有题集，先生成一套试题。</div>
      <div v-else class="list-stack">
        <div v-for="item in questionSets" :key="item.id" class="list-row list-row--action">
          <div>
            <div class="list-row__title">{{ item.title }}</div>
            <div class="list-row__meta">
              {{ item.questionCount }} 题 · {{ item.totalScore }} 分 · 难度 {{ item.difficultyLevel }}
            </div>
          </div>
          <div class="toolbar" style="margin-bottom: 0">
            <el-button link @click="viewQuestionSet(item.id)">查看</el-button>
            <el-button link type="success" @click="startPractice(item.id)">开始练习</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-drawer v-model="drawerVisible" title="题集详情" size="52%">
      <div v-if="detailLoading" class="state-block">正在加载题集详情...</div>
      <template v-else-if="questionSetDetail">
        <div class="detail-meta-grid">
          <div class="detail-meta-item">
            <span>标题</span>
            <strong>{{ questionSetDetail.title }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>题量</span>
            <strong>{{ questionSetDetail.questionCount }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>总分</span>
            <strong>{{ questionSetDetail.totalScore }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>状态</span>
            <strong>{{ questionSetDetail.status }}</strong>
          </div>
        </div>
        <div class="list-stack" style="margin-top: 18px">
          <div v-for="question in questionSetDetail.questions" :key="question.id" class="section-card">
            <div class="section-card__title">{{ question.sortNo }}. {{ question.stemText }}</div>
            <div class="section-card__meta">
              {{ question.questionType }} · {{ question.knowledgePoint || '未标注知识点' }} · {{ question.score }} 分
            </div>
            <div v-if="question.optionA" class="option-list">
              <div>A. {{ question.optionA }}</div>
              <div>B. {{ question.optionB }}</div>
              <div v-if="question.optionC">C. {{ question.optionC }}</div>
              <div v-if="question.optionD">D. {{ question.optionD }}</div>
            </div>
            <div class="analysis-box">
              <div><strong>参考答案：</strong>{{ question.correctAnswer }}</div>
              <div><strong>解析：</strong>{{ question.answerAnalysis || '暂无解析' }}</div>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { generateQuestionSetApi, getAiConfigApi } from '@/api/modules/ai'
import { getMaterialPageApi } from '@/api/modules/material'
import { startPracticeApi } from '@/api/modules/practice'
import { getQuestionSetDetailApi, getQuestionSetPageApi } from '@/api/modules/question'

const route = useRoute()
const router = useRouter()
const materials = ref<any[]>([])
const questionSets = ref<any[]>([])
const generatedSet = ref<any>(null)
const questionSetDetail = ref<any>(null)
const materialsLoading = ref(false)
const questionSetLoading = ref(false)
const generating = ref(false)
const detailLoading = ref(false)
const drawerVisible = ref(false)
const configLoading = ref(false)

const aiConfig = ref({
  enabled: true,
  mockMode: true,
  defaultModel: '',
  apiKeyConfigured: false,
  apiKeyPreview: ''
})

const form = reactive({
  materialId: undefined as number | undefined,
  modelName: '',
  questionCount: 5,
  difficultyLevel: 3
})

const loadAiConfig = async () => {
  configLoading.value = true
  try {
    const res = await getAiConfigApi()
    aiConfig.value = res.data.data
  } catch (error: any) {
    ElMessage.error(error.message || '加载 AI 配置失败')
  } finally {
    configLoading.value = false
  }
}

const loadMaterials = async () => {
  materialsLoading.value = true
  try {
    const res = await getMaterialPageApi({ current: 1, size: 100 })
    materials.value = (res.data.data.records || []).filter((item: any) => item.parseStatus === 'SUCCESS')
  } catch (error: any) {
    ElMessage.error(error.message || '加载资料失败')
  } finally {
    materialsLoading.value = false
  }
}

const loadQuestionSets = async () => {
  questionSetLoading.value = true
  try {
    const res = await getQuestionSetPageApi({ current: 1, size: 20 })
    questionSets.value = res.data.data.records || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载题集失败')
  } finally {
    questionSetLoading.value = false
  }
}

const generateQuestionSet = async () => {
  if (!form.materialId) {
    ElMessage.warning('请先选择资料')
    return
  }

  generating.value = true
  try {
    const res = await generateQuestionSetApi(form.materialId, {
      modelName: form.modelName.trim() || undefined,
      questionCount: form.questionCount,
      difficultyLevel: form.difficultyLevel
    })
    generatedSet.value = res.data.data
    ElMessage.success(aiConfig.value.mockMode ? 'Mock 题集生成成功' : 'AI 出题成功')
    await loadQuestionSets()
  } catch (error: any) {
    ElMessage.error(error.message || '题集生成失败')
  } finally {
    generating.value = false
  }
}

const viewQuestionSet = async (id: number) => {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await getQuestionSetDetailApi(id)
    questionSetDetail.value = res.data.data
  } catch (error: any) {
    questionSetDetail.value = null
    ElMessage.error(error.message || '加载题集详情失败')
  } finally {
    detailLoading.value = false
  }
}

const startPractice = async (questionSetId: number) => {
  try {
    const res = await startPracticeApi(questionSetId)
    ElMessage.success('练习已开始')
    router.push({ path: '/practice', query: { sessionId: String(res.data.data.sessionId) } })
  } catch (error: any) {
    ElMessage.error(error.message || '开始练习失败')
  }
}

onMounted(async () => {
  await Promise.all([loadAiConfig(), loadMaterials(), loadQuestionSets()])
  const queryId = Number(route.query.materialId)
  if (queryId && materials.value.some((item) => item.id === queryId)) {
    form.materialId = queryId
  }
})
</script>
