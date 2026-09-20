<template>
  <div class="chat-page">
    <h2 class="page-title">AI 种草助手</h2>
    <div class="chat-layout">
      <!-- 左：派蒙 Live2D 舞台（桌面常驻；移动端默认收起，点工具条头像展开） -->
      <div class="voice-stage-side" :class="{ 'is-open': stageOpen }">
        <VoiceStage ref="voiceStage" />
      </div>
      <p v-if="stageOpen" class="stage-tip">👁 视线跟随 · 戳一下有惊喜</p>

      <!-- 右：聊天面板（主体，加宽） -->
      <div class="chat-card mall-card">
        <!-- 顶部工具条：会话抽屉切换 + 标题 -->
        <div class="chat-toolbar">
          <el-button size="small" class="drawer-toggle" @click="convOpen = !convOpen">
            <span v-if="convOpen">◀ 收起会话</span>
            <span v-else>▶ 会话列表</span>
          </el-button>
          <button type="button" class="icon-btn conv-toggle-m pressable" :class="{ 'is-on': convOpen }"
                  aria-label="会话列表" @click="convOpen = !convOpen">
            <el-icon><ChatLineSquare /></el-icon>
          </button>
          <span class="toolbar-title">AI 种草助手</span>
          <button type="button" class="stage-toggle pressable" :class="{ 'is-on': stageOpen }"
                  aria-label="展开 AI 形象" @click="toggleStage">派</button>
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
            <div v-for="(m, i) in messages" :key="i" class="msg-row"
                 :class="[m.role, { 'is-lead': isLead(i) }]">
              <div class="msg-avatar" aria-hidden="true">{{ m.role === 'user' ? '我' : '派' }}</div>
              <div class="msg-main">
                <div class="msg-meta">
                  <span class="msg-who">{{ m.role === 'user' ? '我' : 'AI 种草助手' }}</span>
                  <span v-if="m.createdAt" class="msg-time">{{ formatTime(m.createdAt) }}</span>
                </div>
                <div class="bubble"
                     :class="{ streaming: streaming && i === messages.length - 1, 'has-img': !!m.image }">
                  <img v-if="m.image && m.image.indexOf('data:image/') === 0" :src="m.image" class="msg-img" alt="用户图片" />
                  <!-- 助手内容走轻量 Markdown；用户内容原样插值（不 v-html，避免注入） -->
                  <div v-if="m.role === 'assistant' && (m.content || isStreamingAt(i))" class="bubble-text md"
                       v-html="mdHtml(m.content, isStreamingAt(i))"></div>
                  <div v-else-if="m.content" class="bubble-text">{{ m.content }}</div>
                </div>
              </div>
            </div>
            <div v-if="!messages.length && !streaming" class="empty-tip">
              <p>我是 AI 种草助手，可以帮你：</p>
              <ul>
                <li @click="fillInput('AirSound Pro 耳机支持蓝牙 5.3 吗？')">「AirSound Pro 耳机支持蓝牙 5.3 吗？」</li>
                <li @click="fillInput('智能手环 6 续航多久？')">「智能手环 6 续航多久？」</li>
                <li @click="fillInput('帮我推荐一款 100 元以内的送礼好物')">「帮我推荐一款 100 元以内的送礼好物」</li>
              </ul>
            </div>
          </div>
        </div>

        <!-- 输入区（chat-card 为纵向 flex，输入/发送区位于卡片底部） -->
        <div class="input-area">
          <div class="input-row">
            <!-- 移动端：附件入口（桌面隐藏，桌面沿用右侧操作区里的「图片」按钮） -->
            <div class="act-img-m">
              <el-upload :show-file-list="false" accept="image/*" :auto-upload="false" @change="onPickImage">
                <button type="button" class="icon-btn pressable" aria-label="选择图片">
                  <el-icon><Picture /></el-icon>
                </button>
              </el-upload>
            </div>
            <div class="input-box">
              <div v-if="previewImg" class="img-preview">
                <img :src="previewImg" />
                <span class="img-remove" @click="clearImage">×</span>
              </div>
              <el-input v-model="input" type="textarea" :rows="2" resize="none"
                        :placeholder="inputPlaceholder"
                        @keyup.enter.exact.prevent="send" @paste="onPaste" :disabled="streaming" />
            </div>
            <!-- 移动端：圆形发送（桌面隐藏，桌面沿用右侧的「发送」按钮） -->
            <button type="button" class="send-btn pressable" :disabled="streaming"
                    aria-label="发送" @click="send">
              <el-icon v-if="!streaming"><Promotion /></el-icon>
              <span v-else class="send-loading" />
            </button>
          </div>

          <div class="input-actions">
            <div class="toggle-row">
              <el-switch v-model="streamingEnabled" size="small" title="关闭后一次性返回完整回答" />
              <span class="toggle-label">流式输出</span>
            </div>
            <div class="act-img">
              <el-upload :show-file-list="false" accept="image/*" :auto-upload="false" @change="onPickImage">
                <el-button :icon="Picture">图片</el-button>
              </el-upload>
            </div>
            <el-button class="act-new" @click="newConversation">新会话</el-button>
            <el-button class="act-send" type="danger" :loading="streaming" @click="send">发送</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Promotion, ChatLineSquare } from '@element-plus/icons-vue'
