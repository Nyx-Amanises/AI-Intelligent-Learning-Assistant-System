import { onUnmounted, watch, type WatchSource } from 'vue'

/**
 * 条件轮询：`active` 为 true 时每隔 `intervalMs` 调用一次 `task`，为 false 时停止；组件卸载自动停止。
 * 使用 setTimeout 链而不是 setInterval，避免上一轮请求未返回时堆积。
 */
export const usePolling = (
  active: WatchSource<boolean>,
  task: () => void | Promise<void>,
  intervalMs = 3000
) => {
  let timer: number | undefined
  let disposed = false
  let running = false

  const stop = () => {
    window.clearTimeout(timer)
    timer = undefined
    running = false
  }

  const schedule = () => {
    window.clearTimeout(timer)
    if (disposed || !running) return
    timer = window.setTimeout(async () => {
      if (disposed || !running) return
      try {
        await task()
      } finally {
        schedule()
      }
    }, intervalMs)
  }

  const start = () => {
    if (running) return
    running = true
    schedule()
  }

  watch(active, (value) => (value ? start() : stop()), { immediate: true })

  onUnmounted(() => {
    disposed = true
    stop()
  })

  return { start, stop }
}
