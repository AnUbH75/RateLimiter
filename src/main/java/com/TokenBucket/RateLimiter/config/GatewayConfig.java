package com.TokenBucket.RateLimiter.config;

import com.TokenBucket.RateLimiter.filter.TokenBucketRateLimiterFilter;
import com.TokenBucket.RateLimiter.service.RateLimiterService;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;

@Configuration
public class GatewayConfig {
     private final RateLimiterProperties rateLimiterProperties;
     private final TokenBucketRateLimiterFilter tokenBucketRateLimiterFilter;

     public GatewayConfig(RateLimiterProperties rateLimiterProperties, TokenBucketRateLimiterFilter tokenBucketRateLimiterFilter) {
         this.rateLimiterProperties = rateLimiterProperties;
         this.tokenBucketRateLimiterFilter = tokenBucketRateLimiterFilter;
     }

     @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {

         return builder.routes()
                 .route("api-route", r->r
                         .path("/api/**")
                         .filters(f -> f
                                 .stripPrefix(1)
                                 .filter(tokenBucketRateLimiterFilter.apply(new TokenBucketRateLimiterFilter.Config()))
                         )
                         .uri(rateLimiterProperties.getApiServerUrl()))
                 .build();
     }
}
