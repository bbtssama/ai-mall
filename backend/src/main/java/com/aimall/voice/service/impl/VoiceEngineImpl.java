package com.aimall.voice.service.impl;

import com.aimall.voice.VoiceEngine;
import com.aimall.voice.tts.EdgeTtsEngine;
import com.aimall.voice.tts.PaimonBackendTtsClient;
import com.aimall.voice.tts.TtsUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link VoiceEngine} 的实现 —— 纯派蒙 TTS 引擎。
 *
 * <p>t11 简化：本类不再编排会话/人设/SSE，只负责「派蒙 6k VITS 主路径 + 本地 Edge-TTS 兜底」。
 * AI 对话（人设、多轮、落库）由 ai-mall 原有 {@code com.aimall.ai} 的 ChatServiceImpl 承担
 * （单一会话，textSystemPrompt 已调整为派蒙口吻），此处零耦合。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceEngineImpl implements VoiceEngine {

    /** 派蒙音色 TTS 主路径：调派蒙后端 /api/tts（vits-paimon6k，VITS→ffmpeg→mp3）。 */
    private final PaimonBackendTtsClient paimonBackendTtsClient;

    /** 本地纯 Java Edge-TTS 兜底：仅当派蒙后端不可用时才用（非派蒙音色）。 */
    private final EdgeTtsEngine edgeTtsEngine;

    @Override
    public byte[] synthesize(String text) {
        // 主路径：派蒙 6k VITS（经派蒙后端 /api/tts = vits-paimon6k，VITS→ffmpeg→mp3），满足"派蒙音色"硬需求；
        // 仅当派蒙后端不可用时回退本地纯 Java Edge-TTS（非派蒙音色，纯兜底，避免功能硬失败）。
        try {
            return paimonBackendTtsClient.synthesize(text);
        } catch (TtsUnavailableException e) {
            log.warn("派蒙后端 VITS 不可用，回退本地 Edge-TTS（非派蒙音色）: {}", e.getMessage());
            return edgeTtsEngine.synthesize(text);
        }
    }
}
