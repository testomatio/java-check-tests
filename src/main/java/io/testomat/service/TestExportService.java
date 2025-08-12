package io.testomat.service;

import com.github.javaparser.ast.CompilationUnit;
import io.testomat.client.TestomatHttpClient;
import io.testomat.model.TestCase;
import java.io.File;
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
        int totalExported = 0;

        for (File testFile : testFiles) {
            try {
                int exported = processTestFile(testFile, config);
                totalExported += exported;
            } catch (Exception e) {
                handleFileError(testFile, e, config.isVerbose());
            }
        }

        return new ExportResult(totalExported);
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
