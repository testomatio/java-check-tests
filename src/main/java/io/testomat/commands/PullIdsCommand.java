package io.testomat.commands;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import io.testomat.client.CliClient;
import io.testomat.progressbar.ProgressBar;
import io.testomat.service.ResponseParser;
import io.testomat.service.TestIdAnnotationManager;
import io.testomat.service.TestIdSyncService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine;

@CommandLine.Command(name = "pull-ids", description =
        "Pulls IDs into your codebase from testomat.io")
public class PullIdsCommand implements Runnable {
    private static final String DEFAULT_URL = "https://app.testomat.io";
    private final JavaParser javaParser;

    @CommandLine.Option(
            names = {"--directory", "-d"},
            defaultValue = ".")
    private String directory;

    @CommandLine.Option(
            names = {"--apikey", "-key"},
            description = "Testomat project api key",
            defaultValue = "${env:TESTOMATIO}")
    private String apiKey;

    @CommandLine.Option(
            names = "--url",
            description = "Testomat server URL")
    private String serverUrl;

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose = false;

    public PullIdsCommand() {
        this.javaParser = new JavaParser();
    }

    public PullIdsCommand(JavaParser javaParser) {
        this.javaParser = javaParser;
    }

    @Override
    public void run() {
        // Set default URL if not provided and environment variable is not set
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            String envUrl = System.getenv("TESTOMATIO_URL");
            if (envUrl == null || envUrl.trim().isEmpty()) {
                serverUrl = DEFAULT_URL;
            } else {
                serverUrl = envUrl;
            }
        }

        TestIdSyncService syncService = createSyncService();
        List<CompilationUnit> compilationUnits = loadCompilationUnits();

        if (verbose) {
            System.out.println("Found " + compilationUnits.size() + " compilation units");
        }

        ProgressBar progressBar = new ProgressBar(100, "Adding test IDs");
        syncService.syncTestIds(apiKey, serverUrl, compilationUnits, verbose, progressBar);

        System.out.println("Saved modified files");
    }

    private TestIdSyncService createSyncService() {
        return new TestIdSyncService(
                new CliClient(),
                new ResponseParser(),
                new TestIdAnnotationManager()
        );
    }

    private List<CompilationUnit> loadCompilationUnits() {
        List<Path> javaFiles = findJavaFiles();
        return parseJavaFiles(javaFiles);
    }

    private List<Path> findJavaFiles() {
        try (Stream<Path> pathStream = Files.walk(Paths.get(directory))) {
            return pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan directory for Java files", e);
        }
    }

    private List<CompilationUnit> parseJavaFiles(List<Path> javaFiles) {

        return javaFiles.stream()
                .map(javaFile -> parseJavaFile(javaFile, javaParser))
                .collect(Collectors.toList());
    }

    private CompilationUnit parseJavaFile(Path javaFile, JavaParser javaParser) {
        try {
            return javaParser.parse(javaFile)
                    .getResult()
                    .orElseThrow(() -> new RuntimeException("Could not parse file " + javaFile));
        } catch (Exception e) {
            throw new RuntimeException("Could not parse file " + javaFile, e);
        }
    }

    public static void main(String[] args) {
        CommandLine.run(new PullIdsCommand(), args);
    }
}
