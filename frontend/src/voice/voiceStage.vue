<script>
/**
 * voiceStage.vue —— 通用 Live2D 语音/动效舞台（自 PaimonLiveWeb5 Live2DCanvas 平移并去品牌化、参数化）。
 *
 * 【渲染栈】ESM：pixi.js@7 + pixi-live2d-display@0.5.0-beta(cubism4)。
 *  index.html 需先加载全局 live2dcubismcore.min.js（window.Live2DCubismCore）——
 *  本组件会在 init 前按需动态注入 /vendor/live2dcubismcore.min.js，缺失时降级提示、聊天不受影响。
 *
 * 职责：
 * 1. 加载可配置模型（默认 /assets/model/model.model3.json）
 * 2. 视线跟随鼠标；居中自适应 + 「角色净高」放大
 * 3. 表情：emo → exp3 按名应用，播完语音回落 normal
 * 4. 动作指令 [act:名称]：performAct() 按 Name/文件名在动作组内精确查找播放
 * 5. 口型同步：WebAudio 时域 RMS → ParamMouthOpenY，写 PIXI Ticker LOW 优先级，说话态全程接管嘴参数
 * 6. 待机生动化：呼吸/眨眼物理 + 视线跟随；不做无声动作（模型动作「烤嘴省参数」，静音播会空口嚼台词）
 * 7. 点击小人：随机 TapBody 反应（FORCE 优先级 + 模型自带原声），带冷却
 *
 * 【参数化（换角色/换表情/换动作组零改动核心代码）】
 *   modelUrl        模型 settings json 路径
 *   modelBase       由 modelUrl 推导（音效/贴图相对路径基准）
 *   expressionTable emo → exp3 表
 *   motionGroups    搜索动作时遍历的动作组（如 ['TapBody']）
 *   characterName   展示名（提示/角标文案）
 *
 * 对父组件暴露：playAudio(url, emo, act) / stopSpeaking() / performAct(name)
 */
import * as PIXI from 'pixi.js'
// 注意：pixi-live2d-display/cubism4 在模块加载期会校验 window.Live2DCubismCore，缺失即抛错。
// 因此不能用顶层静态 import——否则一旦 core 脚本加载失败（网络问题 / 资产被移除以便公开分发）就会整页白屏。
// 改为在 onMounted 内运行时动态 import + try-catch（见 init()），失败仅降级占位，聊天功能不受影响。
// 这里不 import cubism4，也不做 Live2DModel.registerTicker / config.sound —— 全部移到 init() 动态加载成功后再执行。

const CORE_SCRIPT = '/vendor/live2dcubismcore.min.js'

// 口型参数常量
const MOUTH_ZERO_GRACE_FRAMES = 12    // 声音结束后继续强制闭嘴的帧数（防动作曲线复活嘴型）
const MOUTH_SMOOTH_ATTACK = 0.55      // 开口快跟
const MOUTH_SMOOTH_RELEASE = 0.35     // 闭合快收
// PIXI 渲染分辨率上限：跟随实际 devicePixelRatio（原 Math.min(dpr,2) 把高分屏压成 2 导致模糊/锯齿）。
// 放宽到 4 覆盖 2x/3x Retina 与常见浏览器缩放（如 2x 屏 200% 缩放=DPR4），兼顾高清与性能（舞台约 300px，4x buffer 不大）。
const MAX_RESOLUTION = 4

