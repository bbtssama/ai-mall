package com.aimall.voice.tts;

/** TTS 服务不可用/合成失败时抛出的异常（携带 HTTP 语义的状态码）。 */
public class TtsUnavailableException extends RuntimeException {

    private final int status;

    public TtsUnavailableException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
