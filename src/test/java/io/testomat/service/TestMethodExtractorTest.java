package io.testomat.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import io.testomat.model.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMethodExtractorTest {

    private TestMethodExtractor testMethodExtractor;
    private JavaParser javaParser;

    // JUnit test class with various annotations
    private static final String JUNIT_TEST_CLASS = 
            "package com.example.test;\n" +
            "\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import org.junit.jupiter.api.DisplayName;\n" +
            "import org.junit.jupiter.api.Disabled;\n" +
            "import org.junit.jupiter.api.ParameterizedTest;\n" +
            "import org.junit.jupiter.api.RepeatedTest;\n" +
            "import org.junit.jupiter.api.TestFactory;\n" +
            "import org.junit.jupiter.api.Tag;\n" +
            "import org.springframework.boot.test.context.SpringBootTest;\n" +
            "\n" +
            "@DisplayName(\"Sample Test Suite\")\n" +
            "class SampleTest {\n" +
            "\n" +
            "    @Test\n" +
            "    @DisplayName(\"Simple test case\")\n" +
            "    void simpleTest() {\n" +
            "        // Test implementation\n" +
            "    }\n" +
            "\n" +
            "    @Test\n" +
            "    @Disabled\n" +
            "    void disabledTest() {\n" +
            "        // Disabled test\n" +
            "    }\n" +
            "\n" +
            "    @ParameterizedTest\n" +
            "    @Tag(\"slow\")\n" +
            "    void parameterizedTest(String input) {\n" +
            "        // Parameterized test\n" +
            "    }\n" +
            "\n" +
            "    @RepeatedTest(5)\n" +
            "    void repeatedTest() {\n" +
            "        // Repeated test\n" +
            "    }\n" +
            "\n" +
            "    @TestFactory\n" +
            "    void dynamicTest() {\n" +
            "        // Dynamic test\n" +
            "    }\n" +
            "\n" +
            "    @SpringBootTest\n" +
            "    @Test\n" +
            "    void integrationTest() {\n" +
            "        // Integration test\n" +
            "    }\n" +
            "\n" +
            "    void nonTestMethod() {\n" +
            "        // Not a test method\n" +
            "    }\n" +
            "\n" +
            "    void ignoreThisTest() {\n" +
            "        // Method name starts with 'ignore'\n" +
            "    }\n" +
            "\n" +
            "    void skipThisTest() {\n" +
            "        // Method name starts with 'skip'\n" +
            "    }\n" +
            "\n" +
            "    @Test\n" +
            "    void smokeTestMethod() {\n" +
            "        // Method name contains 'smoke'\n" +
            "    }\n" +
            "\n" +
            "    /**\n" +
            "     * Test with comment labels\n" +
            "     * @smoke @performance:high #critical\n" +
            "     */\n" +
            "    @Test\n" +
            "    void testWithCommentLabels() {\n" +
            "        // Test with comment labels\n" +
            "    }\n" +
            "}";

    // TestNG test class
    private static final String TESTNG_TEST_CLASS = 
            "package com.example.testng;\n" +
            "\n" +
            "import org.testng.annotations.Test;\n" +
            "import org.testng.annotations.DataProvider;\n" +
            "import org.testng.annotations.BeforeMethod;\n" +
            "\n" +
            "class TestNGSample {\n" +
            "\n" +
            "    @Test\n" +
            "    void simpleTestNGTest() {\n" +
            "        // TestNG test\n" +
            "    }\n" +
            "\n" +
            "    @Test(groups = {\"smoke\", \"regression\"})\n" +
            "    void testWithGroups() {\n" +
            "        // TestNG test with groups\n" +
            "    }\n" +
            "\n" +
            "    @DataProvider\n" +
            "    void dataProviderMethod() {\n" +
            "        // Data provider\n" +
            "    }\n" +
            "\n" +
            "    @BeforeMethod\n" +
            "    void setupMethod() {\n" +
            "        // Setup method\n" +
            "    }\n" +
            "}";

    // Nested class test
    private static final String NESTED_CLASS_TEST = 
            "package com.example.nested;\n" +
            "\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import org.junit.jupiter.api.DisplayName;\n" +
            "import org.junit.jupiter.api.Nested;\n" +
            "\n" +
            "@DisplayName(\"Outer Test Class\")\n" +
            "class OuterTest {\n" +
            "\n" +
            "    @Test\n" +
            "    void outerTest() {\n" +
            "        // Outer test\n" +
            "    }\n" +
            "\n" +
            "    @Nested\n" +
            "    @DisplayName(\"Inner Test Class\")\n" +
            "    class InnerTest {\n" +
            "\n" +
            "        @Test\n" +
            "        void innerTest() {\n" +
            "            // Inner test\n" +
            "        }\n" +
            "    }\n" +
            "}";

    @BeforeEach
    void setUp() {
        testMethodExtractor = new TestMethodExtractor();
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("Should extract JUnit test cases correctly")
    void shouldExtractJUnitTestCasesCorrectly() {
        // Given
        CompilationUnit cu = parseCode(JUNIT_TEST_CLASS);
        String filepath = "src/test/java/com/example/test/SampleTest.java";
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, filepath, "junit");
        
        // Then
        assertEquals(8, testCases.size(), "Should extract 8 test methods");
        
        // Verify specific test cases
        TestCase simpleTest = findTestByMethodName(testCases, "simpleTest");
        assertNotNull(simpleTest);
        assertEquals("Simple test case", simpleTest.getName());
        assertFalse(simpleTest.isSkipped());
        assertTrue(simpleTest.getLabels().contains("unit"));
        
        TestCase disabledTest = findTestByMethodName(testCases, "disabledTest");
        assertNotNull(disabledTest);
        assertTrue(disabledTest.isSkipped());
        assertTrue(disabledTest.getLabels().contains("disabled"));
        
        TestCase parameterizedTest = findTestByMethodName(testCases, "parameterizedTest");
        assertNotNull(parameterizedTest);
        assertTrue(parameterizedTest.getLabels().contains("parameterized"));
        assertTrue(parameterizedTest.getLabels().contains("slow"));
    }

    @Test
    @DisplayName("Should extract TestNG test cases correctly")
    void shouldExtractTestNGTestCasesCorrectly() {
        // Given
        CompilationUnit cu = parseCode(TESTNG_TEST_CLASS);
        String filepath = "src/test/java/com/example/testng/TestNGSample.java";
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, filepath, "testng");
        
        // Then
        assertEquals(2, testCases.size(), "Should extract 2 test methods");
        
        TestCase simpleTest = findTestByMethodName(testCases, "simpleTestNGTest");
        assertNotNull(simpleTest);
        assertTrue(simpleTest.getLabels().contains("unit"));
        
        TestCase groupTest = findTestByMethodName(testCases, "testWithGroups");
        assertNotNull(groupTest);
        assertTrue(groupTest.getLabels().contains("smoke"));
        assertTrue(groupTest.getLabels().contains("regression"));
    }

    @Test
    @DisplayName("Should handle nested classes and suites correctly")
    void shouldHandleNestedClassesAndSuitesCorrectly() {
        // Given
        CompilationUnit cu = parseCode(NESTED_CLASS_TEST);
        String filepath = "src/test/java/com/example/nested/OuterTest.java";
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, filepath, "junit");
        
        // Then
        assertEquals(2, testCases.size());
        
        TestCase outerTest = findTestByMethodName(testCases, "outerTest");
        assertNotNull(outerTest);
        assertEquals(1, outerTest.getSuites().size());
        assertEquals("Outer Test Class", outerTest.getSuites().get(0));
        
        TestCase innerTest = findTestByMethodName(testCases, "innerTest");
        assertNotNull(innerTest);
        assertEquals(2, innerTest.getSuites().size());
        assertEquals("Outer Test Class", innerTest.getSuites().get(0));
        assertEquals("Inner Test Class", innerTest.getSuites().get(1));
    }

    @Test
    @DisplayName("Should detect skipped tests by annotation")
    void shouldDetectSkippedTestsByAnnotation() {
        // Given
        CompilationUnit cu = parseCode(JUNIT_TEST_CLASS);
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, "test.java", "junit");
        
        // Then
        TestCase disabledTest = findTestByMethodName(testCases, "disabledTest");
        assertNotNull(disabledTest);
        assertTrue(disabledTest.isSkipped(), "Test with @Disabled should be marked as skipped");
    }

    @Test
    @DisplayName("Should detect skipped tests by method name")
    void shouldDetectSkippedTestsByMethodName() {
        // Given
        CompilationUnit cu = parseCode(JUNIT_TEST_CLASS);
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, "test.java", "junit");
        
        // Then
        // Note: ignoreThisTest and skipThisTest are not actually test methods 
        // because they don't have @Test annotation, so they won't be in the results
        // Let's test with a proper test method that starts with ignore/skip
        
        String testClassWithSkippedNames = 
                "package com.example;\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "class SkipTest {\n" +
                "    @Test\n" +
                "    void ignoreThisTestMethod() {}\n" +
                "    @Test\n" +
                "    void skipThisTestMethod() {}\n" +
                "}";
        
        CompilationUnit cuWithSkipped = parseCode(testClassWithSkippedNames);
        List<TestCase> skippedTests = testMethodExtractor.extractTestCases(cuWithSkipped, "test.java", "junit");
        
        TestCase ignoreTest = findTestByMethodName(skippedTests, "ignoreThisTestMethod");
        TestCase skipTest = findTestByMethodName(skippedTests, "skipThisTestMethod");
        
        assertNotNull(ignoreTest);
        assertNotNull(skipTest);
        assertTrue(ignoreTest.isSkipped(), "Method starting with 'ignore' should be marked as skipped");
        assertTrue(skipTest.isSkipped(), "Method starting with 'skip' should be marked as skipped");
    }

    @Test
    @DisplayName("Should extract labels from method names")
    void shouldExtractLabelsFromMethodNames() {
        // Given
        CompilationUnit cu = parseCode(JUNIT_TEST_CLASS);
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, "test.java", "junit");
        
        // Then
        TestCase smokeTest = findTestByMethodName(testCases, "smokeTestMethod");
        assertNotNull(smokeTest);
        assertTrue(smokeTest.getLabels().contains("smoke"), "Should extract 'smoke' label from method name");
    }

    @Test
    @DisplayName("Should extract labels from comments")
    void shouldExtractLabelsFromComments() {
        // Given
        CompilationUnit cu = parseCode(JUNIT_TEST_CLASS);
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, "test.java", "junit");
        
        // Then
        TestCase commentTest = findTestByMethodName(testCases, "testWithCommentLabels");
        assertNotNull(commentTest);
        assertTrue(commentTest.getLabels().contains("smoke"), "Should extract @smoke from comment");
        assertTrue(commentTest.getLabels().contains("performance:high"), "Should extract @performance:high from comment");
        assertTrue(commentTest.getLabels().contains("critical"), "Should extract #critical from comment");
    }

    @Test
    @DisplayName("Should extract different framework-specific labels")
    void shouldExtractDifferentFrameworkSpecificLabels() {
        // Given
        CompilationUnit cu = parseCode(JUNIT_TEST_CLASS);
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, "test.java", "junit");
        
        // Then
        TestCase repeatedTest = findTestByMethodName(testCases, "repeatedTest");
        assertNotNull(repeatedTest);
        assertTrue(repeatedTest.getLabels().contains("repeated"));
        
        TestCase dynamicTest = findTestByMethodName(testCases, "dynamicTest");
        assertNotNull(dynamicTest);
        assertTrue(dynamicTest.getLabels().contains("dynamic"));
        
        TestCase integrationTest = findTestByMethodName(testCases, "integrationTest");
        assertNotNull(integrationTest);
        assertTrue(integrationTest.getLabels().contains("integration"));
    }

    @Test
    @DisplayName("Should generate correct test method code")
    void shouldGenerateCorrectTestMethodCode() {
        // Given
        String simpleTestClass = 
                "package com.example;\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "import org.junit.jupiter.api.DisplayName;\n" +
                "class SimpleTest {\n" +
                "    @Test\n" +
                "    @DisplayName(\"Simple test\")\n" +
                "    public void testMethod() throws Exception {\n" +
                "        System.out.println(\"test\");\n" +
                "    }\n" +
                "}";
        
        CompilationUnit cu = parseCode(simpleTestClass);
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, "test.java", "junit");
        
        // Then
        assertEquals(1, testCases.size());
        TestCase testCase = testCases.get(0);
        
        String code = testCase.getCode();
        assertTrue(code.contains("@Test"), "Code should contain @Test annotation");
        assertTrue(code.contains("@DisplayName(\"Simple test\")"), "Code should contain @DisplayName annotation");
        assertTrue(code.contains("public void testMethod()"), "Code should contain method signature");
        assertTrue(code.contains("throws Exception"), "Code should contain throws clause");
        assertTrue(code.contains("System.out.println(\"test\");"), "Code should contain method body");
    }

    @Test
    @DisplayName("Should set file path correctly")
    void shouldSetFilePathCorrectly() {
        // Given
        CompilationUnit cu = parseCode(JUNIT_TEST_CLASS);
        String filepath = "src/test/java/com/example/test/SampleTest.java";
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, filepath, "junit");
        
        // Then
        assertFalse(testCases.isEmpty());
        TestCase testCase = testCases.get(0);
        assertEquals("com/example/test/SampleTest.java", testCase.getFile());
    }

    @Test
    @DisplayName("Should return empty list for class without test methods")
    void shouldReturnEmptyListForClassWithoutTestMethods() {
        // Given
        String nonTestClass = 
                "package com.example;\n" +
                "class RegularClass {\n" +
                "    public void regularMethod() {}\n" +
                "    private void anotherMethod() {}\n" +
                "}";
        
        CompilationUnit cu = parseCode(nonTestClass);
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, "test.java", "junit");
        
        // Then
        assertTrue(testCases.isEmpty(), "Should return empty list for class without test methods");
    }

    @Test
    @DisplayName("Should handle unknown framework gracefully")
    void shouldHandleUnknownFrameworkGracefully() {
        // Given
        CompilationUnit cu = parseCode(JUNIT_TEST_CLASS);
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, "test.java", "unknown");
        
        // Then
        assertTrue(testCases.isEmpty(), "Should return empty list for unknown framework");
    }

    @Test
    @DisplayName("Should extract pattern-based labels from method names")
    void shouldExtractPatternBasedLabelsFromMethodNames() {
        // Given
        String testClassWithPatterns = 
                "package com.example;\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "class PatternTest {\n" +
                "    @Test void integrationTestMethod() {}\n" +
                "    @Test void performanceTestMethod() {}\n" +
                "    @Test void acceptanceTestMethod() {}\n" +
                "    @Test void regressionTestMethod() {}\n" +
                "}";
        
        CompilationUnit cu = parseCode(testClassWithPatterns);
        
        // When
        List<TestCase> testCases = testMethodExtractor.extractTestCases(cu, "test.java", "junit");
        
        // Then
        assertEquals(4, testCases.size());
        
        TestCase integrationTest = findTestByMethodName(testCases, "integrationTestMethod");
        assertTrue(integrationTest.getLabels().contains("integration"));
        
        TestCase performanceTest = findTestByMethodName(testCases, "performanceTestMethod");
        assertTrue(performanceTest.getLabels().contains("performance"));
        
        TestCase acceptanceTest = findTestByMethodName(testCases, "acceptanceTestMethod");
        assertTrue(acceptanceTest.getLabels().contains("acceptance"));
        
        TestCase regressionTest = findTestByMethodName(testCases, "regressionTestMethod");
        assertTrue(regressionTest.getLabels().contains("regression"));
    }

    // Helper methods
    
    private CompilationUnit parseCode(String code) {
        return javaParser.parse(code).getResult()
                .orElseThrow(() -> new RuntimeException("Failed to parse test code"));
    }

    private TestCase findTestByMethodName(List<TestCase> testCases, String methodName) {
        return testCases.stream()
                .filter(tc -> tc.getCode().contains("void " + methodName + "("))
                .findFirst()
                .orElse(null);
    }
}