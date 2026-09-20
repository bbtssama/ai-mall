<template>
  <!-- 加载中先占位，避免首屏空白（匿名进入同样适用） -->
  <el-skeleton v-if="loading" class="note-detail" :rows="6" animated />
  <el-empty v-else-if="!note" class="note-detail" description="笔记不存在或已下架" />
  <div class="note-detail" v-else>
    <!-- 驳回提示（作者本人可见） -->
    <el-alert v-if="note.mine && note.status === 'REJECTED'" type="error" :closable="false" class="reject-tip"
      :title="`审核未通过：${note.auditResult || '内容包含违规风险'}`">
      <el-button size="small" @click="resubmit" :loading="submitting">修改后重新送审</el-button>
    </el-alert>
    <el-alert v-else-if="note.mine && note.status === 'AUDITING'" type="info" :closable="false" class="reject-tip"
      title="审核中：AI 正在快审，通过后自动公开（通常几秒到十几秒）" />

    <div class="layout">
      <!-- 左：正文 -->
      <div class="main">
        <h1 class="title">{{ note.title }}</h1>
        <div class="author-bar">
          <el-avatar :size="32" :src="note.authorAvatar">{{ note.authorName?.[0] }}</el-avatar>
          <div>
            <div class="author">{{ note.authorName }}</div>
            <div class="time">{{ formatTime(note.createdAt) }}</div>
          </div>
        </div>
        <img v-if="note.cover" :src="note.cover" class="hero" />
        <div class="content">{{ note.content }}</div>
        <div v-if="note.images?.length" class="imgs">
          <img v-for="(u, i) in note.images" :key="i" :src="u" loading="lazy" />
        </div>
        <div v-if="note.tags?.length" class="tags">
          <el-tag v-for="t in note.tags" :key="t" effect="plain">#{{ t }}</el-tag>
        </div>

        <!-- 互动条 -->
        <div class="actions">
          <el-button round class="act-btn" :type="note.liked ? 'danger' : ''" @click="toggleLike">
            <el-icon><Pointer /></el-icon>{{ formatCount(note.likeCount) }}
          </el-button>
          <el-button round class="act-btn" :type="note.collected ? 'warning' : ''" @click="toggleCollect">
            <el-icon><Star /></el-icon>{{ formatCount(note.collectCount) }}
          </el-button>
          <span class="views"><el-icon><View /></el-icon>{{ formatCount(note.viewCount) }} 浏览</span>
          <el-button v-if="note.mine" round type="danger" plain style="margin-left:auto" @click="offline">下架</el-button>
        </div>
      </div>

      <!-- 右：种草清单（关联商品） -->
      <div class="side" v-if="note.products?.length">
        <div class="side-title">文中好物</div>
        <div v-for="p in note.products" :key="p.productId" class="product-card" @click="openProduct(p.productId)">
          <img :src="p.mainImg" />
          <div class="p-info">
            <div class="p-name">{{ p.productName }}</div>
            <div v-if="p.remark" class="p-remark">“{{ p.remark }}”</div>
            <el-button size="small" type="primary" plain class="buy-btn">去购买</el-button>
          </div>
        </div>
      </div>
    </div>

    <!--
      移动端吸底转化条：正文很长时「文中好物」会被埋在两屏之外，
      这里把主推好物 + 去购买常驻在拇指区（避让底部标签栏与 home indicator）。
      桌面端不渲染（display:none），保持原有右侧清单的形态。
    -->
    <div v-if="note.products?.length" class="buy-bar">
      <img class="buy-bar-img" :src="note.products[0].mainImg" alt="" />
      <div class="buy-bar-info">
        <div class="buy-bar-name">{{ note.products[0].productName }}</div>
        <div class="buy-bar-sub">
          文中好物{{ note.products.length > 1 ? ` · 共 ${note.products.length} 件` : '' }}
        </div>
      </div>
      <button class="buy-bar-btn pressable" type="button" @click="openProduct(note.products[0].productId)">
        去购买
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noteApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { formatTime, formatCount } from '../utils/format'
import { Pointer, Star, View } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const note = ref(null)
const loading = ref(false)
const submitting = ref(false)

