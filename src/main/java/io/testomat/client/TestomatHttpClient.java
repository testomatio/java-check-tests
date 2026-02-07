package io.testomat.client;

import java.util.stream.Stream;

public interface TestomatHttpClient {
    String sendGetRequest(String apiKey, String serverUrl);

    void sendPostRequest(String url, Stream<String> batchJsonBodies);
}
