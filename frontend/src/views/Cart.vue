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

        <el-table-column label="数量/库存" width="160">
          <template #default="{ row }">
            <div class="qty-cell">
              <!-- max 固定为 99（不跟库存挂钩），避免库存下降时 el-input-number 把
                   超库存的真实数量 clamp 成库存；超库存的正确反馈交给下方 outOfStock 提示与'设为N' -->
              <el-input-number v-model="row.quantity" :min="1" :max="99"
                               :disabled="row.productStatus !== 1 || row.skuStock === 0"
                               size="small" @change="updateQty(row)" />
              <!-- 显式展示实时库存：正常显示库存数；下架/售罄以状态 tag 呈现 -->
              <div class="stock-line" v-if="row.productStatus === 1">
                <span v-if="row.skuStock > 0" :class="{ low: row.skuStock <= 10, zero: row.skuStock === 0 }">
                  库存 {{ row.skuStock }} 件
                </span>
                <span v-else class="zero">无货</span>
              </div>
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

    <!-- 结算弹窗：商品清单 + 收货地址簿选择 -->
    <el-dialog v-model="checkoutVisible" title="确认订单" width="560px" @open="openCheckout">
      <div class="checkout-list">
        <div v-for="i in selectedItems" :key="i.id" class="checkout-row">
          <span class="co-name">{{ i.productName }}（{{ i.skuName }}）</span>
          <span class="co-qty">×{{ i.quantity }}</span>
          <span class="co-price">¥{{ subtotalOf(i) }}</span>
        </div>
      </div>
      <el-divider />
      <div class="addr-section" v-loading="addressLoading">
        <div class="addr-title">选择收货地址</div>
        <el-empty v-if="!addresses.length && !addressLoading" description="还没有收货地址" :image-size="60">
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

const router = useRouter()
const auth = useAuthStore()
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
  // 用户改数若超过可购库存：不提交，恢复原值并提示（京东式“点+到库存即停”）。
  // 这样不覆盖“库存下降导致的既有超库存量”(该量由 outOfStock 提示 + '设为N' 处理)。
  if (row.quantity > row.maxBuyable) {
    ElMessage.warning(`该商品最多可购 ${row.maxBuyable} 件`)
    await load() // 恢复为购物车真实数量
    return
  }
  try {
    await cartApi.update(row.id, { quantity: row.quantity })
    // 更新后重新拉（行是否仍合法以服务端为准）
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
  // 打开结算弹窗时加载地址簿并默认选中默认地址
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
  // 从地址簿取选中的收货地址（下单仍存地址快照）
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
    // 下单后取消勾选项（已结算清车）
    selectedItems.value.forEach(i => checkedIds.value.delete(i.id))
    persistChecked()
    checkoutVisible.value = false
    router.push('/orders')
  } catch (e) {
    // 结算失败（典型：勾选后到提交前库存被抢走/商品下架，后端校验拒绝）。
    // 自动刷新购物车，让相关条目立即按最新库存状态展示（超库存提示 + '设为N' 修正、自动取消勾选），更直观。
    ElMessage.warning('下单未成功，已刷新购物车，请检查库存后重试')
    checkoutVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

function onImgError(e) { e.target.src = 'https://picsum.photos/seed/fallback/120/120' }

onMounted(() => {
  load()
  loadAddresses()
})
</script>

<style scoped>
.cart-head { display: flex; justify-content: space-between; align-items: center; }
.goods-cell { display: flex; align-items: center; gap: 10px; }
.thumb { width: 52px; height: 52px; border-radius: 6px; object-fit: cover; opacity: 0.9; }
.goods-info { min-width: 0; }
.goods-name { font-weight: 600; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.goods-sku { color: #999; font-size: 12px; margin-top: 2px; }
.stock-warn { color: #f56c6c; font-size: 12px; margin-top: 3px; }
.qty-cell { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
.stock-line { font-size: 12px; color: #67c23a; line-height: 1.2; }
.stock-line .low { color: #e6a23c; }
.stock-line .zero { color: #f56c6c; }
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
/* 收货地址簿 */
.addr-section { }
.addr-title { font-weight: 600; margin-bottom: 8px; color: #333; }
.addr-list { display: flex; flex-direction: column; gap: 8px; max-height: 220px; overflow: auto; }
.addr-card {
  border: 1px solid #e0e0e0; border-radius: 8px; padding: 10px 12px; cursor: pointer;
  transition: all .15s;
}
.addr-card:hover { border-color: #e8562c; }
.addr-card.active { border-color: #e8562c; background: #fff6f4; }
.addr-main { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
.addr-receiver { font-weight: 600; }
.addr-phone { color: #666; font-size: 13px; }
.addr-detail { color: #999; font-size: 13px; }
.addr-form { margin-top: 12px; padding-top: 12px; border-top: 1px dashed #eee; }
/* 下架整行置灰、超库存整行浅红提示 */
.el-table :deep(.row-off) { background: #fafafa !important; color: #aaa; }
.el-table :deep(.row-off td) { background: #fafafa !important; }
.el-table :deep(.row-warn) { background: #fff7f7 !important; }
.el-table :deep(.row-warn td) { background: #fff7f7 !important; }
</style>