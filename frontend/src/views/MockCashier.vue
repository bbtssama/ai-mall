<template>
  <div class="cashier">
    <div class="card">
      <div class="head">
        <span class="badge">模拟收银台</span>
        <span class="sub">演示环境 · 不产生真实扣款</span>
      </div>

      <div v-if="loading" class="loading"><el-skeleton :rows="3" animated /></div>
      <template v-else-if="payment">
        <div class="amount">¥{{ formatAmount(payment.amount) }}</div>
        <div class="row"><span>支付单号</span><b>{{ payment.paymentNo }}</b></div>
        <div class="row"><span>订单号</span><b>{{ payment.orderNo }}</b></div>
        <div class="row"><span>支付方式</span><b>{{ channelText }}</b></div>
        <div class="row" v-if="countdown > 0"><span>支付剩余时间</span>
          <b class="cd">{{ Math.floor(countdown / 60) }}:{{ String(countdown % 60).padStart(2, '0') }}</b>
        </div>

        <el-button type="primary" size="large" class="pay-btn" :loading="paying" @click="pay">
          确认支付（模拟）
        </el-button>
        <div class="tip">
          这是演示用的收银台：点击"确认支付"相当于你在第三方付好了款，
          系统会收到支付结果并把订单更新为「已支付」。整个过程不涉及真实资金。
        </div>
      </template>
      <el-empty v-else description="支付单不存在或已过期" />

      <div class="back"><el-button text @click="$router.push('/orders')">返回订单</el-button></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { payApi } from '../api'
import { formatAmount } from '../utils/format'

const route = useRoute()
const router = useRouter()
const payment = ref(null)
const loading = ref(true)
const paying = ref(false)
const countdown = ref(0)
let timer = null

// 渠道给用户看的是"怎么付的钱"，不是后端枚举值
const CHANNEL_TEXT = { MOCK: '模拟支付', WECHAT: '微信支付', ALIPAY: '支付宝' }
const channelText = computed(() => {
  const c = payment.value?.channel
  return c ? (CHANNEL_TEXT[c] || c) : '—'
})

async function load () {
  try {
    payment.value = await payApi.detail(route.query.paymentNo)
    // ★ 支付单可能不存在：接口返回 data=null，此处必须用可选链，
    //   否则整页白屏（Cannot read properties of null (reading 'expireTime')）
    if (payment.value?.expireTime) {
      const remain = new Date(payment.value.expireTime.replace(' ', 'T')).getTime() - Date.now()
      countdown.value = Math.max(0, Math.floor(remain / 1000))
      timer = setInterval(() => {
        countdown.value = Math.max(0, countdown.value - 1)
        if (countdown.value === 0) clearInterval(timer)
      }, 1000)
    }
  } catch (e) {
    // 此前只有 finally：请求被拒时错误无人接管，页面停在 loading 态
    payment.value = null
    ElMessage.error('支付单加载失败，请返回订单页重试')
  } finally { loading.value = false }
}

async function pay () {
  paying.value = true
  try {
    await payApi.mockPay(payment.value.paymentNo)
    // 结果提示只留「结果 + 下一步」：单号属于订单详情里能随时翻到的信息
    ElMessage({
      type: 'success',
      duration: 2600,
      message: h('span', { style: 'display:inline-flex;align-items:center;gap:12px' }, [
        h('span', '支付成功'),
        h('a', {
          style: 'color:var(--clr-primary);font-weight:600;cursor:pointer',
          onClick: (e) => { e.preventDefault(); router.push('/orders') }
        }, '查看订单')
      ])
    })
    // 真实场景：收银台成功页 → "查看订单"
    setTimeout(() => { router.push('/orders') }, 800)
  } catch (e) {
    ElMessage.error('支付失败，请稍后重试')
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
.amount {
  font-size: 34px; font-weight: 700; text-align: center; margin: 12px 0 18px;
  color: var(--el-color-primary);
  font-variant-numeric: tabular-nums;   /* 等宽数字：金额变化时不左右抖动 */
}
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
