package com.aimall.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储实现（<b>默认实现，零外部依赖</b>）。
 *
 * <p>适合：本地开发、单机演示、以及"没有 MinIO 环境"的任意场景。
 * 不适用于多实例部署（见 {@link StorageService} 接口注释的三条原因）。</p>
 *
 * <p><b>关键点</b>：写入用 {@code Files.copy(..., REPLACE_EXISTING)}，
 * 目录用 {@code Files.createDirectories}（已存在不报错，等价于 mkdir -p）。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;

    @Override
    public StoredObject upload(MultipartFile file, String directory) {
        String objectKey = newObjectKey(directory, file.getOriginalFilename());
        Path target = Paths.get(properties.getLocal().getRoot(), objectKey);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // 包装为非受检异常，由全局异常处理器统一兜底
            throw new IllegalStateException("本地存储写入失败: " + objectKey, e);
        }

        String url = joinUrl(properties.getLocal().getUrlPrefix(), objectKey);
        return new StoredObject(objectKey, url, file.getContentType(), file.getSize());
    }

    @Override
    public void delete(String objectKey) {
        try {
            boolean deleted = Files.deleteIfExists(Paths.get(properties.getLocal().getRoot(), objectKey));
            if (!deleted) {
                log.debug("本地文件不存在，跳过删除: {}", objectKey);
            }
        } catch (IOException e) {
            // 删除失败不应影响主流程，只记日志（与"上传失败必须报错"不同）
            log.warn("本地文件删除失败 objectKey={}: {}", objectKey, e.getMessage());
        }
    }

    @Override
    public String type() {
        return "local";
    }

    /** 拼接 URL，容忍 prefix 末尾多余或缺失的斜杠 */
    static String joinUrl(String prefix, String objectKey) {
        String p = (prefix == null) ? "" : prefix;
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        String key = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        return p + "/" + key;
    }
}
