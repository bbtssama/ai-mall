package com.aimall.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 存储配置（{@code aimall.storage.*}）。
 *
 * <p>设计原则：<b>默认值必须能直接跑起来</b>——默认 {@code type=local}，
 * 不配任何东西也不影响启动。这与 voice 模块"缺资产自动降级 Edge-TTS"是一致的产品思路。</p>
 */
@Data
@ConfigurationProperties(prefix = "aimall.storage")
public class StorageProperties {

    /** 存储类型：local（默认，零依赖） / minio */
    private String type = "local";

    /** 单文件大小上限（默认 10MB） */
    private long maxSizeBytes = 10L * 1024 * 1024;

    /** 允许的 MIME 类型白名单（白名单而非黑名单，防止上传可执行脚本） */
    private List<String> allowedContentTypes =
            List.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private Local local = new Local();

    private Minio minio = new Minio();

    @Data
    public static class Local {
        /** 本地存储根目录 */
        private String root = "./uploads";
        /** 对外访问前缀（需与 StorageConfig 里的静态资源映射一致） */
        private String urlPrefix = "/files";
    }

    @Data
    public static class Minio {
        /** MinIO 服务地址，如 http://localhost:9000 */
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket = "aimall";
        /**
         * 对外访问前缀（如 CDN 域名 / MinIO 公网地址）。
         * 留空则退化为"预签名 URL"（有效期 7 天），适合私有桶。
         */
        private String urlPrefix;
    }
}
