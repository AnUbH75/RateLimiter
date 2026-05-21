package com.TokenBucket.RateLimiter.filter;

import com.TokenBucket.RateLimiter.service.RateLimiterService;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;


@Component
public class TokenBucketRateLimiterFilter extends AbstractGatewayFilterFactory<TokenBucketRateLimiterFilter.Config> {

    private final RateLimiterService rateLimiterService;

    public TokenBucketRateLimiterFilter(RateLimiterService rateLimiterService) {
        super(Config.class);
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public TokenBucketRateLimiterFilter.Config newConfig() {
        return new Config();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            String clientId = getClientId(request);

            if(!rateLimiterService.isAllowed(clientId)){
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                addRateLimitHeaders(response, clientId);

                String errBody = String.format(
                      "{\"error\":\"Rate limit exceeded\",\"clientId\":\"%s\"}",
                      clientId
                );

                return response.writeWith(
                        Mono.just(response.bufferFactory().wrap(errBody.getBytes(StandardCharsets.UTF_8)))
                );
            }

            return chain.filter(exchange).then(
                    Mono.fromRunnable(() -> {
                        addRateLimitHeaders(response, clientId);
                    })
            );
        };
    }

    private void addRateLimitHeaders(ServerHttpResponse response, String clientId) {
        response.getHeaders().add("X-RateLimit-Limit",
                String.valueOf(rateLimiterService.getCapacity(clientId)));
        response.getHeaders().add("X-RateLimit-Remaining",
                String.valueOf(rateLimiterService.getAvailableTokens(clientId)));
    }

    public  String getClientId(ServerHttpRequest request) {
        String xForwardFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardFor != null && !xForwardFor.isEmpty()) {
            return xForwardFor.split(",")[0].trim();
        }

        var remoteAddr = request.getRemoteAddress();
        if (remoteAddr != null && remoteAddr.getHostName() != null) {
            return remoteAddr.getAddress().getHostAddress();
        }
        return"unknown";
    }

    public static class Config{}
}
