package io.testomat.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.testomat.client.CliClient;
import io.testomat.client.TestomatHttpClient;
import io.testomat.exception.CliException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine;

@CommandLine.Command(name = "pull-ids", description =
        "Pulls IDs into your codebase from testomat.io")
public class PullIdsCommand implements Runnable {

    private static final int PATH_INDEX = 0;
    private static final int CLASS_NAME_INDEX = 1;
    private static final int METHOD_NAME_INDEX = 2;
    private static final int EXPECTED_PARTS_COUNT = 3;
    private static final String JAVA_EXTENSION = ".java";
    private static final String SPLIT_DELIMITER = "#";
    private static final String TEST_ID_IMPORT = "io.testomat.core.annotation.TestId";
    private static final String TEST_ID_ANNOTATION = "TestId";
    private static final String TEST_ID_PREFIX = "@T";
    private static final String TESTS_FIELD = "tests";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TestomatHttpClient httpClient;
    private final JavaParser javaParser;

    @CommandLine.Option(
            names = {"--directory", "-d"},
            defaultValue = ".")
    private String directory;

    @CommandLine.Option(
            names = {"--apikey", "-key"},
            description = "Testomat project api key",
            defaultValue = "${env:TESTOMATIO}",
            required = true)
    private String apiKey;

    @CommandLine.Option(
            names = "--url",
            description = "Testomat server URL",
            defaultValue = "${env:TESTOMATIO_URL}")
    private String serverUrl;

    public PullIdsCommand() {
        this.httpClient = new CliClient();
        this.javaParser = new JavaParser();
    }

    public PullIdsCommand(TestomatHttpClient httpClient, JavaParser javaParser,
                          String directory, String apiKey, String serverUrl) {
        this.httpClient = httpClient;
        this.javaParser = javaParser;
        this.directory = directory;
        this.apiKey = apiKey;
        this.serverUrl = serverUrl;
    }

    public static void main(String[] args) {
        CommandLine.run(new PullIdsCommand(), args);
    }

    @Override
    public void run() {
        String response = httpClient.sendGetRequest(apiKey, serverUrl);
        Map<String, String> testsMap = parseTestsFromResponse(response);
        List<CompilationUnit> compilationUnits = loadCompilationUnits();

        int processedCount = processTestMethods(compilationUnits, testsMap);
        saveModifiedFiles(compilationUnits);

        System.out.println("Processed " + processedCount + " test methods");
        System.out.println("Saved modified files");
    }

    private Map<String, String> parseTestsFromResponse(String response) {
        JsonNode rootNode = parseJsonResponse(response);
        JsonNode testsNode = extractTestsNode(rootNode);
        Map<String, String> testsMap = convertTestsNodeToMap(testsNode);

        validateTestsMap(testsMap);

        return filterJavaTests(testsMap);
    }

    private JsonNode parseJsonResponse(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (JsonProcessingException e) {
            throw new CliException(e.getMessage(), e.getCause());
        }
    }

    private JsonNode extractTestsNode(JsonNode rootNode) {
        JsonNode testsNode = rootNode.get(TESTS_FIELD);

        if (testsNode == null) {
            throw new CliException("Response does not contain '" + TESTS_FIELD + "' field");
        }

        if (!testsNode.isObject()) {
            throw new CliException("'" + TESTS_FIELD + "' field is not a JSON object");
        }

        return testsNode;
    }

    private Map<String, String> convertTestsNodeToMap(JsonNode testsNode) {
        try {
            return objectMapper.convertValue(testsNode, new TypeReference<Map<String, String>>() {
            });
        } catch (IllegalArgumentException e) {
            throw new CliException("Failed to convert tests data: " + e.getMessage(), e);
        }
    }

    private void validateTestsMap(Map<String, String> testsMap) {
        if (testsMap == null || testsMap.isEmpty()) {
            throw new CliException("No tests found in response");
        }
    }

