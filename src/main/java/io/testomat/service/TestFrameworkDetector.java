package io.testomat.service;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

public class TestFrameworkDetector {

    public String detectFramework(CompilationUnit cu) {
        String framework = detectFromImports(cu);
        if (framework != null) {
            return framework;
        }

        framework = detectFromAnnotations(cu);
        if (framework != null) {
            return framework;
        }

        return detectFromPatterns(cu);
    }

    private String detectFromImports(CompilationUnit cu) {
        for (ImportDeclaration imp : cu.getImports()) {
            String importName = imp.getNameAsString();

            if (importName.startsWith("org.junit.jupiter")) {
                return "junit";
            }

            if (importName.equals("org.junit.Test")
                    || importName.startsWith("org.junit.")
                    && !importName.startsWith("org.junit.jupiter")) {
                return "junit";
            }

            if (importName.startsWith("org.testng")) {
                return "testng";
            }

            if (importName.startsWith("org.springframework.test")
                    && hasJUnitImports(cu)) {
                return "junit";
            }
        }
        return null;
    }

    private String detectFromAnnotations(CompilationUnit cu) {
        for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            String framework = checkClassAnnotations(clazz);
            if (framework != null) {
                return framework;
            }
        }

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            String framework = checkMethodAnnotations(method);
            if (framework != null) {
                return framework;
            }
        }

        return null;
    }

    private String checkClassAnnotations(ClassOrInterfaceDeclaration clazz) {
        for (AnnotationExpr annotation : clazz.getAnnotations()) {
            String annName = annotation.getNameAsString();

            if ("SpringBootTest".equals(annName)
                    || "WebMvcTest".equals(annName)
                    || "DataJpaTest".equals(annName)
                    || "JsonTest".equals(annName)) {
                return "junit";
            }

            if ("Test".equals(annName)) {
                return detectFromAnnotationContext(clazz.findCompilationUnit().orElse(null));
            }
        }

        return null;
    }

    private String checkMethodAnnotations(MethodDeclaration method) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String annName = annotation.getNameAsString();

            if ("ParameterizedTest".equals(annName)
                    || "RepeatedTest".equals(annName)
                    || "TestFactory".equals(annName)
                    || "DisplayName".equals(annName)
                    || "BeforeEach".equals(annName)
                    || "AfterEach".equals(annName)
                    || "BeforeAll".equals(annName)
                    || "AfterAll".equals(annName)) {
                return "junit";
            }

            if ("DataProvider".equals(annName)
                    || "BeforeMethod".equals(annName)
                    || "AfterMethod".equals(annName)
                    || "BeforeClass".equals(annName)
                    || "AfterClass".equals(annName)
                    || "BeforeTest".equals(annName)
                    || "AfterTest".equals(annName)
                    || "BeforeSuite".equals(annName)
                    || "AfterSuite".equals(annName)) {
                return "testng";
            }

            if ("Test".equals(annName)) {
                return detectFromAnnotationContext(method.findCompilationUnit().orElse(null));
            }
        }

        return null;
    }

    private String detectFromPatterns(CompilationUnit cu) {
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            String methodName = method.getNameAsString();
            if (methodName.contains("dataProvider")
                    || methodName.contains("DataProvider")) {
                return "testng";
            }
        }

        return null;
    }

    private String detectFromAnnotationContext(CompilationUnit cu) {
        if (cu == null) {
            return "junit";
        }

        boolean hasJunit5 = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().startsWith("org.junit.jupiter"));

        if (hasJunit5) {
            return "junit";
        }

        boolean hasTestNG = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().startsWith("org.testng"));

        if (hasTestNG) {
            return "testng";
        }

        boolean hasJunit5Annotations = cu.findAll(MethodDeclaration.class).stream()
                .flatMap(m -> m.getAnnotations().stream())
                .anyMatch(ann -> {
                    String name = ann.getNameAsString();
                    return "ParameterizedTest".equals(name)
                            || "RepeatedTest".equals(name)
                            || "TestFactory".equals(name)
                            || "DisplayName".equals(name);
                });

        if (hasJunit5Annotations) {
            return "junit";
        }

        boolean hasTestNgAnnotations = cu.findAll(MethodDeclaration.class).stream()
                .flatMap(m -> m.getAnnotations().stream())
                .anyMatch(ann -> {
                    String name = ann.getNameAsString();
                    return "DataProvider".equals(name)
                            || "BeforeMethod".equals(name)
                            || "AfterMethod".equals(name);
                });

        if (hasTestNgAnnotations) {
            return "testng";
        }

        return "junit";
    }

    private boolean hasJUnitImports(CompilationUnit cu) {
        return cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().startsWith("org.junit"));
    }
}
