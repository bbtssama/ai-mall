<template>
  <div class="detail-page" v-loading="loading">
    <!-- 面包屑：移动端靠系统返回键，这一行 12px 高的层级信息是纯废信息（见媒体查询里隐藏） -->
    <el-breadcrumb separator="/" class="crumb">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>{{ product?.spuName || '商品详情' }}</el-breadcrumb-item>
    </el-breadcrumb>

    <div v-if="product" class="detail-wrap">
      <!-- 左：图集（多图可滚动；选中规格切换为规格专属图集） -->
      <div class="gallery">
        <div class="main-img">
          <img :src="activeImage" @error="onImgError" />
        </div>
        <div class="thumb-strip" v-if="currentImages.length > 1">
          <div v-for="(img, idx) in currentImages" :key="idx" class="thumb-item pressable"
               :class="{ active: img === activeImage }" @click="activeImage = img">
            <img :src="img" :alt="'图' + (idx + 1)" loading="lazy" @error="onImgError" />
          </div>
        </div>
      </div>

      <!-- 右：信息 -->
      <div class="buy-panel">
        <h1 class="name">{{ product.spuName }}</h1>
        <div class="sub">{{ product.subTitle }}</div>

        <div class="price-box">
          <div class="price-row">
            <span class="label">价格</span>
            <span class="price">¥{{ formatPrice(selectedSku?.price ?? product.minPrice) }}</span>
          </div>
        </div>

        <!-- SKU 规格选择：卡片式，选中态明显 -->
        <div class="sku-section">
          <div class="sku-label">选择规格</div>
          <div class="sku-list">
            <div v-for="s in product.skus" :key="s.id" class="sku-card pressable"
                 :class="{ active: selectedSku?.id === s.id }" @click="selectedSku = s">
              <div class="sku-name">{{ s.skuName }}</div>
              <div class="sku-price">¥{{ formatPrice(s.price) }}</div>
              <div class="sku-stock">库存 {{ s.stock }}</div>
            </div>
          </div>
        </div>

        <!-- 数量 + 操作 -->
        <div class="buy-row">
          <div class="qty">
            <span class="label">数量</span>
            <el-input-number class="qty-num" v-model="quantity" :min="1" :max="selectedSku?.stock || 99" size="default" />
            <!-- 手机端专用：44px 圆角胶囊步进器（el-input-number 的描边方块在触屏上桌面感太重） -->
            <div class="stepper">
              <button type="button" class="stp" :disabled="quantity <= 1"
                      @click="quantity = Math.max(1, quantity - 1)">−</button>
              <span class="stp-val">{{ quantity }}</span>
              <button type="button" class="stp" :disabled="quantity >= (selectedSku?.stock || 99)"
                      @click="quantity = Math.min(selectedSku?.stock || 99, quantity + 1)">+</button>
            </div>
          </div>
        </div>
        <div class="action-row" v-if="isLoggedIn">
          <el-button type="warning" size="large" class="btn-add" :disabled="!selectedSku || selectedSku.stock <= 0"
                     @click="addCart">加入购物车</el-button>
          <el-button type="danger" size="large" class="btn-buy" :disabled="!selectedSku || selectedSku.stock <= 0"
                     @click="buyNow">立即购买</el-button>
          <el-button size="large" class="btn-ai" @click="$router.push('/chat')">🤖 问 AI</el-button>
        </div>
        <!-- 匿名用户：不下单、不发请求，就地给出登录引导 -->
        <div v-else class="login-hint">
          <span>登录后即可加入购物车 / 立即购买</span>
          <el-button type="primary" @click="goLogin">去登录</el-button>
        </div>
        <div class="sold">已售 {{ formatCount(soldTotal) }} 件 · 支持 7 天无理由退换</div>
      </div>
    </div>

    <!-- 详情区 -->
    <div v-if="product" class="detail-section mall-card">
      <el-collapse v-model="openPanels">
        <el-collapse-item title="商品详情" name="detail">
          <p class="detail-text">{{ product.detail }}</p>
        </el-collapse-item>
        <el-collapse-item title="采购信息" name="meta">
          <div class="meta-grid">
            <div class="meta-item"><span class="k">商品编号</span><span class="v">{{ product.id }}</span></div>
            <div class="meta-item"><span class="k">分类</span><span class="v">{{ categoryName }}</span></div>
            <div class="meta-item"><span class="k">上架状态</span><span class="v">{{ product.status === 1 ? '在售' : '已下架' }}</span></div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <el-empty v-else-if="!loading" description="商品不存在或已下架">
      <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
    </el-empty>

    <!--
      吸底购买条（仅手机端渲染）：
      电商详情页最关键的转化位，不能被"滚到下面才看得见"耽误。
      位置钉在全局底部标签栏之上（bottom = 标签栏高 + 安全区 + 1px 边框），
      两条互不遮挡；页面内容另加 padding-bottom 让位。
    -->
    <div v-if="product" class="buy-bar">
      <button type="button" class="bar-mini" @click="$router.push('/chat')">
        <el-icon class="bar-ico"><Service /></el-icon>
        <span>客服</span>
      </button>
      <button type="button" class="bar-mini" :class="{ 'is-on': favorited }" @click="toggleFav">
        <el-icon class="bar-ico"><Star /></el-icon>
        <span>收藏</span>
      </button>
      <button type="button" class="bar-btn bar-add" :disabled="!selectedSku || selectedSku.stock <= 0"
              @click="addCart">加入购物车</button>
      <button type="button" class="bar-btn bar-buy" :disabled="!selectedSku || selectedSku.stock <= 0"
              @click="buyNow">立即购买</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Service, Star } from '@element-plus/icons-vue'
