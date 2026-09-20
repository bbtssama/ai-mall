<template>
  <div class="cashier">
    <div class="card">
      <div class="head">
        <span class="badge">模拟收银台</span>
        <span class="sub">MOCK 渠道 · 本地支付，无真实资金流动</span>
      </div>

      <div v-if="loading" class="loading"><el-skeleton :rows="3" animated /></div>
      <template v-else-if="payment">
        <div class="amount">¥ {{ payment.amount }}</div>
        <div class="row"><span>支付单号</span><b>{{ payment.paymentNo }}</b></div>
        <div class="row"><span>订单号</span><b>{{ payment.orderNo }}</b></div>
        <div class="row"><span>渠道</span><b>{{ payment.channel }}</b></div>
        <div class="row" v-if="countdown > 0"><span>支付剩余时间</span>
          <b class="cd">{{ Math.floor(countdown / 60) }}:{{ String(countdown % 60).padStart(2, '0') }}</b>
        </div>

        <el-button type="primary" size="large" class="pay-btn" :loading="paying" @click="pay">
          确认支付（模拟）
        </el-button>
        <div class="tip">
          点击后由后端构造<b>带签名的回调</b>走真实验签/幂等/状态机链路——
          只有"第三方不存在"是模拟的。
        </div>
      </template>
      <el-empty v-else description="支付单不存在或已过期" />

      <div class="back"><el-button text @click="$router.push('/orders')">返回订单</el-button></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { payApi } from '../api'

const route = useRoute()
const payment = ref(null)
const loading = ref(true)
const paying = ref(false)
const countdown = ref(0)
let timer = null

async function load () {
  try {
    payment.value = await payApi.detail(route.query.paymentNo)
    if (payment.value.expireTime) {
      const remain = new Date(payment.value.expireTime.replace(' ', 'T')).getTime() - Date.now()
      countdown.value = Math.max(0, Math.floor(remain / 1000))
      timer = setInterval(() => {
        countdown.value = Math.max(0, countdown.value - 1)
        if (countdown.value === 0) clearInterval(timer)
      }, 1000)
    }
  } finally { loading.value = false }
}

async function pay () {
  paying.value = true
  try {
    await payApi.mockPay(payment.value.paymentNo)
    ElMessage.success('支付成功')
    // 跳回订单页（真实场景：收银台成功页 → "查看订单"）
    setTimeout(() => { window.location.href = '/orders' }, 600)
  } finally { paying.value = false }
}

onMounted(load)
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.cashier { min-height: 70vh; display: flex; align-items: flex-start; justify-content: center; padding: 40px 16px; }
.card { width: 420px; background: var(--el-bg-color); border-radius: 14px; padding: 24px; box-shadow: 0 4px 20px rgba(0,0,0,.08); }
.head { display: flex; flex-direction: column; gap: 4px; margin-bottom: 16px; }
.badge { font-weight: 700; font-size: 16px; }
.sub { font-size: 12px; color: var(--el-text-color-secondary); }
.amount { font-size: 34px; font-weight: 700; text-align: center; margin: 12px 0 18px; color: var(--el-color-primary); }
.row { display: flex; justify-content: space-between; font-size: 13px; padding: 7px 0; border-bottom: 1px dashed var(--el-border-color-lighter); }
.row span { color: var(--el-text-color-secondary); }
.cd { color: var(--el-color-danger); }
.pay-btn { width: 100%; margin-top: 20px; }
.tip { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.7; margin-top: 12px; }
.back { text-align: center; margin-top: 8px; }

/* =====================================================================
   移动端适配（≤ 768px）
   收银台缩为「单列 + 全宽」卡片：金额字号略降仍突出重点，单号可折行不溢出，
   确认支付按钮全宽抬高到 48px 便于拇指点按。
   ===================================================================== */
@media (max-width: 768px) {
  .cashier { min-height: auto; padding: 12px 0 16px; align-items: stretch; }
  .card { width: 100%; padding: 16px; border-radius: var(--radius-lg); }

  .badge { font-size: 15px; }
  .sub { line-height: 1.5; }

  .amount { font-size: 28px; margin: 10px 0 14px; }

  /* 支付单号/订单号较长：给 b 留出收缩空间并允许折行，避免撑破卡片 */
  .row { gap: 12px; padding: 8px 0; }
  .row b { min-width: 0; text-align: right; overflow-wrap: anywhere; }
  .cd { font-size: 15px; font-variant-numeric: tabular-nums; }

  .pay-btn { min-height: 48px; font-size: 16px; margin-top: 16px; }
  .tip { line-height: 1.6; }
  .back .el-button { width: 100%; min-height: 40px; }
}
</style>
