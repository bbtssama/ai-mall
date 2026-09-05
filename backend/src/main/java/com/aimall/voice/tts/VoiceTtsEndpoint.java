package com.aimall.voice.tts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 当前激活的派蒙 TTS 端点（运行时可变）。
 *
 * <p>原始值 = {@code application.yml} 的 {@code aimall.voice.tts.backend-url}
 * （默认本机内嵌 VITS {@code http://127.0.0.1:9944/tts}）。{@link VitsLifecycle} 在自包含
 * VITS 服务 {@code /health} 就绪后会调用 {@link #activate(String)} 把端点"指向本机 VITS"，
 * 使 {@link PaimonBackendTtsClient} 主路径始终打到本机派蒙 VITS（而非外部 PaimonLiveWeb5/18781）。</p>
 */
@Component
public class VoiceTtsEndpoint {

    private volatile String backendUrl;

    public VoiceTtsEndpoint(@Value("${aimall.voice.tts.backend-url:http://127.0.0.1:9944/tts}") String backendUrl) {
        this.backendUrl = backendUrl;
    }

    /** 当前主路径 TTS 端点 URL。 */
    public String url() {
        return backendUrl;
    }

    /** 由生命周期在本地 VITS 就绪后调用：把主路径指向本机 VITS。 */
    public void activate(String url) {
        this.backendUrl = url;
    }
}
