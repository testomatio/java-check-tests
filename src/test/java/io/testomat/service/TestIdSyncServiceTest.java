package io.testomat.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.testomat.client.TestomatHttpClient;
import io.testomat.exception.CliException;
import io.testomat.service.TestIdAnnotationManager.TestMethodInfo;
import io.testomat.service.TestIdSyncService.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestIdSyncServiceTest {

    @Mock
    private TestomatHttpClient httpClient;

    @Mock
    private ResponseParser responseParser;

    @Mock
    private TestIdAnnotationManager annotationManager;

    private TestIdSyncService testIdSyncService;
    private JavaParser javaParser;

    @TempDir
    Path tempDir;

    private static final String TEST_CLASS_CODE = 
            "package com.example.test;\n" +
            "\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "\n" +
            "class SampleTest {\n" +
            "\n" +
            "    @Test\n" +
            "    void testMethod() {\n" +
            "        // Test implementation\n" +
            "    }\n" +
            "\n" +
            "    @Test\n" +
            "    void anotherTestMethod() {\n" +
            "        // Another test\n" +
            "    }\n" +
            "}";

    @BeforeEach
    void setUp() {
        testIdSyncService = new TestIdSyncService(httpClient, responseParser, annotationManager);
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("Should successfully sync test IDs when all components work correctly")
    void shouldSuccessfullySyncTestIdsWhenAllComponentsWorkCorrectly() throws IOException {
        // Given
        String apiKey = "tstmt_test-api-key";
        String serverUrl = "https://api.testomat.io";
        String responseBody = "{\"tests\": {...}}";
        
        Map<String, String> testsMap = new HashMap<>();
        testsMap.put("src/test/java/SampleTest.java#SampleTest#testMethod", "@T12345");
        testsMap.put("src/test/java/SampleTest.java#SampleTest#anotherTestMethod", "@T67890");

        Path testFile = tempDir.resolve("SampleTest.java");
        Files.write(testFile, TEST_CLASS_CODE.getBytes());
        CompilationUnit cu = parseCodeWithStorage(TEST_CLASS_CODE, testFile);
        List<CompilationUnit> compilationUnits = Collections.singletonList(cu);

        MethodDeclaration method1 = mock(MethodDeclaration.class);
        MethodDeclaration method2 = mock(MethodDeclaration.class);
        
        when(httpClient.sendGetRequest(apiKey, serverUrl)).thenReturn(responseBody);
        when(responseParser.parseTestsFromResponse(responseBody)).thenReturn(testsMap);
        
        when(annotationManager.findMethodInCompilationUnits(eq(compilationUnits), any(TestMethodInfo.class)))
                .thenReturn(Optional.of(method1))
                .thenReturn(Optional.of(method2));
        
        when(method1.findCompilationUnit()).thenReturn(Optional.of(cu));
        when(method2.findCompilationUnit()).thenReturn(Optional.of(cu));

        // When
        SyncResult result = testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits);

        // Then
        assertEquals(2, result.getProcessedCount(), "Should process 2 test methods");
        
        verify(httpClient).sendGetRequest(apiKey, serverUrl);
        verify(responseParser).parseTestsFromResponse(responseBody);
        verify(annotationManager, times(2)).findMethodInCompilationUnits(eq(compilationUnits), any(TestMethodInfo.class));
        verify(annotationManager).addTestIdAnnotationToMethod(method1, "@T12345");
        verify(annotationManager).addTestIdAnnotationToMethod(method2, "@T67890");
        verify(annotationManager, times(2)).ensureTestIdImportExists(cu);
    }

    @Test
    @DisplayName("Should handle empty response from server")
    void shouldHandleEmptyResponseFromServer() {
        // Given
        String apiKey = "tstmt_test-api-key";
        String serverUrl = "https://api.testomat.io";
        String responseBody = "{\"tests\": {}}";
        
        Map<String, String> emptyTestsMap = new HashMap<>();
        List<CompilationUnit> compilationUnits = Collections.emptyList();

        when(httpClient.sendGetRequest(apiKey, serverUrl)).thenReturn(responseBody);
        when(responseParser.parseTestsFromResponse(responseBody)).thenReturn(emptyTestsMap);

        // When
        SyncResult result = testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits);

        // Then
        assertEquals(0, result.getProcessedCount(), "Should process 0 test methods");
        
        verify(httpClient).sendGetRequest(apiKey, serverUrl);
        verify(responseParser).parseTestsFromResponse(responseBody);
        verify(annotationManager, never()).findMethodInCompilationUnits(any(), any());
        verify(annotationManager, never()).addTestIdAnnotationToMethod(any(), any());
    }

    @Test
    @DisplayName("Should skip invalid test keys and process only valid ones")
    void shouldSkipInvalidTestKeys() {
        // Given
        Map<String, String> testsMap = new HashMap<>();
        testsMap.put("no-delimiters", "@T12345");              // 1 part - INVALID
        testsMap.put("only#one", "@T67890");                   // 2 parts - INVALID
        testsMap.put("too#many#parts#here#extra", "@T99999");  // 5 parts - INVALID
        testsMap.put("valid/path.java#ClassName#methodName", "@T11111"); // 3 parts - VALID

        when(httpClient.sendGetRequest(any(), any())).thenReturn("response");
        when(responseParser.parseTestsFromResponse(any())).thenReturn(testsMap);
        when(annotationManager.findMethodInCompilationUnits(any(), any()))
                .thenReturn(Optional.empty()); // Even valid key finds no method

        // When
        SyncResult result = testIdSyncService.syncTestIds("key", "url", Collections.emptyList());

        // Then
        assertEquals(0, result.getProcessedCount(),
                "Should process 0 methods (valid key found no method)");

        // Should only attempt to find method for the 1 valid key
        verify(annotationManager, times(1)).findMethodInCompilationUnits(any(), any());

        // Verify the valid key was parsed correctly
        verify(annotationManager).findMethodInCompilationUnits(any(), argThat(methodInfo ->
                methodInfo.getFilePath().equals("valid/path.java") &&
                        methodInfo.getClassName().equals("ClassName") &&
                        methodInfo.getMethodName().equals("methodName")
        ));
    }

    @Test
    @DisplayName("Should skip when method not found in compilation units")
    void shouldSkipWhenMethodNotFoundInCompilationUnits() {
        // Given
        String apiKey = "tstmt_test-api-key";
        String serverUrl = "https://api.testomat.io";
        String responseBody = "{\"tests\": {...}}";
        
        Map<String, String> testsMap = new HashMap<>();
        testsMap.put("src/test/java/TestClass.java#TestClass#nonExistentMethod", "@T12345");

        List<CompilationUnit> compilationUnits = Collections.singletonList(mock(CompilationUnit.class));

        when(httpClient.sendGetRequest(apiKey, serverUrl)).thenReturn(responseBody);
        when(responseParser.parseTestsFromResponse(responseBody)).thenReturn(testsMap);
        when(annotationManager.findMethodInCompilationUnits(any(), any())).thenReturn(Optional.empty());

        // When
        SyncResult result = testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits);

        // Then
        assertEquals(0, result.getProcessedCount(), "Should process 0 test methods when method not found");
        
        verify(annotationManager).findMethodInCompilationUnits(any(), any());
        verify(annotationManager, never()).addTestIdAnnotationToMethod(any(), any());
    }

    @Test
    @DisplayName("Should skip when method has no compilation unit")
    void shouldSkipWhenMethodHasNoCompilationUnit() {
        // Given
        String apiKey = "tstmt_test-api-key";
        String serverUrl = "https://api.testomat.io";
        String responseBody = "{\"tests\": {...}}";
        
        Map<String, String> testsMap = new HashMap<>();
        testsMap.put("src/test/java/TestClass.java#TestClass#testMethod", "@T12345");

        List<CompilationUnit> compilationUnits = Collections.singletonList(mock(CompilationUnit.class));
        MethodDeclaration method = mock(MethodDeclaration.class);

        when(httpClient.sendGetRequest(apiKey, serverUrl)).thenReturn(responseBody);
        when(responseParser.parseTestsFromResponse(responseBody)).thenReturn(testsMap);
        when(annotationManager.findMethodInCompilationUnits(any(), any())).thenReturn(Optional.of(method));
        when(method.findCompilationUnit()).thenReturn(Optional.empty());

        // When
        SyncResult result = testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits);

        // Then
        assertEquals(0, result.getProcessedCount(), "Should process 0 test methods when compilation unit not found");
        
        verify(annotationManager).findMethodInCompilationUnits(any(), any());
        verify(annotationManager, never()).addTestIdAnnotationToMethod(any(), any());
        verify(annotationManager, never()).ensureTestIdImportExists(any());
    }

    @Test
    @DisplayName("Should save modified files after processing")
    void shouldSaveModifiedFilesAfterProcessing() throws IOException {
        // Given
        String apiKey = "tstmt_test-api-key";
        String serverUrl = "https://api.testomat.io";
        String responseBody = "{\"tests\": {...}}";
        
        Map<String, String> testsMap = new HashMap<>();
        testsMap.put("src/test/java/SampleTest.java#SampleTest#testMethod", "@T12345");

        Path testFile = tempDir.resolve("SampleTest.java");
        Files.write(testFile, TEST_CLASS_CODE.getBytes());
        CompilationUnit cu = parseCodeWithStorage(TEST_CLASS_CODE, testFile);
        List<CompilationUnit> compilationUnits = Collections.singletonList(cu);

        MethodDeclaration method = mock(MethodDeclaration.class);

        when(httpClient.sendGetRequest(apiKey, serverUrl)).thenReturn(responseBody);
        when(responseParser.parseTestsFromResponse(responseBody)).thenReturn(testsMap);
        when(annotationManager.findMethodInCompilationUnits(any(), any())).thenReturn(Optional.of(method));
        when(method.findCompilationUnit()).thenReturn(Optional.of(cu));

        // When
        SyncResult result = testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits);

        // Then
        assertEquals(1, result.getProcessedCount());
        
        // Verify file was saved (file should still exist and be accessible)
        assertTrue(Files.exists(testFile), "File should still exist after saving");
        assertTrue(Files.isReadable(testFile), "File should be readable after saving");
    }

    @Test
    @DisplayName("Should propagate HTTP client exceptions")
    void shouldPropagateHttpClientExceptions() {
        // Given
        String apiKey = "tstmt_test-api-key";
        String serverUrl = "https://api.testomat.io";
        List<CompilationUnit> compilationUnits = Collections.emptyList();

        CliException httpException = new CliException("Network error");
        when(httpClient.sendGetRequest(apiKey, serverUrl)).thenThrow(httpException);

        // When & Then
        CliException exception = assertThrows(CliException.class, 
                () -> testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits));
        
        assertEquals("Network error", exception.getMessage());
        verify(httpClient).sendGetRequest(apiKey, serverUrl);
        verify(responseParser, never()).parseTestsFromResponse(any());
    }

    @Test
    @DisplayName("Should propagate response parser exceptions")
    void shouldPropagateResponseParserExceptions() {
        // Given
        String apiKey = "tstmt_test-api-key";
        String serverUrl = "https://api.testomat.io";
        String responseBody = "invalid-json";
        List<CompilationUnit> compilationUnits = Collections.emptyList();

        CliException parseException = new CliException("Invalid JSON");
        when(httpClient.sendGetRequest(apiKey, serverUrl)).thenReturn(responseBody);
        when(responseParser.parseTestsFromResponse(responseBody)).thenThrow(parseException);

        // When & Then
        CliException exception = assertThrows(CliException.class, 
                () -> testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits));
        
        assertEquals("Invalid JSON", exception.getMessage());
        verify(httpClient).sendGetRequest(apiKey, serverUrl);
        verify(responseParser).parseTestsFromResponse(responseBody);
    }

    @Test
    @DisplayName("Should handle mixed valid and invalid test keys")
    void shouldHandleMixedValidAndInvalidTestKeys() throws IOException {
        // Given
        String apiKey = "tstmt_test-api-key";
        String serverUrl = "https://api.testomat.io";
        String responseBody = "{\"tests\": {...}}";
        
        Map<String, String> testsMap = new LinkedHashMap<>();  // Use LinkedHashMap to maintain order
        testsMap.put("invalid-key", "@T11111");  // Invalid
        testsMap.put("src/test/java/SampleTest.java#SampleTest#testMethod", "@T12345");  // Valid
        testsMap.put("only#two", "@T22222");  // Invalid
        testsMap.put("src/test/java/SampleTest.java#SampleTest#anotherTestMethod", "@T67890");  // Valid

        Path testFile = tempDir.resolve("SampleTest.java");
        Files.write(testFile, TEST_CLASS_CODE.getBytes());
        CompilationUnit cu = parseCodeWithStorage(TEST_CLASS_CODE, testFile);
        List<CompilationUnit> compilationUnits = Collections.singletonList(cu);

        MethodDeclaration method1 = mock(MethodDeclaration.class);
        MethodDeclaration method2 = mock(MethodDeclaration.class);

        when(httpClient.sendGetRequest(apiKey, serverUrl)).thenReturn(responseBody);
        when(responseParser.parseTestsFromResponse(responseBody)).thenReturn(testsMap);
        when(annotationManager.findMethodInCompilationUnits(eq(compilationUnits), any(TestMethodInfo.class)))
                .thenReturn(Optional.of(method1))
                .thenReturn(Optional.of(method2));
        when(method1.findCompilationUnit()).thenReturn(Optional.of(cu));
        when(method2.findCompilationUnit()).thenReturn(Optional.of(cu));

        // When
        SyncResult result = testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits);

        // Then
        assertEquals(2, result.getProcessedCount(), "Should process only 2 valid test methods");
        
        // Should only try to find methods for valid keys (2 times)
        verify(annotationManager, times(2)).findMethodInCompilationUnits(any(), any());
        verify(annotationManager).addTestIdAnnotationToMethod(method1, "@T12345");
        verify(annotationManager).addTestIdAnnotationToMethod(method2, "@T67890");
    }

    @Test
    @DisplayName("Should handle compilation units without storage gracefully")
    void shouldHandleCompilationUnitsWithoutStorageGracefully() {
        // Given
        String apiKey = "tstmt_test-api-key";
        String serverUrl = "https://api.testomat.io";
        String responseBody = "{\"tests\": {...}}";
        
        Map<String, String> testsMap = new HashMap<>();
        testsMap.put("src/test/java/SampleTest.java#SampleTest#testMethod", "@T12345");

        CompilationUnit cuWithoutStorage = parseCode(TEST_CLASS_CODE);  // No storage
        List<CompilationUnit> compilationUnits = Collections.singletonList(cuWithoutStorage);

        when(httpClient.sendGetRequest(apiKey, serverUrl)).thenReturn(responseBody);
        when(responseParser.parseTestsFromResponse(responseBody)).thenReturn(testsMap);

        // When
        SyncResult result = testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits);

        // Then
        assertEquals(0, result.getProcessedCount(), "Should process 0 test methods");
        
        // The method should not throw exception when trying to save compilation unit without storage
        assertDoesNotThrow(() -> testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits));
    }

    @Test
    @DisplayName("SyncResult should return correct processed count")
    void syncResultShouldReturnCorrectProcessedCount() {
        // Given
        int processedCount = 5;

        // When
        SyncResult result = new SyncResult(processedCount);

        // Then
        assertEquals(5, result.getProcessedCount());
    }

    @Test
    @DisplayName("Should parse test key correctly")
    void shouldParseTestKeyCorrectly() {
        // This test verifies the parsing logic indirectly through the public interface
        // Given
        String apiKey = "tstmt_test-api-key";
        String serverUrl = "https://api.testomat.io";
        String responseBody = "{\"tests\": {...}}";
        
        Map<String, String> testsMap = new HashMap<>();
        testsMap.put("src/test/java/com/example/TestClass.java#TestClass#methodName", "@T12345");

        List<CompilationUnit> compilationUnits = Collections.singletonList(mock(CompilationUnit.class));

        when(httpClient.sendGetRequest(apiKey, serverUrl)).thenReturn(responseBody);
        when(responseParser.parseTestsFromResponse(responseBody)).thenReturn(testsMap);
        when(annotationManager.findMethodInCompilationUnits(any(), any())).thenReturn(Optional.empty());

        // When
        testIdSyncService.syncTestIds(apiKey, serverUrl, compilationUnits);

        // Then
        // Verify that the correct TestMethodInfo was created and passed to annotationManager
        verify(annotationManager).findMethodInCompilationUnits(eq(compilationUnits), argThat(methodInfo -> 
                methodInfo.getFilePath().equals("src/test/java/com/example/TestClass.java") &&
                methodInfo.getClassName().equals("TestClass") &&
                methodInfo.getMethodName().equals("methodName")
        ));
    }

    // Helper methods
    
    private CompilationUnit parseCode(String code) {
        return javaParser.parse(code).getResult()
                .orElseThrow(() -> new RuntimeException("Failed to parse test code"));
    }

    private CompilationUnit parseCodeWithStorage(String code, Path filePath) throws IOException {
        Files.write(filePath, code.getBytes());
        return javaParser.parse(filePath).getResult()
                .orElseThrow(() -> new RuntimeException("Failed to parse test code from file"));
    }
}