package io.testomat.service;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class TestIdAnnotationManager {

    private static final String TEST_ID_IMPORT = "io.testomat.core.annotation.TestId";
    private static final String TEST_ID_ANNOTATION = "TestId";
    private static final String TEST_ID_PREFIX = "@T";

    public Optional<MethodDeclaration> findMethodInCompilationUnits(
            List<CompilationUnit> compilationUnits, TestMethodInfo methodInfo) {
        String expectedFileName = extractFileName(methodInfo.getFilePath());

        return compilationUnits.stream()
                .filter(cu -> isMatchingFile(cu, expectedFileName))
                .flatMap(cu -> findMethodsInCompilationUnit(cu, methodInfo).stream())
                .findFirst();
    }

    public void addTestIdAnnotationToMethod(MethodDeclaration method, String testId) {
        String cleanTestId = cleanTestIdValue(testId);
        Optional<AnnotationExpr> existingAnnotation =
                method.getAnnotationByName(TEST_ID_ANNOTATION);

        if (existingAnnotation.isPresent()) {
            updateExistingTestIdAnnotation(existingAnnotation.get(), method, cleanTestId);
        } else {
            addNewTestIdAnnotation(method, cleanTestId);
        }
    }

    public void ensureTestIdImportExists(CompilationUnit compilationUnit) {
        boolean hasTestIdImport = compilationUnit.getImports().stream()
                .anyMatch(importDecl ->
                        TEST_ID_IMPORT.equals(importDecl.getNameAsString()));

        if (!hasTestIdImport) {
            ImportDeclaration testIdImport =
                    new ImportDeclaration(TEST_ID_IMPORT, false, false);
            compilationUnit.addImport(testIdImport);
        }
    }

    private boolean isMatchingFile(CompilationUnit compilationUnit, String expectedFileName) {
        return compilationUnit.getStorage()
                .map(storage -> storage.getPath()
                        .getFileName()
                        .toString()
                        .equals(expectedFileName))
                .orElse(false);
    }

    private List<MethodDeclaration> findMethodsInCompilationUnit(CompilationUnit compilationUnit,
                                                                 TestMethodInfo methodInfo) {
        return compilationUnit.findAll(MethodDeclaration.class)
                .stream()
                .filter(method ->
                        method.getNameAsString().equals(methodInfo.getMethodName()))
                .filter(method ->
                        isMethodInCorrectClass(method, methodInfo.getClassName()))
                .collect(Collectors.toList());
    }

    private boolean isMethodInCorrectClass(MethodDeclaration method, String expectedClassName) {
        return method.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(classDecl ->
                        classDecl.getNameAsString().equals(expectedClassName))
                .orElse(false);
    }

    private String extractFileName(String filePath) {
        return java.nio.file.Paths.get(filePath).getFileName().toString();
    }

    private String cleanTestIdValue(String testId) {
        return testId.replace(TEST_ID_PREFIX, "");
    }

    private void updateExistingTestIdAnnotation(AnnotationExpr existingAnnotation,
                                                MethodDeclaration method, String cleanTestId) {
        if (existingAnnotation.isSingleMemberAnnotationExpr()) {
            updateSingleMemberAnnotation(existingAnnotation.asSingleMemberAnnotationExpr(),
                    cleanTestId);
        } else {
            replaceAnnotation(method, cleanTestId);
        }
    }

    private void updateSingleMemberAnnotation(SingleMemberAnnotationExpr annotation,
                                              String cleanTestId) {
        Expression currentValue = annotation.getMemberValue();

        if (currentValue.isStringLiteralExpr()) {
            StringLiteralExpr stringLiteral = currentValue.asStringLiteralExpr();
            if (!stringLiteral.getValue().equals(cleanTestId)) {
                annotation.setMemberValue(new StringLiteralExpr(cleanTestId));
            }
        } else {
            annotation.setMemberValue(new StringLiteralExpr(cleanTestId));
        }
    }

    private void replaceAnnotation(MethodDeclaration method, String cleanTestId) {
        method.getAnnotationByName(TEST_ID_ANNOTATION).ifPresent(method::remove);
        addNewTestIdAnnotation(method, cleanTestId);
    }

    private void addNewTestIdAnnotation(MethodDeclaration method, String cleanTestId) {
        SingleMemberAnnotationExpr newAnnotation = new SingleMemberAnnotationExpr(
                new Name(TEST_ID_ANNOTATION),
                new StringLiteralExpr(cleanTestId)
        );
        method.addAnnotation(newAnnotation);
    }

    public static class TestMethodInfo {
        private final String filePath;
        private final String className;
        private final String methodName;

        public TestMethodInfo(String filePath, String className, String methodName) {
            this.filePath = filePath;
            this.className = className;
            this.methodName = methodName;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getClassName() {
            return className;
        }

        public String getMethodName() {
            return methodName;
        }
    }
}
