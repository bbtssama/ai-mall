package com.aimall.common.storage;

import com.aimall.common.api.R;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传/删除端点。
 *
 * <p>V2 的笔记图片、头像等直接复用本端点，业务方只拿到 URL 存库即可。</p>
 *
 * <p><b>鉴权</b>：路径在 {@code /api/**} 下，已由 {@code SaTokenConfig} 的拦截器统一要求登录，
 * 此处无需重复调用 {@code StpUtil.checkLogin()}。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileRestController {

    private final StorageService storageService;
    private final StorageProperties properties;

    /**
     * 上传文件。
     *
     * @param file 文件（表单字段名 file）
     * @param dir  业务目录：notes / avatars / products ...
     */
    @PostMapping("/upload")
    public R<StoredObject> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "dir", defaultValue = "common") String dir) {
        validate(file);
        StoredObject stored = storageService.upload(file, dir);
        log.info("文件上传成功 type={} key={} size={}B",
                storageService.type(), stored.objectKey(), stored.size());
        return R.ok(stored);
    }

    @DeleteMapping
    public R<Void> delete(@RequestParam("key") String objectKey) {
        storageService.delete(objectKey);
        return R.ok();
    }

    /**
     * 上传校验——<b>安全边界在服务端，不能只靠前端</b>。
     *
     * <ol>
     *   <li><b>大小</b>：不限制的话，一个几十 GB 的文件就能把磁盘打满（DoS）。</li>
     *   <li><b>类型白名单</b>：用白名单而非黑名单。黑名单永远列不全，
     *       漏一个 {@code .jsp/.html} 就可能造成<b>存储型 XSS</b>（用户上传恶意页面，
     *       通过你的域名访问，就能窃取同域 Cookie）。</li>
     *   <li><b>文件名</b>：不信任原始文件名，由 {@link StorageService#newObjectKey} 生成 UUID，防路径穿越与覆盖。</li>
     * </ol>
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "文件超过大小限制（最大 " + properties.getMaxSizeBytes() / 1024 / 1024 + "MB）");
        }
        String contentType = file.getContentType();
        if (contentType == null || !properties.getAllowedContentTypes().contains(contentType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的文件类型：" + contentType);
        }
    }
}
