<template>
  <el-dialog
    :model-value="modelValue"
    title="更换封面"
    width="min(420px, calc(100vw - 32px))"
    class="material-cover-editor"
    append-to-body
    destroy-on-close
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    @update:model-value="close"
  >
    <div v-if="material" class="cover-editor">
      <div class="cover-editor__preview">
        <ResourceCover v-if="previewUrl" :title="material.title" subject="" :cover-url="previewUrl" />
        <MaterialCover
          v-else
          :material-id="material.id"
          :title="material.title"
          :cover-url="useDefault ? null : material.coverUrl"
        />
      </div>
      <p class="cover-editor__title">{{ material.title }}</p>
      <input
        ref="fileInput"
        type="file"
        accept="image/jpeg,image/png"
        hidden
        aria-label="选择封面图片"
        @change="selectFile"
      />
      <div class="cover-editor__choices">
        <el-button :disabled="busy" :loading="validating" @click="fileInput?.click()"
          ><AppIcon name="upload" :size="16" />选择图片</el-button
        >
        <el-button v-if="material.coverUrl || previewUrl" text :disabled="busy || useDefault" @click="chooseDefault"
          >恢复默认</el-button
        >
      </div>
      <p class="cover-editor__hint">JPG / PNG，最多 5 MB</p>
      <p v-if="error" class="cover-editor__error" role="alert">{{ error }}</p>
    </div>
    <template #footer>
      <el-button :disabled="busy" @click="close">取消</el-button>
      <el-button class="cover-editor__save" :loading="saving" :disabled="!changed || validating" @click="save"
        >保存</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import MaterialCover from './MaterialCover.vue'
import ResourceCover from './ResourceCover.vue'
import { resetMaterialCoverApi, uploadMaterialCoverApi, type MaterialPageItem } from '@/api/modules/material'
import { getErrorMessage } from '@/utils/errorMessage'

const props = defineProps<{ modelValue: boolean; material: MaterialPageItem | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  updated: [value: { id: number; coverUrl: string | null }]
}>()
const fileInput = ref<HTMLInputElement | null>(null)
const file = ref<File | null>(null)
const previewUrl = ref('')
const useDefault = ref(false)
const validating = ref(false)
const saving = ref(false)
const error = ref('')
const busy = computed(() => saving.value || validating.value)
const changed = computed(() => Boolean(file.value || (useDefault.value && props.material?.coverUrl)))
let selectionVersion = 0

function clearPreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  file.value = null
}

watch(
  () => [props.modelValue, props.material?.id],
  () => {
    selectionVersion++
    clearPreview()
    useDefault.value = false
    error.value = ''
    validating.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
)

async function selectFile(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = input.files?.[0]
  input.value = ''
  if (!selected || busy.value) return
  error.value = ''
  if (!['image/jpeg', 'image/png'].includes(selected.type)) {
    error.value = '请选择 JPG 或 PNG 图片'
    return
  }
  if (selected.size > 5 * 1024 * 1024) {
    error.value = '图片不能超过 5 MB'
    return
  }
  const version = ++selectionVersion
  const url = URL.createObjectURL(selected)
  validating.value = true
  try {
    const image = new Image()
    const dimensions = await new Promise<{ width: number; height: number }>((resolve, reject) => {
      image.onload = () => resolve({ width: image.naturalWidth, height: image.naturalHeight })
      image.onerror = () => reject(new Error('这张图片无法读取，请换一张'))
      image.src = url
    })
    if (version !== selectionVersion) return
    if (Math.max(dimensions.width, dimensions.height) > 8192 || dimensions.width * dimensions.height > 16000000) {
      throw new Error('图片尺寸太大，请缩小到 1600 万像素以内')
    }
    clearPreview()
    previewUrl.value = url
    file.value = selected
    useDefault.value = false
  } catch (reason) {
    if (version === selectionVersion) error.value = getErrorMessage(reason, '这张图片无法读取，请换一张')
  } finally {
    if (previewUrl.value !== url) URL.revokeObjectURL(url)
    if (version === selectionVersion) validating.value = false
  }
}

function chooseDefault() {
  clearPreview()
  useDefault.value = true
  error.value = ''
}

function close() {
  if (!busy.value) emit('update:modelValue', false)
}

async function save() {
  if (!props.material || !changed.value || busy.value) return
  const id = props.material.id
  saving.value = true
  error.value = ''
  try {
    const response = file.value ? await uploadMaterialCoverApi(id, file.value) : await resetMaterialCoverApi(id)
    emit('updated', { id, coverUrl: response.data.data.coverUrl })
    emit('update:modelValue', false)
    ElMessage.success('封面已更新')
  } catch (reason) {
    error.value = getErrorMessage(reason, '封面没有保存成功，请重试')
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(() => {
  selectionVersion++
  clearPreview()
})
</script>

<style scoped>
.cover-editor {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.cover-editor__preview {
  --resource-cover-width: 108px;
  padding: 12px 32px;
  border-radius: 16px;
  background: var(--bg);
}
.cover-editor__title {
  max-width: 100%;
  margin: 14px 0 18px;
  color: var(--text);
  font-weight: 500;
  text-align: center;
  overflow-wrap: anywhere;
}
.cover-editor__choices {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}
.cover-editor__choices :deep(.el-button) {
  min-height: 44px;
  margin: 0;
}
.cover-editor__hint {
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 12px;
}
.cover-editor__error {
  margin: 12px 0 0;
  color: var(--red);
  font-size: 13px;
  text-align: center;
}
.cover-editor__save {
  background: var(--accent-soft);
  border-color: var(--line);
  color: var(--text);
}
</style>
