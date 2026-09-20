package com.aimall.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Sa-Token 鉴权拦截器：{@code /api/**} 默认需要登录，白名单与「匿名只读」路径除外。
 *
 * <h2>★ 2026-09-20 修复：这个拦截器此前形同虚设</h2>
 * 原实现是 {@code new SaInterceptor()} <b>无参构造</b> + {@code excludePathPatterns} 白名单。
 * 但无参构造的默认认证体<b>是空的</b>（只做 {@code @SaCheck*} 注解校验，
 * 而本项目一个这样的注解都没有）—— 也就是说：
 * <pre>
 *   registry.addInterceptor(new SaInterceptor()).addPathPatterns("/api/**")...
 *   看起来拦了 /api/**，实际任何请求都能直接穿过去。
 * </pre>
 * 真实的"保护"是<b>意外形成的</b>：写接口因为在 Service 层调用了
 * {@code StpUtil.getLoginIdAsLong()} 而抛 {@code NotLoginException}；
 * 不碰登录态的读接口（如商品列表）则<b>完全裸奔</b>。
 * 这既不是设计，也不一致 —— 所以本次把它改成<b>真正生效</b>的写法。
 *
 * <h2>规则</h2>
 * <ol>
 *   <li><b>完全公开</b>：登录/注册、支付回调、服务间内部接口、错误页。
 *       它们的"身份"不由用户 token 保证（回调靠验签、内部接口靠 HMAC）。</li>
 *   <li><b>匿名只读</b>：商品与种草社区的 <b>GET</b> 请求。
 *       浏览是"橱窗"，不该强制先注册；但同一前缀下的写操作（发笔记/点赞/收藏）
 *       仍然是 POST/DELETE，照常要求登录。</li>
 *   <li><b>其余全部要求登录</b>：购物车 / 订单 / 支付 / 地址 / AI 问答 / 文件上传 / 建索引。</li>
 * </ol>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 完全公开：无需用户登录态。
     * 这里的路径"身份"由其他机制保证，不能简单按"要不要登录"理解。
     */
    private static final List<String> ALWAYS_PUBLIC = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            // V3：支付回调——第三方（支付宝服务器）不可能持有我们用户的 token。
            // 回调接口的"身份"由【验签】保证，而不是 token：
            //   业务接口靠"你是谁"（认证），回调接口靠"消息是谁发的、有没有被改"（完整性）。
            // 若这里不放行，回调会被 401 拦掉，订单永远无法变为已支付。
            "/api/v1/payments/callback/**",
            // V4：服务间内部接口（ai-service 回调查商品）。
            // 与支付回调同理——调用方是另一个服务，没有用户 token；
            // 身份由【内部 HMAC 签名】保证（见 InternalSignature / InternalAuthFilter）。
            // ⚠️ 放行 ≠ 裸奔：这些端点仍需校验 X-Internal-Sign，
            //    否则等于把内部数据接口开放给任何人。
            "/internal/**",
            "/error"
    );

    /**
     * 匿名只读：<b>列表 / 榜单</b>类的精确路径（GET 且路径完全等于其中之一）。
     *
     * <p>用精确匹配而不是前缀匹配，是为了不接受任何额外路径段与变体。</p>
     */
    private static final List<String> PUBLIC_READ_EXACT = List.of(
            "/api/v1/products",
            "/api/v1/notes",
            "/api/v1/notes/hot"
    );

    /**
     * 匿名只读：<b>详情</b>类路径的前缀 —— 前缀之后必须紧跟<b>纯数字 id</b>。
     *
     * <p>只放行 GET 是刻意的：同一前缀下 {@code POST /api/v1/notes}（发布）、
     * {@code POST /api/v1/notes/{id}/like}（点赞）等写操作仍必须登录。</p>
     *
     * <p>要求"前缀 + 纯数字"而不只是 startsWith，有两个作用：
     * ① 排除 {@code /api/v1/notes/create} 这类非详情路径；
     * ② 顺带堵住路径穿越 —— 即便容器未做归一化，{@code /api/v1/notes/../orders}
     *    也无法通过数字校验，不会被误判成"匿名可读"。</p>
     */
    private static final List<String> PUBLIC_READ_DETAIL_PREFIXES = List.of(
            "/api/v1/products/",
            "/api/v1/notes/"
    );

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter
                        .match("/api/**")
                        // ① 完全公开
                        .notMatch(ALWAYS_PUBLIC)
                        // ② 匿名只读（按 HTTP 方法判定，见 isAnonymousReadable）
                        .notMatch(r -> isAnonymousReadable())
                        // ③ 其余一律校验登录
                        .check(r -> StpUtil.checkLogin())))
                .addPathPatterns("/api/**");
    }

    /**
     * 判断当前请求是否属于「匿名可读」。
     *
     * <p>必须同时满足：请求方法是 <b>GET</b>，且路径命中
     * {@link #PUBLIC_READ_EXACT}（列表/榜单，精确相等）
     * 或 {@link #PUBLIC_READ_DETAIL_PREFIXES}（详情，前缀 + 纯数字 id）。</p>
     *
     * <p>为什么不用 {@code excludePathPatterns}：Spring MVC 的路径排除<b>不支持按 HTTP 方法区分</b>，
     * 而 {@code /api/v1/notes} 这个前缀下既有公开的 GET（Feed 流）又有必须登录的 POST（发布笔记）。</p>
     */
    private static boolean isAnonymousReadable() {
        SaRequest req = SaHolder.getRequest();
        if (req == null || !"GET".equalsIgnoreCase(req.getMethod())) {
            return false;
        }
        String path = req.getRequestPath();
        if (path == null) {
            return false;
        }
        // 归一化尾部斜杠：/api/v1/products/ 与 /api/v1/products 视为同一路径
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (PUBLIC_READ_EXACT.contains(path)) {
            return true;
        }
        return PUBLIC_READ_DETAIL_PREFIXES.stream().anyMatch(prefix -> isNumericDetail(path, prefix));
    }

    /** 形态校验：{@code path == prefix + 纯数字} */
    private static boolean isNumericDetail(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return false;
        }
        String rest = path.substring(prefix.length());
        if (rest.isEmpty()) {
            return false;
        }
        for (int i = 0; i < rest.length(); i++) {
            if (!Character.isDigit(rest.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
