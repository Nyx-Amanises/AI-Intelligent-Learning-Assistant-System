<template>
  <section
    class="book-reader"
    :class="{ 'book-reader--text': textMode }"
    :aria-label="`${title}，完整教材`"
    :aria-busy="loading || rendering"
    :data-mode="textMode ? 'text' : 'original'"
  >
    <div class="book-reader__tools">
      <select v-if="chapters.length" v-model="chapterIndex" aria-label="教材目录" @change="openChapter">
        <option :value="-1" disabled>目录</option>
        <option v-for="(chapter, index) in chapters" :key="index" :value="index">{{ chapter.title }}</option>
      </select>
      <span v-else class="book-reader__title">{{ title }}</span>
      <div class="book-reader__modes" role="group" aria-label="阅读方式">
        <button type="button" :aria-pressed="!textMode" aria-label="原版图文阅读" @click="setMode(false)">原版</button>
        <button type="button" :aria-pressed="textMode" aria-label="大字阅读" @click="setMode(true)">大字</button>
      </div>
      <div v-if="textMode" class="book-reader__font-tools" role="group" aria-label="字号调整">
        <button
          type="button"
          :aria-label="`减小字号，当前 ${fontSize} 像素`"
          :disabled="fontSize <= MIN_FONT_SIZE"
          class="book-reader__font-size"
          @click="adjustFontSize(-2)"
        >
          A<span>−</span>
        </button>
        <button
          type="button"
          :aria-label="`增大字号，当前 ${fontSize} 像素`"
          :disabled="fontSize >= MAX_FONT_SIZE"
          class="book-reader__font-size"
          @click="adjustFontSize(2)"
        >
          A<span>+</span>
        </button>
      </div>
      <button
        v-else
        type="button"
        :aria-label="zoom === 1 ? '放大书页' : '适合屏幕宽度'"
        @click="zoom = zoom === 1 ? 1.8 : 1"
      >
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          aria-hidden="true"
        >
          <circle cx="10.5" cy="10.5" r="6.5" />
          <path d="m16 16 5 5M7 10.5h7" />
          <path v-if="zoom === 1" d="M10.5 7v7" />
        </svg>
      </button>
      <button
        v-if="resourceId"
        type="button"
        aria-label="记笔记"
        title="记笔记"
        :disabled="loading || rendering"
        @mousedown.prevent
        @click="recordNote"
      >
        <MobileIcon name="pencil" :size="20" />
      </button>
    </div>
    <div ref="viewport" class="book-reader__viewport">
      <div v-if="error" class="book-reader__state" role="alert">
        <p>书页加载失败</p>
        <button type="button" @click="loadBook">重试</button>
      </div>
      <p v-else-if="loading || rendering" class="book-reader__state" role="status">正在加载书页…</p>
      <article
        v-show="!error && !loading && !rendering && textMode"
        class="book-reader__text"
        :aria-label="`第 ${pageNumber} 页文字`"
        :style="{ fontSize: `${fontSize}px` }"
      >
        <template v-for="(block, index) in readingBlocks" :key="index">
          <h3 v-if="block.type === 'heading'"><BookTextRuns :runs="block.runs" /></h3>
          <pre v-else-if="block.type === 'code'"><code><BookTextRuns :runs="block.runs" /></code></pre>
          <p v-else :class="{ 'book-reader__caption': block.type === 'caption' }">
            <BookTextRuns :runs="block.runs" />
          </p>
        </template>
        <p v-if="!rendering && !readingBlocks.length">本页以图片为主，请切换原版查看。</p>
      </article>
      <canvas
        v-show="!error && !loading && !rendering && !textMode"
        ref="canvas"
        class="book-reader__page"
        role="img"
        :aria-label="`${title}，第 ${pageNumber} 页。可切换本页文字阅读。`"
      />
    </div>
    <a
      v-if="sourceCredit"
      class="book-reader__source-credit"
      :href="sourceCredit.url"
      target="_blank"
      rel="noopener noreferrer"
      >{{ sourceCredit.label }}</a
    >
    <nav class="book-reader__pagination" aria-label="教材翻页">
      <button
        type="button"
        aria-label="上一页教材"
        :disabled="pageNumber <= 1 || loading || rendering"
        @click="goToPage(pageNumber - 1)"
      >
        ‹
      </button>
      <label
        ><input
          v-model="pageInput"
          inputmode="numeric"
          aria-label="教材页码"
          :disabled="loading"
          @change="jumpToPage"
          @keydown.enter="jumpToPage"
        /><span>/ {{ totalPages || '—' }}</span></label
      >
      <button
        type="button"
        aria-label="下一页教材"
        :disabled="pageNumber >= totalPages || loading || rendering"
        @click="goToPage(pageNumber + 1)"
      >
        ›
      </button>
    </nav>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from 'pdfjs-dist'
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { measurePageInk, reflowPdfText, type PageBounds, type ReadingBlock } from '@/utils/pdfReadingLayout'
import { createPdfReadingSource } from '@/utils/pdfReadingSource'
import BookTextRuns from './BookTextRuns.vue'
import MobileIcon from '@/components/MobileIcon.vue'
import { useNoteWorkspace } from '@/composables/useNoteWorkspace'

