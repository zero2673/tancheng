import { http } from '@/utils/request'
import type { Voucher } from '@/types'

export const voucherApi = {
  listByShop: (shopId: string) => http.get<Voucher[]>('/voucher/list/' + shopId),
  seckill: (voucherId: string) => http.post<string>('/voucher-order/seckill/' + voucherId),
}