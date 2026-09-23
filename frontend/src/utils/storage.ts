/**
 * 安全的 localStorage 封装：读写失败（隐私模式、配额满、数据损坏）不会让页面崩溃。
 */

export const readStorage = (key: string): string | null => {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

export const writeStorage = (key: string, value: string) => {
  try {
    window.localStorage.setItem(key, value)
    return true
  } catch {
    return false
  }
}

export const removeStorage = (key: string) => {
  try {
    window.localStorage.removeItem(key)
  } catch {
    // ignore
  }
}

export const readStorageJson = <T>(key: string, fallback: T): T => {
  const raw = readStorage(key)
  if (raw === null) return fallback
  try {
    return JSON.parse(raw) as T
  } catch {
    removeStorage(key)
    return fallback
  }
}

export const writeStorageJson = (key: string, value: unknown) => writeStorage(key, JSON.stringify(value))
