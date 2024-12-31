/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 31.12.2024 um 02:37
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.libs
 */

package de.kallifabio.cloudsystem.libs;

import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;

public class CloudHttpClient {

    private static final String BASE_URL = "https://kallifabiocloud-backend.onrender.com";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();

    // GET-Anfrage zum Abrufen von SignLayout
    public String fetchSignLayout() {
        String url = BASE_URL + "/signlayout";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Failed to fetch SignLayout: " + response.code());
                return null;
            }
            return response.body().string(); // YAML-String zurückgeben
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // POST-Anfrage zum Aktualisieren der Signs
    public boolean updateSigns(String yamlContent) {
        String url = BASE_URL + "/signs";
        RequestBody body = RequestBody.create(
                yamlContent,
                MediaType.parse("application/yaml")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Failed to update signs: " + response.code());
                return false;
            }
            System.out.println("Signs updated successfully!");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
