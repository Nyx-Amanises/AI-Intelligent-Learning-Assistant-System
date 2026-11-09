/** Reflow PDF text without treating every printed line as a paragraph. */
export interface PdfReadingSpan {
  str: string
  transform: number[]
  width: number
  height: number
  fontName: string
  hasEOL: boolean
  color?: string
  bold?: boolean
  italic?: boolean
  monospace?: boolean
}
export interface ReadingRun {
  text: string
  color?: string
  bold?: boolean
  italic?: boolean
  monospace?: boolean
}
export interface ReadingBlock {
  type: 'paragraph' | 'heading' | 'code' | 'caption'
  text: string
  runs: ReadingRun[]
}
interface ReadingLine {
  text: string
  runs: ReadingRun[]
  x: number
  y: number
  end: number
  height: number
  monoCharacters: number
}

function appendRun(runs: ReadingRun[], run: ReadingRun) {
  if (!run.text) return
  const last = runs.at(-1)
  if (
    last &&
    last.color === run.color &&
    last.bold === run.bold &&
    last.italic === run.italic &&
    last.monospace === run.monospace
  ) {
    last.text += run.text
  } else {
    runs.push({ ...run })
  }
}

function appendRuns(runs: ReadingRun[], additions: ReadingRun[]) {
  for (const run of additions) appendRun(runs, run)
}

function trimRuns(runs: ReadingRun[]): ReadingRun[] {
  const trimmed = runs.map((run) => ({ ...run }))
  while (trimmed.length) {
    trimmed[0]!.text = trimmed[0]!.text.trimStart()
    if (trimmed[0]!.text) break
    trimmed.shift()
  }
  while (trimmed.length) {
    trimmed[trimmed.length - 1]!.text = trimmed[trimmed.length - 1]!.text.trimEnd()
    if (trimmed[trimmed.length - 1]!.text) break
    trimmed.pop()
  }
  return trimmed
}

function runsText(runs: ReadingRun[]): string {
  return runs.map((run) => run.text).join('')
}

