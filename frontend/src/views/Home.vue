<template>
  <div class="home-page">
    <!--
      分类导航条：状态由 URL query 驱动（categoryId）。
      拆成两层是刻意的：
        · 外层 .cat-wrap = 卡片外观 + 右端渐隐遮罩
        · 内层 .cat-bar  = 真正的横滑容器
      遮罩不能直接挂在内层：伪元素在滚动容器里会跟着内容一起滑走，而 mask 会把
      卡片自己的白底也一并淡掉（右边缘"漏底"）。钉在外层才既能提示"右边还有内容"，
      又不动卡片本身。遮罩仅在真的还能向右滑时才出现，避免"滑到头了还在骗人有更多"。
    -->
    <div class="cat-wrap mall-card" :class="{ 'has-more': canScrollX }">
      <div ref="catRef" class="cat-bar" @scroll="syncCatScroll">
        <span
          v-for="c in categories"
          :key="c.id"
          class="cat-item pressable"
          :class="{ active: currentCatId === c.id }"
          @click="switchCat(c.id)"
        >{{ c.name }}</span>
      </div>
    </div>

    <!-- 搜索词横幅 -->
    <div v-if="route.query.keyword" class="search-head">
      搜索「<b>{{ route.query.keyword }}</b>」，共 {{ total }} 件商品
      <el-button text type="primary" size="small" @click="clearSearch">清空</el-button>
    </div>

    <!--
      Banner 轮播。手机端把 el-carousel 默认的指示点换掉：
      默认圆点在渐变底上几乎看不见（实测指示区只有 114×30，白点压橙底对比度极低），
      改成右下角 1/3 数字角标 —— 尺寸更小、对比度更高，也顺手把提示位置让给拇指区。
    -->
    <div v-if="!route.query.keyword && !currentCatId" class="banner-wrap">
      <el-carousel class="banner" height="220px" :interval="5000" arrow="hover" @change="onBannerChange">
        <el-carousel-item v-for="b in banners" :key="b.title">
          <div class="banner-item pressable" :style="{ background: b.bg }" @click="b.to ? $router.push(b.to) : null">
            <div class="banner-text">
              <div class="banner-title">{{ b.title }}</div>
              <div class="banner-sub">{{ b.sub }}</div>
            </div>
            <div class="banner-emoji">{{ b.emoji }}</div>
          </div>
        </el-carousel-item>
      </el-carousel>
      <div class="banner-count">{{ activeBanner + 1 }}/{{ banners.length }}</div>
    </div>

    <!-- 商品网格 -->
    <div class="section-title">{{ sectionTitle }}</div>
    <el-row :gutter="16" v-loading="loading">
      <el-col :xs="12" :sm="8" :md="6" v-for="p in list" :key="p.id" class="card-col">
        <div class="product-card pressable" @click="goDetail(p.id)">
          <div class="cover-wrap">
            <img :src="p.mainImg" class="cover" alt="" loading="lazy" @error="onImgError" />
          </div>
          <div class="info">
            <div class="name">{{ p.spuName }}</div>
            <div class="sub">{{ p.subTitle }}</div>
            <div class="price-row">
              <span class="price">¥{{ formatPrice(p.minPrice) }}</span>
              <span class="start">起</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-empty v-if="!list.length && !loading" description="没有找到相关商品">
      <el-button type="primary" @click="reset">看看全部商品</el-button>
    </el-empty>

    <!-- 列表结束态：单页数据时给一个明确的"到底了"，不要滚到底就凭空截断 -->
    <div v-if="list.length && total <= query.pageSize" ref="endRef" class="list-end" :class="{ show: showEnd }">
      已经到底啦
    </div>

    <div class="pager" v-if="total > query.pageSize">
      <el-pagination background layout="prev, pager, next"
                     :total="total" :page-size="query.pageSize"
                     v-model:current-page="query.page" @current-change="onPageChange" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '../api'
import { formatPrice } from '../utils/format'

const route = useRoute()
const router = useRouter()

const categories = [
  { id: null, name: '全部' },
  { id: 101, name: '数码影音' },
  { id: 102, name: '数码配件' },
  { id: 103, name: '美妆护肤' },
  { id: 104, name: '生活家居' }
]

const banners = [
  { title: '好物种草，AI 帮你挑', sub: '问 AI 助手，秒懂商品参数', emoji: '🤖', bg: 'linear-gradient(135deg,#ff7a45,#ff5000)', to: '/chat' },
  { title: '数码尖货 · 限时种草季', sub: '降噪耳机 / 快充 / 智能穿戴', emoji: '🎧', bg: 'linear-gradient(135deg,#4facfe,#00b7a3)' },
  { title: '美妆护肤 · 焕新', sub: '保湿 / 提亮 / 淡香', emoji: '💄', bg: 'linear-gradient(135deg,#f78ca0,#ffb6a3)' }
]

