package com.yupi.yuaicodemother.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Spring MVC Json 配置
 */
@Configuration
public class JsonConfig {

    /**
     * 添加 Long 转 json 精度丢失的配置
     * Spring Boot 4（Jackson 3）通过 JsonMapperBuilderCustomizer 定制 JsonMapper，
     * 只追加配置，不覆盖 Boot 的其他自动配置
     */
    @Bean
    public JsonMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.addModule(module);
        };
    }
}
