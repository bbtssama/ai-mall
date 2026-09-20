<template>
  <div class="notes-page">
    <!--
      工具栏：排序分段 + 搜索入口 + 发布。
      移动端收成一行 40px —— 此前页面内另有一个搜索框，和全局吸顶搜索（App.vue）上下叠着、
      形状还不一致（pill vs 5px 方角），白占约 64px 首屏；现在改成图标按钮，点开才展开输入框，
      复用同一个 keyword / reload 逻辑，不新增任何请求分支。
    -->
    <div class="topbar">
      <el-input
        ref="searchRef"
        v-model="keyword"
        placeholder="搜索种草笔记…"
        clearable
        class="search"
        :class="{ 'is-open': searchOpen }"
        @keyup.enter="reload"
        @clear="reload"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-radio-group v-model="orderBy" size="small" class="order-seg" @change="reload">
        <el-radio-button value="hot">热门</el-radio-button>
        <el-radio-button value="newest">最新</el-radio-button>
      </el-radio-group>

      <button
        class="search-toggle"
        type="button"
        :aria-label="searchOpen ? '收起搜索' : '搜索笔记'"
        @click="toggleSearch"
      >
        <el-icon><component :is="searchOpen ? Close : Search" /></el-icon>
      </button>

      <el-button type="primary" class="btn-publish" @click="goCreate">
        <el-icon style="margin-right:4px"><EditPen /></el-icon>发笔记
      </el-button>
    </div>

    <!-- 瀑布流卡片 -->
    <div v-if="loading" class="loading"><el-skeleton :rows="4" animated /></div>
    <div v-else-if="notes.length === 0" class="empty">
      <el-empty description="还没有笔记，点右上角发布第一篇吧" />
    </div>
    <template v-else>
      <div class="masonry">
        <div v-for="n in notes" :key="n.id" class="card pressable" @click="open(n.id)">
          <img v-if="n.cover" :src="n.cover" class="cover" loading="lazy" />
          <div class="body">
            <div class="title">{{ n.title }}</div>
            <div class="summary">{{ n.summary }}</div>
            <div class="meta">
              <!-- 移动端把「作者 / 时间」竖排，两列窄卡片下比横铺更耐看；桌面端时间隐藏，布局不变 -->
              <span class="meta-left">
                <span class="author">{{ n.authorName }}</span>
                <span class="card-time">{{ formatTime(n.createdAt) }}</span>
              </span>
              <span class="stats">
                <el-icon><Pointer /></el-icon>{{ formatCount(n.likeCount) }}
                <el-icon style="margin-left:8px"><Star /></el-icon>{{ formatCount(n.collectCount) }}
              </span>
            </div>
            <div v-if="n.tags?.length" class="tags">
              <el-tag v-for="t in n.tags.slice(0, 3)" :key="t" size="small" effect="plain">#{{ t }}</el-tag>
            </div>
          </div>
        </div>
      </div>
      <!-- 列表尾部：桌面保持原描边按钮；移动端换成居中的灰字（无边框无底） -->
      <div class="more">
        <el-button class="more-btn" :loading="loadingMore" @click="loadMore">
          {{ hasMore ? '加载更多' : '没有更多了' }}
        </el-button>
        <button
          v-if="hasMore"
          class="more-end is-tap pressable"
          type="button"
          :disabled="loadingMore"
          @click="loadMore"
        >{{ loadingMore ? '加载中…' : '加载更多' }}</button>
        <span v-else class="more-end">已经到底啦</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { noteApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { formatTime, formatCount } from '../utils/format'
import { Search, Close, EditPen, Pointer, Star } from '@element-plus/icons-vue'

const router = useRouter()
const auth = useAuthStore()
const notes = ref([])
const keyword = ref('')
const orderBy = ref('hot')
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const searchOpen = ref(false)
const searchRef = ref(null)
// 游标：与后端排序配套（hot 是二元组，newest 只要 id）
const cursor = ref({ id: null, hot: null })

/** 移动端的搜索入口：点开才展开输入框，展开后自动聚焦（少一次点击） */
function toggleSearch () {
  searchOpen.value = !searchOpen.value
  if (searchOpen.value) nextTick(() => searchRef.value?.focus())
}

async function fetchPage (isReload) {
  const params = {
    page: 1,
    pageSize: 20,
    orderBy: orderBy.value,
    keyword: keyword.value || undefined
  }
  if (!isReload && cursor.value.id != null) {
    params.cursorId = cursor.value.id
    if (orderBy.value === 'hot') params.cursorHot = cursor.value.hot
  }
  const res = await noteApi.page(params)
  const list = res.records || []
  notes.value = isReload ? list : [...notes.value, ...list]
  hasMore.value = list.length >= 20
  if (list.length) {
    const last = list[list.length - 1]
    cursor.value = { id: last.id, hot: last.hotScore }
  }
}

function reload () { cursor.value = { id: null, hot: null }; fetchPage(true) }
function loadMore () { loadingMore.value = true; fetchPage(false).finally(() => (loadingMore.value = false)) }
function open (id) { router.push(`/notes/${id}`) }

/** 发笔记需要登录：未登录只提示并引导登录，不直接跳受守卫保护的 /notes/create */
function goCreate () {
  if (!auth.token) {
    ElMessage.warning('该操作需要登录')
    return router.push({ path: '/login', query: { redirect: '/notes/create' } })
  }
  router.push('/notes/create')
}

onMounted(async () => {
  loading.value = true
  try { await fetchPage(true) } finally { loading.value = false }
})
</script>

<style scoped>
.notes-page { max-width: 1100px; margin: 0 auto; padding: 16px; }
.topbar { display: flex; gap: 12px; margin-bottom: 16px; }
.search { flex: 1; }
/* 搜索入口只服务移动端：桌面端本来就是常显输入框，图标按钮恒隐 */
.search-toggle { display: none; }
/* 列表尾部的灰字态只服务移动端 */
.more-end { display: none; }
/* 卡片时间只服务移动端（桌面端不显示，保持原有信息密度） */
.card-time { display: none; }
.loading, .empty, .more { display: flex; justify-content: center; padding: 32px 0; }
.masonry { column-count: 4; column-gap: 14px; }
@media (max-width: 1200px) { .masonry { column-count: 3; } }
@media (max-width: 900px) { .masonry { column-count: 2; } }
.card {
  break-inside: avoid; margin-bottom: 14px; border-radius: 10px; overflow: hidden;
  background: var(--el-bg-color); cursor: pointer;
  box-shadow: 0 1px 4px rgba(0,0,0,.06); transition: transform .15s, box-shadow .15s;
}
.card:hover { transform: translateY(-2px); box-shadow: 0 4px 14px rgba(0,0,0,.1); }
.cover { width: 100%; display: block; aspect-ratio: 4/3; object-fit: cover; }
.body { padding: 10px 12px 12px; }
.title { font-weight: 600; font-size: 14px; line-height: 1.4; margin-bottom: 6px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.summary { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.meta { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.stats { display: inline-flex; align-items: center; gap: 3px; }
.tags { margin-top: 6px; display: flex; gap: 4px; flex-wrap: wrap; }

/* =====================================================================
   移动端适配（≤ 768px）
   策略：① 工具栏压成一行 40px（分段 + 搜索图标 + 发布右对齐），删掉与全局顶栏重复的搜索框；
        ② 真·两列瀑布流（此前 ≤480px 会掉到单列，首屏只露 1.6 条），封面统一 3:4；
        ③ 卡片 meta 竖排小灰字 + 时间走 formatTime；尾部换成居中灰字。
   桌面端（>768px）结构、尺寸、配色均未改动。
   ===================================================================== */
@media (max-width: 768px) {
  .notes-page { padding: 0; }

  /* ---- ① 工具栏 ---- */
  .topbar { flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 12px; }
  /* 搜索框展开时换行到第二行（order 保证它排在本行元素之后，而不是插在分段前面） */
  .search { display: none; order: 2; flex: 1 1 100%; margin-top: 8px; }
  .search.is-open { display: block; }
  .search :deep(.el-input__inner) { font-size: 16px; }   /* ≥16px 防 iOS 聚焦缩放 */

  /* 视觉 40px 圆形（与 segmented 同高），但盒模型做到 44px —— 满足触摸目标 ≥44px */
  .search-toggle {
    display: inline-flex; align-items: center; justify-content: center;
    width: 44px; height: 44px; flex: 0 0 44px; padding: 2px;
    border: none; border-radius: var(--radius-full);
    background: var(--el-fill-color-light); background-clip: content-box;
    color: var(--clr-text-2); font-size: 18px;
    cursor: pointer; -webkit-tap-highlight-color: transparent;
    transition: transform .12s ease, background-color .12s ease;
  }
  .search-toggle:active { transform: scale(.94); background: var(--el-fill-color); }

  /* 分段控件：el-radio-button 是桌面形态（方角激活块 + 竖分隔线），
     窄屏换成 40px 胶囊 segmented —— 激活块与容器同圆角、去掉全部分隔线。 */
  .order-seg {
    flex: 0 0 auto; height: 40px; padding: 3px;
    border-radius: var(--radius-full); background: var(--el-fill-color-light);
  }
  .order-seg :deep(.el-radio-button__inner) {
    height: 34px; padding: 0 16px; display: flex; align-items: center;
    border: none; background: transparent; box-shadow: none !important;
    border-radius: var(--radius-full) !important;
    font-size: 14px; color: var(--clr-text-2); transition: color .15s, background-color .15s;
  }
  .order-seg :deep(.el-radio-button:first-child .el-radio-button__inner),
  .order-seg :deep(.el-radio-button:last-child .el-radio-button__inner) {
    border-radius: var(--radius-full) !important;
  }
  .order-seg :deep(.el-radio-button.is-active .el-radio-button__original-radio:not(:disabled) + .el-radio-button__inner) {
    background: var(--clr-primary); color: #fff;
  }
  .btn-publish { margin-left: auto; }

  /* ---- ② 两列瀑布流 ---- */
  .masonry { column-count: 2; column-gap: 10px; }
  .card { margin-bottom: 10px; border-radius: var(--r-md); box-shadow: 0 1px 3px rgba(0,0,0,.05); }
  /* 触屏没有 hover，桌面那套上浮位移会在点按后"粘"住 */
  .card:hover { transform: none; box-shadow: 0 1px 3px rgba(0,0,0,.05); }
  .cover { aspect-ratio: 3/4; }
  .body { padding: 8px 10px 10px; }
  .title { font-size: 13px; margin-bottom: 4px; }
  .summary { -webkit-line-clamp: 2; }

  /* ---- ③ 卡片信息 ---- */
  .meta { margin-top: 6px; gap: 6px; }
  .meta-left { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
  .author { font-size: 12px; color: var(--clr-text-2); max-width: 92px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .card-time { display: block; font-size: 11px; color: var(--clr-text-4); }
  .stats { flex: 0 0 auto; font-size: 12px; color: var(--clr-text-3); }
  .tags { margin-top: 6px; gap: 6px; }
  /* 标签在窄卡片里退成小灰字，且最多留 2 个，避免把卡片撑高 */
  .tags :deep(.el-tag) {
    height: auto; padding: 0; border: none; background: transparent;
    font-size: 11px; color: var(--clr-text-4);
  }
  .tags :deep(.el-tag:nth-child(n + 3)) { display: none; }

  /* ---- 尾部 ---- */
  .loading, .empty, .more { padding: 20px 0; }
  .more-btn { display: none; }
  .more-end {
    display: inline-flex; align-items: center; justify-content: center;
    min-height: 44px; padding: 0 20px;
    border: none; background: transparent;
    font-size: 13px; color: var(--clr-text-3);
  }
  .more-end.is-tap { color: var(--clr-text-2); cursor: pointer; }
}
</style>