export default {
  name: 'VoiceStage',
  emits: ['status'],
  props: {
    modelUrl: { type: String, default: '/assets/model/model.model3.json' },
    expressionTable: {
      type: Object,
      default: () => ({
        normal: 'normal',
        happy: 'happy',
        angry: 'angry',
        sad: 'sad',
        shy: 'shy',
        surprised: 'surprised'
      })
    },
    motionGroups: { type: Array, default: () => ['TapBody'] },
    characterName: { type: String, default: '语音角色' }
  },
  data() {
    return {
      status: 'loading', // loading | ready | error
      errorMsg: ''
    }
  },
  computed: {
    modelBase() {
      const idx = this.modelUrl.lastIndexOf('/')
      return idx >= 0 ? this.modelUrl.slice(0, idx + 1) : '/'
    }
  },
  mounted() {
    this._app = null
    this._model = null
    this._destroyed = false
    this._initStarted = false
    this._speaking = false       // 正在播 TTS 或点击反应
    this._mouthHold = false      // 说话/点击期间嘴部接管（声音结束仍钉住短暂时间）
    this._currentEmo = 'normal'  // 当前应保持的情绪表情名（动作结束后据此回位，防挤眼残留）
    this._idleTimer = null
    this._pokeBusy = false
    this._lastInteract = Date.now()
    this._onResize = () => this._scheduleAutoResize()
    this._onMouseMove = (e) => { this._lastInteract = Date.now(); this.focusAtClient(e.clientX, e.clientY) }
    window.addEventListener('resize', this._onResize)
    window.addEventListener('mousemove', this._onMouseMove)
    // 自动播放策略解锁：任意手势都尝试恢复 AudioContext（防 TTS 被挂起上下文静音吞掉）
    this._unlockAudio = () => {
      try { if (this._audioCtx && this._audioCtx.state === 'suspended') this._audioCtx.resume() } catch (e) { /* 忽略 */ }
    }
    window.addEventListener('pointerdown', this._unlockAudio)
    window.addEventListener('keydown', this._unlockAudio)
    // 跟随设备像素比(DPR)：浏览器缩放/换屏时让 PIXI resolution 实时更新，避免派蒙模糊/锯齿
    this._watchDpr()
    // 容器尺寸变化（布局/视口/抽屉开合等）：ResizeObserver 精确感知，配合 DPR 一并重算缩放
    this._setupResizeObserver()
    this.init()
  },
  beforeUnmount() {
    this._destroyed = true
    this._unwatchDpr()
    this._teardownResizeObserver()
    if (this._resizeTimer) { clearTimeout(this._resizeTimer); this._resizeTimer = null }
    window.removeEventListener('resize', this._onResize)
    window.removeEventListener('mousemove', this._onMouseMove)
    window.removeEventListener('pointerdown', this._unlockAudio)
    window.removeEventListener('keydown', this._unlockAudio)
    this.stopIdleTimer()
    this.stopFaceWatchdog()
    this.detachLipLoop()
    this.destroyApp()
  },
  methods: {
    /** 按需注入 cubism core 全局脚本，返回是否就绪 */
    async ensureCubismCore() {
      if (typeof window.Live2DCubismCore !== 'undefined') return true
      await this._injectCoreScript(CORE_SCRIPT)
      return typeof window.Live2DCubismCore !== 'undefined'
    },
    _injectCoreScript(src) {
      return new Promise((resolve) => {
        const existing = document.querySelector('script[data-live2d-core]')
        if (existing) {
          const t = setInterval(() => {
            if (typeof window.Live2DCubismCore !== 'undefined') { clearInterval(t); resolve() }
          }, 40)
          setTimeout(() => { clearInterval(t); resolve() }, 3000)
          return
        }
        const s = document.createElement('script')
        s.src = src
        s.async = true
        s.dataset.live2dCore = '1'
        s.onload = () => resolve()
        s.onerror = () => resolve()
        document.head.appendChild(s)
      })
    },

    /** 初始化 PIXI 与模型；任何失败都友好提示而非白屏 */
    async init() {
      if (this._initStarted || this._destroyed) return // 单例保护
      this._initStarted = true

      const coreOk = await this.ensureCubismCore()
      // 动态 import cubism4：顶层静态 import 会在模块加载期校验 window.Live2DCubismCore，缺失即抛错导致整页白屏；
      // 改为 onMounted 内运行时 import + try-catch，皮套/core 缺失时优雅降级（显示占位，聊天照常）。
      let Live2DModel
      try {
        const mod = await import('pixi-live2d-display/cubism4')
        Live2DModel = mod.Live2DModel
        Live2DModel.registerTicker(PIXI.Ticker)
        mod.config.sound = false // 禁用插件自动放动作原声，统一走前端口型管线
        if (!coreOk) throw new Error('Cubism core 未加载')
      } catch (err) {
        console.warn('[live2d] 皮套引擎/核心缺失，降级为纯文字聊天:', err && err.message)
        return this.fail('皮套/语音动效未能加载（Live2D 运行时或 Cubism Core 缺失）。聊天、图片识别、工具检索、会话全部照常；确认 frontend/public/vendor/ 与 public/assets/model/ 就位后刷新即可见到皮套与动效。')
      }
      try {
        this._app = new PIXI.Application({
          view: this.$refs.canvasEl,
          resizeTo: this.$refs.wrapEl,
          backgroundAlpha: 0,
          antialias: true,
          autoDensity: true,
          // 跟随实际 DPR（上限放宽到 3，原 Math.min(dpr,2) 会把高分屏压成 2 导致模糊）；
          // 后续 resize/DPR 变化时由 applyAutoResolution() 继续实时更新。
          resolution: Math.min(window.devicePixelRatio || 1, MAX_RESOLUTION),
          preserveDrawingBuffer: true // 供自动化截图/导出真实画面
        })
      } catch (err) {
        console.warn('[live2d] PIXI 初始化失败:', err)
        return this.fail('画布初始化失败，请检查浏览器 WebGL 支持后刷新重试。')
      }
      try {
        await this.prevalidateModelAssets(this.modelUrl)
        this._model = await Live2DModel.from(this.modelUrl)
      } catch (err) {
        console.warn('[live2d] 模型加载失败:', err)
        this.destroyApp()
        if (err && err.isAssetError) {
          return this.fail('皮套资产异常：' + err.message + '。请 Ctrl+F5 强刷；仍复现请重新部署模型资产（聊天与语音不受影响）。')
        }
        return this.fail('皮套/语音资产未安装（model3.json 缺失或加载失败）。聊天、图片识别、工具检索、会话全部照常；部署资产后刷新即可见到皮套与动效。')
      }
      if (this._destroyed) return
      this._app.stage.addChild(this._model) // 不挂上舞台 = 永远透明（经典坑）

      // 抓一份「rest 姿态」全参数快照（此时无动作/无表情 = 自然脸）。
      // 该模型有 100+ 参数，面部变形由不少无名参数驱动；动作结束后必须把【全部】参数还原到这份
      // 快照，而不是只写 7 个标准参数——否则「><」等变形残留。
      try {
        const core = this._model.internalModel.coreModel
        const pv = core && core._parameterValues
        if (pv && typeof pv.length === 'number' && pv.length > 0) {
          this._restParams = Float32Array.from(pv)
        }
      } catch (e) { /* 忽略 */ }

      window.__voiceStage = { app: this._app, model: this._model } // 自动化验证句柄
      // 订阅动作结束：动作会把眼睛/嘴/眉挤成各种表情（如【><】撇嘴），结束即把脸拉回当前应有表情，防残留
      this._onMotionFinish = () => {
        if (this._destroyed) return
        if (this._speaking) this.applyEmotion(this._currentEmo || 'normal')
        else this.resetExpression()   // resetExpression 内部已调用 forceNeutralFace()
      }
      try {
        const mm = this._model.internalModel.motionManager
        if (mm && typeof mm.on === 'function') mm.on('motionFinish', this._onMotionFinish)
      } catch (e) { /* 忽略 */ }
      this.fitModel()
      this.status = 'ready'
      this.startIdleTimer()
      this.startFaceWatchdog() // 无动作且眼睛持续闭合时拉回 normal
      this.measureCharAndFit() // 角色净高探测 → 二次放大
      this.$emit('status', { status: 'ready' })
    },

    /** 贴图预检门：逐张 fetch+createImageBitmap 严格解码，坏资产在此精确报错 */
    async prevalidateModelAssets(settingsUrl) {
      if (typeof createImageBitmap !== 'function') return
      const resp = await fetch(settingsUrl, { cache: 'reload' })
      if (!resp.ok) throw this.assetError('model3.json 加载失败 HTTP ' + resp.status)
      const json = await resp.json()
      const refs = json && json.FileReferences && Array.isArray(json.FileReferences.Textures)
        ? json.FileReferences.Textures : []
      if (!refs.length) throw this.assetError('model3.json 缺少 FileReferences.Textures')
      const base = settingsUrl.slice(0, settingsUrl.lastIndexOf('/') + 1)
      const bad = (await Promise.all(refs.map(async (rel) => {
        try {
          const r = await fetch(base + rel, { cache: 'reload' })
          if (!r.ok) throw new Error('HTTP ' + r.status)
          const bmp = await createImageBitmap(await r.blob())
          const ok = bmp.width > 0 && bmp.height > 0
          if (bmp.close) bmp.close()
          if (!ok) throw new Error('解码尺寸为 0')
          return null
        } catch (e) {
          return rel + '（' + ((e && e.message) || e) + '）'
        }
      }))).filter(Boolean)
      if (bad.length) throw this.assetError(bad.join('; '))
    },
    assetError(msg) {
      const e = new Error(msg)
      e.isAssetError = true
      return e
    },

    /** 实时跟随设备像素比(DPR)：更新 PIXI renderer.resolution + 重 resize buffer + 重 fit 模型 */
    applyAutoResolution() {
      if (!this._app || this._destroyed) return
      const dpr = window.devicePixelRatio || 1
      let target = Number.isFinite(dpr) && dpr > 1 ? dpr : 1
      if (target > MAX_RESOLUTION) target = MAX_RESOLUTION
      const renderer = this._app.renderer
      const current = renderer.resolution || 1
      // 当前逻辑(屏幕/CSS)尺寸 = buffer 宽度 / 当前分辨率
      const logicalW = renderer.width / current
      const logicalH = renderer.height / current
      if (Math.abs(current - target) < 0.01) {
        // 分辨率未变（可能只是窗口尺寸变了）：直接重 fit 模型，保证比例正确
        this.fitModel()
        return
      }
      try {
        renderer.resolution = target
        // resize 传入逻辑(CSS)尺寸，PIXI 按新 resolution 重建 buffer（autoDensity 下画布样式尺寸不变）
        renderer.resize(Math.max(1, Math.round(logicalW)), Math.max(1, Math.round(logicalH)))
      } catch (err) {
        console.warn('[live2d] DPR 分辨率更新失败（忽略）:', err && err.message)
      }
      this.fitModel() // 用新分辨率重算缩放，派蒙清晰且比例正确
    },
    /** 监听 DPR 变化：用 matchMedia(resolution: Xdppx) 探测，浏览器缩放/换屏即回调 */
    _watchDpr() {
      this._unwatchDpr()
      const dpr = window.devicePixelRatio || 1
      this._handleDprChange = () => { this._scheduleAutoResize(); this._watchDpr() }
      try {
        this._dprMedia = window.matchMedia(`(resolution: ${dpr}dppx)`)
        if (this._dprMedia.addEventListener) this._dprMedia.addEventListener('change', this._handleDprChange)
        else if (this._dprMedia.addListener) this._dprMedia.addListener(this._handleDprChange) // 旧浏览器兼容
      } catch (e) { /* matchMedia 不可用时静默 */ }
    },
    _unwatchDpr() {
      if (this._dprMedia && this._handleDprChange) {
        try {
          if (this._dprMedia.removeEventListener) this._dprMedia.removeEventListener('change', this._handleDprChange)
          else if (this._dprMedia.removeListener) this._dprMedia.removeListener(this._handleDprChange)
        } catch (e) { /* 忽略 */ }
      }
      this._dprMedia = null
      this._handleDprChange = null
    },

    /** 防抖：把多次 resize/DPR/容器变化合并到一次 applyAutoResolution，避免循环震荡 */
    _scheduleAutoResize() {
      if (this._destroyed) return
      if (this._resizeTimer) return // 已排程，合并
      this._resizeTimer = setTimeout(() => {
        this._resizeTimer = null
        this.applyAutoResolution()
      }, 120)
    },
    /** ResizeObserver：精确监听舞台容器(wrapEl)尺寸变化（布局/抽屉开合/视口/跨屏），变化即重算缩放 */
    _setupResizeObserver() {
      if (typeof ResizeObserver === 'undefined' || !this.$refs.wrapEl) return
      try {
        this._resizeObserver = new ResizeObserver(() => this._scheduleAutoResize())
        this._resizeObserver.observe(this.$refs.wrapEl)
      } catch (err) {
        console.warn('[live2d] ResizeObserver 不可用（忽略）:', err && err.message)
      }
    },
    _teardownResizeObserver() {
      if (this._resizeObserver) {
        try { this._resizeObserver.disconnect() } catch (e) { /* 忽略 */ }
        this._resizeObserver = null
      }
    },

    /** 居中等比缩放；角色净高已知时放大到视口 ~55% */
    fitModel() {
      const app = this._app
      const model = this._model
      if (!app || !model) return
      const w = app.renderer.width / (app.renderer.resolution || 1)
      const h = app.renderer.height / (app.renderer.resolution || 1)
      if (!w || !h) return
      const mw = model.internalModel.originalWidth || model.width || 1
      const mh = model.internalModel.originalHeight || model.height || 1
      let scale = Math.max(0.0001, Math.min(w / mw, h / mh)) * 0.92
      if (this._charH > 0) {
        const charScreen = scale * this._charH
        const boost = (h * 0.55) / charScreen
        scale *= boost > 1 ? Math.min(6, boost) : 1
      }
      model.scale.set(scale > 0 && isFinite(scale) ? scale : 1)
      try { model.anchor.set(0.5, 0.5) } catch (e) { /* 部分版本无 anchor */ }
      model.position.set(w / 2, h * 0.56)
    },

    /** 读全部 drawable 顶点静态 bbox → 角色"画布单位"高度，驱动二次放大 */
    measureCharAndFit(retries = 0) {
      try {
        const core = this._model && this._model.internalModel && this._model.internalModel.coreModel
        if (!core) return
        const ppu = this._model.internalModel.pixelsPerUnit || 1
        let minY = Infinity, maxY = -Infinity, got = false
        const ids = typeof core.getDrawableIds === 'function' ? core.getDrawableIds() : []
        for (let i = 0; i < ids.length; i++) {
          let v = null
          try { v = core.getDrawableVertices(i) } catch (e) { /* 忽略 */ }
          if (!v || !v.length) continue
          got = true
          for (let j = 1; j < v.length; j += 2) {
            if (v[j] < minY) minY = v[j]
            if (v[j] > maxY) maxY = v[j]
          }
        }
        if (!got || !isFinite(minY) || maxY <= minY) {
          if (retries < 6) setTimeout(() => this.measureCharAndFit(retries + 1), 400)
          return
        }
        this._charH = (maxY - minY) * ppu
        this.fitModel()
      } catch (err) {
        console.warn('[live2d] 角色尺寸探测失败（忽略）:', err && err.message)
      }
    },

    /** 全局鼠标 → 模型注视 */
    focusAtClient(clientX, clientY) {
      if (!this._model || this.status !== 'ready') return
      try {
        const rect = this.$refs.canvasEl.getBoundingClientRect()
        this._model.focus(clientX - rect.left, clientY - rect.top)
      } catch (e) { /* 注视失败不影响主流程 */ }
    },

    /** emo → expression 精确应用；失败绝不抛错 */
    applyEmotion(emo) {
      if (emo) this._currentEmo = emo
      const em = this.expressionManager()
      const wanted = this.expressionTable[emo] || this.expressionTable.normal
      if (wanted && em) {
        const idx = this.findExpressionIndex(em, wanted)
        if (idx >= 0) {
          Promise.resolve(em.setExpression(idx)).catch(() => {})
          return true
        }
      }
      return false
    },
    expressionManager() {
      try {
        const mm = this._model && this._model.internalModel && this._model.internalModel.motionManager
        return mm ? mm.expressionManager : null
      } catch (e) { return null }
    },
    findExpressionIndex(em, name) {
      const pools = []
      if (Array.isArray(em.definitions)) pools.push(em.definitions)
      if (Array.isArray(em.expressionMap)) pools.push(em.expressionMap)
      for (const arr of pools) {
        for (let i = 0; i < arr.length; i++) {
          if (arr[i] && (arr[i].Name === name || arr[i].name === name)) return i
        }
      }
      return -1
    },
    resetExpression() {
      this._currentEmo = 'normal'
      const em = this.expressionManager()
      if (em) {
        const idx = this.findExpressionIndex(em, this.expressionTable.normal)
        if (idx >= 0) Promise.resolve(em.setExpression(idx)).catch(() => {})
      }
      this.forceNeutralFace() // 兜底：即使表情混合未完全到位，也强制把 7 个面部参数拉回 neutral
    },
    /** 强制把全部面部参数写回 neutral（眼睛开/眉平/嘴角微微笑/嘴闭合/眼球居中） */
    forceNeutralFace() {
      try {
        const core = this._model && this._model.internalModel && this._model.internalModel.coreModel
        if (!core) return
        const rest = this._restParams
        const vals = core._parameterValues
        if (rest && vals && vals.length === rest.length) {
          for (let i = 0; i < vals.length; i++) vals[i] = rest[i]
        }
        const set = (id, v) => {
          const i = typeof core.getParameterIndex === 'function' ? core.getParameterIndex(id) : -1
          if (i >= 0 && typeof core.setParameterValueByIndex === 'function') core.setParameterValueByIndex(i, v)
        }
        set('ParamBrowLY', 0)
        set('ParamEyeLOpen', 1)
        set('ParamEyeROpen', 1)
        set('ParamEyeBallX', 0)
        set('ParamEyeBallY', 0)
        set('ParamMouthForm', 0.15)
        set('ParamMouthOpenY', 0)
        set('ParamCheek', 0)
        set('ParamBrowLForm', 0)
        set('ParamBrowRForm', 0)
        set('ParamBrowLAngle', 0)
        set('ParamBrowRAngle', 0)
        set('ParamEyeLSmile', 0)
        set('ParamEyeRSmile', 0)
      } catch (e) { /* 静默 */ }
    },

    /**
     * 【LLM 动作指令】按名称播放动作：先按定义 Name 精确匹配，再退回按文件名词干匹配。
     * @returns {boolean} 是否成功找到并启动
     */
    performAct(name) {
      if (!this._model || !name || this.status !== 'ready') return false
      const found = this.findMotion(name)
      if (!found) return false
      try {
        const mm = this._model.internalModel.motionManager
        // 说话中的 act 用 FORCE 盖过 idle 循环；空闲时 NORMAL 足够
        Promise.resolve(mm.startMotion(found.group, found.index, this._speaking ? 3 : 2))
          .catch(() => {})
        return true
      } catch (err) {
        console.warn('[live2d] act 播放失败（忽略）:', err)
        return false
      }
    },
    /** 在配置的动作组里找动作 */
    findMotion(name) {
      try {
        const settings = this._model.internalModel.settings
        const groups = this.motionGroups
        const eq = (a, b) => a === b || String(a).replace(/\s+/g, '') === String(b).replace(/\s+/g, '')
        for (const g of groups) {
          const defs = settings.motions && settings.motions[g]
          if (!Array.isArray(defs)) continue
          for (let i = 0; i < defs.length; i++) {
            const d = defs[i]
            const stem = d.File ? String(d.File).split('/').pop().replace(/\.motion3\.json$/i, '') : ''
            if ((d.Name && eq(d.Name, name)) || eq(stem, name)) {
              return { group: g, index: i }
            }
          }
        }
      } catch (e) { /* 结构差异忽略 */ }
      return null
    },

    /** 【暴露给父组件】播一句 TTS：表情 + 动作指令 + 口型同步，resolve 即播完 */
    playAudio(url, emo, act) {
      return new Promise((resolve) => {
        this._lastInteract = Date.now()
        this._mouthHold = true // 说话期间嘴全程受控，动作曲线不能复活嘴型
        const done = () => {
          this._speaking = false
          this.resetExpression()
          // 声音结束后继续短暂闭嘴钉住，再释放（覆盖动作曲线残留）
          setTimeout(() => { if (!this._destroyed) this._mouthHold = false }, 1200)
          resolve()
        }
        this._speaking = true
        this.applyEmotion(emo)
        if (act) this.performAct(act)
        this.playWithLipsync(url).then(done)
      })
    },

    /** <audio> 播放 + WebAudio RMS 口型；失败自动降级为无口型播放，绝不崩页 */
    playWithLipsync(url) {
      return new Promise((resolve) => {
        let audio
        try { audio = new Audio(url) } catch (e) { return resolve() }
        this._plainAudio = audio
        let settled = false
        const end = () => {
          if (settled) return
          settled = true
          if (this._plainAudio === audio) this._plainAudio = null
          this.teardownLipsync()   // 结束后 _speaking 仍为 true，由 Ticker 兜底继续强制闭嘴
          resolve()
        }
        try { this.setupLipsync(audio) } catch (e) {
          console.warn('[live2d] 口型链路不可用（无口型仍播放）:', e && e.message)
        }
        audio.addEventListener('ended', end)
        audio.addEventListener('error', end)
        audio.play().catch(end)
      })
    },

    /**
     * 口型核心（贴模型更新时序）：把口参数写入挂到 PIXI Ticker 的 LOW 优先级，
     * 保证【写在模型本帧动作更新之后】、不被动作曲线覆盖。
     */
    setupLipsync(audio) {
      const AC = window.AudioContext || window.webkitAudioContext
      if (!AC) throw new Error('无 WebAudio')
      this._audioCtx = this._audioCtx || new AC({ latencyHint: 'interactive' })
      const ctx = this._audioCtx
      if (ctx.state === 'suspended') ctx.resume().catch(() => {})
      const src = ctx.createMediaElementSource(audio)
      const analyser = ctx.createAnalyser()
      analyser.fftSize = 256               // 时域窗 ≈5ms，足够跟嘴
      analyser.smoothingTimeConstant = 0
      src.connect(analyser)
      analyser.connect(ctx.destination)
      this._lipEngine = { analyser, src }
      this._lipData = new Uint8Array(analyser.fftSize)
      this.attachLipLoop()
    },
    /** 挂上唯一的每帧口型回调（LOW 优先级 = 模型更新之后） */
    attachLipLoop() {
      if (this._lipAttached || !PIXI.Ticker || !PIXI.Ticker.shared) return
      this._lipAttached = true
      this._zeroWriteFrames = 0
      const tick = () => this.lipTick()
      this._lipTickFn = tick
      PIXI.Ticker.shared.add(tick, this, PIXI.UPDATE_PRIORITY.LOW)
    },
    detachLipLoop() {
      if (!this._lipAttached) return
      this._lipAttached = false
      if (PIXI.Ticker && PIXI.Ticker.shared && this._lipTickFn) {
        PIXI.Ticker.shared.remove(this._lipTickFn, this)
      }
      this._lipTickFn = null
    },
    lipTick() {
      if (!this._model || this._destroyed) return
      const eng = this._lipEngine
      let open = 0
      if (eng) {
        const data = this._lipData
        eng.analyser.getByteTimeDomainData(data)
        let sum = 0
        for (let i = 0; i < data.length; i++) {
          const v = (data[i] - 128) / 128
          sum += v * v
        }
        const rms = Math.min(1, Math.sqrt(sum / data.length))
        open = rms < 0.03 ? 0 : Math.min(1, (rms - 0.03) * 3.5)
      }
      // 平滑：开口快跟、闭合快收
      open = this._lastOpen === undefined ? open
        : (open > this._lastOpen
            ? this._lastOpen * (1 - MOUTH_SMOOTH_ATTACK) + open * MOUTH_SMOOTH_ATTACK
            : Math.max(0, this._lastOpen - MOUTH_SMOOTH_RELEASE))
      this._lastOpen = open
      this.setMouthParam(open)
      // 声音停止后的兜底：只要还在说话态/嘴部接管中，就继续钉 0；都不满足才计帧卸载
      if (!eng && !this._speaking && !this._mouthHold) {
        this._zeroWriteFrames = (this._zeroWriteFrames || 0) + 1
        if (this._zeroWriteFrames > MOUTH_ZERO_GRACE_FRAMES) this.detachLipLoop()
      } else {
        this._zeroWriteFrames = 0
      }
    },
    setMouthParam(v) {
      try {
        const core = this._model && this._model.internalModel && this._model.internalModel.coreModel
        if (!core) return
        const idx = typeof core.getParameterIndex === 'function'
          ? core.getParameterIndex('ParamMouthOpenY') : -1
        if (idx >= 0 && typeof core.setParameterValueByIndex === 'function') {
          core.setParameterValueByIndex(idx, v)
        }
      } catch (e) { /* 静默 */ }
    },
    teardownLipsync() {
      this._lipEngine = null
      // 不立即写 0：交给仍在运行的 lipTick 用 LOW 优先级钉住嘴型，避免动作曲线趁隙复活
    },

    /** 【暴露给父组件】立刻停止发声与口型 */
    stopSpeaking() {
      this._speaking = false
      this._mouthHold = false
      this.teardownLipsync()
      try { if (this._plainAudio) { this._plainAudio.pause(); this._plainAudio = null } } catch (e) { /* 静默 */ }
      this.resetExpression()
    },

    /** 待机：不做任何说话类动作（该模型全部动作都烤了嘴参数，静音播会"空口嚼台词"） */
    startIdleTimer() {
      this.stopIdleTimer()
    },
    stopIdleTimer() {
      if (this._idleTimer) { clearTimeout(this._idleTimer); this._idleTimer = null }
    },

    /** 面部看门狗：无动作且面部偏离 rest 姿态时拉回 neutral */
    startFaceWatchdog() {
      this.stopFaceWatchdog()
      this._faceOffCount = 0
      this._faceWatchdog = setInterval(() => this.faceWatchdogTick(), 500)
    },
    stopFaceWatchdog() {
      if (this._faceWatchdog) { clearInterval(this._faceWatchdog); this._faceWatchdog = null }
      this._faceOffCount = 0
    },
    faceWatchdogTick() {
      if (this._destroyed || this._speaking) return
      const model = this._model
      if (!model) return
      const mm = model.internalModel && model.internalModel.motionManager
      const core = model.internalModel && model.internalModel.coreModel
      if (!mm || !core) return
      const busy = mm.state && mm.state.currentPriority > 0   // 有动作在播就不干预
      if (busy) { this._faceOffCount = 0; return }
      const rest = this._restParams
      const vals = core._parameterValues
      let off = false
      if (rest && vals && vals.length === rest.length) {
        let maxDev = 0
        for (let i = 0; i < vals.length; i++) {
          const d = Math.abs(vals[i] - rest[i])
          if (d > maxDev) maxDev = d
        }
        off = maxDev > 0.5
      } else {
        const get = (id) => {
          const i = typeof core.getParameterIndex === 'function' ? core.getParameterIndex(id) : -1
          return i >= 0 ? core.getParameterValueByIndex(i) : 0
        }
        const eyeL = get('ParamEyeLOpen'), eyeR = get('ParamEyeROpen')
        const mouthForm = get('ParamMouthForm'), mouthOpen = get('ParamMouthOpenY'), brow = get('ParamBrowLY')
        off = eyeL < 0.85 || eyeR < 0.85 || Math.abs(mouthForm - 0.15) > 0.2 || Math.abs(mouthOpen) > 0.2 || Math.abs(brow) > 0.2
      }
      if (off) {
        this._faceOffCount = (this._faceOffCount || 0) + 1
        if (this._faceOffCount >= 3) {           // 连续≈1.5s 仍偏 = 真残留
          this._faceOffCount = 0
          this.resetExpression()
        }
      } else {
        this._faceOffCount = 0
      }
    },

    /** 点击小人：随机 TapBody 反应（FORCE），并【用同一动作绑定声音】走口型管线 */
    onCanvasClick() {
      this._lastInteract = Date.now()
      if (this.status !== 'ready' || this._pokeBusy || this._speaking) return
      this._pokeBusy = true
      this._mouthHold = true // 点击反应期间嘴受控，声停即闭
      const settings = this._model && this._model.internalModel && this._model.internalModel.settings
      const tapGroup = this.motionGroups.find((g) =>
        settings && settings.motions && Array.isArray(settings.motions[g]))
      const tapDefs = tapGroup ? settings.motions[tapGroup] : []
      const mm = this._model && this._model.internalModel && this._model.internalModel.motionManager
      const release = (delay) => setTimeout(() => { if (!this._destroyed) this._mouthHold = false }, delay)
      try {
        if (tapDefs.length) {
          const idx = Math.floor(Math.random() * tapDefs.length)
          const def = tapDefs[idx]
          if (mm && typeof mm.startMotion === 'function') {
            Promise.resolve(mm.startMotion(tapGroup, idx, 3)).catch(() => this.resetExpression())
          }
          const sound = def && def.Sound
          if (sound) {
            this.playWithLipsync(this.modelBase + sound).finally(() => release(800))
          } else {
            release(1200)
          }
        } else if (mm && typeof mm.startRandomMotion === 'function') {
          Promise.resolve(mm.startRandomMotion(tapGroup, 3)).catch(() => {})
          release(1200)
        } else {
          release(1200)
        }
      } catch (err) {
        console.warn('[live2d] 戳戳反应异常（忽略）:', err && err.message)
        release(1200)
      } finally {
        setTimeout(() => { this._pokeBusy = false }, 1200)
      }
    },

    fail(msg) {
      this.status = 'error'
      this.errorMsg = msg
      this.$emit('status', { status: 'error', message: msg })
    },
    destroyApp() {
      this._initStarted = false
      this.stopFaceWatchdog()
      try {
        const mm = this._model && this._model.internalModel && this._model.internalModel.motionManager
        if (mm && this._onMotionFinish && typeof mm.off === 'function') mm.off('motionFinish', this._onMotionFinish)
      } catch (e) { /* 静默 */ }
      try { if (window.__voiceStage && window.__voiceStage.app === this._app) delete window.__voiceStage } catch (e) { /* 静默 */ }
      try { this.stopSpeaking() } catch (e) { /* 静默 */ }
      try {
        if (this._audioCtx && typeof this._audioCtx.close === 'function') this._audioCtx.close()
        this._audioCtx = null
      } catch (e) { /* 静默 */ }
      try { if (this._app) this._app.destroy(false, { children: true }) } catch (e) { /* 静默 */ }
      this._app = null
      this._model = null
    }
  }
}
</script>

