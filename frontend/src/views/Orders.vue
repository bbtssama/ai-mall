<template>
  <div>
    <el-card shadow="never" v-loading="loading">
      <template #header>我的订单</template>

      <el-empty v-if="!orders.length && !loading" description="还没有订单">
        <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
      </el-empty>

      <div v-for="o in orders" :key="o.id" class="order-card">
        <div class="order-head">
          <span class="order-no">订单号：{{ o.orderNo }}</span>
          <el-tag :type="statusTag(o.status)" size="small">{{ statusText(o.status) }}</el-tag>
        </div>
        <div class="order-body" @click="showDetail(o)">
          <div class="order-amount">
            <span class="amount">¥{{ o.totalAmount }}</span>
            <span class="time">{{ fmtTime(o.createdAt) }}</span>
          </div>
          <div class="receiver">收货：{{ o.receiverName }} {{ o.receiverPhone }}</div>
        </div>
        <div class="order-foot">
          <el-button v-if="o.status === 'PENDING_PAY'" size="small" type="danger" plain @click="cancel(o)">
            取消订单
          </el-button>
          <el-button size="small" @click="showDetail(o)">查看明细</el-button>
        </div>
      </div>

      <div class="pager" v-if="total > 0">
        <el-pagination background layout="prev, pager, next" :total="total" :page-size="query.pageSize"
                       v-model:current-page="query.page" @current-change="load" />
      </div>
    </el-card>

    <!-- 订单明细 -->
    <el-dialog v-model="detailVisible" title="订单明细" width="600px">
      <template v-if="current">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ fmtTime(current.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="总金额">¥{{ current.totalAmount }}</el-descriptions-item>
          <el-descriptions-item label="收货人">{{ current.receiverName }} {{ current.receiverPhone }}</el-descriptions-item>
          <el-descriptions-item label="地址">{{ current.receiverAddress }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="current.items" size="small" style="margin-top:12px">
          <el-table-column prop="productName" label="商品" min-width="160" />
          <el-table-column prop="skuName" label="规格" width="120" />
          <el-table-column prop="price" label="单价" width="90" />
          <el-table-column prop="quantity" label="数量" width="70" />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '../api'

const orders = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, pageSize: 10 })
const detailVisible = ref(false)
const current = ref(null)

const STATUS_TEXT = {
  PENDING_PAY: '待支付', PAID: '已支付', SHIPPED: '已发货',
  COMPLETED: '已完成', CANCELLED: '已取消'
}
const STATUS_TAG = {
  PENDING_PAY: 'warning', PAID: 'success', SHIPPED: 'primary',
  COMPLETED: 'info', CANCELLED: 'info'
}

async function load() {
  loading.value = true
  try {
    const data = await orderApi.page(query)
    orders.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
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

function statusText(s) { return STATUS_TEXT[s] || s }
function statusTag(s) { return STATUS_TAG[s] || 'info' }
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }

onMounted(load)
</script>

<style scoped>
.order-card {
  border: 1px solid #eee; border-radius: 8px; padding: 14px 16px; margin-bottom: 12px;
}
.order-head { display: flex; justify-content: space-between; align-items: center; }
.order-no { color: #666; font-size: 13px; }
.order-body { margin: 10px 0; cursor: pointer; }
.amount { color: #e8562c; font-size: 20px; font-weight: 700; margin-right: 12px; }
.time { color: #999; font-size: 12px; }
.receiver { color: #666; font-size: 13px; margin-top: 6px; }
.order-foot { display: flex; justify-content: flex-end; gap: 8px; }
.pager { display: flex; justify-content: center; margin-top: 16px; }
</style>