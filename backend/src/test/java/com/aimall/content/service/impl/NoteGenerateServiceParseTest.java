package com.aimall.content.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 草稿 JSON 解析阶梯的单测（bug 修复回归：模型输出未转义引号/围栏/纯文本）。
 * 通过反射调私有方法——不为此把解析方法提为 public（改动面最小的取舍）。
 */
class NoteGenerateServiceParseTest {

    private NoteGenerateService service;

    @BeforeEach
    void setUp() {
        service = new NoteGenerateService(null, null, null, new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String raw) throws Exception {
        Method m = NoteGenerateService.class.getDeclaredMethod("parseDraft", String.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(service, raw);
    }

    @Test
    void 正常JSON_直接解析() throws Exception {
        Map<String, Object> r = parse("{\"title\":\"标题\",\"content\":\"正文\",\"tags\":[\"a\",\"b\"]}");
        assertEquals("标题", r.get("title"));
        assertEquals("正文", r.get("content"));
        assertEquals(2, ((java.util.List<?>) r.get("tags")).size());
    }

    @Test
    void 围栏与说明文字_剥壳解析() throws Exception {
        String raw = "好的，以下是草稿：\n```json\n{\"title\":\"t\",\"content\":\"c\",\"tags\":[\"x\"]}\n```\n希望有帮助";
        Map<String, Object> r = parse(raw);
        assertEquals("t", r.get("title"));
        assertEquals("c", r.get("content"));
    }

    @Test
    void content内未转义半角引号_修复后解析() throws Exception {
        // 用户报的真实 bug 样例形态：模型在 content 里写了 "小玩具"（未转义）
        String raw = "{\"title\":\"t\",\"content\":\"不是只能充手机的\"小玩具\"。\",\"tags\":[\"x\"]}";
        Map<String, Object> r = parse(raw);
        assertEquals("t", r.get("title"));
        assertTrue(((String) r.get("content")).contains("「小玩具」"));
    }

    @Test
    void 完全非法_降级纯文本() throws Exception {
        Map<String, Object> r = parse("今天天气不错，模型罢工输出了大白话");
        assertEquals("", r.get("title"));
        assertEquals("今天天气不错，模型罢工输出了大白话", r.get("content"));
        assertTrue(((java.util.List<?>) r.get("tags")).isEmpty());
    }

    @Test
    void 空输出_返回空结构() throws Exception {
        Map<String, Object> r = parse(null);
        assertEquals("", r.get("title"));
        assertEquals("", r.get("content"));
    }

    @Test
    void 用户报文原始样例_完整回归() throws Exception {
        // 取自真实故障报文（全角引号版，本应合法）——确保正常样例不被修复逻辑误伤
        String raw = "{\"title\":\"野炊带上它，电饭锅都能户外开煮⚡\",\"content\":\"周末和朋友去郊外野炊。\\n\\n这次我直接背上了澎湃 600W 户外电源。\",\"tags\":[\"野炊\",\"户外电源\"]}";
        Map<String, Object> r = parse(raw);
        assertEquals("野炊带上它，电饭锅都能户外开煮⚡", r.get("title"));
        assertTrue(((String) r.get("content")).contains("郊外野炊"));
    }
}
