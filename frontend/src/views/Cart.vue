<template>
  <div>
    <div class="cart-head">
      <h2 class="page-title">购物车</h2>
      <el-button text type="danger" @click="clearCart">清空购物车</el-button>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!items.length && !loading" description="购物车空空如也">
        <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
      </el-empty>

      <!-- 可购商品 -->
      <div v-if="sellableItems.length" class="item-list">
        <div v-for="row in sellableItems" :key="row.id" class="cart-item mall-card">
          <el-checkbox :model-value="checkedIds.has(row.id)" @change="toggleCheck(row, $event)" class="ck" />
          <img :src="row.mainImg" class="thumb" @error="onImgError" @click="goProduct(row)" />
          <div class="goods-info" @click="goProduct(row)">
            <div class="goods-name">{{ row.productName }}</div>
            <div class="goods-sku">{{ row.skuName }}</div>
            <div class="stock-line">库存 {{ row.skuStock }} 件</div>
          </div>
          <div class="col-price">¥{{ row.price }}</div>
          <div class="col-qty">
            <el-input-number v-model="row.quantity" :min="1" :max="99" size="small"
                             @change="updateQty(row)" />
          </div>
          <div class="col-subtotal">¥{{ subtotalOf(row) }}</div>
          <el-button class="del" text type="danger" @click="remove(row)">删除</el-button>
        </div>
      </div>

      <!-- 失效商品（下架/售罄/超库存）分组 -->
      <div v-if="blockedItems.length" class="blocked-group">
        <div class="blocked-title">失效商品（{{ blockedItems.length }}）— 不可购买，可删除或修正数量</div>
        <div v-for="row in blockedItems" :key="row.id" class="cart-item mall-card is-blocked">
          <div class="ck-placeholder" />
          <img :src="row.mainImg" class="thumb blur" @error="onImgError" />
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
          <div class="col-price">¥{{ row.price }}</div>
          <div class="col-qty">
            <span class="qty-text">×{{ row.quantity }}</span>
            <el-button v-if="row.productStatus === 1 && row.outOfStock && row.maxBuyable > 0"
                       size="small" type="warning" plain @click="fixQty(row)">设为 {{ row.maxBuyable }}</el-button>
          </div>
          <div class="col-subtotal off">¥{{ subtotalOf(row) }}</div>
          <el-button class="del" text type="danger" @click="remove(row)">删除</el-button>
        </div>
      </div>

      <!-- 底部吸底结算栏 -->
      <div v-if="items.length" class="settle-bar mall-card">
        <el-checkbox :model-value="allChecked()" :indeterminate="someChecked()"
                     :disabled="sellableItems.length === 0" @change="toggleAll">全选</el-checkbox>
        <div class="settle-tip">
          <span v-if="blockedCount > 0" class="off-tip">⚠ {{ blockedCount }} 件不可购买，已自动跳过</span>
        </div>
        <div class="settle-total">
          <template v-if="checkedCount > 0">
            已选 <b>{{ checkedCount }}</b> 件，合计：
            <span class="total-price">¥{{ checkedAmount }}</span>
          </template>
          <span v-else class="empty-check">请勾选要结算的商品</span>
        </div>
        <el-button type="danger" size="large" :disabled="selectedItems.length === 0"
                   @click="checkoutVisible = true">去结算（{{ selectedItems.length }}）</el-button>
      </div>
    </div>

    <!-- 结算弹窗：商品清单 + 收货地址簿选择 -->
    <el-dialog v-model="checkoutVisible" title="确认订单" width="620px" @open="openCheckout">
      <div class="checkout-list">
        <div v-for="i in selectedItems" :key="i.id" class="checkout-row">
          <span class="co-name">{{ i.productName }}（{{ i.skuName }}）</span>
          <span class="co-qty">×{{ i.quantity }}</span>
          <span class="co-price">¥{{ subtotalOf(i) }}</span>
        </div>
      </div>
      <el-divider />
      <div class="addr-section" v-loading="addressLoading">
        <div class="addr-title">
          选择收货地址
          <el-button link type="primary" size="small" @click="$router.push('/mine/addresses')">管理地址</el-button>
        </div>
        <el-empty v-if="!addresses.length && !addressLoading" description="还没有收货地址" :image-size="50">
          <el-button type="primary" size="small" @click="showAddAddr = true">新增收货地址</el-button>
        </el-empty>
        <template v-else>
          <div class="addr-list">
            <div v-for="a in addresses" :key="a.id" class="addr-card"
                 :class="{ active: selectedAddrId === a.id }" @click="selectedAddrId = a.id">
              <div class="addr-main">
                <span class="addr-receiver">{{ a.receiver }}</span>
                <span class="addr-phone">{{ a.phone }}</span>
                <el-tag v-if="a.isDefault" type="danger" size="small" effect="plain">默认</el-tag>
              </div>
              <div class="addr-detail">{{ a.fullAddress }}</div>
            </div>
            <el-button text type="primary" size="small" @click="showAddAddr = !showAddAddr">＋ 新增地址</el-button>
          </div>
        </template>
        <!-- 新增地址表单：空态 / 有地址 均可展开 -->
        <el-form v-if="showAddAddr" label-width="64px" size="small" class="addr-form">
          <el-form-item label="收货人"><el-input v-model="newAddr.receiver" placeholder="收货人" /></el-form-item>
          <el-form-item label="电话"><el-input v-model="newAddr.phone" placeholder="手机号" /></el-form-item>
          <el-form-item label="省"><el-input v-model="newAddr.province" placeholder="省" /></el-form-item>
          <el-form-item label="市"><el-input v-model="newAddr.city" placeholder="市" /></el-form-item>
          <el-form-item label="详细地址"><el-input v-model="newAddr.detail" placeholder="区 / 街道 / 门牌号" /></el-form-item>
          <el-form-item label="设为默认">
            <el-checkbox v-model="newAddr.isDefault">设为默认收货地址</el-checkbox>
          </el-form-item>
          <el-button type="primary" size="small" :loading="addrSaving" @click="saveNewAddr">保存地址</el-button>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="checkoutVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" :disabled="!selectedAddrId" @click="submitOrder">
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
import { cartApi, orderApi, addressApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'

const router = useRouter()
const auth = useAuthStore()
const cartStore = useCartStore()
const userId = computed(() => auth.user?.id)

const items = ref([])
const loading = ref(false)
const checkoutVisible = ref(false)
const submitting = ref(false)

// 收货地址簿
const addresses = ref([])
const addressLoading = ref(false)
const selectedAddrId = ref(null)
const showAddAddr = ref(false)
const addrSaving = ref(false)
const newAddr = ref({ receiver: '', phone: '', province: '', city: '', detail: '', isDefault: false })

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
const blockedItems = computed(() => items.value.filter(i => !isSellable(i)))
const blockedCount = computed(() => blockedItems.value.length)

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
  // 用户改数若超过可购库存：不提交，恢复原值并提示（京东式“点+到库存即停”）。
  if (row.quantity > row.maxBuyable) {
    ElMessage.warning(`该商品最多可购 ${row.maxBuyable} 件`)
    await load()
    return
  }
  try {
    await cartApi.update(row.id, { quantity: row.quantity })
    await load()
  } catch (e) {
    load()
  }
}

