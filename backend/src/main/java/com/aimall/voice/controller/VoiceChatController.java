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
 *   POST /api/v1/voice/tts   文本 → audio/mpeg 二进制（主路径派蒙 6k VITS，经派蒙后端 /api/tts）
 * </pre>
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
     * （经派蒙后端 /api/tts = vits-paimon6k），派蒙后端不可用时才回退本地纯 Java Edge-TTS（非派蒙音色）。
     * 运行时需先启动派蒙 VITS 服务（scripts/vits_server.py :9944）或其派蒙后端（:18781）。</p>
     */
    @PostMapping(value = "/voice/tts")
    public ResponseEntity<byte[]> tts(@RequestBody TtsRequest req) {
        // 与 ai-mall 其余 /api/** 一致：TTS 端点同样要求登录（由服务层 StpUtil 校验，
        // 本项目 SaInterceptor 默认不强制，鉴权靠各端点显式调用 StpUtil）。
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
