<template>
  <NotePanelShell :model-value="visible" :compact="compact" :before-close="closeEditor">
    <template #header>
      <div class="note-editor-head">
        <button class="note-icon" type="button" aria-label="收起笔记" @click="closeEditor()">
          <MobileIcon name="back" :size="22" />
        </button>
        <span class="note-save-state" :class="{ 'has-error': saveError }" role="status">{{ saveStatus }}</span>
        <button
          class="note-icon"
          type="button"
          aria-label="撤销"
          :disabled="!editor?.can().undo()"
          @click="editor?.chain().focus().undo().run()"
        >
          ↶
        </button>
        <button
          class="note-icon"
          type="button"
          aria-label="重做"
          :disabled="!editor?.can().redo()"
          @click="editor?.chain().focus().redo().run()"
        >
          ↷
        </button>
        <button
          class="note-icon"
          type="button"
          aria-label="笔记大纲"
          :aria-expanded="showOutline"
          @click="showOutline = !showOutline"
        >
          <AppIcon name="list" :size="20" />
        </button>
      </div>
    </template>
    <div v-if="loading" class="note-editor-message" role="status">正在打开笔记…</div>
    <div v-else-if="loadError" class="note-editor-message" role="alert">
      <p>{{ loadError }}</p>
      <button class="note-soft-button" type="button" @click="initialize">重新加载</button>
    </div>
    <div v-else class="note-editor-document">
      <div class="note-editor-meta">
        <label class="note-notebook-picker">
          <MobileIcon name="book" :size="16" />
          <select v-model="form.notebookId" aria-label="归档到笔记本" @change="changed">
            <option :value="null">随手记</option>
            <option v-for="book in notebooks" :key="book.id" :value="book.id">{{ book.name }}</option>
          </select>
        </label>
        <button
          class="note-icon"
          type="button"
          :aria-label="form.favorite ? '取消置顶' : '置顶笔记'"
          :aria-pressed="form.favorite"
          @click="toggleFavorite"
        >
          <AppIcon name="pin" :size="18" />
        </button>
        <button
          class="note-icon"
          type="button"
          aria-label="导出笔记"
          title="导出笔记"
          :disabled="exporting"
          @click="exportNote"
        >
          <AppIcon name="download" :size="18" />
        </button>
      </div>
      <p v-if="notebookError" class="note-inline-error">
        {{ notebookError }}<button type="button" @click="loadNotebooks">重试</button>
      </p>
      <div v-if="source.sourceType !== 'GENERAL'" class="note-source">
        <button v-if="sourceRoute" type="button" class="note-source__link" @click="goToSource">
          <MobileIcon :name="source.sourceType === 'QUESTION' ? 'pencil' : 'book'" :size="16" />
          <span
            >{{ source.sourceTitle || '查看出处'
            }}<small v-if="source.pageNumber"> · 第 {{ source.pageNumber }} 页</small></span
          >
          <MobileIcon name="next" :size="14" />
        </button>
        <p v-else class="note-source__label">{{ source.sourceTitle || '学习记录' }}</p>
        <blockquote v-if="source.sourceExcerpt">{{ source.sourceExcerpt }}</blockquote>
      </div>
      <nav v-if="showOutline" class="note-outline" aria-label="笔记大纲">
        <span v-if="!outline.length">添加标题后会显示大纲</span>
        <button
          v-for="heading in outline"
          :key="heading.pos"
          type="button"
          :style="{ paddingLeft: (heading.level - 1) * 12 + 'px' }"
          @click="jumpHeading(heading.pos)"
        >
          {{ heading.text || '未命名标题' }}
        </button>
      </nav>
      <input
        v-model="form.title"
        class="note-title-input"
        aria-label="笔记标题"
        placeholder="请输入标题"
        maxlength="200"
        @input="changed"
      />
      <EditorContent :editor="editor" class="note-rich-editor" />
    </div>
    <template #footer>
      <div v-if="imageError" class="note-save-error" role="alert">
        <span>{{ imageError }}</span>
        <button type="button" :disabled="imageUploading > 0" @click="retryImages">重试插入</button>
        <button type="button" @click="dismissImageError">关闭提示</button>
      </div>
      <div v-if="saveError" class="note-save-error" role="alert">
        <span>{{ saveError }}</span>
        <button type="button" @click="save">重试保存</button>
        <button v-if="conflict" type="button" @click="saveCopy">另存一份</button>
      </div>
      <div
        v-if="!loading && !loadError"
        class="note-format-bar"
        role="toolbar"
        aria-label="笔记排版"
        @mousedown="preserveEditorSelection"
      >
        <input
          ref="imageInput"
          class="visually-hidden"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          multiple
          tabindex="-1"
          aria-label="选择笔记图片"
          @change="chooseImages"
        />
        <button
          type="button"
          aria-label="插入图片"
          title="插入图片，也可直接粘贴截图"
          :disabled="imageUploading > 0"
          @click="openImagePicker"
        >
          <svg
            viewBox="0 0 24 24"
            width="21"
            height="21"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <rect x="3" y="3" width="18" height="18" rx="3" />
            <circle cx="8.5" cy="8.5" r="1.5" />
            <path d="m4 18 5-5 3 3 3-6 6 8" />
          </svg>
        </button>
        <select :value="headingLevel" aria-label="段落样式" @change="setHeading">
          <option value="0">正文</option>
          <option value="1">标题 1</option>
          <option value="2">标题 2</option>
          <option value="3">标题 3</option>
        </select>
        <button
          type="button"
          aria-label="加粗"
          :aria-pressed="editor?.isActive('bold')"
          @click="editor?.chain().focus().toggleBold().run()"
        >
          <b>B</b>
        </button>
        <button
          type="button"
          aria-label="斜体"
          :aria-pressed="editor?.isActive('italic')"
          @click="editor?.chain().focus().toggleItalic().run()"
        >
          <i>I</i>
        </button>
        <button
          type="button"
          aria-label="项目列表"
          :aria-pressed="editor?.isActive('bulletList')"
          @click="editor?.chain().focus().toggleBulletList().run()"
        >
          <AppIcon name="list" :size="20" />
        </button>
        <button
          type="button"
          aria-label="待办清单"
          :aria-pressed="editor?.isActive('taskList')"
          @click="editor?.chain().focus().toggleTaskList().run()"
        >
          <MobileIcon name="check" :size="20" />
        </button>
        <button
          type="button"
          aria-label="引用"
          :aria-pressed="editor?.isActive('blockquote')"
          @click="editor?.chain().focus().toggleBlockquote().run()"
        >
          “
        </button>
        <button
          type="button"
          aria-label="代码块"
          :aria-pressed="editor?.isActive('codeBlock')"
          @click="editor?.chain().focus().toggleCodeBlock().run()"
        >
          <span class="note-code-icon">&lt;/&gt;</span>
        </button>
      </div>
    </template>
  </NotePanelShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { EditorState } from '@tiptap/pm/state'
