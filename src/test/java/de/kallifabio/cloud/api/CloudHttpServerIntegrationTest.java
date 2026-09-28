package de.kallifabio.cloud.api;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class CloudHttpServerIntegrationTest {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static CloudHttpServer server;
    private static String baseUrl;

    @BeforeAll
    static void bootServer() throws Exception {
        server = new CloudHttpServer();
        baseUrl = "http://localhost:" + server.getPort();
        waitForServerReady();
    }

    @AfterAll
    static void shutdownServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void dashboardContainsTailwindAndAuthUi() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/dashboard"))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("cdn.tailwindcss.com"));
        Assertions.assertTrue(response.body().contains("id='connectBtn'"));
    }

    @Test
    void authAndRoleGatingWorks() throws Exception {
        String dashboardKey = keyByName("dashboard");
        String adminKey = keyByName("admin");

        HttpResponse<String> meViewer = sendJson(
                baseUrl + "/api/v1/auth/me",
                "GET",
                dashboardKey,
                null
        );
        Assertions.assertEquals(200, meViewer.statusCode());
        Map<?, ?> meData = GSON.fromJson(meViewer.body(), Map.class);
        Assertions.assertEquals("VIEWER", meData.get("role"));

        HttpResponse<String> rotateAsViewer = sendJson(
                baseUrl + "/api/v1/auth/rotate",
                "POST",
                dashboardKey,
                "{\"keyName\":\"dashboard\"}"
        );
        Assertions.assertEquals(403, rotateAsViewer.statusCode());
        Map<?, ?> viewerError = GSON.fromJson(rotateAsViewer.body(), Map.class);
        Assertions.assertEquals(false, viewerError.get("success"));
        Assertions.assertTrue(viewerError.containsKey("requestId"));

        HttpResponse<String> rotateAsAdmin = sendJson(
                baseUrl + "/api/v1/auth/rotate",
                "POST",
                adminKey,
                "{\"keyName\":\"dashboard\"}"
        );
        Assertions.assertEquals(200, rotateAsAdmin.statusCode());
        Map<?, ?> rotateData = GSON.fromJson(rotateAsAdmin.body(), Map.class);
        Assertions.assertTrue(rotateData.containsKey("newKey"));
    }

    @Test
    void readinessAndInvalidJsonErrorsAreStructured() throws Exception {
        String adminKey = keyByName("admin");

        HttpResponse<String> readiness = sendJson(
                baseUrl + "/api/v1/readiness",
                "GET",
                adminKey,
                null
        );
        Assertions.assertEquals(200, readiness.statusCode());
        Map<?, ?> readinessData = GSON.fromJson(readiness.body(), Map.class);
        Assertions.assertTrue(readinessData.containsKey("ready"));
        Assertions.assertTrue(readinessData.containsKey("components"));
        Assertions.assertTrue(readinessData.containsKey("apiRuntime"));

        HttpResponse<String> invalidJson = sendJson(
                baseUrl + "/api/v1/servers/start",
                "POST",
                adminKey,
                "{"
        );
        Assertions.assertEquals(400, invalidJson.statusCode());
        Map<?, ?> errorData = GSON.fromJson(invalidJson.body(), Map.class);
        Assertions.assertEquals(false, errorData.get("success"));
        Assertions.assertTrue(errorData.containsKey("requestId"));
    }

    @Test
    void operationsReportAndLogSearchAreAvailable() throws Exception {
        String adminKey = keyByName("admin");

        HttpResponse<String> doctor = sendJson(
                baseUrl + "/api/v1/system/doctor",
                "GET",
                adminKey,
                null
        );
        Assertions.assertEquals(200, doctor.statusCode());
        Map<?, ?> doctorData = GSON.fromJson(doctor.body(), Map.class);
        Assertions.assertTrue(doctorData.containsKey("summary"));
        Assertions.assertTrue(doctorData.containsKey("findings"));

        HttpResponse<String> report = sendJson(
                baseUrl + "/api/v1/system/report",
                "GET",
                adminKey,
                null
        );
        Assertions.assertEquals(200, report.statusCode());
        Map<?, ?> reportData = GSON.fromJson(report.body(), Map.class);
        Assertions.assertTrue(reportData.containsKey("health"));
        Assertions.assertTrue(reportData.containsKey("readiness"));
        Assertions.assertTrue(reportData.containsKey("apiRuntime"));
        Assertions.assertTrue(reportData.containsKey("doctor"));
        Assertions.assertTrue(reportData.containsKey("logStats"));

        HttpResponse<String> search = sendJson(
                baseUrl + "/api/v1/logs/search?query=API&level=all&limit=25",
                "GET",
                adminKey,
                null
        );
        Assertions.assertEquals(200, search.statusCode());
        Map<?, ?> searchData = GSON.fromJson(search.body(), Map.class);
        Assertions.assertTrue(searchData.containsKey("lines"));
        Assertions.assertTrue(searchData.containsKey("count"));
    }

    @Test
    void platformOpsEndpointsExposeSafeSnapshots() throws Exception {
        String adminKey = keyByName("admin");

        HttpResponse<String> events = sendJson(baseUrl + "/api/v1/events/recent?limit=20", "GET", adminKey, null);
        Assertions.assertEquals(200, events.statusCode());
        Assertions.assertTrue(GSON.fromJson(events.body(), Map.class).containsKey("events"));

        HttpResponse<String> lifecycle = sendJson(baseUrl + "/api/v1/lifecycle?limit=20", "GET", adminKey, null);
        Assertions.assertEquals(200, lifecycle.statusCode());
        Assertions.assertTrue(GSON.fromJson(lifecycle.body(), Map.class).containsKey("transitions"));

        HttpResponse<String> incidents = sendJson(baseUrl + "/api/v1/incidents?limit=20", "GET", adminKey, null);
        Assertions.assertEquals(200, incidents.statusCode());
        Assertions.assertTrue(GSON.fromJson(incidents.body(), Map.class).containsKey("incidents"));

        HttpRequest openApiRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/openapi.yml"))
                .GET()
                .build();
        HttpResponse<String> openApi = HTTP.send(openApiRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assertions.assertEquals(200, openApi.statusCode());
        Assertions.assertTrue(openApi.body().contains("openapi: 3.0.3"));
        Assertions.assertTrue(openApi.body().contains("/system/doctor:"));
    }

    private static HttpResponse<String> sendJson(String url, String method, String apiKey, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .header("X-API-Key", apiKey);

        if ("POST".equalsIgnoreCase(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }

        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static String keyByName(String keyName) throws Exception {
        Field f = CloudHttpServer.class.getDeclaredField("apiKeys");
        f.setAccessible(true);
        Map<String, String> keys = (Map<String, String>) f.get(server);
        return keys.get(keyName);
    }

    private static void waitForServerReady() throws Exception {
        Exception last = null;
        for (int i = 0; i < 40; i++) {
            try {
                HttpRequest ping = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> res = HTTP.send(ping, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() == 200) {
                    return;
                }
            } catch (Exception ex) {
                last = ex;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Server wurde nicht rechtzeitig erreichbar: " + baseUrl, last);
    }
}
