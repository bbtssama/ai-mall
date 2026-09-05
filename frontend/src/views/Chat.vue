<template>
  <div class="chat-page">
    <h2 class="page-title">AI 种草助手</h2>
    <div class="chat-layout">
      <!-- 左：派蒙 Live2D 舞台（窄侧，桌面置左，≤360px） -->
      <div class="voice-stage-side">
        <VoiceStage ref="voiceStage" />
      </div>

      <!-- 右：聊天面板（主体，加宽） -->
      <div class="chat-card mall-card">
        <!-- 顶部工具条：会话抽屉切换 + 标题 -->
        <div class="chat-toolbar">
          <el-button size="small" class="drawer-toggle" @click="convOpen = !convOpen">
            <span v-if="convOpen">◀ 收起会话</span>
            <span v-else>▶ 会话列表</span>
          </el-button>
          <span class="toolbar-title">AI 种草助手</span>
        </div>
        <div class="chat-body">
          <!-- 抽屉：会话列表 -->
          <transition name="drawer">
            <div v-if="convOpen && conversations.length" class="conv-list">
              <div class="conv-head">会话</div>
              <div v-for="c in conversations" :key="c.id" class="conv-item"
                   :class="{ active: c.id === currentId }" @click="switchConversation(c.id)">
                <div class="conv-title">{{ c.title }}</div>
              </div>
            </div>
          </transition>

          <!-- 消息区 -->
          <div class="msg-area" ref="msgArea">
            <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
              <div class="bubble" :class="{ streaming: streaming }">
                <img v-if="m.image && m.image.indexOf('data:image/') === 0" :src="m.image" class="msg-img" alt="用户图片" />
                <div v-if="m.content">{{ m.content }}<span v-if="streaming && i === messages.length - 1" class="cursor">▍</span></div>
              </div>
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
        </div>

        <!-- 输入区（chat-card 为纵向 flex，输入/发送区位于卡片底部） -->
        <div class="input-area">
          <div class="input-box">
            <div v-if="previewImg" class="img-preview">
              <img :src="previewImg" />
              <span class="img-remove" @click="clearImage">×</span>
            </div>
            <el-input v-model="input" type="textarea" :rows="2" resize="none"
                      placeholder="输入问题，Enter 发送（Shift+Enter 换行）；也可粘贴/选择图片识别商品"
                      @keyup.enter.exact.prevent="send" @paste="onPaste" :disabled="streaming" />
          </div>
          <div class="input-actions">
            <div class="toggle-row">
              <el-switch v-model="streamingEnabled" size="small" title="关闭后一次性返回完整回答" />
              <span class="toggle-label">流式输出</span>
            </div>
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
import VoiceStage from '../voice/voiceStage.vue'
import { createAudioQueue, splitSentences } from '../voice/useAudioQueue.js'

const conversations = ref([])
const currentId = ref(null)
const messages = ref([])
const input = ref('')
const streaming = ref(false)
const streamingEnabled = ref(true)   // 流式开关（默认开）；关则走 /chat 一次性返回
const msgArea = ref(null)
const previewImg = ref('')   // dataURL 预览 + 发送
const voiceStage = ref(null)
const convOpen = ref(true)   // 会话抽屉：展开/收起（收起后聊天/派蒙区更宽裕）

// 派蒙语音动效：AI 回复流结束后，前端把完整回答按句切分，逐句 POST /voice/tts 顺序播放 + Live2D 口型/表情
let voiceQueue = null