import { chatApi } from '../api'
import VoiceStage from '../voice/voiceStage.vue'
import { createAudioQueue, splitSentences } from '../voice/useAudioQueue.js'
import { formatTime } from '../utils/format'

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
const stageOpen = ref(false) // 移动端 AI 形象：默认收起（≥769px 由 CSS 强制常驻，不受此值影响）
const narrowMq = window.matchMedia('(max-width: 768px)')
const narrow = ref(narrowMq.matches)   // 仅用于切换占位文案，不参与任何业务判断

/** 窄屏用短占位符：移动端输入行只剩约 240px，原长文案会被裁成半行 */
const inputPlaceholder = computed(() => (narrow.value
  ? '输入问题，可粘贴或选择图片'
  : '输入问题，Enter 发送（Shift+Enter 换行）；也可粘贴/选择图片识别商品'))

// 派蒙语音动效：AI 回复流结束后，前端把完整回答按句切分，逐句 POST /voice/tts 顺序播放 + Live2D 口型/表情
let voiceQueue = null

/**
 * 移动端展开/收起 AI 形象。
 * 收起态用 display:none，PIXI 初始化时读到的容器尺寸是 0 —— 展开后必须补一次 resize 事件，
 * 让 PIXI 的 ResizePlugin（监听 window resize，按容器 clientWidth/Height 重设 buffer）
 * 与 voiceStage 自己的 ResizeObserver 一起把渲染尺寸和模型缩放纠正回来。
 */
function toggleStage() {
  stageOpen.value = !stageOpen.value
  if (!stageOpen.value) return
  nextTick(() => {
    window.dispatchEvent(new Event('resize'))
    setTimeout(() => window.dispatchEvent(new Event('resize')), 300)
  })
}

/** 点快捷问题 → 填进输入框（不直接发送，避免误触） */
function fillInput(text) {
  input.value = text
}

function onNarrowChange(e) { narrow.value = e.matches }

// ---------- 轻量 Markdown 渲染（零新增依赖）----------
// 只覆盖助手回答里真实出现的语法：**粗体** / `行内代码` / 有序·无序列表 / 换行 / ¥价格高亮。
// 先做 HTML 转义再套标签，避免回答里的尖括号被当成标签执行。
const MD_ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
const CARET = '\u0001'   // 流式光标占位符：不会被转义吃掉，最后替换成光标元素

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => MD_ESC[c])
}

/** 行内语法 */
function mdInline(s) {
  return s
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/¥\s?(\d[\d,]*(?:\.\d+)?)/g, '<em class="md-price">¥$1</em>')
    .replace(/\u0001/g, '<span class="cursor">▍</span>')
}

