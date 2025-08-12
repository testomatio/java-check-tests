package io.testomat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnnotationCleanerTest {

    private AnnotationCleaner annotationCleaner;
    private JavaParser javaParser;

    private static final String TEST_CLASS_WITH_ANNOTATIONS =
            "package com.library.model.junit;\n" +
                    "\n" +
                    "import static org.junit.jupiter.api.Assertions.assertTrue;\n" +
                    "import org.junit.jupiter.api.Disabled;\n" +
                    "import org.junit.jupiter.api.Test;\n" +
                    "import org.junit.jupiter.api.parallel.Execution;\n" +
                    "import org.junit.jupiter.api.parallel.ExecutionMode;\n" +
                    "import io.testomat.core.annotation.TestId;\n" +
                    "\n" +
                    "@Execution(ExecutionMode.CONCURRENT)\n" +
                    "class Class1Test {\n" +
                    "\n" +
                    "    @Test\n" +
                    "    @Execution(ExecutionMode.CONCURRENT)\n" +
                    "    @Disabled\n" +
                    "    @TestId(\"d4bf3020\")\n" +
                    "    void asyncTest1() throws Exception {\n" +
                    "        Thread.sleep(25000);\n" +
                    "        assertTrue(false);\n" +
                    "    }\n" +
                    "\n" +
                    "    @Test\n" +
                    "    @Execution(ExecutionMode.CONCURRENT)\n" +
                    "    @Disabled\n" +
                    "    @TestId(\"1806f3f4\")\n" +
                    "    void asyncTest2() throws Exception {\n" +
                    "        Thread.sleep(2000);\n" +
                    "        assertTrue(false);\n" +
                    "    }\n" +
                    "\n" +
                    "    @Test\n" +
                    "    @Execution(ExecutionMode.CONCURRENT)\n" +
                    "    @TestId(\"e616c063\")\n" +
                    "    void asyncTest3() throws Exception {\n" +
                    "        Thread.sleep(3000);\n" +
                    "        assertTrue(false);\n" +
                    "    }\n" +
                    "}";

    private static final String TEST_CLASS_WITHOUT_ANNOTATIONS =
            "package com.library.model.junit;\n" +
                    "\n" +
                    "import static org.junit.jupiter.api.Assertions.assertTrue;\n" +
                    "import org.junit.jupiter.api.Test;\n" +
                    "import io.testomat.core.annotation.TestId;\n" +
                    "\n" +
                    "class ClassWithoutTestId {\n" +
                    "\n" +
                    "    @Test\n" +
                    "    void simpleTest() {\n" +
                    "        assertTrue(true);\n" +
                    "    }\n" +
                    "}";

    private static final String TEST_CLASS_WITHOUT_IMPORT =
            "package com.library.model.junit;\n" +
                    "\n" +
                    "import static org.junit.jupiter.api.Assertions.assertTrue;\n" +
                    "import org.junit.jupiter.api.Test;\n" +
                    "\n" +
                    "class ClassWithoutImport {\n" +
                    "\n" +
                    "    @Test\n" +
                    "    @TestId(\"some-id\")\n" +
                    "    void testWithAnnotation() {\n" +
                    "        assertTrue(true);\n" +
                    "    }\n" +
                    "}";

    @BeforeEach
    void setUp() {
        annotationCleaner = new AnnotationCleaner();
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("Should count TestId annotations and imports in dry run mode")
    void shouldCountAnnotationsAndImportsInDryRun() {
        // Given
        CompilationUnit cu = parseCode(TEST_CLASS_WITH_ANNOTATIONS);

        // When
        AnnotationCleaner.CleanupResult result = annotationCleaner.cleanTestIdAnnotations(cu, true);

        // Then
        assertEquals(3, result.getRemovedAnnotations(), "Should count 3 @TestId annotations");
        assertEquals(1, result.getRemovedImports(), "Should count 1 TestId import");

        // Verify that annotations and imports are still present (dry run)
        assertEquals(3, countTestIdAnnotations(cu), "Annotations should still be present in dry run");
        assertEquals(1, countTestIdImports(cu), "Import should still be present in dry run");
    }

    @Test
    @DisplayName("Should remove TestId annotations and imports when not in dry run mode")
    void shouldRemoveAnnotationsAndImportsWhenNotDryRun() {
        // Given
        CompilationUnit cu = parseCode(TEST_CLASS_WITH_ANNOTATIONS);

        // When
        AnnotationCleaner.CleanupResult result = annotationCleaner.cleanTestIdAnnotations(cu, false);

        // Then
        assertEquals(3, result.getRemovedAnnotations(), "Should report 3 removed @TestId annotations");
        assertEquals(1, result.getRemovedImports(), "Should report 1 removed TestId import");

        // Verify that annotations and imports are actually removed
        assertEquals(0, countTestIdAnnotations(cu), "All @TestId annotations should be removed");
        assertEquals(0, countTestIdImports(cu), "TestId import should be removed");
    }

    @Test
    @DisplayName("Should handle class without TestId annotations but with import")
    void shouldHandleClassWithoutAnnotationsButWithImport() {
        // Given
        CompilationUnit cu = parseCode(TEST_CLASS_WITHOUT_ANNOTATIONS);

        // When
        AnnotationCleaner.CleanupResult result = annotationCleaner.cleanTestIdAnnotations(cu, false);

        // Then
        assertEquals(0, result.getRemovedAnnotations(), "Should report 0 removed annotations");
        assertEquals(1, result.getRemovedImports(), "Should report 1 removed import");

        assertEquals(0, countTestIdAnnotations(cu), "No annotations to remove");
        assertEquals(0, countTestIdImports(cu), "Import should be removed");
    }

    @Test
    @DisplayName("Should handle class with TestId annotations but without import")
    void shouldHandleClassWithAnnotationsButWithoutImport() {
        // Given
        CompilationUnit cu = parseCode(TEST_CLASS_WITHOUT_IMPORT);

        // When
        AnnotationCleaner.CleanupResult result = annotationCleaner.cleanTestIdAnnotations(cu, false);

        // Then
        assertEquals(1, result.getRemovedAnnotations(), "Should report 1 removed annotation");
        assertEquals(0, result.getRemovedImports(), "Should report 0 removed imports");

        assertEquals(0, countTestIdAnnotations(cu), "Annotation should be removed");
        assertEquals(0, countTestIdImports(cu), "No import to remove");
    }

    @Test
    @DisplayName("Should handle empty class")
    void shouldHandleEmptyClass() {
        // Given
        String emptyClass = "package com.example;\n\nclass EmptyClass {\n}";
        CompilationUnit cu = parseCode(emptyClass);

        // When
        AnnotationCleaner.CleanupResult result = annotationCleaner.cleanTestIdAnnotations(cu, false);

        // Then
        assertEquals(0, result.getRemovedAnnotations(), "Should report 0 removed annotations");
        assertEquals(0, result.getRemovedImports(), "Should report 0 removed imports");
    }

    @Test
    @DisplayName("Should preserve other annotations when removing TestId")
    void shouldPreserveOtherAnnotationsWhenRemovingTestId() {
        // Given
        CompilationUnit cu = parseCode(TEST_CLASS_WITH_ANNOTATIONS);
        int originalTestAnnotations = countTestAnnotations(cu);
        int originalDisabledAnnotations = countDisabledAnnotations(cu);

        // When
        annotationCleaner.cleanTestIdAnnotations(cu, false);

        // Then
        assertEquals(originalTestAnnotations, countTestAnnotations(cu),
                "@Test annotations should be preserved");
        assertEquals(originalDisabledAnnotations, countDisabledAnnotations(cu),
                "@Disabled annotations should be preserved");
    }

    @Test
    @DisplayName("Should preserve other imports when removing TestId import")
    void shouldPreserveOtherImportsWhenRemovingTestIdImport() {
        // Given
        CompilationUnit cu = parseCode(TEST_CLASS_WITH_ANNOTATIONS);
        int originalImports = cu.getImports().size();

        // When
        annotationCleaner.cleanTestIdAnnotations(cu, false);

        // Then
        assertEquals(originalImports - 1, cu.getImports().size(),
                "Only TestId import should be removed, others preserved");

        // Verify specific imports are still present
        assertTrue(hasImport(cu, "org.junit.jupiter.api.Test"),
                "JUnit Test import should be preserved");
        assertTrue(hasImport(cu, "org.junit.jupiter.api.Disabled"),
                "JUnit Disabled import should be preserved");

        // Verify TestId import is removed
        assertFalse(hasImport(cu, "io.testomat.core.annotation.TestId"),
                "TestId import should be removed");
    }

    @Test
    @DisplayName("CleanupResult should have correct getters")
    void cleanupResultShouldHaveCorrectGetters() {
        // Given
        AnnotationCleaner.CleanupResult result = new AnnotationCleaner.CleanupResult(5, 2);

        // Then
        assertEquals(5, result.getRemovedAnnotations());
        assertEquals(2, result.getRemovedImports());
    }

    // Helper methods

    private CompilationUnit parseCode(String code) {
        return javaParser.parse(code).getResult()
                .orElseThrow(() -> new RuntimeException("Failed to parse test code"));
    }

    private int countTestIdAnnotations(CompilationUnit cu) {
        return (int) cu.findAll(MethodDeclaration.class).stream()
                .flatMap(method -> method.getAnnotations().stream())
                .filter(annotation -> "TestId".equals(annotation.getNameAsString()))
                .count();
    }

    private int countTestIdImports(CompilationUnit cu) {
        return (int) cu.getImports().stream()
                .filter(importDecl -> "io.testomat.core.annotation.TestId".equals(importDecl.getNameAsString()))
                .count();
    }

    private int countTestAnnotations(CompilationUnit cu) {
        return (int) cu.findAll(MethodDeclaration.class).stream()
                .flatMap(method -> method.getAnnotations().stream())
                .filter(annotation -> "Test".equals(annotation.getNameAsString()))
                .count();
    }

    private int countDisabledAnnotations(CompilationUnit cu) {
        return (int) cu.findAll(MethodDeclaration.class).stream()
                .flatMap(method -> method.getAnnotations().stream())
                .filter(annotation -> "Disabled".equals(annotation.getNameAsString()))
                .count();
    }

    private boolean hasImport(CompilationUnit cu, String importName) {
        return cu.getImports().stream()
                .anyMatch(importDecl -> importName.equals(importDecl.getNameAsString()));
    }
}