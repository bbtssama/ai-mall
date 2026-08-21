<template>
  <div class="chat-page">
    <h2 class="page-title">AI 种草助手</h2>
    <div class="chat-card mall-card">
      <div class="chat-body">
      <!-- 会话列表 -->
      <div class="conv-list" v-if="conversations.length">
        <div v-for="c in conversations" :key="c.id" class="conv-item"
             :class="{ active: c.id === currentId }" @click="switchConversation(c.id)">
          <div class="conv-title">{{ c.title }}</div>
        </div>
      </div>

      <!-- 消息区 -->
      <div class="msg-area" ref="msgArea">
        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div class="bubble">
            <img v-if="m.image" :src="m.image" class="msg-img" alt="用户图片" />
            <div v-if="m.content">{{ m.content }}</div>
          </div>
        </div>
        <div v-if="streaming" class="msg-row assistant">
          <div class="bubble streaming">{{ streamText }}<span class="cursor">▍</span></div>
        </div>
        <div v-if="!messages.length && !streaming" class="empty-tip">
          <p>我是 AI 种草助手，可以帮你：</p>
          <ul>
            <li>「AirSound Pro 耳机支持蓝牙 5.3 吗？」</li>
            <li>「智能手环 6 续航多久？」</li>
            <li>「帮我推荐一款 100 元以内的送礼好物」</li>
          </ul>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <div class="input-box">
          <div v-if="previewImg" class="img-preview">
            <img :src="previewImg" />
            <span class="img-remove" @click="clearImage">×</span>
          </div>
          <el-input v-model="input" type="textarea" :rows="2" resize="none"
                    placeholder="输入问题，Enter 发送（Shift+Enter 换行）；也可发图识别商品"
                    @keyup.enter.exact.prevent="send" :disabled="streaming" />
        </div>
        <div class="input-actions">
          <el-upload :show-file-list="false" accept="image/*" :auto-upload="false" @change="onPickImage">
            <el-button :icon="Picture">图片</el-button>
          </el-upload>
          <el-button @click="newConversation">新会话</el-button>
          <el-button type="danger" :loading="streaming" @click="send">发送</el-button>
        </div>
      </div>
    </div>
  </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { chatApi } from '../api'

const conversations = ref([])
const currentId = ref(null)
const messages = ref([])
const input = ref('')
const streaming = ref(false)
const streamText = ref('')
const msgArea = ref(null)
const previewImg = ref('')   // dataURL 预览 + 发送

async function loadConversations() {
  conversations.value = await chatApi.conversations() || []
}

async function newConversation() {
  const c = await chatApi.createConversation({ title: '新会话' })
  conversations.value.unshift(c)
  currentId.value = c.id
  messages.value = []
}

async function switchConversation(id) {
  currentId.value = id
  messages.value = await chatApi.messages(id) || []
}

async function send() {
  const text = input.value.trim()
  const image = previewImg.value
  if ((!text && !image) || streaming.value) return

  if (!currentId.value) {
    const title = text || '图片识别'
    const c = await chatApi.createConversation({ title: title.slice(0, 16) })
    conversations.value.unshift(c)
    currentId.value = c.id
  }

  // 先渲染用户消息（有图则带图）
  messages.value.push({ role: 'user', content: text || '[图片]', image })
  input.value = ''
  previewImg.value = ''
  scrollBottom()

  if (image) {
    // 带图 → 非流式（后端 vision 链路）
    streaming.value = true
    messages.value.push({ role: 'assistant', content: '' })
    try {
      const answer = await chatApi.send({ conversationId: currentId.value, message: text, image })
      messages.value[messages.value.length - 1].content = answer
      await loadConversations()
    } catch (e) {
      messages.value[messages.value.length - 1].content = 'AI 服务暂时不可用'
      ElMessage.error('识别失败')
    } finally {
      streaming.value = false
      scrollBottom()
    }
    return
  }

  // 纯文字 → 流式（后端走搜索工具；标题由后端首条消息自动命名）
  streaming.value = true
  streamText.value = ''
  messages.value.push({ role: 'assistant', content: '' }) // 占位
  let acc = ''
  try {
    await chatApi.sendStream(
      { conversationId: currentId.value, message: text },
      (chunk) => {
        acc += chunk
        streamText.value = acc
        messages.value[messages.value.length - 1].content = acc
        scrollBottom()
      },
      async () => {
        streaming.value = false
        await loadConversations()
      },
      (err) => {
        streaming.value = false
        ElMessage.error('AI 服务暂时不可用')
      }
    )
  } catch (e) {
    streaming.value = false
    ElMessage.error('网络异常')
  }
}

// 选择图片 → 读为 dataURL 预览
function onPickImage(file) {
  const raw = file.raw
  if (!raw || !raw.type.startsWith('image/')) return
  const reader = new FileReader()
  reader.onload = (e) => { previewImg.value = e.target.result }
  reader.readAsDataURL(raw)
}

function clearImage() { previewImg.value = '' }

function scrollBottom() {
  nextTick(() => {
    if (msgArea.value) msgArea.value.scrollTop = msgArea.value.scrollHeight
  })
}

onMounted(async () => {
  await loadConversations()
  if (conversations.value.length) {
    await switchConversation(conversations.value[0].id)
  }
})
</script>

<style scoped>
.chat-card { height: calc(100vh - 140px); display: flex; flex-direction: column; }
.chat-head { display: flex; justify-content: space-between; align-items: center; }
.chat-body { display: flex; flex: 1; min-height: 0; }
.conv-list {
  width: 200px; border-right: 1px solid #eee; overflow-y: auto; padding: 8px;
}
.conv-item {
  padding: 10px; border-radius: 6px; cursor: pointer; margin-bottom: 4px;
  font-size: 13px; color: #444; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.conv-item:hover { background: #f5f5f5; }
.conv-item.active { background: #fdeee9; color: #e8562c; }
.msg-area { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 10px; }
.msg-row { display: flex; }
.msg-row.user { justify-content: flex-end; }
.msg-row.assistant { justify-content: flex-start; }
.bubble {
  max-width: 72%; padding: 10px 14px; border-radius: 10px; line-height: 1.6;
  white-space: pre-wrap; word-break: break-word; font-size: 14px;
}
.user .bubble { background: #e8562c; color: #fff; border-bottom-right-radius: 2px; }
.assistant .bubble { background: #fff; border: 1px solid #eee; border-bottom-left-radius: 2px; }
.msg-img { max-width: 220px; max-height: 220px; border-radius: 8px; display: block; margin-bottom: 4px; }
.cursor { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.empty-tip { color: #999; font-size: 13px; margin: 40px auto; }
.empty-tip li { margin: 6px 0; list-style: none; }
.input-area { display: flex; gap: 10px; padding: 12px; border-top: 1px solid var(--clr-border); align-items: flex-end; }
.input-box { flex: 1; display: flex; flex-direction: column; gap: 6px; }
.img-preview { position: relative; width: 72px; height: 72px; border-radius: var(--radius-sm); overflow: hidden; }
.img-preview img { width: 100%; height: 100%; object-fit: cover; }
.img-remove {
  position: absolute; top: 2px; right: 2px; width: 18px; height: 18px; line-height: 16px;
  text-align: center; background: rgba(0,0,0,.55); color: #fff; border-radius: 50%;
  cursor: pointer; font-size: 13px;
}
.input-actions { display: flex; flex-direction: column; gap: 8px; }
</style>