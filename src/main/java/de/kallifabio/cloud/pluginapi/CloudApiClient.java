package de.kallifabio.cloud.pluginapi;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public final class CloudApiClient implements AutoCloseable {

    private final CloudApiConfig config;
    private final HttpClient httpClient;
    private final Gson gson;

    public CloudApiClient(CloudApiConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .build();
    }

    public JsonObject get(String path) {
        HttpRequest request = buildRequest(path).GET().build();
        return executeJson(request, path);
    }

    public JsonObject get(String path, Map<String, String> queryParams) {
        return get(withQuery(path, queryParams));
    }

    public String getText(String path) {
        HttpRequest request = buildRequest(path).GET().build();
        return executeText(request, path);
    }

    public JsonObject post(String path, Object body) {
        String json = gson.toJson(body == null ? Map.of() : body);
        HttpRequest request = buildRequest(path)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return executeJson(request, path);
    }

    public JsonObject put(String path, Object body) {
        String json = gson.toJson(body == null ? Map.of() : body);
        HttpRequest request = buildRequest(path)
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return executeJson(request, path);
    }

    public JsonObject delete(String path) {
        HttpRequest request = buildRequest(path).DELETE().build();
        return executeJson(request, path);
    }

    public String withQuery(String path, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return path;
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
            String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
            joiner.add(key + "=" + value);
        }
        return path + (path.contains("?") ? "&" : "?") + joiner;
    }

    @Override
    public void close() {
        // No manual shutdown needed for java.net.http.HttpClient.
    }

    private HttpRequest.Builder buildRequest(String path) {
        String fullUrl = path.startsWith("http://") || path.startsWith("https://")
                ? path
                : config.baseUrl() + (path.startsWith("/") ? path : "/" + path);
        return HttpRequest.newBuilder(URI.create(fullUrl))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-API-Key", config.apiKey())
                .header("User-Agent", config.userAgent())
                .timeout(config.readTimeout());
    }

    private JsonObject executeJson(HttpRequest request, String endpoint) {
        String responseText = executeText(request, endpoint);
        try {
            JsonElement parsed = JsonParser.parseString(responseText);
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            JsonObject wrapper = new JsonObject();
            wrapper.add("data", parsed);
            return wrapper;
        } catch (Exception e) {
            throw new CloudApiException("Failed to parse JSON from " + endpoint, e);
        }
    }

    private String executeText(HttpRequest request, String endpoint) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseText = response.body() == null ? "" : response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorMessage = extractErrorMessage(responseText);
                throw new CloudApiException(
                        "API request failed: " + errorMessage,
                        response.statusCode(),
                        endpoint,
                        responseText
                );
            }
            return responseText;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new CloudApiException("HTTP request failed for " + endpoint + ": " + e.getMessage(), e);
        }
    }

    private String extractErrorMessage(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return "empty response body";
        }
        try {
            JsonElement parsed = JsonParser.parseString(responseText);
            if (parsed.isJsonObject()) {
                JsonObject object = parsed.getAsJsonObject();
                if (object.has("error")) {
                    return object.get("error").getAsString();
                }
                if (object.has("message")) {
                    return object.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return responseText;
    }
}
