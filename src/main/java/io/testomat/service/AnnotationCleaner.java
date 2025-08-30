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

    public CleanupResult cleanTestIdAnnotations(CompilationUnit cu, boolean dryRun) {
        int removedAnnotations = removeTestIdAnnotations(cu, dryRun);
        int removedImports = removeTestIdImports(cu, dryRun);

        return new CleanupResult(removedAnnotations, removedImports);
    }

    private int removeTestIdAnnotations(CompilationUnit cu, boolean dryRun) {
        List<AnnotationExpr> testIdAnnotations = findTestIdAnnotations(cu);

        if (!dryRun) {
            testIdAnnotations.forEach(AnnotationExpr::remove);
        }

        return testIdAnnotations.size();
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

    private int removeTestIdImports(CompilationUnit cu, boolean dryRun) {
        List<ImportDeclaration> testIdImports = findTestIdImports(cu);

        if (!dryRun) {
            findTestIdImports(cu).forEach(ImportDeclaration::remove);
        }

        return testIdImports.size();
    }

    private List<ImportDeclaration> findTestIdImports(CompilationUnit cu) {
        return cu.getImports().stream()
                .filter(importDecl ->
                        TEST_ID_IMPORT.equals(importDecl.getNameAsString()))
                .collect(Collectors.toList());
    }
}
