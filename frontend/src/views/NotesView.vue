<template>
  <section class="learning-page notes-view" aria-labelledby="notes-title">
    <h1 id="notes-title" class="visually-hidden">我的笔记</h1>
    <header class="notes-header">
      <div class="notes-tabs" role="tablist" aria-label="笔记视图">
        <button
          id="notes-tab-quick"
          ref="quickTabButton"
          type="button"
          role="tab"
          :aria-selected="activeTab === 'notes'"
          :tabindex="activeTab === 'notes' ? 0 : -1"
          aria-controls="notes-panel"
          @click="changeTab('notes')"
          @keydown="handleTabKey"
        >
          小记
        </button>
        <button
          id="notes-tab-books"
          ref="notebookTabButton"
          type="button"
          role="tab"
          :aria-selected="activeTab === 'notebooks'"
          :tabindex="activeTab === 'notebooks' ? 0 : -1"
          aria-controls="notes-panel"
          @click="changeTab('notebooks')"
          @keydown="handleTabKey"
        >
          笔记本
        </button>
      </div>
      <div class="notes-header__actions">
        <div v-if="showNotebookIndex" class="notebook-layout" role="group" aria-label="笔记本布局">
          <button
            type="button"
            :aria-pressed="notebookLayout === 'list'"
            aria-label="列表布局"
            title="列表布局"
            @click="setNotebookLayout('list')"
          >
            <AppIcon name="list" :size="19" />
          </button>
          <button
            type="button"
            :aria-pressed="notebookLayout === 'grid'"
            aria-label="书架布局"
            title="书架布局"
            @click="setNotebookLayout('grid')"
          >
            <AppIcon name="grid" :size="18" />
          </button>
        </div>
        <button
          class="notes-icon-button"
          type="button"
          aria-label="刷新笔记"
          title="刷新"
          :disabled="visibleLoading"
          @click="refreshAll"
        >
          <AppIcon name="refresh" :size="19" />
        </button>
      </div>
    </header>

    <form v-if="searchVisible" class="notes-search" role="search" @submit.prevent="applySearch">
      <el-input
        ref="searchInput"
        v-model="keywordInput"
        clearable
        maxlength="100"
        :placeholder="showNotebookIndex ? '搜索笔记本' : '搜索标题或正文'"
        :aria-label="showNotebookIndex ? '搜索笔记本' : '搜索笔记标题或正文'"
        @input="scheduleSearch"
        @clear="applySearch"
        ><template #prefix><AppIcon name="search" :size="18" /></template
      ></el-input>
      <button type="button" class="notes-icon-button" aria-label="关闭搜索" @click="closeSearch">
        <AppIcon name="close" :size="18" />
      </button>
    </form>

    <div v-if="selectedNotebook" class="notebook-heading">
      <button type="button" class="notes-icon-button" aria-label="返回笔记本列表" @click="leaveNotebook">
        <AppIcon name="arrow-left" :size="21" />
      </button>
      <span class="notebook-heading__cover" :style="notebookColorStyle(selectedNotebook.color)" aria-hidden="true"
        ><AppIcon name="book-open" :size="24"
      /></span>
      <div>
        <h2>{{ selectedNotebook.name }}</h2>
        <p>{{ selectedNotebook.noteCount }} 篇小记</p>
      </div>
      <el-dropdown trigger="click" @command="handleNotebookCommand($event, selectedNotebook!)">
        <IconMenuButton :label="`${selectedNotebook.name}的操作`" :loading="notebookBusyIds.has(selectedNotebook.id)" />
        <template #dropdown
          ><el-dropdown-menu
            ><el-dropdown-item command="rename">重命名</el-dropdown-item
            ><el-dropdown-item command="delete" divided class="menu-item--danger"
              >删除笔记本</el-dropdown-item
            ></el-dropdown-menu
          ></template
        >
      </el-dropdown>
    </div>

    <nav v-if="!showNotebookIndex" class="notes-filters" aria-label="筛选小记">
      <button type="button" :aria-pressed="sourceGroup === ''" @click="changeSourceGroup('')">全部</button>
      <button type="button" :aria-pressed="sourceGroup === 'READING'" @click="changeSourceGroup('READING')">
        <AppIcon name="book-open" :size="15" />阅读
      </button>
      <button type="button" :aria-pressed="sourceGroup === 'QUESTION'" @click="changeSourceGroup('QUESTION')">
        <AppIcon name="quiz" :size="15" />做题
      </button>
      <button type="button" :aria-pressed="favoriteOnly" @click="toggleFavoriteFilter">
        <AppIcon name="pin" :size="15" />置顶
      </button>
      <span class="notes-sort-label">最近更新</span>
    </nav>

    <p v-if="actionError" class="notes-action-error" role="alert">
      <span>{{ actionError }}</span
      ><button type="button" class="notes-icon-button" aria-label="关闭操作提示" @click="actionError = ''">
        <AppIcon name="close" :size="16" />
      </button>
    </p>

    <div
      id="notes-panel"
      role="tabpanel"
      :aria-labelledby="activeTab === 'notes' ? 'notes-tab-quick' : 'notes-tab-books'"
      :aria-busy="visibleLoading"
    >
      <template v-if="showNotebookIndex">
        <div
          v-if="notebooksLoading"
          class="notebook-grid"
          :class="{ 'notebook-grid--list': notebookLayout === 'list' }"
          role="status"
          aria-label="正在加载笔记本"
        >
          <div v-for="i in 3" :key="i" class="notebook-card notebook-card--skeleton" aria-hidden="true">
            <span class="skeleton notebook-skeleton-cover" />
            <div><span class="skeleton notes-skeleton-title" /><span class="skeleton notes-skeleton-line" /></div>
          </div>
        </div>
        <EmptyState
          v-else-if="notebooksError"
          class="notes-empty"
          variant="error"
          title="笔记本暂时无法加载"
          :description="notebooksError"
          ><el-button @click="loadNotebooks">重试</el-button></EmptyState
        >
        <EmptyState
          v-else-if="!filteredNotebooks.length"
          class="notes-empty"
          icon="folder"
          :title="keywordInput.trim() ? '没有找到这个笔记本' : '建一本笔记，慢慢积累'"
          :description="keywordInput.trim() ? '换个名称试试。' : '按课程或主题整理你的阅读和练习小记。'"
          ><el-button v-if="keywordInput.trim()" @click="clearSearch">清除搜索</el-button
          ><el-button v-else class="notes-soft-button" @click="openNotebookDialog()">新建笔记本</el-button></EmptyState
        >
        <ul
          v-else
          class="notebook-grid"
          :class="{ 'notebook-grid--list': notebookLayout === 'list' }"
          aria-label="我的笔记本"
        >
          <li v-for="book in filteredNotebooks" :key="book.id" class="notebook-card">
            <button
              type="button"
              class="notebook-card__open"
              :aria-label="`打开笔记本：${book.name}`"
              @click="enterNotebook(book)"
            >
              <span class="notebook-cover" :style="notebookColorStyle(book.color)" aria-hidden="true"
                ><AppIcon name="book-open" :size="28"
              /></span>
              <span class="notebook-card__text"
                ><strong>{{ book.name }}</strong
                ><span>{{ book.noteCount }} 篇小记</span
                ><time :datetime="book.updatedAt" :title="formatDateTime(book.updatedAt)"
                  >{{ formatRelativeDay(book.updatedAt) }}更新</time
                ></span
              >
            </button>
            <el-dropdown trigger="click" @command="handleNotebookCommand($event, book)">
              <IconMenuButton :label="`${book.name}的操作`" :loading="notebookBusyIds.has(book.id)" />
              <template #dropdown
                ><el-dropdown-menu
                  ><el-dropdown-item command="rename">重命名</el-dropdown-item
                  ><el-dropdown-item command="delete" divided class="menu-item--danger"
                    >删除笔记本</el-dropdown-item
                  ></el-dropdown-menu
                ></template
              >
            </el-dropdown>
          </li>
        </ul>
      </template>
      <template v-else>
        <div v-if="notesLoading" class="note-list" role="status" aria-label="正在加载小记">
          <div v-for="i in 6" :key="i" class="note-row" aria-hidden="true">
            <span class="skeleton note-row__skeleton" />
          </div>
        </div>
        <EmptyState
          v-else-if="notesError"
          class="notes-empty"
          variant="error"
          title="小记暂时无法加载"
          :description="notesError"
          ><el-button @click="loadNotes">重试</el-button></EmptyState
        >
        <EmptyState
          v-else-if="!notes.length"
          class="notes-empty"
          icon="summary"
          :title="hasNoteFilters ? '没有找到相关小记' : selectedNotebook ? '这本笔记还没有内容' : '记下此刻的想法'"
          :description="hasNoteFilters ? '换个关键词，或清除筛选。' : '读到的重点、做题的思路，都可以记在这里。'"
          ><el-button v-if="hasNoteFilters" @click="resetNoteFilters">清除筛选</el-button
          ><el-button v-else class="notes-soft-button" @click="startNote">写一条小记</el-button></EmptyState
        >
        <ul v-else class="note-list" aria-label="小记列表">
          <li v-for="note in orderedNotes" :key="note.id" class="note-row">
            <button
              type="button"
              class="note-row__open"
              :disabled="openingNoteId !== null"
              :aria-label="`打开小记：${noteTitle(note)}`"
              :aria-description="note.favorite ? '已置顶' : undefined"
              :title="noteTitle(note)"
              @click="openExistingNote(note.id)"
            >
              <span v-if="note.favorite" class="note-row__pin" aria-hidden="true"
                ><AppIcon name="pin" :size="14"
              /></span>
              <span class="note-row__title">{{ noteTitle(note) }}</span>
            </button>
            <el-dropdown trigger="click" @command="handleNoteCommand($event, note)">
              <IconMenuButton :label="`${noteTitle(note)}的操作`" :loading="noteBusyIds.has(note.id)" />
              <template #dropdown
                ><el-dropdown-menu
                  ><el-dropdown-item command="open">打开小记</el-dropdown-item
                  ><el-dropdown-item command="favorite">{{ note.favorite ? '取消置顶' : '置顶' }}</el-dropdown-item
                  ><el-dropdown-item command="delete" divided class="menu-item--danger"
                    >删除小记</el-dropdown-item
                  ></el-dropdown-menu
                ></template
              >
            </el-dropdown>
          </li>
        </ul>
        <div v-if="!notesLoading && !notesError && total > PAGE_SIZE" class="notes-pagination">
          <el-pagination
            :current-page="currentPage"
            :page-size="PAGE_SIZE"
            :total="total"
            :pager-count="5"
            layout="prev, pager, next"
            @current-change="changePage"
          />
        </div>
      </template>
    </div>

    <div class="notes-dock" role="group" aria-label="笔记快捷操作">
      <button
        type="button"
        class="notes-dock__search"
        :aria-label="showNotebookIndex ? '搜索笔记本' : '搜索小记'"
        :aria-expanded="searchVisible"
        @click="toggleSearch"
      >
        <AppIcon name="search" :size="23" /><span>搜索</span>
      </button>
      <span class="notes-dock__divider" aria-hidden="true" />
      <button
        type="button"
        class="notes-dock__create"
        :aria-label="showNotebookIndex ? '新建笔记本' : '新建小记'"
        :disabled="creatingNote"
        @click="handleCreate"
      >
        <AppIcon name="plus" :size="26" /><span>{{ showNotebookIndex ? '笔记本' : '小记' }}</span>
      </button>
    </div>

    <el-dialog
      v-model="notebookDialogVisible"
      :title="editingNotebook ? '重命名笔记本' : '新建笔记本'"
      width="min(440px, calc(100vw - 32px))"
      class="notebook-dialog"
      :close-on-click-modal="!savingNotebook"
      :close-on-press-escape="!savingNotebook"
      :show-close="!savingNotebook"
      destroy-on-close
      @closed="notebookFormError = ''"
    >
      <el-form label-position="top" @submit.prevent="saveNotebook">
        <el-form-item label="名称" required :error="notebookFormError"
          ><el-input
            v-model="notebookForm.name"
            maxlength="60"
            show-word-limit
            placeholder="例如：高数复习"
            :disabled="savingNotebook"
            @keyup.enter="saveNotebook"
        /></el-form-item>
        <el-form-item label="颜色"
          ><div class="notebook-colors" role="group" aria-label="笔记本颜色">
            <button
              v-for="color in NOTEBOOK_COLORS"
              :key="color.value"
              type="button"
              :style="{ '--notebook-color': color.hex }"
              :aria-label="color.label"
              :aria-pressed="notebookForm.color === color.value"
              :disabled="savingNotebook"
              @click="notebookForm.color = color.value"
            >
              <span /><AppIcon v-if="notebookForm.color === color.value" name="check" :size="16" />
            </button></div
        ></el-form-item>
      </el-form>
      <template #footer
        ><el-button :disabled="savingNotebook" @click="notebookDialogVisible = false">取消</el-button
        ><el-button class="notes-soft-button" :loading="savingNotebook" @click="saveNotebook">保存</el-button></template
      >
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconMenuButton from '@/components/IconMenuButton.vue'
import {
  createNotebookApi,
  deleteNotebookApi,
  deleteNoteApi,
  getNotebooksApi,
  getNotesApi,
  updateNotebookApi,
  updateNoteApi,
  type NoteNotebook,
  type NotePageQuery,
  type PersonalNote
} from '@/api/modules/note'
import { useNoteWorkspace } from '@/composables/useNoteWorkspace'
import { confirmDanger } from '@/utils/confirm'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatDateTime, formatRelativeDay } from '@/utils/format'
import { readStorage, writeStorage } from '@/utils/storage'

