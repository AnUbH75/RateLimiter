# RateLimiter

A production-grade **Token Bucket Rate Limiter** built as a Spring Cloud Gateway application. Intercepts all incoming HTTP traffic, enforces per-client request limits using Redis as shared state, and forwards allowed requests to a backend server.

---

## Architecture

```
Client Request
      ↓
Spring Cloud Gateway (:8080)
      ↓
TokenBucketRateLimiterFilter
      ↓ checks Redis atomically via Lua script
   Allowed?
   ✓ YES → strip /api prefix → Backend Server (:8081)
   ✗ NO  → 429 Too Many Requests
```

---

## How it works

### Token Bucket Algorithm
- Each client IP gets a bucket with a fixed **capacity** (max tokens)
- Every request consumes 1 token
- Tokens refill at a fixed **rate** (tokens/second) up to capacity
- Requests are rejected when the bucket is empty

### Why Redis
Token counts are stored in Redis so all app instances share the same state. Without this, a horizontally scaled deployment would have each instance tracking its own count, letting clients bypass the limit entirely.

### Why Lua scripting
The refill + check + decrement sequence must be atomic. Without it, two concurrent requests can both read the same token count, both pass the check, and both decrement — consuming one token but serving two requests. The Lua script executes entirely on the Redis server as a single operation, preventing any interleaving.

---

## Tech Stack

- **Java 21**
- **Spring Boot 3.3.5**
- **Spring Cloud Gateway** (reactive, WebFlux-based)
- **Redis** (via Jedis connection pool)
- **Lombok**

---

## Project Structure

```
src/
└── main/java/com/TokenBucket/RateLimiter/
    ├── RateLimiterApplication.java
    ├── config/
    │   ├── GatewayConfig.java          # Route definition, filter wiring
    │   ├── RateLimiterProperties.java  # Capacity, refill rate, backend URL
    │   └── RedisConfig.java            # JedisPool setup
    ├── controller/
    │   └── StatusController.java       # Health + rate limit status endpoints
    ├── filter/
    │   └── TokenBucketRateLimiterFilter.java  # Gateway filter, 429 responses
    └── service/
        ├── RateLimiterService.java            # Facade
        └── RedisTokenBucketService.java       # Lua script, Redis ops
```

---

## Configuration

`src/main/resources/application.properties`

```properties
server.port=8080

spring.redis.host=localhost
spring.redis.port=6379

rate-limiter.capacity=10        # max tokens per client
rate-limiter.refill-rate=1      # tokens added per second
rate-limiter.api-server-url=http://localhost:8081
```

---

## Running Locally

### Prerequisites
- Java 21
- Redis running on `localhost:6379`
- Python 3 (for the mock backend)

### Steps

```bash
# 1. Start Redis
redis-server

# 2. Start the mock backend (port 8081)
python mock_server_simple.py

# 3. Run the gateway
./gradlew bootRun

# 4. Test it
./quick-test.sh
```

---

## API Reference

### Proxied routes
All requests to `/api/**` are rate-limited and forwarded to the backend with the `/api` prefix stripped.

```
GET /api/anything  →  proxied to :8081/anything
```

Response headers on allowed requests:
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 7
```

Blocked requests return:
```json
HTTP 429 Too Many Requests
{
  "error": "Rate limit exceeded",
  "clientId": "0:0:0:0:0:0:0:1"
}
```

### Status endpoints

```
GET /gateway/health
```
```json
{ "status": "UP", "service": "rate-limiting-gateway" }
```

```
GET /gateway/rate-limit/status
```
```json
{
  "clientId": "0:0:0:0:0:0:0:1",
  "capacity": 10,
  "availableTokens": 7,
  "status": "UP",
  "service": "rate-limiting-gateway"
}
```

---

## Redis Key Schema

| Key | Value | Purpose |
|-----|-------|---------|
| `rate_limiter:tokens:<clientId>` | integer | Current token count |
| `rate_limiter:last_refill:<clientId>` | epoch ms | Last refill timestamp |

---

## Design Decisions

**Per-IP bucketing** — each client is identified by `X-Forwarded-For` header, falling back to remote address. In production this would be replaced with an API key or user ID for authenticated routes.

**Atomic Lua script** — the entire refill + check + decrement runs as a single Redis script. This is the standard production pattern used by Stripe, Cloudflare, and Redis's own `RedisRateLimiter` in Spring Cloud Gateway.

**JedisPool** — configured with 50 max connections and min-evictable idle time to handle burst traffic without connection exhaustion.

**No TTL on keys** — token keys persist indefinitely. In production, a TTL of `capacity / refillRate` seconds should be set so inactive clients don't accumulate stale keys.

---

## Known Limitations

- No TTL on Redis keys (inactive client keys never expire)
- Client identification is IP-based only
- No per-route or per-user rule support
- Single Redis instance (no cluster support)