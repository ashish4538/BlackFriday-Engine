package com.example.flashsale.security;

import com.example.flashsale.service.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class PurchaseRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(PurchaseRateLimitFilter.class);
    private final RateLimiter limiter;
    private final MeterRegistry metrics;

    public PurchaseRateLimitFilter(RateLimiter limiter, MeterRegistry metrics) {
        this.limiter = limiter;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod())
                || !("/purchase".equals(path) || "/api/orders".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        long retry;
        try {
            // Use the authenticated identity; never trust client IP or forwarded headers.
            retry = limiter.retryAfterMs(auth.getName());
        } catch (RuntimeException e) {
            // Fail closed: Redis outages reduce availability rather than bypass purchase admission.
            metrics.counter("purchase.rate_limit", "result", "unavailable").increment();
            log.warn("Purchase admission unavailable ({})", e.getClass().getSimpleName());
            response.setHeader("Retry-After", "1");
            response.sendError(503, "Purchase admission unavailable");
            return;
        }
        if (retry > 0) {
            metrics.counter("purchase.rate_limit", "result", "rejected").increment();
            response.setHeader("Retry-After", Long.toString((retry + 999) / 1000));
            response.sendError(429, "Purchase rate limit exceeded");
            return;
        }
        metrics.counter("purchase.rate_limit", "result", "allowed").increment();
        chain.doFilter(request, response);
    }
}
