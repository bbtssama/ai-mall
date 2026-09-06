package com.aimall.voice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.voice.VoiceEngine;
import com.aimall.voice.dto.TtsRequest;
import com.aimall.voice.tts.TtsUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 语音引擎 HTTP 门户 —— 收敛为<b>单一端点</b>（t11 简化）：
 *
 * <pre>
 *   POST /api/v1/voice/tts   文本 → audio/mpeg 二进制（主路径＝本机内嵌派蒙 6k VITS :9944）
 * </pre>
 *
 * <p><b>★运行时说明（自包含）</b>：派蒙 VITS 由 {@link com.aimall.voice.tts.VitsLifecycle}
 * 在后端启动时自动拉起（脚本 <code>voice-tts/vits_server.py --port 9944</code>），
 * 并轮询 <code>/health</code> 就绪后接管 TTS。<b>无需手动启动任何外部服务</b>，
 * 也不再依赖早期方案中的外部 PaimonLiveWeb5（:18781）。
 * 仅当本机缺 Python / 模型 / 依赖时，才自动回退 {@link com.aimall.voice.tts.EdgeTtsEngine}（非派蒙音色）。</p>
 *
 * <p>原独立的 /api/v1/chat/voice（SSE 会话）与 voice 会话 list/messages 端点已按 t11 移除；
 * AI 对话回归 ai-mall 既有单一会话（com.aimall.ai 的 /api/v1/chat/**）。本端点全部落在
 * /api/** 下自动受 Sa-Token 保护。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VoiceChatController {

    private final VoiceEngine voiceEngine;

    /**
     * 文本转语音。
     *
     * <p>请求 {@code {"text":"..."}} → 响应 audio/mpeg 二进制；失败回 4xx/5xx。<b>主路径为派蒙 6k VITS</b>
     * （本机 :9944，由 {@link com.aimall.voice.tts.VitsLifecycle} 自动拉起，脚本位于 <code>voice-tts/vits_server.py</code>），
     * 不可用时才回退本地纯 Java Edge-TTS（非派蒙音色）。运行时<b>无需手动启动任何外部服务</b>。</p>
     */
    @PostMapping(value = "/voice/tts")
    public ResponseEntity<byte[]> tts(@RequestBody TtsRequest req) {
        // 鉴权说明：本端点在 /api/** 下，已由 SaTokenConfig 的 SaInterceptor 统一要求登录
        // （见 SaTokenConfig#addInterceptors，仅白名单 login/register 放行）。
        // 这里的 checkLogin() 是"显式兜底"：即使将来有人调整了拦截器路径规则，本端点依然受保护。
        StpUtil.checkLogin();
        String text = req.text() == null ? "" : req.text();
        try {
            byte[] audio = voiceEngine.synthesize(text);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .contentLength(audio.length)
                    .header("Cache-Control", "no-store")
                    .body(audio);
        } catch (TtsUnavailableException e) {
            log.warn("TTS 合成失败[{}]: {}", e.getStatus(), e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"code\":" + e.getStatus() + ",\"msg\":\"" + e.getMessage() + "\"}").getBytes());
        }
    }
}
