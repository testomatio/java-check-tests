package io.testomat.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.testomat.client.CliClient;
import io.testomat.client.TestomatHttpClient;
import io.testomat.exception.CliException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "clean-ids",
        description = "Remove @TestId annotations for tests that exist on the server"
)
public class CleanIdsCommand implements Runnable {

    private static final String JAVA_EXTENSION = ".java";
    private static final String TEST_ID_IMPORT = "io.testomat.core.annotation.TestId";
    private static final String TEST_ID_ANNOTATION = "TestId";
    private static final String TESTS_FIELD = "tests";
    private static final String TEST_ID_PREFIX = "@T";

    @Option(
            names = {"-d", "--directory"},
            description = "Directory to scan for test files (default: current directory)",
            defaultValue = ".")
    private String directory;

    @Option(
            names = {"--apikey", "-key"}, required = true,
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
            description = "Show what would be removed without making changes")
    private boolean dryRun = false;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TestomatHttpClient httpClient;
    private final JavaParser parser;

    public CleanIdsCommand() {
        this.httpClient = new CliClient();
        this.parser = new JavaParser();
    }

    public CleanIdsCommand(TestomatHttpClient httpClient, JavaParser parser) {
        this.httpClient = httpClient;
        this.parser = parser;
    }

    @Override
    public void run() {
        try {
            boolean noDryRunFlag = !dryRun;
            if (noDryRunFlag && (serverUrl == null || serverUrl.trim().isEmpty())) {
                log("TESTOMATIO_URL not provided, running in dry-run mode");
                dryRun = true;
            }

            log("Starting @TestId cleanup from directory: "
                    + Paths.get(directory).toAbsolutePath());

            Set<String> serverTestIds = getServerTestIds();

            if (!dryRun && !serverTestIds.isEmpty()) {
                log("Found " + serverTestIds.size() + " test IDs on server");
            }

            List<Path> javaFiles = findJavaFiles();
            log("Found " + javaFiles.size() + " Java files");

            if (javaFiles.isEmpty()) {
                System.out.println("No Java files found!");
                return;
            }

            int totalProcessedAnnotations = 0;
            int totalRemovedImports = 0;
            int modifiedFiles = 0;

            for (Path javaFile : javaFiles) {
                try {
                    log("Processing: " + javaFile.getFileName());

                    CompilationUnit cu = parseFile(javaFile);
                    if (cu == null) {
                        log("  Skipped: Could not parse file");
                        continue;
                    }

                    int processedAnnotations = processTestIdAnnotations(cu, serverTestIds);
                    int removedImports = 0;

                    if (processedAnnotations > 0) {
                        removedImports = removeUnusedTestIdImports(cu);

                        if (dryRun) {
                            log("  Found " + processedAnnotations + " @TestId annotations");
                        } else {
                            log("  Removed " + processedAnnotations + " @TestId annotations");
                        }

                        if (removedImports > 0) {
                            if (dryRun) {
                                log("  Would remove " + removedImports + " unused TestId imports");
                            } else {
                                log("  Removed " + removedImports + " unused TestId imports");
                            }
                        }

                        if (!dryRun) {
                            saveFile(cu, javaFile);
                        }

                        totalProcessedAnnotations += processedAnnotations;
                        totalRemovedImports += removedImports;
                        modifiedFiles++;
                    } else {
                        if (dryRun) {
                            log("  No @TestId annotations found");
                        } else {
                            log("  No matching @TestId annotations found");
                        }
                    }

                } catch (Exception e) {
                    System.err.println("Error processing "
                            + javaFile.getFileName()
                            + ": " + e.getMessage());
                    if (verbose) {
                        e.printStackTrace();
                    }
                }
            }

            printSummary(totalProcessedAnnotations, totalRemovedImports, modifiedFiles);

        } catch (Exception e) {
            System.err.println("Clean-ids failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            throw new CliException("Clean-ids command failed", e);
        }
    }

    private Set<String> getServerTestIds() {
        if (dryRun) {
            return new HashSet<>();
        }

        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new CliException("TESTOMATIO_URL is required for actual execution");
        }

        try {
            String response = httpClient.sendGetRequest(apiKey, serverUrl);
            return parseTestIdsFromResponse(response);
        } catch (Exception e) {
            throw new CliException("Failed to get test IDs from server", e);
        }
    }

