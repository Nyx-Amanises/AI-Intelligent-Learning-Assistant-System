import { ref, computed } from 'vue'

export type ThemeMode = 'light' | 'eye-care' | 'dark'

export interface ThemeOption {
  key: ThemeMode
  label: string
  hint: string
  icon: string
  color: string
}

export const THEME_STORAGE_KEY = 'ai-learning-theme'
export const DEFAULT_THEME: ThemeMode = 'light'

export const THEME_OPTIONS: ThemeOption[] = [
  {
    key: 'light',
    label: '白天纯白',
    hint: '经典纯白明亮基调，清晰干练',
    icon: 'sun',
    color: '#ffffff'
  },
  {
    key: 'eye-care',
    label: '护眼纸质',
    hint: '柔和豆沙绿与护眼纸质，久看不累',
    icon: 'eye',
    color: '#edf2eb'
  },
  {
    key: 'dark',
    label: '黑夜暗色',
    hint: '沉浸夜间深色，低眩光舒适自习',
    icon: 'moon',
    color: '#131c2e'
  }
]

const currentTheme = ref<ThemeMode>(DEFAULT_THEME)

const applyThemeToDom = (theme: ThemeMode) => {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.setAttribute('data-theme', theme)
  if (theme === 'dark') {
    root.classList.add('dark')
  } else {
    root.classList.remove('dark')
  }
}

export const initTheme = (): ThemeMode => {
  let theme: ThemeMode = DEFAULT_THEME
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY)
    if (saved && (saved === 'light' || saved === 'eye-care' || saved === 'dark')) {
      theme = saved as ThemeMode
    }
  } catch {
    // ignore localStorage access issues
  }
  currentTheme.value = theme
  applyThemeToDom(theme)
  return theme
}

export const useTheme = () => {
  const setTheme = (theme: ThemeMode) => {
    if (!['light', 'eye-care', 'dark'].includes(theme)) return
    currentTheme.value = theme
    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme)
    } catch {
      // ignore
    }
    applyThemeToDom(theme)
  }

  const currentThemeOption = computed(() =>
    THEME_OPTIONS.find((t) => t.key === currentTheme.value) || THEME_OPTIONS[0]
  )

  const isDark = computed(() => currentTheme.value === 'dark')

  return {
    currentTheme,
    themeOptions: THEME_OPTIONS,
    currentThemeOption,
    isDark,
    setTheme,
    initTheme
  }
}

// 模块加载时立即初始化一次，保证在页面挂载前生效
if (typeof window !== 'undefined') {
  initTheme()
}
