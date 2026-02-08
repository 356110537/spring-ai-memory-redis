package com.iflytek.ai.memory.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.iflytek.ai.memory.redis.serializer.MessageDeserializer;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.List;

public class RedisChatMemoryRepository implements ChatMemoryRepository, AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String defaultKeyPrefix;
    private final ScanParams params = new ScanParams().match(defaultKeyPrefix + "*");
    private final JedisPooled jedisPool;
    private final ObjectMapper objectMapper;

    public RedisChatMemoryRepository(JedisConnectionFactory jedisConnectionFactory, ObjectMapper objectMapper, String redisPrefix) {
        this.jedisPool = this.jedisPooled(jedisConnectionFactory);
        this.objectMapper = objectMapper;
        this.defaultKeyPrefix = redisPrefix;
        SimpleModule module = new SimpleModule("springAiMemoryRedis");
        module.addDeserializer(Message.class, new MessageDeserializer());
        this.objectMapper.registerModule(module);
    }

    private JedisPooled jedisPooled(JedisConnectionFactory jedisConnectionFactory) {
        String host = jedisConnectionFactory.getHostName();
        int port = jedisConnectionFactory.getPort();
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder().ssl(jedisConnectionFactory.isUseSsl()).clientName(jedisConnectionFactory.getClientName()).timeoutMillis(jedisConnectionFactory.getTimeout()).password(jedisConnectionFactory.getPassword()).build();
        return new JedisPooled(new HostAndPort(host, port), clientConfig);
    }

    @NonNull
    @Override
    public List<String> findConversationIds() {
        List<String> keys = new ArrayList<>();
        final String cursor = "0";
        String index = cursor;
        do {
            ScanResult<String> result = jedisPool.scan(cursor, params);
            if (result != null) {
                List<String> list = result.getResult();
                if (ObjectUtils.isNotEmpty(list)) {
                    keys.addAll(list);
                }
                index = result.getCursor();
            }
        } while (!cursor.equals(index));
        return keys.stream().map(key -> key.substring(defaultKeyPrefix.length())).toList();
    }

    @NonNull
    @Override
    public List<Message> findByConversationId(@NonNull String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        String key = defaultKeyPrefix + conversationId;
        List<String> messageStrings = jedisPool.lrange(key, 0, -1);
        List<Message> messages = new ArrayList<>();
        for (String messageString : messageStrings) {
            try {
                Message message = objectMapper.readValue(messageString, Message.class);
                messages.add(message);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error deserializing message", e);
            }
        }
        return messages;
    }

    @Override
    public void saveAll(@NonNull String conversationId, @NonNull List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");
        // Clear existing messages first
        deleteByConversationId(conversationId);
        // Add all messages in order
        String key = defaultKeyPrefix + conversationId;
        for (Message message : messages) {
            try {
                String messageJson = objectMapper.writeValueAsString(message);
                jedisPool.rpush(key, messageJson);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing message", e);
            }
        }
    }

    @Override
    public void deleteByConversationId(@NonNull String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        String key = defaultKeyPrefix + conversationId;
        jedisPool.del(key);
    }

    @Override
    public void close() throws Exception {
        if (jedisPool != null) {
            jedisPool.close();
            logger.info("Redis connection pool closed");
        }
    }
}