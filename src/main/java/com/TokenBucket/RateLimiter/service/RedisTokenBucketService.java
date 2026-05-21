package com.TokenBucket.RateLimiter.service;

import com.TokenBucket.RateLimiter.config.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
@RequiredArgsConstructor
public class RedisTokenBucketService {
    private final JedisPool jedisPool;
    private final RateLimiterProperties rateLimiterProperties;
    private final String TOKENS_KEY_PREFIX = "rate_limiter:tokens:";
    private static final String LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:";

    public boolean isAllowed(String clientId) {
        String tokenkey = TOKENS_KEY_PREFIX + clientId;
        try(Jedis jedis = jedisPool.getResource()) {
            refillTokens(clientId, jedis);
            String tokenStr = jedis.get(tokenkey);
            long currentTokens = tokenStr != null ? Long.parseLong(tokenStr) : rateLimiterProperties.getCapacity();
            if(currentTokens <= 0){
                return false;
            }
            long decremented = jedis.decr(tokenkey);
            return decremented >= 0;
        }
    }

    public long getCapacity(String clientId) {
        return rateLimiterProperties.getCapacity();
    }

    public long getAvailableTokens(String clientId) {
        String tokenkey = TOKENS_KEY_PREFIX + clientId;

        try(Jedis jedis = jedisPool.getResource()) {
            refillTokens(clientId, jedis);
            String tokenStr = jedis.get(tokenkey);
            return tokenStr != null ? Long.parseLong(tokenStr) : rateLimiterProperties.getCapacity();
        }
    }

    public void refillTokens(String clientId, Jedis jedis) {
        String tokenkey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        long now = System.currentTimeMillis();
        String lastRefillStr = jedis.get(lastRefillKey);
        if(lastRefillStr == null){
            jedis.set(tokenkey, String.valueOf(rateLimiterProperties.getCapacity()));
            jedis.set(lastRefillKey, String.valueOf(now));
            return;
        }
        long lastRefillTime = Long.parseLong(lastRefillStr);
        long elapsedTime = now - lastRefillTime;
        if(elapsedTime <= 0) return;

        long tokensToAdd = (elapsedTime*rateLimiterProperties.getRefillRate())/1000;
        if(tokensToAdd <= 0) return ;

        String tokenStr = jedis.get(tokenkey);
        long currentTokens = tokenStr != null ? Long.parseLong(tokenStr) : rateLimiterProperties.getCapacity();
        long newTokens = Math.min(rateLimiterProperties.getCapacity(), currentTokens + tokensToAdd);
        jedis.set(tokenkey, String.valueOf(newTokens));
        long updatedRefillTime =
                lastRefillTime +
                        (tokensToAdd * 1000 / rateLimiterProperties.getRefillRate());

        jedis.set(lastRefillKey, String.valueOf(updatedRefillTime));
    }
}
