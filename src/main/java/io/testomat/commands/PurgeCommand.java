package io.testomat.commands;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.testomat.exception.CliException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "purge",
        description = "Remove @TestId annotations and imports from test files"
)
public class PurgeCommand implements Runnable {

    private static final String JAVA_EXTENSION = ".java";
    private static final String TEST_ID_IMPORT = "io.testomat.core.annotation.TestId";
    private final JavaParser parser;

    @Option(names = {"-d", "--directory"},
            description = "Directory to scan for test files (default: current directory)")
    private String directory = ".";

    @Option(names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose = false;

    @Option(names = {"--dry-run"},
            description = "Show what would be removed without making changes")
    private boolean dryRun = false;

    public PurgeCommand() {
        this.parser = new JavaParser();
    }

    public PurgeCommand(JavaParser parser) {
        this.parser = parser;
    }

    @Override
    public void run() {
        try {
            log("Starting @TestId cleanup from directory: "
                    + Paths.get(directory).toAbsolutePath());

            List<Path> javaFiles = findJavaFiles();
            log("Found " + javaFiles.size() + " Java files");

            if (javaFiles.isEmpty()) {
                System.out.println("No Java files found!");
                return;
            }

            int totalRemovedAnnotations = 0;
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

                    int removedAnnotations = removeTestIdAnnotations(cu);
                    int removedImports = removeTestIdImports(cu);

                    if (removedAnnotations > 0 || removedImports > 0) {
                        log("  Removed " + removedAnnotations + " @TestId annotations");
                        log("  Removed " + removedImports + " TestId imports");

                        if (!dryRun) {
                            saveFile(cu);
                        }

                        totalRemovedAnnotations += removedAnnotations;
                        totalRemovedImports += removedImports;
                        modifiedFiles++;
                    } else {
                        log("  No @TestId annotations or imports found");
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

            if (dryRun) {
                System.out.println("\nDry run completed. No files were modified.");
                System.out.println("Would remove:");
            } else {
                System.out.println("\n✓ Cleanup completed!");
                System.out.println("Removed:");
            }

            System.out.println("  - @TestId annotations: " + totalRemovedAnnotations);
            System.out.println("  - TestId imports: " + totalRemovedImports);
            System.out.println("  - Modified files: " + modifiedFiles);

        } catch (Exception e) {
            System.err.println("Cleanup failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            throw new CliException("WipeId command failed", e);
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

    private int removeTestIdAnnotations(CompilationUnit cu) {
        List<AnnotationExpr> testIdAnnotations = new ArrayList<>();

        // Знаходимо всі методи з @TestId анотаціями
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            for (AnnotationExpr annotation : method.getAnnotations()) {
                if ("TestId".equals(annotation.getNameAsString())) {
                    testIdAnnotations.add(annotation);
                }
            }
        }

        // Видаляємо знайдені анотації
        for (AnnotationExpr annotation : testIdAnnotations) {
            annotation.remove();
        }

        return testIdAnnotations.size();
    }

    private int removeTestIdImports(CompilationUnit cu) {
        List<ImportDeclaration> testIdImports = new ArrayList<>();

        // Знаходимо всі імпорти TestId
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (TEST_ID_IMPORT.equals(importName)) {
                testIdImports.add(importDecl);
            }
        }

        // Видаляємо знайдені імпорти
        for (ImportDeclaration importDecl : testIdImports) {
            importDecl.remove();
        }

        return testIdImports.size();
    }

    private void saveFile(CompilationUnit cu) {
        cu.getStorage().ifPresent(storage -> {
            try {
                storage.save();
            } catch (Exception e) {
                throw new CliException("Failed to save file: " + storage.getPath(), e);
            }
        });
    }

    private void log(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }
}