import { productApi, cartApi } from '../api'
import { formatPrice, formatCount } from '../utils/format'
import { useCartStore } from '../stores/cart'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const auth = useAuthStore()

// 匿名可浏览本页；加购/购买这类写操作必须登录（未登录时不发请求，直接引导登录）
const isLoggedIn = computed(() => !!auth.token)

const product = ref(null)
const loading = ref(false)
const selectedSku = ref(null)
const quantity = ref(1)
const openPanels = ref(['detail'])
const activeImage = ref('')

// 收藏：后端暂无"商品收藏"接口（只有笔记的 /notes/{id}/collect），
// 故这里只做本页的即时反馈，不做持久化；接入接口时替换 toggleFav 内部即可。
const favorited = ref(false)

const CATEGORY = { 101: '数码影音', 102: '数码配件', 103: '美妆护肤', 104: '生活家居' }
const categoryName = computed(() => (product.value ? (CATEGORY[product.value.categoryId] || '未分类') : ''))
const soldTotal = computed(() => (product.value?.skus || []).reduce((s, x) => s + (x.sales || 0), 0))

// 当前展示图集（京东模式）：一律用选中 SKU 的图集（多张）；
// 无规格图时兜底商品封面。默认选中第一个 SKU → 进详情即其图集。
const currentImages = computed(() => {
  const sku = selectedSku.value
  if (sku && sku.images && sku.images.length) return sku.images
  return [product.value?.mainImg].filter(Boolean)
})

// 切换规格 → 主图切回该图集第一张；无则维持
watch(selectedSku, () => {
  const imgs = currentImages.value
  if (imgs.length) activeImage.value = imgs[0]
})

async function load() {
  loading.value = true
  try {
    product.value = await productApi.detail(route.params.id)
    selectedSku.value = product.value?.skus?.[0] || null
    // 若首 SKU 库存 0，自动选中第一个有货的
    if (selectedSku.value && selectedSku.value.stock <= 0) {
      const ok = (product.value.skus || []).find(s => s.stock > 0)
      if (ok) selectedSku.value = ok
    }
    activeImage.value = currentImages.value[0] || product.value?.mainImg || ''
    if (product.value) applyBottomReserve()
  } finally {
    loading.value = false
  }
}

/**
 * 吸底购买条高 57px（内边距 6+6 + 按钮 44 + 上边框 1），它站在全局标签栏（52 + 安全区）之上。
 * 但 App.vue 的 .layout 只为标签栏预留了 --pad-bottom —— 滚到底时页脚会被购买条压住。
 * 不动 App.vue 的前提下，由本页把这个 token 抬高一截；离开本页即还原。
 * （--pad-bottom 只在 App.vue 的移动端媒体查询里被消费，桌面端完全不受影响。）
 */
const PAD_BOTTOM_WITH_BAR = 'calc(var(--tabbar-h) + var(--safe-b) + 8px + 57px)'
let padBottomBackup = ''

function applyBottomReserve() {
  const root = document.documentElement
  if (!padBottomBackup) padBottomBackup = root.style.getPropertyValue('--pad-bottom') || ' '
  root.style.setProperty('--pad-bottom', PAD_BOTTOM_WITH_BAR)
}

onBeforeUnmount(() => {
  const root = document.documentElement
  if (padBottomBackup.trim()) root.style.setProperty('--pad-bottom', padBottomBackup.trim())
  else root.style.removeProperty('--pad-bottom')
})

function goLogin() {
  // 带上当前页路径，登录后回到这个商品详情
  router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
}

/** 未登录守卫：提示 + 引导登录，绝不发请求（否则 401） */
function requireLogin() {
  ElMessage.warning('该操作需要登录')
  goLogin()
  return false
}

