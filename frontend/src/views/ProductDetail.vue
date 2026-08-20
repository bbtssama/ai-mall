<template>
  <div v-loading="loading">
    <el-card v-if="product" shadow="never">
      <el-row :gutter="24">
        <!-- 左侧图 -->
        <el-col :span="10">
          <img :src="product.mainImg" class="detail-img" @error="onImgError" />
        </el-col>
        <!-- 右侧信息 -->
        <el-col :span="14">
          <h2 class="name">{{ product.spuName }}</h2>
          <p class="sub">{{ product.subTitle }}</p>
          <div class="price-box">
            <span class="price-label">价格</span>
            <span class="price">¥{{ selectedSku?.price ?? product.minPrice }}</span>
          </div>

          <div class="sku-box">
            <div class="sku-label">规格</div>
            <div class="sku-list">
              <el-tag v-for="s in product.skus" :key="s.id" class="sku" :type="selectedSku?.id === s.id ? 'danger' : 'info'"
                      effect="plain" @click="selectedSku = s">
                {{ s.skuName }} ¥{{ s.price }}
              </el-tag>
            </div>
            <div class="stock" v-if="selectedSku">库存 {{ selectedSku.stock }} 件 · 已售 {{ selectedSku.sales }}</div>
          </div>

          <div class="action-row">
            <el-input-number v-model="quantity" :min="1" :max="99" size="large" />
            <el-button type="danger" size="large" :disabled="!selectedSku" @click="addCart">加入购物车</el-button>
            <el-button type="warning" size="large" :disabled="!selectedSku" @click="buyNow">立即购买</el-button>
            <el-button size="large" @click="$router.push('/chat')">🤖 问 AI</el-button>
          </div>
        </el-col>
      </el-row>

      <el-divider />
      <h3>商品详情</h3>
      <p class="detail-text">{{ product.detail }}</p>
    </el-card>

    <el-empty v-else description="商品不存在或已下架">
      <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { productApi, cartApi, orderApi } from '../api'

const route = useRoute()
const router = useRouter()

const product = ref(null)
const loading = ref(false)
const selectedSku = ref(null)
const quantity = ref(1)

async function load() {
  loading.value = true
  try {
    product.value = await productApi.detail(route.params.id)
    selectedSku.value = product.value.skus?.[0] || null
  } finally {
    loading.value = false
  }
}

async function addCart() {
  await cartApi.add({ skuId: selectedSku.value.id, quantity: quantity.value })
  ElMessage.success('已加入购物车')
}

async function buyNow() {
  // 直接购买：跳过购物车下单
  const order = await orderApi.create({
    items: [{ skuId: selectedSku.value.id, quantity: quantity.value }],
    receiverName: '演示用户',
    receiverPhone: '13800138000',
    receiverAddress: '上海市浦东新区 xx 路 100 号'
  })
  ElMessage.success(`下单成功：${order.orderNo}`)
  router.push('/orders')
}

function onImgError(e) { e.target.src = 'https://picsum.photos/seed/fallback/480/480' }

onMounted(load)
</script>

<style scoped>
.detail-img { width: 100%; border-radius: 8px; }
.name { margin-bottom: 6px; }
.sub { color: #999; margin-bottom: 16px; }
.price-box { background: #fff6f4; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px; }
.price-label { color: #999; margin-right: 12px; }
.price { color: #e8562c; font-size: 28px; font-weight: 700; }
.sku-box { margin-bottom: 20px; }
.sku-label { color: #666; margin-bottom: 8px; }
.sku { margin: 0 8px 8px 0; cursor: pointer; font-size: 14px; }
.stock { color: #999; font-size: 12px; margin-top: 4px; }
.action-row { display: flex; gap: 12px; align-items: center; }
.detail-text { line-height: 1.8; color: #444; }
</style>