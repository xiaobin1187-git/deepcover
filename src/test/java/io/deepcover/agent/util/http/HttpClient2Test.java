package io.deepcover.agent.util.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.deepcover.agent.entity.CodeEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpClient2Test {

    private HttpServer server;
    private AtomicInteger receivedRequests;
    private String baseUrl;

    @Before
    public void setUp() throws IOException {
        receivedRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", new StatusHandler(200));
        server.createContext("/failure", new StatusHandler(500));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testDoPostReturnsTrueForSuccessfulResponse() {
        assertTrue(HttpClient2.doPost(baseUrl + "/ok", createCodeEntity("trace-success")));
        assertEquals(1, receivedRequests.get());
    }

    @Test
    public void testDoPostReturnsFalseForErrorResponse() {
        assertFalse(HttpClient2.doPost(baseUrl + "/failure", createCodeEntity("trace-failure")));
        assertEquals(1, receivedRequests.get());
    }

    private CodeEntity createCodeEntity(String traceId) {
        CodeEntity entity = new CodeEntity();
        entity.setTraceId(traceId);
        entity.setServiceName("test-service");
        entity.setProcessId(1);
        entity.setUrl("/test");
        entity.setCodeInfo(new ArrayList<>());
        entity.setCodeInfoSize(0);
        return entity;
    }

    private class StatusHandler implements HttpHandler {
        private final int status;

        private StatusHandler(int status) {
            this.status = status;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream input = exchange.getRequestBody()) {
                byte[] buffer = new byte[1024];
                while (input.read(buffer) != -1) {
                    // Consume the complete request body before responding.
                }
            }
            receivedRequests.incrementAndGet();
            byte[] response = "ok".getBytes("UTF-8");
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        }
    }
}