function toggleFav() {
  if (!isLoggedIn.value) return requireLogin()
  favorited.value = !favorited.value
  ElMessage.success(favorited.value ? '已收藏' : '已取消收藏')
}

async function addCart() {
  if (!isLoggedIn.value) return requireLogin()
  await cartApi.add({ skuId: selectedSku.value.id, quantity: quantity.value })
  cartStore.refresh()
  ElMessage.success('已加入购物车')
}

async function buyNow() {
  if (!isLoggedIn.value) return requireLogin()
  // 立即购买 = 加入购物车并直达购物车结算（复用完整的地址簿/库存校验流程）
  await cartApi.add({ skuId: selectedSku.value.id, quantity: quantity.value })
  cartStore.refresh()
  ElMessage.success('已加入购物车，请确认结算')
  router.push('/cart')
}

function onImgError(e) { e.target.src = 'https://picsum.photos/seed/fallback/480/480' }

onMounted(load)
</script>

<style scoped>
.crumb { margin-bottom: 14px; }
.detail-wrap {
  display: flex; gap: 28px; background: var(--clr-bg-card);
  border-radius: var(--radius-lg); padding: 24px; box-shadow: var(--shadow-sm);
}
.gallery { flex: 0 0 420px; }
.main-img {
  width: 100%; aspect-ratio: 1/1; border-radius: var(--radius-md); overflow: hidden;
  background: #f7f7f7; border: 1px solid var(--clr-border-light);
}
.main-img img { width: 100%; height: 100%; object-fit: cover; }
.thumb-strip { display: flex; gap: 8px; margin-top: 10px; overflow-x: auto; padding-bottom: 2px; }
.thumb-item {
  flex: 0 0 64px; width: 64px; height: 64px; border-radius: var(--radius-sm);
  overflow: hidden; cursor: pointer; border: 2px solid transparent; opacity: .8;
  transition: all .15s;
}
.thumb-item img { width: 100%; height: 100%; object-fit: cover; display: block; }
.thumb-item:hover { opacity: 1; }
.thumb-item.active { border-color: var(--clr-primary); opacity: 1; }

.buy-panel { flex: 1; min-width: 0; }
.name { font-size: 22px; font-weight: 700; color: var(--clr-text); margin: 0 0 6px; }
.sub { color: var(--clr-text-3); font-size: 14px; margin-bottom: 14px; }

.price-box {
  background: var(--clr-primary-bg); border-radius: var(--radius-md);
  padding: 14px 16px; margin-bottom: 18px;
}
.price-row { display: flex; align-items: center; gap: 12px; }
.price-row .label { color: var(--clr-text-3); font-size: 13px; }
.price { color: var(--clr-danger); font-size: 30px; font-weight: 800; }

.sku-section { margin-bottom: 18px; }
.sku-label { font-size: 14px; color: var(--clr-text-2); margin-bottom: 8px; }
.sku-list { display: flex; gap: 10px; flex-wrap: wrap; }
.sku-card {
  border: 1px solid var(--clr-border); border-radius: var(--radius-md);
  padding: 10px 16px; cursor: pointer; text-align: center; min-width: 108px;
  transition: all .15s;
}
.sku-card:hover { border-color: var(--clr-primary); }
.sku-card.active { border-color: var(--clr-primary); background: var(--clr-primary-bg); }
.sku-card .sku-name { font-size: 14px; font-weight: 600; }
.sku-card .sku-price { font-size: 14px; color: var(--clr-danger); margin-top: 4px; }
.sku-card .sku-stock { font-size: 12px; color: var(--clr-text-3); margin-top: 2px; }

