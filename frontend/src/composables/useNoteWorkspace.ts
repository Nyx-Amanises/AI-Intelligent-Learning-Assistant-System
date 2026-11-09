import { ref, shallowRef } from 'vue'
import type { NoteContext } from '@/api/modules/note'
export type { NoteContext } from '@/api/modules/note'

interface NoteRequest {
  key: number
  id?: number
  context?: NoteContext
  notebookId?: number
}
const visible = ref(false)
const request = shallowRef<NoteRequest | null>(null)
const refreshVersion = ref(0)
let sequence = 0

export const useNoteWorkspace = () => ({
  visible,
  request,
  refreshVersion,
  openNote(id: number) {
    request.value = { key: ++sequence, id }
    visible.value = true
  },
  createNote(context: NoteContext = { sourceType: 'GENERAL' }, notebookId?: number) {
    request.value = { key: ++sequence, context: { ...context }, notebookId }
    visible.value = true
  },
  notifySaved() {
    refreshVersion.value++
  }
})
