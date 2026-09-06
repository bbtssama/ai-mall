package com.aimall.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 对象存储抽象 —— 业务代码只依赖接口，不关心底层是本地磁盘还是 MinIO/OSS。
 *
 * <h2>为什么要有这层抽象</h2>
 * 如果业务代码直接写 {@code Files.copy(...)}，将来换 MinIO 就得改所有调用点。
 * 面向接口编程后，换存储只改<b>一个配置</b>（{@code aimall.storage.type=minio}），业务零改动。
 * 这就是"可插拔"——本项目 voice 模块的 TTS（VITS 降级 Edge-TTS）用的也是同一套思路。
 *
 * <h2>为什么不用"本地存储"凑合</h2>
 * 本地磁盘存图片有三个致命问题：
 * <ol>
 *   <li><b>多实例部署必炸</b>：用户上传打到实例 A，下次读请求打到实例 B → 404。</li>
 *   <li><b>容器化即丢失</b>：Docker 容器重启，没有挂载卷的话文件全没。</li>
 *   <li><b>无法做 CDN 与权限控制</b>。</li>
 * </ol>
 * 所以本项目<b>默认仍是本地实现</b>（保证零依赖能跑），但抽象层已就位，
 * V2 笔记图片直接复用，届时只需配置 MinIO 即可切换到生产级存储。
 */
public interface StorageService {

    /**
     * 上传文件。
     *
     * @param file      上传的文件
     * @param directory 业务目录（如 notes / avatars / products），便于分类管理与生命周期清理
     */
    StoredObject upload(MultipartFile file, String directory);

    /** 按 objectKey 删除 */
    void delete(String objectKey);

    /** 实现类型标识，用于日志排查（local / minio） */
    String type();

    // ------------------------------------------------------------------
    // 默认方法：对象键生成策略（所有实现共用，避免重复代码）
    // ------------------------------------------------------------------

    /**
     * 生成对象键：<b>不使用用户上传的原始文件名</b>，而是 UUID + 日期分目录。
     *
     * <p><b>安全考量（面试常问）</b>：
     * <ul>
     *   <li><b>路径穿越</b>：原始文件名可能是 {@code ../../etc/passwd}，直接拼接会写到系统目录。
     *       用 UUID 重命名可彻底杜绝。</li>
     *   <li><b>覆盖攻击</b>：两个用户都传 {@code avatar.jpg}，后者覆盖前者。UUID 保证唯一。</li>
     *   <li><b>文件名乱码/XSS</b>：中文或含特殊字符的文件名在 URL 里需要额外编码处理。</li>
     * </ul>
     */
    default String newObjectKey(String directory, String originalFilename) {
        String dir = (directory == null || directory.isBlank()) ? "common" : directory;
        String datePath = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String name = UUID.randomUUID().toString().replace("-", "") + extensionOf(originalFilename);
        return dir + "/" + datePath + "/" + name;
    }

    /** 取原始文件扩展名（小写），取不到则兜底 .bin */
    default String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return ".bin";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return ".bin";
        }
        return originalFilename.substring(dot).toLowerCase();
    }
}
