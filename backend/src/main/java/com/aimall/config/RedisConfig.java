package com.aimall.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置 —— 重点在<b>序列化器选型</b>（新手最常踩的坑）。
 *
 * <h2>★ 为什么必须换掉默认的 JDK 序列化</h2>
 * RedisTemplate 默认用 {@code JdkSerializationRedisSerializer}，它有三个问题：
 * <ol>
 *   <li><b>不可读</b>：redis-cli 里看到的是乱码（二进制），线上排查只能靠猜；</li>
 *   <li><b>强耦合 Java</b>：写入的字节含类全限定名，换个包名/字段就反序列化失败；</li>
 *   <li><b>反序列化漏洞</b>：任何可执行构造器的类都可能被利用（安全红线）。</li>
 * </ol>
 * 本项目统一用 <b>JSON 序列化</b>（Jackson）：可读、跨语言、且 Jackson 默认关闭
 * 任意类型反序列化。
 *
 * <h2>StringRedisTemplate 与 RedisTemplate 各司其职</h2>
 * <ul>
 *   <li>{@code StringRedisTemplate}：计数、锁、限流、zset 成员——值本来就是字符串；</li>
 *   <li>{@code RedisTemplate<String,Object>}：缓存商品/笔记对象——值需要 JSON 序列化。</li>
 * </ul>
 *
 * <p>注意：这里的序列化器配置与 V2 的 MQ 消息转换器思路一致——
 * <b>跨进程的数据一律用 JSON，不用语言专属的二进制格式</b>。</p>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key / hash-key：字符串序列化（保证 key 可读、与其他客户端兼容）
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // value：JSON 序列化（带类型信息，反序列化时能还原成具体类）
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.registerModule(new JavaTimeModule());   // 支持 LocalDateTime
        // 开启默认类型：反序列化时能还原具体类型（本项目缓存的都是自家类，风险可控）
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(om);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        // Boot 已自动提供，此处显式声明仅为强调其用途；交由 Boot 自动配置亦可
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }
}
