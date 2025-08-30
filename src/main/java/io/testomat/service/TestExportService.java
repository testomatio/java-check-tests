package io.testomat.service;

import com.github.javaparser.ast.CompilationUnit;
import io.testomat.client.CliClient;
import io.testomat.client.TestomatHttpClient;
import io.testomat.exception.CliException;
import io.testomat.model.TestCase;
import io.testomat.progressbar.LoadingSpinner;
import io.testomat.progressbar.ProgressBar;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestExportService {
    private static final Logger log = LoggerFactory.getLogger(TestExportService.class);

    private final JavaFileParser fileParser;
    private final TestMethodExtractor extractor;
    private final TestFrameworkDetector detector;
    private final JsonBuilder jsonBuilder;
    private final TestomatHttpClient httpClient;
    private final LoadingSpinner spinner;

    public TestExportService() {
        this.fileParser = new JavaFileParser();
        this.extractor = new TestMethodExtractor();
        this.detector = new TestFrameworkDetector();
        this.jsonBuilder = new JsonBuilder();
        this.httpClient = new CliClient();
        this.spinner = new LoadingSpinner("Sending test data to server...");
    }

    public TestExportService(JavaFileParser fileParser, TestMethodExtractor extractor,
                             TestFrameworkDetector detector, JsonBuilder jsonBuilder,
                             TestomatHttpClient httpClient, LoadingSpinner spinner) {
        this.fileParser = fileParser;
        this.extractor = extractor;
        this.detector = detector;
        this.jsonBuilder = jsonBuilder;
        this.httpClient = httpClient;
        this.spinner = spinner;
    }

    public int processTestFilesWithProgress(List<File> testFiles, String apiKey,
                                            String serverUrl, boolean dryRun,
                                            boolean verbose, ProgressBar progressBar) {
        ProcessingResult result = processAllFiles(testFiles, verbose, progressBar);

        return handleProcessingResult(result.allTestCases, result.primaryFramework,
                apiKey, serverUrl, dryRun);
    }

    private List<TestCase> collectTestCasesFromFile(File file) {
        CompilationUnit compilationUnit = fileParser.parseFile(file.getAbsolutePath());
        if (compilationUnit == null) {
            return new ArrayList<>();
        }

        String framework = detector.detectFramework(compilationUnit);
        if (framework == null) {
            return new ArrayList<>();
        }

        List<TestCase> testCases = extractor.extractTestCases(
                compilationUnit, file.getAbsolutePath(), framework);

        return testCases.isEmpty() ? new ArrayList<>() : testCases;
    }

    private int exportAllTestCases(List<TestCase> allTestCases, String framework,
                                   String apiKey, String serverUrl) {
        validateExportConfig(serverUrl);

        String requestBody = jsonBuilder.buildRequestBody(allTestCases, framework);
        String requestUrl = serverUrl + "/api/load?api_key=" + apiKey;

        spinner.start();

        try {
            httpClient.sendPostRequest(requestUrl, requestBody);
        } catch (Exception e) {
            throw new CliException("Error while executing request", e);
        }

        spinner.stopWithMessage("Successfully exported " + allTestCases.size()
                + " test methods");

        return allTestCases.size();
    }

    private void printAllTestCases(List<TestCase> testCases) {
        log.info("All test methods found:");
        for (TestCase testCase : testCases) {
            log.info("  - {} [{}] ({})", testCase.getName(),
                    String.join(", ", testCase.getLabels()), testCase.getFile());
        }
    }

    private void validateExportConfig(String serverUrl) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("TESTOMATIO_URL is required for actual execution");
        }
    }

    private ProcessingResult processAllFiles(List<File> testFiles, boolean verbose,
                                             ProgressBar progressBar) {
        List<TestCase> allTestCases = new ArrayList<>();
        String primaryFramework = null;
        int processedFilesCount = 0;

        for (File testFile : testFiles) {
            try {
                List<TestCase> testCases = collectTestCasesFromFile(testFile);
                if (!testCases.isEmpty()) {
                    allTestCases.addAll(testCases);
                    if (primaryFramework == null) {
                        primaryFramework = detectFrameworkFromFile(testFile);
                    }
                }
            } catch (Exception e) {
                if (verbose) {
                    throw new CliException("Error processing file " + testFile.getName(), e);
                }
            } finally {
                processedFilesCount++;
                if (progressBar != null) {
                    progressBar.update(processedFilesCount);
                }
            }
        }

        if (progressBar != null) {
            progressBar.finish();
        }

        return new ProcessingResult(allTestCases, primaryFramework);
    }

    private int handleProcessingResult(List<TestCase> allTestCases, String primaryFramework,
                                       String apiKey, String serverUrl, boolean dryRun) {
        if (allTestCases.isEmpty()) {
            log.info("No test methods found across all files");
            return 0;
        }

        log.info("Found {} total test methods", allTestCases.size());

        if (dryRun) {
            printAllTestCases(allTestCases);
            return allTestCases.size();
        } else {
            return exportAllTestCases(allTestCases, primaryFramework, apiKey, serverUrl);
        }
    }

    private String detectFrameworkFromFile(File file) {
        CompilationUnit compilationUnit = fileParser.parseFile(file.getAbsolutePath());
        if (compilationUnit == null) {
            return null;
        }
        return detector.detectFramework(compilationUnit);
    }

    private static class ProcessingResult {
        private final List<TestCase> allTestCases;
        private final String primaryFramework;

        ProcessingResult(List<TestCase> allTestCases, String primaryFramework) {
            this.allTestCases = allTestCases;
            this.primaryFramework = primaryFramework;
        }
    }
}
