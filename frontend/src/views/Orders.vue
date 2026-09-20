<template>
  <div>
    <h2 class="page-title">我的订单</h2>

    <!-- 状态筛选 tab：计数统一展示（此前"有数字/没数字"混排，同一排信息量不一致） -->
    <div class="tabs-wrap mall-card">
      <span v-for="t in tabs" :key="t.value" class="tab pressable"
            :class="{ active: activeStatus === t.value }" @click="switchTab(t.value)">
        {{ t.label }}
        <span class="tab-count">{{ countOf(t.value) }}</span>
      </span>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!pagedOrders.length && !loading" :description="emptyDesc">
        <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
      </el-empty>

      <div v-for="o in pagedOrders" :key="o.id" class="order-card mall-card">
        <div class="order-head">
          <span class="order-time">{{ fmtTime(o.createdAt) }}</span>
          <el-tag :type="statusTag(o.status)" size="small" class="status-tag">{{ statusText(o.status) }}</el-tag>
          <!-- 订单号弱化 + 整行可复制（23 位单号手抄不现实） -->
          <span class="order-no" role="button" tabindex="0"
                :aria-label="`复制订单号 ${o.orderNo}`" @click.stop="copyNo(o.orderNo)">
            订单号 {{ o.orderNo }}
            <el-icon class="no-copy"><DocumentCopy /></el-icon>
          </span>
        </div>
        <div class="order-body" @click="showDetail(o)">
          <div v-if="itemsOf(o).length" class="goods-brief">
            <img :src="briefImg(o)" class="brief-thumb" @error="onImgError" />
            <div class="brief-info">
              <div class="brief-name">{{ briefName(o) }}</div>
              <div class="brief-sub">共 {{ qtyOf(o) }} 件</div>
            </div>
          </div>
          <div class="order-amount">
            <span class="amount"><span class="lbl">合计</span>¥{{ formatAmount(o.totalAmount) }}</span>
            <span class="receiver">收货：{{ o.receiverName }} {{ o.receiverPhone }}</span>
          </div>
        </div>
        <div class="order-foot">
          <el-button v-if="o.status === 'PENDING_PAY'" size="small" type="primary" @click="goPay(o)">
            去支付
          </el-button>
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

    <!-- 订单明细 Drawer：自绘标题栏（关闭键 ≥44px），抽屉内只放卡片上没有的信息 -->
    <el-drawer v-model="detailVisible" class="order-drawer" :show-close="false" size="420px">
      <template #header>
        <div class="dw-head">
          <span class="dw-title">订单明细</span>
          <button class="dw-close pressable" aria-label="关闭" @click="detailVisible = false">
            <el-icon><Close /></el-icon>
          </button>
        </div>
      </template>

      <template v-if="current">
        <div class="dw-status">
          <span class="amount"><span class="lbl">合计</span>¥{{ formatAmount(current.totalAmount) }}</span>
          <el-tag :type="statusTag(current.status)" size="small">{{ statusText(current.status) }}</el-tag>
        </div>

        <div class="items-title">商品明细（{{ (current.items || []).length }}）</div>
        <div v-for="it in current.items" :key="it.id" class="item-row">
          <img :src="itemImg(it)" class="item-thumb" @error="onImgError" />
          <div class="item-info">
            <div class="item-name">{{ it.productName }}</div>
            <div class="item-sku">{{ it.skuName }}</div>
          </div>
          <div class="item-right">
            <div class="item-price">¥{{ formatPrice(it.price) }}</div>
            <div class="item-qty">×{{ it.quantity }}</div>
          </div>
        </div>

        <div class="items-title">收货信息</div>
        <div class="flow-meta">
          <div class="fm-row"><span class="fm-k">收货人</span><span class="fm-v">{{ current.receiverName }} {{ current.receiverPhone }}</span></div>
          <div class="fm-row"><span class="fm-k">地址</span><span class="fm-v">{{ current.receiverAddress }}</span></div>
        </div>

        <div class="items-title">订单信息</div>
        <div class="flow-meta">
          <div class="fm-row"><span class="fm-k">订单号</span><span class="fm-v mono">{{ current.orderNo }}</span></div>
          <div class="fm-row"><span class="fm-k">下单时间</span><span class="fm-v">{{ fmtTime(current.createdAt) }}</span></div>
          <div class="fm-row" v-if="current.payTime"><span class="fm-k">支付时间</span><span class="fm-v">{{ fmtTime(current.payTime) }}</span></div>
        </div>
      </template>

      <template #footer v-if="current && current.status === 'PENDING_PAY'">
        <div class="dw-foot">
          <el-button plain @click="cancelFromDrawer(current)">取消订单</el-button>
          <el-button type="primary" @click="goPay(current)">去支付</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentCopy, Close } from '@element-plus/icons-vue'
import { orderApi, payApi } from '../api'
import { formatAmount, formatPrice, formatTime } from '../utils/format'

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

