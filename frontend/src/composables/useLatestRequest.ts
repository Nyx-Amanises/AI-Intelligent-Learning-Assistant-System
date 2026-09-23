/**
 * 为"可能被后发先至的响应覆盖"的异步加载提供最新请求判定。
 *
 * 用法：
 *   const detailRequest = useLatestRequest()
 *   const load = async (id: number) => {
 *     const requestId = detailRequest.begin()
 *     const res = await api(id)
 *     if (!detailRequest.isLatest(requestId)) return   // 已有更新的请求，丢弃这份过期响应
 *     ...
 *   }
 */
export const useLatestRequest = () => {
  let latest = 0
  const begin = () => ++latest
  const isLatest = (requestId: number) => requestId === latest
  /** 让所有在途请求作废（例如关闭抽屉、切换对象时）。 */
  const invalidate = () => {
    latest += 1
  }
  return { begin, isLatest, invalidate }
}
