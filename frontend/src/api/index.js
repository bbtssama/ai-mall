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