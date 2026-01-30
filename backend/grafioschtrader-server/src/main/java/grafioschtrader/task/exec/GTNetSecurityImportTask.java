package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.DateHelper;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.entities.UserEntityChangeCount;
import grafiosch.entities.UserEntityChangeCount.UserEntityChangeCountId;
import grafiosch.entities.projection.UserCountLimit;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.UserEntityChangeCountJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.OperationType;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.GTNetSecurityImpHead;
import grafioschtrader.entities.GTNetSecurityImpPos;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.gtnet.GTNetSecurityLookupService;
import grafioschtrader.gtnet.model.SecurityGtnetLookupDTO;
import grafioschtrader.repository.AssetclassJpaRepository;
import grafioschtrader.repository.GTNetSecurityImpHeadJpaRepository;
import grafioschtrader.repository.GTNetSecurityImpPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Background task that imports securities from GTNet peers for positions in a GTNetSecurityImpHead batch.
 *
 * For each position without a linked security, this task:
 * <ol>
 *   <li>Queries GTNet peers using batch lookup</li>
 *   <li>Selects the best match from results using a scoring algorithm</li>
 *   <li>Either links an existing local security or creates a new one</li>
 *   <li>Updates the position with the security reference</li>
 * </ol>
 *
 * @see TaskTypeExtended#GTNET_SECURITY_IMPORT_POSITIONS
 */
