package io.testomat.client;

import java.net.http.HttpResponse;

public interface TestomatHttpClient {
    String sendGetRequest(String apiKey, String serverUrl);

    HttpResponse<String> sendPostRequest(String url, String jsonBody);
}