/**
 * 列表接口只回订单主体（items 恒为 null），商品名/件数/缩略图拿不到，
 * 卡片上就只剩一串订单号——用户看不出自己买了什么。
 * 后端不改，只能前端补齐：按当前页订单并发拉明细并缓存（切 tab / 翻页不重复请求）。
 */
const detailCache = reactive({})
const itemsOf = (o) => detailCache[o.id] || []
const qtyOf = (o) => itemsOf(o).reduce((s, i) => s + (Number(i.quantity) || 0), 0)
function briefName(o) {
  const list = itemsOf(o)
  if (!list.length) return ''
  return list.length > 1 ? `${list[0].productName} 等 ${list.length} 种` : list[0].productName
}
function briefImg(o) {
  const first = itemsOf(o)[0]
  return first?.skuId ? `https://picsum.photos/seed/sku${first.skuId}/120/120` : itemImg()
}
async function hydrateItems(list) {
  await Promise.all(list.map(async (o) => {
    if (detailCache[o.id]) return
    try {
      const d = await orderApi.detail(o.id)
      detailCache[o.id] = d?.items || []
    } catch {
      detailCache[o.id] = []   // 拉不到就退回"无商品信息"，不影响卡片其余部分
    }
  }))
}
watch(pagedOrders, (list) => { if (list.length) hydrateItems(list) })

async function copyNo(no) {
  try {
    await navigator.clipboard.writeText(no)
    ElMessage.success('订单号已复制')
  } catch {
    ElMessage.warning('复制失败，请长按订单号手动选择')
  }
}

function switchTab(v) {
  activeStatus.value = v
  page.value = 1
}

async function cancel(o) {
  try {
    await ElMessageBox.confirm('确定取消该订单吗？库存将自动回补', '提示', { type: 'warning' })
  } catch {
    return
  }
  await orderApi.cancel(o.id)
  ElMessage.success('订单已取消')
  load()
}

// 抽屉里的取消：先关抽屉，再走同一套确认 + 取消流程
async function cancelFromDrawer(o) {
  detailVisible.value = false
  await cancel(o)
}

// V3：发起支付 → 创建/复用支付单 → 跳模拟收银台
// cashierUrl 由后端拼好（含 paymentNo/amount），前端不重复拼参数
async function goPay(o) {
  const vo = await payApi.create(o.id)
  window.location.href = vo.cashierUrl
}

async function showDetail(o) {
  current.value = await orderApi.detail(o.id)
  detailVisible.value = true
}

// 订单明细无图片字段，用占位图（同商品同图，便于区分不同商品）
function itemImg(it) {
  return it?.skuId ? `https://picsum.photos/seed/sku${it.skuId}/120/120` : 'https://picsum.photos/seed/order/120/120'
}

function statusText(s) { return STATUS_TEXT[s] || s }
function statusTag(s) { return STATUS_TAG[s] || 'info' }
function fmtTime(t) { return t ? formatTime(t) : '-' }
function onImgError(e) { e.target.src = 'https://picsum.photos/seed/fallback/120/120' }

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
/* 模板里为了移动端的阅读顺序把「时间」放到了 DOM 最前，
   桌面端用 order 还原成原来的「订单号 / 时间 / 状态」三列。 */
.order-head { display: flex; align-items: center; gap: 12px; }
.order-no { order: 1; color: var(--clr-text-3); font-size: 13px; flex: 1; cursor: pointer; }
.order-time { order: 2; color: var(--clr-text-4); font-size: 12px; }
.status-tag { order: 3; }
.order-body { padding: 12px 0 8px; cursor: pointer; display: flex; align-items: center; gap: 16px; }
.amount { color: var(--clr-danger); font-size: 20px; font-weight: 800; margin-right: 16px; }
.receiver { color: var(--clr-text-3); font-size: 13px; }
.order-foot { display: flex; justify-content: flex-end; gap: 8px; border-top: 1px solid var(--clr-border-light); padding-top: 10px; }

