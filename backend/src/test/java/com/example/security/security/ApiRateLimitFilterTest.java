package com.example.security.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRateLimitFilterTest {

    @Test
    void returns429AfterBurstIsConsumed() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(true, 0.0001, 2, "");

        assertThat(call(filter, "203.0.113.10").getStatus()).isEqualTo(200);
        assertThat(call(filter, "203.0.113.10").getStatus()).isEqualTo(200);

        MockHttpServletResponse throttled = call(filter, "203.0.113.10");
        assertThat(throttled.getStatus()).isEqualTo(429);
        assertThat(throttled.getHeader("Retry-After")).isEqualTo("1");
        assertThat(throttled.getContentAsString()).contains("Too many requests");
    }

    @Test
    void keepsClientsIndependentAndHonoursTemporaryAllowlist() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(true, 0.0001, 1, "198.51.100.7");

        assertThat(call(filter, "203.0.113.10").getStatus()).isEqualTo(200);
        assertThat(call(filter, "203.0.113.10").getStatus()).isEqualTo(429);
        assertThat(call(filter, "203.0.113.11").getStatus()).isEqualTo(200);
        assertThat(call(filter, "198.51.100.7").getStatus()).isEqualTo(200);
        assertThat(call(filter, "198.51.100.7").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse call(ApiRateLimitFilter filter, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.setServletPath("/api/me");
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