import StarterKit from '@tiptap/starter-kit'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Placeholder from '@tiptap/extension-placeholder'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import MobileIcon from '@/components/MobileIcon.vue'
import NotePanelShell from '@/components/notes/NotePanelShell.vue'
import {
  NoteImage,
  addImagePlaceholder,
  removeImagePlaceholder,
  imagePlaceholderPosition
} from '@/components/notes/noteImageExtension'
import { prepareNoteImage, downloadNoteDocument } from '@/utils/noteImageFiles'
import {
  createNoteApi,
  getNoteApi,
  getNotebooksApi,
  updateNoteApi,
  uploadNoteImageApi,
  isNoteImageUrl,
  type NoteContext,
  type NoteNotebook,
  type PersonalNote
} from '@/api/modules/note'
import { useNoteWorkspace } from '@/composables/useNoteWorkspace'
import { useUserStore } from '@/stores/user'
import { emptyNoteDocument, noteSourceRoute, readNoteDocument } from '@/utils/noteDocument'
import { getErrorMessage } from '@/utils/errorMessage'

const router = useRouter()
const userStore = useUserStore()
const { visible, request, notifySaved } = useNoteWorkspace()
const compact = ref(window.innerWidth < 1100)
const loading = ref(false)
const loadError = ref('')
const saveError = ref('')
const notebookError = ref('')
const saving = ref(false)
const conflict = ref(false)
const restored = ref(false)
const notebooks = ref<NoteNotebook[]>([])
const noteId = ref<number | null>(null)
const source = ref<NoteContext>({ sourceType: 'GENERAL' })
const form = reactive({
  title: '',
  contentJson: JSON.stringify(emptyNoteDocument()),
  contentText: '',
  notebookId: null as number | null,
  favorite: false
})
const showOutline = ref(false)
const editorRevision = ref(0)
const imageInput = ref<HTMLInputElement>()
const imageUploading = ref(0)
const imageError = ref('')
const exporting = ref(false)
let failedImages: File[] = []
let imageInsertionPosition: number | undefined
let imageUploadController: AbortController | null = null
let version = 0
let clientKey = ''
let activeKey = 0
let ownerToken = ''
let draftKey = ''
let savedFingerprint = ''
let localDraftStored = true
let timer: ReturnType<typeof setTimeout> | null = null
let saveTask: { key: number; promise: Promise<boolean> } | null = null
let media: MediaQueryList | null = null

