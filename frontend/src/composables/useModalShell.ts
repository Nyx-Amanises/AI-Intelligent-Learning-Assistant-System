import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

export interface ModalShellOptions {
  /** 面板是否可见（可写）。 */
  visible: Ref<boolean>
  /** 对话框根元素，作为焦点陷阱范围与初始焦点目标。 */
  panelRef: Ref<HTMLElement | null>
  /** 移动端抽屉式侧栏；打开时焦点陷阱只在侧栏内循环。 */
  sidebarRef: Ref<HTMLElement | null>
  /** 关闭面板后若原焦点元素已不存在，则把焦点还给启动按钮。 */
  launcherRef: Ref<HTMLElement | null>
  /** 收起移动端侧栏后焦点回到的菜单按钮。 */
  mobileMenuRef: Ref<HTMLElement | null>
  /** 判定紧凑布局（移动端）的媒体查询。 */
  compactMediaQuery?: string
  /** 面板打开时（锁定滚动之后、聚焦之前）执行的业务逻辑。 */
  onOpen?: () => void
}

const FOCUSABLE_SELECTOR =
  'button:not([disabled]), a[href], input:not([disabled]), textarea:not([disabled]), select:not([disabled]), summary, [tabindex]:not([tabindex="-1"])'

/**
 * 模态面板通用壳层逻辑：body 滚动锁定、焦点保存与还原、Tab 焦点陷阱、
 * Escape 关闭，以及紧凑布局（移动端）下的离屏侧栏开合。
 */
export const useModalShell = (options: ModalShellOptions) => {
  const { visible, panelRef, sidebarRef, launcherRef, mobileMenuRef } = options
  const isCompactLayout = ref(false)
  const mobileSidebarOpen = ref(false)
  let compactLayoutQuery: MediaQueryList | null = null
  let previousFocusedElement: HTMLElement | null = null
  let previousBodyOverflow = ''
  let bodyScrollLocked = false

  const lockBodyScroll = () => {
    if (!bodyScrollLocked) previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    bodyScrollLocked = true
  }

  const restoreBodyScroll = () => {
    if (!bodyScrollLocked) return
    document.body.style.overflow = previousBodyOverflow
    bodyScrollLocked = false
  }

  const syncPanelVisibility = async (value: boolean) => {
    if (value) {
      previousFocusedElement = document.activeElement instanceof HTMLElement ? document.activeElement : null
      lockBodyScroll()
      options.onOpen?.()
      await nextTick()
      if (visible.value) panelRef.value?.focus({ preventScroll: true })
    } else {
      restoreBodyScroll()
      await nextTick()
      if (!visible.value) {
        const target = previousFocusedElement?.isConnected ? previousFocusedElement : launcherRef.value
        target?.focus({ preventScroll: true })
      }
    }
  }

  watch(visible, syncPanelVisibility)

  const syncCompactLayout = () => {
    isCompactLayout.value = Boolean(compactLayoutQuery?.matches)
    if (!isCompactLayout.value) mobileSidebarOpen.value = false
  }

  const openPanel = () => {
    mobileSidebarOpen.value = false
    visible.value = true
  }

  const closePanel = () => {
    mobileSidebarOpen.value = false
    visible.value = false
  }

  const openMobileSidebar = () => {
    mobileSidebarOpen.value = true
    void nextTick(() => sidebarRef.value?.querySelector<HTMLButtonElement>('button')?.focus())
  }

  const closeMobileSidebar = () => {
    const wasOpen = mobileSidebarOpen.value
    mobileSidebarOpen.value = false
    if (wasOpen) void nextTick(() => mobileMenuRef.value?.focus())
  }

  const handlePanelKeydown = (event: KeyboardEvent) => {
    if (event.defaultPrevented || event.isComposing) return
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      if (mobileSidebarOpen.value) closeMobileSidebar()
      else closePanel()
      return
    }
    if (event.key !== 'Tab') return
    const scope = isCompactLayout.value && mobileSidebarOpen.value ? sidebarRef.value : panelRef.value
    if (!scope) return
    const focusable = Array.from(scope.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
      (element) =>
        !element.closest('[inert]') &&
        element.getClientRects().length > 0 &&
        getComputedStyle(element).visibility !== 'hidden'
    )
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    const active = document.activeElement
    if (!first || !last) {
      event.preventDefault()
      panelRef.value?.focus()
    } else if (!focusable.includes(active as HTMLElement)) {
      event.preventDefault()
      ;(event.shiftKey ? last : first).focus()
    } else if (event.shiftKey && active === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && active === last) {
      event.preventDefault()
      first.focus()
    }
  }

  onMounted(() => {
    compactLayoutQuery = window.matchMedia(options.compactMediaQuery || '(max-width: 900px)')
    compactLayoutQuery.addEventListener('change', syncCompactLayout)
    syncCompactLayout()
    if (visible.value) void syncPanelVisibility(true)
  })

  onBeforeUnmount(() => {
    restoreBodyScroll()
    compactLayoutQuery?.removeEventListener('change', syncCompactLayout)
  })

  return {
    isCompactLayout,
    mobileSidebarOpen,
    openPanel,
    closePanel,
    openMobileSidebar,
    closeMobileSidebar,
    handlePanelKeydown
  }
}
