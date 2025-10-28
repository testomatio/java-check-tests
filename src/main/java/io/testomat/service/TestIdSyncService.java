package io.testomat.service;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.testomat.client.TestomatHttpClient;
import io.testomat.progressbar.LoadingSpinner;
import io.testomat.progressbar.ProgressBar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestIdSyncService {

    private static final int PATH_INDEX = 0;
    private static final int CLASS_NAME_INDEX = 1;
    private static final int METHOD_NAME_INDEX = 2;
    private static final int EXPECTED_PARTS_COUNT = 3;
    private static final String SPLIT_DELIMITER = "#";
    private static final String TEST_ID_IMPORT = "io.testomat.core.annotation.TestId";

    private final TestomatHttpClient httpClient;
    private final ResponseParser responseParser;
    private final TestIdAnnotationManager annotationManager;
    private final MinimalFileModificationService fileModificationService;

    public TestIdSyncService(TestomatHttpClient httpClient, ResponseParser responseParser,
                             TestIdAnnotationManager annotationManager) {
        this.httpClient = httpClient;
        this.responseParser = responseParser;
        this.annotationManager = annotationManager;
        this.fileModificationService = new MinimalFileModificationService();
    }

    public SyncResult syncTestIds(String apiKey, String serverUrl,
                                  List<CompilationUnit> compilationUnits) {
        return syncTestIds(apiKey, serverUrl, compilationUnits, false);
    }

    public SyncResult syncTestIds(String apiKey, String serverUrl,
                                  List<CompilationUnit> compilationUnits, boolean verbose) {
        return syncTestIds(apiKey, serverUrl, compilationUnits, verbose, null);
    }

    public SyncResult syncTestIds(String apiKey, String serverUrl,
                                  List<CompilationUnit> compilationUnits, boolean verbose,
                                  ProgressBar progressBar) {
        LoadingSpinner spinner = new LoadingSpinner("Fetching test data from server...");
        spinner.start();

        String response = httpClient.sendGetRequest(apiKey, serverUrl);
        Map<String, String> testsMap = responseParser.parseTestsFromResponse(response);

        spinner.stopWithMessage("Received test data from server");

        System.out.println("Received " + testsMap.size() + " test entries from API");
        if (verbose) {
            System.out.println("Processing each test entry for annotation...");
        }

        if (progressBar != null && testsMap.size() != progressBar.getTotal()) {
            progressBar = new ProgressBar(testsMap.size(), "Processing test IDs");
        }

        // Track file modifications
        Map<CompilationUnit, MinimalFileModificationService.FileModification> modifications =
                new HashMap<>();

        int processedCount = processTestMethods(compilationUnits, testsMap, modifications,
                verbose, progressBar);
        int modifiedFilesCount = applyFileModifications(modifications);

        return new SyncResult(processedCount, modifiedFilesCount);
    }

    private int processTestMethods(
            List<CompilationUnit> compilationUnits,
            Map<String, String> testsMap,
            Map<CompilationUnit, MinimalFileModificationService.FileModification> modifications,
            boolean verbose,
            ProgressBar progressBar) {
        int processedCount = 0;
        int skippedCount = 0;
        int currentEntry = 0;

        for (Map.Entry<String, String> testEntry : testsMap.entrySet()) {
            currentEntry++;
            String testKey = testEntry.getKey();
            String testId = testEntry.getValue();

            if (verbose) {
                System.out.println("Processing test key: " + testKey + " -> " + testId);
            }

            TestIdAnnotationManager.TestMethodInfo methodInfo = parseTestKey(testKey, verbose);
            if (methodInfo == null) {
                skippedCount++;
                if (verbose) {
                    System.out.println("  Skipped: Invalid test key format");
                }
                continue;
            }

            Optional<MethodDeclaration> methodOptional = annotationManager
                    .findMethodInCompilationUnits(compilationUnits, methodInfo, verbose);

            if (methodOptional.isPresent()) {
                MethodDeclaration method = methodOptional.get();
                CompilationUnit compilationUnit = method.findCompilationUnit().orElse(null);

                if (compilationUnit != null) {
                    // Check if we can use minimal modification (has storage and position)
                    boolean canUseMinimalMod = compilationUnit.getStorage().isPresent()
                            && method.getBegin().isPresent();

                    if (canUseMinimalMod) {
                        // Track modification for later application
                        MinimalFileModificationService.FileModification modification =
                                modifications.computeIfAbsent(compilationUnit,
                                        MinimalFileModificationService.FileModification::new);

                        modification.addMethodAnnotation(method, testId);

                        // Check if import is needed
                        boolean hasImport = compilationUnit.getImports().stream()
                                .anyMatch(imp -> TEST_ID_IMPORT.equals(imp.getNameAsString()));
                        if (!hasImport) {
                            modification.setNeedsImport(true);
                        }
                    } else {
                        // Fallback: direct AST modification for tests/in-memory CUs
                        annotationManager.addTestIdAnnotationToMethod(method, testId);
                        annotationManager.ensureTestIdImportExists(compilationUnit);
                    }

                    processedCount++;
                    if (verbose) {
                        System.out.println("  ✓ Added TestId annotation to method: "
                                + methodInfo.getMethodName());
                    }
                } else {
                    skippedCount++;
                    if (verbose) {
                        System.out.println("  Skipped: Method has no compilation unit");
                    }
                }
            } else {
                skippedCount++;
                if (verbose) {
                    System.out.println("  Skipped: Method not found in compilation units");
                }
            }

            if (progressBar != null) {
                progressBar.update(currentEntry);
            }
        }

        if (progressBar != null) {
            progressBar.finish();
        }

        if (skippedCount > 0) {
            System.out.println("Skipped " + skippedCount + " test methods (not found or invalid)");
        }

        return processedCount;
    }

    private TestIdAnnotationManager.TestMethodInfo parseTestKey(String testKey) {
        return parseTestKey(testKey, false);
    }

    private TestIdAnnotationManager.TestMethodInfo parseTestKey(String testKey, boolean verbose) {
        String[] parts = testKey.split(SPLIT_DELIMITER);

        if (parts.length != EXPECTED_PARTS_COUNT) {
            if (verbose) {
                System.out.println("  Invalid key format - expected 3 parts separated by '#', got "
                        + parts.length + " parts: " + testKey);
            }
            return null;
        }

        String filePath = parts[PATH_INDEX].trim();
        String className = parts[CLASS_NAME_INDEX].trim();
        String methodName = parts[METHOD_NAME_INDEX].trim();

        if (verbose) {
            System.out.println("  Parsed - File: " + filePath + ", Class: " + className
                    + ", Method: " + methodName);
        }

        return new TestIdAnnotationManager.TestMethodInfo(filePath, className, methodName);
    }

    private int applyFileModifications(Map<CompilationUnit,
            MinimalFileModificationService.FileModification> modifications) {
        int modifiedCount = 0;
        for (MinimalFileModificationService.FileModification modification
                : modifications.values()) {
            if (modification.hasModifications()) {
                fileModificationService.applyModifications(modification);
                modifiedCount++;
            }
        }
        return modifiedCount;
    }

    public static class SyncResult {
        private final int processedCount;
        private final int modifiedFilesCount;

        public SyncResult(int processedCount) {
            this(processedCount, 0);
        }

        public SyncResult(int processedCount, int modifiedFilesCount) {
            this.processedCount = processedCount;
            this.modifiedFilesCount = modifiedFilesCount;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public int getModifiedFilesCount() {
            return modifiedFilesCount;
        }
    }
}
