package com.TokenBucket.RateLimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
@Data
@ConfigurationProperties(prefix = "spring.redis")
public class RedisConfig {
    public String host =  "localhost";
    public int port = 6379;
    public int timeout = 2000;

    @Bean
    public JedisPool getJedisPool() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(50);
        jedisPoolConfig.setMaxIdle(10);
        jedisPoolConfig.setMinIdle(5);
        jedisPoolConfig.setTestOnBorrow(true);
        jedisPoolConfig.setTestOnBorrow(true);
        return new JedisPool(jedisPoolConfig, host, port, timeout);
    }
}
