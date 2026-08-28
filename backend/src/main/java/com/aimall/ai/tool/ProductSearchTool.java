package com.aimall.ai.tool;

import com.aimall.common.page.PageResult;
import com.aimall.goods.dto.ProductQuery;
import com.aimall.goods.dto.ProductVO;
import com.aimall.goods.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品搜索工具 —— 本项目函数调用（Function Calling）的主角，AI 的"查库之手"。
 *
 * <h2>它解决什么问题？（教程第 4 章主线）</h2>
 * 大模型不知道你店里的商品（第 0 章心智模型）。早期版本把商品全量塞进 system prompt，
 * 有四个致命伤：token 爆炸 / 数据过期 / 模型"翻找"易看漏 / 无法分页聚合——已废弃。
 * 现在：把本方法注册为"工具"，模型判断用户在问商品时<b>自己决定</b>调用它、<b>自己填参数</b>，
 * 拿到真实查询结果后再组织回答。数据永远实时来自 MySQL，不编造。
 *
 * <h2>幕后发生了什么（面试必考）</h2>
 * <pre>
 *   第 1 轮请求：请求带 tools=[本方法的 JSON Schema（Spring AI 从 @Tool/@ToolParam 生成）]
 *              → 模型响应："我要调 searchProduct({"keyword":"降噪"})"  ← 叫 tool_call
 *   （Spring AI 在你的 JVM 里执行本方法 → 真实查库 → 拿到商品 JSON）
 *   第 2 轮请求：把工具结果作为 role=tool 的消息发回 → 模型生成最终回答
 * </pre>
 * 模型只"点菜"，真正执行查库的是框架调用的本方法——所以这里的参数校验要按
 * "处理不可信外部输入"的标准写（见方法体内逐参数防御）。
 *
 * <h2>两个真实踩坑（教程第 4.6 节、第 9 章案例二/三）</h2>
 * <ul>
 *   <li>description 只写"搜索商品"两个字时，模型该调不调、开始自己编商品——
 *       description 必须写清"返回什么 + 什么时候该调"；</li>
 *   <li>参数不写 required=false 时默认必填，模型硬填 categoryId=0 交差 → 查不到货——
 *       全部参数设为可空 + 方法体内逐个兜底后稳定。</li>
 * </ul>
 *
 * <h2>设计亮点</h2>
 * 复用 ProductService.pageOnSale 而非另写 SQL：<b>AI 检索与前端搜索页永远看到同一份数据</b>
 * （同样的在售过滤、同样的排序）——AI 能力复用业务 Service，而不是绕过它。
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 4 章（函数调用，全书最重章）。</p>
 */
@Component
@RequiredArgsConstructor
public class ProductSearchTool {

    /** 商品业务 Service：AI 的查询复用它，与前端搜索行为保持一致 */
    private final ProductService productService;

    /** Jackson 序列化器：把结果 Map 转成 JSON 字符串返回给模型（模型只读文本） */
    private final ObjectMapper objectMapper;

    /**
     * 搜索本店在售商品，返回分页商品列表（名称/副标题/起售价）。
     * 当用户询问商品、价格、找某类商品/关键词时调用。
     *
     * <p>下面 @Tool 的 description 就是发给模型的"工具说明书"：
     * 前半句说清返回什么，后半句说清什么场景该调（并加"别自己编造"压制幻觉）。
     * 模型看不到 Java 代码，全靠这段文字决定调不调、怎么传参。</p>
     */
    @Tool(description = "搜索本店在售商品：可按关键词、分类过滤，返回商品列表(名称/副标题/起售价)与总数。"
            + "当用户问商品、价格、或要找某类商品时调用，别自己编造商品。")
    public String searchProduct(
            // 四个参数全部 required=false（可空）。血泪教训：默认必填时，模型在用户
            // 没提分类的情况下硬填 categoryId=0 交差 → 查询为空 → AI 回答"没有找到"。
            @ToolParam(required = false, description = "关键词，如'耳机'") String keyword,
            @ToolParam(required = false, description = "分类id，可不传") Long categoryId,
            @ToolParam(required = false, description = "页码，默认1") Integer page,
            @ToolParam(required = false, description = "每页条数，默认10") Integer pageSize) {
        // ---- 参数防御：模型是"不可信的外部调用者"，每个参数都可能传 null/0/负数 ----
        // 组装查询条件（复用 goods 模块的查询对象，与前端搜索同一条 SQL 路径）
        ProductQuery query = new ProductQuery();
        // 关键词：空白串按"没传"处理，并去掉首尾空格
        query.setKeyword(keyword == null || keyword.isBlank() ? null : keyword.trim());
        // 防御：模型可能传 0 表示"无分类"，视为 null
        query.setCategoryId(categoryId != null && categoryId > 0 ? categoryId : null);
        // 页码：非法值兜底为第 1 页
        query.setPage(page == null || page < 1 ? 1 : page);
        // 页大小：非法值兜底为 10 条
        query.setPageSize(pageSize == null || pageSize < 1 ? 10 : pageSize);
        // 真实查库：这一行执行时，数据是最新的（对比"塞进 prompt 的快照"永不更新）
        PageResult<ProductVO> result = productService.pageOnSale(query);

        // ---- 精简返回：只挑"回答问题用得上"的 4 个字段 ----
        // 商品详情/库存/图文等大字段不给——工具返回值也是要计入 token 的，能省则省
        List<Map<String, Object>> items = result.getRecords().stream().map(vo -> {
            // LinkedHashMap 保证输出字段顺序稳定（name 在前），模型阅读与日志排查都友好
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", vo.getSpuName());
            m.put("subTitle", vo.getSubTitle());
            m.put("minPrice", vo.getMinPrice());
            m.put("id", vo.getId());
            return m;
        }).toList();

        // ---- 组装并序列化为 JSON 字符串（方法的返回值会原样成为 role=tool 消息的内容）----
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", result.getTotal());
        resp.put("items", items);
        try {
            return objectMapper.writeValueAsString(resp);
        } catch (Exception e) {
            // 序列化失败的兜底：宁可返回空列表，也绝不让异常冒出去
            // （工具抛异常 = 这次对话直接失败，见教程 4.3 设计点 1）
            return "{\"total\":" + result.getTotal() + ",\"items\":[]}";
        }
    }
}