const editor = useEditor({
  extensions: [
    StarterKit.configure({ heading: { levels: [1, 2, 3] }, link: false }),
    TaskList,
    TaskItem.configure({ nested: true }),
    NoteImage,
    Placeholder.configure({ placeholder: '记下此刻的理解…' })
  ],
  content: emptyNoteDocument(),
  editorProps: {
    attributes: { 'aria-label': '笔记正文', role: 'textbox', 'aria-multiline': 'true', spellcheck: 'false' },
    handlePaste(view, event) {
      const files = Array.from(event.clipboardData?.files || [])
      if (!files.length) return false
      event.preventDefault()
      void insertImages(files, view.state.selection.from)
      return true
    },
    handleDrop(view, event, _slice, moved) {
      const files = Array.from(event.dataTransfer?.files || [])
      if (moved || !files.length) return false
      event.preventDefault()
      const position = view.posAtCoords({ left: event.clientX, top: event.clientY })?.pos
      void insertImages(files, position)
      return true
    }
  },
  onUpdate({ editor: current }) {
    form.contentJson = JSON.stringify(current.getJSON())
    form.contentText = current.getText()
    editorRevision.value++
    changed()
  },
  onSelectionUpdate() {
    editorRevision.value++
  }
})
const fieldsFingerprint = (value: typeof form) =>
  JSON.stringify({
    title: value.title.trim(),
    contentJson: value.contentJson,
    contentText: value.contentText,
    notebookId: value.notebookId ?? null,
    favorite: Boolean(value.favorite)
  })
const fingerprint = () => fieldsFingerprint(form)
const dirty = computed(() => {
  void editorRevision.value
  return fingerprint() !== savedFingerprint
})
const meaningful = () =>
  Boolean(form.title.trim() || form.contentText.trim() || form.contentJson.includes('"type":"image"'))
const saveable = () => noteId.value !== null || meaningful()
const sourceRoute = computed(() => noteSourceRoute(source.value))
const saveStatus = computed(() =>
  loading.value
    ? '打开中'
    : imageUploading.value > 0
      ? '正在插入图片…'
      : saving.value
        ? '保存中…'
        : saveError.value
          ? localDraftStored
            ? '已暂存本机'
            : '尚未保存'
          : restored.value && dirty.value
            ? '已恢复草稿'
            : dirty.value && saveable()
              ? '待保存'
              : noteId.value
                ? '已保存'
                : '随手记'
)
const headingLevel = computed(() => {
  void editorRevision.value
  return editor.value?.isActive('heading') ? String(editor.value.getAttributes('heading').level) : '0'
})
const outline = computed(() => {
  void editorRevision.value
  const headings: { pos: number; level: number; text: string }[] = []
  editor.value?.state.doc.descendants((node, pos) => {
    if (node.type.name === 'heading')
      headings.push({ pos: pos + 1, level: Number(node.attrs.level), text: node.textContent })
  })
  return headings
})