/** 块级语法 → HTML 字符串（供 v-html；样式由 .bubble-text 的 :deep() 提供） */
function mdHtml(text, caret) {
  const lines = escapeHtml(text || '').split('\n')
  const blocks = []
  let para = null
  const flush = () => { if (para) { blocks.push({ t: 'p', v: para }); para = null } }

  for (const raw of lines) {
    const line = raw.trim()
    const ul = /^[-*•·]\s+(.+)$/.exec(line)
    const ol = /^\d+[.、)]\s+(.+)$/.exec(line)
    if (ul && !line.startsWith('**')) {
      flush()
      const last = blocks[blocks.length - 1]
      if (last && last.t === 'ul') last.v.push(ul[1])
      else blocks.push({ t: 'ul', v: [ul[1]] })
    } else if (ol) {
      flush()
      const last = blocks[blocks.length - 1]
      if (last && last.t === 'ol') last.v.push(ol[1])
      else blocks.push({ t: 'ol', v: [ol[1]] })
    } else if (line === '') {
      flush()
    } else if (para) {
      para.push(line)
    } else {
      para = [line]
    }
  }
  flush()
  if (!blocks.length) blocks.push({ t: 'p', v: [''] })
  if (caret) {
    const last = blocks[blocks.length - 1]
    last.v[last.v.length - 1] += CARET
  }
  return blocks.map((b) => {
    const items = b.v.map(mdInline)
    if (b.t === 'p') return `<p>${items.join('<br>')}</p>`
    return `<${b.t}>${items.map((s) => `<li>${s}</li>`).join('')}</${b.t}>`
  }).join('')
}

/** 该条是否为流式中的最后一条 */
function isStreamingAt(i) {
  return streaming.value && i === messages.value.length - 1
}

/** 是否与上一条同角色（同角色不再重复头像/时间，即消息分组） */
function isLead(i) {
  if (i === 0) return true
  return messages.value[i - 1].role !== messages.value[i].role
}

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
  scrollBottom()   // 进会话即停在最新一条（聊天页的通行行为）
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
  messages.value.push({ role: 'user', content: text || '[图片]', image, createdAt: now() })
  input.value = ''
  previewImg.value = ''
  scrollBottom()

  // 占位助手消息：流式逐字填充 / 非流式一次填满（统一渲染源，避免双框）
  messages.value.push({ role: 'assistant', content: '', createdAt: now() })
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

/** 本地时间戳（后端返回 createdAt，本地追加的消息补一个，保证时间戳一致可见） */
function now() { return new Date().toISOString() }

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
  narrowMq.addEventListener('change', onNarrowChange)
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

onBeforeUnmount(() => narrowMq.removeEventListener('change', onNarrowChange))
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
/* 移动端专用元素：默认不参与任何布局，保证桌面端渲染与改动前完全一致 */
.stage-tip { display: none; }
.conv-toggle-m { display: none; }
.stage-toggle { display: none; }
.act-img-m { display: none; }
.send-btn { display: none; }

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
/* 桌面端：包裹层不产生盒子（display:contents），气泡仍是 .msg-row 的 flex 子项，
   宽度/对齐与改动前一致；头像与时间戳在桌面隐藏 */
.msg-main { display: contents; }
.msg-avatar { display: none; }
.msg-meta { display: none; }
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