<template>
  <div ref="wrapEl" class="voice-stage" @click="onCanvasClick" title="戳戳我试试~">
    <canvas ref="canvasEl" class="stage-canvas"></canvas>

    <div v-if="status === 'loading'" class="hint">
      <div class="hint-icon">✨</div>
      <p>正在唤醒{{ characterName }}……</p>
    </div>

    <div v-else-if="status === 'error'" class="hint hint-warn">
      <div class="hint-icon">🫧</div>
      <p>{{ errorMsg }}</p>
    </div>

    <div v-if="status === 'ready'" class="badge">👁 视线跟随 · 🖱 戳戳有惊喜</div>
  </div>
</template>

<style scoped>
.voice-stage {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: 0;                 /* 舞台自成一层，避免溢出层盖住相邻面板 */
}
.stage-canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: pointer;
  pointer-events: auto;       /* 戳戳互动需要 canvas 接收点击 */
}
.hint {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  text-align: center;
  padding: 24px;
  color: var(--ink-soft, #888);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(2px);
}
.hint p {
  margin: 0;
  max-width: 420px;
  line-height: 1.8;
  font-size: 15px;
}
.hint-warn { color: #a07b33; }
.hint-icon { font-size: 42px; animation: float 2.6s ease-in-out infinite; }
.badge {
  position: absolute;
  left: 50%;
  bottom: 14px;
  transform: translateX(-50%);
  font-size: 12px;
  color: var(--ink-soft, #888);
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid var(--line, #eee);
  padding: 5px 14px;
  border-radius: 999px;
  box-shadow: var(--shadow, 0 1px 4px rgba(0,0,0,.06));
  pointer-events: none;       /* 装饰角标不拦截点击 */
}
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-7px); }
}

/* 移动端：舞台常被压缩成窄条（见 Chat.vue），收紧提示/角标，
   保证不溢出舞台、不撑破父容器、不产生横向滚动条（Live2D 逻辑不变） */
@media (max-width: 768px) {
  .hint { padding: 12px; gap: 6px; }
  .hint p { font-size: 12px; line-height: 1.5; max-width: 100%; }
  .hint-icon { font-size: 26px; }
  .badge {
    bottom: 6px; max-width: calc(100% - 16px);
    font-size: 11px; padding: 3px 10px; box-sizing: border-box;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }
}
</style>