    private Map<String, String> filterJavaTests(Map<String, String> testsMap) {
        return testsMap.entrySet().stream()
                .filter(entry -> entry.getKey().contains(JAVA_EXTENSION))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<CompilationUnit> loadCompilationUnits() {
        List<Path> javaFiles = findJavaFiles();
        List<CompilationUnit> compilationUnits = parseJavaFiles(javaFiles);

        if (compilationUnits.isEmpty()) {
            throw new CliException("No compilation units found");
        }

        return compilationUnits;
    }

    private List<Path> findJavaFiles() {

        try (Stream<Path> pathStream = Files.walk(Paths.get(directory))) {
            return pathStream
                    .filter(path -> path.toString().endsWith(JAVA_EXTENSION))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new CliException("Failed to scan directory for Java files", e);
        }
    }

    private List<CompilationUnit> parseJavaFiles(List<Path> javaFiles) {
        return javaFiles.stream()
                .map(this::parseJavaFile)
                .collect(Collectors.toList());
    }

    private CompilationUnit parseJavaFile(Path javaFile) {
        validateFileAccess(javaFile);

        try {
            return javaParser.parse(javaFile)
                    .getResult()
                    .orElseThrow(() -> new CliException("Could not parse file " + javaFile));
        } catch (IOException e) {
            throw new CliException("Could not parse file " + javaFile, e);
        }
    }

    private void validateFileAccess(Path file) {
        if (!Files.exists(file)) {
            throw new CliException("File does not exist: " + file);
        }

        if (!Files.isRegularFile(file)) {
            throw new CliException("Path is not a regular file: " + file);
        }

        if (!Files.isReadable(file)) {
            throw new CliException("File is not readable: " + file);
        }
    }

    private int processTestMethods(List<CompilationUnit> compilationUnits,
                                   Map<String, String> testsMap) {
        int processedCount = 0;

        for (Map.Entry<String, String> testEntry : testsMap.entrySet()) {
            String testKey = testEntry.getKey();
            String testId = testEntry.getValue();

            TestMethodInfo methodInfo = parseTestKey(testKey);
            if (methodInfo == null) {
                continue;
            }

            Optional<MethodDeclaration> methodOptional =
                    findMethodInCompilationUnits(compilationUnits, methodInfo);

            if (methodOptional.isPresent()) {
                MethodDeclaration method = methodOptional.get();
                CompilationUnit compilationUnit = method.findCompilationUnit().orElse(null);

                if (compilationUnit != null) {
                    addTestIdAnnotationToMethod(method, testId);
                    ensureTestIdImportExists(compilationUnit);
                    processedCount++;
                }
            }
        }

        return processedCount;
    }

    private TestMethodInfo parseTestKey(String testKey) {
        String[] parts = testKey.split(SPLIT_DELIMITER);

        if (parts.length != EXPECTED_PARTS_COUNT) {
            return null;
        }

        return new TestMethodInfo(
                parts[PATH_INDEX],
                parts[CLASS_NAME_INDEX],
                parts[METHOD_NAME_INDEX]
        );
    }

    private Optional<MethodDeclaration> findMethodInCompilationUnits(
            List<CompilationUnit> compilationUnits, TestMethodInfo methodInfo) {
        String expectedFileName = extractFileName(methodInfo.filePath);

        return compilationUnits.stream()
                .filter(cu -> isMatchingFile(cu, expectedFileName))
                .flatMap(cu -> findMethodsInCompilationUnit(cu, methodInfo).stream())
                .findFirst();
    }

    private boolean isMatchingFile(CompilationUnit compilationUnit, String expectedFileName) {
        return compilationUnit.getStorage()
                .map(storage -> storage.getPath().getFileName()
                        .toString().equals(expectedFileName))
                .orElse(false);
    }

    private List<MethodDeclaration> findMethodsInCompilationUnit(CompilationUnit compilationUnit,
                                                                 TestMethodInfo methodInfo) {
        return compilationUnit.findAll(MethodDeclaration.class)
                .stream()
                .filter(method -> method.getNameAsString().equals(methodInfo.methodName))
                .filter(method -> isMethodInCorrectClass(method, methodInfo.className))
                .collect(Collectors.toList());
    }

    private boolean isMethodInCorrectClass(MethodDeclaration method, String expectedClassName) {
        return method.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(classDecl -> classDecl.getNameAsString().equals(expectedClassName))
                .orElse(false);
    }

    private String extractFileName(String filePath) {
        return Paths.get(filePath).getFileName().toString();
    }

    private void addTestIdAnnotationToMethod(MethodDeclaration method, String testId) {
        String cleanTestId = cleanTestIdValue(testId);
        Optional<AnnotationExpr> existingAnnotation =
                method.getAnnotationByName(TEST_ID_ANNOTATION);

        if (existingAnnotation.isPresent()) {
            updateExistingTestIdAnnotation(existingAnnotation.get(), method, cleanTestId);
        } else {
            addNewTestIdAnnotation(method, cleanTestId);
        }
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

    private void ensureTestIdImportExists(CompilationUnit compilationUnit) {
        boolean hasTestIdImport = compilationUnit.getImports().stream()
                .anyMatch(importDecl
                        -> TEST_ID_IMPORT.equals(importDecl.getNameAsString()));

        if (!hasTestIdImport) {
            ImportDeclaration testIdImport =
                    new ImportDeclaration(TEST_ID_IMPORT, false, false);
            compilationUnit.addImport(testIdImport);
        }
    }

    private void saveModifiedFiles(List<CompilationUnit> compilationUnits) {
        compilationUnits.forEach(cu ->
                cu.getStorage().ifPresent(CompilationUnit.Storage::save)
        );
    }

    private static class TestMethodInfo {
        private final String filePath;
        private final String className;
        private final String methodName;

        TestMethodInfo(String filePath, String className, String methodName) {
            this.filePath = filePath;
            this.className = className;
            this.methodName = methodName;
        }
    }
}
