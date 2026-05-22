package com.TokenBucket.RateLimiter.service;

import com.TokenBucket.RateLimiter.config.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisTokenBucketService {

    private final JedisPool jedisPool;
    private final RateLimiterProperties rateLimiterProperties;

    private static final String TOKENS_KEY_PREFIX = "rate_limiter:tokens:";
    private static final String LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:";

    // Lua script — runs atomically on Redis
    private static final String RATE_LIMIT_SCRIPT = """
            local tokenKey = KEYS[1]
            local lastRefillKey = KEYS[2]
            local capacity = tonumber(ARGV[1])
            local refillRate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            
            -- First time this client is seen
            local lastRefill = tonumber(redis.call('GET', lastRefillKey))
            if lastRefill == nil then
                redis.call('SET', tokenKey, capacity - 1)
                redis.call('SET', lastRefillKey, now)
                return {capacity - 1, 1}
            end
            
            -- Refill tokens based on elapsed time
            local elapsed = now - lastRefill
            local tokensToAdd = math.floor((elapsed * refillRate) / 1000)
            if tokensToAdd > 0 then
                local current = tonumber(redis.call('GET', tokenKey)) or 0
                local newTokens = math.min(capacity, current + tokensToAdd)
                redis.call('SET', tokenKey, newTokens)
                local updatedRefill = lastRefill + math.floor((tokensToAdd * 1000) / refillRate)
                redis.call('SET', lastRefillKey, updatedRefill)
            end
            
            -- Check and consume
            local tokens = tonumber(redis.call('GET', tokenKey)) or 0
            if tokens <= 0 then
                return {0, 0}
            end
            redis.call('DECR', tokenKey)
            return {tokens - 1, 1}
            """;

    private static final String GET_TOKENS_SCRIPT = """
            local tokenKey = KEYS[1]
            local lastRefillKey = KEYS[2]
            local capacity = tonumber(ARGV[1])
            local refillRate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            
            local lastRefill = tonumber(redis.call('GET', lastRefillKey))
            if lastRefill == nil then
                return capacity
            end
            
            local elapsed = now - lastRefill
            local tokensToAdd = math.floor((elapsed * refillRate) / 1000)
            local current = tonumber(redis.call('GET', tokenKey)) or 0
            return math.min(capacity, current + tokensToAdd)
            """;

    public boolean isAllowed(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {
            List<Long> result = (List<Long>) jedis.eval(
                    RATE_LIMIT_SCRIPT,
                    List.of(tokenKey, lastRefillKey),
                    List.of(
                            String.valueOf(rateLimiterProperties.getCapacity()),
                            String.valueOf(rateLimiterProperties.getRefillRate()),
                            String.valueOf(System.currentTimeMillis())
                    )
            );
            return result.get(1) == 1L;
        }
    }

    public long getCapacity(String clientId) {
        return rateLimiterProperties.getCapacity();
    }

    public long getAvailableTokens(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(
                    GET_TOKENS_SCRIPT,
                    List.of(tokenKey, lastRefillKey),
                    List.of(
                            String.valueOf(rateLimiterProperties.getCapacity()),
                            String.valueOf(rateLimiterProperties.getRefillRate()),
                            String.valueOf(System.currentTimeMillis())
                    )
            );
            return (Long) result;
        }
    }
}