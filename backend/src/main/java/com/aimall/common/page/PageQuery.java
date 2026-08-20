package com.aimall.common.page;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询参数：V1 手写 LIMIT 分页（不引入分页插件，风格与早期项目一致）
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "页码从 1 开始")
    private long page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 100, message = "每页最多 100 条")
    private long pageSize = 10;

    /** SQL LIMIT 起始偏移 */
    public long getOffset() {
        return (page - 1) * pageSize;
    }
}