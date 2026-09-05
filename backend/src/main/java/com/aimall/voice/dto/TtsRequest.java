package com.aimall.voice.dto;

/** TTS 合成请求（POST /api/v1/voice/tts 的请求体）：{ "text": "..." } → audio/mpeg。 */
public record TtsRequest(String text) {
}