const props = defineProps<{
  url: string
  title: string
  preferOriginal?: boolean
  sourceCredit?: { label: string; url: string }
  resourceId?: number
  initialPage?: number
}>()
const { createNote } = useNoteWorkspace()
const emit = defineEmits<{ progress: [percentage: number]; pageChange: [page: number] }>()
const viewport = ref<HTMLElement>()
const recordNote = () => {
  if (!props.resourceId) return
  const selection = window.getSelection()
  const selected =
    textMode.value &&
    selection?.anchorNode &&
    selection.focusNode &&
    viewport.value?.contains(selection.anchorNode) &&
    viewport.value.contains(selection.focusNode)
      ? selection.toString().trim().slice(0, 2000)
      : ''
  createNote({
    sourceType: 'RESOURCE',
    sourceId: props.resourceId,
    pageNumber: pageNumber.value,
    sourceTitle: props.title,
    sourceExcerpt: selected
  })
}
const canvas = ref<HTMLCanvasElement>()
const pageNumber = ref(1)
const pageInput = ref('1')
const totalPages = ref(0)
const loading = ref(true)
const rendering = ref(false)
const error = ref(false)
const zoom = ref(1)
const width = ref(360)
const textMode = ref(false)
const fontSize = ref(18)
const MIN_FONT_SIZE = 14
const MAX_FONT_SIZE = 26
const adjustFontSize = (amount: number) => {
  fontSize.value = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, fontSize.value + amount))
}
const readingPercent = computed(() =>
  totalPages.value > 0 ? Math.min(100, Math.max(0, Math.round((pageNumber.value / totalPages.value) * 1000) / 10)) : 0
)
watch(readingPercent, (value) => emit('progress', value), { immediate: true })
const readingBlocks = ref<ReadingBlock[]>([])
const pageLayouts = new Map<number, { blocks: ReadingBlock[]; textLoaded?: boolean; bounds?: PageBounds }>()
let explicitMode = false
const chapters = ref<{ title: string; dest: string | unknown[] }[]>([])
const chapterIndex = ref(-1)
let document: PDFDocumentProxy | undefined
let readingSource: ReturnType<typeof createPdfReadingSource> | undefined
let loadingTask: PDFDocumentLoadingTask | undefined
let renderTask: RenderTask | undefined
let observer: ResizeObserver | undefined
let loadVersion = 0
let renderVersion = 0
let chapterVersion = 0
let disposed = false

const setMode = (text: boolean) => {
  explicitMode = true
  textMode.value = text
  viewport.value?.scrollTo(0, 0)
  try {
    localStorage.setItem('book-reader-mode', text ? 'text' : 'original')
    localStorage.setItem(`book-reader-mode:${props.url}`, text ? 'text' : 'original')
  } catch {
    /* Optional preference. */
  }
}

