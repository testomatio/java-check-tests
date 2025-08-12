package io.testomat.service;

import io.testomat.exception.CliException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryValidatorTest {

    private DirectoryValidator directoryValidator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        directoryValidator = new DirectoryValidator();
    }

    @Test
    @DisplayName("Should throw CliException when directory is null")
    void shouldThrowExceptionWhenDirectoryIsNull() {
        // When & Then
        CliException exception = assertThrows(CliException.class, 
                () -> directoryValidator.validateDirectory(null));
        
        assertEquals("Directory cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw CliException when directory does not exist")
    void shouldThrowExceptionWhenDirectoryDoesNotExist() {
        // Given
        File nonExistentDirectory = new File("/path/that/does/not/exist");
        
        // When & Then
        CliException exception = assertThrows(CliException.class, 
                () -> directoryValidator.validateDirectory(nonExistentDirectory));
        
        assertTrue(exception.getMessage().startsWith("Directory does not exist:"));
        assertTrue(exception.getMessage().contains(nonExistentDirectory.getAbsolutePath()));
    }

    @Test
    @DisplayName("Should throw CliException when path is not a directory")
    void shouldThrowExceptionWhenPathIsNotDirectory() throws IOException {
        // Given - create a regular file instead of directory
        Path filePath = tempDir.resolve("test-file.txt");
        Files.createFile(filePath);
        File file = filePath.toFile();
        
        // When & Then
        CliException exception = assertThrows(CliException.class, 
                () -> directoryValidator.validateDirectory(file));
        
        assertTrue(exception.getMessage().startsWith("Path is not directory:"));
        assertTrue(exception.getMessage().contains(file.getAbsolutePath()));
    }

    @Test
    @DisplayName("Should throw CliException when directory is not readable")
    void shouldThrowExceptionWhenDirectoryIsNotReadable() throws IOException {
        // Given - create directory without read permissions (Unix/Linux systems)
        Path unreadableDir = tempDir.resolve("unreadable-dir");
        Files.createDirectory(unreadableDir);
        
        // Try to make directory unreadable (this may not work on all systems)
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("-wx-wx-wx");
            Files.setPosixFilePermissions(unreadableDir, permissions);
            
            File directory = unreadableDir.toFile();
            
            // Only test if we successfully made it unreadable
            if (!directory.canRead()) {
                // When & Then
                CliException exception = assertThrows(CliException.class, 
                        () -> directoryValidator.validateDirectory(directory));
                
                assertTrue(exception.getMessage().startsWith("Cannot read directory:"));
                assertTrue(exception.getMessage().contains(directory.getAbsolutePath()));
            }
        } catch (UnsupportedOperationException e) {
            // Skip this test on file systems that don't support POSIX permissions
            // (like Windows or some cloud environments)
        } finally {
            // Restore permissions for cleanup
            try {
                Set<PosixFilePermission> restorePermissions = PosixFilePermissions.fromString("rwxrwxrwx");
                Files.setPosixFilePermissions(unreadableDir, restorePermissions);
            } catch (UnsupportedOperationException ignored) {
                // Ignore if POSIX permissions not supported
            }
        }
    }

    @Test
    @DisplayName("Should pass validation for valid readable directory")
    void shouldPassValidationForValidDirectory() {
        // Given
        File validDirectory = tempDir.toFile();
        
        // When & Then - should not throw any exception
        assertDoesNotThrow(() -> directoryValidator.validateDirectory(validDirectory));
    }

    @Test
    @DisplayName("Should pass validation for nested directory")
    void shouldPassValidationForNestedDirectory() throws IOException {
        // Given
        Path nestedDir = tempDir.resolve("nested").resolve("directory");
        Files.createDirectories(nestedDir);
        File directory = nestedDir.toFile();
        
        // When & Then - should not throw any exception
        assertDoesNotThrow(() -> directoryValidator.validateDirectory(directory));
    }

    @Test
    @DisplayName("Should pass validation for current directory")
    void shouldPassValidationForCurrentDirectory() {
        // Given
        File currentDirectory = new File(".");
        
        // When & Then - should not throw any exception
        assertDoesNotThrow(() -> directoryValidator.validateDirectory(currentDirectory));
    }

    @Test
    @DisplayName("Should include full path in error messages")
    void shouldIncludeFullPathInErrorMessages() throws IOException {
        // Given
        Path filePath = tempDir.resolve("not-a-directory.txt");
        Files.createFile(filePath);
        File file = filePath.toFile();
        String expectedPath = file.getAbsolutePath();
        
        // When
        CliException exception = assertThrows(CliException.class, 
                () -> directoryValidator.validateDirectory(file));
        
        // Then
        assertTrue(exception.getMessage().contains(expectedPath), 
                "Error message should contain full absolute path");
    }

    @Test
    @DisplayName("Should validate empty directory successfully")
    void shouldValidateEmptyDirectorySuccessfully() throws IOException {
        // Given
        Path emptyDir = tempDir.resolve("empty-directory");
        Files.createDirectory(emptyDir);
        File directory = emptyDir.toFile();
        
        // When & Then - should not throw any exception
        assertDoesNotThrow(() -> directoryValidator.validateDirectory(directory));
    }

    @Test
    @DisplayName("Should validate directory with files successfully")
    void shouldValidateDirectoryWithFilesSuccessfully() throws IOException {
        // Given
        Path dirWithFiles = tempDir.resolve("directory-with-files");
        Files.createDirectory(dirWithFiles);
        Files.createFile(dirWithFiles.resolve("file1.txt"));
        Files.createFile(dirWithFiles.resolve("file2.java"));
        File directory = dirWithFiles.toFile();
        
        // When & Then - should not throw any exception
        assertDoesNotThrow(() -> directoryValidator.validateDirectory(directory));
    }
}