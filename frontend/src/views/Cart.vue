<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="cart-head">
          <span>购物车</span>
          <el-button text type="danger" @click="clearCart">清空购物车</el-button>
        </div>
      </template>

      <el-table :data="items" v-loading="loading" :row-class-name="rowClass">
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="goods-cell">
              <img :src="row.mainImg" class="thumb" :class="{ off: row.productStatus !== 1 }" @error="onImgError" />
              <div class="goods-info">
                <div class="goods-name">
                  {{ row.productName }}
                  <el-tag v-if="row.productStatus !== 1" type="info" size="small" effect="plain">已下架</el-tag>
                </div>
                <div class="goods-sku">{{ row.skuName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="数量" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="1" :max="99" size="small"
                             :disabled="row.productStatus !== 1" @change="updateQty(row)" />
          </template>
        </el-table-column>
        <el-table-column label="小计" width="120">
          <template #default="{ row }">
            <span class="subtotal" :class="{ off: row.productStatus !== 1 }">¥{{ subtotalOf(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="cart-footer" v-if="items.length">
        <div class="total">
          <template v-if="offShelfCount > 0">
            <span class="off-tip">⚠ {{ offShelfCount }} 件商品已下架，不可购买（请删除后结算）</span>
          </template>
          <template v-else>
            共 <b>{{ buyableCount }}</b> 件，合计：
          </template>
          <span class="total-price">¥{{ buyableAmount }}</span>
        </div>
        <el-button type="danger" size="large" :disabled="buyableCount === 0" @click="checkoutVisible = true">去结算</el-button>
      </div>
      <el-empty v-else description="购物车空空如也">
        <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
      </el-empty>
    </el-card>

    <!-- 结算弹窗 -->
    <el-dialog v-model="checkoutVisible" title="确认订单" width="480px">
      <el-form label-width="70px">
        <el-form-item label="收货人"><el-input v-model="checkout.receiverName" placeholder="收货人姓名" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="checkout.receiverPhone" placeholder="手机号" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="checkout.receiverAddress" placeholder="收货地址" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkoutVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitOrder">提交订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cartApi, orderApi } from '../api'

const router = useRouter()
const items = ref([])
const loading = ref(false)
const checkoutVisible = ref(false)
const submitting = ref(false)
const checkout = ref({ receiverName: '', receiverPhone: '', receiverAddress: '' })

// 下架判定：productStatus == 1 才能购买
const isSellable = (i) => i.productStatus === 1

// 可购买条目（过滤下架）
const sellableItems = computed(() => items.value.filter(isSellable))
const buyableCount = computed(() => sellableItems.value.reduce((s, i) => s + i.quantity, 0))
const buyableAmount = computed(() => sellableItems.value.reduce((s, i) => s + subtotalOf(i), 0))
const offShelfCount = computed(() => items.value.length - sellableItems.value.length)

function subtotalOf(i) { return Number(i.price) * Number(i.quantity) || 0 }

function rowClass({ row }) {
  return row.productStatus === 1 ? '' : 'row-off'
}

async function load() {
  loading.value = true
  try { items.value = await cartApi.list() || [] } finally { loading.value = false }
}

async function updateQty(row) {
  if (row.productStatus !== 1) return
  try {
    await cartApi.update(row.id, { quantity: row.quantity })
  } catch (e) {
    load()
  }
}

async function remove(row) {
  await cartApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

async function clearCart() {
  await ElMessageBox.confirm('确定清空购物车吗？', '提示', { type: 'warning' })
  await cartApi.clear()
  load()
}

async function submitOrder() {
  if (!checkout.value.receiverName || !checkout.value.receiverPhone || !checkout.value.receiverAddress) {
    ElMessage.warning('请填写完整的收货信息')
    return
  }
  // 下架商品不参与下单
  if (sellableItems.value.length === 0) {
    ElMessage.warning('没有可购买的商品')
    return
  }
  submitting.value = true
  try {
    const order = await orderApi.create({
      items: sellableItems.value.map(i => ({ skuId: i.skuId, quantity: i.quantity })),
      ...checkout.value
    })
    ElMessage.success(`下单成功：${order.orderNo}`)
    checkoutVisible.value = false
    router.push('/orders')
  } finally {
    submitting.value = false
  }
}

function onImgError(e) { e.target.src = 'https://picsum.photos/seed/fallback/120/120' }

onMounted(load)
</script>

<style scoped>
.cart-head { display: flex; justify-content: space-between; align-items: center; }
.goods-cell { display: flex; align-items: center; gap: 10px; }
.thumb { width: 56px; height: 56px; border-radius: 6px; object-fit: cover; filter: grayscale(1); opacity: 0.6; }
.thumb.off { }
.goods-info { min-width: 0; }
.goods-name { font-weight: 600; display: flex; align-items: center; gap: 6px; }
.goods-sku { color: #999; font-size: 12px; margin-top: 2px; }
.subtotal { color: #e8562c; font-weight: 700; }
.subtotal.off { color: #bbb; }
.cart-footer { display: flex; justify-content: flex-end; align-items: center; gap: 16px; margin-top: 16px; flex-wrap: wrap; }
.off-tip { color: #e6a23c; font-size: 13px; margin-right: 8px; }
.total-price { color: #e8562c; font-size: 22px; font-weight: 700; }
/* Element Plus 行样式：下架整行置灰 */
.el-table :deep(.row-off) {
  background: #fafafa;
  color: #888;
}
.el-table :deep(.row-off td) { background: #fafafa; }
</style>