package io.testomat.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans directory trees for Java source files that may contain test methods.
 * Handles symbolic links safely and skips build/output directories for performance.
 */
public class TestFileScanner {

    private static final String JAVA_FILE_EXTENSION = ".java";
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            "target", "build", "out", "bin", "classes", "node_modules",
            ".git", ".svn", ".idea", ".vscode"
    );

    private final Set<String> visitedPaths = new HashSet<>();

    /**
     * Finds all Java source files in the specified directory tree.
     *
     * @param directory the root directory to scan
     * @return immutable list of Java files found, never null
     * @throws IllegalArgumentException if directory is null, doesn't exist, or isn't readable
     */
    public List<File> findTestFiles(File directory) {
        validateDirectory(directory);

        List<File> javaFiles = new ArrayList<>();
        visitedPaths.clear();

        try {
            scanDirectory(directory, javaFiles);
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan directory: "
                    + directory.getAbsolutePath(), e);
        }

        return Collections.unmodifiableList(javaFiles);
    }

    /**
     * Recursively scans directory structure for Java files.
     *
     * @param directory current directory being scanned
     * @param javaFiles accumulator list for discovered files
     */
    private void scanDirectory(File directory, List<File> javaFiles) {
        if (!isDirectoryAccessible(directory)) {
            return;
        }

        if (!markDirectoryAsVisited(directory)) {
            return;
        }

        if (isSymbolicLinkOutsideProject(directory)) {
            return;
        }

        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            processChild(child, javaFiles);
        }
    }

    /**
     * Processes a single file or directory child.
     */
    private void processChild(File child, List<File> javaFiles) {
        if (child.isDirectory()) {
            if (!shouldSkipDirectory(child.getName())) {
                scanDirectory(child, javaFiles);
            }
        } else if (isJavaSourceFile(child)) {
            javaFiles.add(child);
        }
    }

    /**
     * Validates that directory exists and is accessible.
     */
    private void validateDirectory(File directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Directory cannot be null");
        }
        if (!directory.exists()) {
            throw new IllegalArgumentException("Directory does not exist: "
                    + directory.getAbsolutePath());
        }
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Path is not a directory: "
                    + directory.getAbsolutePath());
        }
        if (!directory.canRead()) {
            throw new IllegalArgumentException("Directory is not readable: "
                    + directory.getAbsolutePath());
        }
    }

    /**
     * Checks if directory is accessible for scanning.
     */
    private boolean isDirectoryAccessible(File directory) {
        return directory != null && directory.isDirectory() && directory.canRead();
    }

    /**
     * Marks directory as visited to prevent infinite loops with symbolic links.
     *
     * @return true if successfully marked, false if already visited
     */
    private boolean markDirectoryAsVisited(File directory) {
        try {
            String canonicalPath = directory.getCanonicalPath();
            return visitedPaths.add(canonicalPath);
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Checks if directory is a symbolic link pointing outside the project.
     */
    private boolean isSymbolicLinkOutsideProject(File directory) {
        if (!Files.isSymbolicLink(directory.toPath())) {
            return false;
        }

        try {
            Path target = Files.readSymbolicLink(directory.toPath());
            return !isWithinProject(target, directory.getParentFile());
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Checks if the file is a readable Java source file.
     */
    private boolean isJavaSourceFile(File file) {
        return file != null
                && file.isFile()
                && file.canRead()
                && file.getName().endsWith(JAVA_FILE_EXTENSION);
    }

    /**
     * Determines if directory should be excluded from scanning.
     * Skips build directories, version control, and IDE directories.
     */
    private boolean shouldSkipDirectory(String dirName) {
        if (dirName == null) {
            return true;
        }

        return EXCLUDED_DIRECTORIES.contains(dirName) || dirName.startsWith(".");
    }

    /**
     * Verifies that symbolic link target is within project boundaries.
     * Prevents scanning of external directories that could contain sensitive data.
     */
    private boolean isWithinProject(Path target, File projectRoot) {
        if (target == null || projectRoot == null) {
            return false;
        }

        try {
            Path targetAbsolute = target.toAbsolutePath().normalize();
            Path rootAbsolute = projectRoot.toPath().toAbsolutePath().normalize();
            return targetAbsolute.startsWith(rootAbsolute);
        } catch (Exception e) {
            return false;
        }
    }
}
