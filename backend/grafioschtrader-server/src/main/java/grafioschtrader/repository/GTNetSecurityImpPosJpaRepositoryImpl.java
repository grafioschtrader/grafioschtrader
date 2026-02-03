package grafioschtrader.repository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.BaseConstants;
import grafiosch.common.CSVImportHelper;
import grafiosch.common.FieldColumnMapping;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.dto.UploadHistoryquotesSuccess;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.GTNetSecurityImpGap;
import grafioschtrader.entities.GTNetSecurityImpHead;
import grafioschtrader.entities.GTNetSecurityImpPos;
import grafioschtrader.entities.Security;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Implementation of custom repository operations for GTNetSecurityImpPos.
 * All operations verify tenant access through the parent header entity.
 */
public class GTNetSecurityImpPosJpaRepositoryImpl implements GTNetSecurityImpPosJpaRepositoryCustom {

  private static final Logger LOG = LoggerFactory.getLogger(GTNetSecurityImpPosJpaRepositoryImpl.class);

  @Autowired
  private GTNetSecurityImpPosJpaRepository gtNetSecurityImpPosJpaRepository;

  @Autowired
  private GTNetSecurityImpHeadJpaRepository gtNetSecurityImpHeadJpaRepository;

  @Autowired
  private GTNetSecurityImpGapJpaRepository gtNetSecurityImpGapJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private ImportTransactionPosJpaRepository importTransactionPosJpaRepository;

  @Autowired
  private Validator validator;

  @Override
  public List<GTNetSecurityImpPos> findByIdGtNetSecurityImpHeadAndIdTenant(Integer idGtNetSecurityImpHead,
      Integer idTenant) {
    // Verify tenant has access to the header
    GTNetSecurityImpHead header = gtNetSecurityImpHeadJpaRepository
        .findByIdGtNetSecurityImpHeadAndIdTenant(idGtNetSecurityImpHead, idTenant);
    if (header == null) {
      return Collections.emptyList();
    }
    List<GTNetSecurityImpPos> positions = gtNetSecurityImpPosJpaRepository
        .findByIdGtNetSecurityImpHead(idGtNetSecurityImpHead);

    // Populate gaps for positions without linked security
    if (!positions.isEmpty()) {
      List<Integer> positionIds = positions.stream()
          .map(GTNetSecurityImpPos::getIdGtNetSecurityImpPos)
          .collect(Collectors.toList());
      List<GTNetSecurityImpGap> allGaps = gtNetSecurityImpGapJpaRepository.findByIdGtNetSecurityImpPosIn(positionIds);

      // Group gaps by position ID
      Map<Integer, List<GTNetSecurityImpGap>> gapsByPosition = allGaps.stream()
          .collect(Collectors.groupingBy(GTNetSecurityImpGap::getIdGtNetSecurityImpPos));

      // Assign gaps to each position
      for (GTNetSecurityImpPos pos : positions) {
        pos.setGaps(gapsByPosition.getOrDefault(pos.getIdGtNetSecurityImpPos(), Collections.emptyList()));
      }
    }

    return positions;
  }

