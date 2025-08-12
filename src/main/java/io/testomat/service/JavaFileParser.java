package io.testomat.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import io.testomat.exception.CliException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JavaFileParser {
    private final Object lock = new Object();

    public CompilationUnit parseFile(String filepath) {
        Path filePath = Paths.get(filepath);

        if (!filePath.toFile().exists()) {
            return null;
        }

        if (!filePath.toFile().canRead()) {
            throw new CliException("Cannot read file: " + filepath);
        }

        try {
            synchronized (lock) {
                return StaticJavaParser.parse(filePath, StandardCharsets.UTF_8);
            }
        } catch (CliException e) {
            throw e;
        } catch (Exception e) {
            throw new CliException("Failed to parse file " + filepath, e);
        }
    }
}
