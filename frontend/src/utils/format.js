/**
 * 展示层格式化工具（前端统一出口）
 *
 * 为什么单独抽出来：后端返回的是原始字段（ISO 时间串、不带千分位的金额），
 * 直接在模板里渲染会出现 `2026-09-09T12:30:30` 这种"把数据库字段怼到用户脸上"的画面——
 * 商业级产品不会这样。所有面向用户的格式化都走这里，保证全站口径一致。
 */

/**
 * 时间：`2026-09-09T12:30:30` / `2026-09-09 12:30:30` → `2026-09-09 12:30`
 * 最近 7 天内显示相对时间（刚刚 / N 分钟前 / N 小时前 / N 天前），更早显示日期
 */
export function formatTime(input) {
  if (!input) return ''
  const d = toDate(input)
  if (!d) return String(input)

  const diff = Date.now() - d.getTime()
  const min = 60 * 1000, hour = 60 * min, day = 24 * hour

  if (diff >= 0) {
    if (diff < min) return '刚刚'
    if (diff < hour) return `${Math.floor(diff / min)} 分钟前`
    if (diff < day) return `${Math.floor(diff / hour)} 小时前`
    if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  }
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 只到日期：`2026-09-09` */
export function formatDate(input) {
  const d = toDate(input)
  if (!d) return input ? String(input) : ''
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 金额：加千分位并保留两位小数 → `5,216.00`（后端可能给 number 或 "5216"） */
export function formatAmount(v) {
  const n = Number(v)
  if (!isFinite(n)) return String(v ?? '')
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 价格展示（整数时不留 .00，电商习惯）→ `1,299` / `59.9` */
export function formatPrice(v) {
  const n = Number(v)
  if (!isFinite(n)) return String(v ?? '')
  return Number.isInteger(n)
    ? n.toLocaleString('zh-CN')
    : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 大数：1.2万 */
export function formatCount(v) {
  const n = Number(v)
  if (!isFinite(n)) return '0'
  if (n < 10000) return String(n)
  return (n / 10000).toFixed(1).replace(/\.0$/, '') + '万'
}

function p(n) { return String(n).padStart(2, '0') }

/** 兼容 `YYYY-MM-DD HH:mm:ss`（Safari/iOS 对空格分隔的解析不一致，故显式替换为 T） */
function toDate(input) {
  if (input instanceof Date) return isNaN(input.getTime()) ? null : input
  const s = String(input).trim()
  const d = new Date(s.includes('T') ? s : s.replace(' ', 'T'))
  return isNaN(d.getTime()) ? null : d
}
