package io.testomat.commands;

import io.testomat.progressbar.ProgressBar;
import io.testomat.service.DirectoryValidator;
import io.testomat.service.TestExportService;
import io.testomat.service.TestFileScanner;
import io.testomat.service.VerboseLogger;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "import",
        description = "Imports JUnit and TestNG test methods to testomat.io",
        mixinStandardHelpOptions = true
)
public class ImportCommand implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(ImportCommand.class);

    private static final String CURRENT_DIRECTORY = ".";
    private static final String DEFAULT_URL = "https://app.testomat.io";
    private static final int SUCCESS_EXIT_CODE = 0;
    private static final int ERROR_EXIT_CODE = 1;

    private final DirectoryValidator validator;
    private final TestFileScanner scanner;
    private final TestExportService exportService;

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
            description = "Testomat server URL")
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
        this.exportService = new TestExportService();
        this.validator = new DirectoryValidator();
        this.scanner = new TestFileScanner();
    }

    public ImportCommand(DirectoryValidator validator,
                         TestFileScanner scanner,
                         TestExportService exportService) {
        this.validator = validator;
        this.scanner = scanner;
        this.exportService = exportService;
    }

    @Override
    public Integer call() throws Exception {
        try {
            if (serverUrl == null || serverUrl.trim().isEmpty()) {
                String envUrl = System.getenv("TESTOMATIO_URL");
                if (envUrl == null || envUrl.trim().isEmpty()) {
                    serverUrl = DEFAULT_URL;
                } else {
                    serverUrl = envUrl;
                }
            }

            VerboseLogger logger = new VerboseLogger(verbose);

            boolean noDryRunFlag = !dryRun;
            if (noDryRunFlag && (apiKey == null || apiKey.trim().isEmpty())) {
                logger.log("TESTOMATIO API key not provided, running in dry-run mode");
                dryRun = true;
            }

            logger.log("Starting test export from directory: " + directory.getAbsolutePath());

            validator.validateDirectory(directory);

            List<File> testFiles = scanner.findTestFiles(directory);
            logger.log("Found " + testFiles.size() + " test files");

            if (testFiles.isEmpty()) {
                System.out.println("No test files found!");
                return SUCCESS_EXIT_CODE;
            }

            ProgressBar progressBar = new ProgressBar(testFiles.size(),
                    "Parsing " + testFiles.size() + " files");
            int totalExported = exportService.processTestFilesWithProgress(
                    testFiles, apiKey, serverUrl, dryRun, verbose, progressBar);

            printCompletionMessage(totalExported);

            return SUCCESS_EXIT_CODE;

        } catch (Exception e) {
            System.err.println("Export failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return ERROR_EXIT_CODE;
        }
    }

    private void printCompletionMessage(int totalExported) {
        if (dryRun) {
            System.out.println("\nDry run completed. No data was sent to server.");
            System.out.println("Found " + totalExported + " test methods");
            if (apiKey == null || apiKey.trim().isEmpty()) {
                System.out.println("Run the same command with apikey and url provided to execute.");
            }
        } else {
            System.out.println("\n");
        }
    }
}
