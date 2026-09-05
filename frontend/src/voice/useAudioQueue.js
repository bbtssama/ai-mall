/**
 * 播放队列（复用自 PaimonLiveWeb5，去派蒙品牌、TTS 终点可配置）。
 *
 * 每个 sentence 事件 → 立刻 POST /api/v1/voice/tts 预取音频（并行），
 * 再按句子顺序依次播放；轮到某句时调用 playFn(url, emo, act) 让 Live2D 张嘴 + 做表情/动作。
 *
 * 设计要点：
 * - TTS 预取与播放解耦：合成慢不阻塞已就绪句子的连续衔接。
 * - generation 代际号：用户发新消息 / 点打断时 stop() 清空队列并作废旧任务。
 * - 任何一步失败只丢弃该句（console.warn），绝不向上抛错崩页（契约容错要求）。
 */
import { voiceApi } from '../api'

/**
 * POST /api/v1/voice/tts → 返回可播放的 blob URL；失败抛错由调用方捕获降级。
 * 可传自定义 text→Promise<url> 的合成函数以换音色/换服务。
 */
export async function fetchTtsUrl(text) {
  return voiceApi.voiceTts(text)
}

/**
 * 前端按句切分：把整段回复切成适合逐句朗读（TTS + 口型/表情/动作）的句子。
 * 以句末标点（。！？；!?\n）切分，标点随句保留；空句/纯空白丢弃。
 * @param {string} text
 * @returns {string[]}
 */
export function splitSentences(text) {
  const s = (text || '').replace(/\r/g, '')
  const out = []
  let buf = ''
  for (let i = 0; i < s.length; i++) {
    buf += s[i]
    if ('。！？；!?\n'.includes(s[i])) {
      const t = buf.trim()
      if (hasText(t)) out.push(t)   // 只保留含文字（中文/英文/数字）的句子，纯表情/符号句不读
      buf = ''
    }
  }
  const tail = buf.trim()
  if (hasText(tail)) out.push(tail)
  return out
}

/** 句子是否含有效文字（中英文/数字）；纯 emoji/符号句跳过，避免派蒙 VITS 因特殊字符失败回退导致音色漂移 */
function hasText(s) {
  return /[\p{L}\p{N}]/u.test(s)
}

/**
 * @param {(url: string, emo: string, act: string) => Promise<void>} playFn 播放一句（resolve 即本句播完）
 * @param {() => void} stopCurrentFn 打断当前正在播放的一句
 * @param {() => void} onIdle 队列清空且当前句播完时回调（用于隐藏打断按钮）
 * @param {{ fetchTts?: (text:string)=>Promise<string> }} [options] 可选：覆盖 TTS 取流函数
 */
export function createAudioQueue(playFn, stopCurrentFn, onIdle, options = {}) {
  const fetchTts = options.fetchTts || fetchTtsUrl
  let items = []          // [{ promise: Promise<{url?, error?}>, emo, act }]
  let playing = false
  let generation = 0

  /** 入队一句：立即发起 TTS 预取，顺序等待播放（emo 表情 / act 动作指令随句透传） */
  function enqueue(text, emo = 'normal', act = '') {
    if (!text || !text.trim()) return
    const myGen = generation
    const promise = fetchTts(text)
      .then((url) => ({ url }))
      .catch((err) => {
        console.warn('[audioQueue] TTS 预取失败，跳过该句:', err && err.message)
        return { error: err }
      })
    items.push({ promise, emo, act, gen: myGen })
    pump()
  }

  async function pump() {
    if (playing) return
    playing = true
    try {
      while (items.length > 0) {
        const item = items.shift()
        const result = await item.promise // 等待该句 TTS 就绪
        if (item.gen !== generation) continue // 已被打断作废
        if (!result || result.error) continue // 合成失败：静默丢句
        try {
          await playFn(result.url, item.emo, item.act) // 播完才轮到下一句（口型同步在 playFn 内）
        } catch (err) {
          console.warn('[audioQueue] 播放失败，跳过该句:', err)
        }
      }
    } finally {
      playing = false
      // 全部播完（且没有新入队）→ 通知外部回到空闲态；失败也绝不抛错
      try { if (items.length === 0 && onIdle) onIdle() } catch (e) { /* 忽略 */ }
    }
  }

  /** 打断：清空队列 + 作废代际 + 停掉当前发声 */
  function stop() {
    generation++
    items = []
    try { stopCurrentFn && stopCurrentFn() } catch (e) { /* 忽略 */ }
  }

  return { enqueue, stop }
}
