package dev.eliascosta.invoice.stock;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class StockArchitectureTest {

  private static final JavaClasses STOCK_CLASSES =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackagesOf(StockApplication.class);

  // The business layers start empty in F1-06. Each rule checks classes as they are added.
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
            "com.fasterxml.jackson..",
            "tools.jackson..")
        .allowEmptyShould(true)
        .check(STOCK_CLASSES);
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
        .check(STOCK_CLASSES);
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
        .check(STOCK_CLASSES);
  }
}