async function loadConversations() {
  // 单一 AI 页：会话列表直接回显主链路对话（不做 bizType/语音会话过滤）
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

  // 占位助手消息：流式逐字填充 / 非流式一次填满（统一渲染源，避免双框）
  messages.value.push({ role: 'assistant', content: '' })
  const last = messages.value[messages.value.length - 1]
  streaming.value = true
  const payload = { conversationId: currentId.value, message: text, image }
  try {
    if (streamingEnabled.value) {
      // 流式通道：SSE 逐块更新最后一条占位消息
      let acc = ''
      await chatApi.sendStream(
        payload,
        (chunk) => {
          acc += chunk
          last.content = acc
          scrollBottom()
        },
        async () => {
          streaming.value = false
          await loadConversations()
          speakReply(acc) // 回复流结束 → 逐句 TTS 播放（派蒙语音 + Live2D 动效）
        },
        () => {
          streaming.value = false
          ElMessage.error('AI 服务暂时不可用')
        }
      )
    } else {
      // 非流式通道：/chat 一次性返回完整回答
      const answer = await chatApi.send(payload)
      last.content = answer || ''
      streaming.value = false
      await loadConversations()
      speakReply(answer || '')
    }
  } catch (e) {
    streaming.value = false
    if (last.content === '') messages.value.pop()  // 失败且无内容，移除空占位
    ElMessage.error('网络异常')
  }
}

// ---------- 语音动效：前端按句切分 + TTS 播放队列 ----------
function speakReply(text) {
  if (!voiceQueue || !text || !text.trim()) return
  const sents = splitSentences(text)
  for (const s of sents) {
    voiceQueue.enqueue(s, pickEmo(s), '') // 前端无 emo/act 元数据，用内容启发式表情；无动作指令
  }
}
// 简单启发式：按句意挑一个表情（驱动 Live2D 表情/口型），无 [act] 元数据
function pickEmo(sentence) {
  if (/推荐|适合|不错|好|棒|赞|性价比|优惠/.test(sentence)) return 'happy'
  if (/？|\?/.test(sentence)) return 'surprised'
  if (/抱歉|遗憾|可惜|不好意思|无法|没有/.test(sentence)) return 'sad'
  if (/问题|错误|不行|缺货|失败/.test(sentence)) return 'angry'
  return 'normal'
}

// 图片压缩：最长边 ≤1280、JPEG 0.8（白底防透明变黑），返回 dataURL。
// 真实大图（截图/照片动辄几 MB）先压缩再发送，避免请求体过大与存储超限
function compressImage(file) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      URL.revokeObjectURL(url)
      const MAX = 1280
      let { width, height } = img
      const scale = Math.min(1, MAX / Math.max(width, height))
      if (scale < 1) {
        width = Math.round(width * scale)
        height = Math.round(height * scale)
      }
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = '#fff'
      ctx.fillRect(0, 0, width, height)
      ctx.drawImage(img, 0, 0, width, height)
      resolve(canvas.toDataURL('image/jpeg', 0.8))
    }
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('图片解码失败')) }
    img.src = url
  })
}

// 选择图片 → 压缩后预览
async function onPickImage(file) {
  const raw = file.raw
  if (!raw || !raw.type.startsWith('image/')) return
  try {
    previewImg.value = await compressImage(raw)
  } catch {
    ElMessage.error('图片处理失败')
  }
}

// 输入框粘贴图片（Ctrl+V）→ 压缩后预览，走图片识别链路；剪贴板无图则正常粘贴文本
function onPaste(e) {
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        e.preventDefault()
        compressImage(file)
          .then((dataUrl) => { previewImg.value = dataUrl })
          .catch(() => ElMessage.error('图片处理失败'))
      }
      break
    }
  }
}

function clearImage() { previewImg.value = '' }

function scrollBottom() {
  nextTick(() => {
    if (msgArea.value) msgArea.value.scrollTop = msgArea.value.scrollHeight
  })
}

onMounted(async () => {
  // 建立派蒙语音播放队列：entry → fetchTts(voiceApi.voiceTts) → voiceStage.playAudio
  voiceQueue = createAudioQueue(
    (url, emo, act) => (voiceStage.value && voiceStage.value.playAudio(url, emo, act)) || Promise.resolve(),
    () => voiceStage.value && voiceStage.value.stopSpeaking(),
    () => {}
  )
  await loadConversations()
  if (conversations.value.length) {
    await switchConversation(conversations.value[0].id)
  }
})
</script>

