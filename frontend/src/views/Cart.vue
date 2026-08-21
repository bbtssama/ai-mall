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
        <!-- 勾选列：非法（下架/超库存）禁勾 -->
        <el-table-column width="46" align="center">
          <template #default="{ row }">
            <el-checkbox :model-value="checkedIds.has(row.id)" :disabled="!isSellable(row)"
                         @change="toggleCheck(row, $event)" />
          </template>
        </el-table-column>

        <el-table-column label="商品" min-width="250">
          <template #default="{ row }">
            <div class="goods-cell">
              <img :src="row.mainImg" class="thumb" :class="{ off: row.productStatus !== 1 }" @error="onImgError" />
              <div class="goods-info">
                <div class="goods-name">
                  {{ row.productName }}
                  <el-tag v-if="row.productStatus !== 1" type="info" size="small" effect="plain">已下架</el-tag>
                  <el-tag v-else-if="row.skuStock === 0" type="danger" size="small" effect="plain">售罄</el-tag>
                </div>
                <div class="goods-sku">{{ row.skuName }}</div>
                <div v-if="row.productStatus === 1 && row.outOfStock" class="stock-warn">
                  ⚠ 库存不足（最多 {{ row.maxBuyable }} 件）
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="单价" width="110">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>

        <el-table-column label="数量" width="150">
          <template #default="{ row }">
            <div class="qty-cell">
              <el-input-number v-model="row.quantity" :min="1"
                               :max="row.productStatus === 1 ? Math.max(row.maxBuyable, 1) : 99"
                               :disabled="row.productStatus !== 1 || row.skuStock === 0"
                               size="small" @change="updateQty(row)" />
              <!-- 仅"超库存但仍有货"时提供修正按钮；售罄(maxBuyable=0)设 0 无意义，不显示 -->
              <el-button v-if="row.productStatus === 1 && row.outOfStock && row.maxBuyable > 0"
                         size="small" type="warning" plain @click="fixQty(row)">设为 {{ row.maxBuyable }}</el-button>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="小计" width="110">
          <template #default="{ row }">
            <span class="subtotal" :class="{ off: !isSellable(row) }">¥{{ subtotalOf(row) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部操作条：全选 + 已选合计 + 结算（只统计勾选的合法项） -->
      <div class="cart-footer" v-if="items.length">
        <div class="total">
          <el-checkbox :model-value="allChecked()" :indeterminate="someChecked()"
                       :disabled="sellableItems.length === 0" @change="toggleAll">全选</el-checkbox>
          <span v-if="checkedCount > 0" class="checked-info">
            已选 <b>{{ checkedCount }}</b> 件，合计：<span class="total-price">¥{{ checkedAmount }}</span>
          </span>
          <span v-else class="empty-check">请勾选要结算的商品</span>
          <!-- 汇总提示 -->
          <span v-if="blockedCount > 0" class="off-tip">
            ⚠ {{ blockedCount }} 件不可购买（已下架/库存不足），已自动跳过
          </span>
        </div>
        <el-button type="danger" size="large" :disabled="selectedItems.length === 0" @click="checkoutVisible = true">
          去结算（{{ selectedItems.length }}）
        </el-button>
      </div>
      <el-empty v-else description="购物车空空如也">
        <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
      </el-empty>
    </el-card>

    <!-- 结算弹窗：列出将下单的勾选商品 -->
    <el-dialog v-model="checkoutVisible" title="确认订单" width="500px">
      <div class="checkout-list">
        <div v-for="i in selectedItems" :key="i.id" class="checkout-row">
          <span class="co-name">{{ i.productName }}（{{ i.skuName }}）</span>
          <span class="co-qty">×{{ i.quantity }}</span>
          <span class="co-price">¥{{ subtotalOf(i) }}</span>
        </div>
      </div>
      <el-divider />
      <el-form label-width="70px">
        <el-form-item label="收货人"><el-input v-model="checkout.receiverName" placeholder="收货人姓名" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="checkout.receiverPhone" placeholder="手机号" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="checkout.receiverAddress" placeholder="收货地址" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkoutVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitOrder">
          提交订单（¥{{ checkedAmount }}）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cartApi, orderApi } from '../api'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const userId = computed(() => auth.user?.id)

const items = ref([])
const loading = ref(false)
const checkoutVisible = ref(false)
const submitting = ref(false)
const checkout = ref({ receiverName: '', receiverPhone: '', receiverAddress: '' })

// 勾选态：本地持久化（按用户隔离），key = cart_checked_{userId}
const checkedIds = ref(new Set())
const storageKey = computed(() => `cart_checked_${userId.value ?? 'anon'}`)

function loadChecked() {
  try {
    const raw = localStorage.getItem(storageKey.value)
    checkedIds.value = new Set(raw ? JSON.parse(raw) : [])
  } catch (e) {
    checkedIds.value = new Set()
  }
}
function persistChecked() {
  localStorage.setItem(storageKey.value, JSON.stringify([...checkedIds.value]))
}

// 合法性：上架 + 数量 <= 库存
const isSellable = (i) => i.productStatus === 1 && !i.outOfStock
const sellableItems = computed(() => items.value.filter(isSellable))
const blockedCount = computed(() => items.value.length - sellableItems.value.length)

// 已勾选且合法的项 = 结算集合
const selectedItems = computed(() => items.value.filter(i => isSellable(i) && checkedIds.value.has(i.id)))
const checkedCount = computed(() => selectedItems.value.reduce((s, i) => s + i.quantity, 0))
const checkedAmount = computed(() => selectedItems.value.reduce((s, i) => s + subtotalOf(i), 0))

function subtotalOf(i) { return Number(i.price) * Number(i.quantity) || 0 }

function allChecked() {
  return sellableItems.value.length > 0 && sellableItems.value.every(i => checkedIds.value.has(i.id))
}
function someChecked() {
  return sellableItems.value.some(i => checkedIds.value.has(i.id))
}
function toggleCheck(row, val) {
  if (val) checkedIds.value.add(row.id)
  else checkedIds.value.delete(row.id)
  persistChecked()
}
function toggleAll() {
  if (allChecked()) {
    sellableItems.value.forEach(i => checkedIds.value.delete(i.id))
  } else {
    sellableItems.value.forEach(i => checkedIds.value.add(i.id))
  }
  persistChecked()
}

function rowClass({ row }) {
  if (row.productStatus !== 1) return 'row-off'
  if (row.outOfStock) return 'row-warn'
  return ''
}

async function load() {
  loading.value = true
  try {
    items.value = await cartApi.list() || []
    loadChecked()
    // 清理勾选中已失效的项（下架/超库存自动取消勾选）
    let dirty = false
    items.value.forEach(i => {
      if (!isSellable(i) && checkedIds.value.has(i.id)) {
        checkedIds.value.delete(i.id)
        dirty = true
      }
    })
    if (dirty) persistChecked()
  } finally {
    loading.value = false
  }
}

async function updateQty(row) {
  if (row.productStatus !== 1 || row.skuStock === 0) return
  try {
    await cartApi.update(row.id, { quantity: row.quantity })
    // 更新后重新拉（后端已按库存封顶，行是否仍合法以服务端为准）
    await load()
  } catch (e) {
    load()
  }
}

// 一键把数量修正到可售库存（超库存引导）。
// 注意：不走 updateQty（其售罄/下架早退会拦截修正）；这里就是要处理超库存非法项，直接调接口。
async function fixQty(row) {
  try {
    // 后端 update 本身会封顶到可售库存，这里按前端 maxBuyable 传即可
    await cartApi.update(row.id, { quantity: row.maxBuyable })
    await load()
    ElMessage.success(`已将数量调整为 ${row.maxBuyable} 件`)
  } catch (e) {
    ElMessage.error('调整失败，请重试')
    load()
  }
}

async function remove(row) {
  await cartApi.remove(row.id)
  checkedIds.value.delete(row.id)
  persistChecked()
  ElMessage.success('已删除')
  load()
}

async function clearCart() {
  await ElMessageBox.confirm('确定清空购物车吗？', '提示', { type: 'warning' })
  await cartApi.clear()
  checkedIds.value.clear()
  persistChecked()
  load()
}

async function submitOrder() {
  if (!checkout.value.receiverName || !checkout.value.receiverPhone || !checkout.value.receiverAddress) {
    ElMessage.warning('请填写完整的收货信息')
    return
  }
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请先勾选要结算的商品')
    return
  }
  submitting.value = true
  try {
    const order = await orderApi.create({
      items: selectedItems.value.map(i => ({ skuId: i.skuId, quantity: i.quantity })),
      ...checkout.value
    })
    ElMessage.success(`下单成功：${order.orderNo}`)
    // 下单后取消勾选项（已结算清车）
    selectedItems.value.forEach(i => checkedIds.value.delete(i.id))
    persistChecked()
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
.thumb { width: 52px; height: 52px; border-radius: 6px; object-fit: cover; opacity: 0.9; }
.goods-info { min-width: 0; }
.goods-name { font-weight: 600; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.goods-sku { color: #999; font-size: 12px; margin-top: 2px; }
.stock-warn { color: #f56c6c; font-size: 12px; margin-top: 3px; }
.qty-cell { display: flex; align-items: center; gap: 6px; }
.subtotal { color: #e8562c; font-weight: 700; }
.subtotal.off { color: #bbb; }
.cart-footer { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-top: 16px; flex-wrap: wrap; }
.total { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.checked-info b { color: #e8562c; }
.total-price { color: #e8562c; font-size: 22px; font-weight: 700; }
.empty-check { color: #999; font-size: 13px; }
.off-tip { color: #e6a23c; font-size: 12px; }
.checkout-list { max-height: 240px; overflow: auto; }
.checkout-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; }
.co-name { flex: 1; }
.co-qty { color: #666; margin: 0 12px; }
.co-price { color: #e8562c; font-weight: 600; }
/* 下架整行置灰、超库存整行浅红提示 */
.el-table :deep(.row-off) { background: #fafafa !important; color: #aaa; }
.el-table :deep(.row-off td) { background: #fafafa !important; }
.el-table :deep(.row-warn) { background: #fff7f7 !important; }
.el-table :deep(.row-warn td) { background: #fff7f7 !important; }
</style>