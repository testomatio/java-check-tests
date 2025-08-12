package io.testomat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class PathUtilsTest {

    @Test
    @DisplayName("Should return default filename when filepath is null")
    void shouldReturnDefaultFilenameWhenFilepathIsNull() {
        // When
        String result = PathUtils.extractRelativeFilePath(null);

        // Then
        assertEquals("UnknownFile.java", result);
    }

    @Test
    @DisplayName("Should return default filename when filepath is empty")
    void shouldReturnDefaultFilenameWhenFilepathIsEmpty() {
        // When
        String result = PathUtils.extractRelativeFilePath("");

        // Then
        assertEquals("UnknownFile.java", result);
    }

    @Test
    @DisplayName("Should extract relative path from src/test/java structure")
    void shouldExtractRelativePathFromSrcTestJava() {
        // Given
        String filepath = "/home/user/project/src/test/java/com/example/TestClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/TestClass.java", result);
    }

    @Test
    @DisplayName("Should extract relative path from src/main/java structure")
    void shouldExtractRelativePathFromSrcMainJava() {
        // Given
        String filepath = "/home/user/project/src/main/java/com/example/MainClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/MainClass.java", result);
    }

    @Test
    @DisplayName("Should handle Windows-style paths with src/test/java")
    void shouldHandleWindowsStylePathsWithSrcTestJava() {
        // Given
        String filepath = "C:\\Users\\user\\project\\src\\test\\java\\com\\example\\TestClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/TestClass.java", result);
    }

    @Test
    @DisplayName("Should handle Windows-style paths with src/main/java")
    void shouldHandleWindowsStylePathsWithSrcMainJava() {
        // Given
        String filepath = "C:\\Users\\user\\project\\src\\main\\java\\com\\example\\MainClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/MainClass.java", result);
    }

    @Test
    @DisplayName("Should prefer src/test/java over src/main/java when both present")
    void shouldPreferSrcTestJavaOverSrcMainJava() {
        // Given - unusual but possible path with both patterns
        String filepath = "/project/src/main/java/src/test/java/com/example/TestClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/TestClass.java", result);
    }

    @Test
    @DisplayName("Should extract from generic src with java structure")
    void shouldExtractFromGenericSrcWithJavaStructure() {
        // Given
        String filepath = "/project/src/integration/java/com/example/IntegrationTest.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/IntegrationTest.java", result);
    }

    @Test
    @DisplayName("Should use last java occurrence for generic src structure")
    void shouldUseLastJavaOccurrenceForGenericSrcStructure() {
        // Given
        String filepath = "/project/src/java/src/custom/java/com/example/CustomTest.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/CustomTest.java", result);
    }

    @Test
    @DisplayName("Should return normalized path when no standard structure found")
    void shouldReturnNormalizedPathWhenNoStandardStructureFound() {
        // Given
        String filepath = "/random/path/to/SomeClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        // Result depends on OS normalization, but should not contain backslashes
        assertFalse(result.contains("\\"), "Result should not contain backslashes");
        assertTrue(result.contains("SomeClass.java"), "Result should contain the filename");
        assertTrue(result.contains("random/path/to") || result.contains("path/to"),
                "Result should contain path components");
    }

    @Test
    @DisplayName("Should return normalized path for relative paths without standard structure")
    void shouldReturnNormalizedPathForRelativePathsWithoutStandardStructure() {
        // Given
        String filepath = "custom/directory/SomeClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("custom/directory/SomeClass.java", result);
    }

    @Test
    @DisplayName("Should handle path with only src but no java directory")
    void shouldHandlePathWithOnlySrcButNoJavaDirectory() {
        // Given
        String filepath = "/project/src/main/resources/config.properties";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        // Result depends on OS normalization
        assertFalse(result.contains("\\"), "Result should not contain backslashes");
        assertTrue(result.contains("config.properties"), "Result should contain the filename");
        assertTrue(result.contains("src/main/resources") || result.contains("project/src/main/resources"),
                "Result should contain path components");
    }

    @Test
    @DisplayName("Should handle path with only java but no src directory")
    void shouldHandlePathWithOnlyJavaButNoSrcDirectory() {
        // Given
        String filepath = "/random/java/com/example/SomeClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        // Result depends on OS normalization
        assertFalse(result.contains("\\"), "Result should not contain backslashes");
        assertTrue(result.contains("SomeClass.java"), "Result should contain the filename");
        assertTrue(result.contains("java/com/example") || result.contains("random/java/com/example"),
                "Result should contain path components");
    }

    @Test
    @DisplayName("Should handle complex nested paths with multiple src directories")
    void shouldHandleComplexNestedPathsWithMultipleSrcDirectories() {
        // Given
        String filepath = "/project/module1/src/main/java/module2/src/test/java/com/example/Test.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/Test.java", result);
    }

    @Test
    @DisplayName("Should handle paths with dots and special characters")
    void shouldHandlePathsWithDotsAndSpecialCharacters() {
        // Given
        String filepath = "/project/../src/test/java/com/example/Test$Inner.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/Test$Inner.java", result);
    }

    @Test
    @DisplayName("Should handle Maven-style multi-module project structure")
    void shouldHandleMavenStyleMultiModuleProjectStructure() {
        // Given
        String filepath = "/project/module-name/src/test/java/com/company/module/TestClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/company/module/TestClass.java", result);
    }

    @Test
    @DisplayName("Should handle Gradle-style project structure")
    void shouldHandleGradleStyleProjectStructure() {
        // Given
        String filepath = "/project/subproject/src/main/java/com/company/app/MainClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/company/app/MainClass.java", result);
    }

    @Test
    @DisplayName("Should handle file in package root")
    void shouldHandleFileInPackageRoot() {
        // Given
        String filepath = "/project/src/test/java/TestClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("TestClass.java", result);
    }

    @Test
    @DisplayName("Should handle deeply nested package structure")
    void shouldHandleDeeplyNestedPackageStructure() {
        // Given
        String filepath = "/project/src/main/java/com/company/product/module/service/impl/ServiceImpl.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/company/product/module/service/impl/ServiceImpl.java", result);
    }

    @Test
    @DisplayName("Should handle mixed forward and backward slashes")
    void shouldHandleMixedForwardAndBackwardSlashes() {
        // Given
        String filepath = "C:\\project/src\\test/java\\com/example\\TestClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/TestClass.java", result);
        assertFalse(result.contains("\\"), "Result should not contain backslashes");
    }

    @Test
    @DisplayName("Should handle case sensitivity in directory names")
    void shouldHandleCaseSensitivityInDirectoryNames() {
        // Given
        String filepath = "/project/SRC/TEST/JAVA/com/example/TestClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        // Since the method looks for exact matches "src/test/java", this should return the normalized path
        assertFalse(result.contains("\\"), "Result should not contain backslashes");
        assertTrue(result.contains("TestClass.java"), "Result should contain the filename");
        assertTrue(result.contains("SRC/TEST/JAVA") || result.contains("project/SRC/TEST/JAVA"),
                "Result should contain original case since no standard structure matched");
    }

    @Test
    @DisplayName("Should handle path normalization with current directory references")
    void shouldHandlePathNormalizationWithCurrentDirectoryReferences() {
        // Given
        String filepath = "/project/./src/test/java/./com/example/TestClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/TestClass.java", result);
    }

    @Test
    @DisplayName("Should handle relative paths consistently")
    void shouldHandleRelativePathsConsistently() {
        // Given
        String filepath = "src/test/java/com/example/TestClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/TestClass.java", result);
    }

    @Test
    @DisplayName("Should handle paths without leading slash")
    void shouldHandlePathsWithoutLeadingSlash() {
        // Given
        String filepath = "project/src/main/java/com/example/MainClass.java";

        // When
        String result = PathUtils.extractRelativeFilePath(filepath);

        // Then
        assertEquals("com/example/MainClass.java", result);
    }
}