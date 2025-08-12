package io.testomat.service;

import io.testomat.model.TestCase;
import java.util.List;

public class JsonBuilder {

    public String buildRequestBody(List<TestCase> testCases, String framework) {
        StringBuilder json = new StringBuilder();

        json.append("{\n")
                .append("  \"framework\": \"").append(framework != null ? framework : "junit")
                .append("\",\n")
                .append("  \"language\": \"java\",\n")
                .append("  \"noempty\": true,\n")
                .append("  \"no-detach\": true,\n")
                .append("  \"structure\": true,\n")
                .append("  \"sync\": true,\n")
                .append("  \"tests\": [\n");

        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);

            json.append("    {\n")
                    .append("      \"name\": \"").append(escapeJson(testCase.getName()))
                    .append("\",\n")
                    .append("      \"suites\": ").append(formatStringArray(testCase.getSuites()))
                    .append(",\n")
                    .append("      \"code\": \"").append(escapeJson(testCase.getCode()))
                    .append("\",\n")
                    .append("      \"file\": \"").append(escapeJson(testCase.getFile()))
                    .append("\",\n")
                    .append("      \"skipped\": ").append(testCase.isSkipped()).append(",\n")
                    .append("      \"labels\": ").append(formatStringArray(testCase.getLabels()))
                    .append("\n")
                    .append("    }");

            if (i < testCases.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ]\n").append("}");

        return json.toString();
    }

    private String formatStringArray(List<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < strings.size(); i++) {
            sb.append("\"").append(escapeJson(strings.get(i))).append("\"");
            if (i < strings.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.append("]").toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\u0000", "\\u0000")
                .replace("\u0001", "\\u0001")
                .replace("\u0002", "\\u0002")
                .replace("\u0003", "\\u0003")
                .replace("\u0004", "\\u0004")
                .replace("\u0005", "\\u0005")
                .replace("\u0006", "\\u0006")
                .replace("\u0007", "\\u0007");
    }
}
