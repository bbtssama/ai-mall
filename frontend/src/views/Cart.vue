<template>
  <div>
    <div class="cart-head">
      <h2 class="page-title">购物车</h2>
      <!-- 空购物车不显示"清空购物车"：没有东西可清，留着只会让空态更空 -->
      <el-button v-if="items.length" text type="danger" @click="clearCart">清空购物车</el-button>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!items.length && !loading" description="购物车还是空的">
        <div class="empty-tip">把心仪的宝贝加进来，随时回来一起结算</div>
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
          <div class="col-price">¥{{ formatPrice(row.price) }}</div>
          <div class="col-qty">
            <!-- 桌面：Element 步进器；手机：自绘 [−] N [+]（见下方 @media，桌面端不受影响） -->
            <el-input-number class="qty-desk" v-model="row.quantity" :min="1" :max="99" size="small"
                             @change="updateQty(row)" />
            <div class="qty-step mobile-only">
              <button class="step-btn pressable" :disabled="Number(row.quantity) <= 1"
                      aria-label="减少数量" @click="stepQty(row, -1)">−</button>
              <span class="step-num">{{ row.quantity }}</span>
              <button class="step-btn pressable" :disabled="Number(row.quantity) >= 99"
                      aria-label="增加数量" @click="stepQty(row, 1)">＋</button>
            </div>
          </div>
          <div class="col-subtotal"><span class="lbl">小计</span>¥{{ formatPrice(subtotalOf(row)) }}</div>
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
          <div class="col-price">¥{{ formatPrice(row.price) }}</div>
          <div class="col-qty">
            <span class="qty-text">×{{ row.quantity }}</span>
            <el-button v-if="row.productStatus === 1 && row.outOfStock && row.maxBuyable > 0"
                       size="small" type="warning" plain @click="fixQty(row)">设为 {{ row.maxBuyable }}</el-button>
          </div>
          <div class="col-subtotal off"><span class="lbl">小计</span>¥{{ formatPrice(subtotalOf(row)) }}</div>
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
            共 <b>{{ checkedCount }}</b> 件，合计
            <span class="total-price">¥{{ formatAmount(checkedAmount) }}</span>
          </template>
          <span v-else class="empty-check">请勾选要结算的商品</span>
        </div>
        <el-button type="primary" size="large" :disabled="selectedItems.length === 0"
                   @click="checkoutVisible = true">去结算</el-button>
      </div>
    </div>

    <!-- 结算弹窗：商品清单 + 收货地址簿选择 -->
    <el-dialog v-model="checkoutVisible" title="确认订单" width="620px" @open="openCheckout">
      <div class="checkout-list">
        <div v-for="i in selectedItems" :key="i.id" class="checkout-row">
          <span class="co-name">{{ i.productName }}（{{ i.skuName }}）</span>
          <span class="co-qty">×{{ i.quantity }}</span>
          <span class="co-price">¥{{ formatPrice(subtotalOf(i)) }}</span>
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
        <el-button type="primary" :loading="submitting" :disabled="!selectedAddrId" @click="submitOrder">
          提交订单（¥{{ formatAmount(checkedAmount) }}）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cartApi, orderApi, addressApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'
import { formatAmount, formatPrice } from '../utils/format'

const router = useRouter()
const auth = useAuthStore()
const cartStore = useCartStore()
const userId = computed(() => auth.user?.id)

const items = ref([])
const loading = ref(false)
const checkoutVisible = ref(false)
const submitting = ref(false)
// 下单幂等 token：弹窗打开时领取，提交时带回；后端一次性消费（防双击重复下单）
const orderToken = ref('')

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

// 移动端自绘步进器：±1 后复用同一套 updateQty（含超库存拦截 / 失败回滚）
function stepQty(row, delta) {
  const next = Number(row.quantity) + delta
  if (next < 1 || next > 99) return
  row.quantity = next
  updateQty(row)
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
  // 领取幂等 token：每次打开结算弹窗都取新的（上一次未用的自然过期）
  try {
    orderToken.value = await orderApi.token() || ''
  } catch {
    orderToken.value = ''   // 领取失败不挡下单（后端 token 可空兼容）
  }
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
  // 删除不可逆：与"清空购物车"保持同一道确认（此前单击即删，同页两套标准）
  try {
    await ElMessageBox.confirm(`确定把「${row.productName}」移出购物车吗？`, '提示',
      { type: 'warning', confirmButtonText: '移出', cancelButtonText: '再想想' })
  } catch {
    return
  }
  await cartApi.remove(row.id)
  checkedIds.value.delete(row.id)
  persistChecked()
  cartStore.refresh()
  ElMessage.success('已移出购物车')
  load()
}

