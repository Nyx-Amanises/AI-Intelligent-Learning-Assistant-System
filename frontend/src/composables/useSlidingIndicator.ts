import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref, type WatchSource } from 'vue'

/**
 * 导航中跟随当前项滑动的指示条。在容器内查找 `activeSelector` 对应的元素，
 * 把指示条移动到它的位置；同一组导航之间切换时平滑滑动，首次出现时直接定位。
 */
export const useSlidingIndicator = (
  containerRef: Ref<HTMLElement | null>,
  activeSelector: string,
  source: WatchSource
) => {
  const indicatorStyle = ref<Record<string, string>>({ opacity: '0' })
  let visible = false
  let observer: ResizeObserver | null = null

  const measure = () => {
    const container = containerRef.value
    const active = container?.querySelector<HTMLElement>(activeSelector)
    if (!container || !active) {
      visible = false
      indicatorStyle.value = { ...indicatorStyle.value, opacity: '0', transition: 'none' }
      return
    }
    indicatorStyle.value = {
      opacity: '1',
      width: `${active.offsetWidth}px`,
      height: `${active.offsetHeight}px`,
      transform: `translate(${active.offsetLeft}px, ${active.offsetTop}px)`,
      transition: visible ? '' : 'none'
    }
    visible = true
  }

  watch(source, () => void nextTick(measure), { flush: 'post' })

  onMounted(() => {
    measure()
    if (typeof ResizeObserver !== 'undefined' && containerRef.value) {
      observer = new ResizeObserver(() => measure())
      observer.observe(containerRef.value)
    }
    // 中文字体晚于首帧加载时，宽度会变化，加载完成后再量一次。
    void document.fonts?.ready.then(measure)
  })

  onBeforeUnmount(() => observer?.disconnect())

  return { indicatorStyle, measure }
}
