package com.boombapcompile.blckvox.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules enforcing code hygiene standards to prevent code rot.
 *
 * <p>Rules cover:
 * <ul>
 *   <li>No deprecated API usage (ThreadLocal removal, deprecated method removal)</li>
 *   <li>No System.out/err in production code</li>
 *   <li>No java.util.logging in production code (use Log4j2)</li>
 * </ul>
 */
class CodeHygieneRulesTest {

    private static final String BASE = "com.boombapcompile.blckvox";

    private static JavaClasses prodClasses;
    private static JavaClasses testClasses;

    @BeforeAll
    static void importClasses() {
        prodClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
        testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Nested
    @DisplayName("No Deprecated Patterns")
    class NoDeprecatedPatterns {

        @Test
        @DisplayName("Production code should not use ThreadLocal")
        void noThreadLocalInProductionCode() {
            noClasses()
                    .that().resideInAPackage(BASE + "..")
                    .should().dependOnClassesThat()
                    .areAssignableTo(ThreadLocal.class)
                    .as("Production code should not use ThreadLocal " +
                        "(use direct return values like TranscriptionOutput instead)")
                    .check(prodClasses);
        }
    }

    @Nested
    @DisplayName("Logging Hygiene")
    class LoggingHygiene {

        @Test
        @DisplayName("Production code should not use System.out or System.err")
        void noSystemOutInProductionCode() {
            noClasses()
                    .that().resideInAPackage(BASE + "..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("java.io.PrintStream")
                    .as("Production code should use Log4j2 instead of System.out/err")
                    .check(prodClasses);
        }

        @Test
        @DisplayName("Production code should not use java.util.logging")
        void noJulInProductionCode() {
            noClasses()
                    .that().resideInAPackage(BASE + "..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("java.util.logging..")
                    .as("Production code should use Log4j2 instead of java.util.logging")
                    .check(prodClasses);
        }

        @Test
        @DisplayName("Test code should not use System.out or System.err")
        void noSystemOutInTestCode() {
            noClasses()
                    .that().resideInAPackage(BASE + "..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("java.io.PrintStream")
                    .as("Test code should use Log4j2 instead of System.out/err")
                    .check(testClasses);
        }
    }
}