type NotesTab = 'notes' | 'notebooks'
type SourceGroup = '' | 'READING' | 'QUESTION'
const PAGE_SIZE = 12
const NOTEBOOK_COLORS = [
  { value: 'orange', hex: '#B96830', label: '暖橙色' },
  { value: 'green', hex: '#448577', label: '松绿色' },
  { value: 'blue', hex: '#4C7DA8', label: '雾蓝色' },
  { value: 'purple', hex: '#836AA5', label: '紫藤色' },
  { value: 'pink', hex: '#9A717D', label: '藕粉色' },
  { value: 'gray', hex: '#737577', label: '石灰色' }
]
const notebookColor = (value?: string | null) =>
  NOTEBOOK_COLORS.find((color) => color.value === value) || NOTEBOOK_COLORS[0]!
const noteWorkspace = useNoteWorkspace()
const activeTab = ref<NotesTab>('notes')
const notes = ref<PersonalNote[]>([])
const total = ref(0)
const currentPage = ref(1)
const notebooks = ref<NoteNotebook[]>([])
const selectedNotebook = ref<NoteNotebook | null>(null)
const notebookLayout = ref<'list' | 'grid'>(readStorage('notes-notebook-layout') === 'list' ? 'list' : 'grid')
const notesLoading = ref(true)
const notebooksLoading = ref(true)
const notesError = ref('')
const notebooksError = ref('')
const actionError = ref('')
const keywordInput = ref('')
const searchKeyword = ref('')
const searchVisible = ref(false)
const searchInput = ref<{ focus: () => void } | null>(null)
const quickTabButton = ref<HTMLButtonElement | null>(null)
const notebookTabButton = ref<HTMLButtonElement | null>(null)
const sourceGroup = ref<SourceGroup>('')
const favoriteOnly = ref(false)
const openingNoteId = ref<number | null>(null)
const creatingNote = ref(false)
const noteBusyIds = reactive(new Set<number>())
const notebookBusyIds = reactive(new Set<number>())
const notebookDialogVisible = ref(false)
const editingNotebook = ref<NoteNotebook | null>(null)
const savingNotebook = ref(false)
const notebookFormError = ref('')
const notebookForm = reactive({ name: '', color: NOTEBOOK_COLORS[0]!.value })
let notesRequest = 0
let notebooksRequest = 0
let active = true
let searchTimer: ReturnType<typeof setTimeout> | undefined

