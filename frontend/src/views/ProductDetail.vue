<template>
  <div v-loading="loading">
    <el-breadcrumb separator="/" class="crumb">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>{{ product?.spuName || '商品详情' }}</el-breadcrumb-item>
    </el-breadcrumb>

    <div v-if="product" class="detail-wrap">
      <!-- 左：主图 -->
      <div class="gallery">
        <div class="main-img">
          <img :src="product.mainImg" @error="onImgError" />
        </div>
      </div>

      <!-- 右：信息 -->
      <div class="buy-panel">
        <h1 class="name">{{ product.spuName }}</h1>
        <div class="sub">{{ product.subTitle }}</div>

        <div class="price-box">
          <div class="price-row">
            <span class="label">价格</span>
            <span class="price">¥{{ selectedSku?.price ?? product.minPrice }}</span>
          </div>
        </div>

        <!-- SKU 规格选择：卡片式，选中态明显 -->
        <div class="sku-section">
          <div class="sku-label">选择规格</div>
          <div class="sku-list">
            <div v-for="s in product.skus" :key="s.id" class="sku-card"
                 :class="{ active: selectedSku?.id === s.id }" @click="selectedSku = s">
              <div class="sku-name">{{ s.skuName }}</div>
              <div class="sku-price">¥{{ s.price }}</div>
              <div class="sku-stock">库存 {{ s.stock }}</div>
            </div>
          </div>
        </div>

        <!-- 数量 + 操作 -->
        <div class="buy-row">
          <div class="qty">
            <span class="label">数量</span>
            <el-input-number v-model="quantity" :min="1" :max="selectedSku?.stock || 99" size="default" />
          </div>
        </div>
        <div class="action-row">
          <el-button type="warning" size="large" class="btn-add" :disabled="!selectedSku || selectedSku.stock <= 0"
                     @click="addCart">加入购物车</el-button>
          <el-button type="danger" size="large" class="btn-buy" :disabled="!selectedSku || selectedSku.stock <= 0"
                     @click="buyNow">立即购买</el-button>
          <el-button size="large" class="btn-ai" @click="$router.push('/chat')">🤖 问 AI</el-button>
        </div>
        <div class="sold">已售 {{ soldTotal }} 件 · 支持 7 天无理由退换</div>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { productApi, cartApi } from '../api'
import { useCartStore } from '../stores/cart'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const product = ref(null)
const loading = ref(false)
const selectedSku = ref(null)
const quantity = ref(1)
const openPanels = ref(['detail'])

const CATEGORY = { 101: '数码影音', 102: '数码配件', 103: '美妆护肤', 104: '生活家居' }
const categoryName = computed(() => (product.value ? (CATEGORY[product.value.categoryId] || '未分类') : ''))
const soldTotal = computed(() => (product.value?.skus || []).reduce((s, x) => s + (x.sales || 0), 0))

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
  } finally {
    loading.value = false
  }
}

async function addCart() {
  await cartApi.add({ skuId: selectedSku.value.id, quantity: quantity.value })
  cartStore.refresh()
  ElMessage.success('已加入购物车')
}

async function buyNow() {
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

.detail-section { margin-top: 18px; padding: 8px 20px; }
.detail-text { line-height: 1.9; color: var(--clr-text-2); white-space: pre-wrap; }
.meta-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.meta-item { display: flex; flex-direction: column; gap: 4px; }
.meta-item .k { color: var(--clr-text-3); font-size: 13px; }
.meta-item .v { color: var(--clr-text); font-size: 14px; }
</style>