    private Set<String> parseTestIdsFromResponse(String response) {
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode testsNode = rootNode.get(TESTS_FIELD);

            if (testsNode == null || !testsNode.isObject()) {
                throw new CliException("Invalid response format: missing or invalid 'tests' field");
            }

            Set<String> testIds = new HashSet<>();
            testsNode.fields().forEachRemaining(entry -> {
                String testId = entry.getValue().asText().replace(TEST_ID_PREFIX, "");
                testIds.add(testId);
            });

            return testIds;
        } catch (JsonProcessingException e) {
            throw new CliException("Failed to parse server response", e);
        }
    }

    private List<Path> findJavaFiles() {
        try (Stream<Path> pathStream = Files.walk(Paths.get(directory))) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(JAVA_EXTENSION))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new CliException("Failed to scan directory for Java files", e);
        }
    }

    private CompilationUnit parseFile(Path file) {
        if (!Files.exists(file)) {
            throw new CliException("File does not exist: " + file);
        }

        if (!Files.isReadable(file)) {
            throw new CliException("File is not readable: " + file);
        }

        try {
            return parser.parse(file, StandardCharsets.UTF_8)
                    .getResult()
                    .orElse(null);
        } catch (IOException e) {
            throw new CliException("Could not parse file " + file, e);
        }
    }

    private int processTestIdAnnotations(CompilationUnit cu, Set<String> serverTestIds) {
        List<AnnotationExpr> annotationsToProcess = new ArrayList<>();

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            for (AnnotationExpr annotation : method.getAnnotations()) {
                if (TEST_ID_ANNOTATION.equals(annotation.getNameAsString())) {
                    String testId = extractTestIdValue(annotation);

                    if (dryRun) {
                        if (testId != null) {
                            annotationsToProcess.add(annotation);
                            log("    Found @TestId(\"" + testId
                                    + "\") in method: " + method.getNameAsString());
                        }
                    } else if (testId != null && serverTestIds.contains(testId)) {
                        annotationsToProcess.add(annotation);
                        log("    Removing @TestId(\"" + testId
                                + "\") from method: " + method.getNameAsString());
                    }
                }
            }
        }

        if (!dryRun) {
            for (AnnotationExpr annotation : annotationsToProcess) {
                annotation.remove();
            }
        }

        return annotationsToProcess.size();
    }

    private String extractTestIdValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr singleMember = (SingleMemberAnnotationExpr) annotation;
            if (singleMember.getMemberValue() instanceof StringLiteralExpr) {
                StringLiteralExpr stringLiteral = (StringLiteralExpr) singleMember.getMemberValue();
                return stringLiteral.getValue();
            }
        }
        return null;
    }

    private int removeUnusedTestIdImports(CompilationUnit cu) {
        boolean hasTestIdAnnotations = cu.findAll(MethodDeclaration.class).stream()
                .flatMap(method -> method.getAnnotations().stream())
                .anyMatch(annotation -> TEST_ID_ANNOTATION.equals(annotation.getNameAsString()));

        if (hasTestIdAnnotations) {
            return 0;
        }

        List<ImportDeclaration> importsToRemove = cu.getImports().stream()
                .filter(importDecl -> TEST_ID_IMPORT.equals(importDecl.getNameAsString()))
                .collect(Collectors.toList());

        if (!dryRun) {
            importsToRemove.forEach(ImportDeclaration::remove);
        }

        return importsToRemove.size();
    }

    private void saveFile(CompilationUnit cu, Path file) {
        cu.getStorage().ifPresent(storage -> {
            try {
                storage.save();
            } catch (Exception e) {
                throw new CliException("Failed to save file: " + file, e);
            }
        });
    }

    private void printSummary(int totalProcessedAnnotations,
                              int totalRemovedImports,
                              int modifiedFiles) {
        if (dryRun) {
            System.out.println("\nDry run completed. No files were modified.");
            System.out.println("Found @TestId annotations: " + totalProcessedAnnotations);
        } else {
            System.out.println("\n✓ Clean-ids completed!");
            System.out.println("Removed:");
            System.out.println("  - @TestId annotations: " + totalProcessedAnnotations);
        }

        if (totalRemovedImports > 0) {
            if (dryRun) {
                System.out.println("TestId imports that would be removed: " + totalRemovedImports);
            } else {
                System.out.println("  - TestId imports: " + totalRemovedImports);
            }
        }
        System.out.println((dryRun ? "Files that would be processed: "
                : "  - Files modified: ") + modifiedFiles);

        if (dryRun && (serverUrl == null || serverUrl.trim().isEmpty())) {
            System.out.println("\nRun the same command with TESTOMATIO_URL provided to execute.");
        }
    }

    private void log(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }
}
