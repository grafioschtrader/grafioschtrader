package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.GTNet;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.entities.UserEntityChangeCount;
import grafiosch.entities.UserEntityChangeCount.UserEntityChangeCountId;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.UserEntityChangeCountJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.service.EntityLimitService;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.OperationType;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.GTNetSecurityImpGap;
import grafioschtrader.entities.GTNetSecurityImpHead;
import grafioschtrader.entities.GTNetSecurityImpPos;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.gtnet.GTNetSecurityLookupService;
import grafioschtrader.gtnet.model.ConnectorHint;
import grafioschtrader.gtnet.model.ConnectorHint.ConnectorCapability;
import grafioschtrader.gtnet.model.SecurityGtnetLookupDTO;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageResponseMsg;
import grafioschtrader.repository.AssetclassJpaRepository;
import grafioschtrader.repository.GTNetSecurityImpGapJpaRepository;
import grafioschtrader.repository.GTNetSecurityImpHeadJpaRepository;
import grafioschtrader.repository.GTNetSecurityImpPosJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.service.GTNetHistoricalImportService;
import grafioschtrader.service.GTNetHistoricalImportService.PeerSelection;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.GapCodeType;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Background task that imports securities from GTNet peers for positions in a GTNetSecurityImpHead batch.
 *
 * For each position without a linked security, this task:
 * <ol>
 *   <li>Queries GTNet peers using batch lookup</li>
 *   <li>Selects the best match from results using a scoring algorithm</li>
 *   <li>Either links an existing local security or creates a new one</li>
 *   <li>Updates the GTNetSecurityImpPos with the security reference</li>
 *   <li>When triggered from transaction import context, also assigns the security to matching
 *       ImportTransactionPos entries (identified by ISIN or ticker symbol)</li>
 * </ol>
 *
 * After security creation, this task immediately imports historical price data:
 * <ol>
 *   <li>Queries GTNet peers for coverage metadata (message code 85) to find peer with longest history</li>
 *   <li>Fetches historical prices from the selected peer (message code 80)</li>
 *   <li>Falls back to connector if no GTNet peer has data</li>
 *   <li>Updates GTNetSecurityImpPos with import status and source peer information</li>
 * </ol>
 *
 * @see TaskTypeExtended#GTNET_SECURITY_IMPORT_POSITIONS
 * @see GTNetHistoricalImportService for the historical import orchestration
 */
