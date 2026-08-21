package com.aimall.goods.dto;

import com.aimall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分页查询：分页 + 关键词（名称/副标题） + 分类过滤
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductQuery extends PageQuery {

    /** 关键词：模糊匹配 名称/副标题 */
    private String keyword;

    /** 分类 id（可空） */
    private Long categoryId;
}