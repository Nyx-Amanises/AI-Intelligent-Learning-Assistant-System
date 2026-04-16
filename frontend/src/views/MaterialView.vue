<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">资料管理</h1>
        <p class="page-desc">支持手动录入文本资料，或直接上传 pdf、docx、txt 文件并触发解析。</p>
      </div>
      <el-button type="primary" @click="loadMaterials">刷新列表</el-button>
    </div>

    <div class="page-card" style="margin-bottom: 18px">
      <div class="toolbar">
        <el-upload :show-file-list="false" :http-request="uploadMaterial">
          <el-button>上传文件资料</el-button>
        </el-upload>
      </div>

      <el-form :model="form" label-position="top">
        <el-form-item label="资料标题">
          <el-input v-model="form.title" placeholder="例如：数据库系统原理 第三章" />
        </el-form-item>
        <el-form-item label="资料类型">
          <el-select v-model="form.materialType" style="width: 100%">
            <el-option label="TEXT" value="TEXT" />
            <el-option label="MARKDOWN" value="MARKDOWN" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度等级">
          <el-slider v-model="form.difficultyLevel" :min="1" :max="5" show-stops />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="如：数据库,期末复习" />
        </el-form-item>
        <el-form-item label="资料内容">
          <el-input v-model="form.contentText" type="textarea" :rows="8" placeholder="请输入课程笔记或知识内容" />
        </el-form-item>
        <el-button type="primary" @click="createMaterial">新增文本资料</el-button>
      </el-form>
    </div>

    <div class="page-card">
      <el-table :data="materials">
        <el-table-column prop="title" label="资料标题" />
        <el-table-column prop="materialType" label="类型" width="120" />
        <el-table-column prop="parseStatus" label="解析状态" width="120" />
        <el-table-column prop="summaryStatus" label="总结状态" width="120" />
        <el-table-column prop="totalCharacters" label="字数" width="100" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="parseMaterial(row.id)">解析</el-button>
            <el-button link @click="viewDetail(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="drawerVisible" title="资料详情" size="50%">
      <template v-if="detail">
        <p><strong>标题：</strong>{{ detail.title }}</p>
        <p><strong>类型：</strong>{{ detail.materialType }}</p>
        <p><strong>解析状态：</strong>{{ detail.parseStatus }}</p>
        <p><strong>总结状态：</strong>{{ detail.summaryStatus }}</p>
        <div v-for="segment in detail.segments || []" :key="segment.id" class="page-card" style="margin-top: 12px">
          <strong>{{ segment.sectionTitle }}</strong>
          <div class="summary-block">{{ segment.contentText }}</div>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import {
  createTextMaterialApi,
  getMaterialDetailApi,
  getMaterialPageApi,
  parseMaterialApi,
  uploadMaterialApi
} from '@/api/modules/material'

const materials = ref<any[]>([])
const detail = ref<any>(null)
const drawerVisible = ref(false)

const form = reactive({
  title: '',
  materialType: 'TEXT',
  difficultyLevel: 3,
  tags: '',
  contentText: ''
})

const loadMaterials = async () => {
  const res = await getMaterialPageApi({ current: 1, size: 20 })
  materials.value = res.data.data.records
}

const createMaterial = async () => {
  await createTextMaterialApi(form)
  ElMessage.success('资料新增成功')
  form.title = ''
  form.tags = ''
  form.contentText = ''
  await loadMaterials()
}

const parseMaterial = async (id: number) => {
  await parseMaterialApi(id)
  ElMessage.success('资料解析成功')
  await loadMaterials()
}

const viewDetail = async (id: number) => {
  const res = await getMaterialDetailApi(id)
  detail.value = res.data.data
  drawerVisible.value = true
}

const uploadMaterial = async (options: UploadRequestOptions) => {
  await uploadMaterialApi(options.file as File)
  ElMessage.success('资料上传成功')
  await loadMaterials()
}

loadMaterials()
</script>
