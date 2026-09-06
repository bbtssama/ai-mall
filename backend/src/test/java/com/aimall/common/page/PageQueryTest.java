package com.aimall.common.page;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 手写分页参数测试。
 *
 * <p>为什么测这么简单的类：offset 算错是典型的"差一错误"，
 * 且它会导致<b>漏数据</b>（第一页少一条）而不是报错——最难发现的 bug 类型。</p>
 */
class PageQueryTest {

    @Test
    @DisplayName("第一页 offset 应为 0（LIMIT 从 0 开始，不是 1）")
    void offset_firstPage_shouldBeZero() {
        PageQuery q = new PageQuery();
        q.setPage(1);
        q.setPageSize(10);
        assertEquals(0, q.getOffset());
    }

    @Test
    @DisplayName("第三页每页 10 条 → offset 应为 20")
    void offset_thirdPage_shouldSkipTwoPages() {
        PageQuery q = new PageQuery();
        q.setPage(3);
        q.setPageSize(10);
        assertEquals(20, q.getOffset());
    }

    @Test
    @DisplayName("默认值：第 1 页、每页 10 条")
    void defaults_shouldBeFirstPageTenSize() {
        PageQuery q = new PageQuery();
        assertEquals(1, q.getPage());
        assertEquals(10, q.getPageSize());
        assertEquals(0, q.getOffset());
    }
}
