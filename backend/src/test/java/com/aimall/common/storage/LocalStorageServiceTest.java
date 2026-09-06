package com.aimall.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地存储实现测试。
 *
 * <p>用 JUnit5 的 {@code @TempDir} 在每个测试后自动清理临时目录，
 * 不会在仓库里留下垃圾文件，也不需要真的启动 MinIO。</p>
 */
class LocalStorageServiceTest {

    private LocalStorageService service(Path root) {
        StorageProperties props = new StorageProperties();
        StorageProperties.Local local = new StorageProperties.Local();
        local.setRoot(root.toString());
        local.setUrlPrefix("/files");
        props.setLocal(local);
        return new LocalStorageService(props);
    }

    private MultipartFile png(String originalFilename) {
        return new MockMultipartFile("file", originalFilename, "image/png", "fake-bytes".getBytes());
    }

    @Test
    @DisplayName("上传落盘成功，且 objectKey 不含原始文件名（防路径穿越 / 覆盖攻击）")
    void upload_shouldPersistAndRandomizeName(@TempDir Path tempDir) {
        LocalStorageService svc = service(tempDir);

        // 故意传入恶意文件名
        StoredObject stored = svc.upload(png("../../etc/passwd.png"), "notes");

        assertNotNull(stored.objectKey());
        assertTrue(stored.objectKey().startsWith("notes/"), "应按业务目录归档");
        assertFalse(stored.objectKey().contains("passwd"), "objectKey 绝不能包含原始文件名");
        assertFalse(stored.objectKey().contains(".."), "objectKey 绝不能包含 .. 路径穿越片段");
        assertTrue(stored.url().startsWith("/files/"));
        assertTrue(Files.exists(tempDir.resolve(stored.objectKey())), "文件应真实落盘");
        assertEquals("image/png", stored.contentType());
    }

    @Test
    @DisplayName("同一目录下两次上传不会互相覆盖（UUID 命名）")
    void upload_twice_shouldNotOverwrite(@TempDir Path tempDir) {
        LocalStorageService svc = service(tempDir);

        StoredObject first = svc.upload(png("a.png"), "notes");
        StoredObject second = svc.upload(png("a.png"), "notes");

        assertFalse(first.objectKey().equals(second.objectKey()));
        assertTrue(Files.exists(tempDir.resolve(first.objectKey())));
        assertTrue(Files.exists(tempDir.resolve(second.objectKey())));
    }

    @Test
    @DisplayName("删除已存在文件后磁盘上应消失")
    void delete_shouldRemoveFile(@TempDir Path tempDir) {
        LocalStorageService svc = service(tempDir);
        StoredObject stored = svc.upload(png("a.png"), "notes");

        svc.delete(stored.objectKey());

        assertFalse(Files.exists(tempDir.resolve(stored.objectKey())));
    }

    @Test
    @DisplayName("删除不存在的文件不应抛异常（幂等，方便重试）")
    void delete_missingFile_shouldNotThrow(@TempDir Path tempDir) {
        LocalStorageService svc = service(tempDir);
        assertDoesNotThrow(() -> svc.delete("notes/20260101/not-exist.png"));
    }

    @Test
    @DisplayName("joinUrl 应容忍前缀末尾多余斜杠与 key 开头斜杠")
    void joinUrl_shouldNormalizeSlashes() {
        assertEquals("/files/notes/a.png", LocalStorageService.joinUrl("/files", "notes/a.png"));
        assertEquals("/files/notes/a.png", LocalStorageService.joinUrl("/files/", "notes/a.png"));
        assertEquals("/files/notes/a.png", LocalStorageService.joinUrl("/files", "/notes/a.png"));
        assertEquals("/files/notes/a.png", LocalStorageService.joinUrl("/files/", "/notes/a.png"));
    }
}