function openProduct (id) { router.push(`/product/${id}`) }

async function load () {
  loading.value = true
  try {
    note.value = await noteApi.detail(route.params.id)
  } finally {
    loading.value = false
  }
}

/** 未登录守卫：点赞/收藏等写操作只提示并引导登录，绝不发请求（否则 401） */
function requireLogin () {
  ElMessage.warning('该操作需要登录')
  router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  return false
}

async function toggleLike () {
  if (!auth.token) return requireLogin()
  await noteApi.like(note.value.id, !note.value.liked)
  note.value.liked = !note.value.liked
  note.value.likeCount += note.value.liked ? 1 : -1
}

async function toggleCollect () {
  if (!auth.token) return requireLogin()
  await noteApi.collect(note.value.id, !note.value.collected)
  note.value.collected = !note.value.collected
  note.value.collectCount += note.value.collected ? 1 : -1
}

async function resubmit () {
  submitting.value = true
  try {
    // 跳到编辑页修改后由那边送审；这里直接原样重提（简化流）
    await noteApi.resubmit(note.value.id)
    ElMessage.success('已重新送审')
    await load()
  } finally { submitting.value = false }
}

async function offline () {
  await ElMessageBox.confirm('下架后笔记不再公开，确定？', '下架笔记', { type: 'warning' })
  await noteApi.offline(note.value.id)
  ElMessage.success('已下架')
  router.push('/notes')
}

onMounted(load)
</script>

