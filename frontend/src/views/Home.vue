<template>
  <div>
    <div class="banner">
      <h1>好物种草，AI 帮你挑</h1>
      <p>问 AI 助手，秒懂商品参数，一键加入购物车</p>
      <el-button type="danger" round @click="$router.push('/chat')">问 AI 助手</el-button>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6" v-for="p in list" :key="p.id" class="card-col">
        <el-card class="product-card" shadow="hover" :body-style="{ padding: '0' }" @click="goDetail(p.id)">
          <img :src="p.mainImg" class="cover" alt="" @error="onImgError" />
          <div class="info">
            <div class="name">{{ p.spuName }}</div>
            <div class="sub">{{ p.subTitle }}</div>
            <div class="price-row">
              <span class="price">¥{{ p.minPrice }}</span>
              <span class="start">起</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="pager" v-if="total > 0">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="query.pageSize"
                     v-model:current-page="query.page" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '../api'

const route = useRoute()
const router = useRouter()

const list = ref([])
const total = ref(0)
const loading = ref(false)
// 页码从 URL query 初始化：进入详情再返回（或刷新）时能恢复到原页码，而不是重置回第 1 页
const query = reactive({ page: Number(route.query.page) || 1, pageSize: 8 })

// 翻页时把页码同步到 URL（router.replace 不新增历史记录，返回仍能回到 ?page=N）
watch(() => query.page, (p) => {
  router.replace({ query: { ...route.query, page: p } })
})

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

onMounted(load)
</script>

<style scoped>
.banner {
  background: linear-gradient(135deg, #ff9a6c, #ff5e3a);
  color: #fff; border-radius: 12px; padding: 32px; margin-bottom: 20px;
}
.banner h1 { margin-bottom: 8px; }
.banner p { margin-bottom: 16px; opacity: 0.9; }
.card-col { margin-bottom: 16px; }
.product-card { cursor: pointer; }
.cover { width: 100%; height: 220px; object-fit: cover; display: block; }
.info { padding: 12px; }
.name { font-weight: 600; font-size: 15px; margin-bottom: 4px; }
.sub { color: #999; font-size: 12px; margin-bottom: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.price { color: #e8562c; font-size: 20px; font-weight: 700; }
.start { color: #999; font-size: 12px; margin-left: 2px; }
.pager { display: flex; justify-content: center; margin: 20px 0; }
</style>