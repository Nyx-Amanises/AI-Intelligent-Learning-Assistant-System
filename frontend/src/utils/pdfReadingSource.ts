import type { PdfReadingSpan } from './pdfReadingLayout'

interface RichSpan {
  t: string
  x: number
  y: number
  w: number
  h: number
  c: string
  b: boolean
  i: boolean
  m: boolean
  e?: boolean
}
interface RichPage {
  number: number
  width: number
  height: number
  spans: RichSpan[]
}
interface ReadingIndex {
  schemaVersion: number
  pdfSha256: string
  pageCount: number
  chunkSize: number
}
interface ReadingChunk {
  schemaVersion: number
  pdfSha256: string
  pages: RichPage[]
}
export interface RichReadingText {
  items: PdfReadingSpan[]
  styles: Record<string, { fontFamily: string }>
}

/** Local, versioned extraction keeps the book's colours and verified glyph mappings. */
export function createPdfReadingSource(documentUrl: string, pageCount: number) {
  const base = documentUrl.replace(/\/book\.pdf$/, '/reading/')
  const abort = new AbortController()
  const chunks = new Map<number, Promise<ReadingChunk | null>>()
  let indexPromise: Promise<ReadingIndex | null> | undefined
  const json = async <T>(url: string): Promise<T | null> => {
    if (abort.signal.aborted) return null
    const request = new AbortController()
    const cancel = () => request.abort()
    abort.signal.addEventListener('abort', cancel, { once: true })
    const timeout = setTimeout(cancel, 3000)
    try {
      const response = await fetch(url, { signal: request.signal })
      return response.ok ? ((await response.json()) as T) : null
    } catch {
      return null
    } finally {
      clearTimeout(timeout)
      abort.signal.removeEventListener('abort', cancel)
    }
  }
  return {
    async page(number: number, width: number, height: number): Promise<RichReadingText | null> {
      if (
        abort.signal.aborted ||
        !/^\/open-books\/[a-z0-9-]+\/book\.pdf$/.test(documentUrl) ||
        ![width, height].every((value) => Number.isFinite(value) && value > 0) ||
        !Number.isInteger(number) ||
        number < 1 ||
        number > pageCount
      )
        return null
      indexPromise ||= json<ReadingIndex>(base + 'index.json')
      const index = await indexPromise
      if (
        abort.signal.aborted ||
        !index ||
        index.schemaVersion !== 1 ||
        index.pageCount !== pageCount ||
        index.chunkSize !== 20 ||
        typeof index.pdfSha256 !== 'string' ||
        !/^[a-f0-9]{64}$/.test(index.pdfSha256)
      )
        return null
      const chunkNumber = Math.floor((number - 1) / index.chunkSize) + 1
      if (!chunks.has(chunkNumber)) {
        chunks.set(chunkNumber, json<ReadingChunk>(base + String(chunkNumber).padStart(3, '0') + '.json'))
        if (chunks.size > 4) chunks.delete(chunks.keys().next().value!)
      }
      const chunk = await chunks.get(chunkNumber)
      if (
        abort.signal.aborted ||
        !chunk ||
        chunk.schemaVersion !== 1 ||
        chunk.pdfSha256 !== index.pdfSha256 ||
        !Array.isArray(chunk.pages)
      )
        return null
      const page = chunk.pages.find((item) => item?.number === number)
      if (
        !page ||
        !Array.isArray(page.spans) ||
        ![page.width, page.height].every(Number.isFinite) ||
        Math.abs(page.width - width) > 1 ||
        Math.abs(page.height - height) > 1
      )
        return null
      if (
        !page.spans.every(
          (item) =>
            item &&
            typeof item.t === 'string' &&
            /^#[0-9a-f]{6}$/i.test(item.c) &&
            [item.x, item.y, item.w, item.h].every(Number.isFinite) &&
            item.h > 0 &&
            item.w >= 0 &&
            (item.e === undefined || typeof item.e === 'boolean') &&
            [item.b, item.i, item.m].every((value) => typeof value === 'boolean')
        )
      )
        return null
      return {
        items: page.spans.map((item) => ({
          str: item.t,
          transform: [item.h, 0, 0, item.h, item.x, item.y],
          width: item.w,
          height: item.h,
          fontName: item.m ? 'mono' : 'body',
          hasEOL: Boolean(item.e),
          color: item.c,
          bold: item.b,
          italic: item.i,
          monospace: item.m
        })),
        styles: { mono: { fontFamily: 'monospace' }, body: { fontFamily: 'sans-serif' } }
      }
    },
    dispose() {
      abort.abort()
      chunks.clear()
    }
  }
}
