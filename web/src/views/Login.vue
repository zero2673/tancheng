<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'

const phone = ref('')
const code = ref('')
const sending = ref(false)
const loading = ref(false)
const countdown = ref(0)
let timer: number | undefined

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

function phoneValid() {
  return /^1[3-9]\d{9}$/.test(phone.value)
}

async function handleSendCode() {
  if (!phoneValid()) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    await userApi.sendCode(phone.value)
    ElMessage.success('验证码已发送（开发环境在后端日志里看）')
    countdown.value = 60
    timer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && timer) {
        window.clearInterval(timer)
        timer = undefined
      }
    }, 1000)
  } catch {
    // 拦截器已提示
  } finally {
    sending.value = false
  }
}

async function handleLogin() {
  if (!phoneValid()) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (!code.value) {
    ElMessage.warning('请输入验证码')
    return
  }
  loading.value = true
  try {
    const res = await userApi.login({ phone: phone.value, code: code.value })
    userStore.saveToken(String(res.data ?? ''))
    await userStore.loadProfile(true)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="page login-page">
    <div class="hero">
      <h1>探城</h1>
      <p>发现身边值得去的店，抢到划算的券</p>
    </div>

    <el-form class="login-form" @submit.prevent>
      <el-form-item>
        <el-input v-model="phone" size="large" maxlength="11" placeholder="请输入手机号" clearable />
      </el-form-item>
      <el-form-item>
        <div class="code-row">
          <el-input v-model="code" size="large" maxlength="6" placeholder="请输入验证码" />
          <el-button
            class="code-btn"
            size="large"
            :loading="sending"
            :disabled="countdown > 0"
            @click="handleSendCode"
          >
            {{ countdown > 0 ? countdown + 's' : '获取验证码' }}
          </el-button>
        </div>
      </el-form-item>
      <el-button class="submit" type="primary" size="large" :loading="loading" @click="handleLogin">
        登录 / 注册
      </el-button>
    </el-form>

    <p class="tip">开发环境：验证码打印在后端控制台日志里</p>
  </div>
</template>

<style scoped>
.login-page {
  padding: 48px 24px;
  background: #fff;
}

.hero h1 {
  margin: 0;
  font-size: 32px;
  letter-spacing: 2px;
  color: var(--brand);
}

.hero p {
  margin: 8px 0 32px;
  color: var(--text-sub);
  font-size: 13px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.code-btn {
  flex: none;
  width: 116px;
}

.submit {
  width: 100%;
  margin-top: 8px;
  background: var(--brand);
  border-color: var(--brand);
}

.tip {
  margin-top: 24px;
  text-align: center;
  color: var(--text-sub);
  font-size: 12px;
}
</style>