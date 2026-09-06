package com.aimall.common.storage;

/**
 * 上传后的对象描述（不可变）。
 *
 * @param objectKey   存储对象键（删除时用），如 {@code notes/20260906/abc123.jpg}
 * @param url         可直接访问的 URL（前端存这个）
 * @param contentType MIME 类型
 * @param size        字节大小
 */
public record StoredObject(String objectKey, String url, String contentType, long size) {
}