const restoreReadingMode = () => {
  let preference: string | null = null
  try {
    preference =
      localStorage.getItem(`book-reader-mode:${props.url}`) ??
      (props.preferOriginal ? null : localStorage.getItem('book-reader-mode'))
  } catch {
    /* Reading works without persistent preferences. */
  }
  explicitMode = preference === 'text' || preference === 'original'
  textMode.value = explicitMode ? preference === 'text' : !props.preferOriginal && window.innerWidth <= 760
}

const goToPage = (value: number) => {
  ++chapterVersion
  if (Number.isInteger(value)) pageNumber.value = Math.max(1, Math.min(totalPages.value || 1, value))
  chapterIndex.value = -1
}
const jumpToPage = () => {
  const value = Number(pageInput.value)
  goToPage(value)
  pageInput.value = String(pageNumber.value)
}
const openChapter = async () => {
  const version = ++chapterVersion
  const book = document
  const chapter = chapters.value[chapterIndex.value]
  if (!book || !chapter) return
  try {
    const destination = typeof chapter.dest === 'string' ? await book.getDestination(chapter.dest) : chapter.dest
    if (!destination || version !== chapterVersion || book !== document || disposed) return
    const reference = destination[0]
    const index =
      typeof reference === 'number' ? reference : await book.getPageIndex(reference as { num: number; gen: number })
    if (version === chapterVersion && book === document && !disposed)
      pageNumber.value = Math.max(1, Math.min(book.numPages, index + 1))
  } catch {
    if (version === chapterVersion && !disposed) chapterIndex.value = -1
  }
}
const renderPage = async () => {
  const book = document
  const target = canvas.value
  if (!book || !target || loading.value || disposed) return
  const version = ++renderVersion
  const previous = renderTask
  previous?.cancel()
  rendering.value = true
  try {
    if (previous) await previous.promise.catch(() => undefined)
    if (version !== renderVersion || disposed) return
    const pageIndex = pageNumber.value
    const page = await book.getPage(pageIndex)
    if (version !== renderVersion || disposed) return
    const base = page.getViewport({ scale: 1 })
    let layout = pageLayouts.get(pageIndex)
    if (!layout) {
      layout = { blocks: [] }
      pageLayouts.set(pageIndex, layout)
      if (pageLayouts.size > 8) pageLayouts.delete(pageLayouts.keys().next().value!)
    }
    // Optional colour data must never delay the original PDF rendering.
    if (textMode.value && !layout.textLoaded) {
      const richText = await readingSource?.page(pageIndex, base.width, base.height)
      if (version !== renderVersion || disposed) return
      const text = richText || (await page.getTextContent())
      if (version !== renderVersion || disposed) return
      layout.blocks = reflowPdfText(
        text.items.filter((item) => 'str' in item),
        text.styles,
        base.height
      )
      layout.textLoaded = true
    }
    readingBlocks.value = layout.blocks
    if (textMode.value) {
      error.value = false
      try {
        localStorage.setItem(`book-page:${props.url}`, String(pageIndex))
      } catch {
        /* Optional reading position. */
      }
      return
    }
    if (!layout.bounds) {
      if (pageIndex === 1) layout.bounds = { x: 0, y: 0, width: base.width, height: base.height }
      else {
        // Measure a small rendering once per page, including images and formulas.
        const probe = window.document.createElement('canvas')
        const probeView = page.getViewport({ scale: Math.min(1, 600 / Math.max(base.width, base.height)) })
        probe.width = Math.ceil(probeView.width)
        probe.height = Math.ceil(probeView.height)
        renderTask = page.render({ canvas: probe, viewport: probeView })
        await renderTask.promise
        if (version !== renderVersion || disposed) return
        const context = probe.getContext('2d', { willReadFrequently: true })
        layout.bounds = context
          ? measurePageInk(context.getImageData(0, 0, probe.width, probe.height), base.width, base.height)
          : { x: 0, y: 0, width: base.width, height: base.height }
        probe.width = 0
        probe.height = 0
      }
    }
    const bounds = layout.bounds
    // ResizeObserver already reports the content width, excluding padding.
    const scale = (Math.max(200, width.value) / bounds.width) * zoom.value
    const view = page.getViewport({ scale })
    const visibleWidth = bounds.width * scale,
      visibleHeight = bounds.height * scale
    const ratio = Math.min(
      Math.max(window.devicePixelRatio || 1, 2),
      3,
      Math.sqrt(12000000 / (visibleWidth * visibleHeight))
    )
    target.width = Math.ceil(visibleWidth * ratio)
    target.height = Math.ceil(visibleHeight * ratio)
    target.style.width = `${visibleWidth}px`
    target.style.height = `${visibleHeight}px`
    renderTask = page.render({
      canvas: target,
      viewport: view,
      transform: [ratio, 0, 0, ratio, -bounds.x * scale * ratio, -bounds.y * scale * ratio]
    })
    await renderTask.promise
    if (version !== renderVersion || disposed) return
    error.value = false
    try {
      localStorage.setItem(`book-page:${props.url}`, String(pageNumber.value))
    } catch {
      /* Reading also works with storage disabled. */
    }
  } catch (exception) {
    if (
      version === renderVersion &&
      !disposed &&
      !(exception instanceof Error && exception.name === 'RenderingCancelledException')
    )
      error.value = true
  } finally {
    if (version === renderVersion && !disposed) rendering.value = false
  }
}
const loadBook = async () => {
  const version = ++loadVersion
  ++chapterVersion
  ++renderVersion
  renderTask?.cancel()
  readingSource?.dispose()
  readingSource = undefined
  document = undefined
  pageLayouts.clear()
  totalPages.value = 0
  loading.value = true
  error.value = false
  chapters.value = []
  chapterIndex.value = -1
  try {
    await loadingTask?.destroy()
    const pdf = await import('pdfjs-dist')
    if (version !== loadVersion || disposed) return
    if (!/^\/open-books\/[a-z0-9-]+\/book\.pdf$/.test(props.url)) throw new Error('Invalid document path')
    pdf.GlobalWorkerOptions.workerSrc = pdfWorker
    loadingTask = pdf.getDocument({ url: props.url, useSystemFonts: true, isEvalSupported: false })
    const book = await loadingTask.promise
    if (version !== loadVersion || disposed) return
    document = book
    readingSource = createPdfReadingSource(props.url, book.numPages)
    totalPages.value = book.numPages
    let remembered = 1
    try {
      remembered = Number(localStorage.getItem(`book-page:${props.url}`)) || 1
    } catch {
      /* Optional reading position. */
    }
    const outline = await book.getOutline()
    if (version !== loadVersion || disposed) return
    const initialPage = props.initialPage
    const requestedPage = initialPage && Number.isSafeInteger(initialPage) && initialPage > 0 ? initialPage : remembered
    pageNumber.value = Math.max(1, Math.min(book.numPages, Math.floor(requestedPage)))
    pageInput.value = String(pageNumber.value)
    chapters.value = (outline || []).flatMap((item) => (item.dest ? [{ title: item.title, dest: item.dest }] : []))
    loading.value = false
    await nextTick()
    await renderPage()
  } catch {
    if (version === loadVersion && !disposed) {
      loading.value = false
      error.value = true
    }
  }
}
watch(pageNumber, (value) => {
  emit('pageChange', value)
  pageInput.value = String(value)
  readingBlocks.value = []
  viewport.value?.scrollTo(0, 0)
  void renderPage()
})
watch([zoom, width, textMode], () => {
  void renderPage()
})
watch(
  () => props.initialPage,
  (value) => {
    if (value && value !== pageNumber.value && Number.isSafeInteger(value) && value > 0 && document && !loading.value)
      goToPage(value)
  }
)
watch(
  () => props.url,
  () => {
    restoreReadingMode()
    void loadBook()
  }
)
onMounted(() => {
  restoreReadingMode()
  observer = new ResizeObserver((entries) => {
    width.value = Math.round(entries[0]?.contentRect.width || 360)
    if (!explicitMode) textMode.value = !props.preferOriginal && window.innerWidth <= 760
  })
  if (viewport.value) observer.observe(viewport.value)
  void loadBook()
})
onBeforeUnmount(() => {
  disposed = true
  ++chapterVersion
  ++loadVersion
  ++renderVersion
  observer?.disconnect()
  renderTask?.cancel()
  readingSource?.dispose()
  void loadingTask?.destroy()
})
</script>