function removeDraft() {
  try {
    if (draftKey) localStorage.removeItem(draftKey)
  } catch {
    /* A saved server copy remains available. */
  }
}
function persistDraft() {
  if (!draftKey || loading.value || !saveable()) return
  if (fingerprint() === savedFingerprint) {
    removeDraft()
    return
  }
  try {
    localStorage.setItem(
      draftKey,
      JSON.stringify({ ...form, id: noteId.value, version, clientKey, source: source.value })
    )
    localDraftStored = true
  } catch {
    localDraftStored = false
  }
}
function changed() {
  if (loading.value || !visible.value) return
  persistDraft()
  if (timer) clearTimeout(timer)
  if (conflict.value) return
  timer = setTimeout(() => void save(), 700)
}
function toggleFavorite() {
  form.favorite = !form.favorite
  changed()
}
async function loadNotebooks() {
  const token = userStore.token
  try {
    const result = await getNotebooksApi()
    if (token !== userStore.token) return
    notebooks.value = result.data.data
    notebookError.value = ''
  } catch (error) {
    if (token === userStore.token) notebookError.value = getErrorMessage(error, '笔记本暂未加载')
  }
}
function applyNote(note: PersonalNote) {
  noteId.value = note.id
  version = note.version
  clientKey = note.clientKey || clientKey
  source.value = {
    sourceType: note.sourceType,
    sourceId: note.sourceId,
    practiceSessionId: note.practiceSessionId,
    pageNumber: note.pageNumber,
    sourceTitle: note.sourceTitle,
    sourceExcerpt: note.sourceExcerpt
  }
  Object.assign(form, {
    title: note.title,
    contentJson: note.contentJson,
    contentText: note.contentText,
    notebookId: note.notebookId ?? null,
    favorite: note.favorite
  })
}
async function initialize() {
  const current = request.value
  if (!current) return
  imageUploadController?.abort()
  imageUploading.value = 0
  imageError.value = ''
  failedImages = []
  if (timer) clearTimeout(timer)
  const key = (activeKey = current.key)
  ownerToken = userStore.token
  loading.value = true
  saving.value = false
  loadError.value = saveError.value = ''
  conflict.value = restored.value = false
  showOutline.value = false
  noteId.value = null
  version = 0
  clientKey = crypto.randomUUID()
  source.value = current.context || { sourceType: 'GENERAL' }
  Object.assign(form, {
    title: '',
    contentJson: JSON.stringify(emptyNoteDocument()),
    contentText: '',
    notebookId: current.notebookId ?? null,
    favorite: false
  })
  const owner = userStore.profile?.id || userStore.profile?.username
  const target = current.id
    ? `note-${current.id}`
    : `new-${source.value.sourceType}-${source.value.sourceId || 0}-${source.value.practiceSessionId || 0}-${source.value.pageNumber || 0}-${current.notebookId || 0}`
  draftKey = owner ? `personal-note-draft:${owner}:${target}` : ''
  void loadNotebooks()
  let draft: (typeof form & { id: number | null; version: number; clientKey: string; source: NoteContext }) | null =
    null
  try {
    const stored = draftKey ? localStorage.getItem(draftKey) : null
    if (stored) {
      const parsed = JSON.parse(stored)
      readNoteDocument(parsed.contentJson)
      if (
        typeof parsed.title !== 'string' ||
        typeof parsed.contentText !== 'string' ||
        typeof parsed.clientKey !== 'string' ||
        !Number.isInteger(parsed.version)
      )
        throw new Error('Invalid draft')
      draft = parsed
    }
  } catch {
    ElMessage.warning('本机草稿无法读取，将尝试打开服务器版本')
  }
  try {
    let serverNote: PersonalNote | null = null
    const existingId = current.id || draft?.id
    if (existingId) {
      try {
        const result = await getNoteApi(existingId)
        if (key !== activeKey || ownerToken !== userStore.token) return
        serverNote = result.data.data
        applyNote(serverNote)
      } catch (error) {
        if (key !== activeKey || ownerToken !== userStore.token) return
        if (!draft) throw error
        saveError.value = '暂时无法同步，已打开本机草稿'
      }
    }
    savedFingerprint = saveError.value ? '' : fingerprint()
    if (draft) {
      if (serverNote && fieldsFingerprint(draft) === savedFingerprint) {
        removeDraft()
        if (!current.id) {
          // A completed save can leave a stale new-note draft after a lost response or tab close.
          // Starting another note must not edit the already saved document.
          noteId.value = null
          version = 0
          clientKey = crypto.randomUUID()
          source.value = current.context || { sourceType: 'GENERAL' }
          Object.assign(form, {
            title: '',
            contentJson: JSON.stringify(emptyNoteDocument()),
            contentText: '',
            notebookId: current.notebookId ?? null,
            favorite: false
          })
          savedFingerprint = fingerprint()
        }
      } else {
        Object.assign(form, {
          title: draft.title,
          contentJson: draft.contentJson,
          contentText: draft.contentText,
          notebookId: draft.notebookId ?? null,
          favorite: Boolean(draft.favorite)
        })
        noteId.value = serverNote?.id || draft.id || null
        version = draft.version
        clientKey = draft.clientKey
        if (!serverNote && draft.source) source.value = draft.source
        restored.value = true
        if (serverNote && draft.version !== serverNote.version) {
          conflict.value = true
          saveError.value = '笔记已在其他窗口更新，本机草稿已保留，可另存一份'
        }
      }
    }
    await nextTick()
    if (key !== activeKey) return
    const currentEditor = editor.value
    if (currentEditor) {
      currentEditor.commands.setContent(readNoteDocument(form.contentJson), { emitUpdate: false })
      // Each note starts with its own undo history; undo must never reveal a previously opened note.
      currentEditor.view.updateState(
        EditorState.create({
          doc: currentEditor.state.doc,
          schema: currentEditor.schema,
          plugins: currentEditor.state.plugins
        })
      )
    }
    editorRevision.value++
  } catch (error) {
    if (key === activeKey) loadError.value = getErrorMessage(error, '笔记无法打开')
  } finally {
    if (key === activeKey) {
      loading.value = false
      if (restored.value && !loadError.value && !saveError.value) changed()
    }
  }
}

