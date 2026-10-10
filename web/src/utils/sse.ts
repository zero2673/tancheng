import { getToken } from '@/utils/request'

export interface SeckillResult {
  orderId?: string
  success: boolean
  message: string
}

/**
 * 订阅秒杀结果。
 *
 * 这里用 fetch + ReadableStream 而不是 EventSource：EventSource 不能自定义请求头，
 * 而后端拦截器要求 authorization 头，所以只能自己解析 SSE 报文。
 */
export async function subscribeSeckillResult(
  orderId: string,
  onResult: (result: SeckillResult) => void,
  onError?: (error: unknown) => void,
): Promise<void> {
  try {
    const response = await fetch(`/api/voucher-order/seckill/result/${orderId}`, {
      headers: { authorization: getToken() },
    })
    if (!response.ok || !response.body) {
      throw new Error(`SSE 连接失败：${response.status}`)
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const chunks = buffer.split('\n\n')
      buffer = chunks.pop() ?? ''
      for (const chunk of chunks) {
        const dataLine = chunk.split('\n').find((line) => line.startsWith('data:'))
        if (!dataLine) continue
        const payload = dataLine.slice(5).trim()
        if (!payload) continue
        try {
          const parsed = JSON.parse(payload)
          if (parsed && typeof parsed === 'object' && 'success' in parsed) {
            onResult(parsed as SeckillResult)
            return
          }
        } catch {
          // connected 事件的数据是纯字符串（订单号），忽略即可
        }
      }
    }
  } catch (error) {
    onError?.(error)
  }
}