// ---------- 收货地址簿 ----------
async function loadAddresses(selectDefault = true) {
  addressLoading.value = true
  try {
    addresses.value = await addressApi.list() || []
    if (selectDefault) {
      const d = addresses.value.find(a => a.isDefault)
      selectedAddrId.value = (d ? d.id : addresses.value[0]?.id) || null
    }
  } finally {
    addressLoading.value = false
  }
}

async function openCheckout() {
  await loadAddresses(true)
}

async function saveNewAddr() {
  if (!newAddr.value.receiver || !newAddr.value.phone || !newAddr.value.detail) {
    ElMessage.warning('请填写收货人、电话和详细地址')
    return
  }
  addrSaving.value = true
  try {
    const saved = await addressApi.add(newAddr.value)
    await loadAddresses(false)
    selectedAddrId.value = saved.id
    newAddr.value = { receiver: '', phone: '', province: '', city: '', detail: '', isDefault: false }
    showAddAddr.value = false
    ElMessage.success('地址已保存')
  } finally {
    addrSaving.value = false
  }
}

// 一键把数量修正到可售库存（超库存引导）。
async function fixQty(row) {
  try {
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
  cartStore.refresh()
  ElMessage.success('已删除')
  load()
}

async function clearCart() {
  await ElMessageBox.confirm('确定清空购物车吗？', '提示', { type: 'warning' })
  await cartApi.clear()
  checkedIds.value.clear()
  persistChecked()
  cartStore.refresh()
  load()
}

async function submitOrder() {
  const addr = addresses.value.find(a => a.id === selectedAddrId.value)
  if (!addr) {
    ElMessage.warning('请选择收货地址')
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
      receiverName: addr.receiver,
      receiverPhone: addr.phone,
      receiverAddress: addr.fullAddress
    })
    ElMessage.success(`下单成功：${order.orderNo}`)
    selectedItems.value.forEach(i => checkedIds.value.delete(i.id))
    persistChecked()
    cartStore.refresh()
    checkoutVisible.value = false
    router.push('/orders')
  } catch (e) {
    ElMessage.warning('下单未成功，已刷新购物车，请检查库存后重试')
    checkoutVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

function goProduct(row) { router.push(`/product/${row.productId}`) }
function onImgError(e) { e.target.src = 'https://picsum.photos/seed/fallback/120/120' }

onMounted(() => {
  load()
  loadAddresses()
})
</script>

<style scoped>
.cart-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.page-title { font-size: 20px; font-weight: 700; color: var(--clr-text); margin: 0; }

/* 商品行（横向紧凑） */
.item-list, .blocked-group { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; }
.cart-item {
  display: flex; align-items: center; gap: 14px; padding: 14px 16px;
}
.ck { flex: 0 0 auto; }
.ck-placeholder { width: 14px; flex: 0 0 auto; }
.thumb { width: 64px; height: 64px; border-radius: var(--radius-md); object-fit: cover; cursor: pointer; }
.thumb.blur { opacity: .55; filter: grayscale(1); }
.goods-info { flex: 1; min-width: 0; cursor: pointer; }
.goods-name { font-weight: 600; color: var(--clr-text); display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.goods-sku { color: var(--clr-text-3); font-size: 12px; margin-top: 2px; }
.stock-line { color: var(--clr-success); font-size: 12px; margin-top: 2px; }
.stock-warn { color: var(--clr-danger); font-size: 12px; }
.col-price { width: 80px; text-align: right; color: var(--clr-text-2); }
.col-qty { width: 120px; display: flex; align-items: center; gap: 6px; }
.col-qty .qty-text { color: var(--clr-text-2); }
.col-subtotal { width: 100px; text-align: right; font-weight: 700; color: var(--clr-danger); }
.col-subtotal.off { color: var(--clr-text-4); font-weight: 400; }
.del { flex: 0 0 auto; }

/* 失效分组 */
.blocked-group .blocked-title {
  color: var(--clr-text-3); font-size: 13px; margin-bottom: 6px; padding-left: 4px;
}
.cart-item.is-blocked { background: #fafafa; }

/* 底部吸底结算栏 */
.settle-bar {
  display: flex; align-items: center; gap: 18px; padding: 14px 18px;
  position: sticky; bottom: 12px; z-index: 10;
}
.settle-tip { flex: 1; }
.off-tip { color: var(--clr-warning); font-size: 13px; }
.settle-total { color: var(--clr-text-2); font-size: 14px; }
.settle-total b { color: var(--clr-danger); }
.total-price { color: var(--clr-danger); font-size: 22px; font-weight: 800; }
.empty-check { color: var(--clr-text-3); font-size: 13px; }

/* 结算弹窗 */
.checkout-list { max-height: 220px; overflow: auto; }
.checkout-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; }
.co-name { flex: 1; }
.co-qty { color: var(--clr-text-3); margin: 0 12px; }
.co-price { color: var(--clr-danger); font-weight: 600; }

/* 地址簿 */
.addr-title { font-weight: 600; margin-bottom: 8px; color: var(--clr-text); display: flex; align-items: center; justify-content: space-between; }
.addr-list { display: flex; flex-direction: column; gap: 8px; max-height: 220px; overflow: auto; }
.addr-card {
  border: 1px solid var(--clr-border); border-radius: var(--radius-md);
  padding: 10px 12px; cursor: pointer; transition: all .15s;
}
.addr-card:hover { border-color: var(--clr-primary); }
.addr-card.active { border-color: var(--clr-primary); background: var(--clr-primary-bg); }
.addr-main { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
.addr-receiver { font-weight: 600; }
.addr-phone { color: var(--clr-text-2); font-size: 13px; }
.addr-detail { color: var(--clr-text-3); font-size: 13px; }
.addr-form { margin-top: 12px; padding-top: 12px; border-top: 1px dashed var(--clr-border); }
</style>