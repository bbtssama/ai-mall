package com.aimall.voice.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 派蒙音色 TTS 客户端（主路径）—— 调<b>本机内嵌派蒙 VITS</b>（t18 自包含）。
 *
 * <p>主路径由 {@link VoiceTtsEndpoint} 决定（默认 {@code http://127.0.0.1:9944/tts}，即内嵌
 * {@code voice-tts/vits_server.py}），POST {@code {"text":...}} 返回 {@code audio/wav}；
 * 用 ffmpeg 转 mp3（满足 {@code audio/mpeg} 契约），ffmpeg 不可用则直接回 wav。启动时由
 * {@link VitsLifecycle} 自动拉起本机 VITS，无需外部 PaimonLiveWeb5/18781。</p>
 *
 * <p>调用失败（本机 VITS 未就绪/不可用）由 {@link VoiceEngineImpl} 捕获并回退本地纯 Java Edge-TTS
 * （非派蒙音色，仅兜底）。</p>
 */
@Component
public class PaimonBackendTtsClient {

    private static final Logger log = LoggerFactory.getLogger(PaimonBackendTtsClient.class);
    /** ffmpeg 转 mp3 的最长等待（秒）。 */
    private static final int FFMPEG_TIMEOUT_SECONDS = 20;

    private final VoiceTtsEndpoint endpoint;
    private final RestClient restClient;
    private final boolean ffmpegEnabled;
    private final String ffmpegPath;

    public PaimonBackendTtsClient(
            VoiceTtsEndpoint endpoint,
            @Value("${aimall.voice.tts.backend-connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${aimall.voice.tts.backend-read-timeout-ms:60000}") int readTimeoutMs,
            @Value("${aimall.voice.tts.vits.ffmpeg-enabled:true}") boolean ffmpegEnabled,
            @Value("${aimall.voice.tts.vits.ffmpeg-path:ffmpeg}") String ffmpegPath) {
        this.endpoint = endpoint;
        this.ffmpegEnabled = ffmpegEnabled;
        this.ffmpegPath = ffmpegPath;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(0, connectTimeoutMs)));
        factory.setReadTimeout(Duration.ofMillis(Math.max(0, readTimeoutMs)));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 调本机内嵌派蒙 VITS /tts 合成派蒙音色。
     *
     * @param text 要朗读的纯文本
     * @return mp3 音频字节（派蒙音色；ffmpeg 不可用时可能为 wav）
     * @throws TtsUnavailableException 本机 VITS 不可用或合成失败
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
            byte[] wav = restClient.post()
                    .uri(endpoint.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", clean))
                    .retrieve()
                    .body(byte[].class);
            if (wav == null || wav.length == 0) {
                throw new IllegalStateException("本机 VITS 返回空音频");
            }
            // VITS 返回 wav；有 ffmpeg 则转 mp3（满足 audio/mpeg），否则直接回 wav
            return ffmpegEnabled ? toMp3OrWav(wav) : wav;
        } catch (Exception e) {
            log.warn("本机派蒙 VITS 不可用({}): {}", endpoint.url(), rootMessage(e));
            throw new TtsUnavailableException(502,
                    "本机派蒙 VITS 不可用(" + endpoint.url() + "): " + rootMessage(e));
        }
    }

    /** 用 ffmpeg 把 wav 转 mp3；任何失败（含未安装 ffmpeg）都回退为原 wav 字节。 */
    private byte[] toMp3OrWav(byte[] wav) {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "aimall-voice-tts");
        Path wavPath = null;
        Path mp3Path = null;
        try {
            Files.createDirectories(dir);
            String id = UUID.randomUUID().toString();
            wavPath = dir.resolve(id + ".wav");
            mp3Path = dir.resolve(id + ".mp3");
            Files.write(wavPath, wav);
            Process p = new ProcessBuilder(ffmpegPath, "-hide_banner", "-loglevel", "error",
                    "-y", "-i", wavPath.toString(),
                    "-codec:a", "libmp3lame", "-b:a", "128k", "-f", "mp3", mp3Path.toString())
                    .redirectErrorStream(true)
                    .start();
            if (!p.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("ffmpeg 转换超时");
            }
            if (p.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg 退出码 " + p.exitValue());
            }
            byte[] mp3 = Files.readAllBytes(mp3Path);
            if (mp3.length == 0) {
                throw new IllegalStateException("ffmpeg 输出空 mp3");
            }
            return mp3;
        } catch (Exception e) {
            log.warn("ffmpeg 转 mp3 失败，直接返回 wav: {}", rootMessage(e));
            return wav;
        } finally {
            deleteQuietly(wavPath);
            deleteQuietly(mp3Path);
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

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignore) {
            // 临时文件清理失败不影响主流程
        }
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
