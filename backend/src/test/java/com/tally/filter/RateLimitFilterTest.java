package com.tally.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    // =========================================================================
    // isAuthEndpoint
    // =========================================================================

    @Test
    void shouldIdentifyAuthLoginAsAuthEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        assertTrue(filter.isAuthEndpoint(request));
    }

    @Test
    void shouldIdentifyAuthRegisterAsAuthEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        assertTrue(filter.isAuthEndpoint(request));
    }

    @Test
    void shouldIdentifyAuthRefreshAsAuthEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        assertTrue(filter.isAuthEndpoint(request));
    }

    @Test
    void shouldNotRateLimitOptionsPreflightOnAuthEndpoints() {
        // Browsers send OPTIONS before the actual POST (CORS preflight).
        // Counting these against the limit would exhaust tokens before the real request.
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        assertFalse(filter.isAuthEndpoint(request));
    }

    @Test
    void shouldNotIdentifyHabitsAsAuthEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/habits");
        assertFalse(filter.isAuthEndpoint(request));
    }

    @Test
    void shouldNotIdentifyHealthAsAuthEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        assertFalse(filter.isAuthEndpoint(request));
    }

    // =========================================================================
    // resolveClientIp
    // =========================================================================

    @Test
    void shouldReturnRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertEquals("10.0.0.1", filter.resolveClientIp(request));
    }

    @Test
    void shouldNotParseXForwardedForDirectly() {
        // X-Forwarded-For resolution is delegated to Spring's forward-headers-strategy
        // in production (server.forward-headers-strategy=native in application-prod.properties).
        // The filter always uses getRemoteAddr(), which Spring/Tomcat resolves correctly.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5");

        // getRemoteAddr() is returned as-is; in prod Tomcat resolves it from XFF
        assertEquals("10.0.0.1", filter.resolveClientIp(request));
    }

    // =========================================================================
    // Rate limiting behaviour (via doFilter)
    // =========================================================================

    @Test
    void shouldPassThroughNonAuthEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/habits");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotEquals(429, response.getStatus());
        assertNotNull(chain.getRequest()); // chain was called
    }

    @Test
    void shouldPassThroughOptionsEvenOnAuthPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotEquals(429, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void shouldAllowAuthRequestsUpToLimit() throws Exception {
        for (int i = 0; i < RateLimitFilter.AUTH_REQUESTS_PER_MINUTE; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("192.168.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertNotEquals(429, response.getStatus(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        String ip = "192.168.1.2";

        // Burn through all tokens
        for (int i = 0; i < RateLimitFilter.AUTH_REQUESTS_PER_MINUTE; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr(ip);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        // One more should be rejected
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("Rate limit exceeded"));
        assertEquals("application/json", response.getContentType());
    }

    @Test
    void shouldMaintainSeparateBucketsPerIp() throws Exception {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";

        // Exhaust IP1's limit
        for (int i = 0; i < RateLimitFilter.AUTH_REQUESTS_PER_MINUTE; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr(ip1);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        // IP2 should still have its full bucket
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip2);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotEquals(429, response.getStatus());
    }
}
