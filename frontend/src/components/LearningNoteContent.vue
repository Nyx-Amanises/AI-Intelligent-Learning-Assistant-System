<template>
  <div class="learning-note">
    <template v-for="(block, index) in blocks" :key="index">
      <component
        :is="'h' + Math.min(block.level + 1, 6)"
        v-if="block.type === 'heading'"
        class="learning-note__heading"
      >
        <component v-for="(part, partIndex) in block.parts" :is="part.tag" :key="partIndex">{{ part.text }}</component>
      </component>
      <component
        :is="block.ordered ? 'ol' : 'ul'"
        v-else-if="block.type === 'list'"
        class="learning-note__list"
        :start="block.ordered ? block.items[0]?.value : undefined"
      >
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex" :value="item.value">
          <component v-for="(part, partIndex) in item.parts" :is="part.tag" :key="partIndex">{{ part.text }}</component>
        </li>
      </component>
      <div v-else-if="block.type === 'code'" class="learning-note__code-block">
        <div class="learning-note__code-bar">
          <span>{{ block.lang || '代码' }}</span>
          <button
            type="button"
            class="learning-note__copy"
            :class="{ 'is-copied': copiedIndex === index }"
            :aria-label="copiedIndex === index ? '代码已复制' : '复制代码'"
            @click="copyCode(index, block.text)"
          >
            {{ copiedIndex === index ? '已复制' : '复制' }}
          </button>
        </div>
        <pre class="learning-note__code"><code>{{ block.text }}</code></pre>
      </div>
      <hr v-else-if="block.type === 'rule'" class="learning-note__rule" />
      <blockquote v-else-if="block.type === 'quote'" class="learning-note__quote">
        <p>
          <component v-for="(part, partIndex) in block.parts" :is="part.tag" :key="partIndex">{{
            part.text
          }}</component>
        </p>
      </blockquote>
      <div v-else-if="block.type === 'table'" class="learning-note__table-wrap">
        <table class="learning-note__table">
          <thead>
            <tr>
              <th
                v-for="(cell, cellIndex) in block.header"
                :key="cellIndex"
                :style="alignStyle(block.aligns[cellIndex])"
              >
                <component v-for="(part, partIndex) in cell" :is="part.tag" :key="partIndex">{{ part.text }}</component>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
              <td v-for="(cell, cellIndex) in row" :key="cellIndex" :style="alignStyle(block.aligns[cellIndex])">
                <component v-for="(part, partIndex) in cell" :is="part.tag" :key="partIndex">{{ part.text }}</component>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="learning-note__paragraph">
        <component v-for="(part, partIndex) in block.parts" :is="part.tag" :key="partIndex">{{ part.text }}</component>
      </p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{ text?: string | null }>()

interface InlinePart {
  tag: 'span' | 'strong' | 'code'
  text: string
}

type TableAlign = 'left' | 'center' | 'right' | undefined

type NoteBlock =
  | { type: 'heading'; level: number; parts: InlinePart[] }
  | { type: 'paragraph'; parts: InlinePart[] }
  | { type: 'list'; ordered: boolean; items: { value?: number; parts: InlinePart[] }[] }
  | { type: 'code'; text: string; lang: string }
  | { type: 'rule' }
  | { type: 'quote'; parts: InlinePart[] }
  | { type: 'table'; header: InlinePart[][]; aligns: TableAlign[]; rows: InlinePart[][][] }

// A small text formatter, deliberately excluding HTML, links and embedded media.
const parseInline = (text: string): InlinePart[] => {
  const parts: InlinePart[] = []
  let plain = ''
  let cursor = 0
  const flushPlain = () => {
    if (plain) parts.push({ tag: 'span', text: plain })
    plain = ''
  }

  while (cursor < text.length) {
    const isCode = text.charCodeAt(cursor) === 96
    const isStrong = text.startsWith('**', cursor)
    if (!isCode && !isStrong) {
      plain += text[cursor++]
      continue
    }

    let markerLength = isStrong ? 2 : 1
    if (isCode) {
      while (text.charCodeAt(cursor + markerLength) === 96) markerLength++
    }
    const marker = text.slice(cursor, cursor + markerLength)
    let end = text.indexOf(marker, cursor + markerLength)
    if (isCode) {
      while (end >= 0 && (text.charCodeAt(end - 1) === 96 || text.charCodeAt(end + markerLength) === 96)) {
        end = text.indexOf(marker, end + markerLength)
      }
    }
    const nextLine = text.indexOf('\n', cursor + markerLength)
    const isEscaped = cursor > 0 && text.charCodeAt(cursor - 1) === 92
    if (isEscaped || end <= cursor + markerLength || (nextLine >= 0 && end > nextLine)) {
      plain += marker
      cursor += markerLength
      continue
    }

    flushPlain()
    parts.push({ tag: isCode ? 'code' : 'strong', text: text.slice(cursor + markerLength, end) })
    cursor = end + markerLength
  }

  flushPlain()
  return parts
}

