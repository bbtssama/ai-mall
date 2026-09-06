<template>
  <div class="note-detail" v-if="note">
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
            <div class="time">{{ note.createdAt }}</div>
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
          <el-button round :type="note.liked ? 'danger' : ''" @click="toggleLike">
            <el-icon><Pointer /></el-icon>{{ note.likeCount }}
          </el-button>
          <el-button round :type="note.collected ? 'warning' : ''" @click="toggleCollect">
            <el-icon><Star /></el-icon>{{ note.collectCount }}
          </el-button>
          <span class="views"><el-icon><View /></el-icon>{{ note.viewCount }} 浏览</span>
          <el-button v-if="note.mine" round type="danger" plain style="margin-left:auto" @click="offline">下架</el-button>
        </div>
      </div>

      <!-- 右：种草清单（关联商品） -->
      <div class="side" v-if="note.products?.length">
        <div class="side-title">文中好物</div>
        <div v-for="p in note.products" :key="p.productId" class="product-card" @click="$router.push(`/product/${p.productId}`)">
          <img :src="p.mainImg" />
          <div class="p-info">
            <div class="p-name">{{ p.productName }}</div>
            <div v-if="p.remark" class="p-remark">“{{ p.remark }}”</div>
            <el-button size="small" type="primary" plain>去购买</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noteApi } from '../api'
import { Pointer, Star, View } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const note = ref(null)
const submitting = ref(false)

async function load () {
  note.value = await noteApi.detail(route.params.id)
}

async function toggleLike () {
  await noteApi.like(note.value.id, !note.value.liked)
  note.value.liked = !note.value.liked
  note.value.likeCount += note.value.liked ? 1 : -1
}

async function toggleCollect () {
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
@media (max-width: 900px) { .layout { flex-direction: column; } .side { width: 100%; position: static; } }
</style>
