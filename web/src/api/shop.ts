import { http } from '@/utils/request'
import type { Shop, ShopType } from '@/types'

export const shopApi = {
  types: () => http.get<ShopType[]>('/shop-type/list'),
  detail: (id: string) => http.get<Shop>('/shop/' + id),
  byType: (params: { typeId: number; current: number; x?: number; y?: number }) =>
    http.get<Shop[]>('/shop/of/type', params),
  searchByName: (params: { name: string; current: number }) => http.get<Shop[]>('/shop/of/name', params),
}