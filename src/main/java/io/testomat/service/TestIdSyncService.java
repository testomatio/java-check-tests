package io.testomat.service;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.testomat.client.TestomatHttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestIdSyncService {

    private static final int PATH_INDEX = 0;
    private static final int CLASS_NAME_INDEX = 1;
    private static final int METHOD_NAME_INDEX = 2;
    private static final int EXPECTED_PARTS_COUNT = 3;
    private static final String SPLIT_DELIMITER = "#";

    private final TestomatHttpClient httpClient;
    private final ResponseParser responseParser;
    private final TestIdAnnotationManager annotationManager;

    public TestIdSyncService(TestomatHttpClient httpClient, ResponseParser responseParser,
                             TestIdAnnotationManager annotationManager) {
        this.httpClient = httpClient;
        this.responseParser = responseParser;
        this.annotationManager = annotationManager;
    }

    public SyncResult syncTestIds(String apiKey, String serverUrl,
                                  List<CompilationUnit> compilationUnits) {
        String response = httpClient.sendGetRequest(apiKey, serverUrl);
        Map<String, String> testsMap = responseParser.parseTestsFromResponse(response);

        int processedCount = processTestMethods(compilationUnits, testsMap);
        saveModifiedFiles(compilationUnits);

        return new SyncResult(processedCount);
    }

    private int processTestMethods(List<CompilationUnit> compilationUnits,
                                   Map<String, String> testsMap) {
        int processedCount = 0;

        for (Map.Entry<String, String> testEntry : testsMap.entrySet()) {
            String testKey = testEntry.getKey();
            String testId = testEntry.getValue();

            TestIdAnnotationManager.TestMethodInfo methodInfo = parseTestKey(testKey);
            if (methodInfo == null) {
                continue;
            }

            Optional<MethodDeclaration> methodOptional =
                    annotationManager.findMethodInCompilationUnits(compilationUnits, methodInfo);

            if (methodOptional.isPresent()) {
                MethodDeclaration method = methodOptional.get();
                CompilationUnit compilationUnit = method.findCompilationUnit().orElse(null);

                if (compilationUnit != null) {
                    annotationManager.addTestIdAnnotationToMethod(method, testId);
                    annotationManager.ensureTestIdImportExists(compilationUnit);
                    processedCount++;
                }
            }
        }

        return processedCount;
    }

    private TestIdAnnotationManager.TestMethodInfo parseTestKey(String testKey) {
        String[] parts = testKey.split(SPLIT_DELIMITER);

        if (parts.length != EXPECTED_PARTS_COUNT) {
            return null;
        }

        return new TestIdAnnotationManager.TestMethodInfo(
                parts[PATH_INDEX],
                parts[CLASS_NAME_INDEX],
                parts[METHOD_NAME_INDEX]
        );
    }

    private void saveModifiedFiles(List<CompilationUnit> compilationUnits) {
        compilationUnits.forEach(cu ->
                cu.getStorage().ifPresent(CompilationUnit.Storage::save)
        );
    }

    public static class SyncResult {
        private final int processedCount;

        public SyncResult(int processedCount) {
            this.processedCount = processedCount;
        }

        public int getProcessedCount() {
            return processedCount;
        }
    }
}
