package com.aimall.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO（S3 兼容）存储实现。
 *
 * <h2>★ 关键设计：懒初始化 + 不阻断启动</h2>
 * 本类<b>不在构造时连接 MinIO</b>，而是等第一次真正上传时才连接。
 * 这样即使 MinIO 没启动，应用也<b>能正常起来</b>——只有"上传"这一个操作会失败。
 * 这正是本项目一贯的降级思路（对比 voice 模块：缺 VITS 就降级 Edge-TTS，但应用照常启动）。
 *
 * <h2>为什么客户端要双重检查锁</h2>
 * {@code client} 是共享可变状态，多线程并发首次上传时可能创建多个连接。
 * 用 {@code volatile + synchronized 双重检查} 保证只建一次，且发布安全。
 *
 * <h2>访问 URL 的两种策略</h2>
 * <ul>
 *   <li>配了 {@code urlPrefix}（如 CDN 域名）→ 直接拼 URL，桶需设为公开只读。</li>
 *   <li>没配 → 生成<b>预签名 URL</b>（默认 7 天有效）。桶可以完全私有，
 *       靠签名临时授权访问——这是生产上更安全的做法（避免桶被公开拖库）。</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final StorageProperties properties;

    private volatile MinioClient client;
    private volatile boolean bucketReady = false;

    @Override
    public StoredObject upload(MultipartFile file, String directory) {
        ensureBucket();
        StorageProperties.Minio cfg = properties.getMinio();
        String objectKey = newObjectKey(directory, file.getOriginalFilename());

        try (InputStream in = file.getInputStream()) {
            client().putObject(PutObjectArgs.builder()
                    .bucket(cfg.getBucket())
                    .object(objectKey)
                    // partSize = -1：让 SDK 自动分片；size 必须准确，否则大文件会报错
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 上传失败: " + objectKey + " -> " + e.getMessage(), e);
        }

        return new StoredObject(objectKey, resolveUrl(objectKey), file.getContentType(), file.getSize());
    }

    @Override
    public void delete(String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO 删除失败 objectKey={}: {}", objectKey, e.getMessage());
        }
    }

    @Override
    public String type() {
        return "minio";
    }

    // ------------------------------------------------------------------

    /** 懒初始化 MinIO 客户端（双重检查锁） */
    private MinioClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    StorageProperties.Minio cfg = properties.getMinio();
                    if (cfg.getEndpoint() == null || cfg.getEndpoint().isBlank()) {
                        throw new IllegalStateException(
                                "存储类型已选 minio，但未配置 aimall.storage.minio.endpoint");
                    }
                    client = MinioClient.builder()
                            .endpoint(cfg.getEndpoint())
                            .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                            .build();
                }
            }
        }
        return client;
    }

    /** 确保 bucket 存在（只做一次） */
    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            String bucket = properties.getMinio().getBucket();
            try {
                boolean exists = client().bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO bucket 已创建: {}", bucket);
                }
                bucketReady = true;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "MinIO 不可用（bucket 检查/创建失败）: " + e.getMessage(), e);
            }
        }
    }

    /** 优先用配置的前缀，否则退化成预签名 URL */
    private String resolveUrl(String objectKey) {
        StorageProperties.Minio cfg = properties.getMinio();
        if (cfg.getUrlPrefix() != null && !cfg.getUrlPrefix().isBlank()) {
            return LocalStorageService.joinUrl(cfg.getUrlPrefix(), objectKey);
        }
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(cfg.getBucket())
                    .object(objectKey)
                    .expiry(7, TimeUnit.DAYS)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("生成 MinIO 预签名 URL 失败: " + e.getMessage(), e);
        }
    }
}
