package io.testomat.service;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.testomat.model.CleanupResult;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AnnotationCleaner {

    private static final String TEST_ID_IMPORT = "io.testomat.core.annotation.TestId";
    private static final String TEST_ID_ANNOTATION = "TestId";

    private final MinimalFileModificationService fileModificationService;

    public AnnotationCleaner() {
        this.fileModificationService = new MinimalFileModificationService();
    }

    public CleanupResult cleanTestIdAnnotations(CompilationUnit cu, boolean dryRun) {
        List<AnnotationExpr> testIdAnnotations = findTestIdAnnotations(cu);
        List<ImportDeclaration> testIdImports = findTestIdImports(cu);

        if (!dryRun) {
            // Try to use minimal modification service if possible (real files)
            // Otherwise fall back to direct AST modification (tests, in-memory CUs)
            if (cu.getStorage().isPresent() && canUseMinimalModification(testIdAnnotations)) {
                try {
                    applyMinimalModifications(cu, testIdAnnotations, testIdImports);
                } catch (Exception e) {
                    // Fallback to direct modification if minimal modification fails
                    applyDirectModifications(testIdAnnotations, testIdImports);
                }
            } else {
                // Direct modification for in-memory CompilationUnits
                applyDirectModifications(testIdAnnotations, testIdImports);
            }
        }

        return new CleanupResult(testIdAnnotations.size(), testIdImports.size());
    }

    private boolean canUseMinimalModification(List<AnnotationExpr> annotations) {
        // Check if all annotations have positions (required for minimal modification)
        return annotations.stream().allMatch(ann -> ann.getBegin().isPresent());
    }

    private void applyMinimalModifications(CompilationUnit cu,
                                          List<AnnotationExpr> annotations,
                                          List<ImportDeclaration> imports) {
        MinimalFileModificationService.FileModification modification =
                new MinimalFileModificationService.FileModification(cu);

        for (AnnotationExpr annotation : annotations) {
            modification.removeAnnotation(annotation);
        }

        for (ImportDeclaration importDecl : imports) {
            modification.removeImport(importDecl);
        }

        if (modification.hasModifications()) {
            fileModificationService.applyModifications(modification);
        }
    }

    private void applyDirectModifications(List<AnnotationExpr> annotations,
                                         List<ImportDeclaration> imports) {
        // Direct AST modification (used for tests and fallback)
        annotations.forEach(AnnotationExpr::remove);
        imports.forEach(ImportDeclaration::remove);
    }

    private List<AnnotationExpr> findTestIdAnnotations(CompilationUnit cu) {
        List<AnnotationExpr> testIdAnnotations = new ArrayList<>();

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            for (AnnotationExpr annotation : method.getAnnotations()) {
                if (TEST_ID_ANNOTATION.equals(annotation.getNameAsString())) {
                    testIdAnnotations.add(annotation);
                }
            }
        }

        return testIdAnnotations;
    }

    private List<ImportDeclaration> findTestIdImports(CompilationUnit cu) {
        return cu.getImports().stream()
                .filter(importDecl ->
                        TEST_ID_IMPORT.equals(importDecl.getNameAsString()))
                .collect(Collectors.toList());
    }
}
