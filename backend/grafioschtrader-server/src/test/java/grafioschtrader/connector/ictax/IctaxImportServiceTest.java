package grafioschtrader.connector.ictax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.entities.TaxCountry;
import grafiosch.entities.TaxUpload;
import grafiosch.entities.TaxYear;
import grafioschtrader.entities.IctaxSecurityTaxData;
import grafioschtrader.repository.IctaxSecurityTaxDataJpaRepository;
import grafioschtrader.repository.TaxCountryJpaRepository;
import grafioschtrader.repository.TaxUploadJpaRepository;
import grafioschtrader.repository.TaxYearJpaRepository;
import grafioschtrader.service.IctaxImportService;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class IctaxImportServiceTest {

  private static final String TEST_ISIN = "IE00B9M04V95";
  private static final short TAX_YEAR = 2025;

  @Autowired
  private TaxCountryJpaRepository taxCountryJpaRepository;

  @Autowired
  private TaxYearJpaRepository taxYearJpaRepository;

  @Autowired
  private IctaxSecurityTaxDataJpaRepository ictaxSecurityTaxDataJpaRepository;

  @Autowired
  private TaxUploadJpaRepository taxUploadJpaRepository;

  @Autowired
  private IctaxImportService ictaxImportService;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private boolean yearCreatedByTest = false;
  private Integer yearId;
  private final List<TaxUpload> createdUploads = new ArrayList<>();

  @BeforeAll
  void setup() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      TaxCountry country = findOrCreateCountry("CH");
      Optional<TaxYear> existing = taxYearJpaRepository.findByIdTaxCountryOrderByTaxYearDesc(
          country.getIdTaxCountry()).stream().filter(y -> y.getTaxYear() == TAX_YEAR).findFirst();
      if (existing.isPresent()) {
        yearId = existing.get().getIdTaxYear();
        yearCreatedByTest = false;
        // Clean up any leftover uploads from previous test runs
        List<TaxUpload> existingUploads = taxUploadJpaRepository.findAll().stream()
            .filter(u -> yearId.equals(u.getIdTaxYear())).toList();
        for (TaxUpload u : existingUploads) {
          try { ictaxImportService.deleteUpload(u.getIdTaxUpload()); } catch (Exception ignored) {}
        }
      } else {
        TaxYear year = new TaxYear();
        year.setTaxCountry(country);
        year.setTaxYear(TAX_YEAR);
        year = taxYearJpaRepository.saveAndFlush(year);
        yearId = year.getIdTaxYear();
        yearCreatedByTest = true;
      }
    });
  }

  @Test
  @Order(1)
  @DisplayName("Upload both ZIPs for year 2025, then query for IE00B9M04V95")
  void uploadAndQueryTest() throws IOException {
    // 1. Upload both ZIP files
    MultipartFile[] files = loadTestZips("ictax/kursliste_2025.zip", "ictax/kursliste_2025_diff.zip");
    List<TaxUpload> uploads = ictaxImportService.uploadAndImport(yearId, files);
    createdUploads.addAll(uploads);

    // 2. Verify uploads were created with records
    assertEquals(2, uploads.size(), "Two upload records should be created");
    for (TaxUpload upload : uploads) {
      assertTrue(upload.getRecordCount() > 0,
          "Each upload should have imported records, got: " + upload.getRecordCount());
    }

    // 3. Query for IE00B9M04V95 in year 2025
    List<IctaxSecurityTaxData> results = ictaxSecurityTaxDataJpaRepository
        .findByIsinInAndTaxYear(Collections.singleton(TEST_ISIN), TAX_YEAR);

    assertFalse(results.isEmpty(), "Should find tax data for ISIN " + TEST_ISIN);
    assertEquals(1, results.size(), "Should find exactly 1 record for " + TEST_ISIN);

    // 4. Verify data content
    IctaxSecurityTaxData data = results.getFirst();
    assertEquals(TEST_ISIN, data.getIsin());
    assertFalse(data.getPayments().isEmpty(), "Tax data should have payment entries");
  }

 //  @AfterAll
  void cleanup() throws IOException {
    for (TaxUpload upload : createdUploads) {
      ictaxImportService.deleteUpload(upload.getIdTaxUpload());
    }
    if (yearCreatedByTest) {
      new TransactionTemplate(transactionManager).executeWithoutResult(
          status -> taxYearJpaRepository.deleteById(yearId));
    }
  }

  private TaxCountry findOrCreateCountry(String countryCode) {
    Optional<TaxCountry> existing = taxCountryJpaRepository.findAll().stream()
        .filter(c -> countryCode.equals(c.getCountryCode())).findFirst();
    if (existing.isPresent()) {
      return existing.get();
    }
    TaxCountry country = new TaxCountry();
    country.setCountryCode(countryCode);
    return taxCountryJpaRepository.saveAndFlush(country);
  }

  private MultipartFile[] loadTestZips(String... resourcePaths) throws IOException {
    MultipartFile[] files = new MultipartFile[resourcePaths.length];
    for (int i = 0; i < resourcePaths.length; i++) {
      ClassPathResource resource = new ClassPathResource(resourcePaths[i]);
      files[i] = new MockMultipartFile("file", resource.getFilename(), "application/zip",
          resource.getInputStream());
    }
    return files;
  }
}