  @Override
  @Transactional
  public GTNetSecurityImpPos saveWithTenantCheck(GTNetSecurityImpPos entity, Integer idTenant) {
    // Verify tenant has access to the header
    GTNetSecurityImpHead header = gtNetSecurityImpHeadJpaRepository
        .findByIdGtNetSecurityImpHeadAndIdTenant(entity.getIdGtNetSecurityImpHead(), idTenant);
    if (header == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    // If updating existing, verify it belongs to this header
    if (entity.getId() != null) {
      Optional<GTNetSecurityImpPos> existing = gtNetSecurityImpPosJpaRepository.findById(entity.getId());
      if (existing.isEmpty()
          || !existing.get().getIdGtNetSecurityImpHead().equals(entity.getIdGtNetSecurityImpHead())) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
    }

    // Check if a security with the same ISIN+currency already exists
    if (entity.getIsin() != null && entity.getCurrency() != null) {
      Security existingSecurity = securityJpaRepository.findByIsinAndCurrency(entity.getIsin(), entity.getCurrency());
      if (existingSecurity != null) {
        throw new DataViolationException("isin", "gt.security.isin.currency.exists",
            new Object[] { entity.getIsin(), entity.getCurrency(), existingSecurity.getName() });
      }
    }

    return gtNetSecurityImpPosJpaRepository.save(entity);
  }

  @Override
  @Transactional
  public int deleteWithTenantCheck(Integer id, Integer idTenant) {
    Optional<GTNetSecurityImpPos> posOpt = gtNetSecurityImpPosJpaRepository.findById(id);
    if (posOpt.isEmpty()) {
      return 0;
    }

    // Verify tenant has access through the header
    GTNetSecurityImpHead header = gtNetSecurityImpHeadJpaRepository
        .findByIdGtNetSecurityImpHeadAndIdTenant(posOpt.get().getIdGtNetSecurityImpHead(), idTenant);
    if (header == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    gtNetSecurityImpPosJpaRepository.deleteById(id);
    return 1;
  }

  /**
   * Uploads and processes a CSV file containing GTNet security import positions.
   *
   * <p>The CSV file must have a header row with column names that map to entity fields:
   * <ul>
   *   <li>{@code isin} - International Securities Identification Number</li>
   *   <li>{@code tickerSymbol} (or {@code ticker_symbol}, {@code ticker}) - Stock ticker symbol</li>
   *   <li>{@code currency} - ISO 4217 currency code (required)</li>
   * </ul>
   *
   * <p>Processing rules:
   * <ul>
   *   <li>At least ISIN or tickerSymbol must be provided for each row</li>
   *   <li>Currency is always required</li>
   *   <li>Duplicate entries (same ISIN + currency) already in the database are skipped</li>
   *   <li>Duplicate entries within the file are skipped</li>
   *   <li>All string values are converted to uppercase</li>
   * </ul>
   *
   * @param idGtNetSecurityImpHead the header ID to associate positions with
   * @param uploadFiles array of uploaded CSV files (only first file is processed)
   * @param idTenant the tenant ID for access verification
   * @return upload result statistics
   * @throws SecurityException if tenant doesn't have access to the header
   * @throws Exception if file processing fails
   */
  @Override
  @Transactional
  public UploadHistoryquotesSuccess uploadCSV(Integer idGtNetSecurityImpHead, MultipartFile[] uploadFiles,
      Integer idTenant) throws Exception {
    // Verify tenant access through header
    GTNetSecurityImpHead header = gtNetSecurityImpHeadJpaRepository
        .findByIdGtNetSecurityImpHeadAndIdTenant(idGtNetSecurityImpHead, idTenant);
    if (header == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    UploadHistoryquotesSuccess result = new UploadHistoryquotesSuccess();

    if (uploadFiles.length == 0 || uploadFiles[0].isEmpty()) {
      return result;
    }

    // Get existing positions for duplicate detection (key = ISIN|currency, case-insensitive)
    Set<String> existingKeys = gtNetSecurityImpPosJpaRepository.findByIdGtNetSecurityImpHead(idGtNetSecurityImpHead)
        .stream()
        .map(pos -> createDuplicateKey(pos.getIsin(), pos.getCurrency()))
        .collect(Collectors.toSet());

    Set<String> keysInFile = new HashSet<>();
    List<GTNetSecurityImpPos> positionsToSave = new ArrayList<>();
    List<FieldColumnMapping> fieldColumnMappings = null;

    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(uploadFiles[0].getInputStream(), StandardCharsets.UTF_8))) {

      int lineNumber = 0;
      String line;

      while ((line = reader.readLine()) != null) {
        lineNumber++;
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }

        if (lineNumber == 1) {
          // Parse header line and create field mappings
          fieldColumnMappings = CSVImportHelper.getHeaderFieldNameMapping(line, GTNetSecurityImpPos.class, user);
          continue;
        }

        // Parse data line using the field mappings
        GTNetSecurityImpPos pos = new GTNetSecurityImpPos();
        pos.setIdGtNetSecurityImpHead(idGtNetSecurityImpHead);

        try {
          CSVImportHelper.parseLineToEntity(line, lineNumber, pos, fieldColumnMappings, null, true, user);
        } catch (DataViolationException e) {
          result.validationErrors++;
          continue;
        }

        // Check that at least ISIN or tickerSymbol is provided
        if (pos.getIsin() == null && pos.getTickerSymbol() == null) {
          result.validationErrors++;
          continue;
        }

        String key = createDuplicateKey(pos.getIsin(), pos.getCurrency());

        // Check for duplicates in existing data
        if (existingKeys.contains(key)) {
          result.notOverridden++;
          continue;
        }

        // Check for duplicates within file
        if (keysInFile.contains(key)) {
          result.duplicatedInImport++;
          continue;
        }

        // Validate entity using Bean Validation
        Set<ConstraintViolation<GTNetSecurityImpPos>> violations = validator.validate(pos);
        if (!violations.isEmpty()) {
          result.validationErrors++;
          continue;
        }

        keysInFile.add(key);
        positionsToSave.add(pos);
        result.success++;
      }
    }

    gtNetSecurityImpPosJpaRepository.saveAll(positionsToSave);
    return result;
  }

