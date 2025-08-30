package io.testomat.commands;

import com.github.javaparser.ast.CompilationUnit;
import io.testomat.exception.CliException;
import io.testomat.model.FilesProcessingResult;
import io.testomat.service.AnnotationCleaner;
import io.testomat.service.JavaFileParser;
import io.testomat.service.TestFileScanner;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "clean-ids",
        description = "Remove @TestId annotations and imports from test files"
)
public class CleanIdsCommand implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(CleanIdsCommand.class);

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
            log.info("Starting @TestId cleanup from directory: {}", Paths.get(directory).toAbsolutePath());

            List<File> javaFiles = scanner.findTestFiles(new File(directory));
            log.info("Found {} Java files", javaFiles.size());

            if (javaFiles.isEmpty()) {
                log.info("No Java files found!");
                return;
            }

            FilesProcessingResult result = processFiles(javaFiles, parser, cleaner);
            printSummary(result);

        } catch (Exception e) {
            handleProcessingException(e);
        }
    }

    private FilesProcessingResult processFiles(List<File> javaFiles,
                                               JavaFileParser parser,
                                               AnnotationCleaner cleaner) {
        FilesProcessingResult totalResult = new FilesProcessingResult();

        for (File javaFile : javaFiles) {
            try {
                processSingleFile(javaFile, parser, cleaner, totalResult);
            } catch (Exception e) {
                handleFileError(javaFile, e);
            }
        }

        return totalResult;
    }

    private void processSingleFile(File javaFile, JavaFileParser parser, AnnotationCleaner cleaner, FilesProcessingResult totalResult) {
        log.info("Processing: {}", javaFile.getName());

        CompilationUnit cu = parser.parseFile(javaFile.getAbsolutePath());
        if (cu == null) {
            log.info("  Skipped: Could not parse file");
            return;
        }

        AnnotationCleaner.CleanupResult result = cleaner.cleanTestIdAnnotations(cu, dryRun);

        if (result.getRemovedAnnotations() > 0 || result.getRemovedImports() > 0) {
            log.info("  Removed {} @TestId annotations", result.getRemovedAnnotations());
            log.info("  Removed {} TestId imports", result.getRemovedImports());

            if (!dryRun) {
                saveFile(cu);
            }

            totalResult.addResults(result.getRemovedAnnotations(), result.getRemovedImports());
        } else {
            log.info("  No @TestId annotations or imports found");
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

    private void printSummary(FilesProcessingResult result) {
        if (dryRun) {
            log.info("\nDry run completed. No files were modified.");
            log.info("Would remove:");
        } else {
            log.info("\n Cleanup completed!");
            log.info("Removed:");
        }

        log.info("  - @TestId annotations: {}", result.getTotalAnnotations());
        log.info("  - TestId imports: {}", result.getTotalImports());
        log.info("  - Modified files: {}", result.getModifiedFiles());
    }

    private void handleFileError(File javaFile, Exception e) {
        System.err.println("Error processing " + javaFile.getName() + ": " + e.getMessage());
        if (verbose) {
            throw new CliException("Failed to process file: " + javaFile, e);
        }
    }

    private void handleProcessingException(Exception e) {
        System.err.println("Cleanup failed" + ": " + e.getMessage());
        if (verbose) {
            throw new CliException("Failed to process file: " + "Cleanup failed", e);
        }
        throw new CliException("Cleanup failed", e);
    }
}