@Component
public class GTNetSecurityImportTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetSecurityImportTask.class);

  @Autowired
  private EntityLimitService entityLimitService;

  @Autowired
  private GTNetSecurityImpHeadJpaRepository gtNetSecurityImpHeadJpaRepository;

  @Autowired
  private GTNetSecurityImpPosJpaRepository gtNetSecurityImpPosJpaRepository;

  @Autowired
  private GTNetSecurityImpGapJpaRepository gtNetSecurityImpGapJpaRepository;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

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

  @Autowired
  private ImportTransactionPosJpaRepository importTransactionPosJpaRepository;

  @Autowired
  private GTNetHistoricalImportService gtNetHistoricalImportService;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  private static final String ENTITY_NAME_GTNET_SECURITY_IMPORT = LimitKeyConfig.ENTITY_NAME_GTNET_SECURITY_IMPORT;

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

    ImportContext ctx = buildImportContext(taskDataChange);

    GTNetSecurityImpHead head = gtNetSecurityImpHeadJpaRepository.findById(idHead).orElse(null);
    if (head == null) {
      log.error("GTNetSecurityImpHead not found for ID: {}", idHead);
      return;
    }

    List<GTNetSecurityImpPos> positions = gtNetSecurityImpPosJpaRepository
        .findByIdGtNetSecurityImpHeadAndSecurityIsNull(idHead);

    if (positions.isEmpty()) {
      log.info("No unmatched positions found for head ID: {}", idHead);
      return;
    }

    log.info("Processing {} unmatched positions", positions.size());

    Map<Integer, List<SecurityGtnetLookupDTO>> lookupResults =
        gtNetSecurityLookupService.lookupSecuritiesBatch(positions);

    int[] counters = {0, 0, 0, 0}; // created, linked, failed, processed
    List<GTNetSecurityImpPos> pendingHistoricalImports = new ArrayList<>();

    for (GTNetSecurityImpPos pos : positions) {
      counters[3]++;
      processPosition(pos, lookupResults, head, ctx, counters, positions.size(), pendingHistoricalImports);
    }

    log.info("GTNet security import completed for head {}: {} created, {} linked, {} failed",
        idHead, counters[0], counters[1], counters[2]);

    // Process historical imports for newly created securities
    if (!pendingHistoricalImports.isEmpty()) {
      processHistoricalImports(pendingHistoricalImports);
    }
  }

  /**
   * Builds the import context from task parameters.
   */
  private ImportContext buildImportContext(TaskDataChange taskDataChange) {
    ImportContext ctx = new ImportContext();

    if (taskDataChange.getOldValueNumber() != null) {
      ctx.idCreatedByUser = taskDataChange.getOldValueNumber().intValue();
      ctx.user = userJpaRepository.findById(ctx.idCreatedByUser).orElse(null);
      log.debug("Using user ID {} for created_by field", ctx.idCreatedByUser);
    }

    ctx.fromImport = taskDataChange.getOldValueString() != null && !taskDataChange.getOldValueString().isEmpty();

    if (ctx.fromImport) {
      log.info("Import context detected (idTransactionHead={}), will auto-update matching ImportTransactionPos entries",
          taskDataChange.getOldValueString());
    }

    return ctx;
  }

  /**
   * Processes a single position: finds matches, links or creates security.
   *
   * @param pos the position to process
   * @param lookupResults batch lookup results keyed by position ID
   * @param head the import head containing tenant info
   * @param ctx the import context with user and flags
   * @param counters array of [created, linked, failed, processed]
   * @param totalPositions total number of positions for limit calculation
   * @param pendingHistoricalImports list to track positions that need historical import
   */
  private void processPosition(GTNetSecurityImpPos pos, Map<Integer, List<SecurityGtnetLookupDTO>> lookupResults,
      GTNetSecurityImpHead head, ImportContext ctx, int[] counters, int totalPositions,
      List<GTNetSecurityImpPos> pendingHistoricalImports) throws TaskBackgroundException {
    try {
      gtNetSecurityImpGapJpaRepository.deleteByIdGtNetSecurityImpPos(pos.getIdGtNetSecurityImpPos());

      List<SecurityGtnetLookupDTO> matches = lookupResults.get(pos.getIdGtNetSecurityImpPos());

      if (matches == null || matches.isEmpty()) {
        log.debug("No matches found for position ID={}, ISIN={}, ticker={}",
            pos.getIdGtNetSecurityImpPos(), pos.getIsin(), pos.getTickerSymbol());
        counters[2]++;
        return;
      }

      SecurityGtnetLookupDTO bestMatch = selectBestMatch(matches);
      if (bestMatch == null) {
        log.debug("Could not select best match for position ID={}", pos.getIdGtNetSecurityImpPos());
        counters[2]++;
        return;
      }

      recordGapsForMatch(pos.getIdGtNetSecurityImpPos(), bestMatch);

      Security existingSecurity = findExistingSecurity(bestMatch);
      if (existingSecurity != null) {
        linkExistingSecurity(pos, existingSecurity, ctx);
        counters[1]++;
        return;
      }

      checkAndEnforceLimits(ctx, counters, totalPositions);
      createAndLinkNewSecurity(pos, bestMatch, head, ctx, counters, pendingHistoricalImports);

    } catch (TaskBackgroundException tbe) {
      throw tbe;
    } catch (Exception e) {
      log.error("Error processing position ID={}: {}", pos.getIdGtNetSecurityImpPos(), e.getMessage(), e);
      counters[2]++;
    }
  }

  /**
   * Links an existing security to a position and auto-assigns to import positions if applicable.
   */
  private void linkExistingSecurity(GTNetSecurityImpPos pos, Security security, ImportContext ctx) {
    pos.setSecurity(security);
    // Left false on purpose: the security already existed, so it keeps counting against the ordinary Security cap.
    pos.setSecurityCreatedByImport(false);
    gtNetSecurityImpPosJpaRepository.save(pos);
    log.debug("Linked existing security {} to position {}", security.getIdSecuritycurrency(), pos.getIdGtNetSecurityImpPos());

    if (ctx.fromImport) {
      int updated = importTransactionPosJpaRepository.assignSecurityToMatchingImportPositions(security);
      if (updated > 0) {
        log.info("Auto-assigned linked security {} to {} ImportTransactionPos entries",
            security.getIdSecuritycurrency(), updated);
      }
    }
  }

  /**
   * Enforces both budgets that bound securities created by an import, before the next one is written.
   *
   * <p>
   * There is deliberately no role test here. Which users a budget applies to is a matter of configuration: the
   * resolver picks a row written for the user, then one for the user's most privileged role, then the default row of
   * the key, and no row at all means unlimited. The former {@code isLimitedEditingUser} gate contradicted that - an
   * administrator could not bound an {@code ALLEDIT} or {@code ADMIN} account here even by writing a row.
   * </p>
   */
  private void checkAndEnforceLimits(ImportContext ctx, int[] counters, int totalPositions)
      throws TaskBackgroundException {
    if (isLimitExceeded(ctx.user)) {
      int remaining = totalPositions - counters[3];
      log.warn("Daily limit exceeded for user {}. Created: {}, Linked: {}, Remaining: {}",
          ctx.idCreatedByUser, counters[0], counters[1], remaining);
      throw new TaskBackgroundException("GTNET_IMPORT_LIMIT_EXCEEDED", false);
    }
    // The lifetime cap on import created securities was registered, seeded and reported but never enforced.
    if (!entityLimitService.fitsWithinLimit(ctx.user, LimitKeyConfig.KEY_GTNET_SECURITY_IMPORT, null, 1)) {
      log.warn("Lifetime limit for import created securities reached for user {}", ctx.idCreatedByUser);
      throw new TaskBackgroundException("GTNET_IMPORT_MAX_EXCEEDED", false);
    }
  }

  /**
   * Creates a new security from the DTO, links it to the position, and adds to pending historical imports list.
   */
  private void createAndLinkNewSecurity(GTNetSecurityImpPos pos, SecurityGtnetLookupDTO bestMatch,
      GTNetSecurityImpHead head, ImportContext ctx, int[] counters,
      List<GTNetSecurityImpPos> pendingHistoricalImports) {
    Security newSecurity = createSecurityFromDTO(bestMatch, head.getIdTenant(), ctx.idCreatedByUser);

    if (newSecurity != null) {
      pos.setSecurity(newSecurity);
      // Marks the security as created by this import rather than merely matched by it, which is what separates the
      // GTNetSecurityImport lifetime cap from the ordinary Security cap.
      pos.setSecurityCreatedByImport(true);
      gtNetSecurityImpPosJpaRepository.save(pos);
      log.debug("Created and linked new security {} to position {}",
          newSecurity.getIdSecuritycurrency(), pos.getIdGtNetSecurityImpPos());
      counters[0]++;

      // Track for historical import
      pendingHistoricalImports.add(pos);

      if (ctx.idCreatedByUser != null) {
        logSecurityCreation(ctx.idCreatedByUser);
      }

      if (ctx.fromImport) {
        int updated = importTransactionPosJpaRepository.assignSecurityToMatchingImportPositions(newSecurity);
        if (updated > 0) {
          log.info("Auto-assigned created security {} to {} ImportTransactionPos entries",
              newSecurity.getIdSecuritycurrency(), updated);
        }
      }
    } else {
      log.warn("Failed to create security for position ID={}", pos.getIdGtNetSecurityImpPos());
      counters[2]++;
    }
  }

  /**
   * Holds context information for an import operation.
   */
  private static class ImportContext {
    Integer idCreatedByUser;
    User user;
    boolean fromImport;
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
    Assetclass assetclass = findOrCreateAssetclass(dto);
    if (assetclass == null) {
      log.warn("Could not find asset class for security {}", dto.getIsin());
      return null;
    }

    Stockexchange stockexchange = findStockexchange(dto);
    if (stockexchange == null) {
      log.warn("Could not find stock exchange for security {}", dto.getIsin());
      return null;
    }

    Security security = new Security();
    security.setName(dto.getName());
    security.setIsin(dto.getIsin());
    security.setTickerSymbol(dto.getTickerSymbol());
    security.setCurrency(dto.getCurrency());
    security.setAssetClass(assetclass);
    security.setStockexchange(stockexchange);

    applyConnectorSettings(security, dto);
    applyOptionalProperties(security, dto);

    if (idCreatedByUser != null) {
      security.setCreatedBy(idCreatedByUser);
    }

    return securityJpaRepository.save(security);
  }

  /**
   * Applies connector settings from DTO to security.
   */
  private void applyConnectorSettings(Security security, SecurityGtnetLookupDTO dto) {
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
  }

  /**
   * Applies optional properties and dates from DTO to security.
   */
  private void applyOptionalProperties(Security security, SecurityGtnetLookupDTO dto) {
    security.setDenomination(dto.getDenomination());
    security.setProductLink(dto.getProductLink());
    security.setStockexchangeLink(dto.getStockexchangeLink());
    security.setLeverageFactor(dto.getLeverageFactor() != null ? dto.getLeverageFactor() : 1.0f);
    security.setDistributionFrequency(dto.getDistributionFrequency() != null
        ? dto.getDistributionFrequency() : DistributionFrequency.DF_NONE);
    security.setActiveFromDate(dto.getActiveFromDate() != null
        ? dto.getActiveFromDate() : LocalDate.now());
    security.setActiveToDate(dto.getActiveToDate() != null
        ? dto.getActiveToDate() : LocalDate.of(2099, 12, 31));
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
   * Checks whether the user has reached today's budget for GTNet security imports. Applies to whichever users an
   * {@code entity_limit} row is configured for; no row at all means unlimited.
   *
   * @param user the user performing the import, may be {@code null} in a context without one
   * @return true if the budget is exhausted and no more securities may be created
   */
  private boolean isLimitExceeded(User user) {
    if (user == null) {
      return false;
    }
    Optional<Integer> limitOpt = entityLimitService.resolve(user, LimitKeyConfig.KEY_DAY_GTNET_SECURITY_IMPORT);
    // No configured row means unlimited, which is why returning false here is still correct.
    return limitOpt.isPresent() && userEntityChangeCountJpaRepository.getCudTransactionCount(user.getIdUser(),
        ENTITY_NAME_GTNET_SECURITY_IMPORT) >= limitOpt.get();
  }

  /**
   * Logs a security creation operation for daily limit tracking.
   *
   * @param idUser the ID of the user who created the security
   */
  private void logSecurityCreation(Integer idUser) {
    UserEntityChangeCount userEntityChangeCount = userEntityChangeCountJpaRepository
        .findById(new UserEntityChangeCountId(idUser, LocalDate.now(), ENTITY_NAME_GTNET_SECURITY_IMPORT))
        .orElse(new UserEntityChangeCount(new UserEntityChangeCountId(idUser, LocalDate.now(), ENTITY_NAME_GTNET_SECURITY_IMPORT)));
    userEntityChangeCount.incrementCounter(OperationType.ADD);
    userEntityChangeCountJpaRepository.save(userEntityChangeCount);
  }

  /**
   * Records gaps (mismatches) for a lookup result. Gap records document what didn't match between
   * the remote peer's data and local configuration.
   *
   * <p>Gaps are recorded for:
   * <ul>
   *   <li>Asset class mismatch: when the asset class match type is not EXACT</li>
   *   <li>Connector gaps: when the remote peer has a connector hint but no local connector was matched</li>
   * </ul>
   *
   * @param idGtNetSecurityImpPos the position ID to record gaps for
   * @param dto the lookup result DTO containing match information
   */
  private void recordGapsForMatch(Integer idGtNetSecurityImpPos, SecurityGtnetLookupDTO dto) {
    Integer idGtNet = resolveGtNetId(dto.getSourceDomain());
    if (idGtNet == null) {
      log.warn("Could not resolve GTNet ID for domain: {}", dto.getSourceDomain());
      return;
    }

    List<GTNetSecurityImpGap> gaps = new ArrayList<>();

    if (dto.getMatchedAssetClassId() == null || !"EXACT".equals(dto.getAssetClassMatchType())) {
      String assetClassMessage = buildAssetClassMessage(dto);
      gaps.add(new GTNetSecurityImpGap(idGtNetSecurityImpPos, idGtNet, GapCodeType.ASSET_CLASS, assetClassMessage));
      log.debug("Asset class gap for position {}: {}", idGtNetSecurityImpPos, assetClassMessage);
    }

    collectConnectorGaps(gaps, idGtNetSecurityImpPos, idGtNet, dto);

    if (!gaps.isEmpty()) {
      gtNetSecurityImpGapJpaRepository.saveAll(gaps);
      log.info("Recorded {} gaps for position {}", gaps.size(), idGtNetSecurityImpPos);
    }
  }

  /**
   * Checks connector hints and adds gaps for unmatched connectors.
   */
  private void collectConnectorGaps(List<GTNetSecurityImpGap> gaps, Integer idPos, Integer idGtNet,
      SecurityGtnetLookupDTO dto) {
    if (dto.getConnectorHints() == null) {
      return;
    }
    for (ConnectorHint hint : dto.getConnectorHints()) {
      if (hint.getCapabilities() == null) {
        continue;
      }
      checkConnectorGap(gaps, idPos, idGtNet, hint, ConnectorCapability.INTRADAY,
          dto.getMatchedIntraConnector(), GapCodeType.INTRADAY_CONNECTOR);
      checkConnectorGap(gaps, idPos, idGtNet, hint, ConnectorCapability.HISTORY,
          dto.getMatchedHistoryConnector(), GapCodeType.HISTORY_CONNECTOR);
      checkConnectorGap(gaps, idPos, idGtNet, hint, ConnectorCapability.DIVIDEND,
          dto.getMatchedDividendConnector(), GapCodeType.DIVIDEND_CONNECTOR);
      checkConnectorGap(gaps, idPos, idGtNet, hint, ConnectorCapability.SPLIT,
          dto.getMatchedSplitConnector(), GapCodeType.SPLIT_CONNECTOR);
    }
  }

  /**
   * Adds a gap if the hint has the capability but no connector was matched.
   */
  private void checkConnectorGap(List<GTNetSecurityImpGap> gaps, Integer idPos, Integer idGtNet,
      ConnectorHint hint, ConnectorCapability capability, String matchedConnector, GapCodeType gapCode) {
    if (hint.getCapabilities().contains(capability) && matchedConnector == null) {
      gaps.add(new GTNetSecurityImpGap(idPos, idGtNet, gapCode, hint.getConnectorFamily()));
      log.debug("{} gap for position {}: {}", gapCode, idPos, hint.getConnectorFamily());
    }
  }

  /**
   * Resolves the GTNet ID from a domain name.
   *
   * @param domainRemoteName the domain name to look up
   * @return the GTNet ID, or null if not found
   */
  private Integer resolveGtNetId(String domainRemoteName) {
    if (domainRemoteName == null || domainRemoteName.isBlank()) {
      return null;
    }
    GTNet gtNet = gtNetJpaRepository.findByDomainRemoteName(domainRemoteName);
    return gtNet != null ? gtNet.getIdGtNet() : null;
  }

  /**
   * Builds a human-readable message describing the expected asset class configuration.
   *
   * @param dto the lookup result DTO
   * @return a message in format "categoryType / subCategory / specialInvestmentInstrument"
   */
  private String buildAssetClassMessage(SecurityGtnetLookupDTO dto) {
    StringBuilder sb = new StringBuilder();

    // Category type
    if (dto.getCategoryType() != null) {
      sb.append(dto.getCategoryType().name());
    } else {
      sb.append("?");
    }
    sb.append(" / ");

    // Sub-category (use English if available)
    if (dto.getSubCategoryNLS() != null && !dto.getSubCategoryNLS().isEmpty()) {
      String subCategory = dto.getSubCategoryNLS().get("en");
      if (subCategory == null) {
        subCategory = dto.getSubCategoryNLS().values().iterator().next();
      }
      sb.append(subCategory);
    } else {
      sb.append("?");
    }
    sb.append(" / ");

    // Special investment instrument
    if (dto.getSpecialInvestmentInstrument() != null) {
      sb.append(dto.getSpecialInvestmentInstrument().name());
    } else {
      sb.append("?");
    }

    return sb.toString();
  }

  /**
   * Processes historical price data import for newly created securities.
   *
   * For each position with a newly created security:
   * <ol>
   *   <li>Queries all eligible GTNet peers for coverage metadata (message code 85)</li>
   *   <li>Selects the peer with the longest historical coverage</li>
   *   <li>Fetches historical data from the selected peer (message code 80)</li>
   *   <li>Falls back to connector if no GTNet peer has data</li>
   * </ol>
   *
   * @param pendingPositions list of positions with newly created securities needing historical data
   */
  private void processHistoricalImports(List<GTNetSecurityImpPos> pendingPositions) {
    if (pendingPositions.isEmpty()) {
      return;
    }

    log.info("Starting historical import for {} newly created securities", pendingPositions.size());

    // Collect all securities needing historical data
    List<Security> securities = pendingPositions.stream()
        .filter(p -> p.getSecurity() != null)
        .map(GTNetSecurityImpPos::getSecurity)
        .toList();

    if (securities.isEmpty()) {
      return;
    }

    // Query all eligible peers for coverage metadata
    Map<GTNet, HistoryquoteCoverageResponseMsg> coverageByPeer =
        gtNetHistoricalImportService.queryCoverageFromPeers(securities);

    int gtnetLoaded = 0;
    int connectorLoaded = 0;
    int failed = 0;

    // Process each position
    for (GTNetSecurityImpPos pos : pendingPositions) {
      Security security = pos.getSecurity();
      if (security == null) {
        continue;
      }

      try {
        // Select best peer for this security
        PeerSelection bestPeer = gtNetHistoricalImportService.selectBestPeer(
            coverageByPeer, security.getIsin(), security.getCurrency());

        if (bestPeer != null) {
          // Fetch historical data from selected GTNet peer
          List<Historyquote> quotes = gtNetHistoricalImportService.fetchFromPeer(
              bestPeer.peer, security, bestPeer.minDate, bestPeer.maxDate);

          if (!quotes.isEmpty()) {
            historyquoteJpaRepository.saveAll(quotes);
            gtnetLoaded++;
            log.debug("Loaded {} historyquotes from GTNet for security {}",
                quotes.size(), security.getIdSecuritycurrency());
            continue;
          }
        }

        // Fallback to connector
        if (gtNetHistoricalImportService.fallbackToConnector(security)) {
          connectorLoaded++;
          log.debug("Loaded historyquotes via connector fallback for security {}",
              security.getIdSecuritycurrency());
        } else {
          failed++;
          log.debug("Failed to load historyquotes for security {}", security.getIdSecuritycurrency());
        }

      } catch (Exception e) {
        log.warn("Error importing historical data for security {}: {}",
            security.getIdSecuritycurrency(), e.getMessage());
        failed++;
      }
    }

    log.info("Historical import completed: {} from GTNet, {} from connector, {} failed",
        gtnetLoaded, connectorLoaded, failed);
  }
}
