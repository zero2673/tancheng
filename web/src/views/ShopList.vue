<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { shopApi } from '@/api/shop'
import type { Shop, ShopType } from '@/types'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const types = ref<ShopType[]>([])
const activeType = ref<number | null>(null)
const shops = ref<Shop[]>([])
const current = ref(1)
const loading = ref(false)
const finished = ref(false)

const PAGE_SIZE = 5

async function loadTypes() {
  const res = await shopApi.types()
  types.value = (res.data as ShopType[]) || []
  if (types.value.length && activeType.value === null) {
    activeType.value = types.value[0].id
  }
}

async function loadShops(reset = false) {
  if (loading.value) return
  if (reset) {
    current.value = 1
    finished.value = false
    shops.value = []
  }
  if (finished.value || activeType.value === null) return

  loading.value = true
  try {
    const res = await shopApi.byType({ typeId: activeType.value, current: current.value })
    const list = (res.data as Shop[]) || []
    shops.value.push(...list)
    current.value += 1
    if (list.length < PAGE_SIZE) finished.value = true
  } catch {
    finished.value = true
  } finally {
    loading.value = false
  }
}

function switchType(id: number) {
  if (activeType.value === id) return
  activeType.value = id
  loadShops(true)
}

function openShop(id: string) {
  router.push('/shop/' + id)
}

function goLogin() {
  router.push({ name: 'login', query: { redirect: '/' } })
}

function firstImage(images: string) {
  return (images || '').split(',')[0] || ''
}

function handleScroll() {
  const bottom = document.documentElement.scrollHeight - window.innerHeight - window.scrollY
  if (bottom < 240) loadShops()
}

onMounted(async () => {
  await loadTypes()
  await loadShops(true)
  window.addEventListener('scroll', handleScroll)
  if (userStore.isLogin) {
    userStore.loadProfile().catch(() => undefined)
  }
})

onUnmounted(() => window.removeEventListener('scroll', handleScroll))
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div class="brand">探城</div>
      <div class="user" @click="userStore.isLogin ? undefined : goLogin()">
        <template v-if="userStore.isLogin">
          <img v-if="userStore.profile?.icon" class="avatar" :src="userStore.profile.icon" alt="头像" />
          <span class="nickname">{{ userStore.profile?.nickName || '我' }}</span>
        </template>
        <template v-else>
          <span class="nickname">登录</span>
        </template>
      </div>
    </header>

    <nav class="type-tabs">
      <button
        v-for="item in types"
        :key="item.id"
        class="type-tab"
        :class="{ active: item.id === activeType }"
        @click="switchType(item.id)"
      >
        {{ item.name }}
      </button>
    </nav>

    <section class="shop-list">
      <article v-for="shop in shops" :key="shop.id" class="shop-card" @click="openShop(shop.id)">
        <div class="thumb">
          <img v-if="firstImage(shop.images)" :src="firstImage(shop.images)" :alt="shop.name" loading="lazy" />
          <div v-else class="thumb-fallback">暂无图片</div>
        </div>
        <div class="info">
          <h3 class="ellipsis">{{ shop.name }}</h3>
          <p class="meta">
            <span>{{ shop.area || shop.address }}</span>
            <span v-if="shop.distance">· {{ shop.distance }}m</span>
          </p>
          <p class="meta">
            <span class="price">￥{{ shop.avgPrice }}</span>
            <span class="text-sub">已售 {{ shop.sold }} · 评分 {{ shop.score }}</span>
          </p>
        </div>
      </article>
    </section>

    <div class="list-footer">
      <span v-if="loading">加载中…</span>
      <span v-else-if="finished && shops.length">没有更多了</span>
      <span v-else-if="!shops.length && !loading">这个分类下暂时没有店铺</span>
    </div>
  </div>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}

.brand {
  font-size: 20px;
  font-weight: 600;
  color: var(--brand);
  letter-spacing: 1px;
}

.user {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-sub);
}

.avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  object-fit: cover;
}

.type-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 10px 12px;
  background: #fff;
  scrollbar-width: none;
}

.type-tabs::-webkit-scrollbar {
  display: none;
}

.type-tab {
  flex: none;
  padding: 6px 14px;
  border: none;
  border-radius: 16px;
  background: #f4f4f5;
  color: var(--text-main);
  font-size: 13px;
  cursor: pointer;
}

.type-tab.active {
  background: var(--brand);
  color: #fff;
}

.shop-list {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.shop-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 8px;
  cursor: pointer;
}

.thumb {
  flex: none;
  width: 96px;
  height: 96px;
  border-radius: 6px;
  overflow: hidden;
  background: #f2f3f5;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-sub);
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.info h3 {
  margin: 0;
  font-size: 15px;
}

.meta {
  margin: 4px 0 0;
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--text-sub);
}

.price {
  color: var(--brand);
  font-size: 14px;
  font-weight: 600;
}

.list-footer {
  padding: 16px 0 24px;
  text-align: center;
  font-size: 12px;
  color: var(--text-sub);
}
</style>