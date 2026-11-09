import type { JSONContent } from '@tiptap/vue-3'
import type { NoteContext } from '@/api/modules/note'
import type { RouteLocationRaw } from 'vue-router'
import { isNoteImageUrl } from '@/api/modules/note'

export const emptyNoteDocument = (): JSONContent => ({ type: 'doc', content: [{ type: 'paragraph' }] })
const allowedNodes = new Set([
  'doc',
  'paragraph',
  'text',
  'heading',
  'bulletList',
  'orderedList',
  'listItem',
  'taskList',
  'taskItem',
  'blockquote',
  'codeBlock',
  'hardBreak',
  'horizontalRule',
  'image'
])
const allowedMarks = new Set(['bold', 'italic', 'strike', 'code', 'underline'])

/** Only schema-supported text and attributes enter the editor; stored HTML/URLs never execute. */
export function readNoteDocument(value: string): JSONContent {
  const raw = JSON.parse(value) as JSONContent
  let count = 0
  const clean = (node: JSONContent, depth: number): JSONContent => {
    if (++count > 15000 || depth > 40 || !node || !allowedNodes.has(node.type || ''))
      throw new Error('笔记内容格式无法读取')
    const result: JSONContent = { type: node.type }
    if (node.type === 'text') result.text = typeof node.text === 'string' ? node.text : ''
    if (node.type === 'heading') result.attrs = { level: Math.max(1, Math.min(3, Number(node.attrs?.level) || 1)) }
    if (node.type === 'taskItem') result.attrs = { checked: node.attrs?.checked === true }
    if (node.type === 'orderedList') result.attrs = { start: Math.max(1, Number(node.attrs?.start) || 1) }
    if (node.type === 'image') {
      if (!isNoteImageUrl(node.attrs?.src)) throw new Error('笔记图片地址无效')
      result.attrs = {
        src: node.attrs.src,
        alt: typeof node.attrs.alt === 'string' ? node.attrs.alt.slice(0, 200) : '',
        width: Math.max(1, Math.min(8192, Number(node.attrs.width) || 640)),
        height: Math.max(1, Math.min(8192, Number(node.attrs.height) || 480))
      }
    }
    if (node.marks)
      result.marks = node.marks.filter((mark) => allowedMarks.has(mark.type)).map((mark) => ({ type: mark.type }))
    if (node.content) result.content = node.content.map((child) => clean(child, depth + 1))
    return result
  }
  if (raw.type !== 'doc') throw new Error('笔记内容格式无法读取')
  return clean(raw, 0)
}

export function noteSourceRoute(source: NoteContext): RouteLocationRaw | null {
  if (!source.sourceId) return null
  if (source.sourceType === 'MATERIAL')
    return { path: '/materials', query: { view: 'library', materialId: source.sourceId } }
  if (source.sourceType === 'RESOURCE')
    return { path: '/resources', query: { resourceId: source.sourceId, page: source.pageNumber || 1 } }
  if (source.sourceType === 'QUESTION' && source.practiceSessionId)
    return { path: '/practice', query: { sessionId: source.practiceSessionId }, hash: `#question-${source.sourceId}` }
  return null
}

export function noteDocumentMarkdown(node: JSONContent, depth = 0): string {
  const body = (node.content || []).map((child) => noteDocumentMarkdown(child, depth + 1)).join('')
  switch (node.type) {
    case 'text': {
      let text = node.text || ''
      for (const mark of node.marks || []) {
        if (mark.type === 'bold') text = `**${text}**`
        if (mark.type === 'italic') text = `*${text}*`
        if (mark.type === 'code') text = '`' + text + '`'
        if (mark.type === 'strike') text = `~~${text}~~`
      }
      return text
    }
    case 'heading':
      return '\n' + '#'.repeat(Number(node.attrs?.level) || 1) + ' ' + body.trim() + '\n\n'
    case 'paragraph':
      return body + (depth > 1 ? '\n' : '\n\n')
    case 'blockquote':
      return (
        body
          .trim()
          .split('\n')
          .map((line) => '> ' + line)
          .join('\n') + '\n\n'
      )
    case 'codeBlock':
      return '\n```\n' + body.trimEnd() + '\n```\n\n'
    case 'listItem':
      return '- ' + body.trim() + '\n'
    case 'taskItem':
      return `- [${node.attrs?.checked ? 'x' : ' '}] ` + body.trim() + '\n'
    case 'hardBreak':
      return '\n'
    case 'horizontalRule':
      return '\n---\n\n'
    case 'image':
      return `\n![${String(node.attrs?.alt || '笔记插图').replace(/[\[\]\\]/g, '\\$&')}](${node.attrs?.src || ''})\n\n`
    default:
      return body
  }
}
