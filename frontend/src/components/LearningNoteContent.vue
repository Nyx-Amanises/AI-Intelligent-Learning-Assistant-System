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
      <pre v-else-if="block.type === 'code'" class="learning-note__code"><code>{{ block.text }}</code></pre>
      <p v-else class="learning-note__paragraph">
        <component v-for="(part, partIndex) in block.parts" :is="part.tag" :key="partIndex">{{ part.text }}</component>
      </p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ text?: string | null }>()

interface InlinePart {
  tag: 'span' | 'strong' | 'code'
  text: string
}

type NoteBlock =
  | { type: 'heading'; level: number; parts: InlinePart[] }
  | { type: 'paragraph'; parts: InlinePart[] }
  | { type: 'list'; ordered: boolean; items: { value?: number; parts: InlinePart[] }[] }
  | { type: 'code'; text: string }

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
    const isEscaped = /\\$/.test(text.slice(0, cursor))
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

const syntaxLine = (line: string) => line.endsWith('\r') ? line.slice(0, -1) : line
const listPattern = /^ {0,3}([-+*]|\d{1,9}[.)])[ \t]+(.+)$/

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
      if (closing === lines.length) {
        // Preserve an unfinished fence and everything after it exactly as text.
        result.push({ type: 'paragraph', parts: [{ tag: 'span', text: lines.slice(cursor).join('\n') }] })
        break
      }
      const body = lines.slice(cursor + 1, closing)
      result.push({ type: 'code', text: body.join('\n') + (body.length ? '\n' : '') })
      cursor = closing + 1
      continue
    }

    const heading = line.match(/^ {0,3}(#{1,6})[ \t]+(.+)$/)
    if (heading) {
      flushParagraph()
      result.push({ type: 'heading', level: heading[1].length, parts: parseInline(heading[2]) })
      cursor++
      continue
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
</script>

<style scoped>
.learning-note { color: var(--text); font-size: 15px; line-height: 1.9; overflow-wrap: anywhere; }
.learning-note > :first-child { margin-top: 0; }
.learning-note > :last-child { margin-bottom: 0; }
.learning-note__heading { margin: 28px 0 12px; color: var(--text); font-weight: 650; line-height: 1.55; }
h2.learning-note__heading { font-size: 23px; letter-spacing: -.4px; }
h3.learning-note__heading { padding-bottom: 8px; border-bottom: 1px solid var(--line); font-size: 18px; }
h4.learning-note__heading, h5.learning-note__heading, h6.learning-note__heading { font-size: 16px; }
.learning-note__paragraph { margin: 0 0 16px; white-space: pre-wrap; }
.learning-note__list { margin: 12px 0 20px; padding-left: 1.6em; }
.learning-note__list li { padding-left: 5px; white-space: pre-wrap; }
.learning-note__list li + li { margin-top: 7px; }
.learning-note__list li::marker { color: var(--brand); font-weight: 600; }
.learning-note strong { font-weight: 650; }
.learning-note code { padding: 2px 5px; border: 1px solid var(--line); border-radius: 5px; background: var(--bg-secondary); color: var(--brand); font-family: Consolas, "SFMono-Regular", monospace; font-size: .9em; }
.learning-note__code { margin: 18px 0 22px; padding: 18px 20px; overflow-x: auto; border: 1px solid var(--line); border-radius: 10px; background: var(--bg-secondary); white-space: pre; overflow-wrap: normal; tab-size: 4; }
.learning-note__code code { padding: 0; border: 0; border-radius: 0; background: transparent; color: var(--text); line-height: 1.7; }
@media (max-width: 640px) {
  .learning-note { font-size: 14px; }
  h2.learning-note__heading { font-size: 21px; }
  h3.learning-note__heading { font-size: 17px; }
  .learning-note__code { padding: 14px; }
}
</style>
