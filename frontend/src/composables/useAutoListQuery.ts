import { nextTick, onUnmounted, watch, type WatchSource } from 'vue'

export const useAutoListQuery = (
  sources: WatchSource | WatchSource[],
  query: () => void | Promise<void>,
  delay = 360
) => {
  let timer: number | undefined
  let ignoringProgrammaticChange = false

  const cancel = () => {
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timer = undefined
    }
  }

  const trigger = () => {
    cancel()
    timer = window.setTimeout(() => {
      timer = undefined
      void query()
    }, delay)
  }

  const runNow = () => {
    cancel()
    void query()
  }

  const runAfterMutation = (mutate: () => void) => {
    ignoringProgrammaticChange = true
    cancel()
    mutate()
    void nextTick(() => {
      ignoringProgrammaticChange = false
      runNow()
    })
  }

  watch(sources, () => {
    if (!ignoringProgrammaticChange) {
      trigger()
    }
  })

  onUnmounted(cancel)

  return {
    cancel,
    runNow,
    runAfterMutation,
    trigger
  }
}
