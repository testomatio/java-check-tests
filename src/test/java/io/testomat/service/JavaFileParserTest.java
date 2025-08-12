package io.testomat.service;

import com.github.javaparser.ast.CompilationUnit;
import io.testomat.exception.CliException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JavaFileParserTest {

    private JavaFileParser javaFileParser;

    @TempDir
    Path tempDir;

    private static final String VALID_JAVA_CODE = 
            "package com.example;\n" +
            "\n" +
            "import java.util.List;\n" +
            "\n" +
            "public class TestClass {\n" +
            "    private String field;\n" +
            "\n" +
            "    public void method() {\n" +
            "        System.out.println(\"Hello World\");\n" +
            "    }\n" +
            "}";

    private static final String INVALID_JAVA_CODE = 
            "package com.example;\n" +
            "\n" +
            "public class InvalidClass {\n" +
            "    invalid syntax here!!!\n" +
            "    missing braces and semicolons\n" +
            "    +++wrong+++\n";

    private static final String SIMPLE_VALID_JAVA = 
            "class Simple {}";

    @BeforeEach
    void setUp() {
        javaFileParser = new JavaFileParser();
    }

    @Test
    @DisplayName("Should return null when file does not exist")
    void shouldReturnNullWhenFileDoesNotExist() {
        // Given
        String nonExistentFilePath = "/path/that/does/not/exist/TestFile.java";
        
        // When
        CompilationUnit result = javaFileParser.parseFile(nonExistentFilePath);
        
        // Then
        assertNull(result, "Should return null for non-existent file");
    }

    @Test
    @DisplayName("Should throw CliException when file is not readable")
    void shouldThrowExceptionWhenFileIsNotReadable() throws IOException {
        // Given
        Path javaFile = tempDir.resolve("UnreadableFile.java");
        Files.write(javaFile, VALID_JAVA_CODE.getBytes(StandardCharsets.UTF_8));
        
        // Try to make file unreadable (Unix/Linux systems)
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("-wx-wx-wx");
            Files.setPosixFilePermissions(javaFile, permissions);
            
            // Only test if we successfully made it unreadable
            if (!javaFile.toFile().canRead()) {
                // When & Then
                CliException exception = assertThrows(CliException.class, 
                        () -> javaFileParser.parseFile(javaFile.toString()));
                
                assertTrue(exception.getMessage().startsWith("Cannot read file:"));
                assertTrue(exception.getMessage().contains(javaFile.toString()));
            }
        } catch (UnsupportedOperationException e) {
            // Skip this test on file systems that don't support POSIX permissions
        } finally {
            // Restore permissions for cleanup
            try {
                Set<PosixFilePermission> restorePermissions = PosixFilePermissions.fromString("rw-rw-rw-");
                Files.setPosixFilePermissions(javaFile, restorePermissions);
            } catch (UnsupportedOperationException ignored) {
                // Ignore if POSIX permissions not supported
            }
        }
    }

    @Test
    @DisplayName("Should successfully parse valid Java file")
    void shouldSuccessfullyParseValidJavaFile() throws IOException {
        // Given
        Path javaFile = tempDir.resolve("ValidClass.java");
        Files.write(javaFile, VALID_JAVA_CODE.getBytes(StandardCharsets.UTF_8));
        
        // When
        CompilationUnit result = javaFileParser.parseFile(javaFile.toString());
        
        // Then
        assertNotNull(result, "Should return CompilationUnit for valid Java file");
        assertTrue(result.getPackageDeclaration().isPresent(), "Should parse package declaration");
        assertEquals("com.example", result.getPackageDeclaration().get().getNameAsString());
        assertEquals(1, result.getTypes().size(), "Should parse one type declaration");
        assertEquals("TestClass", result.getType(0).getNameAsString());
    }

    @Test
    @DisplayName("Should throw CliException when Java file has syntax errors")
    void shouldThrowExceptionWhenJavaFileHasSyntaxErrors() throws IOException {
        // Given
        Path javaFile = tempDir.resolve("InvalidClass.java");
        Files.write(javaFile, INVALID_JAVA_CODE.getBytes(StandardCharsets.UTF_8));
        
        // When & Then
        CliException exception = assertThrows(CliException.class, 
                () -> javaFileParser.parseFile(javaFile.toString()));
        
        assertTrue(exception.getMessage().startsWith("Failed to parse file"));
        assertTrue(exception.getMessage().contains(javaFile.toString()));
        assertNotNull(exception.getCause(), "Should have underlying parsing exception as cause");
    }

    @Test
    @DisplayName("Should parse simple Java class")
    void shouldParseSimpleJavaClass() throws IOException {
        // Given
        Path javaFile = tempDir.resolve("Simple.java");
        Files.write(javaFile, SIMPLE_VALID_JAVA.getBytes(StandardCharsets.UTF_8));
        
        // When
        CompilationUnit result = javaFileParser.parseFile(javaFile.toString());
        
        // Then
        assertNotNull(result, "Should parse simple Java class");
        assertEquals(1, result.getTypes().size());
        assertEquals("Simple", result.getType(0).getNameAsString());
        assertFalse(result.getPackageDeclaration().isPresent(), "Simple class has no package");
    }

    @Test
    @DisplayName("Should parse Java file with UTF-8 encoding")
    void shouldParseJavaFileWithUtf8Encoding() throws IOException {
        // Given - Java code with UTF-8 characters
        String javaCodeWithUnicode = 
                "package com.example;\n" +
                "\n" +
                "public class UnicodeClass {\n" +
                "    // Коментар українською мовою\n" +
                "    private String greeting = \"Привіт світ!\";\n" +
                "    private String emoji = \"😀\";\n" +
                "}";
        
        Path javaFile = tempDir.resolve("UnicodeClass.java");
        Files.write(javaFile, javaCodeWithUnicode.getBytes(StandardCharsets.UTF_8));
        
        // When
        CompilationUnit result = javaFileParser.parseFile(javaFile.toString());
        
        // Then
        assertNotNull(result, "Should parse Java file with UTF-8 characters");
        assertEquals("UnicodeClass", result.getType(0).getNameAsString());
    }

    @Test
    @DisplayName("Should handle concurrent parsing safely")
    void shouldHandleConcurrentParsingSafely() throws IOException, InterruptedException {
        // Given
        Path javaFile1 = tempDir.resolve("ConcurrentClass1.java");
        Path javaFile2 = tempDir.resolve("ConcurrentClass2.java");
        Files.write(javaFile1, VALID_JAVA_CODE.replace("TestClass", "ConcurrentClass1").getBytes());
        Files.write(javaFile2, VALID_JAVA_CODE.replace("TestClass", "ConcurrentClass2").getBytes());
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(20);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // When - multiple threads parsing files concurrently
        for (int i = 0; i < 20; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    String filePath = (threadIndex % 2 == 0) ? javaFile1.toString() : javaFile2.toString();
                    CompilationUnit result = javaFileParser.parseFile(filePath);
                    
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete");
        assertEquals(20, successCount.get(), "All parsing operations should succeed");
        assertEquals(0, errorCount.get(), "No errors should occur during concurrent parsing");
        
        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle file path with spaces")
    void shouldHandleFilePathWithSpaces() throws IOException {
        // Given
        Path javaFile = tempDir.resolve("File With Spaces.java");
        Files.write(javaFile, SIMPLE_VALID_JAVA.getBytes(StandardCharsets.UTF_8));
        
        // When
        CompilationUnit result = javaFileParser.parseFile(javaFile.toString());
        
        // Then
        assertNotNull(result, "Should parse file with spaces in path");
        assertEquals("Simple", result.getType(0).getNameAsString());
    }

    @Test
    @DisplayName("Should include file path in error message when parsing fails")
    void shouldIncludeFilePathInErrorMessageWhenParsingFails() throws IOException {
        // Given
        Path javaFile = tempDir.resolve("ErrorFile.java");
        Files.write(javaFile, INVALID_JAVA_CODE.getBytes(StandardCharsets.UTF_8));
        String expectedPath = javaFile.toString();
        
        // When
        CliException exception = assertThrows(CliException.class, 
                () -> javaFileParser.parseFile(expectedPath));
        
        // Then
        assertTrue(exception.getMessage().contains(expectedPath), 
                "Error message should contain the file path");
        assertEquals("Failed to parse file " + expectedPath, 
                exception.getMessage().substring(0, ("Failed to parse file " + expectedPath).length()));
    }
}