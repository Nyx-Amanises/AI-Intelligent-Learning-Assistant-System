import { ref } from 'vue'

const active = ref(false)
const progress = ref(0)
const visible = ref(false)
let timer: number | undefined
let hideTimer: number | undefined

export function usePageProgress() {
  const start = () => {
    window.clearTimeout(timer)
    window.clearTimeout(hideTimer)
    progress.value = 18
    visible.value = true
    active.value = true

    const step = () => {
      if (!active.value) return
      if (progress.value < 65) {
        progress.value += Math.floor(Math.random() * 15 + 10)
      } else if (progress.value < 85) {
        progress.value += Math.floor(Math.random() * 6 + 3)
      } else if (progress.value < 96) {
        progress.value += 1
      }
      timer = window.setTimeout(step, 180)
    }
    timer = window.setTimeout(step, 120)
  }

  const finish = () => {
    window.clearTimeout(timer)
    active.value = false
    progress.value = 100
    hideTimer = window.setTimeout(() => {
      visible.value = false
      progress.value = 0
    }, 280)
  }

  return {
    active,
    progress,
    visible,
    start,
    finish
  }
}
