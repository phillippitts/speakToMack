package com.phillippitts.blckvox.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantLock;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules that guard against previously-fixed bugs.
 *
 * <p>Each rule corresponds to a specific defect class discovered in this codebase:
 * <ul>
 *   <li>Explicit {@code throw new NullPointerException} instead of {@code Objects.requireNonNull}</li>
 *   <li>Duplicate Vosk model loading (50MB–1.8GB each) instead of shared provider</li>
 *   <li>Unconditional {@code @Component} depending on {@code @ConditionalOnProperty} beans</li>
 *   <li>Non-atomic read-compute-write on concurrent state without explicit locking</li>
 * </ul>
 */
class BugPreventionRulesTest {

    private static final String BASE = "com.phillippitts.blckvox";

    private static JavaClasses prodClasses;

    @BeforeAll
    static void importClasses() {
        prodClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Nested
    @DisplayName("Null Safety")
    class NullSafety {

        @Test
        @DisplayName("Production code should use Objects.requireNonNull instead of throw new NullPointerException")
        void noExplicitNullPointerExceptionConstruction() {
            noClasses()
                    .that().resideInAPackage(BASE + "..")
                    .should(notConstructNullPointerException())
                    .as("Use Objects.requireNonNull() instead of throw new NullPointerException()")
                    .check(prodClasses);
        }
    }

    @Nested
    @DisplayName("Vosk Model Sharing")
    class VoskModelSharing {

        @Test
        @DisplayName("VoskStreamingService should use shared VoskModelProvider")
        void voskStreamingServiceShouldUseSharedModelProvider() {
            classes()
                    .that().haveSimpleName("VoskStreamingService")
                    .should().dependOnClassesThat()
                    .haveSimpleName("VoskModelProvider")
                    .as("VoskStreamingService must use VoskModelProvider " +
                        "to avoid loading a duplicate Vosk model (50MB-1.8GB)")
                    .check(prodClasses);
        }

        @Test
        @DisplayName("VoskSttEngine should use shared VoskModelProvider")
        void voskSttEngineShouldUseSharedModelProvider() {
            classes()
                    .that().haveSimpleName("VoskSttEngine")
                    .should().dependOnClassesThat()
                    .haveSimpleName("VoskModelProvider")
                    .as("VoskSttEngine must use VoskModelProvider " +
                        "to avoid loading a duplicate Vosk model (50MB-1.8GB)")
                    .check(prodClasses);
        }

        @Test
        @DisplayName("VoskStreamingService should not construct org.vosk.Model directly")
        void voskStreamingServiceShouldNotConstructModelDirectly() {
            noClasses()
                    .that().haveSimpleName("VoskStreamingService")
                    .should(constructClassNamed("org.vosk.Model"))
                    .as("VoskStreamingService must not construct org.vosk.Model directly; " +
                        "use VoskModelProvider to share the model instance")
                    .check(prodClasses);
        }
    }

    @Nested
    @DisplayName("Conditional Bean Consistency")
    class ConditionalBeanConsistency {

        @Test
        @DisplayName("ReconciliationDependencies should be conditional on reconciliation.enabled")
        void reconciliationDependenciesShouldBeConditional() {
            classes()
                    .that().haveSimpleName("ReconciliationDependencies")
                    .should().beAnnotatedWith(
                            "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty")
                    .as("ReconciliationDependencies depends on conditional bean TranscriptReconciler, " +
                        "so it must itself be conditional to avoid NoSuchBeanDefinitionException")
                    .check(prodClasses);
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("DynamicConcurrencyGuard should use explicit locking for permit adjustments")
        void dynamicConcurrencyGuardShouldUseExplicitLocking() {
            classes()
                    .that().haveSimpleName("DynamicConcurrencyGuard")
                    .should().dependOnClassesThat()
                    .areAssignableTo(ReentrantLock.class)
                    .as("DynamicConcurrencyGuard.adjustPermits() reads AtomicInteger and modifies " +
                        "Semaphore non-atomically; explicit locking prevents permit count divergence")
                    .check(prodClasses);
        }
    }

    // -- Custom ArchConditions --

    private static ArchCondition<JavaClass> notConstructNullPointerException() {
        return new ArchCondition<>("not construct NullPointerException directly") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaConstructorCall call : item.getConstructorCallsFromSelf()) {
                    if (call.getTargetOwner().isEquivalentTo(NullPointerException.class)) {
                        events.add(SimpleConditionEvent.violated(item,
                                item.getDescription() + " constructs NullPointerException at "
                                + call.getSourceCodeLocation()
                                + ". Use Objects.requireNonNull() instead"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> constructClassNamed(String className) {
        return new ArchCondition<>("construct " + className) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaConstructorCall call : item.getConstructorCallsFromSelf()) {
                    if (call.getTargetOwner().getName().equals(className)) {
                        events.add(SimpleConditionEvent.violated(item,
                                item.getDescription() + " constructs " + className
                                + " at " + call.getSourceCodeLocation()));
                    }
                }
            }
        };
    }
}