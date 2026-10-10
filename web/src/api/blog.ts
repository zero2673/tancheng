import { http } from '@/utils/request'
import type { Blog, BlogComment, UserDTO } from '@/types'

export const blogApi = {
  hot: (current = 1) => http.get<Blog[]>('/blog/hot', { current }),
  detail: (id: string) => http.get<Blog>('/blog/' + id),
  mine: (current = 1) => http.get<Blog[]>('/blog/of/me', { current }),
  ofUser: (userId: string, current = 1) => http.get<Blog[]>('/blog/of/user', { userId, current }),
  ofFollow: (params: { lastId: string; offset: number }) => http.get<Blog[]>('/blog/of/follow', params),
  like: (id: string) => http.put<boolean>('/blog/like/' + id),
  likes: (id: string) => http.get<UserDTO[]>('/blog/likes/' + id),
  save: (data: { shopId: string; title: string; images: string; content: string }) =>
    http.post<string>('/blog', data),

  comments: (blogId: string, current = 1) => http.get<BlogComment[]>('/blog-comments/of/blog/' + blogId, { current }),
  addComment: (data: { blogId: string; content: string; parentId?: string }) =>
    http.post<string>('/blog-comments', data),
  deleteComment: (id: string) => http.delete('/blog-comments/' + id),
}