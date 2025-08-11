package io.testomat.commands;

import io.testomat.client.CliClient;
import io.testomat.service.DirectoryValidator;
import io.testomat.service.JavaFileParser;
import io.testomat.service.JsonBuilder;
import io.testomat.service.TestExportService;
import io.testomat.service.TestFileScanner;
import io.testomat.service.TestFrameworkDetector;
import io.testomat.service.TestMethodExtractor;
import io.testomat.service.VerboseLogger;
import java.io.File;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "import",
        description = "Imports JUnit and TestNG test methods to testomat.io",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class ImportCommand implements Callable<Integer> {

    private static final String CURRENT_DIRECTORY = ".";
    private static final int SUCCESS_EXIT_CODE = 0;
    private static final int ERROR_EXIT_CODE = 1;

    private final DirectoryValidator validator;
    private TestExportService.ExportConfig config;

    @Option(
            names = {"-d", "--directory"},
            description = "Directory to scan for test files (default: current directory)",
            defaultValue = ".")
    private File directory = new File(CURRENT_DIRECTORY);

    @Option(
            names = {"-key", "--apikey"},
            description = "API key for testomat.io",
            defaultValue = "${env:TESTOMATIO}")
    private String apiKey;

    @Option(
            names = "--url",
            description = "Testomat server URL",
            defaultValue = "${env:TESTOMATIO_URL}")
    private String serverUrl;

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose = false;

    @Option(
            names = {"--dry-run"},
            description = "Show what would be exported without sending")
    private boolean dryRun = false;

    public ImportCommand() {
        this.validator = new DirectoryValidator();
    }

    public ImportCommand(DirectoryValidator validator) {
        this.validator = validator;
    }

    @Override
    public Integer call() throws Exception {
        try {
            VerboseLogger logger = new VerboseLogger(verbose);

            boolean noDryRunFlag = !dryRun;
            if (noDryRunFlag && (apiKey == null || apiKey.trim().isEmpty())) {
                logger.log("TESTOMATIO API key not provided, running in dry-run mode");
                dryRun = true;
            }

            if (verbose) {
                logPlatformInfo(logger);
            }
            logger.log("Starting test export from directory: " + directory.getAbsolutePath());

            validator.validateDirectory(directory);

            TestFileScanner scanner = new TestFileScanner();
            List<File> testFiles = scanner.findTestFiles(directory);
            logger.log("Found " + testFiles.size() + " test files");

            if (testFiles.isEmpty()) {
                System.out.println("No test files found!");
                return SUCCESS_EXIT_CODE;
            }

            config = new TestExportService.ExportConfig(
                    apiKey, serverUrl, dryRun, verbose);

            TestExportService exportService = createExportService(logger);
            TestExportService.ExportResult result =
                    exportService.processTestFiles(testFiles, config);

            printCompletionMessage(result.getTotalExported());

            return SUCCESS_EXIT_CODE;

        } catch (Exception e) {
            System.err.println("Export failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return ERROR_EXIT_CODE;
        }
    }

    private TestExportService createExportService(VerboseLogger logger) {
        return new TestExportService(
                new JavaFileParser(),
                new TestMethodExtractor(),
                new TestFrameworkDetector(),
                new JsonBuilder(),
                new CliClient(),
                logger
        );
    }

    private void printCompletionMessage(int totalExported) {
        if (dryRun) {
            System.out.println("\nDry run completed. No data was sent to server.");
            if (apiKey == null || apiKey.trim().isEmpty()) {
                System.out.println("Run the same command with apikey and url provided to execute.");
            }
        } else {
            System.out.println("\n✓ Export completed! Total methods exported: " + totalExported);
        }
    }

    private void logPlatformInfo(VerboseLogger logger) {
        final String os = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        final String osArch = System.getProperty("os.arch");
        final String javaVersion = System.getProperty("java.version");
        final String javaVendor = System.getProperty("java.vendor");
        final String userDir = System.getProperty("user.dir");
        final String fileSeparator = FileSystems.getDefault().getSeparator();

        logger.log("=== Platform Information ===");
        logger.log("OS: " + os + " " + osVersion + " (" + osArch + ")");
        logger.log("Java: " + javaVersion + " (" + javaVendor + ")");
        logger.log("Working directory: " + userDir);
        logger.log("File separator: '" + fileSeparator + "'");
        logger.log("===========================");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ImportCommand()).execute(args);
        System.exit(exitCode);
    }
}