const showNotebookIndex = computed(() => activeTab.value === 'notebooks' && !selectedNotebook.value)
const visibleLoading = computed(() => (showNotebookIndex.value ? notebooksLoading.value : notesLoading.value))
const hasNoteFilters = computed(() => Boolean(searchKeyword.value || sourceGroup.value || favoriteOnly.value))
const filteredNotebooks = computed(() => {
  const keyword = keywordInput.value.trim().toLocaleLowerCase()
  return notebooks.value
    .filter((book) => !keyword || book.name.toLocaleLowerCase().includes(keyword))
    .slice()
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt) || b.id - a.id)
})
const orderedNotes = computed(() =>
  notes.value
    .slice()
    .sort((a, b) => Number(b.favorite) - Number(a.favorite) || b.updatedAt.localeCompare(a.updatedAt) || b.id - a.id)
)
const noteTitle = (note: PersonalNote) => note.title?.trim() || '无标题小记'
const notebookColorStyle = (color?: string | null) => ({ '--notebook-color': notebookColor(color).hex })

async function loadNotes() {
  if (showNotebookIndex.value) return
  const request = ++notesRequest
  notesLoading.value = true
  notesError.value = ''
  const params: NotePageQuery = { pageNum: currentPage.value, pageSize: PAGE_SIZE }
  if (searchKeyword.value) params.keyword = searchKeyword.value
  if (selectedNotebook.value) params.notebookId = selectedNotebook.value.id
  if (sourceGroup.value) params.sourceGroup = sourceGroup.value
  if (favoriteOnly.value) params.favorite = true
  try {
    const response = await getNotesApi(params)
    if (!active || request !== notesRequest) return
    const result = response.data.data
    if (!result || !Array.isArray(result.records) || !Number.isFinite(result.total))
      throw new Error('未收到完整的小记列表，请重试')
    total.value = result.total
    const lastPage = Math.max(1, Math.ceil(result.total / PAGE_SIZE))
    if (currentPage.value > lastPage) {
      currentPage.value = lastPage
      await loadNotes()
      return
    }
    notes.value = result.records
  } catch (error) {
    if (active && request === notesRequest) notesError.value = getErrorMessage(error, '小记暂时无法加载')
  } finally {
    if (active && request === notesRequest) notesLoading.value = false
  }
}

