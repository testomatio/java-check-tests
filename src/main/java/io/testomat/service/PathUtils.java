package io.testomat.service;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtils {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

    public static String extractRelativeFilePath(String filepath) {
        if (filepath == null || filepath.isEmpty()) {
            return "UnknownFile.java";
        }

        String normalizedPath = normalizePath(filepath);

        if (normalizedPath.contains("src/test/java/")) {
            int index = normalizedPath.indexOf("src/test/java/");
            return normalizedPath.substring(index + "src/test/java/".length());
        }

        if (normalizedPath.contains("src/main/java/")) {
            int index = normalizedPath.indexOf("src/main/java/");
            return normalizedPath.substring(index + "src/main/java/".length());
        }

        if (normalizedPath.contains("src/") && normalizedPath.contains("/java/")) {
            int javaIndex = normalizedPath.lastIndexOf("/java/");
            if (javaIndex != -1) {
                return normalizedPath.substring(javaIndex + "/java/".length());
            }
        }

        return normalizedPath;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        try {
            Path normalizedPath = Paths.get(path).normalize();
            String result = normalizedPath.toString().replace('\\', '/');

            if (IS_WINDOWS && result.startsWith("/")
                    && result.length() > 1
                    && !hasDriveLetter(result)) {
                return result.substring(1);
            }

            if (!IS_WINDOWS && hasDriveLetter(result)) {
                return result.startsWith("/")
                        ? result.substring(3)
                        : result.substring(2);
            }

            return result;
        } catch (Exception e) {
            return path.replace('\\', '/');
        }
    }

    private static boolean hasDriveLetter(String path) {
        if (path.length() < 2) {
            return false;
        }

        int colonIndex = path.indexOf(':');
        if (colonIndex == -1) {
            return false;
        }

        if (path.startsWith("/") && colonIndex == 2) {
            return Character.isLetter(path.charAt(1));
        }

        if (colonIndex == 1) {
            return Character.isLetter(path.charAt(0));
        }

        return false;
    }
}
