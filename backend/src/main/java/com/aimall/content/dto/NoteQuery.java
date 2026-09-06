package com.aimall.content.dto;

import com.aimall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 笔记列表查询参数 —— <b>游标分页</b>（而非 OFFSET 深分页）。
 *
 * <h2>为什么 Feed 流不能用 LIMIT offset</h2>
 * {@code LIMIT 100000, 20} 的代价：MySQL 必须先<b>扫描并丢弃</b>前 10 万行，
 * 才能返回后面 20 行。页数越深越慢，这是典型的慢 SQL。
 *
 * <p>游标分页：{@code WHERE id < 上一页最后一条的 id ORDER BY id DESC LIMIT 20}
 * —— 靠主键索引直接定位，无论翻到第几页都是一样的开销。</p>
 *
 * <h2>代价（面试要能说出来）</h2>
 * 游标分页<b>不支持"直接跳到第 N 页"</b>，只能"下一页/上一页"连续翻。
 * 这对信息流（Feed）完全够用（用户本来就是一直往下滑），
 * 但后台管理需要跳页时就得用 offset——所以两种方案是<b>按场景选</b>，不是谁替代谁。
 *
 * <p>{@code keyword} 走 MySQL FULLTEXT(ngram)，见 NoteMapper.xml。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class NoteQuery extends PageQuery {

    /**
     * 游标：上一页最后一条笔记的 id。为 null 表示第一页。
     *
     * <p>★ 注意：游标必须和排序键配套，否则会漏数据/重复数据。</p>
     * <ul>
     *   <li>按最新排（id DESC）：游标只需 {@code id < cursorId}；</li>
     *   <li>按热度排（hot_score DESC, id DESC）：游标是<b>二元组</b>，
     *       SQL 写作 {@code hot_score < cursorHot OR (hot_score = cursorHot AND id < cursorId)}。
     *       只用 id 做游标会漏掉"热度比上页末条更高、但 id 更小"的笔记。</li>
     * </ul>
     */
    private Long cursorId;

    /** 热度排序时的游标第二分量：上一页最后一条的 hot_score */
    private Integer cursorHot;

    /** 关键词搜索（标题+正文，ngram 全文索引） */
    private String keyword;

    /** 标签筛选，如"通勤穿搭" */
    private String tag;

    /**
     * 排序：hot（热度，默认） / newest（最新）
     */
    private String orderBy = "hot";

    /** 只看某作者的笔记（个人主页用） */
    private Long userId;

    /**
     * 状态过滤：列表页传 PUBLISHED（只看已发布），个人主页不传（看全部）。
     * 由 Service 按场景设置，不放开给前端，避免前端随意查询未审核内容。
     */
    private String status;
}
