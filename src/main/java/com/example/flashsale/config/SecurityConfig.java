package com.example.flashsale.config;

import com.example.flashsale.security.PurchaseRateLimitFilter;
import com.example.flashsale.service.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimiter limiter, MeterRegistry metrics) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/home.html", "/deals.html",
                        "/support.html", "/login.html", "/register.html", "/css/**", "/js/**",
                        "/images/**", "/api/auth/me", "/api/auth/csrf", "/api/products/**",
                        "/api/inventory/stock/**", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers("/api/products/**", "/api/inventory/**", "/admin", "/admin.html",
                        "/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            // Keep session CSRF protection, including login, registration and logout.
            .formLogin(form -> form.loginPage("/login.html").loginProcessingUrl("/perform_login")
                    .defaultSuccessUrl("/home.html", true).failureUrl("/login.html?error=true"))
            .logout(logout -> logout.logoutUrl("/perform_logout").logoutSuccessUrl("/home.html"))
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                    (request, response, error) -> response.sendError(401),
                    new AntPathRequestMatcher("/api/**")))
            .addFilterAfter(new PurchaseRateLimitFilter(limiter, metrics), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
