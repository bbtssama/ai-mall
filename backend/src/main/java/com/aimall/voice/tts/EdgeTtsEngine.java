package com.aimall.voice.tts;

import io.github.whitemagic2014.tts.TTS;
import io.github.whitemagic2014.tts.TTSVoice;
import io.github.whitemagic2014.tts.bean.Voice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * 本地纯 Java Edge-TTS 合成引擎<b>（兜底 / fallback）</b>。
 *
 * <p>主路径是「派蒙 6k VITS」：{@link PaimonBackendTtsClient} 调派蒙后端 /api/tts
 * （vits-paimon6k）。本类仅在派蒙后端不可用时兜底（非派蒙音色），避免功能硬失败。</p>
 *
 * <p>封装 {@code io.github.whitemagic2014:tts-edge-java:1.3.3}：websocket 直连微软免费接口，
 * 库内置 Sec-MS-GEC DRM 令牌，无需 key。音色由 {@code aimall.voice.tts.voice} 配置
 * （默认 zh-CN-XiaoyiNeural 活泼声线）。本引擎同时承担串行化 + 瞬态重试——edge/微软接口在
 * 并发合成下会整批失败（限流），因此：</p>
 * <ol>
 *   <li><b>串行化</b>：Semaphore 把同时合成数限制为 {@code aimall.voice.tts.max-concurrency}
 *       （默认 1，可放宽到 2），并发请求在此排队；</li>
 *   <li><b>瞬态重试</b>：单次失败按 {@code aimall.voice.tts.retry-attempts}（默认 2）次重试，
 *       退避 {@code retry-backoff-ms}×尝试轮次（默认 500ms 起）。</li>
 * </ol>
 *
 * <p>⚠️ 官方 issue #20/#22：不要用 transToAudioStream() 直接透传——它返回含多个 mp3 文件头的
 * 分段流，直接输出有杂音；一律用 trans() 落盘 → 读字节 → 删临时文件。</p>
 */
@Component
public class EdgeTtsEngine {

    private static final Logger log = LoggerFactory.getLogger(EdgeTtsEngine.class);

    private final String voiceName;
    private final Semaphore synthesisSlots;
    private final int maxAttempts;
    private final long retryBackoffMs;

    public EdgeTtsEngine(
            @Value("${aimall.voice.tts.voice:zh-CN-XiaoyiNeural}") String voiceName,
            @Value("${aimall.voice.tts.max-concurrency:1}") int maxConcurrency,
            @Value("${aimall.voice.tts.retry-attempts:2}") int retryAttempts,
            @Value("${aimall.voice.tts.retry-backoff-ms:500}") long retryBackoffMs) {
        this.voiceName = voiceName;
        this.synthesisSlots = new Semaphore(Math.max(1, maxConcurrency));
        this.maxAttempts = 1 + Math.max(0, retryAttempts);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
    }

    /** 合成一段语音为 mp3 字节；服务不可用时抛 {@link TtsUnavailableException}。 */
    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) {
            throw new TtsUnavailableException(400, "text 不能为空");
        }
        return synthesizeWithRetry(text.strip());
    }

    /** 串行化 + 瞬态重试的主合成路径。音色解析是确定性操作，失败不重试。 */
    private byte[] synthesizeWithRetry(String text) {
        Path storageDir = Path.of(System.getProperty("java.io.tmpdir"), "aimall-voice-tts");
        Voice voice = resolveVoice();

        synthesisSlots.acquireUninterruptibly();
        try {
            for (int attempt = 1; ; attempt++) {
                // 【库名坑】formatMp3() 会自动追加 ".mp3"，fileName 不能再带扩展名；
                // trans() 返回的是"文件名"而非全路径，读取须 storageDir.resolve(path)。
                String name = UUID.randomUUID().toString();
                Path target = storageDir.resolve(name + ".mp3");
                try {
                    String path = new TTS(voice, text)
                            .isRateLimited(true)   // 规避部分区域限流（官方 demo 默认参数）
                            .formatMp3()
                            .storage(storageDir.toString())
                            .fileName(name)
                            .trans();
                    if (path == null) {
                        throw new IllegalStateException("未产出音频文件（多为网络受限或限流）");
                    }
                    byte[] audio = Files.readAllBytes(storageDir.resolve(path));
                    if (audio.length == 0) {
                        throw new IllegalStateException("返回空音频");
                    }
                    if (attempt > 1) {
                        log.info("Edge-TTS 第{}次尝试成功（text={}...）", attempt, abbreviate(text));
                    }
                    return audio;
                } catch (Exception e) {
                    if (attempt >= maxAttempts) {
                        throw new TtsUnavailableException(502,
                                "Edge-TTS 合成失败（共" + attempt + "次尝试）: " + rootMessage(e));
                    }
                    long backoff = retryBackoffMs * attempt;
                    log.warn("Edge-TTS 第{}次合成失败，{}ms 后重试: {}",
                            attempt, backoff, rootMessage(e));
                    sleepQuietly(backoff);
                } finally {
                    deleteQuietly(target);
                }
            }
        } finally {
            synthesisSlots.release();
        }
    }

    private Voice resolveVoice() {
        return TTSVoice.provides().stream()
                .filter(v -> voiceName.equals(v.getShortName()))
                .findFirst()
                .orElseThrow(() -> new TtsUnavailableException(502, "找不到音色 " + voiceName));
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignore) {
            // 临时文件清理失败不影响主流程
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TtsUnavailableException(502, "TTS 重试等待被中断");
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 12 ? s : s.substring(0, 12) + "...";
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