  /**
   * Creates a case-insensitive key for duplicate detection based on ISIN and currency.
   *
   * @param isin the ISIN value (may be null)
   * @param currency the currency value (may be null)
   * @return a normalized key string in format "ISIN|CURRENCY"
   */
  private String createDuplicateKey(String isin, String currency) {
    String isinKey = isin != null ? isin.toUpperCase().trim() : "";
    String currencyKey = currency != null ? currency.toUpperCase().trim() : "";
    return isinKey + "|" + currencyKey;
  }

  @Override
  @Transactional
  public GTNetSecurityImpPos deleteLinkedSecurity(Integer idGtNetSecurityImpPos, Integer idTenant) {
    Optional<GTNetSecurityImpPos> posOpt = gtNetSecurityImpPosJpaRepository.findById(idGtNetSecurityImpPos);
    if (posOpt.isEmpty()) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    GTNetSecurityImpPos position = posOpt.get();

    // Verify tenant has access through the header
    GTNetSecurityImpHead header = gtNetSecurityImpHeadJpaRepository
        .findByIdGtNetSecurityImpHeadAndIdTenant(position.getIdGtNetSecurityImpHead(), idTenant);
    if (header == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    // Verify position has a linked security
    if (position.getSecurity() == null) {
      throw new DataViolationException("security", "gt.net.security.not.linked", null);
    }

    Integer idSecuritycurrency = position.getSecurity().getIdSecuritycurrency();

    // Unlink security from position
    position.setSecurity(null);
    gtNetSecurityImpPosJpaRepository.save(position);

    // Delete history quotes for the security
    historyquoteJpaRepository.removeAllSecurityHistoryquote(idSecuritycurrency);

    // Delete the security
    securityJpaRepository.deleteById(idSecuritycurrency);

    return position;
  }

  @Override
  @Transactional
  public GTNetSecurityImpHead createFromImportTransactionHead(Integer idTransactionHead,
      Integer idGtNetSecurityImpHead, String headName, Integer idTenant) {

    List<ImportTransactionPos> missingSecurityPositions = findMissingSecurityPositions(idTransactionHead, idTenant);
    GTNetSecurityImpHead header = getOrCreateHeader(idGtNetSecurityImpHead, headName, idTenant);
    List<GTNetSecurityImpPos> positionsToSave = buildPositionsToSave(missingSecurityPositions, header);

    if (!positionsToSave.isEmpty()) {
      gtNetSecurityImpPosJpaRepository.saveAll(positionsToSave);
      LOG.info("Saved {} GTNet positions for header {}", positionsToSave.size(), header.getIdGtNetSecurityImpHead());
    } else {
      LOG.warn("No valid positions to save for header {}", header.getIdGtNetSecurityImpHead());
    }

    return header;
  }

  private List<ImportTransactionPos> findMissingSecurityPositions(Integer idTransactionHead, Integer idTenant) {
    List<ImportTransactionPos> importPositions = importTransactionPosJpaRepository
        .findByIdTransactionHeadAndIdTenant(idTransactionHead, idTenant);

    LOG.info("Found {} import positions for head {}", importPositions.size(), idTransactionHead);

    if (importPositions.isEmpty()) {
      throw new DataViolationException(null, "gt.net.import.no.positions.found", null);
    }

    List<ImportTransactionPos> missingSecurityPositions = importPositions.stream()
        .filter(pos -> pos.getSecurity() == null)
        .filter(pos -> hasValidIdentifier(pos.getIsin()) || hasValidIdentifier(pos.getSymbolImp()))
        .collect(Collectors.toList());

    LOG.info("Found {} positions with missing security and valid identifiers", missingSecurityPositions.size());

    if (missingSecurityPositions.isEmpty()) {
      throw new DataViolationException(null, "gt.net.import.no.missing.securities", null);
    }

    return missingSecurityPositions;
  }

  private GTNetSecurityImpHead getOrCreateHeader(Integer idGtNetSecurityImpHead, String headName, Integer idTenant) {
    if (idGtNetSecurityImpHead != null) {
      GTNetSecurityImpHead header = gtNetSecurityImpHeadJpaRepository
          .findByIdGtNetSecurityImpHeadAndIdTenant(idGtNetSecurityImpHead, idTenant);
      if (header == null) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
      return header;
    }

    if (headName == null || headName.trim().isEmpty()) {
      throw new DataViolationException("headName", "gt.net.security.imp.head.name.required", null);
    }

    GTNetSecurityImpHead header = new GTNetSecurityImpHead();
    header.setName(headName.trim());
    header.setIdTenant(idTenant);
    return gtNetSecurityImpHeadJpaRepository.save(header);
  }

  private List<GTNetSecurityImpPos> buildPositionsToSave(List<ImportTransactionPos> importPositions,
      GTNetSecurityImpHead header) {
    Set<String> existingKeys = gtNetSecurityImpPosJpaRepository
        .findByIdGtNetSecurityImpHead(header.getIdGtNetSecurityImpHead())
        .stream()
        .map(pos -> createDuplicateKey(pos.getIsin(), pos.getCurrency()))
        .collect(Collectors.toSet());

    Set<String> processedKeys = new HashSet<>();
    List<GTNetSecurityImpPos> positionsToSave = new ArrayList<>();

    for (ImportTransactionPos importPos : importPositions) {
      GTNetSecurityImpPos pos = createPositionFromImport(importPos, header, existingKeys, processedKeys);
      if (pos != null) {
        positionsToSave.add(pos);
      }
    }

    return positionsToSave;
  }

  private GTNetSecurityImpPos createPositionFromImport(ImportTransactionPos importPos, GTNetSecurityImpHead header,
      Set<String> existingKeys, Set<String> processedKeys) {
    String isin = normalizeValue(importPos.getIsin());
    String tickerSymbol = normalizeValue(importPos.getSymbolImp());
    if (tickerSymbol != null && tickerSymbol.length() > 6) {
      tickerSymbol = null;
    }
    String currency = normalizeValue(importPos.getCurrencySecurity());
    if (currency == null) {
      currency = normalizeValue(importPos.getCurrencyAccount());
    }

    if ((isin == null && tickerSymbol == null) || currency == null) {
      return null;
    }

    String key = createDuplicateKey(isin, currency);
    if (existingKeys.contains(key) || processedKeys.contains(key)) {
      return null;
    }

    GTNetSecurityImpPos pos = new GTNetSecurityImpPos(header.getIdGtNetSecurityImpHead(), isin, tickerSymbol, currency);
    if (!validator.validate(pos).isEmpty()) {
      return null;
    }

    processedKeys.add(key);
    return pos;
  }

  private boolean hasValidIdentifier(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private String normalizeValue(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.toUpperCase().trim();
  }
}