export function reflowPdfText(
  items: PdfReadingSpan[],
  styles: Record<string, { fontFamily: string }>,
  pageHeight?: number
): ReadingBlock[] {
  const lines: ReadingLine[] = []
  let line: ReadingLine | undefined
  for (const item of items) {
    if (!item.str) {
      if (item.hasEOL) line = undefined
      continue
    }
    const x = item.transform[4] || 0
    const y = item.transform[5] || 0
    const height = Math.max(1, Math.abs(item.height || item.transform[3] || 10))
    const mono = item.monospace ?? /mono|courier|consolas/i.test(styles[item.fontName]?.fontFamily || '')
    const characters = item.str.replace(/\s/g, '').length
    const run: ReadingRun = {
      text: item.str,
      color: item.color,
      bold: item.bold,
      italic: item.italic,
      monospace: mono
    }
    if (!line || Math.abs(y - line.y) > Math.max(height, line.height) * 0.4) {
      line = { text: item.str, runs: [run], x, y, end: x + item.width, height, monoCharacters: mono ? characters : 0 }
      lines.push(line)
    } else {
      const gap = x - line.end
      const spaceCount = mono && line.runs.at(-1)?.monospace
        ? Math.min(12, Math.max(1, Math.round(gap / (height * 0.6))))
        : 1
      const separator = gap > height * 0.15 && !/\s$/.test(line.text) && !/^\s/.test(item.str)
        ? ' '.repeat(spaceCount)
        : ''
      const oldLength = line.text.trim().length
      const addedLength = item.str.trim().length
      if (oldLength + addedLength) {
        line.height = (line.height * oldLength + height * addedLength) / (oldLength + addedLength)
      }
      appendRun(line.runs, { ...line.runs.at(-1), text: separator })
      appendRun(line.runs, run)
      line.text += separator + item.str
      line.end = x + item.width
      line.monoCharacters += mono ? characters : 0
    }
    if (item.hasEOL) line = undefined
  }
  const weightedSizes = lines.flatMap((item) => Array(Math.min(item.text.length, 100)).fill(item.height) as number[])
  weightedSizes.sort((a, b) => a - b)
  const bodySize = weightedSizes[Math.floor(weightedSizes.length / 2)] || 10
  const blocks: ReadingBlock[] = []
  const codeLines = new Map<ReadingBlock, ReadingLine[]>()
  let previous: ReadingLine | undefined
  for (const current of lines) {
    const text = current.text.trim()
    if (!text) continue
    const atPageEdge = pageHeight && (current.y > pageHeight * 0.9 || current.y < pageHeight * 0.08)
    // Running page numbers and website headers are navigation furniture, not body paragraphs.
    if (atPageEdge && (/^\d+$/.test(text) || (text.length < 120 && /www[.．]|https?:\/\//.test(text)))) continue
    const type: ReadingBlock['type'] =
      current.monoCharacters > text.replace(/\s/g, '').length * 0.65 || /^(?:#\s|\/\/|\/\*)/.test(text)
        ? 'code'
        : current.height > bodySize * 1.13 || /^(?:第.{1,12}[章节]|\d+(?:\.\d+)+\s*[^\d\s]|\d+[.．]\s+\S)/.test(text)
          ? 'heading'
          : /^(?:图|表)\s*\d|^Figure\s+\d|^(?:https?:\/\/|www\.)/.test(text) || /^\d+$/.test(text)
            ? 'caption'
            : 'paragraph'
    const last = blocks.at(-1)
    const lineGap = previous ? Math.abs(current.y - previous.y) : Infinity
    const sameParagraph =
      previous &&
      type === 'paragraph' &&
      last?.type === 'paragraph' &&
      lineGap < bodySize * 1.9 &&
      Math.abs(previous.x - current.x) < bodySize * 1.6 &&
      Math.abs(previous.height - current.height) < bodySize * 0.12 &&
      !/[。！？!?：:]$/.test(previous.text.trimEnd()) &&
      !/^[•●▪\-*]\s/.test(text)
    if (last && type === 'code' && last.type === 'code' && lineGap < bodySize * 2) {
      codeLines.get(last)?.push(current)
    } else if (last && sameParagraph) {
      const betweenLatinWords = /[A-Za-z0-9,;.)]$/.test(last.text) && /^[A-Za-z0-9(]/.test(text)
      appendRun(last.runs, { ...last.runs.at(-1), text: betweenLatinWords ? ' ' : '' })
      appendRuns(last.runs, trimRuns(current.runs))
      last.text = runsText(last.runs)
    } else {
      const runs = trimRuns(current.runs)
      const block = { type, text: runsText(runs), runs }
      blocks.push(block)
      if (type === 'code') codeLines.set(block, [current])
    }
    previous = current
  }
  for (const [block, code] of codeLines) {
    const origin = Math.min(...code.map((item) => item.x))
    block.runs = []
    for (const [index, item] of code.entries()) {
      if (index) appendRun(block.runs, { text: '\n' })
      const indent = ' '.repeat(Math.min(48, Math.max(0, Math.round((item.x - origin) / (item.height * 0.6)))))
      appendRun(block.runs, { text: indent, monospace: true })
      appendRuns(block.runs, item.runs)
    }
    block.text = runsText(block.runs)
  }
  return blocks
}

export interface PageBounds {
  x: number
  y: number
  width: number
  height: number
}

/** Measure rendered marks, including diagrams; never crop based only on text. */
export function measurePageInk(pixels: ImageData, pageWidth: number, pageHeight: number): PageBounds {
  let left = pixels.width,
    top = pixels.height,
    right = -1,
    bottom = -1
  for (let y = 0; y < pixels.height; y++) {
    for (let x = 0; x < pixels.width; x++) {
      const index = (y * pixels.width + x) * 4
      if (
        pixels.data[index + 3]! > 8 &&
        Math.min(pixels.data[index]!, pixels.data[index + 1]!, pixels.data[index + 2]!) < 248
      ) {
        left = Math.min(left, x)
        right = Math.max(right, x)
        top = Math.min(top, y)
        bottom = Math.max(bottom, y)
      }
    }
  }
  if (right < left) return { x: 0, y: 0, width: pageWidth, height: pageHeight }
  const scaleX = pageWidth / pixels.width,
    scaleY = pageHeight / pixels.height
  // Bound the crop on sparse title pages and keep a little space around every mark.
  const x = Math.max(0, Math.min(left * scaleX - 8, pageWidth * 0.15))
  const y = Math.max(0, Math.min(top * scaleY - 8, pageHeight * 0.08))
  const endX = Math.min(pageWidth, Math.max((right + 1) * scaleX + 8, pageWidth * 0.85))
  const endY = Math.min(pageHeight, Math.max((bottom + 1) * scaleY + 8, pageHeight * 0.8))
  return { x, y, width: endX - x, height: endY - y }
}
