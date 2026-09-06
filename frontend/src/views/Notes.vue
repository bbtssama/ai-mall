<template>
  <div class="notes-page">
    <!-- 顶栏：搜索 + 发布入口 -->
    <div class="topbar">
      <el-input
        v-model="keyword" placeholder="搜索种草笔记…" clearable class="search"
        @keyup.enter="reload" @clear="reload">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-radio-group v-model="orderBy" size="small" @change="reload">
        <el-radio-button value="hot">热门</el-radio-button>
        <el-radio-button value="newest">最新</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="$router.push('/notes/create')">
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
        <div v-for="n in notes" :key="n.id" class="card" @click="open(n.id)">
          <img v-if="n.cover" :src="n.cover" class="cover" loading="lazy" />
          <div class="body">
            <div class="title">{{ n.title }}</div>
            <div class="summary">{{ n.summary }}</div>
            <div class="meta">
              <span class="author">{{ n.authorName }}</span>
              <span class="stats">
                <el-icon><Pointer /></el-icon>{{ fmt(n.likeCount) }}
                <el-icon style="margin-left:8px"><Star /></el-icon>{{ fmt(n.collectCount) }}
              </span>
            </div>
            <div v-if="n.tags?.length" class="tags">
              <el-tag v-for="t in n.tags.slice(0, 3)" :key="t" size="small" effect="plain">#{{ t }}</el-tag>
            </div>
          </div>
        </div>
      </div>
      <div class="more">
        <el-button :loading="loadingMore" @click="loadMore">
          {{ hasMore ? '加载更多' : '没有更多了' }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { noteApi } from '../api'
import { Search, EditPen, Pointer, Star } from '@element-plus/icons-vue'

const router = useRouter()
const notes = ref([])
const keyword = ref('')
const orderBy = ref('hot')
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
// 游标：与后端排序配套（hot 是二元组，newest 只要 id）
const cursor = ref({ id: null, hot: null })

const fmt = (n) => n >= 10000 ? (n / 10000).toFixed(1) + 'w' : n >= 1000 ? (n / 1000).toFixed(1) + 'k' : n

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

onMounted(async () => {
  loading.value = true
  try { await fetchPage(true) } finally { loading.value = false }
})
</script>

<style scoped>
.notes-page { max-width: 1100px; margin: 0 auto; padding: 16px; }
.topbar { display: flex; gap: 12px; margin-bottom: 16px; }
.search { flex: 1; }
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
</style>