async function loadNotebooks() {
  const request = ++notebooksRequest
  notebooksLoading.value = true
  notebooksError.value = ''
  try {
    const response = await getNotebooksApi()
    if (!active || request !== notebooksRequest) return
    if (!Array.isArray(response.data.data)) throw new Error('未收到完整的笔记本列表，请重试')
    notebooks.value = response.data.data
    if (selectedNotebook.value) {
      const refreshed = notebooks.value.find((book) => book.id === selectedNotebook.value?.id)
      if (refreshed) selectedNotebook.value = refreshed
      else leaveNotebook()
    }
  } catch (error) {
    if (active && request === notebooksRequest) notebooksError.value = getErrorMessage(error, '笔记本暂时无法加载')
  } finally {
    if (active && request === notebooksRequest) notebooksLoading.value = false
  }
}

async function refreshAll() {
  await Promise.allSettled([loadNotebooks(), loadNotes()])
}

function resetSearchState() {
  clearTimeout(searchTimer)
  keywordInput.value = ''
  searchKeyword.value = ''
  currentPage.value = 1
}
function changeTab(tab: NotesTab) {
  if (activeTab.value === tab && !selectedNotebook.value) return
  notesRequest++
  activeTab.value = tab
  selectedNotebook.value = null
  actionError.value = ''
  resetSearchState()
  sourceGroup.value = ''
  favoriteOnly.value = false
  if (tab === 'notes') void loadNotes()
  else void loadNotebooks()
}
function handleTabKey(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const target =
    event.key === 'Home'
      ? 'notes'
      : event.key === 'End'
        ? 'notebooks'
        : activeTab.value === 'notes'
          ? 'notebooks'
          : 'notes'
  changeTab(target)
  void nextTick(() => (target === 'notes' ? quickTabButton.value : notebookTabButton.value)?.focus())
}
function setNotebookLayout(layout: 'list' | 'grid') {
  notebookLayout.value = layout
  writeStorage('notes-notebook-layout', layout)
}
function enterNotebook(book: NoteNotebook) {
  resetSearchState()
  sourceGroup.value = ''
  favoriteOnly.value = false
  selectedNotebook.value = book
  void loadNotes()
}
function leaveNotebook() {
  notesRequest++
  selectedNotebook.value = null
  activeTab.value = 'notebooks'
  resetSearchState()
}
function changeSourceGroup(group: SourceGroup) {
  sourceGroup.value = group
  currentPage.value = 1
  void loadNotes()
}
function toggleFavoriteFilter() {
  favoriteOnly.value = !favoriteOnly.value
  currentPage.value = 1
  void loadNotes()
}
function resetNoteFilters() {
  resetSearchState()
  sourceGroup.value = ''
  favoriteOnly.value = false
  void loadNotes()
}
function scheduleSearch() {
  clearTimeout(searchTimer)
  if (showNotebookIndex.value) return
  searchTimer = setTimeout(applySearch, 300)
}
function applySearch() {
  clearTimeout(searchTimer)
  searchKeyword.value = keywordInput.value.trim()
  currentPage.value = 1
  void loadNotes()
}
function clearSearch() {
  resetSearchState()
  void loadNotes()
}
async function toggleSearch() {
  if (searchVisible.value) {
    searchInput.value?.focus()
    return
  }
  searchVisible.value = true
  await nextTick()
  searchInput.value?.focus()
}
function closeSearch() {
  searchVisible.value = false
  clearSearch()
}
function changePage(page: number) {
  currentPage.value = page
  void loadNotes()
}

