package io.testomat.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestFileScanner {

    private final Set<String> visitedPaths = new HashSet<>();

    public List<File> findTestFiles(File directory) {
        List<File> testFiles = new ArrayList<>();
        visitedPaths.clear();
        scanDirectory(directory, testFiles);
        return testFiles;
    }

    private void scanDirectory(File directory, List<File> testFiles) {
        if (!directory.isDirectory() || !directory.canRead()) {
            return;
        }

        try {
            String canonicalPath = directory.getCanonicalPath();
            if (visitedPaths.contains(canonicalPath)) {
                return;
            }
            visitedPaths.add(canonicalPath);

            if (Files.isSymbolicLink(directory.toPath())) {
                Path target = Files.readSymbolicLink(directory.toPath());
                if (!isWithinProject(target, directory.getParentFile())) {
                    return;
                }
            }
        } catch (IOException e) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                if (!shouldSkipDirectory(file.getName())) {
                    scanDirectory(file, testFiles);
                }
            } else if (isTestFile(file)) {
                testFiles.add(file);
            }
        }
    }

    private boolean isTestFile(File file) {
        if (!file.canRead() || !file.isFile()) {
            return false;
        }

        String name = file.getName();

        if (!name.endsWith(".java")) {
            return false;
        }

        return name.endsWith("Test.java")
                || name.endsWith("Tests.java")
                || (name.startsWith("Test") && name.length() > 9);
    }

    private boolean shouldSkipDirectory(String dirName) {
        return dirName.equals("target")
                || dirName.equals("build")
                || dirName.equals("out")
                || dirName.equals("bin")
                || dirName.equals("classes")
                || dirName.equals("node_modules")
                || dirName.equals(".git")
                || dirName.equals(".svn")
                || dirName.equals(".idea")
                || dirName.equals(".vscode")
                || dirName.startsWith(".");
    }

    private boolean isWithinProject(Path target, File projectRoot) {
        try {
            if (projectRoot == null) {
                return true;
            }

            Path targetAbsolute = target.toAbsolutePath().normalize();
            Path rootAbsolute = projectRoot.toPath().toAbsolutePath().normalize();

            return targetAbsolute.startsWith(rootAbsolute);
        } catch (Exception e) {
            return false;
        }
    }
}
