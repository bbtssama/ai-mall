import request from './request'

// ---------- 认证 ----------
export const authApi = {
  login: (data) => request.post('/v1/auth/login', data),
  register: (data) => request.post('/v1/auth/register', data),
  logout: () => request.post('/v1/auth/logout'),
  me: () => request.get('/v1/auth/me')
}

// ---------- 商品 ----------
export const productApi = {
  page: (params) => request.get('/v1/products', { params }),
  detail: (id) => request.get(`/v1/products/${id}`)
}

// ---------- 购物车 ----------
export const cartApi = {
  list: () => request.get('/v1/cart'),
  add: (data) => request.post('/v1/cart', data),
  update: (id, data) => request.put(`/v1/cart/${id}`, data),
  remove: (id) => request.delete(`/v1/cart/${id}`),
  clear: () => request.delete('/v1/cart')
}

// ---------- 订单 ----------
export const orderApi = {
  create: (data) => request.post('/v1/orders', data),
  page: (params) => request.get('/v1/orders', { params }),
  detail: (id) => request.get(`/v1/orders/${id}`),
  cancel: (id) => request.post(`/v1/orders/${id}/cancel`)
}

// ---------- 收货地址 ----------
export const addressApi = {
  list: () => request.get('/v1/addresses'),
  add: (data) => request.post('/v1/addresses', data),
  update: (id, data) => request.put(`/v1/addresses/${id}`, data),
  remove: (id) => request.delete(`/v1/addresses/${id}`),
  setDefault: (id) => request.put(`/v1/addresses/${id}/default`)
}

// ---------- 内容社区（V2：种草笔记） ----------
export const noteApi = {
  // Feed 流：游标分页（cursorId/cursorHot 由上页末条提供）
  page: (params) => request.get('/v1/notes', { params }),
  detail: (id) => request.get(`/v1/notes/${id}`),
  create: (data) => request.post('/v1/notes', data),
  like: (id, liked) => request.post(`/v1/notes/${id}/like`, { liked }),
  collect: (id, collected) => request.post(`/v1/notes/${id}/collect`, { collected }),
  resubmit: (id) => request.post(`/v1/notes/${id}/resubmit`),
  offline: (id) => request.delete(`/v1/notes/${id}`)
}

// ---------- 支付（V3） ----------
export const payApi = {
  create: (orderId, channel) => request.post('/v1/payments', { orderId, channel }),
  detail: (paymentNo) => request.get(`/v1/payments/${paymentNo}`),
  byOrder: (orderId) => request.get(`/v1/payments/order/${orderId}`),
  // 主动查单对账（兜"回调丢失"）
  sync: (paymentNo) => request.post(`/v1/payments/${paymentNo}/sync`),
  // 仅 MOCK 渠道：模拟"用户在第三方完成支付"（内部走真实回调链路）
  mockPay: (paymentNo) => request.post(`/v1/payments/mock-pay/${paymentNo}`)
}

// ---------- 限量发售（V3） ----------
export const dropApi = {
  ongoing: () => request.get('/v1/drops'),
  detail: (id) => request.get(`/v1/drops/${id}`),
  buy: (id, quantity = 1) => request.post(`/v1/drops/${id}/buy`, { quantity })
}

// ---------- AI 内容创作（V2） ----------
export const aiContentApi = {
  // AI 生成种草文案草稿（返回 {draft, editable}，用户编辑后再发布）
  noteDraft: (data) => request.post('/v1/ai/note-draft', data)
}

// ---------- 文件上传（V1.5） ----------
export const fileApi = {
  upload: (file, dir = 'notes') => {
    const form = new FormData()
    form.append('file', file)
    form.append('dir', dir)
    return request.post('/v1/files/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

// ---------- AI 问答 ----------
export const chatApi = {
  conversations: () => request.get('/v1/chat/conversations'),
  createConversation: (data) => request.post('/v1/chat/conversations', data),
  messages: (id) => request.get(`/v1/chat/conversations/${id}/messages`),
  send: (data) => request.post('/v1/chat', data),
  // SSE 流式：POST /api/v1/chat/stream，逐块回调 onChunk
  sendStream: async (data, onChunk, onDone, onError) => {
    const resp = await fetch('/api/v1/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: localStorage.getItem('token') || ''
      },
      body: JSON.stringify(data)
    })
    if (!resp.ok || !resp.body) {
      onError?.(new Error('流式请求失败'))
      return
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 解析 SSE：按 "data:" 行切分
      const lines = buffer.split('\n')
      buffer = lines.pop()
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          const payload = trimmed.slice(5).trim()
          if (payload && payload !== '[DONE]') onChunk?.(payload)
        }
      }
    }
    onDone?.()
  }
}

// ---------- 语音动效（单一 TTS：前端按句切分后逐句合成派蒙语音） ----------
// 仅保留 POST /api/v1/voice/tts；SSE 句子契约(session/sentence)与语音会话(list/messages)已随"单一 AI 页"简化移除。
export const voiceApi = {
  /** POST /api/v1/voice/tts {text} → 可播放的 objectURL（失败抛错由调用方捕获降级） */
  voiceTts: async (text) => {
    const resp = await fetch('/api/v1/voice/tts', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json;charset=utf-8',
        Authorization: localStorage.getItem('token') || ''
      },
      body: JSON.stringify({ text })
    })
    if (!resp.ok) throw new Error(`TTS HTTP ${resp.status}`)
    const blob = await resp.blob()
    return URL.createObjectURL(blob)
  }
}