/* 卡片里的商品摘要（图 + 名称 + 件数）：此前卡片只有一串订单号，看不出买了什么 */
.goods-brief { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; }
.brief-thumb { width: 56px; height: 56px; border-radius: var(--radius-md); object-fit: cover; flex: 0 0 auto; }
.brief-info { min-width: 0; }
.brief-name {
  font-size: 14px; color: var(--clr-text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.brief-sub { font-size: 12px; color: var(--clr-text-3); margin-top: 2px; }
.order-amount { display: flex; align-items: baseline; flex: 0 0 auto; }
.no-copy { display: none; }        /* 复制入口是移动端交互，桌面端不增加视觉元素 */
.lbl { display: none; }            /* 仅移动端出现的"合计"标签 */

.pager { display: flex; justify-content: center; margin-top: 16px; }

/* 明细 Drawer */
.dw-head { display: flex; align-items: center; justify-content: space-between; }
.dw-title { font-weight: 600; color: var(--clr-text); font-size: 16px; }
.dw-close {
  width: 32px; height: 32px; border: 0; padding: 0; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
  background: transparent; color: var(--clr-text-2); border-radius: var(--radius-md);
}
.dw-close:hover { background: var(--clr-bg); }
.dw-status { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
.dw-foot { display: flex; justify-content: flex-end; gap: 8px; }
.flow-meta { font-size: 13px; }
.fm-row { display: flex; gap: 12px; padding: 5px 0; }
.fm-k { color: var(--clr-text-3); flex: 0 0 64px; }
.fm-v { color: var(--clr-text); flex: 1; min-width: 0; overflow-wrap: anywhere; }
.fm-v.mono { font-variant-numeric: tabular-nums; letter-spacing: .2px; }

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

/* =====================================================================
   移动端适配（≤ 768px）
   ★ 状态筛选：原为单行横向滚动（scrollWidth 474 / 可视 350），"已取消"完全在屏外
     且无任何溢出暗示 → 改成两行平铺的 chip 网格，6 个筛选项一屏看全。
   ★ 订单卡：时间上移为首行（订单号降级为第二行弱化文本 + 整行可复制）；
     补商品缩略图 / 商品名 / 件数；金额与收货人各自独占一行（此前两者贴死）。
   ===================================================================== */
@media (max-width: 768px) {
  .page-title { font-size: 18px; margin-bottom: 10px; }

  .tabs-wrap {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
    padding: 10px;
    margin-bottom: 10px;
    overflow: visible;
  }
  .tab {
    justify-content: center;
    min-height: 40px; padding: 6px 4px;
    font-size: 13px; border-radius: var(--r-sm);
    background: var(--clr-bg);
  }
  .tab.active { background: var(--clr-primary); color: #fff; }
  .tab-count { font-size: 11px; }

  .order-card { padding: 6px 12px 10px; margin-bottom: 10px; }

  /* 行 1：时间（左）＋状态（右）；行 2：订单号（弱化）＋复制图标 */
  .order-head { display: flex; flex-wrap: wrap; align-items: center; gap: 2px 8px; }
  .order-time { order: 1; flex: 1 1 auto; font-size: 12px; color: var(--clr-text-3); }
  .status-tag { order: 2; }
  .order-no {
    order: 3; flex: 1 1 100%;
    display: inline-flex; align-items: center; gap: 6px;
    min-height: 40px;                       /* 整行都是复制热区，不用瞄准小图标 */
    font-size: 11px; color: var(--clr-text-4);
    overflow-wrap: anywhere;
  }
  .no-copy { display: inline-flex; font-size: 14px; color: var(--clr-text-3); }

  .order-body { flex-direction: column; align-items: stretch; gap: 8px; padding: 2px 0 8px; }
  .goods-brief { gap: 10px; }
  .brief-thumb { width: 64px; height: 64px; border-radius: var(--r-sm); }
  .brief-name { font-size: 13px; }

  /* 金额独占一行，收货信息另起一行 */
  .order-amount { flex-direction: column; align-items: flex-start; gap: 4px; }
  .amount { margin-right: 0; font-size: 20px; color: var(--clr-primary); }
  .amount .lbl {
    display: inline; font-size: 12px; font-weight: 400;
    color: var(--clr-text-3); margin-right: 4px;
  }
  .receiver { font-size: 12px; }

  /* 操作按钮：等分一行（放不下时自动换行），触摸目标 ≥44px */
  .order-foot { flex-wrap: wrap; gap: 8px; }
  .order-foot .el-button { flex: 1 1 0; min-width: 0; min-height: 44px; margin: 0; padding: 0 8px; }

  /* 分页器：仅 prev/pager/next，窄屏放大触摸目标，必要时横向滑动而不是撑破视口 */
  .pager { margin-top: 12px; padding-bottom: 2px; overflow-x: auto; }
  .pager :deep(.el-pagination) { flex-wrap: nowrap; }
  .pager :deep(.el-pagination > button),
  .pager :deep(.el-pagination .el-pager li) { min-width: 36px; height: 36px; line-height: 36px; }

  /* 抽屉：关闭键抬到 44px；底部操作等分整行 */
  .dw-close { width: 44px; height: 44px; margin-right: -6px; }
  .dw-status { margin-bottom: 8px; }
  .dw-foot { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
  .dw-foot .el-button { width: 100%; min-height: 44px; margin: 0; }

  /* 收货/订单信息：label 上、value 下（窄抽屉里左右分栏会把 23 位单号挤断） */
  .fm-row { display: block; padding: 4px 0; }
  .fm-k { display: block; font-size: 12px; margin-bottom: 2px; }
  .fm-v { font-size: 13px; }

  .item-thumb { width: 56px; height: 56px; }
  .item-name { font-size: 13px; }
}
</style>