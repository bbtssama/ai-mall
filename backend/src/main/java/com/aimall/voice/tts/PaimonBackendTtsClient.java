package com.aimall.voice.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 派蒙音色 TTS 客户端（主路径）—— 把 PaimonLiveWeb5 后端当「派蒙声音黑盒服务」来调用。
 *
 * <p><b>为什么这样设计（契约更正 t8）</b>：用户硬需求是语音用「派蒙 6k VITS」音色，不是通用
 * Edge-TTS。本类调用派蒙后端已封装的 {@code POST /api/tts}（默认 {@code http://127.0.0.1:18781/api/tts}），
 * 其内部配置 {@code paimon.tts.mode=vits}，走派蒙6k VITS（scripts/vits_server.py :9944，
 * wav→ffmpeg→mp3），仅在 VITS 失败时才回退 Edge-TTS —— 因此本客户端拿到的永远是派蒙音色
 * （或派蒙侧自动兜底的音频）。</p>
 *
 * <p><b>环境要求</b>：运行时需先启动派蒙 VITS 服务（{@code scripts/vits_server.py} 默认 :9944）
 * 或其派蒙后端（:18781，本客户端直接依赖它）。地址 / 超时均可通过 {@code aimall.voice.tts.*} 配置。</p>
 *
 * <p>调用失败由 {@link VoiceEngineImpl} 捕获并回退到本地纯 Java Edge-TTS（非派蒙音色，仅作兜底）。</p>
 */
@Component
public class PaimonBackendTtsClient {

    private static final Logger log = LoggerFactory.getLogger(PaimonBackendTtsClient.class);

    private final String backendUrl;
    private final RestClient restClient;

    public PaimonBackendTtsClient(
            @Value("${aimall.voice.tts.backend-url:http://127.0.0.1:18781/api/tts}") String backendUrl,
            @Value("${aimall.voice.tts.backend-connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${aimall.voice.tts.backend-read-timeout-ms:60000}") int readTimeoutMs) {
        this.backendUrl = backendUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(0, connectTimeoutMs)));
        factory.setReadTimeout(Duration.ofMillis(Math.max(0, readTimeoutMs)));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 调派蒙后端 /api/tts 合成派蒙音色。
     *
     * @param text 要朗读的纯文本
     * @return mp3 音频字节（派蒙音色，或派蒙侧自动兜底的音频）
     * @throws TtsUnavailableException 派蒙后端不可用或合成失败
     */
    public byte[] synthesize(String text) {
        // 清洗：剥离 emoji/特殊符号/零宽字符（如 🌬️ 会让派蒙 VITS 合成失败，从而整句回退到本地 Edge-TTS、
        // 变成另一种音色）。只保留文字/数字/标点/空白，保证派蒙音色稳定。
        String clean = stripNonText(text);
        if (clean == null || clean.isBlank()) {
            // 无有效文字（纯表情/符号）：返回空音频，前端静默跳过该句；
            // 不抛异常、不触发 Edge-TTS 兜底（否则又会音色漂移）。
            return new byte[0];
        }
        try {
            byte[] audio = restClient.post()
                    .uri(backendUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("audio/mpeg"))
                    .body(Map.of("text", clean))
                    .retrieve()
                    .body(byte[].class);
            if (audio == null || audio.length == 0) {
                throw new IllegalStateException("派蒙后端返回空音频");
            }
            return audio;
        } catch (Exception e) {
            log.warn("派蒙后端 VITS TTS 不可用({}): {}", backendUrl, rootMessage(e));
            throw new TtsUnavailableException(502,
                    "派蒙后端 VITS TTS 不可用(" + backendUrl + "): " + rootMessage(e));
        }
    }

    /**
     * 剥离非文本字符（emoji、杂项符号、变体选择器、零宽字符、全角空格等），只保留
     * 字母/数字/标点/空白，避免派蒙 VITS 因特殊字符合成失败而回退到 Edge-TTS（音色漂移）。
     */
    private static String stripNonText(String text) {
        if (text == null) {
            return null;
        }
        // \p{L}=字母(含中文) \p{N}=数字 \p{P}=标点 \s=空白；其余（含 emoji 的 \p{So} 等）替换为空格
        String cleaned = text.replaceAll("[^\\p{L}\\p{N}\\p{P}\\s]", " ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }
}