async function clearCart() {
  try {
    await ElMessageBox.confirm('确定清空购物车吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await cartApi.clear()
  checkedIds.value.clear()
  persistChecked()
  cartStore.refresh()
  load()
}

/**
 * 结果提示 + 一个明确的下一步入口。
 * 此前把 23 位订单号塞进顶部绿条：既盖住品牌栏，用户也读不出任何信息。
 * 订单号属于"订单详情里能随时翻到"的东西，收据式的提示只需要「结果 + 去哪」。
 */
function toastWithAction(text, actionText, onAction) {
  ElMessage({
    type: 'success',
    duration: 2600,
    message: h('span', { style: 'display:inline-flex;align-items:center;gap:12px' }, [
      h('span', text),
      h('a', {
        style: 'color:var(--clr-primary);font-weight:600;cursor:pointer',
        onClick: (e) => { e.preventDefault(); onAction() }
      }, actionText)
    ])
  })
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
    await orderApi.create({
      items: selectedItems.value.map(i => ({ skuId: i.skuId, quantity: i.quantity })),
      receiverName: addr.receiver,
      receiverPhone: addr.phone,
      receiverAddress: addr.fullAddress,
      idempotentToken: orderToken.value || undefined
    })
    toastWithAction('下单成功', '查看订单', () => router.push('/orders'))
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
/* 移动端才出现的"小计"标签：桌面端保持原样（display:none 不影响布局） */
.lbl { display: none; }
/* 自绘步进器只在手机端出现（.mobile-only 桌面 display:none，手机端由 @media 覆盖为 flex） */
.qty-step { display: none; }

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

/* =====================================================================
   移动端适配（≤ 768px）
   ★ 要点：桌面端一行 7 列的「勾选/图/名称/单价/数量/小计/删除」在 390px 下
     放不下，此前用 flex-wrap + flex-basis:100% 硬挤，结果名称被顶到图片下方，
     单条高达 197px（首屏只放得下 2.8 条）。
   现改为电商 App 通用的两行网格（DOM 顺序不变，仅用 grid-area 重新布点）：
     行 1：[勾选] [图 80]  商品名 / 规格 / 库存              [删除]
     行 2：[勾选] [图 80]  小计                      [−  N  +]
   勾选框与图片跨两行 → 单条 ≈ 118px，首屏 4 条以上。
   ===================================================================== */
@media (max-width: 768px) {
  .cart-head { margin-bottom: 8px; }
  .page-title { font-size: 18px; }

  .item-list, .blocked-group { gap: 8px; }

  .cart-item {
    position: relative;                    /* 供右上角「删除」定位 */
    display: grid;
    grid-template-columns: 40px 80px minmax(0, 1fr) auto;
    grid-template-rows: auto auto;
    align-items: center;
    column-gap: 6px; row-gap: 4px;
    padding: 8px 10px;
  }

  /* 勾选框：整格 40×44 触摸区（EP 默认 22×40 且带 30px 右外边距，一并收回） */
  .ck, .ck-placeholder {
    grid-area: 1 / 1 / 3 / 2;
    width: 40px; height: 44px; min-height: 44px;
    padding: 0; margin: 0;
    display: flex; align-items: center; justify-content: center;
  }
  .ck :deep(.el-checkbox__inner) { width: 20px; height: 20px; border-radius: var(--r-sm); }
  .ck :deep(.el-checkbox__inner::after) { height: 10px; left: 7px; top: 3px; }

  .thumb { grid-area: 1 / 2 / 3 / 3; width: 80px; height: 80px; border-radius: var(--r-sm); }

  /* 商品名/规格/库存：跨 3、4 两列（右侧留 46px 给绝对定位的删除） */
  .goods-info { grid-area: 1 / 3 / 2 / 5; padding-right: 46px; }
  .goods-name {
    font-size: 14px; line-height: 1.3;
    display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  }
  .goods-sku, .stock-line, .stock-warn { font-size: 12px; }

  /* 行 2：左「小计 / ¥x」（标签在上、金额在下，宽度只需取两者较大值），
     右「[−] N [+]」。单价在窄屏让位——两个无标签数字并排本来就读不懂。
     标签换行不会加高卡片：该行高度由 44px 的步进器决定。 */
  .col-price { display: none; }
  .col-subtotal {
    grid-area: 2 / 3 / 3 / 4; justify-self: start; align-self: center;
    display: flex; flex-direction: column; align-items: flex-start;
    width: auto; min-width: 0; text-align: left;
    font-size: 14px; color: var(--clr-primary);
    white-space: nowrap; overflow: hidden;
  }
  .col-subtotal .lbl {
    display: block; font-size: 11px; font-weight: 400; line-height: 1.25;
    color: var(--clr-text-3);
  }
  .col-subtotal.off { color: var(--clr-text-4); }
  .col-qty { grid-area: 2 / 4 / 3 / 5; justify-self: end; width: auto; margin: 0; }

  /* 手机端抛弃 el-input-number：EP 的 ± 实际点击区只有 25px 宽、数字 12px */
  .col-qty :deep(.el-input-number) { display: none; }
  .qty-step {
    display: flex; align-items: center;
    border: 1px solid var(--clr-border); border-radius: var(--r-sm);
    overflow: hidden; background: #fff;
  }
  .step-btn {
    width: 44px; height: 44px; flex: 0 0 44px;
    border: 0; background: #fafafa; color: var(--clr-text);
    font-size: 20px; line-height: 1;
    display: flex; align-items: center; justify-content: center;
  }
  .step-btn:disabled { color: var(--clr-text-4); background: #fdfdfd; }
  .step-num {
    min-width: 32px; text-align: center;
    font-size: 15px; font-variant-numeric: tabular-nums;
  }

  /* 删除挪到卡片右上角：不占正文行，触摸目标 44px */
  .del {
    position: absolute; top: 0; right: 0;
    min-width: 44px; min-height: 44px; margin: 0; padding: 0;
  }

  /* 失效分组 */
  .blocked-group .blocked-title { font-size: 12px; }
  .blocked-group .qty-text { font-size: 13px; }

  /* 结算栏吸底：紧贴底部标签栏（bottom = 标签栏高度），视觉上连成一条双行操作区。
     用 sticky 而非 fixed —— 吸底期间不遮挡列表最后一条，滚到底时回到文档流末尾。 */
  .settle-bar {
    position: sticky;
    bottom: calc(var(--tabbar-h) + var(--safe-b));
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    column-gap: 8px; row-gap: 4px;
    padding: 8px 10px;
    margin-top: 6px;
    border-radius: var(--r-md);
    box-shadow: 0 -2px 12px rgba(0, 0, 0, .08);
    z-index: 10;
  }
  .settle-tip { grid-column: 1 / -1; grid-row: 1; }
  .settle-tip:empty { display: none; }          /* 无失效商品时不占行 */
  .settle-bar > .el-checkbox {
    grid-column: 1; grid-row: 2;
    min-height: 44px; margin: 0; padding: 0 2px;
  }
  .settle-bar > .el-checkbox :deep(.el-checkbox__inner) { width: 20px; height: 20px; border-radius: var(--r-sm); }
  .settle-bar > .el-checkbox :deep(.el-checkbox__inner::after) { height: 10px; left: 7px; top: 3px; }
  .settle-total { grid-column: 2; grid-row: 2; text-align: left; font-size: 12px; color: var(--clr-text-3); }
  .settle-total b { color: var(--clr-primary); }
  .settle-bar > .el-button { grid-column: 3; grid-row: 2; margin: 0; min-height: 44px; }
  .total-price { font-size: 18px; color: var(--clr-primary); }
  .empty-check { font-size: 12px; }

  /* 空态：不再留一大片纯白 */
  .empty-tip { color: var(--clr-text-3); font-size: 13px; margin: -4px 0 12px; }

  /* 结算弹窗：清单/地址簿限高，窄屏不至于把弹窗撑到视口外 */
  .checkout-list { max-height: 30vh; }
  .checkout-row { font-size: 13px; }
  .addr-list { max-height: 38vh; }
  .addr-card { padding: 12px; }
  .addr-main { flex-wrap: wrap; gap: 6px 8px; }

  /* 弹窗内的新增地址表单：label 上移、控件全宽（纯 CSS，桌面端 prop 不变）。
     EP 的 .el-form-item 默认 display:flex，必须显式改 block 才能让 label 独占一行。 */
  .addr-form { margin-top: 10px; padding-top: 10px; }
  .addr-form :deep(.el-form-item) { display: block; margin-bottom: 14px; }
  .addr-form :deep(.el-form-item__label) {
    display: block; width: auto !important; height: auto; line-height: 1.4;
    text-align: left; padding: 0 0 6px; font-size: 13px;
  }
  .addr-form :deep(.el-form-item__content) { display: block; margin-left: 0 !important; }
  .addr-form .el-button { width: 100%; }
  /* 弹窗内输入框 ≥16px 防 iOS 聚焦缩放 */
  .addr-form :deep(.el-input__inner) { font-size: 16px; }
}

/* 360px 级窄屏（Galaxy S8 一类）：图片与步进器各收一档，
   保证「小计 / ¥1,299」仍有完整显示宽度。± 保持 40×44 可点面积。 */
@media (max-width: 375px) {
  .cart-item { grid-template-columns: 40px 72px minmax(0, 1fr) auto; }
  .thumb { width: 72px; height: 72px; }
  .step-btn { width: 40px; flex: 0 0 40px; }
  .step-num { min-width: 28px; }
  .col-subtotal { font-size: 13px; }
}
</style>