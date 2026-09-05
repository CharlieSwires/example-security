package com.example.security.security;

import com.example.security.repository.SecurityAuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityAuditServiceTest {

    @Test
    void doesNotTrustForwardedHeaderInsideApplicationCode() {
        SecurityAuditService service = new SecurityAuditService(
                mock(SecurityAuditEventRepository.class), false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.20");
        request.addHeader("X-Forwarded-For", "198.51.100.99");

        assertThat(service.clientIp(request)).isEqualTo("203.0.113.20");
    }
}
