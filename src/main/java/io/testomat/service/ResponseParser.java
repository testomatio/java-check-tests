package io.testomat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testomat.exception.CliException;
import java.util.Map;
import java.util.stream.Collectors;

public class ResponseParser {

    private static final String TESTS_FIELD = "tests";
    private static final String JAVA_EXTENSION = ".java";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, String> parseTestsFromResponse(String response) {
        JsonNode rootNode = parseJsonResponse(response);
        JsonNode testsNode = extractTestsNode(rootNode);
        Map<String, String> testsMap = convertTestsNodeToMap(testsNode);

        validateTestsMap(testsMap);

        return filterJavaTests(testsMap);
    }

    private JsonNode parseJsonResponse(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (JsonProcessingException e) {
            throw new CliException(e.getMessage(), e.getCause());
        }
    }

    private JsonNode extractTestsNode(JsonNode rootNode) {
        JsonNode testsNode = rootNode.get(TESTS_FIELD);

        if (testsNode == null) {
            throw new CliException("Response does not contain '" + TESTS_FIELD + "' field");
        }

        if (!testsNode.isObject()) {
            throw new CliException("'" + TESTS_FIELD + "' field is not a JSON object");
        }

        return testsNode;
    }

    private Map<String, String> convertTestsNodeToMap(JsonNode testsNode) {
        try {
            return objectMapper.convertValue(testsNode, new TypeReference<Map<String, String>>() {
            });
        } catch (IllegalArgumentException e) {
            throw new CliException("Failed to convert tests data: " + e.getMessage(), e);
        }
    }

    private void validateTestsMap(Map<String, String> testsMap) {
        if (testsMap == null || testsMap.isEmpty()) {
            throw new CliException("No tests found in response");
        }
    }

    private Map<String, String> filterJavaTests(Map<String, String> testsMap) {
        return testsMap.entrySet().stream()
                .filter(entry -> entry.getKey().contains(JAVA_EXTENSION))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