<style scoped>
.book-reader {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  color: var(--text);
}
.book-reader__tools,
.book-reader__pagination {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
  padding: 4px 12px;
  background: var(--surface, #fff);
}
.book-reader__source-credit {
  flex: none;
  padding: 5px 12px;
  text-align: center;
  font-size: 10px;
  color: var(--muted);
  background: var(--surface, #fff);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.book-reader button,
.book-reader select,
.book-reader input {
  min-height: 44px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  border-radius: 8px;
}
.book-reader button {
  min-width: 44px;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
}
.book-reader button:disabled {
  opacity: 0.35;
  cursor: default;
}
.book-reader button[aria-pressed='true'] {
  background: #eaf2ee;
}
.book-reader :is(button, select, input):focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
.book-reader__title,
.book-reader select {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}
.book-reader select {
  max-width: calc(100% - 104px);
}
.book-reader__viewport {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--bg, #f5f5f5);
  padding: 8px;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}
.book-reader__page {
  display: block;
  margin: 0 auto;
  background: #fff;
  box-shadow: 0 2px 9px #0000000d;
}
.book-reader__text {
  max-width: 760px;
  margin: 0 auto;
  font-size: 18px;
  line-height: 1.9;
  padding: 12px 16px 32px;
  overflow-wrap: anywhere;
  color: #212629;
}
.book-reader--text .book-reader__viewport {
  /* PDF text colours are authored against white paper, including in dark mode. */
  background: #fff;
}
.book-reader__text h3 {
  margin: 1.2em 0 0.6em;
  font-size: 1.13em;
  line-height: 1.5;
  color: inherit;
}
.book-reader__text p {
  margin: 0 0 0.85em;
}
.book-reader__text pre {
  margin: 16px 0;
  padding: 14px;
  overflow-x: auto;
  background: #f6f7f8;
  border-radius: 10px;
  line-height: 1.65;
  font-size: 0.84em;
  white-space: pre;
}
.book-reader__caption {
  color: #697078;
  font-size: 12px;
}
.book-reader__modes {
  display: flex;
  flex: none;
  padding: 2px;
  gap: 2px;
  border-radius: 10px;
  background: var(--bg, #f5f5f5);
}
.book-reader__modes button {
  padding: 0 8px;
  font-size: 13px;
  min-height: 44px;
}
.book-reader__modes button[aria-pressed='true'] {
  background: var(--panel, #fff);
  box-shadow: 0 1px 4px #00000008;
}
.book-reader__font-size {
  font-size: 19px;
}
.book-reader__font-tools {
  display: flex;
  flex: none;
  gap: 0;
}
.book-reader__font-size span {
  margin-left: 1px;
  font-size: 12px;
}
.book-reader__state {
  text-align: center;
  padding: 60px 12px;
  color: var(--muted);
  font-size: 14px;
}
.book-reader__pagination {
  justify-content: center;
  border-top: 1px solid var(--line);
}
.book-reader__pagination button {
  font-size: 28px;
}
.book-reader__pagination label {
  display: flex;
  align-items: center;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}
.book-reader__pagination input {
  width: 56px;
  text-align: center;
  font-size: 16px;
}
.book-reader__pagination span {
  color: var(--muted);
  padding-right: 10px;
}
</style>
