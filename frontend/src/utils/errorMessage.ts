import { ElMessage } from 'element-plus'

const NETWORK_PATTERN = /network|timeout|timed out|failed to fetch|econnaborted|err_|socket|aborted/i
const CJK_PATTERN = /[㐀-鿿]/

/**
 * 把接口或网络层抛出的错误整理成适合直接展示给学生的中文提示。
 *
 * - 后端返回的中文业务提示原样保留；
 * - axios / fetch 的英文网络、超时信息统一换成中文；
 * - 其他英文或空信息一律用调用方给的兜底文案。
 */
export const getErrorMessage = (error: unknown, fallback: string) => {
  const raw = error instanceof Error ? error.message : typeof error === 'string' ? error : ''
  const message = raw.trim()
  if (!message) return fallback
  const hasChinese = CJK_PATTERN.test(message)
  if (!hasChinese && NETWORK_PATTERN.test(message)) return '网络连接暂时不可用，请检查连接后重试。'
  return hasChinese ? message : fallback
}

/**
 * 直接弹出整理后的错误提示。
 */
export const showErrorMessage = (error: unknown, fallback: string) => {
  ElMessage.error(getErrorMessage(error, fallback))
}
