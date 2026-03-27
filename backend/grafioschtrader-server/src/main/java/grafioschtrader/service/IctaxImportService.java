package grafioschtrader.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.entities.TaxUpload;
import grafiosch.entities.TaxYear;
import grafioschtrader.entities.IctaxSecurityTaxData;
import grafioschtrader.repository.IctaxSecurityTaxDataJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TaxUploadJpaRepository;
import grafioschtrader.repository.TaxYearJpaRepository;
import grafioschtrader.tax.swiss.ictax.IctaxKurslisteParser;

/**
 * Service for importing ICTax Kursliste XML data from uploaded zip files. Handles file storage, XML parsing, and
 * database persistence of Swiss tax data.
 */
@Service
public class IctaxImportService {

  private static final Logger log = LoggerFactory.getLogger(IctaxImportService.class);

  @Value("${gt.taxdata.storage.path:#{systemProperties['user.home'] + '/.grafioschtrader/taxdata'}}")
  private String storagePath;

  @Autowired
  private TaxYearJpaRepository taxYearJpaRepository;

  @Autowired
  private TaxUploadJpaRepository taxUploadJpaRepository;

  @Autowired
  private IctaxSecurityTaxDataJpaRepository ictaxSecurityTaxDataJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  private final IctaxKurslisteParser parser = new IctaxKurslisteParser();

  /**
   * Uploads and processes one or more zip files for a given tax year.
   *
   * @param idTaxYear the tax year to associate uploads with
   * @param files     uploaded zip files containing XML + .ix data
   * @return list of created TaxUpload records
   */
  @Transactional
  public List<TaxUpload> uploadAndImport(int idTaxYear, MultipartFile[] files) throws IOException {
    TaxYear taxYear = taxYearJpaRepository.getReferenceById(idTaxYear);
    String countryCode = taxYear.getTaxCountry() != null ? taxYear.getTaxCountry().getCountryCode() : "XX";
    Set<String> allIsins = getAllIsinsFromSecurities();

    List<TaxUpload> results = new ArrayList<>();
    for (MultipartFile file : files) {
      TaxUpload upload = processZipFile(taxYear, countryCode, file, allIsins);
      if (upload != null) {
        results.add(upload);
      }
    }
    return results;
  }

  /**
   * Re-imports an existing upload file with the current set of ISINs from GT's security table.
   *
   * @param idTaxUpload the upload to re-import
   * @return updated TaxUpload with new record count
   */
  @Transactional
  public TaxUpload reimport(int idTaxUpload) throws IOException {
    TaxUpload upload = taxUploadJpaRepository.getReferenceById(idTaxUpload);
    Path zipPath = Paths.get(upload.getFilePath());
    if (!Files.exists(zipPath)) {
      throw new IOException("Stored zip file not found: " + upload.getFilePath());
    }

    ictaxSecurityTaxDataJpaRepository.deleteByIdTaxUpload(idTaxUpload);
    Set<String> allIsins = getAllIsinsFromSecurities();

    byte[] zipBytes = Files.readAllBytes(zipPath);
    int count = importFromZipBytes(zipBytes, upload.getIdTaxUpload(), allIsins);
    upload.setRecordCount(count);
    return taxUploadJpaRepository.save(upload);
  }

  /**
   * Deletes an upload including its stored file and all associated tax data (cascaded via DB).
   *
   * @param idTaxUpload the upload to delete
   */
  @Transactional
  public void deleteUpload(int idTaxUpload) throws IOException {
    TaxUpload upload = taxUploadJpaRepository.getReferenceById(idTaxUpload);
    Path zipPath = Paths.get(upload.getFilePath());
    taxUploadJpaRepository.deleteById(idTaxUpload);
    Files.deleteIfExists(zipPath);
  }

  private TaxUpload processZipFile(TaxYear taxYear, String countryCode, MultipartFile file, Set<String> allIsins)
      throws IOException {
    // Store zip file
    Path storageDir = Paths.get(storagePath, countryCode, String.valueOf(taxYear.getTaxYear()));
    Files.createDirectories(storageDir);
    Path storedPath = storageDir.resolve(file.getOriginalFilename());
    Files.copy(file.getInputStream(), storedPath, StandardCopyOption.REPLACE_EXISTING);

    // Create upload record
    TaxUpload upload = new TaxUpload();
    upload.setTaxYear(taxYear);
    upload.setFileName(file.getOriginalFilename());
    upload.setFilePath(storedPath.toString());
    upload.setUploadDate(LocalDateTime.now());
    upload.setRecordCount(0);
    upload = taxUploadJpaRepository.save(upload);

    // Import data from zip
    byte[] zipBytes = Files.readAllBytes(storedPath);
    int count = importFromZipBytes(zipBytes, upload.getIdTaxUpload(), allIsins);
    upload.setRecordCount(count);
    return taxUploadJpaRepository.save(upload);
  }

  private int importFromZipBytes(byte[] zipBytes, int idTaxUpload, Set<String> allIsins) throws IOException {
    byte[] xmlBytes = null;

    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName().toLowerCase().endsWith(".xml")) {
          xmlBytes = zis.readAllBytes();
        }
      }
    }

    if (xmlBytes == null) {
      log.warn("No XML file found in zip for upload {}", idTaxUpload);
      return 0;
    }

    List<IctaxSecurityTaxData> dataList;
    try {
      if (!allIsins.isEmpty()) {
        // Selective import: full parse then filter by portfolio ISINs
        dataList = parser.parseSelective(xmlBytes, allIsins, idTaxUpload);
      } else {
        // Full import
        dataList = parser.parseFull(new ByteArrayInputStream(xmlBytes), idTaxUpload);
      }
    } catch (Exception e) {
      log.error("Failed to parse XML for upload {}", idTaxUpload, e);
      return 0;
    }

    if (!dataList.isEmpty()) {
      ictaxSecurityTaxDataJpaRepository.saveAll(dataList);
    }
    log.info("Imported {} securities for upload {}", dataList.size(), idTaxUpload);
    return dataList.size();
  }

  private Set<String> getAllIsinsFromSecurities() {
    return securityJpaRepository.findAll().stream()
        .filter(s -> s.getIsin() != null && !s.getIsin().isEmpty())
        .map(s -> s.getIsin())
        .collect(Collectors.toSet());
  }
}
