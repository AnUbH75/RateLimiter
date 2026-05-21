package com.TokenBucket.RateLimiter.controller;

import com.TokenBucket.RateLimiter.service.RateLimiterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/gateway")
public class StatusController {
    private final RateLimiterService rateLimiterService;

    public StatusController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {

        return Mono.just(
                ResponseEntity.ok(
                        Map.of(
                                "status", "UP",
                                "service", "rate-limiting-gateway"
                        )
                )
        );
    }

    @GetMapping("/rate-limit/status")
    public Mono<ResponseEntity<Map<String, Object>>> rateLimitStatus(ServerWebExchange exchange) {
        String clientId = getClientId(exchange);
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "rate-limiting-gateway",
                "clientId", clientId,
                "capacity", rateLimiterService.getCapacity(clientId),
                "availableTokens", rateLimiterService.getAvailableTokens(clientId)
        )));
    }

    public String getClientId(ServerWebExchange exchange) {
        String xForwardedFor =
                exchange.getRequest()
                        .getHeaders()
                        .getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
