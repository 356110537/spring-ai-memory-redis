package com.iflytek.ai.memory.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.ai.memory.redis.RedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

@AutoConfiguration
public class RedisChatMemoryAutoConfiguration {

    @Value("${spring.ai.iflytek.redis-memory.prefix:chat_memory:}")
    private String redisPrefix;

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.jedis.JedisConnectionFactory")
    public RedisChatMemoryRepository redisChatMemoryRepository(JedisConnectionFactory connectionFactory, ObjectMapper mapper) {
        return new RedisChatMemoryRepository(connectionFactory, mapper, redisPrefix);
    }
}