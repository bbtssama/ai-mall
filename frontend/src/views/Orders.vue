<template>
  <div>
    <h2 class="page-title">我的订单</h2>

    <!-- 状态筛选 tab -->
    <div class="tabs-wrap mall-card">
      <span v-for="t in tabs" :key="t.value" class="tab"
            :class="{ active: activeStatus === t.value }" @click="switchTab(t.value)">
        {{ t.label }}
        <span v-if="countOf(t.value)" class="tab-count">{{ countOf(t.value) }}</span>
      </span>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!pagedOrders.length && !loading" :description="emptyDesc">
        <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
      </el-empty>

      <div v-for="o in pagedOrders" :key="o.id" class="order-card mall-card">
        <div class="order-head">
          <span class="order-no">订单号：{{ o.orderNo }}</span>
          <span class="order-time">{{ fmtTime(o.createdAt) }}</span>
          <el-tag :type="statusTag(o.status)" size="small" class="status-tag">{{ statusText(o.status) }}</el-tag>
        </div>
        <div class="order-body" @click="showDetail(o)">
          <div class="order-amount">
            <span class="amount">¥{{ o.totalAmount }}</span>
            <span class="receiver">收货：{{ o.receiverName }} {{ o.receiverPhone }}</span>
          </div>
        </div>
        <div class="order-foot">
          <el-button v-if="o.status === 'PENDING_PAY'" size="small" type="danger" plain @click="cancel(o)">
            取消订单
          </el-button>
          <el-button size="small" @click="showDetail(o)">查看明细</el-button>
        </div>
      </div>

      <div class="pager" v-if="pagedOrders.length && pages > 1">
        <el-pagination background layout="prev, pager, next"
                       :total="filteredOrders.length" :page-size="pageSize"
                       v-model:current-page="page" />
      </div>
    </div>

    <!-- 订单明细 Drawer -->
    <el-drawer v-model="detailVisible" title="订单明细" size="420px">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ fmtTime(current.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="总金额">¥{{ current.totalAmount }}</el-descriptions-item>
          <el-descriptions-item label="收货人">{{ current.receiverName }} {{ current.receiverPhone }}</el-descriptions-item>
          <el-descriptions-item label="地址">{{ current.receiverAddress }}</el-descriptions-item>
        </el-descriptions>

        <div class="items-title">商品明细（{{ (current.items || []).length }}）</div>
        <div v-for="it in current.items" :key="it.id" class="item-row">
          <img :src="itemImg(it)" class="item-thumb" @error="onImgError" />
          <div class="item-info">
            <div class="item-name">{{ it.productName }}</div>
            <div class="item-sku">{{ it.skuName }}</div>
          </div>
          <div class="item-right">
            <div class="item-price">¥{{ it.price }}</div>
            <div class="item-qty">×{{ it.quantity }}</div>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '../api'

const STATUS_TEXT = {
  PENDING_PAY: '待支付', PAID: '已支付', SHIPPED: '已发货',
  COMPLETED: '已完成', CANCELLED: '已取消'
}
const STATUS_TAG = {
  PENDING_PAY: 'warning', PAID: 'success', SHIPPED: 'primary',
  COMPLETED: 'info', CANCELLED: 'info'
}

const tabs = [
  { value: '', label: '全部' },
  { value: 'PENDING_PAY', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]

const allOrders = ref([])
const loading = ref(false)
const activeStatus = ref('')
const page = ref(1)
const pageSize = 8
const detailVisible = ref(false)
const current = ref(null)

// V1 订单量小：一次拉取全量(上限100)，前端按状态过滤 + 分页
const filteredOrders = computed(() =>
  activeStatus.value ? allOrders.value.filter(o => o.status === activeStatus.value) : allOrders.value
)
const pagedOrders = computed(() => filteredOrders.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const pages = computed(() => Math.max(1, Math.ceil(filteredOrders.value.length / pageSize)))
const emptyDesc = computed(() => activeStatus.value ? `暂无「${statusText(activeStatus.value)}」订单` : '还没有订单')

function countOf(v) {
  if (!v) return allOrders.value.length
  return allOrders.value.filter(o => o.status === v).length
}

async function load() {
  loading.value = true
  try {
    // 分页固定取前 N 页；V1 订单少，一次拉 100 条足够覆盖
    const data = await orderApi.page({ page: 1, pageSize: 100 })
    allOrders.value = data.records || []
  } finally {
    loading.value = false
  }
}

function switchTab(v) {
  activeStatus.value = v
  page.value = 1
}

async function cancel(o) {
  await ElMessageBox.confirm('确定取消该订单吗？库存将自动回补', '提示', { type: 'warning' })
  await orderApi.cancel(o.id)
  ElMessage.success('订单已取消')
  load()
}

async function showDetail(o) {
  current.value = await orderApi.detail(o.id)
  detailVisible.value = true
}

// 商品缩略图：订单明细无图字段，用占位（V1 简化）
function itemImg() { return 'https://picsum.photos/seed/order/80/80' }

function statusText(s) { return STATUS_TEXT[s] || s }
function statusTag(s) { return STATUS_TAG[s] || 'info' }
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }
function onImgError(e) { e.target.src = 'https://picsum.photos/seed/fallback/80/80' }

onMounted(load)
</script>

<style scoped>
.page-title { font-size: 20px; font-weight: 700; color: var(--clr-text); margin: 0 0 14px; }

/* 状态 tab */
.tabs-wrap { display: flex; padding: 10px 16px; margin-bottom: 16px; overflow-x: auto; }
.tab {
  padding: 8px 18px; border-radius: var(--radius-full); cursor: pointer; font-size: 14px;
  color: var(--clr-text-2); white-space: nowrap; transition: all .15s;
  display: flex; align-items: center; gap: 6px;
}
.tab:hover { color: var(--clr-primary); }
.tab.active { background: var(--clr-primary); color: #fff; }
.tab-count { font-size: 12px; opacity: .8; }

/* 订单卡片（紧凑） */
.order-card { padding: 14px 18px; margin-bottom: 12px; }
.order-head { display: flex; align-items: center; gap: 12px; }
.order-no { color: var(--clr-text-3); font-size: 13px; flex: 1; }
.order-time { color: var(--clr-text-4); font-size: 12px; }
.status-tag { }
.order-body { padding: 12px 0 8px; cursor: pointer; display: flex; align-items: center; }
.amount { color: var(--clr-danger); font-size: 20px; font-weight: 800; margin-right: 16px; }
.receiver { color: var(--clr-text-3); font-size: 13px; }
.order-foot { display: flex; justify-content: flex-end; gap: 8px; border-top: 1px solid var(--clr-border-light); padding-top: 10px; }

.pager { display: flex; justify-content: center; margin-top: 16px; }

/* 明细 */
.items-title { margin: 16px 0 8px; font-weight: 600; color: var(--clr-text); }
.item-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--clr-border-light); }
.item-thumb { width: 48px; height: 48px; border-radius: 6px; object-fit: cover; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-size: 14px; color: var(--clr-text); }
.item-sku { font-size: 12px; color: var(--clr-text-3); margin-top: 2px; }
.item-right { text-align: right; }
.item-price { color: var(--clr-danger); font-weight: 600; }
.item-qty { color: var(--clr-text-3); font-size: 12px; }
</style>