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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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

import grafioschtrader.entities.IctaxExchangeRate;
import grafioschtrader.entities.IctaxSecurityTaxData;
import grafioschtrader.entities.TaxUpload;
import grafioschtrader.entities.TaxYear;
import grafioschtrader.repository.IctaxExchangeRateJpaRepository;
import grafioschtrader.repository.IctaxSecurityTaxDataJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TaxUploadJpaRepository;
import grafioschtrader.repository.TaxYearJpaRepository;
import grafioschtrader.tax.swiss.ictax.IctaxKurslisteParser;
import grafioschtrader.tax.swiss.ictax.KurslisteParseResult;
import grafioschtrader.tax.swiss.ictax.ParsedExchangeRate;

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
  private IctaxExchangeRateJpaRepository ictaxExchangeRateJpaRepository;

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
    int count = importFromZipBytes(zipBytes, upload.getIdTaxUpload(), upload.getTaxYear(), allIsins);
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
    int count = importFromZipBytes(zipBytes, upload.getIdTaxUpload(), taxYear, allIsins);
    upload.setRecordCount(count);
    return taxUploadJpaRepository.save(upload);
  }

  private int importFromZipBytes(byte[] zipBytes, int idTaxUpload, TaxYear taxYear, Set<String> allIsins)
      throws IOException {
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

    KurslisteParseResult parseResult;
    try {
      if (!allIsins.isEmpty()) {
        // Selective import: full parse then filter by portfolio ISINs
        parseResult = parser.parseSelective(xmlBytes, allIsins, idTaxUpload);
      } else {
        // Full import
        parseResult = parser.parseFull(new ByteArrayInputStream(xmlBytes), idTaxUpload);
      }
    } catch (Exception e) {
      log.error("Failed to parse XML for upload {}", idTaxUpload, e);
      return 0;
    }

    List<IctaxSecurityTaxData> dataList = parseResult.securities();
    if (!dataList.isEmpty()) {
      ictaxSecurityTaxDataJpaRepository.saveAll(dataList);
    }
    upsertExchangeRates(parseResult.exchangeRates(), taxYear);
    log.info("Imported {} securities for upload {}", dataList.size(), idTaxUpload);
    return dataList.size();
  }

  /**
   * Writes the official exchange rates of the Kursliste into {@code ictax_exchange_rate}, keyed by tax year and
   * currency.
   *
   * <p>
   * Only the imported values are written; a manually entered override stays untouched, which is why the rates are not
   * deleted and re-inserted the way the per-security data is. A differential Kursliste carries no rates at all, so an
   * upload that yields none simply leaves the year as it was.
   * </p>
   *
   * @param rates   the rates found in the XML, possibly empty
   * @param taxYear the tax year the upload belongs to; rates for a different year are rejected
   */
  private void upsertExchangeRates(List<ParsedExchangeRate> rates, TaxYear taxYear) {
    if (rates.isEmpty() || taxYear == null) {
      return;
    }
    Map<String, IctaxExchangeRate> existingByCurrency = ictaxExchangeRateJpaRepository
        .findByIdTaxYearOrderByCurrency(taxYear.getIdTaxYear()).stream()
        .collect(Collectors.toMap(IctaxExchangeRate::getCurrency, Function.identity(), (a, _) -> a));
    List<IctaxExchangeRate> toSave = new ArrayList<>();
    int rejected = 0;
    for (ParsedExchangeRate rate : rates) {
      if (rate.year() != null && !rate.year().equals(taxYear.getTaxYear())) {
        rejected++;
        continue;
      }
      IctaxExchangeRate entity = existingByCurrency.get(rate.currency());
      if (entity == null) {
        entity = new IctaxExchangeRate(taxYear.getIdTaxYear(), rate.currency(), rate.denomination(), rate.yearEndRate(),
            rate.annualMeanRate());
      } else {
        entity.setDenomination(rate.denomination());
        entity.setYearEndRate(rate.yearEndRate());
        entity.setAnnualMeanRate(rate.annualMeanRate());
      }
      toSave.add(entity);
    }
    ictaxExchangeRateJpaRepository.saveAll(toSave);
    log.info("Imported {} exchange rates for tax year {}, {} rejected for a different year", toSave.size(),
        taxYear.getTaxYear(), rejected);
  }

  private Set<String> getAllIsinsFromSecurities() {
    return securityJpaRepository.findAll().stream().filter(s -> s.getIsin() != null && !s.getIsin().isEmpty())
        .map(s -> s.getIsin()).collect(Collectors.toSet());
  }
}
