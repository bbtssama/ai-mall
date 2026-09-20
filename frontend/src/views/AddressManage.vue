<template>
  <div class="addr-page">
    <div class="page-head">
      <h2>收货地址</h2>
      <el-button type="primary" @click="openForm()">＋ 新增地址</el-button>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!list.length && !loading" description="还没有收货地址">
        <el-button type="primary" @click="openForm()">新增收货地址</el-button>
      </el-empty>

      <div v-else class="addr-list">
        <div v-for="a in list" :key="a.id" class="addr-item mall-card">
          <div class="addr-head">
            <span class="addr-receiver">{{ a.receiver }}</span>
            <span class="addr-phone">{{ a.phone }}</span>
            <el-tag v-if="a.isDefault" type="danger" size="small" effect="plain">默认</el-tag>
            <el-button v-else size="small" text type="primary" @click="setDefault(a)">设为默认</el-button>
          </div>
          <div class="addr-detail">{{ a.fullAddress }}</div>
          <div class="addr-ops">
            <el-button size="small" text @click="openForm(a)">编辑</el-button>
            <el-button size="small" text type="danger" @click="removeAddr(a)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑地址弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑地址' : '新增地址'" width="480px">
      <el-form class="addr-form" label-width="70px" size="default">
        <el-form-item label="收货人"><el-input v-model="form.receiver" placeholder="收货人" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" placeholder="手机号" /></el-form-item>
        <el-form-item label="省"><el-input v-model="form.province" placeholder="省" /></el-form-item>
        <el-form-item label="市"><el-input v-model="form.city" placeholder="市" /></el-form-item>
        <el-form-item label="详细地址"><el-input v-model="form.detail" placeholder="区 / 街道 / 门牌号" /></el-form-item>
        <el-form-item label="设为默认">
          <el-checkbox v-model="form.isDefault">设为默认收货地址</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addressApi } from '../api'

const list = ref([])
const loading = ref(false)
const formVisible = ref(false)
const saving = ref(false)
const form = ref({ id: null, receiver: '', phone: '', province: '', city: '', detail: '', isDefault: false })

async function load() {
  loading.value = true
  try { list.value = await addressApi.list() || [] } finally { loading.value = false }
}

function openForm(a) {
  form.value = a
    ? { id: a.id, receiver: a.receiver, phone: a.phone, province: a.province, city: a.city, detail: a.detail, isDefault: a.isDefault }
    : { id: null, receiver: '', phone: '', province: '', city: '', detail: '', isDefault: false }
  formVisible.value = true
}

async function save() {
  if (!form.value.receiver || !form.value.phone || !form.value.detail) {
    ElMessage.warning('请填写收货人、电话和详细地址')
    return
  }
  saving.value = true
  try {
    if (form.value.id) {
      await addressApi.update(form.value.id, form.value)
      ElMessage.success('地址已更新')
    } else {
      await addressApi.add(form.value)
      ElMessage.success('地址已新增')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function removeAddr(a) {
  await ElMessageBox.confirm(`确定删除收货人「${a.receiver}」的地址吗？`, '确认删除', { type: 'warning' })
  await addressApi.remove(a.id)
  ElMessage.success('已删除')
  load()
}

async function setDefault(a) {
  await addressApi.setDefault(a.id)
  ElMessage.success('已设为默认')
  load()
}

onMounted(load)
</script>

<style scoped>
.addr-page { max-width: 720px; margin: 0 auto; }
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-head h2 { font-size: 20px; color: var(--clr-text); margin: 0; }
.addr-list { display: flex; flex-direction: column; gap: 12px; }
.addr-item { padding: 16px 20px; position: relative; }
.addr-head { display: flex; align-items: center; gap: 12px; margin-bottom: 6px; }
.addr-receiver { font-weight: 700; font-size: 16px; }
.addr-phone { color: var(--clr-text-2); font-size: 14px; }
.addr-detail { color: var(--clr-text-3); font-size: 14px; margin-bottom: 8px; }
.addr-ops { position: absolute; right: 16px; bottom: 10px; }

/* =====================================================================
   移动端适配（≤ 768px）
   地址卡单列紧凑排版；编辑/删除由「右下角绝对定位」改为卡片底部一行，
   避免窄屏与地址文字重叠；弹窗表单 label 上移、控件全宽（纯 CSS，
   桌面端 label-width prop 保持不变）。
   ===================================================================== */
@media (max-width: 768px) {
  .addr-page { max-width: none; }
  .page-head { margin-bottom: 12px; gap: 8px; }
  .page-head h2 { font-size: 18px; }

  .addr-list { gap: 10px; }
  .addr-item { padding: 12px 14px; }

  .addr-head { flex-wrap: wrap; gap: 4px 8px; margin-bottom: 4px; }
  .addr-receiver { font-size: 15px; }
  .addr-phone { font-size: 13px; }
  .addr-detail { font-size: 13px; line-height: 1.5; margin-bottom: 0; }
  .addr-head .el-button { min-height: 40px; margin: 0; padding: 0 6px; }

  /* 操作区回到文档流：卡片底部一行，按钮触摸目标 ≥40px */
  .addr-ops {
    position: static;
    display: flex; justify-content: flex-end; gap: 4px;
    margin-top: 8px; padding-top: 6px;
    border-top: 1px solid var(--clr-border-light);
  }
  .addr-ops .el-button { min-height: 40px; margin: 0; padding: 0 12px; }

  /* 弹窗表单：label 上移独占一行、控件全宽。
     EP 的 .el-form-item 默认 display:flex，必须显式改 block 才能让 label 换行。 */
  .addr-form :deep(.el-form-item) { display: block; margin-bottom: 14px; }
  .addr-form :deep(.el-form-item__label) {
    display: block; width: auto !important; height: auto; line-height: 1.4;
    text-align: left; padding: 0 0 6px; font-size: 13px;
  }
  .addr-form :deep(.el-form-item__content) { display: block; margin-left: 0 !important; }
}
</style>