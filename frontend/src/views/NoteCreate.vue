<template>
  <div class="note-create">
    <div class="editor">
      <h2>发布种草笔记</h2>

      <!-- 第 1 步：AI 起草（可选） -->
      <el-card shadow="never" class="ai-card">
        <template #header>
          <span>✨ AI 帮你起草（可选）</span>
        </template>
        <div class="ai-row">
          <el-input v-model="aiBrief" placeholder="描述你的想法或场景，如：通勤用，预算 500，想要降噪耳机" />
          <el-select v-model="aiProductId" placeholder="关联商品(可选)" clearable filterable style="width:220px">
            <el-option v-for="p in products" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button type="primary" plain :loading="aiLoading" @click="generate">生成草稿</el-button>
        </div>
        <div class="ai-hint">AI 草稿仅供参考，发布前请务必自己审阅修改；发布后会经过 AI 审核。</div>
      </el-card>

      <!-- 第 2 步：编辑正文 -->
      <el-input v-model="form.title" placeholder="标题（20 字内更容易被点开哦）" maxlength="100" show-word-limit size="large" class="title-input" />
      <el-input v-model="form.content" type="textarea" :rows="12" maxlength="20000" show-word-limit
        placeholder="写下你的真实体验：什么场景、用了多久、感受如何、值不值…" class="content-input" />

      <!-- 图片 -->
      <div class="imgs">
        <div v-for="(u, i) in form.images" :key="u" class="img-item">
          <img :src="u" />
          <el-icon class="del" @click="form.images.splice(i, 1)"><CircleClose /></el-icon>
        </div>
        <el-upload v-if="form.images.length < 9" :show-file-list="false" :http-request="uploadImg" accept="image/*">
          <div class="upload-btn"><el-icon><Plus /></el-icon></div>
        </el-upload>
      </div>

      <!-- 标签与关联商品 -->
      <div class="row">
        <span class="label">标签</span>
        <el-select v-model="form.tags" multiple filterable allow-create default-first-option
          placeholder="选择或输入标签（最多 10 个）" style="flex:1">
          <el-option v-for="t in TAG_SUGGEST" :key="t" :label="'#' + t" :value="t" />
        </el-select>
      </div>
      <div class="row">
        <span class="label">好物</span>
        <el-select v-model="form.products" multiple filterable placeholder="关联你在用的商品（种草清单）" style="flex:1">
          <el-option v-for="p in products" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </div>

      <div class="submit-row">
        <el-button type="primary" size="large" :loading="submitting" @click="submit">发布</el-button>
        <span class="submit-hint">发布后秒级可见"审核中"，AI 快审通过后自动公开</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { noteApi, aiContentApi, fileApi, productApi } from '../api'
import { Plus, CircleClose } from '@element-plus/icons-vue'

const router = useRouter()
const TAG_SUGGEST = ['数码好物', '平价好物', '通勤必备', '学生党', '真实测评', '自用推荐', '颜值党', '性价比']

const form = ref({ title: '', content: '', images: [], tags: [], products: [] })
const products = ref([])
const submitting = ref(false)

// AI 起草
const aiBrief = ref('')
const aiProductId = ref(null)
const aiLoading = ref(false)

async function generate () {
  if (!aiBrief.value.trim()) return ElMessage.warning('先描述一下你的想法～')
  aiLoading.value = true
  try {
    const res = await aiContentApi.noteDraft({ brief: aiBrief.value, productId: aiProductId.value || undefined })
    // 后端已解析为结构化对象 {title, content, tags}（解析/降级都在服务端收口，
    // 前端不再 JSON.parse——早期字符串契约下 parse 一失败整串 JSON 会糊进正文框）
    const draft = res.draft || {}
    if (draft.title) form.value.title = draft.title
    if (draft.content) form.value.content = draft.content
    if (Array.isArray(draft.tags) && draft.tags.length) form.value.tags = draft.tags.slice(0, 10)
    ElMessage.success('草稿已填入，记得按自己的真实体验修改哦')
  } finally { aiLoading.value = false }
}

async function uploadImg ({ file }) {
  const res = await fileApi.upload(file, 'notes')
  form.value.images.push(res.url)
}

async function submit () {
  if (!form.value.title.trim() || !form.value.content.trim()) {
    return ElMessage.warning('标题和正文都要填哦')
  }
  submitting.value = true
  try {
    const body = {
      title: form.value.title,
      content: form.value.content,
      images: form.value.images,
      tags: form.value.tags,
      products: form.value.products.map(id => ({ productId: id }))
    }
    const note = await noteApi.create(body)
    ElMessage.success('已提交，AI 快审中')
    router.push(`/notes/${note.id}`)
  } finally { submitting.value = false }
}

onMounted(async () => {
  // 商品列表供关联选择（拉一页 100 够选）
  const res = await productApi.page({ page: 1, pageSize: 100 })
  products.value = (res.records || []).map(p => ({ id: p.id, name: p.spuName }))
})
</script>

<style scoped>
.note-create { max-width: 820px; margin: 0 auto; padding: 16px; }
.editor { background: var(--el-bg-color); border-radius: 12px; padding: 24px; }
.ai-card { margin-bottom: 16px; }
.ai-row { display: flex; gap: 10px; }
.ai-hint { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 8px; }
.title-input { margin-bottom: 12px; }
.content-input { margin-bottom: 14px; }
.imgs { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.img-item { position: relative; width: 90px; height: 90px; }
.img-item img { width: 100%; height: 100%; border-radius: 8px; object-fit: cover; }
.del { position: absolute; right: -6px; top: -6px; cursor: pointer; background: #fff; border-radius: 50%; }
.upload-btn { width: 90px; height: 90px; border: 1px dashed var(--el-border-color); border-radius: 8px; display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 22px; color: var(--el-text-color-secondary); }
.row { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.label { width: 40px; font-size: 14px; color: var(--el-text-color-regular); flex-shrink: 0; }
.submit-row { display: flex; align-items: center; gap: 12px; margin-top: 20px; }
.submit-hint { font-size: 12px; color: var(--el-text-color-secondary); }
</style>
