<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { shopApi } from '@/api/shop'
import { voucherApi } from '@/api/voucher'
import { subscribeSeckillResult } from '@/utils/sse'
import type { Shop, Voucher } from '@/types'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const shopId = computed(() => String(route.params.id))
const shop = ref<Shop | null>(null)
const vouchers = ref<Voucher[]>([])
const loading = ref(false)
const seckillingId = ref('')

async function load() {
  loading.value = true
  try {
    const [shopRes, voucherRes] = await Promise.all([
      shopApi.detail(shopId.value),
      voucherApi.listByShop(shopId.value),
    ])
    shop.value = (shopRes.data as Shop) ?? null
    vouchers.value = (voucherRes.data as Voucher[]) || []
  } finally {
    loading.value = false
  }
}

function firstImage(images?: string) {
  return (images || '').split(',')[0] || ''
}

function goBack() {
  router.back()
}

async function handleSeckill(voucher: Voucher) {
  if (!userStore.isLogin) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  seckillingId.value = voucher.id
  try {
    const res = await voucherApi.seckill(voucher.id)
    const orderId = String(res.data ?? '')
    ElMessage.info('订单处理中，结果马上推送…')
    // 下单接口只返回订单号，真正的结果由后端 Stream 消费完成后通过 SSE 推送
    await subscribeSeckillResult(
      orderId,
      (result) => {
        if (result.success) {
          ElMessageBox.alert(`订单号 ${orderId}`, '秒杀成功', { confirmButtonText: '知道了' })
        } else {
          ElMessage.error(result.message || '下单失败')
        }
      },
      () => ElMessage.error('结果推送失败，请稍后在订单里查看'),
    )
  } catch {
    // 拦截器已提示（库存不足 / 重复下单 / 限流 都会走到这里）
  } finally {
    seckillingId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="navbar">
      <button class="back" @click="goBack">←</button>
      <span class="title">店铺详情</span>
    </header>

    <template v-if="shop">
      <div class="cover">
        <img v-if="firstImage(shop.images)" :src="firstImage(shop.images)" :alt="shop.name" />
      </div>

      <section class="shop-info">
        <h2>{{ shop.name }}</h2>
        <p class="meta">
          <span class="score">{{ shop.score }}分</span>
          <span>已售 {{ shop.sold }}</span>
          <span>￥{{ shop.avgPrice }}/人</span>
        </p>
        <p class="meta text-sub">{{ shop.openHours }}</p>
        <p class="meta text-sub">{{ shop.area }} · {{ shop.address }}</p>
      </section>

      <section class="voucher-block">
        <h3 class="block-title">优惠券</h3>
        <div v-if="vouchers.length" class="voucher-list">
          <article v-for="voucher in vouchers" :key="voucher.id" class="voucher-card">
            <div class="voucher-main">
              <p class="voucher-title ellipsis">{{ voucher.title }}</p>
              <p class="voucher-sub ellipsis">{{ voucher.subTitle }}</p>
              <p class="voucher-price">
                <span class="pay">￥{{ (voucher.payValue / 100).toFixed(2) }}</span>
                <span class="text-sub">原价 ￥{{ (voucher.actualValue / 100).toFixed(2) }}</span>
                <span v-if="voucher.stock !== undefined" class="text-sub">剩余 {{ voucher.stock }}</span>
              </p>
            </div>
            <el-button
              v-if="voucher.type === 1"
              class="seckill-btn"
              :loading="seckillingId === voucher.id"
              @click="handleSeckill(voucher)"
            >
              立即抢购
            </el-button>
            <el-button v-else class="seckill-btn" disabled>到店核销</el-button>
          </article>
        </div>
        <p v-else class="empty">这家店暂时没有优惠券</p>
      </section>
    </template>

    <p v-else-if="loading" class="empty">加载中…</p>
    <p v-else class="empty">店铺不存在</p>
  </div>
</template>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 52px;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}

.back {
  border: none;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
  color: var(--text-main);
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.cover {
  width: 100%;
  height: 200px;
  background: #f2f3f5;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.shop-info {
  padding: 14px 16px;
  background: #fff;
}

.shop-info h2 {
  margin: 0 0 8px;
  font-size: 19px;
}

.meta {
  display: flex;
  gap: 12px;
  margin: 4px 0;
  font-size: 12px;
  color: var(--text-main);
}

.score {
  color: var(--brand);
  font-weight: 600;
}

.voucher-block {
  margin-top: 10px;
  padding: 14px 16px;
  background: #fff;
}

.block-title {
  margin: 0 0 12px;
  font-size: 15px;
}

.voucher-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.voucher-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #ffe0d3;
  border-radius: 8px;
  background: #fffaf7;
}

.voucher-main {
  flex: 1;
  min-width: 0;
}

.voucher-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.voucher-sub,
.voucher-price {
  margin: 4px 0 0;
  display: flex;
  gap: 10px;
  align-items: baseline;
  font-size: 12px;
  color: var(--text-sub);
}

.pay {
  color: var(--brand);
  font-size: 16px;
  font-weight: 600;
}

.seckill-btn {
  flex: none;
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
}

.empty {
  padding: 32px 16px;
  text-align: center;
  color: var(--text-sub);
  font-size: 13px;
}
</style>