<style scoped>
.note-detail { max-width: 1100px; margin: 0 auto; padding: 16px; }
.reject-tip { margin-bottom: 14px; }
.layout { display: flex; gap: 20px; align-items: flex-start; }
.main { flex: 1; min-width: 0; background: var(--el-bg-color); border-radius: 12px; padding: 24px; }
.title { margin: 0 0 12px; font-size: 22px; }
.author-bar { display: flex; gap: 10px; align-items: center; margin-bottom: 16px; }
.author { font-weight: 600; font-size: 14px; }
.time { font-size: 12px; color: var(--el-text-color-secondary); }
.hero { width: 100%; border-radius: 10px; margin-bottom: 14px; }
.content { white-space: pre-wrap; line-height: 1.8; font-size: 15px; }
.imgs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 14px; }
.imgs img { width: 100%; border-radius: 8px; aspect-ratio: 1; object-fit: cover; }
.tags { margin-top: 14px; display: flex; gap: 6px; flex-wrap: wrap; }
.actions { display: flex; align-items: center; gap: 10px; margin-top: 20px; padding-top: 14px; border-top: 1px solid var(--el-border-color-lighter); }
.views { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; color: var(--el-text-color-secondary); }
.side { width: 300px; flex-shrink: 0; background: var(--el-bg-color); border-radius: 12px; padding: 16px; position: sticky; top: 70px; }
.side-title { font-weight: 600; margin-bottom: 12px; }
.product-card { display: flex; gap: 10px; padding: 10px; border-radius: 10px; cursor: pointer; transition: background .15s; }
.product-card:hover { background: var(--el-fill-color-light); }
.product-card img { width: 64px; height: 64px; border-radius: 8px; object-fit: cover; }
.p-info { flex: 1; min-width: 0; }
.p-name { font-size: 13px; font-weight: 500; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.p-remark { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 6px; }
/* 吸底转化条：仅移动端渲染 */
.buy-bar { display: none; }
@media (max-width: 900px) { .layout { flex-direction: column; } .side { width: 100%; position: static; } }

/* =====================================================================
   移动端适配（≤ 768px）
   策略：布局已由 900px 断点降为单列，这里做「可读性 + 转化 + 触摸目标」收口。
        ① 时间戳走 formatTime（不再是裸 ISO）；
        ② 唯一转化入口「去购买」从 70×34 浅色描边升为 44px 主色按钮，并加吸底转化条；
        ③ 互动条触摸目标 ≥44px + 按压反馈。
   桌面端（>768px）视觉未改动（时间戳格式化除外，见报告）。
   ===================================================================== */
@media (max-width: 768px) {
  .note-detail { padding: 0 0 72px; }   /* 给吸底转化条让位（标签栏高度由全局 layout 让出） */
  .reject-tip { margin-bottom: 10px; }

  .main { padding: 14px; border-radius: var(--r-md); }
  .title { font-size: 18px; margin-bottom: 10px; }
  .author-bar { margin-bottom: 12px; }
  .author { font-size: 13px; }
  .time { font-size: 11px; }

  /* 正文：字号与行高按阅读场景收紧，左右留白由 .main 的 14px 提供 */
  .hero { margin-bottom: 12px; }
  .content { font-size: 15px; line-height: 1.9; }
  .imgs { grid-template-columns: repeat(2, 1fr); gap: 6px; margin-top: 12px; }

  /* ---- ③ 互动条：触摸目标 ≥44px，按下有反馈 ---- */
  .actions { flex-wrap: wrap; gap: 8px; margin-top: 16px; padding-top: 12px; }
  .actions :deep(.act-btn) { min-height: 44px; min-width: 84px; }
  .actions :deep(.act-btn:active) { transform: scale(.96); }
  .views { font-size: 12px; }

  /* ---- 文中好物：单列铺满，商品行紧凑排列 ---- */
  .side { width: 100%; position: static; padding: 12px; border-radius: var(--r-md); }
  .side-title { margin-bottom: 8px; }
  .product-card { padding: 8px; gap: 8px; border-radius: var(--r-sm); }
  .product-card:active { background: var(--el-fill-color-light); }
  .product-card img { width: 56px; height: 56px; }
  .p-name { font-size: 13px; }

  /* ②「去购买」：浅色描边 → 醒目主色按钮（≥44px）。
     type/plain 保留给桌面，这里用更高优先级的选择器在窄屏覆盖成实心。 */
  .product-card :deep(.buy-btn.is-plain) {
    width: 100%; height: 44px; margin-top: 6px; padding: 0;
    border-radius: var(--radius-full); font-size: 15px; font-weight: 600;
    background-color: var(--clr-primary); border-color: var(--clr-primary); color: #fff;
  }
  .product-card :deep(.buy-btn.is-plain:hover) {
    background-color: var(--clr-primary-hover); border-color: var(--clr-primary-hover); color: #fff;
  }
  .product-card :deep(.buy-btn.is-plain:active) {
    background-color: var(--clr-primary-active); border-color: var(--clr-primary-active); color: #fff;
  }

  /* ---- ② 吸底转化条 ---- */
  .buy-bar {
    display: flex; align-items: center; gap: 10px;
    position: fixed; left: 0; right: 0; z-index: 110;
    /* 避让底部标签栏与 home indicator */
    bottom: calc(var(--tabbar-h) + var(--safe-b));
    padding: 8px 12px;
    background: rgba(255, 255, 255, .97);
    backdrop-filter: saturate(180%) blur(12px);
    border-top: 1px solid var(--clr-border);
    box-shadow: 0 -2px 10px rgba(0, 0, 0, .05);
  }
  .buy-bar-img { width: 40px; height: 40px; flex: 0 0 40px; border-radius: var(--r-sm); object-fit: cover; }
  .buy-bar-info { flex: 1; min-width: 0; }
  .buy-bar-name {
    font-size: 13px; font-weight: 600; color: var(--clr-text);
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  .buy-bar-sub { font-size: 11px; color: var(--clr-text-3); margin-top: 1px; }
  .buy-bar-btn {
    flex: 0 0 auto; height: 44px; min-width: 104px; padding: 0 20px;
    border: none; border-radius: var(--radius-full);
    background: linear-gradient(90deg, #ff6a2b, var(--clr-primary));
    color: #fff; font-size: 15px; font-weight: 600; cursor: pointer;
    -webkit-tap-highlight-color: transparent;
  }
}

@media (max-width: 480px) {
  .imgs { grid-template-columns: 1fr; }
}
</style>
