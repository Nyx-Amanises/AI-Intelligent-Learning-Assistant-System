<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="min(520px, calc(100vw - 32px))"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item :label="label" :error="error">
        <el-input
          ref="inputRef"
          v-model="draft"
          :maxlength="maxlength"
          show-word-limit
          :placeholder="placeholder"
          @keyup.enter="submit"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'

/**
 * 通用重命名对话框。父组件监听 `save` 完成请求，成功后自行关闭（设置 modelValue=false）。
 */
const props = withDefaults(defineProps<{
  modelValue: boolean
  title?: string
  label?: string
  placeholder?: string
  initialValue?: string
  maxlength?: number
  loading?: boolean
}>(), {
  title: '重命名',
  label: '名称',
  placeholder: '请输入新的名称',
  initialValue: '',
  maxlength: 200,
  loading: false
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [value: string]
}>()

const draft = ref('')
const error = ref('')
const inputRef = ref<{ focus: () => void; select?: () => void } | null>(null)

const handleOpen = () => {
  draft.value = props.initialValue
  error.value = ''
  void nextTick(() => {
    inputRef.value?.focus()
    inputRef.value?.select?.()
  })
}

const submit = () => {
  const value = draft.value.trim()
  if (!value) {
    error.value = '名称不能为空'
    return
  }
  if (value === props.initialValue.trim()) {
    emit('update:modelValue', false)
    return
  }
  error.value = ''
  emit('save', value)
}
</script>
