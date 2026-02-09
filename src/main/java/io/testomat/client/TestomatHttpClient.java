package io.testomat.client;

public interface TestomatHttpClient {
    String sendGetRequest(String apiKey, String serverUrl);

    void sendPostRequest(String url, String jsonBody);
}
