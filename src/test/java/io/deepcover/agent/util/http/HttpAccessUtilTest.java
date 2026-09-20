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

        FakeRequest request = new FakeRequest(headers);
        HttpAccessUtil access = new HttpAccessUtil().wrapperHttpAccess(new Object[]{request});

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", access.getTraceId());
        assertEquals(Collections.emptyMap(), access.getParameterMap());
        assertEquals(0, request.getRemoteAddressCalls);
        assertEquals(0, request.getParameterMapCalls);
        assertEquals(0, request.getHeaderCalls("User-Agent"));
    }

    @Test
    public void testStopsReadingTraceHeadersAfterW3cMatch() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        FakeRequest request = new FakeRequest(headers);

        new HttpAccessUtil().wrapperHttpAccess(new Object[]{request});

        assertEquals(1, request.getHeaderCalls("traceparent"));
        assertEquals(0, request.getHeaderCalls("X-B3-TraceId"));
        assertEquals(0, request.getHeaderCalls("X-Trace-Id"));
        assertEquals(0, request.getHeaderCalls("traceId"));
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
        private final Map<String, Integer> headerCalls = new HashMap<>();
        private int getRemoteAddressCalls;
        private int getParameterMapCalls;

        FakeRequest(Map<String, String> headers) {
            this.headers = headers;
        }

        public String getRemoteAddr() {
            getRemoteAddressCalls++;
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
            getParameterMapCalls++;
            return Collections.emptyMap();
        }

        public String getHeader(String name) {
            headerCalls.put(name, getHeaderCalls(name) + 1);
            return headers.get(name);
        }

        int getHeaderCalls(String name) {
            Integer calls = headerCalls.get(name);
            return calls == null ? 0 : calls;
        }
    }
}
