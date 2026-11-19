package dev.eliascosta.invoice.billing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class BillingArchitectureTest {

  private static final JavaClasses BILLING_CLASSES =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackagesOf(BillingApplication.class);

  // The business layers start empty. Each rule checks classes as they are added.
  @Test
  void domainIsIndependentOfFrameworks() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "org.apache.kafka..",
            "org.mapstruct..",
            "com.fasterxml.jackson..",
            "tools.jackson..")
        .allowEmptyShould(true)
        .check(BILLING_CLASSES);
  }

  @Test
  void applicationDoesNotDependOnAdapters() {
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..adapter..")
        .allowEmptyShould(true)
        .check(BILLING_CLASSES);
  }

  @Test
  void inboundAdaptersDoNotDependOnOutboundAdapters() {
    noClasses()
        .that()
        .resideInAPackage("..adapter.in..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..adapter.out..")
        .allowEmptyShould(true)
        .check(BILLING_CLASSES);
  }
}
