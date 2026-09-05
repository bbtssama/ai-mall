package com.aimall.voice;

/**
 * 语音引擎 —— 纯派蒙 TTS 引擎（窄接口黑盒）。
 *
 * <p>t11 简化后，voice 子域只保留一件职责：把文本合成派蒙音色音频。多会话/人设切换/SSE
 * sentence 契约已整体移除，AI 对话回归 ai-mall 单一会话（com.aimall.ai 的 /api/v1/chat/**）。</p>
 *
 * <p><b>主路径为派蒙 6k VITS</b>（经派蒙后端 /api/tts = vits-paimon6k），仅在派蒙后端不可用时
 * 回退本地纯 Java Edge-TTS（非派蒙音色，仅兜底）。</p>
 */
public interface VoiceEngine {

    /**
     * 文本转语音。
     *
     * @param text 要朗读的纯文本（一句或一段）
     * @return mp3 音频字节（派蒙音色，或派蒙后端不可用时兜底的 Edge-TTS）
     * @throws com.aimall.voice.tts.TtsUnavailableException 服务不可用/合成失败时抛出
     */
    byte[] synthesize(String text);
}