async function openExistingNote(id: number) {
  if (openingNoteId.value !== null) return
  openingNoteId.value = id
  actionError.value = ''
  try {
    await noteWorkspace.openNote(id)
  } catch (error) {
    actionError.value = getErrorMessage(error, '小记没有打开，请重试')
  } finally {
    openingNoteId.value = null
  }
}
async function startNote() {
  if (creatingNote.value) return
  creatingNote.value = true
  actionError.value = ''
  try {
    await noteWorkspace.createNote({ sourceType: 'GENERAL' }, selectedNotebook.value?.id)
  } catch (error) {
    actionError.value = getErrorMessage(error, '暂时无法新建小记')
  } finally {
    creatingNote.value = false
  }
}
function handleCreate() {
  if (showNotebookIndex.value) openNotebookDialog()
  else void startNote()
}
async function handleNoteCommand(command: string, note: PersonalNote) {
  if (command === 'open') return openExistingNote(note.id)
  if (noteBusyIds.has(note.id)) return
  if (command === 'delete' && !(await confirmDanger(`删除“${noteTitle(note)}”？`, { title: '删除小记' }))) return
  if (!['favorite', 'delete'].includes(command)) return
  noteBusyIds.add(note.id)
  actionError.value = ''
  try {
    if (command === 'favorite') await updateNoteApi(note.id, { favorite: !note.favorite, version: note.version })
    else await deleteNoteApi(note.id)
    await refreshAll()
  } catch (error) {
    actionError.value = getErrorMessage(
      error,
      command === 'favorite' ? '置顶状态没有保存，请重试' : '小记没有删除，请重试'
    )
    if (command === 'favorite') await loadNotes()
  } finally {
    noteBusyIds.delete(note.id)
  }
}
function openNotebookDialog(book?: NoteNotebook) {
  editingNotebook.value = book || null
  notebookForm.name = book?.name || ''
  notebookForm.color = notebookColor(book?.color).value
  notebookFormError.value = ''
  notebookDialogVisible.value = true
}
async function saveNotebook() {
  if (savingNotebook.value) return
  const name = notebookForm.name.trim()
  if (!name) {
    notebookFormError.value = '请输入笔记本名称'
    return
  }
  if (name.length > 60) {
    notebookFormError.value = '笔记本名称最多 60 个字符'
    return
  }
  savingNotebook.value = true
  notebookFormError.value = ''
  try {
    const payload = { name, color: notebookForm.color }
    if (editingNotebook.value) await updateNotebookApi(editingNotebook.value.id, payload)
    else await createNotebookApi(payload)
    notebookDialogVisible.value = false
    await refreshAll()
  } catch (error) {
    notebookFormError.value = getErrorMessage(error, '笔记本没有保存，请重试')
  } finally {
    savingNotebook.value = false
  }
}
async function handleNotebookCommand(command: string, book: NoteNotebook) {
  if (notebookBusyIds.has(book.id)) return
  if (command === 'rename') {
    openNotebookDialog(book)
    return
  }
  if (
    command !== 'delete' ||
    !(await confirmDanger(`删除“${book.name}”后，其中的小记仍会保留在全部小记中。`, { title: '删除笔记本' }))
  )
    return
  notebookBusyIds.add(book.id)
  actionError.value = ''
  try {
    await deleteNotebookApi(book.id)
    if (selectedNotebook.value?.id === book.id) leaveNotebook()
    await refreshAll()
  } catch (error) {
    actionError.value = getErrorMessage(error, '笔记本没有删除，请重试')
  } finally {
    notebookBusyIds.delete(book.id)
  }
}

