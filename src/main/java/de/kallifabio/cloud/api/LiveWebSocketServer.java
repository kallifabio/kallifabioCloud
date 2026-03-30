package de.kallifabio.cloud.api;

import com.google.gson.Gson;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Predicate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;

public class LiveWebSocketServer extends WebSocketServer {

    private final Gson gson = new Gson();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Supplier<Map<String, Object>> snapshotSupplier;
    private Predicate<String> apiKeyValidator;

    public LiveWebSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    public void setSnapshotSupplier(Supplier<Map<String, Object>> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    public void setApiKeyValidator(Predicate<String> apiKeyValidator) {
        this.apiKeyValidator = apiKeyValidator;
    }

    public void configureTls(SSLContext sslContext) {
        if (sslContext != null) {
            setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        if (!isAuthorized(handshake)) {
            conn.close(1008, "Unauthorized");
            return;
        }
        conn.send("{\"type\":\"welcome\",\"message\":\"Live stream connected\"}");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if ("ping".equalsIgnoreCase(message)) {
            conn.send("pong");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
    }

    @Override
    public void onStart() {
        scheduler.scheduleAtFixedRate(this::broadcastSnapshot, 1, 2, TimeUnit.SECONDS);
    }

    private void broadcastSnapshot() {
        if (snapshotSupplier == null || getConnections().isEmpty()) {
            return;
        }
        Map<String, Object> snapshot = snapshotSupplier.get();
        String payload = gson.toJson(snapshot);
        broadcast(payload);
    }

    public void shutdown() {
        scheduler.shutdownNow();
        try {
            stop(1000);
        } catch (Exception ignored) {
        }
    }

    private boolean isAuthorized(ClientHandshake handshake) {
        if (apiKeyValidator == null) {
            return true;
        }

        String headerKey = handshake.getFieldValue("X-API-Key");
        if (headerKey != null && !headerKey.isBlank()) {
            return apiKeyValidator.test(headerKey.trim());
        }

        String token = extractTokenFromPath(handshake.getResourceDescriptor());
        return token != null && apiKeyValidator.test(token);
    }

    private String extractTokenFromPath(String descriptor) {
        if (descriptor == null || !descriptor.contains("?")) {
            return null;
        }
        String query = descriptor.substring(descriptor.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            if ("token".equalsIgnoreCase(parts[0]) || "apiKey".equalsIgnoreCase(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
