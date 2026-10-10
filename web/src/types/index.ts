/** 后端统一返回结构（注意：Long 已被序列化成字符串，避免 JS 精度丢失） */
export interface Result<T = unknown> {
  success: boolean
  errorMsg?: string | null
  data?: T
  total?: string | number
}

export interface UserDTO {
  id: string
  nickName: string
  icon: string
}

export interface ShopType {
  id: number
  name: string
  icon: string
  sort: number
}

export interface Shop {
  id: string
  name: string
  typeId: number
  images: string
  area: string
  address: string
  x: number
  y: number
  avgPrice: number
  sold: number
  comments: number
  score: number
  openHours: string
  distance?: number
}

export interface Voucher {
  id: string
  shopId: string
  title: string
  subTitle: string
  rules: string
  payValue: number
  actualValue: number
  type: number
  status: number
  stock?: number
  beginTime?: string
  endTime?: string
}

export interface Blog {
  id: string
  shopId: string
  userId: string
  title: string
  images: string
  content: string
  liked: number
  comments: number
  name?: string
  icon?: string
  isLike?: boolean
  createTime?: string
}

export interface BlogComment {
  id: string
  userId: string
  blogId: string
  parentId: string
  answerId: string
  content: string
  liked: number
  name?: string
  icon?: string
  createTime?: string
}