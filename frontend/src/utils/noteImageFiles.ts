import type { JSONContent } from '@tiptap/vue-3'
import { getNoteImageApi, isNoteImageUrl, type NoteContext } from '@/api/modules/note'
import { noteDocumentMarkdown } from '@/utils/noteDocument'

const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const MAX_IMAGE_SIDE = 8192
const MAX_IMAGE_PIXELS = 16_000_000
const WEBP_OUTPUT_SIDE = 2400

export async function prepareNoteImage(file: File): Promise<File> {
  if (!file.size) throw new Error('图片文件为空，请重新选择')
  if (file.size > MAX_IMAGE_BYTES) throw new Error('图片不能超过 5 MB')
  const extension = file.name.split('.').pop()?.toLowerCase()
  const type =
    file.type.toLowerCase() ||
    (extension === 'jpg' || extension === 'jpeg'
      ? 'image/jpeg'
      : extension === 'png'
        ? 'image/png'
        : extension === 'webp'
          ? 'image/webp'
          : '')
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(type)) throw new Error('请选择 JPG、PNG 或 WebP 图片')
  if (type !== 'image/webp') return file

  const imageUrl = URL.createObjectURL(file)
  const image = new Image()
  let canvas: HTMLCanvasElement | undefined
  try {
    image.src = imageUrl
    try {
      await image.decode()
    } catch {
      throw new Error('WebP 图片无法读取，请重新选择')
    }
    const width = image.naturalWidth
    const height = image.naturalHeight
    if (!width || !height || width > MAX_IMAGE_SIDE || height > MAX_IMAGE_SIDE || width * height > MAX_IMAGE_PIXELS)
      throw new Error('图片单边不能超过 8192 像素，总像素不能超过 1600 万')

    const scale = Math.min(1, WEBP_OUTPUT_SIDE / Math.max(width, height))
    canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(width * scale))
    canvas.height = Math.max(1, Math.round(height * scale))
    const context = canvas.getContext('2d')
    if (!context) throw new Error('当前浏览器无法转换 WebP 图片')
    context.drawImage(image, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas!.toBlob(
        (result) => (result ? resolve(result) : reject(new Error('WebP 图片转换失败，请重新选择'))),
        'image/png'
      )
    })
    if (blob.size > MAX_IMAGE_BYTES) throw new Error('转换为 PNG 后超过 5 MB，请缩小图片后再上传')
    const name = file.name.replace(/\.[^.]*$/, '') || '笔记插图'
    return new File([blob], `${name}.png`, { type: 'image/png', lastModified: file.lastModified })
  } finally {
    image.src = ''
    if (canvas) {
      canvas.width = 0
      canvas.height = 0
    }
    URL.revokeObjectURL(imageUrl)
  }
}

interface NoteDownloadOptions {
  title: string
  document: JSONContent
  source: NoteContext
}

function noteMarkdown({ title, document: noteDocument, source }: NoteDownloadOptions): string {
  return (
    `# ${title || '未命名笔记'}\n\n` +
    (source.sourceTitle
      ? `> ${source.sourceTitle}${source.pageNumber ? ' · 第 ' + source.pageNumber + ' 页' : ''}\n\n`
      : '') +
    (source.sourceExcerpt
      ? source.sourceExcerpt
          .split('\n')
          .map((line) => '> ' + line)
          .join('\n') + '\n\n'
      : '') +
    noteDocumentMarkdown(noteDocument)
  )
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  try {
    link.href = url
    link.download = filename
    link.hidden = true
    document.body.append(link)
    link.click()
  } finally {
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
}

export async function downloadNoteDocument(options: NoteDownloadOptions): Promise<void> {
  const noteDocument = JSON.parse(JSON.stringify(options.document)) as JSONContent
  const imageNodes: JSONContent[] = []
  const imageUrls = new Set<string>()
  const collectImages = (node: JSONContent) => {
    if (node.type === 'image') {
      if (!isNoteImageUrl(node.attrs?.src)) throw new Error('笔记包含无效图片地址，无法导出')
      imageNodes.push(node)
      imageUrls.add(node.attrs.src)
    }
    node.content?.forEach(collectImages)
  }
  collectImages(noteDocument)
  const filename = (options.title || '未命名笔记').replace(/[\\/:*?"<>|]/g, '_').slice(0, 80)
  if (!imageUrls.size) {
    downloadBlob(
      new Blob([noteMarkdown({ ...options, document: noteDocument })], { type: 'text/markdown;charset=utf-8' }),
      `${filename}.md`
    )
    return
  }

  const controller = new AbortController()
  try {
    const { zipSync, strToU8 } = await import('fflate')
    const imageFiles = await Promise.all(
      [...imageUrls].map(async (src, index) => {
        const blob = await getNoteImageApi(src, controller.signal)
        const path = `images/${index + 1}.${blob.type === 'image/png' ? 'png' : 'jpg'}`
        return { src, path, data: new Uint8Array(await blob.arrayBuffer()) }
      })
    )
    const paths = new Map(imageFiles.map(({ src, path }) => [src, path]))
    for (const node of imageNodes) node.attrs = { ...node.attrs, src: paths.get(node.attrs!.src) }
    const files: Record<string, Uint8Array> = {
      '笔记.md': strToU8(noteMarkdown({ ...options, document: noteDocument }))
    }
    for (const { path, data } of imageFiles) files[path] = data
    const archive = new Uint8Array(zipSync(files)).buffer
    downloadBlob(new Blob([archive], { type: 'application/zip' }), `${filename}.zip`)
  } catch (error) {
    controller.abort()
    throw error
  }
}
