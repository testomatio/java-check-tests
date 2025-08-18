package io.testomat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.testomat.model.TestCase;
import java.util.List;

public class JsonBuilder {
    
    private static final String DEFAULT_FRAMEWORK = "junit";
    private static final String LANGUAGE = "java";
    private final ObjectMapper objectMapper;

    public JsonBuilder() {
        this.objectMapper = new ObjectMapper();
    }

    public String buildRequestBody(List<TestCase> testCases, String framework) {
        try {
            ObjectNode rootNode = objectMapper.createObjectNode();
            
            rootNode.put("framework", framework != null ? framework : DEFAULT_FRAMEWORK);
            rootNode.put("language", LANGUAGE);
            rootNode.put("noempty", true);
            rootNode.put("no-detach", true);
            rootNode.put("structure", true);
            rootNode.put("sync", true);
            
            ArrayNode testsArray = objectMapper.createArrayNode();
            for (TestCase testCase : testCases) {
                testsArray.add(createTestCaseNode(testCase));
            }
            rootNode.set("tests", testsArray);
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build JSON request body", e);
        }
    }
    
    private ObjectNode createTestCaseNode(TestCase testCase) {
        ObjectNode testCaseNode = objectMapper.createObjectNode();
        
        testCaseNode.put("name", testCase.getName());
        testCaseNode.set("suites", createStringArray(testCase.getSuites()));
        testCaseNode.put("code", testCase.getCode());
        testCaseNode.put("file", testCase.getFile());
        testCaseNode.put("skipped", testCase.isSkipped());
        testCaseNode.set("labels", createStringArray(testCase.getLabels()));
        
        return testCaseNode;
    }
    
    private ArrayNode createStringArray(List<String> strings) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        if (strings != null) {
            for (String string : strings) {
                arrayNode.add(string);
            }
        }
        return arrayNode;
    }
}
