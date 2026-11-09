import { Node, VueNodeViewRenderer, type Editor } from '@tiptap/vue-3'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import { isNoteImageUrl } from '@/api/modules/note'
import NoteImageView from './NoteImageView.vue'

const uploads = new PluginKey<DecorationSet>('note-image-uploads')
export const addImagePlaceholder = (editor: Editor, id: string, pos: number) =>
  editor.view.dispatch(editor.state.tr.setMeta(uploads, { add: { id, pos } }))
export const removeImagePlaceholder = (editor: Editor, id: string) =>
  editor.view.dispatch(editor.state.tr.setMeta(uploads, { remove: id }))
export const imagePlaceholderPosition = (editor: Editor, id: string) =>
  uploads.getState(editor.state)?.find(undefined, undefined, (spec) => spec.id === id)[0]?.from

export const NoteImage = Node.create({
  name: 'image',
  group: 'block',
  atom: true,
  draggable: true,
  addAttributes() {
    return {
      src: { default: '', parseHTML: (element) => element.getAttribute('data-note-image-src') },
      alt: { default: '' },
      width: { default: 640 },
      height: { default: 480 }
    }
  },
  parseHTML() {
    return [
      {
        tag: 'img[data-note-image]',
        getAttrs: (element) => (isNoteImageUrl(element.getAttribute('data-note-image-src')) ? null : false)
      }
    ]
  },
  renderHTML({ HTMLAttributes }) {
    // Private URLs are resolved by the authenticated node view, never by an unauthenticated img request.
    const { src, ...attributes } = HTMLAttributes
    return ['img', { ...attributes, 'data-note-image': 'true', 'data-note-image-src': src }]
  },
  addNodeView() {
    return VueNodeViewRenderer(NoteImageView)
  },
  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: uploads,
        state: {
          init: () => DecorationSet.empty,
          apply(transaction, previous) {
            let decorations = previous.map(transaction.mapping, transaction.doc)
            const action = transaction.getMeta(uploads)
            if (action?.add) {
              const label = document.createElement('span')
              label.className = 'note-image-uploading'
              label.textContent = '正在插入图片…'
              label.setAttribute('contenteditable', 'false')
              label.setAttribute('role', 'status')
              decorations = decorations.add(transaction.doc, [
                Decoration.widget(action.add.pos, label, { id: action.add.id, side: 1 })
              ])
            }
            if (action?.remove)
              decorations = decorations.remove(
                decorations.find(undefined, undefined, (spec) => spec.id === action.remove)
              )
            return decorations
          }
        },
        props: { decorations: (state) => uploads.getState(state) }
      })
    ]
  }
})
