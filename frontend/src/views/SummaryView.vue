<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 总结</h1>
        <p class="page-desc">
          选择已解析资料，一键生成结构化总结，帮助你把原始笔记转成更适合复习的内容。
        </p>
      </div>
    </div>

    <div class="content-grid content-grid--2">
      <div class="page-card">
        <div class="panel-title-row">
          <h3>生成设置</h3>
          <span class="soft-text">仅展示解析成功的资料</span>
        </div>
        <el-form label-position="top">
          <el-form-item label="选择资料">
            <el-select
              v-model="materialId"
              filterable
              style="width: 100%"
              placeholder="请选择资料"
              :loading="materialsLoading"
              @change="handleMaterialChange"
            >
              <el-option
                v-for="item in materials"
                :key="item.id"
                :label="`${item.title} · ${item.totalCharacters || 0}字`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="总结类型">
            <el-select v-model="summaryType" style="width: 100%">
              <el-option label="标准总结" value="STANDARD" />
              <el-option label="考试重点" value="EXAM" />
              <el-option label="结构提纲" value="OUTLINE" />
            </el-select>
          </el-form-item>
          <el-button type="primary" :loading="generating" @click="generateSummary">生成 AI 总结</el-button>
        </el-form>
      </div>

      <div class="page-card">
        <div class="panel-title-row">
          <h3>资料预览</h3>
        </div>
        <div v-if="detailLoading" class="state-block">正在读取资料内容...</div>
        <div v-else-if="selectedMaterialDetail" class="preview-stack">
          <div class="detail-meta-grid">
            <div class="detail-meta-item">
              <span>标题</span>
              <strong>{{ selectedMaterialDetail.title }}</strong>
            </div>
            <div class="detail-meta-item">
              <span>标签</span>
              <strong>{{ selectedMaterialDetail.tags || '暂无' }}</strong>
            </div>
            <div class="detail-meta-item">
              <span>字数</span>
              <strong>{{ selectedMaterialDetail.totalCharacters || 0 }}</strong>
            </div>
            <div class="detail-meta-item">
              <span>状态</span>
              <strong>{{ selectedMaterialDetail.parseStatus }}</strong>
            </div>
          </div>
          <div v-if="selectedMaterialDetail.segments?.length" class="preview-block">
            <div class="preview-block__title">解析片段预览</div>
            <div class="summary-block">
              {{ selectedMaterialDetail.segments[0].contentText }}
            </div>
          </div>
        </div>
        <div v-else class="state-block empty">先选中一份资料，这里会展示资料概况和内容片段。</div>
      </div>
    </div>

    <div v-if="result" class="page-card" style="margin-top: 20px">
      <div class="panel-title-row">
        <h3>总结结果</h3>
        <span class="soft-text">{{ result.modelName }} · {{ result.summaryType }}</span>
      </div>
      <div class="summary-block">{{ result.summaryText }}</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { generateSummaryApi } from '@/api/modules/ai'
import { getMaterialDetailApi, getMaterialPageApi } from '@/api/modules/material'

const route = useRoute()
const materialId = ref<number>()
const summaryType = ref('STANDARD')
const materials = ref<any[]>([])
const result = ref<any>(null)
const selectedMaterialDetail = ref<any>(null)
const materialsLoading = ref(false)
const detailLoading = ref(false)
const generating = ref(false)

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

const loadMaterialDetail = async (id: number) => {
  detailLoading.value = true
  try {
    const res = await getMaterialDetailApi(id)
    selectedMaterialDetail.value = res.data.data
  } catch (error: any) {
    selectedMaterialDetail.value = null
    ElMessage.error(error.message || '加载资料详情失败')
  } finally {
    detailLoading.value = false
  }
}

const handleMaterialChange = async (value: number) => {
  result.value = null
  await loadMaterialDetail(value)
}

const generateSummary = async () => {
  if (!materialId.value) {
    ElMessage.warning('请先选择资料')
    return
  }

  generating.value = true
  try {
    const res = await generateSummaryApi(materialId.value, {
      summaryType: summaryType.value,
      saveAsNote: true
    })
    result.value = res.data.data
    ElMessage.success('AI 总结生成成功')
    await loadMaterialDetail(materialId.value)
  } catch (error: any) {
    ElMessage.error(error.message || '生成总结失败')
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  await loadMaterials()
  const queryId = Number(route.query.materialId)
  if (queryId && materials.value.some((item) => item.id === queryId)) {
    materialId.value = queryId
    await loadMaterialDetail(queryId)
  }
})
</script>
