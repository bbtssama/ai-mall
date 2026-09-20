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
          <el-input v-model="aiBrief" type="textarea" :rows="2" resize="none"
            placeholder="一句话说清：什么场景、预算多少、想要什么" />
          <el-select v-model="aiProductId" placeholder="AI 参考商品（可选）" clearable filterable>
            <el-option v-for="p in products" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>          <el-button class="ai-gen" :loading="aiLoading" @click="generate">生成草稿</el-button>
        </div>
        <div class="ai-hint">AI 草稿仅供参考，发布前请务必自己审阅修改；发布后会经过 AI 审核。</div>
      </el-card>

      <!-- 第 2 步：编辑正文 -->
      <label class="field-label" for="note-title">
        标题<span class="field-tip">（{{ TITLE_MAX }} 字内更容易被点开）</span>
      </label>
      <el-input id="note-title" v-model="form.title" :maxlength="TITLE_MAX" show-word-limit size="large"
        class="title-input" placeholder="例如：500 元内通勤降噪耳机，实测两周" />

      <label class="field-label" for="note-content">
        正文<span class="field-tip">（说清场景 / 用了多久 / 值不值）</span>
      </label>
      <el-input id="note-content" v-model="form.content" type="textarea" :rows="12" :maxlength="CONTENT_MAX"
        show-word-limit placeholder="写下你的真实体验：什么场景、用了多久、感受如何、值不值…"
        class="content-input" />

      <!-- 图片：3 列 1:1 网格，首格是上传位，已选可单张删除 -->
      <div class="imgs">
        <div v-for="(u, i) in form.images" :key="u" class="img-item">
          <img :src="u" alt="已选图片" />
          <el-icon class="del pressable" @click="form.images.splice(i, 1)"><CircleClose /></el-icon>
        </div>
        <el-upload v-if="form.images.length < IMG_MAX" :show-file-list="false" :http-request="uploadImg" accept="image/*">
          <div class="upload-btn"><el-icon><Plus /></el-icon></div>
        </el-upload>
      </div>
      <div class="imgs-hint">最多 {{ IMG_MAX }} 张，已选 {{ form.images.length }} 张</div>

      <!-- 标签与关联商品 -->
      <div class="row">
        <span class="label">标签</span>
        <el-select v-model="form.tags" multiple filterable allow-create default-first-option
          placeholder="选择或输入标签（最多 10 个）" class="row-select">
          <el-option v-for="t in TAG_SUGGEST" :key="t" :label="'#' + t" :value="t" />
        </el-select>
      </div>
      <div class="row">
        <span class="label">好物</span>
        <el-select v-model="form.products" multiple filterable class="row-select"
          placeholder="关联你在用的商品，会展示在笔记里">
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

/**
 * 字段上限集中在这里。
 * 此前口径是矛盾的：标题占位写「20 字内更容易被点开」而计数是 /100，
 * 正文计数是 /20000（后端上限），用户不知道该信哪个。
 * 后端校验是 title ≤ 100 / content ≤ 20000（NoteCreateRequest），
 * 前端取更严的 20 / 1000，只会更安全，不会出现「前端放过、后端 400」。
 */
const TITLE_MAX = 20
const CONTENT_MAX = 1000
const IMG_MAX = 9

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
    // 超出前端上限的草稿要裁掉，否则计数会顶成 "27 / 20" 这种自相矛盾的状态
    if (draft.title) form.value.title = draft.title.slice(0, TITLE_MAX)
    if (draft.content) form.value.content = draft.content.slice(0, CONTENT_MAX)
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
/* el-cascader / el-select 是多根组件，class 不一定落到根元素上，统一用 :deep 定宽 */
.ai-row :deep(.el-select) { width: 220px; flex: none; }
.title-input { margin-bottom: 12px; }
.content-input { margin-bottom: 14px; }
.imgs { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.img-item { position: relative; width: 90px; height: 90px; }
.img-item img { width: 100%; height: 100%; border-radius: 8px; object-fit: cover; display: block; }
.del { position: absolute; right: -6px; top: -6px; cursor: pointer; background: #fff; border-radius: 50%; }
.upload-btn { width: 90px; height: 90px; border: 1px dashed var(--el-border-color); border-radius: 8px; display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 22px; color: var(--el-text-color-secondary); }
.row { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.label { width: 40px; font-size: 14px; color: var(--el-text-color-regular); flex-shrink: 0; }
.row-select { flex: 1; }
.submit-row { display: flex; align-items: center; gap: 12px; margin-top: 20px; }
.submit-hint { font-size: 12px; color: var(--el-text-color-secondary); }

/* 常驻小标签与图片计数：移动端才需要（桌面端视觉保持不变，见下方 @media） */
.field-label { display: none; }
.imgs-hint { display: none; }

/* =====================================================================
   移动端适配（≤ 768px）—— 桌面端（>768px）样式完全不受影响
   ===================================================================== */
@media (max-width: 768px) {
  .note-create { max-width: 100%; padding: 12px; }
  .editor { padding: 14px; border-radius: 10px; }

  /* AI 起草：三件套改为单列堆叠、全宽 */
  .ai-row { flex-direction: column; gap: 8px; }
  .ai-row :deep(.el-input),
  .ai-row :deep(.el-select) { width: 100% !important; }
  .ai-row :deep(.el-textarea__inner) { font-size: 16px; }
  .ai-row :deep(.el-button) { width: 100%; min-height: 44px; }

  /* 常驻字段标签：此前全表单 0 个 label，只靠 placeholder，
     输入之后就再也看不出这个框是干什么的。 */
  .field-label {
    display: block;
    font-size: 13px; font-weight: 600; color: var(--clr-text);
    margin: 2px 0 6px;
  }
  .field-tip { font-weight: 400; color: var(--clr-text-3); font-size: 12px; }

  /* 图片网格：固定 90px 换行 → 3 列等宽方格（1:1），触摸目标更大 */
  .imgs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 8px; }
  .imgs :deep(.el-upload) { width: 100%; }
  .img-item { width: 100%; height: auto; aspect-ratio: 1 / 1; }
  .img-item img { border-radius: var(--r-sm); }
  .upload-btn { width: 100%; height: auto; aspect-ratio: 1 / 1; font-size: 26px; border-radius: var(--r-sm); }
  .del {
    right: 0; top: 0; font-size: 20px; background: #fff; border-radius: 50%;
    box-shadow: 0 1px 4px rgba(0, 0, 0, .18);
    padding: 4px; margin: 0;                     /* 视觉 20px，触摸区 28px */
  }
  .imgs-hint { display: block; font-size: 12px; color: var(--clr-text-3); margin-bottom: 14px; }

  /* 标签 / 好物：label 顶部对齐 + 选择器全宽 */
  .row { flex-direction: column; align-items: stretch; gap: 6px; }
  .label { width: auto; font-weight: 600; color: var(--clr-text); }
  .row :deep(.el-select) { flex: none !important; width: 100%; }

  /* 提交区：主按钮「发布」全宽 ≥48px（实心主色），与次级「生成草稿」拉开层级 */
  .submit-row { flex-direction: column; align-items: stretch; gap: 8px; margin-top: 16px; }
  .submit-row :deep(.el-button) { width: 100%; min-height: 48px; font-size: 16px; }

  /* 正文 textarea：≥16px 防 iOS 聚焦缩放，高度自适应 */
  .content-input :deep(.el-textarea__inner) { font-size: 16px; line-height: 1.7; min-height: 200px; }
  .note-create :deep(.el-input__inner) { font-size: 16px; }
}
</style>