<style scoped>
/* 主布局：左派蒙舞台（≤360px 窄侧）+ 右聊天面板（加宽，主体） */
.chat-layout { display: flex; gap: 16px; align-items: stretch; }
.chat-card {
  flex: 1;
  min-width: 0;
  height: calc(100vh - 190px);
  display: flex; flex-direction: column;
  background: #fff;
  border: 1px solid #ececec;
  border-radius: 16px;
  box-shadow: 0 6px 28px rgba(0,0,0,.06);
  overflow: hidden;
}
.voice-stage-side {
  flex: 0 0 auto;
  width: 300px;
  max-width: 25%;
  min-width: 240px;
  height: calc(100vh - 190px);
  position: relative;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(255,253,246,.9), rgba(255,233,179,.3));
  border: 1px solid #ececec;
  border-radius: 16px;
  box-shadow: 0 6px 28px rgba(0,0,0,.06);
}

/* 顶部工具条：会话抽屉切换 + 标题 */
.chat-toolbar {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
  flex-shrink: 0;
}
.drawer-toggle {
  min-width: 96px;
  border: 1px solid #e8562c; color: #e8562c; background: #fff;
}
.drawer-toggle:hover { background: #fdeee9; }
.toolbar-title { font-weight: 600; color: #333; font-size: 15px; }

.chat-head { display: flex; justify-content: space-between; align-items: center; }
.chat-body { display: flex; flex: 1; min-height: 0; }

/* 会话抽屉 */
.conv-list {
  width: 200px; flex-shrink: 0;
  background: #fafafa; border-right: 1px solid #eee;
  overflow-y: auto; padding: 8px;
}
.conv-head { padding: 6px 8px; font-size: 12px; color: #999; font-weight: 600; }
.conv-item {
  padding: 10px; border-radius: 8px; cursor: pointer; margin-bottom: 4px;
  font-size: 13px; color: #444; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  transition: background .15s, color .15s;
}
.conv-item:hover { background: #f0f0f0; }
.conv-item.active { background: #fdeee9; color: #e8562c; font-weight: 600; }
.conv-title { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* 抽屉开合过渡 */
.drawer-enter-active, .drawer-leave-active { transition: opacity .2s ease, transform .2s ease; }
.drawer-enter-from, .drawer-leave-to { opacity: 0; transform: translateX(-12px); }

.msg-area { flex: 1; overflow-y: auto; padding: 16px 18px; display: flex; flex-direction: column; gap: 12px; }
.msg-row { display: flex; width: 100%; }
.msg-row.user { justify-content: flex-end; }
.msg-row.assistant { justify-content: flex-start; }
.bubble {
  max-width: 78%; padding: 11px 15px; border-radius: 12px; line-height: 1.65;
  white-space: pre-wrap; word-break: break-word; font-size: 14px;
}
.user .bubble { background: #e8562c; color: #fff; border-bottom-right-radius: 4px; }
.assistant .bubble { background: #fff; border: 1px solid #eee; border-bottom-left-radius: 4px; box-shadow: 0 1px 2px rgba(0,0,0,.03); }
.msg-img { max-width: 220px; max-height: 220px; border-radius: 10px; display: block; margin-bottom: 4px; }
.cursor { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.empty-tip { color: #999; font-size: 13px; margin: 40px auto; text-align: center; }
.empty-tip li { margin: 6px 0; list-style: none; }
.input-area { display: flex; gap: 10px; padding: 12px 14px; border-top: 1px solid #eee; align-items: flex-end; background: #fff; }
.input-box { flex: 1; display: flex; flex-direction: column; gap: 6px; }
.img-preview { position: relative; width: 72px; height: 72px; border-radius: 10px; overflow: hidden; }
.img-preview img { width: 100%; height: 100%; object-fit: cover; }
.img-remove {
  position: absolute; top: 2px; right: 2px; width: 18px; height: 18px; line-height: 16px;
  text-align: center; background: rgba(0,0,0,.55); color: #fff; border-radius: 50%;
  cursor: pointer; font-size: 13px;
}
.input-actions { display: flex; flex-direction: column; gap: 8px; align-items: center; }
.toggle-row { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.toggle-label { font-size: 12px; color: #999; }

@media (max-width: 900px) {
  .chat-layout { flex-direction: column; gap: 12px; }
  .voice-stage-side { width: 100%; max-width: 100%; height: 280px; min-height: 240px; }
  .chat-card { height: auto; min-height: 70vh; }
}
</style>
