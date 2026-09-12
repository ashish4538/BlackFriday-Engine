package com.example.flashsale;

import com.example.flashsale.config.SecurityConfig;
import com.example.flashsale.controller.*;
import com.example.flashsale.model.Order;
import com.example.flashsale.repository.*;
import com.example.flashsale.security.CustomUserDetailsService;
import com.example.flashsale.service.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, OrderController.class, ProductController.class})
@Import({SecurityConfig.class, CustomUserDetailsService.class, SecurityTest.Metrics.class})
class SecurityTest {
    @TestConfiguration static class Metrics {
        @Bean MeterRegistry metrics() { return new SimpleMeterRegistry(); }
    }
    @Autowired MockMvc mvc;
    @MockBean UserRepository users;
    @MockBean ProductRepository products;
    @MockBean InventoryService inventory;
    @MockBean RateLimiter limiter;

    @Test
    void anonymousAndOrdinaryUsersCannotChangeProductsOrStock() throws Exception {
        mvc.perform(delete("/api/products/item").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/products/item").with(user("buyer")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/item/initialize").param("stock", "99")
                .with(user("buyer")).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(products, inventory);
    }

    @Test
    void registrationIgnoresSuppliedIdAndRoleAndHashesPassword() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("""
                    {"id":1,"role":"ADMIN","username":"buyer","password":"long-password-123",
                     "email":"buyer@example.com","fullName":"Buyer"}
                    """)).andExpect(status().isCreated());
        verify(users).saveAndFlush(argThat(u -> u.getId() == null && u.getRole().equals("USER")
                && new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                    .matches("long-password-123", u.getPassword())));
    }

    @Test
    void csrfIsRequiredForSessionWrites() throws Exception {
        mvc.perform(post("/api/orders").param("itemId", "item").with(user("buyer")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/perform_login").param("username", "buyer").param("password", "password"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventory);
    }

    @Test
    void purchaseUsesPrincipalAndIgnoresClientPriceAndForwardedHeaders() throws Exception {
        when(inventory.purchase("buyer", "item")).thenReturn(new Order("id", "buyer", "item", BigDecimal.TEN));
        mvc.perform(post("/api/orders").with(user("buyer")).with(csrf())
                .param("itemId", "item").param("userId", "victim").param("price", "0.01")
                .header("X-Forwarded-For", "1.2.3.4")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.price").value(10)).andExpect(jsonPath("$.userId").value("buyer"));
        verify(limiter).retryAfterMs("buyer");
        verify(inventory).purchase("buyer", "item");
    }

    @Test
    void limiterReturns429AndRedisFailureFailsClosed() throws Exception {
        when(limiter.retryAfterMs("buyer")).thenReturn(100L).thenThrow(new IllegalStateException());
        mvc.perform(post("/purchase").param("itemId", "item").with(user("buyer")).with(csrf()))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1"));
        mvc.perform(post("/api/orders").param("itemId", "item").with(user("buyer")).with(csrf()))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(inventory);
    }

    @Test
    void validationRejectsBadRegistrationAndAdminProduct() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{\"username\":\"<script>\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json")
                .content("{\"id\":\"item\",\"name\":\"item\",\"price\":-1,\"imageUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metricsRequireAdminAndCsrfEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/metrics").with(user("buyer"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }
}
