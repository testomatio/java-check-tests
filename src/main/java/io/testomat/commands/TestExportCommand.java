package io.testomat.commands;

import com.github.javaparser.ast.CompilationUnit;
import io.testomat.client.CliClient;
import io.testomat.client.TestomatHttpClient;
import io.testomat.exception.CliException;
import io.testomat.service.JavaFileParser;
import io.testomat.service.JsonBuilder;
import io.testomat.service.TestCase;
import io.testomat.service.TestFileScanner;
import io.testomat.service.TestFrameworkDetector;
import io.testomat.service.TestMethodExtractor;
import java.io.File;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "export",
        description = "Export JUnit and TestNG test methods to testomat.io",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class TestExportCommand implements Callable<Integer> {

    private static final String API_URL = "/api/load?api_key=";
    private static final String CURRENT_DIRECTORY = ".";
    private static final int SUCCESS_EXIT_CODE = 0;
    private static final int ERROR_EXIT_CODE = 1;

    @Option(names = {"-d", "--directory"},
            description = "Directory to scan for test files (default: current directory)")
    private File directory = new File(CURRENT_DIRECTORY);

    @Option(names = {"-key", "--apikey"},
            description = "API key for testomat.io",
            defaultValue = "${env:TESTOMATIO}")
    private String apiKey;

    @Option(names = "--url",
            description = "Testomat server URL",
            defaultValue = "${env:TESTOMATIO_URL}")
    private String serverUrl;

    @Option(names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose = false;

    @Option(names = {"--dry-run"},
            description = "Show what would be exported without sending")
    private boolean dryRun = false;

    @Override
    public Integer call() throws Exception {
        try {
            boolean noDryRunFlag = !dryRun;
            if (noDryRunFlag && (apiKey == null || apiKey.trim().isEmpty())) {
                log("TESTOMATIO API key not provided, running in dry-run mode");
                dryRun = true;
            }

            if (verbose) {
                logPlatformInfo();
            }

            log("Starting test export from directory: " + directory.getAbsolutePath());

            if (!validateDirectory()) {
                return ERROR_EXIT_CODE;
            }

            TestFileScanner scanner = new TestFileScanner();
            List<File> testFiles = scanner.findTestFiles(directory);
            log("Found " + testFiles.size() + " test files");

            if (testFiles.isEmpty()) {
                System.out.println("No test files found!");
                return SUCCESS_EXIT_CODE;
            }

            int totalExported = processTestFiles(testFiles);

            if (dryRun) {
                System.out.println("\nDry run completed. No data was sent to server.");
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    System.out.println(
                            "Run the same command with apikey and url provided to execute.");
                }
            } else {
                System.out.println("\n✓ Export completed! Total methods exported: "
                        + totalExported);
            }

            return SUCCESS_EXIT_CODE;

        } catch (Exception e) {
            System.err.println("Export failed: " + e.getMessage());
            if (verbose) {
                throw new CliException("Export failed: " + e.getMessage());
            }
            return ERROR_EXIT_CODE;
        }
    }

    private boolean validateDirectory() {
        if (!directory.exists()) {
            System.err.println("Error: Directory does not exist: " + directory.getAbsolutePath());
            return false;
        }

        if (!directory.isDirectory()) {
            System.err.println("Error: Path is not directory: " + directory.getAbsolutePath());
            return false;
        }

        if (!directory.canRead()) {
            System.err.println("Error: Cannot read directory: " + directory.getAbsolutePath());
            return false;
        }

        return true;
    }

    private int processTestFiles(List<File> testFiles) {
        JavaFileParser fileParser = new JavaFileParser();
        TestMethodExtractor extractor = new TestMethodExtractor();
        TestFrameworkDetector detector = new TestFrameworkDetector();
        JsonBuilder jsonBuilder = new JsonBuilder();
        TestomatHttpClient httpClient = new CliClient();

        int totalExported = 0;

        for (File testFile : testFiles) {
            try {
                log("Processing: " + testFile.getName());

                CompilationUnit compilationUnit = fileParser.parseFile(testFile.getAbsolutePath());
                if (compilationUnit == null) {
                    log("  Skipped: Could not parse file");
                    continue;
                }

                String framework = detector.detectFramework(compilationUnit);
                if (framework == null) {
                    log("  Skipped: No test framework detected");
                    continue;
                }

                log("  Framework: " + framework);

                List<TestCase> testCases = extractor.extractTestCases(
                        compilationUnit,
                        testFile.getAbsolutePath(),
                        framework
                );

                if (testCases.isEmpty()) {
                    log("  Skipped: No test methods found");
                    continue;
                }

                log("  Found " + testCases.size() + " test methods");

                if (dryRun) {
                    printTestCases(testCases);
                } else {
                    if (serverUrl == null || serverUrl.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                "TESTOMATIO_URL is required for actual execution");
                    }

                    String requestBody = jsonBuilder.buildRequestBody(testCases, framework);
                    String requestUrl = serverUrl + API_URL + apiKey;
                    httpClient.sendPostRequest(requestUrl, requestBody);
                    totalExported += testCases.size();
                    log("  ✓ Exported " + testCases.size() + " test methods");
                }

            } catch (Exception e) {
                System.err.println("Error processing "
                        + testFile.getName()
                        + ": " + e.getMessage());
                if (verbose) {
                    e.printStackTrace();
                }
            }
        }

        return totalExported;
    }

    private void logPlatformInfo() {
        final String os = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        final String osArch = System.getProperty("os.arch");
        final String javaVersion = System.getProperty("java.version");
        final String javaVendor = System.getProperty("java.vendor");
        final String userDir = System.getProperty("user.dir");
        final String fileSeparator = FileSystems.getDefault().getSeparator();

        log("=== Platform Information ===");
        log("OS: " + os + " " + osVersion + " (" + osArch + ")");
        log("Java: " + javaVersion + " (" + javaVendor + ")");
        log("Working directory: " + userDir);
        log("File separator: '" + fileSeparator + "'");
        log("===========================");
    }

    private void log(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }

    private void printTestCases(List<TestCase> testCases) {
        for (TestCase testCase : testCases) {
            System.out.println("    - " + testCase.getName()
                    + " [" + String.join(", ", testCase.getLabels()) + "]");
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TestExportCommand()).execute(args);
        System.exit(exitCode);
    }
}