/* 助手 Markdown 内容：块级元素由 v-html 生成，收不到 scoped 属性，用 :deep() 下发样式 */
.bubble-text.md { white-space: normal; }
.bubble-text.md :deep(p) { margin: 0 0 6px; }
.bubble-text.md :deep(p:last-child) { margin-bottom: 0; }
.bubble-text.md :deep(ul),
.bubble-text.md :deep(ol) { margin: 4px 0 6px; padding-left: 20px; }
.bubble-text.md :deep(ul) { list-style: disc; }
.bubble-text.md :deep(ol) { list-style: decimal; }
.bubble-text.md :deep(li) { margin: 2px 0; }
.bubble-text.md :deep(strong) { font-weight: 700; }
.bubble-text.md :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: .92em; padding: 1px 5px; border-radius: 4px; background: rgba(0,0,0,.06);
}
.assistant .bubble-text.md :deep(code) { background: #f2f3f5; color: #c7254e; }
.bubble-text.md :deep(.md-price) { font-style: normal; font-weight: 700; color: #e8562c; }
.assistant .bubble-text.md :deep(.md-price) { color: var(--clr-primary, #ff5000); }

/* 输入区：desktop 为「左输入框 + 右操作列」；.input-row 只是输入框的包裹层，视觉等价 */
.input-area { display: flex; gap: 10px; padding: 12px 14px; border-top: 1px solid #eee; align-items: flex-end; background: #fff; }
.input-row { flex: 1; min-width: 0; display: flex; align-items: flex-end; gap: 8px; }
.input-box { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.img-preview { position: relative; width: 72px; height: 72px; border-radius: 10px; overflow: hidden; }
.img-preview img { width: 100%; height: 100%; object-fit: cover; }
.img-remove {
  position: absolute; top: 2px; right: 2px; width: 18px; height: 18px; line-height: 16px;
  text-align: center; background: rgba(0,0,0,.55); color: #fff; border-radius: 50%;
  cursor: pointer; font-size: 13px;
}
.input-actions { display: flex; flex-direction: column; gap: 8px; align-items: center; }
.act-img { display: contents; }   /* 桌面：包裹层不产生盒子，el-upload 仍直接参与操作列布局 */
.toggle-row { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.toggle-label { font-size: 12px; color: #999; }

@media (max-width: 900px) {
  .chat-layout { flex-direction: column; gap: 12px; }
  .voice-stage-side { width: 100%; max-width: 100%; height: 280px; min-height: 240px; }
  .chat-card { height: auto; min-height: 70vh; }
}

/* =====================================================================
   移动端适配（≤ 768px）—— 桌面端（>768px）样式完全不受影响
   ===================================================================== */
@media (max-width: 768px) {
  /* ── ① 独立高度骨架 ────────────────────────────────────────────────
     此前 .msg-area 不滚（scrollHeight === clientHeight），整页靠 window 滚，
     于是 sticky 的输入区只能"粘在文档中段"，下方还压着 552px 消息、并与底部
     标签栏之间露出半截正文。现在把 /chat 变成一个真正的 App 页：
     页面本体脱离文档流铺满视口，用 padding 让出顶栏与标签栏，内部自成高度链。
     顶栏 48px 之后留 8px 视觉间隙（App.vue 全局未重置 body 的 8px 默认外边距，
     这里顺带把它算进"间隙"，即使将来全局修掉也只会多出 8px 空白，不会错位）。 */
  .chat-page {
    position: fixed;
    inset: 0;
    z-index: 60;                 /* 顶栏 100 / 标签栏 120 在其之上，不遮壳 */
    display: flex;
    flex-direction: column;
    background: var(--clr-bg, #f5f6f8);
    padding: calc(var(--hdr-h, 48px) + 8px) 10px calc(var(--tabbar-h, 52px) + var(--safe-b, 0px));
    overflow: hidden;
  }
  /* 标题由卡片工具条承担，省下首屏 40px */
  .page-title { display: none; }

  .chat-layout { flex: 1; min-height: 0; display: flex; flex-direction: column; gap: 8px; }

  /* AI 形象：默认收起（canvas 在 3.18:1 的窄条里必然裁到腿，且穿帮）；
     点工具条上的 32px 头像入口才展开成 1.2:1 的舞台，裁切落在卡内 */
  .voice-stage-side { display: none; }
  .voice-stage-side.is-open {
    display: block;
    width: 100%; max-width: 100%; min-width: 0;
    flex: 0 0 auto;
    height: 200px;
    border-radius: var(--r-md, 12px);
  }
  .stage-tip {
    display: block; flex: 0 0 auto; margin: -2px 0 0; text-align: center;
    font-size: 11px; color: var(--clr-text-3, #999);
  }
  /* voiceStage 自带的提示胶囊压在角色腰腹上：移动端隐藏，改由上面的 .stage-tip 承担 */
  .voice-stage-side :deep(.badge) { display: none; }

  /* 卡片吃满剩余高度，内部链条 chat-body → msg-area 逐级 min-height:0 */
  .chat-card {
    flex: 1; height: auto; min-height: 0; overflow: hidden;
    border-radius: var(--r-md, 12px);
  }

  .chat-toolbar { padding: 6px 8px; gap: 8px; border-radius: var(--r-md, 12px) var(--r-md, 12px) 0 0; }
  .drawer-toggle { display: none; }                 /* 桌面文案按钮 → 移动端图标按钮 */
  .toolbar-title { flex: 1; min-width: 0; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

  .icon-btn {
    display: inline-flex; align-items: center; justify-content: center;
    width: 40px; height: 40px; padding: 0; flex: 0 0 auto;
    border: none; border-radius: 50%; background: transparent;
    color: var(--clr-text-2, #666); font-size: 20px; cursor: pointer;
  }
  .icon-btn:active { background: #f0f0f0; }
  .icon-btn.is-on { color: var(--clr-primary, #ff5000); background: var(--clr-primary-bg, #fff3ec); }
  .conv-toggle-m { display: inline-flex; }
  .stage-toggle {
    display: inline-flex; align-items: center; justify-content: center;
    width: 34px; height: 34px; padding: 0; flex: 0 0 auto;
    border: none; border-radius: 50%; cursor: pointer;
    font-size: 13px; font-weight: 700; color: #fff;
    background: linear-gradient(135deg, #ffb347, #ff5000);
    box-shadow: 0 1px 4px rgba(255, 80, 0, .3);
  }
  .stage-toggle.is-on { box-shadow: 0 0 0 2px var(--clr-primary-bg, #fff3ec); }

  /* 会话列表：左侧竖列 → 顶部横向滚动条（width:100% + border-box，杜绝 3px 横向溢出） */
  .chat-body { flex: 1 1 auto; min-height: 0; flex-direction: column; }
  .conv-list {
    width: 100%; min-width: 0; flex: 0 0 auto; display: flex; gap: 8px;
    overflow-x: auto; overflow-y: hidden;
    border-right: none; border-bottom: 1px solid var(--clr-border-light, #f5f5f5);
    padding: 8px 10px; -webkit-overflow-scrolling: touch;
  }
  .conv-head { display: none; }
  .conv-item {
    flex: 0 0 auto; max-width: 60vw; margin-bottom: 0; padding: 8px 14px;
    min-height: 40px; display: flex; align-items: center;
    background: #f5f5f5; border-radius: 999px; font-size: 12px;
    -webkit-tap-highlight-color: transparent;
  }
  .conv-item:active { background: #ebebeb; }
  .conv-item.active { background: var(--clr-primary-bg, #fff3ec); color: var(--clr-primary, #ff5000); }

  /* ── ② 消息区自己滚（页面不再滚）──────────────────────────────── */
  .msg-area {
    flex: 1 1 auto; min-height: 0;
    overflow-y: auto; -webkit-overflow-scrolling: touch;
    overscroll-behavior: contain;
    padding: 12px 10px; gap: 12px;
    background: var(--clr-bg, #f5f6f8);
  }

  /* 头像 / 时间戳 / 分组 */
  .msg-row { align-items: flex-start; gap: 8px; }
  .msg-main { display: flex; flex-direction: column; max-width: 84%; min-width: 0; }
  .msg-row.user .msg-main { align-items: flex-end; order: 1; }
  .msg-row.user .msg-avatar { order: 2; }
  .msg-avatar {
    display: flex; align-items: center; justify-content: center; flex: 0 0 auto;
    width: 30px; height: 30px; margin-top: 16px;
    border-radius: 50%; font-size: 12px; font-weight: 700; color: #fff;
    background: linear-gradient(135deg, #ffb347, #ff5000);
  }
  .msg-row.user .msg-avatar { background: linear-gradient(135deg, #9aa4b2, #6b7280); }
  .msg-row:not(.is-lead) .msg-avatar { visibility: hidden; height: 0; margin-top: 0; }
  .msg-row:not(.is-lead) { margin-top: -6px; }
  .msg-meta {
    display: flex; align-items: center; gap: 6px;
    margin: 0 4px 4px; font-size: 11px; color: var(--clr-text-3, #999);
  }
  .msg-row:not(.is-lead) .msg-meta { display: none; }
  .msg-row.user .msg-meta { flex-direction: row-reverse; }
  .msg-who { color: var(--clr-text-3, #999); }

  .bubble { max-width: 100%; padding: 10px 13px; font-size: 15px; border-radius: var(--r-md, 12px); }
  .user .bubble { border-bottom-right-radius: 4px; }
  .assistant .bubble { border-bottom-left-radius: 4px; }
  .msg-img { max-width: 100%; max-height: 220px; border-radius: var(--r-md, 12px); }

  /* 带图消息：气泡本体透明、零内边距，图片自成一个 12px 圆角块（去掉套在照片上的橙色粗边） */
  .bubble.has-img { background: transparent; border: none; box-shadow: none; padding: 0; }
  .bubble.has-img .msg-img { margin-bottom: 0; border: none; }
  .bubble.has-img .bubble-text { display: inline-block; margin-top: 6px; padding: 9px 13px; border-radius: var(--r-md, 12px); }
  .user .bubble.has-img .bubble-text {
    background: #e8562c; color: #fff; border-bottom-right-radius: 4px;
  }
  .assistant .bubble.has-img .bubble-text {
    background: #fff; border: 1px solid var(--clr-border, #ececec); border-bottom-left-radius: 4px;
  }

  /* 快捷问题 chips：≥40px 触摸区 + 按压反馈 */
  .empty-tip { margin: 16px auto; }
  .empty-tip ul { padding: 0; }
  .empty-tip li {
    display: flex; align-items: center; justify-content: center;
    min-height: 44px; margin: 8px 0; padding: 10px 14px;
    background: #fff; border: 1px solid var(--clr-border, #ececec);
    border-radius: 999px; font-size: 13px; color: var(--clr-text-2, #666);
    cursor: pointer; -webkit-tap-highlight-color: transparent;
  }
  .empty-tip li:active { background: var(--clr-primary-bg, #fff3ec); color: var(--clr-primary, #ff5000); border-color: #ffd0bc; }

  /* ── ③ 输入区回归卡片底部（static，不再 sticky/fixed）──────────── */
  .input-area {
    position: static; flex: 0 0 auto;
    flex-direction: column; align-items: stretch; gap: 6px;
    padding: 8px 10px;
    border-radius: 0; border-top: 1px solid var(--clr-border, #ececec);
    background: var(--clr-bg-card, #fff);
  }
  .input-row { width: 100%; flex: 0 0 auto; align-items: flex-end; gap: 8px; }
  .input-box { width: auto; }
  .act-img-m { display: block; flex: 0 0 auto; }
  .act-img-m :deep(.el-upload) { display: block; }
  .send-btn {
    display: inline-flex; align-items: center; justify-content: center;
    width: 44px; height: 44px; flex: 0 0 auto; padding: 0;
    border: none; border-radius: 50%; color: #fff; font-size: 20px;
    background: var(--clr-primary, #ff5000);
    box-shadow: 0 2px 8px rgba(255, 80, 0, .28);
  }
  .send-btn:disabled { background: #ffc7ab; box-shadow: none; }
  .send-loading {
    width: 16px; height: 16px; border-radius: 50%;
    border: 2px solid rgba(255,255,255,.45); border-top-color: #fff;
    animation: spin .7s linear infinite;
  }
  @keyframes spin { to { transform: rotate(360deg); } }

  /* 次级操作收成一条 40px 的操作条：左「流式输出」，右「新会话」 */
  .input-actions {
    flex-direction: row; flex-wrap: nowrap; align-items: center;
    gap: 8px; width: 100%;
  }
  .toggle-row { margin-right: auto; min-height: 40px; }
  .toggle-row :deep(.el-switch) { height: 40px; }   /* 触摸区抬高到 40px（视觉核心仍是 small） */
  .act-img { display: none; }                        /* 附件入口已移到输入行左侧 */
  .act-send { display: none; }                       /* 发送已改为输入行右侧的圆形按钮 */
  .act-new { min-height: 40px; }

  /* ≥16px 防 iOS 聚焦时整页缩放；圆角胶囊，视觉上从"表单"变成"聊天输入条" */
  .input-box :deep(.el-textarea__inner) {
    font-size: 16px;
    line-height: 1.5;
    padding: 11px 14px;
    border-radius: 22px;
    background: #f4f5f7;
    box-shadow: none;
  }
  .img-preview { width: 56px; height: 56px; }

  /* 输入焦点态：整卡描边（覆盖 EP 默认的聚焦内阴影被清掉后的无反馈） */
  .input-box :deep(.el-textarea__inner:focus) { background: #fff; box-shadow: 0 0 0 1px #ffd0bc inset; }
}

@media (max-width: 480px) {
  /* 更窄的屏幕：舞台再矮一点，消息气泡再宽一点 */
  .voice-stage-side.is-open { height: 180px; }
  .msg-main { max-width: 86%; }
}
</style>
