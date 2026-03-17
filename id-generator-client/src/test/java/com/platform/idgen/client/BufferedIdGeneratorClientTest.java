package com.platform.idgen.client;

import com.platform.idgen.client.config.IdGeneratorClientConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferedIdGeneratorClientTest {

    private HttpServer server;
    private BufferedIdGeneratorClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void isHealthy读取ApiResponseDataStatus() throws Exception {
        startServer("{\"code\":200,\"message\":\"success\",\"data\":{\"status\":\"UP\"}}");

        client = new BufferedIdGeneratorClient(IdGeneratorClientConfig.builder()
                .serverUrl("http://localhost:" + server.getAddress().getPort())
                .bufferEnabled(false)
                .build());

        assertTrue(client.isHealthy());
    }

    @Test
    void isHealthy在服务降级时返回False() throws Exception {
        startServer("{\"code\":200,\"message\":\"success\",\"data\":{\"status\":\"DEGRADED\"}}");

        client = new BufferedIdGeneratorClient(IdGeneratorClientConfig.builder()
                .serverUrl("http://localhost:" + server.getAddress().getPort())
                .bufferEnabled(false)
                .build());

        assertFalse(client.isHealthy());
    }

    private void startServer(String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/id/health", exchange -> writeJsonResponse(exchange, responseBody));
        server.start();
    }

    private void writeJsonResponse(HttpExchange exchange, String responseBody) throws IOException {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
