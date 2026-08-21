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
      <el-form label-width="70px" size="default">
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
</style>