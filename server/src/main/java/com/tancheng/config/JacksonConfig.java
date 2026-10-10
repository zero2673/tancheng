package com.tancheng.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花 ID 是 18~19 位的 long，超过 JavaScript 的 Number.MAX_SAFE_INTEGER（2^53-1），
 * 直接返回数字会被前端"四舍五入"（例如 ...617 变成 ...616）。
 * 这里统一把 Long 序列化成字符串，前端按字符串处理即可。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}