watch(noteWorkspace.refreshVersion, refreshAll)
onMounted(refreshAll)
onBeforeUnmount(() => {
  active = false
  notesRequest++
  notebooksRequest++
  clearTimeout(searchTimer)
})
</script>

<style scoped>
.notes-view {
  max-width: 1120px;
  padding-bottom: 112px;
}
.notes-header,
.notes-header__actions,
.notes-tabs,
.notebook-layout,
.notes-search,
.notes-filters,
.notes-dock,
.notebook-heading {
  display: flex;
  align-items: center;
}
.notes-header {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
.notes-tabs {
  gap: 24px;
}
.notes-tabs button {
  min-height: 48px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--muted);
  font: inherit;
  font-size: 25px;
  font-weight: 500;
  cursor: pointer;
}
.notes-tabs button[aria-selected='true'] {
  color: var(--text);
  font-weight: 700;
}
.notes-header__actions {
  gap: 8px;
}
.notes-icon-button,
.notebook-layout button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 44px;
  width: 44px;
  min-height: 44px;
  padding: 0;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.notes-icon-button:hover:not(:disabled),
.notebook-layout button:hover {
  background: var(--panel);
  color: var(--text);
}
.notes-icon-button:disabled {
  opacity: 0.45;
  cursor: default;
}
.notebook-layout {
  padding: 2px;
  border-radius: 13px;
  background: var(--bg-secondary);
}
.notebook-layout button[aria-pressed='true'] {
  background: var(--panel);
  color: var(--text);
}
.notes-view button:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 3px;
}
.notes-search {
  gap: 8px;
  margin-bottom: 18px;
}
.notes-search :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 14px;
  box-shadow: none;
}
.notes-filters {
  flex-wrap: wrap;
  gap: 4px;
  margin: -4px 0 18px;
}
.notes-filters button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 44px;
  padding: 0 12px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.notes-filters button[aria-pressed='true'] {
  background: var(--panel);
  color: var(--text);
}
.notes-filters button:last-of-type[aria-pressed='true'] {
  background: var(--brand-light);
  color: var(--brand);
}
.notes-sort-label {
  margin-left: auto;
  padding-left: 10px;
  color: var(--muted);
  font-size: 12px;
}
.notes-action-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 16px;
  padding: 4px 8px 4px 16px;
  border-radius: 12px;
  background: var(--red-soft);
  color: var(--red);
  font-size: 14px;
  overflow-wrap: anywhere;
}
.notes-action-error .notes-icon-button {
  color: inherit;
}
.notebook-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.note-list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  margin: 0;
  padding: 0;
  overflow: hidden;
  border-radius: 14px;
  background: var(--panel);
  list-style: none;
}
.note-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px;
  align-items: center;
  gap: 8px;
  min-width: 0;
  height: 56px;
  padding: 0 8px 0 16px;
}
.note-row + .note-row {
  border-top: 1px solid var(--line);
}
.note-row:hover {
  background: var(--panel-hover);
}
.note-row__open {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  height: 100%;
  min-height: 44px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.note-row__open:disabled {
  cursor: wait;
}
.note-row__title {
  min-width: 0;
  overflow: hidden;
  font-size: 16px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.note-row__pin {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  color: var(--brand);
}
.note-row__skeleton {
  display: block;
  width: min(64%, 260px);
  height: 14px;
  border-radius: 4px;
}
.notes-view :deep(.icon-menu-button) {
  width: 44px;
  height: 44px;
}
.notes-empty {
  min-height: 320px;
  border-radius: 18px;
  background: var(--panel);
}
.notes-pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
.notebook-grid--list {
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  overflow: hidden;
  border-radius: 18px;
  background: var(--panel);
}
.notebook-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 20px 14px 20px 20px;
  border-radius: 18px;
  background: var(--panel);
}
.notebook-grid--list .notebook-card {
  border-radius: 0;
}
.notebook-grid--list .notebook-card + .notebook-card {
  border-top: 1px solid var(--line);
}
.notebook-card__open {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
  padding: 0;
  border: 0;
  background: none;
  text-align: left;
  cursor: pointer;
}
.notebook-cover,
.notebook-heading__cover {
  display: grid;
  place-items: center;
  flex: 0 0 56px;
  width: 56px;
  height: 72px;
  border-left: 4px solid color-mix(in srgb, var(--notebook-color) 45%, var(--panel));
  border-radius: 4px 11px 11px 4px;
  background: color-mix(in srgb, var(--notebook-color) 12%, var(--panel));
  color: var(--notebook-color);
}
.notebook-card__text {
  display: grid;
  gap: 5px;
  min-width: 0;
}
.notebook-card__text strong {
  overflow: hidden;
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notebook-card__text > span,
.notebook-card__text time {
  color: var(--muted);
  font-size: 12px;
}
.notebook-heading {
  gap: 14px;
  margin: 0 0 20px;
  padding: 18px 12px;
  border-radius: 16px;
  background: var(--panel);
}
.notebook-heading > div:not(.el-dropdown) {
  flex: 1;
  min-width: 0;
}
.notebook-heading h2 {
  margin: 0;
  font-size: 20px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.notebook-heading p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}
.notebook-heading__cover {
  flex-basis: 44px;
  width: 44px;
  height: 56px;
}
.notes-dock {
  position: fixed;
  z-index: 35;
  bottom: calc(24px + env(safe-area-inset-bottom));
  left: 50%;
  gap: 8px;
  padding: 7px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--panel);
  box-shadow: 0 8px 28px rgb(24 34 29 / 12%);
  transform: translateX(-50%);
}
.notes-dock button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-width: 94px;
  min-height: 48px;
  padding: 0 12px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.notes-dock .notes-dock__create {
  color: var(--brand);
}
.notes-dock button:hover {
  background: var(--bg-secondary);
}
.notes-dock button:disabled {
  opacity: 0.5;
  cursor: wait;
}
.notes-dock__divider {
  width: 1px;
  height: 26px;
  background: var(--line);
}
.notes-soft-button {
  --el-button-text-color: var(--brand);
  --el-button-bg-color: var(--brand-light);
  --el-button-border-color: transparent;
  --el-button-hover-text-color: var(--brand);
  --el-button-hover-bg-color: var(--brand-soft);
  --el-button-hover-border-color: transparent;
  min-height: 44px;
  box-shadow: none;
}
.notebook-colors {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.notebook-colors button {
  position: relative;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 50%;
  background: transparent;
  color: var(--text);
  cursor: pointer;
}
.notebook-colors button[aria-pressed='true'] {
  border-color: var(--notebook-color);
}
.notebook-colors button > span {
  position: absolute;
  inset: 5px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--notebook-color) 23%, var(--panel));
}
.notebook-colors button :deep(.app-icon) {
  z-index: 1;
}
.notes-skeleton-title,
.notes-skeleton-line {
  display: block;
  border-radius: 5px;
}
.notes-skeleton-title {
  width: 64%;
  height: 20px;
}
.notes-skeleton-line {
  width: 100%;
  height: 13px;
}
.notebook-card--skeleton {
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 16px;
}
.notebook-card--skeleton > div {
  display: grid;
  gap: 12px;
}
.notebook-skeleton-cover {
  width: 56px;
  height: 72px;
  border-radius: 10px;
}
@media (min-width: 1180px) {
  .notebook-grid:not(.notebook-grid--list) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .notes-header {
    gap: 8px;
    margin-bottom: 20px;
  }
  .notes-tabs {
    gap: 22px;
  }
  .notes-tabs button {
    font-size: 24px;
  }
  .notes-header__actions {
    gap: 2px;
  }
  .notebook-layout {
    gap: 0;
    padding: 0;
    background: transparent;
  }
  .notebook-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }
  .notebook-grid--list {
    gap: 0;
  }
  .notes-filters {
    gap: 1px;
    margin-bottom: 14px;
  }
  .notes-filters button {
    padding-inline: 9px;
    font-size: 13px;
  }
  .notes-sort-label {
    display: none;
  }
  .notebook-card {
    padding: 17px 12px 17px 18px;
  }
  .notebook-heading {
    gap: 10px;
    padding: 14px 6px;
  }
  .notebook-heading h2 {
    font-size: 18px;
  }
  .notes-dock {
    bottom: calc(22px + env(safe-area-inset-bottom));
  }
  .notes-dock button {
    min-width: 90px;
  }
}
</style>
