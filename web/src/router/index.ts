import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/request'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'shop-list', component: () => import('@/views/ShopList.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/Login.vue') },
    { path: '/shop/:id', name: 'shop-detail', component: () => import('@/views/ShopDetail.vue') },
  ],
})

// 需要登录的页面统一在这里拦（新页面加 meta.requiresAuth 即可）
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !getToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router