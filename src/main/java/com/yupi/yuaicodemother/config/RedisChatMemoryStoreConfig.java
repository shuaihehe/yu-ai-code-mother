package com.yupi.yuaicodemother.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 持久化对话记忆
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisChatMemoryStoreConfig {

    private String host;

    private int port;

    private String password;

    private long ttl;

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        // 注意：langchain4j-community-redis 1.18.0-beta28 的 RedisChatMemoryStore 存在缺陷，
        // 仅当 user 非空时才会把 password 传给 Jedis 客户端；
        // 若不设置 user，密码会被静默丢弃，导致远端 Redis 报 NOAUTH Authentication required。
        // 远端 Redis 8.x 支持 ACL，使用 default 用户 + 密码认证（等价于 AUTH default <password>）。
        RedisChatMemoryStore.Builder builder = RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .user("default")
                .password(password)
                .ttl(ttl);
        return builder.build();
    }
}
