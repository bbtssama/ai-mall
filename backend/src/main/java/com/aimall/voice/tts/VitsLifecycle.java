package com.aimall.voice.tts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内嵌派蒙 VITS 服务生命周期（t18：ai-mall 自包含派蒙音色）。
 *
 * <p>在 ai-mall 后端启动时：若检测到 Python 命令与内嵌模型（{@code voice-tts/paimon6k_390k.pth}）
 * 均存在，则用 {@code ProcessBuilder} spawn {@code python voice-tts/vits_server.py --port <port>}，
 * 并在后台轮询 {@code /health}；就绪后把 {@link VoiceTtsEndpoint} 指向本机 VITS
 * （{@code http://127.0.0.1:<port>/tts}），使 {@link PaimonBackendTtsClient} 主路径打到本机派蒙音色
 * （不再依赖外部 PaimonLiveWeb5/18781）。应用关闭时优雅停掉该进程。</p>
 *
 * <p><b>降级</b>：任何一步失败（无 Python / 无模型 / spawn 异常 / /health 超时）都<b>不阻断</b>应用启动，
 * ${@link com.aimall.voice.service.impl.VoiceEngineImpl} 会让 TTS 自动回退本地 Edge-TTS（非派蒙音色，仅兜底）。</p>
 */
@Slf4j
@Component
public class VitsLifecycle implements SmartLifecycle {

    private final VoiceTtsEndpoint endpoint;
    private final int port;
    private final String python;
    private final String scriptDir;
    private final long healthTimeoutMs;
    private final long healthIntervalMs;
    private final RestClient restClient;
    private final String logFile;

    private volatile Process process;
    private volatile ScheduledExecutorService healthScheduler;
    private volatile boolean running;

    public VitsLifecycle(VoiceTtsEndpoint endpoint,
                         @Value("${aimall.voice.tts.vits.port:9944}") int port,
                         @Value("${aimall.voice.tts.vits.python:python}") String python,
                         @Value("${aimall.voice.tts.vits.script-dir:voice-tts}") String scriptDir,
                         @Value("${aimall.voice.tts.vits.health-timeout-ms:90000}") long healthTimeoutMs,
                         @Value("${aimall.voice.tts.vits.health-interval-ms:1500}") long healthIntervalMs,
                         @Value("${aimall.voice.tts.vits.log:vits-server.log}") String logFile) {
        this.endpoint = endpoint;
        this.port = port;
        this.python = python;
        this.scriptDir = scriptDir;
        this.healthTimeoutMs = Math.max(1000, healthTimeoutMs);
        this.healthIntervalMs = Math.max(200, healthIntervalMs);
        this.logFile = logFile;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(2));
        f.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(f).build();
    }

    @Override
    public void start() {
        try {
            Path dir = resolveScriptDir();
            Path script = dir.resolve("vits_server.py");
            Path model = dir.resolve("paimon6k_390k.pth");
            if (!Files.exists(script) || !Files.exists(model)) {
                log.warn("未找到内嵌派蒙 VITS（{} / {}），跳过自拉起；TTS 将回退本地 Edge-TTS",
                        script, model);
                return;   // 降级：不 spawn，不阻断启动
            }
            ProcessBuilder pb = new ProcessBuilder(python, script.toString(), "--port", String.valueOf(port));
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            if (logFile != null && !logFile.isBlank()) {
                // log 为相对路径时，落到内嵌 voice-tts 目录下（避免在 backend/ 下产生空 voice-tts 干扰解析）
                Path logPath = Path.of(logFile);
                if (!logPath.isAbsolute()) {
                    logPath = dir.resolve(logPath);
                }
                logPath = logPath.toAbsolutePath();
                if (logPath.getParent() != null) {
                    Files.createDirectories(logPath.getParent());
                }
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            }
            process = pb.start();
            log.info("已拉起内嵌派蒙 VITS ({} {} --port {}), 后台等待 /health 就绪", python, script, port);
            pollHealthAsync();
        } catch (Exception e) {
            log.warn("拉起内嵌派蒙 VITS 失败（TTS 将回退 Edge-TTS）: {}", e.getMessage());
        } finally {
            running = true;
        }
    }

    /** 后台轮询 /health，就绪后激活端点；超时则放弃（不阻断）。 */
    private void pollHealthAsync() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vits-health-poll");
            t.setDaemon(true);
            return t;
        });
        this.healthScheduler = scheduler;
        long[] startedAt = {System.currentTimeMillis()};
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                // ★ 进程死亡快速失败：spawn 出的 Python 缺依赖（ModuleNotFoundError 等）会秒退，
                //   若只轮询 /health 要干等 health-timeout（90s）才告警——这里立刻发现并指路日志文件。
                if (process != null && !process.isAlive()) {
                    log.error("内嵌派蒙 VITS 进程已退出（常见原因：Python 环境缺依赖/版本不符），"
                            + "详情见 voice-tts 目录下的 {}；TTS 回退 Edge-TTS", logFile);
                    scheduler.shutdown();
                    return;
                }
                if (checkHealth()) {
                    endpoint.activate("http://127.0.0.1:" + port + "/tts");
                    log.info("内嵌派蒙 VITS 就绪: http://127.0.0.1:{}/ (health=ok)，TTS 主路径已指向本机 VITS", port);
                    scheduler.shutdown();
                    return;
                }
                if (System.currentTimeMillis() - startedAt[0] >= healthTimeoutMs) {
                    log.warn("内嵌派蒙 VITS /health 超过 {}ms 未就绪，TTS 将回退 Edge-TTS", healthTimeoutMs);
                    scheduler.shutdown();
                }
            } catch (Exception e) {
                if (System.currentTimeMillis() - startedAt[0] >= healthTimeoutMs) {
                    log.warn("内嵌派蒙 VITS 轮询异常且超时，TTS 将回退 Edge-TTS: {}", e.getMessage());
                    scheduler.shutdown();
                }
            }
        }, 0, healthIntervalMs, TimeUnit.MILLISECONDS);
    }

    /** /health 是否 ok（兼容返回 {"ok":true,...}）。 */
    private boolean checkHealth() {
        ResponseEntity<String> resp = restClient.get()
                .uri("http://127.0.0.1:" + port + "/health")
                .retrieve()
                .toEntity(String.class);
        return resp.getStatusCode().is2xxSuccessful()
                && resp.getBody() != null && resp.getBody().contains("\"ok\":true");
    }

    @Override
    public void stop() {
        running = false;
        if (healthScheduler != null) {
            healthScheduler.shutdownNow();
        }
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            log.info("已优雅停止内嵌派蒙 VITS");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 尽早启动（在 web 服务接收请求前尝试拉起 VITS）
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /** 解析内嵌 voice-tts 目录：优先 cwd 下、其次 cwd 上一级，且必须<b>包含 vits_server.py</b>才算数
     *（兼容从 ai-mall/backend 或 ai-mall 根目录启动，并避免被空目录/日志目录误导）。 */
    private Path resolveScriptDir() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path d = Path.of(scriptDir);
        Path cand1 = cwd.resolve(d);
        if (Files.isRegularFile(cand1.resolve("vits_server.py"))) {
            return cand1;
        }
        return cwd.resolve("..").resolve(d).normalize();
    }
}
