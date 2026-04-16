<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 总结</h1>
        <p class="page-desc">选择已经解析过的资料，生成结构化总结，并保存为学习笔记。</p>
      </div>
    </div>

    <div class="page-card">
      <el-form label-position="top">
        <el-form-item label="选择资料">
          <el-select v-model="materialId" style="width: 100%" placeholder="请选择资料">
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="`${item.title}（${item.parseStatus}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="总结类型">
          <el-select v-model="summaryType" style="width: 100%">
            <el-option label="标准总结" value="STANDARD" />
            <el-option label="考试重点" value="EXAM" />
            <el-option label="结构大纲" value="OUTLINE" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="generateSummary">生成 AI 总结</el-button>
      </el-form>
    </div>

    <div v-if="result" class="page-card" style="margin-top: 18px">
      <h3>总结结果</h3>
      <p class="page-desc">模型：{{ result.modelName }} ｜ 类型：{{ result.summaryType }}</p>
      <div class="summary-block">{{ result.summaryText }}</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { generateSummaryApi } from '@/api/modules/ai'
import { getMaterialPageApi } from '@/api/modules/material'

const materialId = ref<number>()
const summaryType = ref('STANDARD')
const materials = ref<any[]>([])
const result = ref<any>(null)

const loadMaterials = async () => {
  const res = await getMaterialPageApi({ current: 1, size: 50 })
  materials.value = res.data.data.records.filter((item: any) => item.parseStatus === 'SUCCESS')
}

const generateSummary = async () => {
  if (!materialId.value) {
    ElMessage.warning('请先选择资料')
    return
  }
  const res = await generateSummaryApi(materialId.value, {
    summaryType: summaryType.value,
    saveAsNote: true
  })
  result.value = res.data.data
  ElMessage.success('AI 总结生成成功')
}

loadMaterials()
</script>
