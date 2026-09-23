import { ElMessageBox } from 'element-plus'

/**
 * 危险操作二次确认。返回 true 表示用户点了确认，取消或关闭返回 false，不会抛错。
 */
export const confirmDanger = async (
  message: string,
  options: { title?: string; confirmText?: string; cancelText?: string } = {}
) => {
  const { title = '删除确认', confirmText = '确认删除', cancelText = '取消' } = options
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: confirmText,
      cancelButtonText: cancelText
    })
    return true
  } catch {
    return false
  }
}
