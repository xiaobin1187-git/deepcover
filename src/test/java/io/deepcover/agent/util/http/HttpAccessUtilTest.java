package io.deepcover.agent.util.http;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HttpAccessUtilTest {

    @Test
    public void testUsesW3cTraceParent() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        HttpAccessUtil access = new HttpAccessUtil().wrapperHttpAccess(new Object[]{new FakeRequest(headers)});

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", access.getTraceId());
    }

    @Test
    public void testFallsBackToB3TraceId() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "invalid");
        headers.put("X-B3-TraceId", "b3-trace-id");

        HttpAccessUtil access = new HttpAccessUtil().wrapperHttpAccess(new Object[]{new FakeRequest(headers)});

        assertEquals("b3-trace-id", access.getTraceId());
    }

    @Test
    public void testMissingTraceHeaderReturnsNull() {
        HttpAccessUtil access = new HttpAccessUtil().wrapperHttpAccess(new Object[]{new FakeRequest(Collections.emptyMap())});

        assertNull(access.getTraceId());
    }

    public static class FakeRequest {
        private final Map<String, String> headers;

        FakeRequest(Map<String, String> headers) {
            this.headers = headers;
        }

        public String getRemoteAddr() {
            return "127.0.0.1";
        }

        public int getServerPort() {
            return 8080;
        }

        public String getMethod() {
            return "GET";
        }

        public String getRequestURI() {
            return "/test";
        }

        public Map<String, String[]> getParameterMap() {
            return Collections.emptyMap();
        }

        public String getHeader(String name) {
            return headers.get(name);
        }
    }
}
