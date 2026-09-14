import { onMounted, onUnmounted, shallowRef } from 'vue'
import { getDailyQuote, getNextQuoteUpdateAt } from '@/utils/dailyQuote'

export const useDailyQuote = () => {
  const quote = shallowRef(getDailyQuote())
  let timer: number | undefined

  const cancelTimer = () => {
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timer = undefined
    }
  }

  const refreshQuote = () => {
    cancelTimer()
    const now = Date.now()
    quote.value = getDailyQuote(now)
    timer = window.setTimeout(refreshQuote, Math.max(1, getNextQuoteUpdateAt(now) - now))
  }

  const refreshWhenVisible = () => {
    if (document.visibilityState === 'visible') refreshQuote()
  }

  onMounted(() => {
    refreshQuote()
    document.addEventListener('visibilitychange', refreshWhenVisible)
    window.addEventListener('focus', refreshQuote)
    window.addEventListener('pageshow', refreshQuote)
  })

  onUnmounted(() => {
    cancelTimer()
    document.removeEventListener('visibilitychange', refreshWhenVisible)
    window.removeEventListener('focus', refreshQuote)
    window.removeEventListener('pageshow', refreshQuote)
  })

  return quote
}