async function performSave(): Promise<boolean> {
  const key = activeKey
  if (loading.value || loadError.value || !saveable() || !dirty.value) return true
  saving.value = true
  saveError.value = ''
  try {
    while (key === activeKey && ownerToken === userStore.token && saveable() && fingerprint() !== savedFingerprint) {
      const submitted = { ...form, title: form.title.trim() }
      if (submitted.contentJson.length > 200000 || submitted.contentText.length > 100000)
        throw new Error('这篇笔记较长，请导出后拆成两篇保存')
      const response = noteId.value
        ? await updateNoteApi(noteId.value, { ...submitted, version })
        : await createNoteApi({ ...submitted, ...source.value, clientKey })
      if (key !== activeKey || ownerToken !== userStore.token) return false
      const result = response.data.data
      noteId.value = result.id
      version = result.version
      if (!submitted.title && !form.title.trim()) form.title = result.title
      savedFingerprint = fieldsFingerprint(result)
      conflict.value = false
      restored.value = false
      editorRevision.value++
      if (fingerprint() === savedFingerprint) removeDraft()
      else persistDraft()
      notifySaved()
    }
    return true
  } catch (error) {
    if (key === activeKey) {
      saveError.value = getErrorMessage(error, '暂时未能保存，请重试')
      conflict.value = /版本|冲突|其他|更新|删除/.test(saveError.value)
      persistDraft()
    }
    return false
  } finally {
    if (key === activeKey) saving.value = false
  }
}
function save(): Promise<boolean> {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
  if (saveTask?.key === activeKey) return saveTask.promise
  const key = activeKey
  const promise = performSave().finally(() => {
    if (saveTask?.key === key) saveTask = null
  })
  saveTask = { key, promise }
  return promise
}
async function closeEditor(done?: () => void) {
  if (imageUploading.value > 0) {
    ElMessage.info('图片正在上传，完成后即可收起')
    return
  }
  persistDraft()
  // Closing remains available even while the network is slow; the draft is already local.
  void save()
  visible.value = false
  done?.()
}
async function saveCopy() {
  if (saving.value) return
  noteId.value = null
  version = 0
  clientKey = crypto.randomUUID()
  form.title = (form.title || '笔记') + '（副本）'
  savedFingerprint = ''
  persistDraft()
  await save()
}
async function goToSource() {
  const target = sourceRoute.value
  if (!target) return
  await closeEditor()
  if (visible.value) return
  await router.push(target)
}
function setHeading(event: Event) {
  const level = Number((event.target as HTMLSelectElement).value)
  if (!level) editor.value?.chain().focus().setParagraph().run()
  else
    editor.value
      ?.chain()
      .focus()
      .setHeading({ level: level as 1 | 2 | 3 })
      .run()
}
function preserveEditorSelection(event: MouseEvent) {
  if ((event.target as HTMLElement).closest('button')) event.preventDefault()
}
function jumpHeading(pos: number) {
  editor.value?.chain().focus().setTextSelection(pos).scrollIntoView().run()
  showOutline.value = false
}
async function exportNote() {
  if (exporting.value || imageUploading.value) return
  exporting.value = true
  try {
    await downloadNoteDocument({
      title: form.title,
      document: readNoteDocument(form.contentJson),
      source: { ...source.value }
    })
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '导出失败，请稍后重试'))
  } finally {
    exporting.value = false
  }
}
function openImagePicker() {
  imageInsertionPosition = editor.value?.state.selection.from
  imageInput.value?.click()
}
function chooseImages(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  void insertImages(files, imageInsertionPosition)
}
function retryImages() {
  void insertImages([...failedImages])
}
function dismissImageError() {
  imageError.value = ''
  failedImages = []
}
async function insertImages(files: File[], position?: number) {
  const currentEditor = editor.value
  if (!currentEditor || loading.value || !visible.value || !files.length) return
  if (imageUploading.value) {
    ElMessage.info('请等当前图片插入完成')
    return
  }
  if (files.length > 6) {
    ElMessage.warning('一次最多插入 6 张图片')
    return
  }
  const key = activeKey
  const token = userStore.token
  const controller = (imageUploadController = new AbortController())
  imageError.value = ''
  failedImages = []
  imageUploading.value = files.length
  const start = Math.max(
    0,
    Math.min(position ?? currentEditor.state.selection.from, currentEditor.state.doc.content.size)
  )
  const placeholders = files.map(() => crypto.randomUUID())
  placeholders.forEach((id) => addImagePlaceholder(currentEditor, id, start))
  try {
    for (const [index, file] of files.entries()) {
      if (controller.signal.aborted || key !== activeKey || token !== userStore.token) return
      try {
        const imageFile = await prepareNoteImage(file)
        const response = await uploadNoteImageApi(imageFile, controller.signal)
        if (controller.signal.aborted || key !== activeKey || token !== userStore.token) return
        const data = response.data.data
        if (!isNoteImageUrl(data.url)) throw new Error('图片地址无效')
        const target = imagePlaceholderPosition(currentEditor, placeholders[index])
        if (target !== undefined)
          currentEditor.commands.insertContentAt(
            target,
            { type: 'image', attrs: { src: data.url, width: data.width, height: data.height, alt: '' } },
            { updateSelection: false }
          )
      } catch (error) {
        if (controller.signal.aborted || key !== activeKey || token !== userStore.token) return
        failedImages.push(file)
        imageError.value = getErrorMessage(error, '图片未能插入，请重试')
      } finally {
        if (key === activeKey && !currentEditor.isDestroyed) {
          removeImagePlaceholder(currentEditor, placeholders[index])
          imageUploading.value--
        }
      }
    }
  } finally {
    if (key === activeKey) imageUploading.value = 0
  }
}
function syncLayout() {
  compact.value = window.innerWidth < 1100
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (imageUploading.value) {
    event.preventDefault()
    event.returnValue = ''
  }
  if (visible.value && dirty.value) {
    persistDraft()
    if (!localDraftStored) {
      event.preventDefault()
      event.returnValue = ''
    }
  }
}
watch(
  request,
  () => {
    void initialize()
  },
  { immediate: true }
)
watch([visible, compact], () => document.body.classList.toggle('note-split-open', visible.value && !compact.value), {
  immediate: true
})
watch(
  () => userStore.token,
  (value) => {
    if (value !== ownerToken) {
      imageUploadController?.abort()
      activeKey++
      visible.value = false
      if (timer) clearTimeout(timer)
    }
  }
)
onMounted(() => {
  media = window.matchMedia('(max-width: 1099px)')
  media.addEventListener('change', syncLayout)
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => {
  imageUploadController?.abort()
  if (visible.value) persistDraft()
  activeKey++
  if (timer) clearTimeout(timer)
  media?.removeEventListener('change', syncLayout)
  window.removeEventListener('beforeunload', beforeUnload)
  document.body.classList.remove('note-split-open')
})
</script>

<style>
.note-editor-drawer {
  --el-drawer-padding-primary: 0px;
  background: var(--panel);
  box-shadow: -8px 0 36px rgb(0 0 0 / 9%);
}
.note-editor-drawer .el-drawer__header {
  margin: 0;
  padding: 8px 14px;
  border-bottom: 1px solid var(--line);
}
.note-editor-drawer .el-drawer__body {
  min-height: 0;
  padding: 0;
  overscroll-behavior: contain;
}
.note-editor-drawer .el-drawer__footer {
  padding: 0;
  border-top: 1px solid var(--line);
  text-align: left;
}
.note-editor-head {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 4px;
}
.note-icon {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 24px;
  cursor: pointer;
}
.note-icon:hover,
.note-icon[aria-pressed='true'] {
  background: var(--brand-light);
  color: var(--brand);
}
.note-icon:disabled {
  color: var(--line-strong);
  cursor: default;
}
.note-save-state {
  flex: 1;
  font-size: 12px;
  color: var(--muted);
}
.note-save-state.has-error {
  color: var(--accent);
}
.note-editor-document {
  padding: 12px 26px 36px;
}
.note-editor-meta {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 10px;
}
.note-notebook-picker {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  color: var(--muted);
}
.note-notebook-picker select {
  max-width: 220px;
  width: 100%;
  border: 0;
  outline-offset: 4px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
}
.note-title-input {
  display: block;
  width: 100%;
  margin: 24px 0 18px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 27px;
  font-weight: 650;
  line-height: 1.5;
  outline: none;
}
.note-title-input::placeholder {
  color: #b6b7b8;
}
.note-source {
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--panel-hover);
}
.note-source__link {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text-secondary);
  text-align: left;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.note-source__link span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.note-source__link small {
  font-size: inherit;
}
.note-source__label {
  margin: 0;
  font-size: 12px;
}
.note-source blockquote {
  max-height: 110px;
  overflow: auto;
  margin: 8px 0 0;
  padding-left: 10px;
  border-left: 2px solid var(--line-strong);
  color: var(--muted);
  font-size: 12px;
  white-space: pre-wrap;
  line-height: 1.65;
}
.note-rich-editor .tiptap {
  min-height: 320px;
  outline: none;
  color: var(--text);
  font-size: 16px;
  line-height: 1.85;
  overflow-wrap: anywhere;
}
.note-rich-editor .tiptap > :first-child {
  margin-top: 0;
}
.note-rich-editor p {
  margin: 0 0 14px;
}
.note-rich-editor h1,
.note-rich-editor h2,
.note-rich-editor h3 {
  margin: 28px 0 14px;
  line-height: 1.5;
}
.note-rich-editor h1 {
  font-size: 26px;
}
.note-rich-editor h2 {
  font-size: 22px;
}
.note-rich-editor h3 {
  font-size: 19px;
}
.note-rich-editor ul,
.note-rich-editor ol {
  padding-left: 24px;
}
.note-rich-editor blockquote {
  margin: 16px 0;
  padding: 2px 14px;
  border-left: 3px solid var(--line-strong);
  color: var(--text-secondary);
}
.note-rich-editor pre {
  overflow: auto;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel-hover);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre;
}
.note-rich-editor code {
  border-radius: 4px;
  background: var(--bg-secondary);
  font-family: var(--font-mono);
  font-size: 0.88em;
}
.note-rich-editor pre code {
  background: none;
  font-size: inherit;
}
.note-rich-editor ul[data-type='taskList'] {
  padding-left: 0;
  list-style: none;
}
.note-rich-editor ul[data-type='taskList'] li {
  display: flex;
  gap: 10px;
}
.note-rich-editor ul[data-type='taskList'] li > label {
  flex: 0 0 auto;
}
.note-rich-editor ul[data-type='taskList'] li > div {
  flex: 1;
  min-width: 0;
}
.note-rich-editor input[type='checkbox'] {
  accent-color: var(--brand);
  width: 17px;
  height: 17px;
}
.note-rich-editor .is-editor-empty:first-child::before {
  height: 0;
  float: left;
  color: #aaa;
  content: attr(data-placeholder);
  pointer-events: none;
}
.note-format-bar {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: thin;
  padding: 8px 10px max(8px, env(safe-area-inset-bottom));
  background: var(--panel);
}
.note-format-bar button {
  display: grid;
  place-items: center;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 10px;
  background: none;
  color: var(--text-secondary);
  font: inherit;
  font-size: 20px;
  cursor: pointer;
}
.note-format-bar button[aria-pressed='true'] {
  color: var(--brand);
  background: var(--brand-light);
}
.note-format-bar select {
  flex: 0 0 68px;
  width: 68px;
  height: 44px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
}
.note-code-icon {
  font-family: var(--font-mono);
  font-size: 14px;
}
.note-outline {
  display: grid;
  gap: 10px;
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
}
.note-outline button {
  border: 0;
  background: none;
  text-align: left;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
.note-editor-message {
  padding: 32px;
  color: var(--muted);
  text-align: center;
}
.note-soft-button {
  border: 0;
  padding: 10px 18px;
  border-radius: 10px;
  background: var(--brand-light);
  color: var(--brand);
  cursor: pointer;
}
.note-save-error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 12px;
  padding: 10px 18px;
  color: var(--red);
  background: var(--red-soft);
  font-size: 12px;
}
.note-save-error span {
  flex: 1 1 100%;
}
.note-save-error button,
.note-inline-error button {
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
.note-inline-error {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 12px;
}
.note-editor-drawer button:focus-visible,
.note-editor-drawer select:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}
@media (min-width: 1100px) {
  body.note-split-open .workspace-content {
    max-width: 1660px;
    padding-right: 492px;
  }
}
@media (max-width: 1099px) {
  .note-editor-drawer .el-drawer__header {
    padding-top: max(8px, env(safe-area-inset-top));
  }
  .note-editor-document {
    max-width: 720px;
    margin: 0 auto;
    padding-inline: 22px;
  }
  .note-icon {
    width: 44px;
    height: 44px;
  }
  .note-title-input {
    font-size: 27px;
  }
}
</style>