const syntaxLine = (line: string) => (line.endsWith('\r') ? line.slice(0, -1) : line)
const listPattern = /^ {0,3}([-+*]|\d{1,9}[.)])[ \t]+(.+)$/
const rulePattern = /^ {0,3}([-*_])(?: *\1){2,} *$/
const quotePattern = /^ {0,3}> ?(.*)$/
const tableDividerPattern = /^ {0,3}\|?\s*:?-+:?\s*(?:\|\s*:?-+:?\s*)*\|?\s*$/

const splitTableRow = (line: string) => {
  let content = line.trim()
  if (content.startsWith('|')) content = content.slice(1)
  if (content.endsWith('|') && !content.endsWith('\\|')) content = content.slice(0, -1)
  const cells: string[] = []
  let current = ''
  for (let index = 0; index < content.length; index++) {
    const char = content[index]
    if (char === '\\' && content[index + 1] === '|') {
      current += '|'
      index++
    } else if (char === '|') {
      cells.push(current.trim())
      current = ''
    } else {
      current += char
    }
  }
  cells.push(current.trim())
  return cells
}

const isTableRow = (line: string) => line.includes('|') && Boolean(line.trim())

const resolveAlign = (divider: string): TableAlign => {
  const cell = divider.trim()
  const left = cell.startsWith(':')
  const right = cell.endsWith(':')
  if (left && right) return 'center'
  if (right) return 'right'
  if (left) return 'left'
  return undefined
}

