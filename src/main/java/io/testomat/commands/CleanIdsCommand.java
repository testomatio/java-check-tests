package io.testomat.commands;

import com.github.javaparser.ast.CompilationUnit;
import io.testomat.exception.CliException;
import io.testomat.service.AnnotationCleaner;
import io.testomat.service.JavaFileParser;
import io.testomat.service.TestFileScanner;
import io.testomat.service.VerboseLogger;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "clean-ids",
        description = "Remove @TestId annotations and imports from test files"
)
public class CleanIdsCommand implements Runnable {

    private final JavaFileParser parser;
    private final TestFileScanner scanner;
    private final AnnotationCleaner cleaner;

    @Option(
            names = {"-d", "--directory"},
            description = "Directory to scan for test files (default: current directory)",
            defaultValue = ".")
    private String directory;

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose = false;

    @Option(
            names = {"--dry-run"},
            description = "Show what would be removed without making changes")
    private boolean dryRun = false;

    public CleanIdsCommand() {
        this.parser = new JavaFileParser();
        this.scanner = new TestFileScanner();
        this.cleaner = new AnnotationCleaner();
    }

    public CleanIdsCommand(JavaFileParser parser,
                           TestFileScanner scanner,
                           AnnotationCleaner cleaner) {
        this.parser = parser;
        this.scanner = scanner;
        this.cleaner = cleaner;
    }

    @Override
    public void run() {
        try {
            VerboseLogger logger = new VerboseLogger(verbose);

            logger.log("Starting @TestId cleanup from directory: "
                    + Paths.get(directory).toAbsolutePath());

            List<File> javaFiles = scanner.findTestFiles(new File(directory));
            logger.log("Found " + javaFiles.size() + " Java files");

            if (javaFiles.isEmpty()) {
                System.out.println("No Java files found!");
                return;
            }

            CleanupResult result = processFiles(javaFiles, parser, cleaner, logger);
            printSummary(result);

        } catch (Exception e) {
            handleError("Cleanup failed", e);
        }
    }

    private CleanupResult processFiles(List<File> javaFiles, JavaFileParser parser,
                                       AnnotationCleaner cleaner, VerboseLogger logger) {
        CleanupResult totalResult = new CleanupResult();

        for (File javaFile : javaFiles) {
            try {
                processFile(javaFile, parser, cleaner, logger, totalResult);
            } catch (Exception e) {
                handleFileError(javaFile, e);
            }
        }

        return totalResult;
    }

    private void processFile(File javaFile, JavaFileParser parser, AnnotationCleaner cleaner,
                             VerboseLogger logger, CleanupResult totalResult) {
        logger.log("Processing: " + javaFile.getName());

        CompilationUnit cu = parser.parseFile(javaFile.getAbsolutePath());
        if (cu == null) {
            logger.log("  Skipped: Could not parse file");
            return;
        }

        AnnotationCleaner.CleanupResult result = cleaner.cleanTestIdAnnotations(cu, dryRun);

        if (result.getRemovedAnnotations() > 0 || result.getRemovedImports() > 0) {
            logger.log("  Removed " + result.getRemovedAnnotations() + " @TestId annotations");
            logger.log("  Removed " + result.getRemovedImports() + " TestId imports");

            if (!dryRun) {
                saveFile(cu);
            }

            totalResult.addResults(result.getRemovedAnnotations(), result.getRemovedImports(), 1);
        } else {
            logger.log("  No @TestId annotations or imports found");
        }
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

    private void printSummary(CleanupResult result) {
        if (dryRun) {
            System.out.println("\nDry run completed. No files were modified.");
            System.out.println("Would remove:");
        } else {
            System.out.println("\n✓ Cleanup completed!");
            System.out.println("Removed:");
        }

        System.out.println("  - @TestId annotations: " + result.getTotalAnnotations());
        System.out.println("  - TestId imports: " + result.getTotalImports());
        System.out.println("  - Modified files: " + result.getModifiedFiles());
    }

    private void handleFileError(File javaFile, Exception e) {
        System.err.println("Error processing " + javaFile.getName() + ": " + e.getMessage());
        if (verbose) {
            throw new CliException("Failed to process file: " + javaFile, e);
        }
    }

    private void handleError(String message, Exception e) {
        System.err.println(message + ": " + e.getMessage());
        if (verbose) {
            throw new CliException("Failed to process file: " + message, e);
        }
        throw new CliException(message, e);
    }

    private static class CleanupResult {
        private int totalAnnotations = 0;
        private int totalImports = 0;
        private int modifiedFiles = 0;

        void addResults(int annotations, int imports, int files) {
            this.totalAnnotations += annotations;
            this.totalImports += imports;
            this.modifiedFiles += files;
        }

        int getTotalAnnotations() {
            return totalAnnotations;
        }

        int getTotalImports() {
            return totalImports;
        }

        int getModifiedFiles() {
            return modifiedFiles;
        }
    }
}