const list = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, pageSize: 12, keyword: '', categoryId: null })

// 当前分类从 URL 派生（单一来源，组件内不残留状态）
const currentCatId = computed(() => route.query.categoryId ? Number(route.query.categoryId) : null)
const sectionTitle = computed(() => {
  if (route.query.keyword) return '搜索结果'
  const c = categories.find(x => x.id === currentCatId.value)
  return c && c.id !== null ? `${c.name} · 好物` : '为你推荐'
})

// ---------- 分类条：只有真的还能向右滑，才亮出"可滑"暗示 ----------
const catRef = ref(null)
const canScrollX = ref(false)
function syncCatScroll() {
  const el = catRef.value
  if (!el) return
  canScrollX.value = el.scrollWidth - el.clientWidth - el.scrollLeft > 4
}

// ---------- Banner 角标：当前第几屏 ----------
const activeBanner = ref(0)
function onBannerChange(i) { activeBanner.value = i }

// ---------- 列表结束态：滚到底才淡入 ----------
const endRef = ref(null)
const showEnd = ref(false)
let endIO = null

watch(endRef, (el) => {
  endIO?.disconnect()
  showEnd.value = false
  if (!el || typeof IntersectionObserver === 'undefined') { showEnd.value = true; return }
  endIO = new IntersectionObserver(([entry]) => {
    if (entry.isIntersecting) { showEnd.value = true; endIO.disconnect() }
  })
  endIO.observe(el)
})

// URL 变化（搜索/分类/翻页/返回）→ 同步查询参数并重新加载
watch(() => route.query, (q) => {
  query.page = Number(q.page) || 1
  query.keyword = q.keyword || ''
  query.categoryId = currentCatId.value
  load()
}, { immediate: true })

onMounted(async () => {
  await nextTick()
  syncCatScroll()
  window.addEventListener('resize', syncCatScroll)
})
onBeforeUnmount(() => {
  endIO?.disconnect()
  window.removeEventListener('resize', syncCatScroll)
})

function switchCat(id) {
  // 切换分类：重置页码；保留搜索词做"分类内搜索"体验（或清空）。
  // 这里采用：切分类时清空搜索词，回到分类浏览
  const q = { page: 1 }
  if (id != null) q.categoryId = id
  router.push({ path: '/', query: q })
}

function onPageChange(page) {
  const q = { ...route.query, page }
  router.push({ path: '/', query: q })
}

function reset() {
  router.push({ path: '/' })
}

function clearSearch() {
  router.push({ path: '/' })
}

async function load() {
  loading.value = true
  try {
    const data = await productApi.page(query)
    list.value = data.records || []
    total.value = data.total || 0
    syncCatScroll()
  } finally {
    loading.value = false
  }
}

function goDetail(id) { router.push(`/product/${id}`) }
function onImgError(e) { e.target.src = 'https://picsum.photos/seed/fallback/480/480' }
</script>

<style scoped>
.home-page { }

/* 分类导航 */
.cat-wrap { position: relative; margin-bottom: 16px; }
.cat-bar {
  display: flex; gap: 4px; padding: 12px 16px;
  overflow-x: auto;
}
.cat-item {
  padding: 6px 18px; border-radius: var(--radius-full); cursor: pointer;
  font-size: 14px; color: var(--clr-text); white-space: nowrap;
  transition: all .15s;
}
.cat-item:hover { color: var(--clr-primary); }
.cat-item.active { background: var(--clr-primary); color: #fff; }

/* 右端渐隐：钉在卡片右边缘（不随内容滚动），仅在还能右滑时出现 */
.cat-wrap::after {
  content: ''; position: absolute; top: 0; right: 0; bottom: 0; width: 28px;
  pointer-events: none; display: none;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0), #fff 78%);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
}

/* 搜索横幅 */
.search-head {
  background: var(--clr-primary-bg); color: var(--clr-text-2);
  border-radius: var(--radius-md); padding: 12px 16px; margin-bottom: 16px; font-size: 14px;
}
.search-head b { color: var(--clr-primary); }

/* Banner */
.banner-wrap { position: relative; }
.banner { border-radius: var(--radius-lg); overflow: hidden; margin-bottom: 20px; }
.banner-item {
  height: 100%; display: flex; align-items: center; justify-content: space-between;
  padding: 0 48px; cursor: pointer; color: #fff; border-radius: var(--radius-lg);
}
.banner-title { font-size: 28px; font-weight: 800; margin-bottom: 8px; }
.banner-sub { font-size: 15px; opacity: .92; }
.banner-emoji { font-size: 72px; }
/* 1/3 角标只在手机端出现（桌面端沿用 el-carousel 默认圆点） */
.banner-count { display: none; }

