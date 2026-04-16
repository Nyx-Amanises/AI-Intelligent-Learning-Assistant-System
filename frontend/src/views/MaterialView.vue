<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">资料管理</h1>
        <p class="page-desc">
          统一管理课程资料，支持录入文本、上传文件、解析内容，并继续进入 AI 总结和智能出题。
        </p>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <el-upload :show-file-list="false" :http-request="uploadMaterial" :disabled="uploading">
          <el-button :loading="uploading">上传文件</el-button>
        </el-upload>
        <el-button type="primary" :loading="tableLoading" @click="loadMaterials">刷新列表</el-button>
      </div>
    </div>

    <div class="content-grid content-grid--2">
      <div class="page-card">
        <div class="panel-title-row">
          <h3>新增文本资料</h3>
          <span class="soft-text">推荐先录入一节课的重点笔记</span>
        </div>
        <el-form label-position="top">
          <el-form-item label="资料标题">
            <el-input v-model="form.title" maxlength="100" placeholder="例如：数据库系统原理 第三章事务管理" />
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
            <el-input v-model="form.tags" placeholder="例如：数据库、期末复习、重点章节" />
          </el-form-item>
          <el-form-item label="资料内容">
            <el-input
              v-model="form.contentText"
              type="textarea"
              :rows="10"
              maxlength="10000"
              show-word-limit
              placeholder="请输入课堂笔记、知识点整理或教材摘录内容"
            />
          </el-form-item>
          <el-button type="primary" :loading="creating" @click="createMaterial">保存资料</el-button>
        </el-form>
      </div>

      <div class="page-card">
        <div class="panel-title-row">
          <h3>使用建议</h3>
        </div>
        <div class="tips-stack">
          <div class="tip-card">
            <div class="tip-card__title">适合放什么内容</div>
            <div class="tip-card__desc">课堂笔记、知识点清单、实验报告提纲、章节总结都可以。</div>
          </div>
          <div class="tip-card">
            <div class="tip-card__title">什么时候先解析</div>
            <div class="tip-card__desc">文件上传后建议先执行解析，再进行 AI 总结或 AI 出题。</div>
          </div>
          <div class="tip-card">
            <div class="tip-card__title">怎样更像完整产品</div>
            <div class="tip-card__desc">资料页负责沉淀知识资产，后面两步再把内容变成练习和复习闭环。</div>
          </div>
        </div>
      </div>
    </div>

    <div class="page-card" style="margin-top: 20px">
      <div class="panel-title-row">
        <h3>资料列表</h3>
        <span class="soft-text">共 {{ materials.length }} 条</span>
      </div>

      <div v-if="tableLoading" class="state-block">正在加载资料列表...</div>
      <div v-else-if="!materials.length" class="state-block empty">目前还没有资料，先新增一条文本资料或上传文件。</div>
      <el-table v-else :data="materials">
        <el-table-column prop="title" label="资料标题" min-width="220" />
        <el-table-column prop="materialType" label="类型" width="120" />
        <el-table-column prop="parseStatus" label="解析状态" width="130" />
        <el-table-column prop="summaryStatus" label="总结状态" width="130" />
        <el-table-column prop="totalCharacters" label="字数" width="100" />
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="actionLoadingId === row.id && actionType === 'parse'" @click="parseMaterial(row.id)">
              解析
            </el-button>
            <el-button link @click="viewDetail(row.id)">查看</el-button>
            <el-button link type="success" @click="goSummary(row.id)">去总结</el-button>
            <el-button link type="warning" @click="goQuiz(row.id)">去出题</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="drawerVisible" title="资料详情" size="52%">
      <template v-if="detailLoading">
        <div class="state-block">正在加载资料详情...</div>
      </template>
      <template v-else-if="detail">
        <div class="detail-meta-grid">
          <div class="detail-meta-item">
            <span>标题</span>
            <strong>{{ detail.title }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>类型</span>
            <strong>{{ detail.materialType }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>解析状态</span>
            <strong>{{ detail.parseStatus }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>总结状态</span>
            <strong>{{ detail.summaryStatus }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>标签</span>
            <strong>{{ detail.tags || '暂无' }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>字符数</span>
            <strong>{{ detail.totalCharacters || 0 }}</strong>
          </div>
        </div>

        <div v-if="detail.segments?.length" class="list-stack" style="margin-top: 18px">
          <div v-for="segment in detail.segments" :key="segment.id" class="section-card">
            <div class="section-card__title">{{ segment.sectionTitle || '未命名片段' }}</div>
            <div class="summary-block">{{ segment.contentText }}</div>
          </div>
        </div>
        <div v-else class="state-block empty" style="margin-top: 18px">
          这份资料暂时没有解析片段，可以先点击“解析”。
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  createTextMaterialApi,
  getMaterialDetailApi,
  getMaterialPageApi,
  parseMaterialApi,
  uploadMaterialApi
} from '@/api/modules/material'

const router = useRouter()
const materials = ref<any[]>([])
const detail = ref<any>(null)
const drawerVisible = ref(false)
const tableLoading = ref(false)
const detailLoading = ref(false)
const creating = ref(false)
const uploading = ref(false)
const actionLoadingId = ref<number | null>(null)
const actionType = ref('')

const form = reactive({
  title: '',
  materialType: 'TEXT',
  difficultyLevel: 3,
  tags: '',
  contentText: ''
})

const resetForm = () => {
  form.title = ''
  form.materialType = 'TEXT'
  form.difficultyLevel = 3
  form.tags = ''
  form.contentText = ''
}

const loadMaterials = async () => {
  tableLoading.value = true
  try {
    const res = await getMaterialPageApi({ current: 1, size: 50 })
    materials.value = res.data.data.records || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载资料失败')
  } finally {
    tableLoading.value = false
  }
}

const createMaterial = async () => {
  if (!form.title.trim() || !form.contentText.trim()) {
    ElMessage.warning('请先填写资料标题和内容')
    return
  }

  creating.value = true
  try {
    await createTextMaterialApi({
      ...form,
      title: form.title.trim(),
      tags: form.tags.trim(),
      contentText: form.contentText.trim()
    })
    ElMessage.success('资料保存成功')
    resetForm()
    await loadMaterials()
  } catch (error: any) {
    ElMessage.error(error.message || '新增资料失败')
  } finally {
    creating.value = false
  }
}

const parseMaterial = async (id: number) => {
  actionLoadingId.value = id
  actionType.value = 'parse'
  try {
    await parseMaterialApi(id)
    ElMessage.success('资料解析成功')
    await loadMaterials()
  } catch (error: any) {
    ElMessage.error(error.message || '资料解析失败')
  } finally {
    actionLoadingId.value = null
    actionType.value = ''
  }
}

const viewDetail = async (id: number) => {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await getMaterialDetailApi(id)
    detail.value = res.data.data
  } catch (error: any) {
    detail.value = null
    ElMessage.error(error.message || '加载资料详情失败')
  } finally {
    detailLoading.value = false
  }
}

const uploadMaterial = async (options: UploadRequestOptions) => {
  uploading.value = true
  try {
    await uploadMaterialApi(options.file as File)
    ElMessage.success('资料上传成功')
    await loadMaterials()
    options.onSuccess?.({})
  } catch (error: any) {
    options.onError?.(error)
    ElMessage.error(error.message || '资料上传失败')
  } finally {
    uploading.value = false
  }
}

const goSummary = (id: number) => {
  router.push({ path: '/summary', query: { materialId: String(id) } })
}

const goQuiz = (id: number) => {
  router.push({ path: '/quiz', query: { materialId: String(id) } })
}

loadMaterials()
</script>