.buy-row { margin-bottom: 16px; }
.buy-row .qty { display: flex; align-items: center; gap: 12px; }
.buy-row .label { color: var(--clr-text-2); font-size: 14px; }
.action-row { display: flex; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.btn-add { flex: 1; min-width: 150px; }
.btn-buy { flex: 1; min-width: 150px; }
.btn-ai { flex: 0 0 auto; }
.sold { color: var(--clr-text-4); font-size: 13px; }

/* 胶囊步进器 / 吸底购买条：桌面端一律不出现 */
.stepper { display: none; }
.buy-bar { display: none; }

.detail-section { margin-top: 18px; padding: 8px 20px; }
.detail-text { line-height: 1.9; color: var(--clr-text-2); white-space: pre-wrap; }
.meta-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.meta-item { display: flex; flex-direction: column; gap: 4px; }
.meta-item .k { color: var(--clr-text-3); font-size: 13px; }
.meta-item .v { color: var(--clr-text); font-size: 14px; }

/* =====================================================================
   移动端适配（≤ 768px）
   策略：左图右信息 → 单列堆叠（图在上、信息在下）；
        面包屑下线、规格改横向 chips、数量改胶囊步进器；
        下单入口从文中"抬"到吸底购买条（避开全局标签栏）——这才是详情页的转化位。
   ===================================================================== */
@media (max-width: 768px) {
  /* 面包屑 12px 高，手机上没有可点价值（返回靠系统手势/返回键） */
  .crumb { display: none; }

  .detail-wrap { flex-direction: column; gap: 14px; padding: 12px; }
  .gallery { flex: 0 0 auto; width: 100%; }
  .thumb-strip { -webkit-overflow-scrolling: touch; }
  .thumb-item { flex: 0 0 56px; width: 56px; height: 56px; }

  .name { font-size: 18px; }
  .sub { margin-bottom: 10px; }

  .price-box { padding: 12px; margin-bottom: 14px; }
  .price { font-size: 24px; }

  /* 规格：横向 chips —— 名称 + 价格同一行，库存退成中性灰（此前库存和价格同为红色，
     抢了价格的重点；且竖排三行 326×85 一屏放不下几个规格） */
  .sku-section { margin-bottom: 14px; }
  .sku-list { flex-direction: column; gap: 8px; }
  .sku-card {
    width: 100%; min-width: 0; flex: 0 0 auto;
    display: flex; align-items: center; gap: 10px;
    min-height: 44px; padding: 10px 16px; text-align: left;
  }
  .sku-card .sku-name {
    flex: 1; min-width: 0; font-size: 14px;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  .sku-card .sku-price { margin-top: 0; font-weight: 700; }
  .sku-card .sku-stock { margin-top: 0; color: #909399; }

  /* 数量：44px 圆角胶囊 [−] n [+] */
  .qty { width: 100%; justify-content: space-between; }
  .qty-num { display: none; }
  .stepper {
    display: inline-flex; align-items: center;
    height: 44px; border-radius: 22px; background: #f4f5f7; overflow: hidden;
  }
  .stp {
    width: 44px; height: 44px; padding: 0; border: 0; background: transparent;
    display: inline-flex; align-items: center; justify-content: center;
    font-size: 20px; line-height: 1; color: var(--clr-text); cursor: pointer;
    -webkit-tap-highlight-color: transparent;
  }
  .stp:active { background: rgba(0, 0, 0, .07); }
  .stp:disabled { color: var(--clr-text-4); }
  .stp-val { min-width: 32px; text-align: center; font-size: 15px; font-weight: 600; }

  /* 文中操作行收掉：手机端的加购/立即购买统一由吸底购买条承担 */
  .action-row { display: none; }

  /* 登录引导块：窄屏竖排居中，占满整行 */
  .buy-panel > .login-hint { flex-direction: column; text-align: center; padding: 14px 12px; gap: 10px; }

  .detail-section { margin-top: 14px; padding: 4px 12px; }
  .meta-grid { grid-template-columns: repeat(2, 1fr); gap: 10px; }

  /* ---- 吸底购买条 ---- */
  .buy-bar {
    display: flex; align-items: center; gap: 8px;
    position: fixed; left: 0; right: 0;
    /* ★ 钉在全局标签栏之上（+1px 是标签栏的上边框），两者互不遮挡 */
    bottom: calc(var(--tabbar-h) + var(--safe-b) + 1px);
    z-index: 110;
    min-height: 52px; padding: 6px 10px;
    background: rgba(255, 255, 255, .98);
    backdrop-filter: saturate(180%) blur(10px);
    border-top: 1px solid var(--clr-border);
    box-shadow: 0 -2px 10px rgba(0, 0, 0, .06);
  }
  .bar-mini {
    flex: 0 0 46px; height: 44px; padding: 0;
    border: 0; background: transparent; cursor: pointer;
    display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 1px;
    color: var(--clr-text-2); font-size: 10px; line-height: 1.1;
    -webkit-tap-highlight-color: transparent;
  }
  .bar-mini:active { opacity: .6; }
  .bar-mini.is-on { color: var(--clr-primary); }
  .bar-ico { font-size: 18px; }
  .bar-btn {
    height: 44px; border: 0; border-radius: 22px;
    color: #fff; font-size: 15px; font-weight: 600; cursor: pointer;
    -webkit-tap-highlight-color: transparent;
  }
  .bar-btn:active { opacity: .88; }
  .bar-btn:disabled { background: #d9d9d9; color: #fff; cursor: not-allowed; }
  .bar-add { flex: 2 1 0; background: var(--clr-warning); }
  .bar-buy { flex: 3 1 0; background: var(--clr-primary); }
}

@media (max-width: 480px) {
  .meta-grid { grid-template-columns: 1fr; }
}
</style>
