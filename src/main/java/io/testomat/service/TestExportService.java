package io.testomat.service;

import com.github.javaparser.ast.CompilationUnit;
import io.testomat.client.TestomatHttpClient;
import io.testomat.model.TestCase;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TestExportService {

    private final JavaFileParser fileParser;
    private final TestMethodExtractor extractor;
    private final TestFrameworkDetector detector;
    private final JsonBuilder jsonBuilder;
    private final TestomatHttpClient httpClient;
    private final VerboseLogger logger;

    public TestExportService(JavaFileParser fileParser, TestMethodExtractor extractor,
                             TestFrameworkDetector detector, JsonBuilder jsonBuilder,
                             TestomatHttpClient httpClient, VerboseLogger logger) {
        this.fileParser = fileParser;
        this.extractor = extractor;
        this.detector = detector;
        this.jsonBuilder = jsonBuilder;
        this.httpClient = httpClient;
        this.logger = logger;
    }

    public ExportResult processTestFiles(List<File> testFiles, ExportConfig config) {
        List<TestCase> allTestCases = new ArrayList<>();
        String primaryFramework = null;

        // First pass: collect all test cases from all files
        for (File testFile : testFiles) {
            try {
                ProcessFileResult result = collectTestCasesFromFile(testFile);
                if (result != null && !result.getTestCases().isEmpty()) {
                    allTestCases.addAll(result.getTestCases());
                    if (primaryFramework == null) {
                        primaryFramework = result.getFramework();
                    }
                }
            } catch (Exception e) {
                handleFileError(testFile, e, config.isVerbose());
            }
        }

        if (allTestCases.isEmpty()) {
            logger.log("No test methods found across all files");
            return new ExportResult(0);
        }

        logger.log("Found " + allTestCases.size() + " total test methods across all files");

        if (config.isDryRun()) {
            printAllTestCases(allTestCases);
            return new ExportResult(0);
        } else {
            return exportAllTestCases(allTestCases, primaryFramework, config);
        }
    }

    private int processTestFile(File testFile, ExportConfig config) {
        logger.log("Processing: " + testFile.getName());

        CompilationUnit compilationUnit = fileParser.parseFile(testFile.getAbsolutePath());
        if (compilationUnit == null) {
            logger.log("  Skipped: Could not parse file");
            return 0;
        }

        String framework = detector.detectFramework(compilationUnit);
        if (framework == null) {
            logger.log("  Skipped: No test framework detected");
            return 0;
        }

        logger.log("  Framework: " + framework);

        List<TestCase> testCases = extractor.extractTestCases(
                compilationUnit,
                testFile.getAbsolutePath(),
                framework
        );

        if (testCases.isEmpty()) {
            logger.log("  Skipped: No test methods found");
            return 0;
        }

        logger.log("  Found " + testCases.size() + " test methods");

        if (config.isDryRun()) {
            printTestCases(testCases);
            return 0;
        } else {
            return exportTestCases(testCases, framework, config);
        }
    }

    private ProcessFileResult collectTestCasesFromFile(File testFile) {
        logger.log("Processing: " + testFile.getName());

        CompilationUnit compilationUnit = fileParser.parseFile(testFile.getAbsolutePath());
        if (compilationUnit == null) {
            logger.log("  Skipped: Could not parse file");
            return null;
        }

        String framework = detector.detectFramework(compilationUnit);
        if (framework == null) {
            logger.log("  Skipped: No test framework detected");
            return null;
        }

        logger.log("  Framework: " + framework);

        List<TestCase> testCases = extractor.extractTestCases(
                compilationUnit,
                testFile.getAbsolutePath(),
                framework
        );

        if (testCases.isEmpty()) {
            logger.log("  Skipped: No test methods found");
            return null;
        }

        logger.log("  Found " + testCases.size() + " test methods");
        return new ProcessFileResult(testCases, framework);
    }

    private ExportResult exportAllTestCases(List<TestCase> allTestCases, String framework,
                                             ExportConfig config) {
        validateExportConfig(config);

        String requestBody = jsonBuilder.buildRequestBody(allTestCases, framework);
        String requestUrl = config.getServerUrl() + "/api/load?api_key=" + config.getApiKey();

        httpClient.sendPostRequest(requestUrl, requestBody);

        logger.log("✓ Exported " + allTestCases.size() + " test methods in single request");
        return new ExportResult(allTestCases.size());
    }

    private void printAllTestCases(List<TestCase> testCases) {
        System.out.println("All test methods found:");
        for (TestCase testCase : testCases) {
            System.out.println("  - " + testCase.getName()
                    + " [" + String.join(", ", testCase.getLabels()) + "] "
                    + "(" + testCase.getFile() + ")");
        }
    }

    private int exportTestCases(List<TestCase> testCases, String framework, ExportConfig config) {
        validateExportConfig(config);

        String requestBody = jsonBuilder.buildRequestBody(testCases, framework);
        String requestUrl = config.getServerUrl() + "/api/load?api_key=" + config.getApiKey();

        httpClient.sendPostRequest(requestUrl, requestBody);

        logger.log("  ✓ Exported " + testCases.size() + " test methods");
        return testCases.size();
    }

    private void validateExportConfig(ExportConfig config) {
        if (config.getServerUrl() == null || config.getServerUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("TESTOMATIO_URL is required for actual execution");
        }
    }

    private void printTestCases(List<TestCase> testCases) {
        for (TestCase testCase : testCases) {
            System.out.println("    - " + testCase.getName()
                    + " [" + String.join(", ", testCase.getLabels()) + "]");
        }
    }

    private void handleFileError(File testFile, Exception e, boolean verbose) {
        System.err.println("Error processing " + testFile.getName() + ": " + e.getMessage());
        if (verbose) {
            e.printStackTrace();
        }
    }

    private static class ProcessFileResult {
        private final List<TestCase> testCases;
        private final String framework;

        public ProcessFileResult(List<TestCase> testCases, String framework) {
            this.testCases = testCases;
            this.framework = framework;
        }

        public List<TestCase> getTestCases() {
            return testCases;
        }

        public String getFramework() {
            return framework;
        }
    }

    public static class ExportResult {
        private final int totalExported;

        public ExportResult(int totalExported) {
            this.totalExported = totalExported;
        }

        public int getTotalExported() {
            return totalExported;
        }
    }

    public static class ExportConfig {
        private final String apiKey;
        private final String serverUrl;
        private final boolean dryRun;
        private final boolean verbose;

        public ExportConfig(String apiKey, String serverUrl, boolean dryRun, boolean verbose) {
            this.apiKey = apiKey;
            this.serverUrl = serverUrl;
            this.dryRun = dryRun;
            this.verbose = verbose;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getServerUrl() {
            return serverUrl;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public boolean isVerbose() {
            return verbose;
        }
    }
}