/* 区块标题 */
.section-title {
  font-size: 18px; font-weight: 700; color: var(--clr-text);
  margin-bottom: 14px; padding-left: 10px; border-left: 4px solid var(--clr-primary);
}

/* 商品卡片 */
.card-col { margin-bottom: 16px; }
.product-card {
  background: var(--clr-bg-card); border-radius: var(--radius-md);
  overflow: hidden; cursor: pointer; box-shadow: var(--shadow-sm);
  transition: transform .15s, box-shadow .15s;
}
.product-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); }
.cover-wrap { width: 100%; aspect-ratio: 1 / 1; overflow: hidden; background: #f7f7f7; }
.cover { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform .2s; }
.product-card:hover .cover { transform: scale(1.04); }
.info { padding: 12px 14px 14px; }
.name {
  font-weight: 600; font-size: 15px; color: var(--clr-text);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.sub {
  color: var(--clr-text-3); font-size: 12px; margin-top: 4px; margin-bottom: 10px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.price-row { display: flex; align-items: baseline; }
.price { color: var(--clr-danger); font-size: 20px; font-weight: 700; }
.start { color: var(--clr-text-4); font-size: 12px; margin-left: 2px; }

/* 列表结束态（桌面端由分页器承担，这里不显示） */
.list-end { display: none; }

.pager { display: flex; justify-content: center; margin-top: 20px; }

/* =====================================================================
   移动端适配（≤ 768px）
   策略：Banner 整体压低一档、分类条横滑保留并补"可滑"暗示、
        单列信息密度收紧；桌面端（>768px）不受影响。
   ===================================================================== */
@media (max-width: 768px) {
  /* 分类条：横滑 + 触摸惯性；触摸目标抬到 40px（此前仅 31px，低于可点下限） */
  .cat-wrap { margin-bottom: 10px; }
  .cat-bar {
    padding: 4px 12px; gap: 4px;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }
  .cat-bar::-webkit-scrollbar { display: none; }
  .cat-item {
    display: inline-flex; align-items: center;
    min-height: 40px; padding: 9px 14px; font-size: 13px;
  }
  .cat-wrap.has-more::after { display: block; }

  .search-head { padding: 10px 12px; margin-bottom: 12px; font-size: 13px; }

  /* Banner：高度由内联 style 给定，用 :deep 覆盖；文字/插画同步缩一档 */
  .banner-wrap { margin-bottom: 12px; }
  .banner { margin-bottom: 0; }
  .banner :deep(.el-carousel__container) { height: 132px !important; }
  .banner :deep(.el-carousel__indicators) { display: none; }
  .banner-item { padding: 0 16px; }
  .banner-title { font-size: 18px; margin-bottom: 4px; }
  .banner-sub { font-size: 12px; }
  .banner-emoji { font-size: 40px; }
  .banner-count {
    display: block; position: absolute; right: 10px; bottom: 10px;
    padding: 1px 8px; border-radius: var(--radius-full);
    background: rgba(0, 0, 0, .3); color: #fff;
    font-size: 11px; line-height: 16px; letter-spacing: .5px;
    pointer-events: none;
  }

  .section-title { font-size: 16px; margin-bottom: 10px; padding-left: 8px; border-left-width: 3px; }

  /* 商品卡片：栅格已由 el-col 的 xs/sm/md 控制，这里只收内边距与字号 */
  .card-col { margin-bottom: 10px; }
  /* 圆角统一走 --r-md（12px）：图片被卡片的 overflow:hidden 裁切，不会出现"图直角+壳圆角"错位 */
  .product-card { border-radius: var(--r-md); }
  .info { padding: 10px 10px 12px; }
  .name { font-size: 14px; }
  .sub { margin-bottom: 6px; }
  .price { font-size: 17px; }

  .list-end {
    display: block; text-align: center; color: var(--clr-text-4);
    font-size: 12px; padding: 2px 0 10px; opacity: 0; transition: opacity .25s;
  }
  .list-end.show { opacity: 1; }

  .pager { margin-top: 14px; }
  /* 分页器此前因为 v-if 里的变量写错而从未渲染过，一露出来就是 32×32 的小方块（低于可点下限）。
     手机端抬到 44px 高；宽度受 320px 窄屏限制，压到 38px 并收紧间距，保证一行放得下。 */
  .pager :deep(.el-pager li),
  .pager :deep(.btn-prev),
  .pager :deep(.btn-next) {
    height: 44px; min-width: 38px; line-height: 44px;
    font-size: 14px; border-radius: var(--r-sm);
  }
  .pager :deep(.el-pager li) { margin: 0 1px; }
}
</style>