@Component
public class GTNetSecurityImportTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetSecurityImportTask.class);

  @Autowired
  private GTNetSecurityImpHeadJpaRepository gtNetSecurityImpHeadJpaRepository;

  @Autowired
  private GTNetSecurityImpPosJpaRepository gtNetSecurityImpPosJpaRepository;

  @Autowired
  private GTNetSecurityLookupService gtNetSecurityLookupService;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private AssetclassJpaRepository assetclassJpaRepository;

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  private static final String ENTITY_NAME_GTNET_SECURITY_IMPORT = "GTNetSecurityImport";

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.GTNET_SECURITY_IMPORT_POSITIONS;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList(GTNetSecurityImpHead.class.getSimpleName());
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    Integer idHead = taskDataChange.getIdEntity();
    log.info("Starting GTNet security import for head ID: {}", idHead);

    // Extract user ID from task parameters (stored in oldValueNumber)
    Integer idCreatedByUser = null;
    User user = null;
    if (taskDataChange.getOldValueNumber() != null) {
      idCreatedByUser = taskDataChange.getOldValueNumber().intValue();
      user = userJpaRepository.findById(idCreatedByUser).orElse(null);
      log.debug("Using user ID {} for created_by field", idCreatedByUser);
    }

    // Check if user is a limited editing user (subject to daily limits)
    boolean isLimitedUser = user != null && UserAccessHelper.isLimitedEditingUser(user);

    // 1. Load header to get tenant ID
    GTNetSecurityImpHead head = gtNetSecurityImpHeadJpaRepository.findById(idHead).orElse(null);
    if (head == null) {
      log.error("GTNetSecurityImpHead not found for ID: {}", idHead);
      return;
    }

    // 2. Load positions without linked security
    List<GTNetSecurityImpPos> positions = gtNetSecurityImpPosJpaRepository
        .findByIdGtNetSecurityImpHeadAndSecurityIsNull(idHead);

    if (positions.isEmpty()) {
      log.info("No unmatched positions found for head ID: {}", idHead);
      return;
    }

    log.info("Processing {} unmatched positions", positions.size());

    // 3. Perform batch lookup
    Map<Integer, List<SecurityGtnetLookupDTO>> lookupResults =
        gtNetSecurityLookupService.lookupSecuritiesBatch(positions);

    int created = 0;
    int linked = 0;
    int failed = 0;
    int processed = 0;

    // 4. Process each position
    for (GTNetSecurityImpPos pos : positions) {
      processed++;
      try {
        List<SecurityGtnetLookupDTO> matches = lookupResults.get(pos.getIdGtNetSecurityImpPos());

        if (matches == null || matches.isEmpty()) {
          log.debug("No matches found for position ID={}, ISIN={}, ticker={}",
              pos.getIdGtNetSecurityImpPos(), pos.getIsin(), pos.getTickerSymbol());
          failed++;
          continue;
        }

        // Select best match
        SecurityGtnetLookupDTO bestMatch = selectBestMatch(matches);
        if (bestMatch == null) {
          log.debug("Could not select best match for position ID={}", pos.getIdGtNetSecurityImpPos());
          failed++;
          continue;
        }

        // Check if security already exists locally
        Security existingSecurity = findExistingSecurity(bestMatch);
        if (existingSecurity != null) {
          pos.setSecurity(existingSecurity);
          gtNetSecurityImpPosJpaRepository.save(pos);
          log.debug("Linked existing security {} to position {}",
              existingSecurity.getIdSecuritycurrency(), pos.getIdGtNetSecurityImpPos());
          linked++;
          continue;
        }

        // Check daily limit for limited users before creating new security
        if (isLimitedUser && isLimitExceeded(user)) {
          int remaining = positions.size() - processed;
          log.warn("Daily limit exceeded for user {}. Created: {}, Linked: {}, Remaining: {}",
              idCreatedByUser, created, linked, remaining);
          throw new TaskBackgroundException("GTNET_IMPORT_LIMIT_EXCEEDED", false);
        }

        // Create new security
        Security newSecurity = createSecurityFromDTO(bestMatch, head.getIdTenant(), idCreatedByUser);
        if (newSecurity != null) {
          pos.setSecurity(newSecurity);
          gtNetSecurityImpPosJpaRepository.save(pos);
          log.debug("Created and linked new security {} to position {}",
              newSecurity.getIdSecuritycurrency(), pos.getIdGtNetSecurityImpPos());
          created++;

          // Log the security creation for limit tracking
          if (idCreatedByUser != null) {
            logSecurityCreation(idCreatedByUser);
          }
        } else {
          log.warn("Failed to create security for position ID={}", pos.getIdGtNetSecurityImpPos());
          failed++;
        }

      } catch (TaskBackgroundException tbe) {
        // Re-throw TaskBackgroundException to be handled by BackgroundWorker
        throw tbe;
      } catch (Exception e) {
        log.error("Error processing position ID={}: {}", pos.getIdGtNetSecurityImpPos(), e.getMessage(), e);
        failed++;
      }
    }

    log.info("GTNet security import completed for head {}: {} created, {} linked, {} failed",
        idHead, created, linked, failed);
  }

  /**
   * Selects the best matching DTO from lookup results using a scoring algorithm.
   *
   * Scoring criteria (highest score wins):
   * <ul>
   *   <li>Connector match score: 0-4 points (one for each matched connector type)</li>
   *   <li>Asset class match type: EXACT=3, SCHEME_MATCH=2, PARTIAL=1</li>
   *   <li>Stock exchange MIC presence: +1 point if MIC is available</li>
   * </ul>
   *
   * @param results list of matching DTOs from peer lookup
   * @return the best matching DTO, or null if list is empty
   */
  private SecurityGtnetLookupDTO selectBestMatch(List<SecurityGtnetLookupDTO> results) {
    return results.stream()
        .max(Comparator.comparingInt(this::calculateMatchScore))
        .orElse(null);
  }

  /**
   * Calculates the match score for a DTO to determine best match.
   */
  private int calculateMatchScore(SecurityGtnetLookupDTO dto) {
    int score = 0;

    // Connector match score (0-4)
    if (dto.getConnectorMatchScore() != null) {
      score += dto.getConnectorMatchScore();
    }

    // Asset class match type
    String matchType = dto.getAssetClassMatchType();
    if ("EXACT".equals(matchType)) {
      score += 3;
    } else if ("SCHEME_MATCH".equals(matchType)) {
      score += 2;
    } else if ("PARTIAL".equals(matchType)) {
      score += 1;
    }

    // Stock exchange MIC presence
    if (dto.getStockexchangeMic() != null && !dto.getStockexchangeMic().isEmpty()) {
      score += 1;
    }

    return score;
  }

  /**
   * Checks if a matching security already exists locally.
   * First tries ISIN + currency, then ticker + currency.
   */
  private Security findExistingSecurity(SecurityGtnetLookupDTO dto) {
    // Try ISIN + currency first
    if (dto.getIsin() != null && !dto.getIsin().isBlank() && dto.getCurrency() != null) {
      Security found = securityJpaRepository.findByIsinAndCurrency(dto.getIsin(), dto.getCurrency());
      if (found != null) {
        return found;
      }
    }

    // Try ticker + currency
    if (dto.getTickerSymbol() != null && !dto.getTickerSymbol().isBlank() && dto.getCurrency() != null) {
      Security found = securityJpaRepository.findByTickerSymbolAndCurrency(dto.getTickerSymbol(), dto.getCurrency());
      if (found != null) {
        return found;
      }
    }

    return null;
  }

  /**
   * Creates a new Security entity from the GTNet lookup DTO.
   *
   * @param dto the lookup result DTO containing security metadata
   * @param idTenant the tenant ID for the security (null for public securities)
   * @param idCreatedByUser the user ID to set as created_by (Spring audit doesn't work in background tasks)
   * @return the created and persisted Security, or null if required data is missing
   */
  private Security createSecurityFromDTO(SecurityGtnetLookupDTO dto, Integer idTenant, Integer idCreatedByUser) {
    // Find asset class
    Assetclass assetclass = findOrCreateAssetclass(dto);
    if (assetclass == null) {
      log.warn("Could not find asset class for security {}", dto.getIsin());
      return null;
    }

    // Find stock exchange
    Stockexchange stockexchange = findStockexchange(dto);
    if (stockexchange == null) {
      log.warn("Could not find stock exchange for security {}", dto.getIsin());
      return null;
    }

    Security security = new Security();

    // Basic identification
    security.setName(dto.getName());
    security.setIsin(dto.getIsin());
    security.setTickerSymbol(dto.getTickerSymbol());
    security.setCurrency(dto.getCurrency());

    // Asset class and stock exchange
    security.setAssetClass(assetclass);
    security.setStockexchange(stockexchange);

    // Connector settings
    if (dto.getMatchedHistoryConnector() != null) {
      security.setIdConnectorHistory(dto.getMatchedHistoryConnector());
      security.setUrlHistoryExtend(dto.getMatchedHistoryUrlExtension());
    }
    if (dto.getMatchedIntraConnector() != null) {
      security.setIdConnectorIntra(dto.getMatchedIntraConnector());
      security.setUrlIntraExtend(dto.getMatchedIntraUrlExtension());
    }
    if (dto.getMatchedDividendConnector() != null) {
      security.setIdConnectorDividend(dto.getMatchedDividendConnector());
      security.setUrlDividendExtend(dto.getMatchedDividendUrlExtension());
    }
    if (dto.getMatchedSplitConnector() != null) {
      security.setIdConnectorSplit(dto.getMatchedSplitConnector());
      security.setUrlSplitExtend(dto.getMatchedSplitUrlExtension());
    }

    // Other properties
    security.setDenomination(dto.getDenomination());
    security.setProductLink(dto.getProductLink());
    security.setStockexchangeLink(dto.getStockexchangeLink());
    security.setLeverageFactor(dto.getLeverageFactor() != null ? dto.getLeverageFactor() : 1.0f);
    security.setDistributionFrequency(dto.getDistributionFrequency() != null
        ? dto.getDistributionFrequency()
        : DistributionFrequency.DF_NONE);

    // Active dates
    security.setActiveFromDate(dto.getActiveFromDate() != null
        ? dto.getActiveFromDate()
        : new Date());
    security.setActiveToDate(dto.getActiveToDate() != null
        ? dto.getActiveToDate()
        : DateHelper.getDateFromLocalDate(LocalDate.of(2099, 12, 31)));

    // Set created_by manually (Spring audit doesn't work in background tasks)
    if (idCreatedByUser != null) {
      security.setCreatedBy(idCreatedByUser);
    }

    // Save and return
    return securityJpaRepository.save(security);
  }

  /**
   * Finds a matching local asset class using the matched ID from the DTO,
   * or falls back to finding by category type and special investment instrument.
   */
  private Assetclass findOrCreateAssetclass(SecurityGtnetLookupDTO dto) {
    // Use matched asset class ID if available
    if (dto.getMatchedAssetClassId() != null) {
      return assetclassJpaRepository.findById(dto.getMatchedAssetClassId()).orElse(null);
    }

    // Fallback: find by category type and special investment instrument
    if (dto.getCategoryType() != null && dto.getSpecialInvestmentInstrument() != null) {
      List<Assetclass> candidates = assetclassJpaRepository.findByCategoryTypeAndSpecialInvestmentInstrument(
          dto.getCategoryType().getValue(), dto.getSpecialInvestmentInstrument().getValue());
      if (!candidates.isEmpty()) {
        return candidates.get(0);
      }
    }

    return null;
  }

  /**
   * Finds a matching stock exchange by MIC code, then by name.
   * Falls back to the first available stock exchange if no match found.
   */
  private Stockexchange findStockexchange(SecurityGtnetLookupDTO dto) {
    // Try MIC code first
    if (dto.getStockexchangeMic() != null && !dto.getStockexchangeMic().isEmpty()) {
      Stockexchange found = stockexchangeJpaRepository.findByMic(dto.getStockexchangeMic()).orElse(null);
      if (found != null) {
        return found;
      }
    }

    // Try name
    if (dto.getStockexchangeName() != null && !dto.getStockexchangeName().isEmpty()) {
      Stockexchange found = stockexchangeJpaRepository.findByName(dto.getStockexchangeName());
      if (found != null) {
        return found;
      }
    }

    // Fallback to first available stock exchange (should not normally happen)
    List<Stockexchange> all = stockexchangeJpaRepository.findAllByOrderByNameAsc();
    return all.isEmpty() ? null : all.get(0);
  }

  /**
   * Checks if the user has reached their daily limit for GTNet security imports.
   * Only applies to users with ROLE_LIMIT_EDIT role.
   *
   * @param user the user performing the import
   * @return true if limit is exceeded and no more securities can be created, false otherwise
   */
  private boolean isLimitExceeded(User user) {
    Optional<UserCountLimit> userCountLimitOpt = userEntityChangeCountJpaRepository
        .getCudTransactionAndUserLimit(user.getIdUser(), ENTITY_NAME_GTNET_SECURITY_IMPORT);

    if (userCountLimitOpt.isPresent()) {
      Integer limit = userCountLimitOpt.get().getDayLimit() != null
          ? userCountLimitOpt.get().getDayLimit()
          : globalparametersJpaRepository.getMaxValueByKey(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_GTNETSECURITYIMPORT);
      int cudTransaction = userCountLimitOpt.get().getCudTrans();
      return cudTransaction >= limit;
    }
    return false;
  }

  /**
   * Logs a security creation operation for daily limit tracking.
   *
   * @param idUser the ID of the user who created the security
   */
  private void logSecurityCreation(Integer idUser) {
    UserEntityChangeCount userEntityChangeCount = userEntityChangeCountJpaRepository
        .findById(new UserEntityChangeCountId(idUser, new Date(), ENTITY_NAME_GTNET_SECURITY_IMPORT))
        .orElse(new UserEntityChangeCount(new UserEntityChangeCountId(idUser, new Date(), ENTITY_NAME_GTNET_SECURITY_IMPORT)));
    userEntityChangeCount.incrementCounter(OperationType.ADD);
    userEntityChangeCountJpaRepository.save(userEntityChangeCount);
  }
}
