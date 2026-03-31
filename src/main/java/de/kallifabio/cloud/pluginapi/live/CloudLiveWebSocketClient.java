package de.kallifabio.cloud.pluginapi.live;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kallifabio.cloud.pluginapi.CloudApiException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class CloudLiveWebSocketClient {

    private final HttpClient httpClient;
    private final String wsUrl;
    private final String token;
    private final CloudLiveEventListener listener;
    private volatile WebSocket webSocket;

    public CloudLiveWebSocketClient(String wsUrl, String token, CloudLiveEventListener listener) {
        this.wsUrl = Objects.requireNonNull(wsUrl, "wsUrl");
        this.token = Objects.requireNonNull(token, "token");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public CompletableFuture<WebSocket> connect() {
        String finalUrl = wsUrl + (wsUrl.contains("?") ? "&" : "?") + "token=" + token;
        return httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(finalUrl), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        CloudLiveWebSocketClient.this.webSocket = webSocket;
                        listener.onOpen();
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        try {
                            JsonObject payload = JsonParser.parseString(data.toString()).getAsJsonObject();
                            listener.onEvent(payload);
                        } catch (Exception ex) {
                            listener.onError(ex);
                        }
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        listener.onClose(statusCode, reason);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        listener.onError(error);
                    }
                })
                .exceptionally(ex -> {
                    throw new CloudApiException("WebSocket connection failed: " + ex.getMessage(), ex);
                });
    }

    public void send(String message) {
        WebSocket socket = webSocket;
        if (socket == null) {
            throw new CloudApiException("WebSocket is not connected", -1, wsUrl, "");
        }
        socket.sendText(message, true);
    }

    public CompletableFuture<WebSocket> close() {
        WebSocket socket = webSocket;
        if (socket == null) {
            return CompletableFuture.completedFuture(null);
        }
        return socket.sendClose(WebSocket.NORMAL_CLOSURE, "client-close")
                .thenApply(ignored -> socket);
    }
}
