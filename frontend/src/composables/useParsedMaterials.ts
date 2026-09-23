import { ref } from 'vue'
import { getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import { getErrorMessage } from '@/utils/errorMessage'

const PAGE_SIZE = 100

/**
 * 拉取当前用户"已解析"的全部资料，供总结、出题、错题、统计等页面的资料下拉使用。
 * 自动翻完所有分页；请求失败时 `error` 有值，不弹 toast，由页面决定如何提示。
 */
export const useParsedMaterials = () => {
  const materials = ref<MaterialPageItem[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')
  let requestSeq = 0

  const load = async () => {
    const requestId = ++requestSeq
    loading.value = true
    error.value = ''
    try {
      const first = await getMaterialPageApi({ current: 1, size: PAGE_SIZE, parseStatus: 'SUCCESS' })
      const firstPage = first.data.data
      const records: MaterialPageItem[] = firstPage.records || []
      const pageCount = Math.ceil(Number(firstPage.total || records.length) / PAGE_SIZE)
      const rest = await Promise.all(
        Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) =>
          getMaterialPageApi({ current: index + 2, size: PAGE_SIZE, parseStatus: 'SUCCESS' })
        )
      )
      if (requestId !== requestSeq) return
      materials.value = [...records, ...rest.flatMap((res) => res.data.data.records || [])].filter(
        (item) => String(item.parseStatus || '').toUpperCase() === 'SUCCESS'
      )
      loaded.value = true
    } catch (err) {
      if (requestId !== requestSeq) return
      error.value = getErrorMessage(err, '资料列表暂时无法加载')
    } finally {
      if (requestId === requestSeq) loading.value = false
    }
  }

  return { materials, loading, loaded, error, load }
}
