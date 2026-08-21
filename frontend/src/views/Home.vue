<template>
  <div class="home-page">
    <!-- 分类导航条：状态由 URL query 驱动（categoryId） -->
    <div class="cat-bar mall-card">
      <span
        v-for="c in categories"
        :key="c.id"
        class="cat-item"
        :class="{ active: currentCatId === c.id }"
        @click="switchCat(c.id)"
      >{{ c.name }}</span>
    </div>

    <!-- 搜索词横幅 -->
    <div v-if="route.query.keyword" class="search-head">
      搜索「<b>{{ route.query.keyword }}</b>」，共 {{ total }} 件商品
      <el-button text type="primary" size="small" @click="clearSearch">清空</el-button>
    </div>

    <!-- Banner 轮播 -->
    <el-carousel v-if="!route.query.keyword && !currentCatId" height="220px" class="banner" :interval="5000" arrow="hover">
      <el-carousel-item v-for="b in banners" :key="b.title">
        <div class="banner-item" :style="{ background: b.bg }" @click="b.to ? $router.push(b.to) : null">
          <div class="banner-text">
            <div class="banner-title">{{ b.title }}</div>
            <div class="banner-sub">{{ b.sub }}</div>
          </div>
          <div class="banner-emoji">{{ b.emoji }}</div>
        </div>
      </el-carousel-item>
    </el-carousel>

    <!-- 商品网格 -->
    <div class="section-title">{{ sectionTitle }}</div>
    <el-row :gutter="16" v-loading="loading">
      <el-col :xs="12" :sm="8" :md="6" v-for="p in list" :key="p.id" class="card-col">
        <div class="product-card" @click="goDetail(p.id)">
          <div class="cover-wrap">
            <img :src="p.mainImg" class="cover" alt="" loading="lazy" @error="onImgError" />
          </div>
          <div class="info">
            <div class="name">{{ p.spuName }}</div>
            <div class="sub">{{ p.subTitle }}</div>
            <div class="price-row">
              <span class="price">¥{{ p.minPrice }}</span>
              <span class="start">起</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-empty v-if="!list.length && !loading" description="没有找到相关商品">
      <el-button type="primary" @click="reset">看看全部商品</el-button>
    </el-empty>

    <div class="pager" v-if="total > pageSize">
      <el-pagination background layout="prev, pager, next"
                     :total="total" :page-size="query.pageSize"
                     v-model:current-page="query.page" @current-change="onPageChange" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '../api'

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

// URL 变化（搜索/分类/翻页/返回）→ 同步查询参数并重新加载
watch(() => route.query, (q) => {
  query.page = Number(q.page) || 1
  query.keyword = q.keyword || ''
  query.categoryId = currentCatId.value
  load()
}, { immediate: true })

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
.cat-bar {
  display: flex; gap: 4px; padding: 12px 16px; margin-bottom: 16px;
  overflow-x: auto;
}
.cat-item {
  padding: 6px 18px; border-radius: var(--radius-full); cursor: pointer;
  font-size: 14px; color: var(--clr-text); white-space: nowrap;
  transition: all .15s;
}
.cat-item:hover { color: var(--clr-primary); }
.cat-item.active { background: var(--clr-primary); color: #fff; }

/* 搜索横幅 */
.search-head {
  background: var(--clr-primary-bg); color: var(--clr-text-2);
  border-radius: var(--radius-md); padding: 12px 16px; margin-bottom: 16px; font-size: 14px;
}
.search-head b { color: var(--clr-primary); }

/* Banner */
.banner { border-radius: var(--radius-lg); overflow: hidden; margin-bottom: 20px; }
.banner-item {
  height: 100%; display: flex; align-items: center; justify-content: space-between;
  padding: 0 48px; cursor: pointer; color: #fff; border-radius: var(--radius-lg);
}
.banner-title { font-size: 28px; font-weight: 800; margin-bottom: 8px; }
.banner-sub { font-size: 15px; opacity: .92; }
.banner-emoji { font-size: 72px; }

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

.pager { display: flex; justify-content: center; margin-top: 20px; }
</style>