const parseBlocks = (text: string): NoteBlock[] => {
  const result: NoteBlock[] = []
  const lines = text.split('\n')
  let paragraph: string[] = []
  let cursor = 0
  const flushParagraph = () => {
    const content = paragraph.join('\n')
    if (content.trim()) result.push({ type: 'paragraph', parts: parseInline(content) })
    paragraph = []
  }

  while (cursor < lines.length) {
    const line = syntaxLine(lines[cursor])
    const fence = line.match(/^ {0,3}(\x60{3,}|~{3,})([^\r\n]*)$/)
    if (fence && !fence[2].includes(String.fromCharCode(96))) {
      const closingPattern = new RegExp('^ {0,3}' + fence[1][0] + '{' + fence[1].length + ',}[ \\t]*$')
      let closing = cursor + 1
      while (closing < lines.length && !closingPattern.test(syntaxLine(lines[closing]))) closing++
      flushParagraph()
      const body = lines.slice(cursor + 1, closing)
      result.push({
        type: 'code',
        text: body.join('\n') + (body.length ? '\n' : ''),
        lang: fence[2].trim().split(/\s+/)[0] || ''
      })
      // An unclosed fence (typical while a reply is still streaming) is rendered as code up to the end.
      cursor = closing === lines.length ? lines.length : closing + 1
      continue
    }

    if (rulePattern.test(line)) {
      flushParagraph()
      result.push({ type: 'rule' })
      cursor++
      continue
    }

    const heading = line.match(/^ {0,3}(#{1,6})[ \t]+(.+)$/)
    if (heading) {
      flushParagraph()
      result.push({ type: 'heading', level: heading[1].length, parts: parseInline(heading[2]) })
      cursor++
      continue
    }

    if (quotePattern.test(line)) {
      flushParagraph()
      const quoteLines: string[] = []
      while (cursor < lines.length) {
        const quote = syntaxLine(lines[cursor]).match(quotePattern)
        if (!quote) break
        quoteLines.push(quote[1])
        cursor++
      }
      const quoteText = quoteLines.join('\n').trim()
      if (quoteText) result.push({ type: 'quote', parts: parseInline(quoteText) })
      continue
    }

    const nextLine = cursor + 1 < lines.length ? syntaxLine(lines[cursor + 1]) : ''
    if (isTableRow(line) && tableDividerPattern.test(nextLine)) {
      const headerCells = splitTableRow(line)
      const dividerCells = splitTableRow(nextLine)
      if (headerCells.length >= 1 && dividerCells.length >= 1) {
        flushParagraph()
        const columnCount = headerCells.length
        const aligns = Array.from({ length: columnCount }, (_, index) => resolveAlign(dividerCells[index] || ''))
        const rows: InlinePart[][][] = []
        cursor += 2
        while (cursor < lines.length) {
          const rowLine = syntaxLine(lines[cursor])
          if (!isTableRow(rowLine)) break
          const cells = splitTableRow(rowLine)
          rows.push(Array.from({ length: columnCount }, (_, index) => parseInline(cells[index] || '')))
          cursor++
        }
        result.push({ type: 'table', header: headerCells.map(parseInline), aligns, rows })
        continue
      }
    }

    const list = line.match(listPattern)
    if (list) {
      flushParagraph()
      const ordered = /^\d/.test(list[1])
      const items: { value?: number; parts: InlinePart[] }[] = []
      while (cursor < lines.length) {
        const item = syntaxLine(lines[cursor]).match(listPattern)
        if (!item || /^\d/.test(item[1]) !== ordered) break
        items.push({ value: ordered ? Number.parseInt(item[1], 10) : undefined, parts: parseInline(item[2]) })
        cursor++
      }
      result.push({ type: 'list', ordered, items })
      continue
    }

    paragraph.push(lines[cursor])
    cursor++
  }
  flushParagraph()
  return result
}

const blocks = computed(() => parseBlocks(props.text || ''))

const alignStyle = (align: TableAlign) => (align ? { textAlign: align } : undefined)

const copiedIndex = ref<number | null>(null)
let copiedTimer: number | undefined

const writeClipboard = async (text: string) => {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }
  const helper = document.createElement('textarea')
  helper.value = text
  helper.setAttribute('readonly', '')
  helper.style.position = 'fixed'
  helper.style.opacity = '0'
  document.body.appendChild(helper)
  helper.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(helper)
  if (!copied) throw new Error('copy command failed')
}

const copyCode = async (index: number, text: string) => {
  try {
    await writeClipboard(text.replace(/\n$/, ''))
    copiedIndex.value = index
    window.clearTimeout(copiedTimer)
    copiedTimer = window.setTimeout(() => {
      copiedIndex.value = null
    }, 1600)
  } catch {
    ElMessage.error('复制失败，请手动选中代码后复制')
  }
}

onBeforeUnmount(() => window.clearTimeout(copiedTimer))
</script>

<style scoped>
.learning-note {
  color: var(--text);
  font-size: 15px;
  line-height: 1.9;
  overflow-wrap: anywhere;
}
.learning-note > :first-child {
  margin-top: 0;
}
.learning-note > :last-child {
  margin-bottom: 0;
}
.learning-note__heading {
  margin: 28px 0 12px;
  color: var(--text);
  font-weight: 650;
  line-height: 1.55;
}
h2.learning-note__heading {
  font-size: 23px;
  letter-spacing: -0.4px;
}
h3.learning-note__heading {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--line);
  font-size: 18px;
}
h4.learning-note__heading,
h5.learning-note__heading,
h6.learning-note__heading {
  font-size: 16px;
}
.learning-note__paragraph {
  margin: 0 0 16px;
  white-space: pre-wrap;
}
.learning-note__list {
  margin: 12px 0 20px;
  padding-left: 1.6em;
}
.learning-note__list li {
  padding-left: 5px;
  white-space: pre-wrap;
}
.learning-note__list li + li {
  margin-top: 7px;
}
.learning-note__list li::marker {
  color: var(--brand);
  font-weight: 600;
}
.learning-note strong {
  font-weight: 650;
}
.learning-note code {
  padding: 2px 5px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: var(--bg-secondary);
  color: var(--brand);
  font-family: Consolas, 'SFMono-Regular', monospace;
  font-size: 0.9em;
}
.learning-note__code-block {
  margin: 18px 0 22px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-secondary);
  overflow: hidden;
}
.learning-note__code-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 10px 6px 16px;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.02em;
}
.learning-note__copy {
  min-height: 30px;
  padding: 4px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background-color 0.15s ease,
    border-color 0.15s ease;
}
.learning-note__copy:hover {
  color: var(--brand);
  border-color: var(--line);
  background: var(--panel);
}
.learning-note__copy.is-copied {
  color: var(--green);
}
.learning-note__copy:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}
.learning-note__code {
  margin: 0;
  padding: 16px 20px;
  overflow-x: auto;
  border: 0;
  border-radius: 0;
  background: transparent;
  white-space: pre;
  overflow-wrap: normal;
  tab-size: 4;
}
.learning-note__code code {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--text);
  line-height: 1.7;
}
.learning-note__rule {
  height: 0;
  margin: 22px 0;
  border: 0;
  border-top: 1px solid var(--line);
}
.learning-note__quote {
  margin: 14px 0 18px;
  padding: 8px 18px;
  border-left: 3px solid var(--brand);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--brand-light);
  color: var(--text-secondary);
}
.learning-note__quote p {
  margin: 0;
  white-space: pre-wrap;
}
.learning-note__table-wrap {
  margin: 14px 0 20px;
  overflow-x: auto;
}
.learning-note__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.95em;
  line-height: 1.6;
}
.learning-note__table th,
.learning-note__table td {
  padding: 9px 12px;
  border: 1px solid var(--line);
  text-align: left;
  vertical-align: top;
}
.learning-note__table th {
  background: var(--bg-secondary);
  color: var(--text);
  font-weight: 600;
}
.learning-note__table tbody tr:nth-child(even) td {
  background: color-mix(in srgb, var(--bg-secondary) 50%, transparent);
}
@media (max-width: 640px) {
  .learning-note {
    font-size: 14px;
  }
  h2.learning-note__heading {
    font-size: 21px;
  }
  h3.learning-note__heading {
    font-size: 17px;
  }
  .learning-note__code {
    padding: 14px;
  }
  .learning-note__table th,
  .learning-note__table td {
    padding: 7px 9px;
  }
}
</style>
