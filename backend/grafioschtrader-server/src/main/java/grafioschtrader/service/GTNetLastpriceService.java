package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetEntity;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.m2m.GTNetMessageHelper;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetSupplierDetail;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.handler.impl.lastprice.PushOpenLastpriceQueryStrategy;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.gtnet.model.msg.LastpriceExchangeMsg;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetExchangeLogJpaRepository;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;
import grafioschtrader.repository.GTNetSupplierDetailJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Service for orchestrating intraday price exchange with GTNet providers.
 *
 * This service implements the consumer-side flow for intraday price sharing:
 * <ol>
 *   <li>Filter instruments by GTNetExchange.lastpriceRecv configuration</li>
 *   <li>Query push-open servers first (by priority with random selection for same priority)</li>
 *   <li>Query open servers for remaining unfilled instruments</li>
 *   <li>Fall back to connectors (IFeedConnector) for any still-unfilled instruments</li>
 *   <li>If own mode is push-open: push connector-fetched prices back to remote servers</li>
 * </ol>
 *
 * @see InstrumentExchangeSet for tracking which instruments have been filled
 */
@Service
public class GTNetLastpriceService extends BaseGTNetExchangeService {

  private static final Logger log = LoggerFactory.getLogger(GTNetLastpriceService.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetSupplierDetailJpaRepository gtNetSupplierDetailJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private PushOpenLastpriceQueryStrategy pushOpenLastpriceQueryStrategy;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private GTNetExchangeLogService gtNetExchangeLogService;

  @Autowired
  private GTNetExchangeLogJpaRepository gtNetExchangeLogJpaRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Main entry point for intraday price update with GTNet integration.
   * Called from WatchlistReport.executeLastPriceUpdate().
   *
   * @param securities list of securities from the watchlist
   * @param currencypairs list of currency pairs from the watchlist
   * @param currenciesNotInList additional currency pairs needed for calculations
   * @return updated securities and currency pairs
   */
  public SecurityCurrency updateLastpriceIncludeSupplier(List<Security> securities,
      List<Currencypair> currencypairs, List<Currencypair> currenciesNotInList) {

    // Check if GTNet is enabled
    if (!globalparametersJpaRepository.isGTNetEnabled()) {
      // GTNet disabled - fall back to connectors only
      currencypairJpaRepository.updateLastPriceByList(currenciesNotInList);
      return new SecurityCurrency(
          securityJpaRepository.updateLastPriceByList(securities),
          currencypairJpaRepository.updateLastPriceByList(currencypairs));
    }

    try {
      return executeGTNetExchange(securities, currencypairs, currenciesNotInList);
    } catch (Exception e) {
      log.error("GTNet exchange failed, falling back to connectors only", e);
      currencypairJpaRepository.updateLastPriceByList(currenciesNotInList);
      return new SecurityCurrency(
          securityJpaRepository.updateLastPriceByList(securities),
          currencypairJpaRepository.updateLastPriceByList(currencypairs));
    }
  }

  private SecurityCurrency executeGTNetExchange(List<Security> securities,
      List<Currencypair> currencypairs, List<Currencypair> currenciesNotInList) {

    long totalStart = System.nanoTime();
    long stepStart;

    // 1. Get IDs of instruments configured for GTNet receive (from both securities and currency pairs)
    stepStart = System.nanoTime();
    Set<Integer> gtNetEnabledIds = new HashSet<>(securityJpaRepository.findIdsWithGtNetLastpriceRecv());
    gtNetEnabledIds.addAll(currencypairJpaRepository.findIdsWithGtNetLastpriceRecv());
    log.debug("Step 1 - Get GTNet enabled IDs: {} ms", (System.nanoTime() - stepStart) / 1_000_000);

    // 2. Build instrument sets - separate GTNet-enabled from connector-only
    stepStart = System.nanoTime();
    InstrumentExchangeSet gtNetInstruments = new InstrumentExchangeSet();
    List<Security> connectorOnlySecurities = new ArrayList<>();
    List<Currencypair> connectorOnlyCurrencypairs = new ArrayList<>();

    for (Security security : securities) {
      if (gtNetEnabledIds.contains(security.getIdSecuritycurrency())) {
        gtNetInstruments.addSecurity(security);
      } else {
        connectorOnlySecurities.add(security);
      }
    }

    for (Currencypair currencypair : currencypairs) {
      if (gtNetEnabledIds.contains(currencypair.getIdSecuritycurrency())) {
        gtNetInstruments.addCurrencypair(currencypair);
      } else {
        connectorOnlyCurrencypairs.add(currencypair);
      }
    }
    log.debug("Step 2 - Build instrument sets: {} ms", (System.nanoTime() - stepStart) / 1_000_000);

    log.info("GTNet exchange: {} instruments enabled, {} securities connector-only, {} pairs connector-only",
        gtNetInstruments.getTotalCount(), connectorOnlySecurities.size(), connectorOnlyCurrencypairs.size());

    // 2b. Load supplier details for filtering AC_OPEN requests
    stepStart = System.nanoTime();
    SupplierInstrumentFilter filter = null;
    List<Integer> allInstrumentIds = gtNetInstruments.getAllInstrumentIds();
    if (!allInstrumentIds.isEmpty()) {
      List<GTNetSupplierDetail> supplierDetails = gtNetSupplierDetailJpaRepository
          .findByEntityKindAndInstrumentIds(GTNetExchangeKindType.LAST_PRICE.getValue(), allInstrumentIds);
      filter = new SupplierInstrumentFilter(supplierDetails);
    }
    log.debug("Step 2b - Load supplier details: {} ms", (System.nanoTime() - stepStart) / 1_000_000);

    // 2c. Load success rates for AC_OPEN supplier scoring (past 30 days)
    LocalDate fromDate = LocalDate.now().minusDays(30);
    List<Object[]> successRateData = gtNetExchangeLogJpaRepository
        .getSupplierSuccessRates(GTNetExchangeKindType.LAST_PRICE.getValue(), fromDate);
    SupplierScoreCalculator scoreCalculator = new SupplierScoreCalculator(successRateData);
    Set<Integer> requestedInstrumentIds = new HashSet<>(allInstrumentIds);

    // 3. If local server is AC_PUSH_OPEN, query own push pool first (before contacting remote servers)
    if (needsFilling(gtNetInstruments)) {
      stepStart = System.nanoTime();
      queryLocalPushPoolIfPushOpen(gtNetInstruments);
      log.debug("Step 3 - Query local push pool: {} ms", (System.nanoTime() - stepStart) / 1_000_000);
    }

    // 4. Query push-open servers by priority (excluding own entry to prevent self-communication)
    // PUSH_OPEN uses priority+random algorithm (unchanged)
    if (needsFilling(gtNetInstruments)) {
      stepStart = System.nanoTime();
      List<GTNet> pushOpenSuppliers = getSuppliersByPriorityWithRandomization(
          excludeOwnEntry(gtNetJpaRepository.findPushOpenSuppliers()), GTNetExchangeKindType.LAST_PRICE);
      queryRemoteServers(pushOpenSuppliers, gtNetInstruments);
      log.debug("Step 4 - Query push-open servers: {} ms", (System.nanoTime() - stepStart) / 1_000_000);
    }

    // 5. Query open servers for remaining (excluding own entry to prevent self-communication)
    // AC_OPEN uses score-based selection: coverage x success_rate, then priority, then random
    if (needsFilling(gtNetInstruments)) {
      stepStart = System.nanoTime();
      List<GTNet> openSuppliers = getSuppliersByScoreWithRandomization(
          excludeOwnEntry(gtNetJpaRepository.findOpenSuppliers()),
          GTNetExchangeKindType.LAST_PRICE,
          scoreCalculator,
          filter,
          requestedInstrumentIds);
      queryRemoteServersFiltered(openSuppliers, gtNetInstruments, filter);
      log.debug("Step 5 - Query open servers: {} ms", (System.nanoTime() - stepStart) / 1_000_000);
    }

    // 6. Collect unfilled instruments for connector fallback
    stepStart = System.nanoTime();
    List<Security> remainingSecurities = gtNetInstruments.getUnfilledSecurities();
    remainingSecurities.addAll(connectorOnlySecurities);

    List<Currencypair> remainingPairs = gtNetInstruments.getUnfilledCurrencypairs();
    remainingPairs.addAll(connectorOnlyCurrencypairs);
    log.debug("Step 6 - Collect unfilled instruments: {} ms", (System.nanoTime() - stepStart) / 1_000_000);

    log.info("GTNet exchange complete: {} securities remaining for connector, {} currency pairs remaining",
        remainingSecurities.size(), remainingPairs.size());

    // 7. Fall back to connectors for remaining instruments
    stepStart = System.nanoTime();
    currencypairJpaRepository.updateLastPriceByList(currenciesNotInList);
    List<Security> updatedSecurities = securityJpaRepository.updateLastPriceByList(remainingSecurities);
    List<Currencypair> updatedPairs = currencypairJpaRepository.updateLastPriceByList(remainingPairs);
    log.debug("Step 7 - Connector fallback: {} ms", (System.nanoTime() - stepStart) / 1_000_000);

    // 8. If local server is AC_PUSH_OPEN, update push pool with connector-fetched prices
    stepStart = System.nanoTime();
    updatePushPoolIfPushOpen(updatedSecurities, updatedPairs);
    log.debug("Step 8 - Update push pool: {} ms", (System.nanoTime() - stepStart) / 1_000_000);

    // 9. Combine with already-filled instruments
    stepStart = System.nanoTime();
    List<Security> allSecurities = new ArrayList<>(gtNetInstruments.getAllSecurities());
    for (Security s : updatedSecurities) {
      if (!containsSecurity(allSecurities, s)) {
        allSecurities.add(s);
      }
    }

    List<Currencypair> allPairs = new ArrayList<>(gtNetInstruments.getAllCurrencypairs());
    for (Currencypair cp : updatedPairs) {
      if (!containsCurrencypair(allPairs, cp)) {
        allPairs.add(cp);
      }
    }
    log.debug("Step 9 - Combine results: {} ms", (System.nanoTime() - stepStart) / 1_000_000);

    log.debug("Total executeGTNetExchange: {} ms", (System.nanoTime() - totalStart) / 1_000_000);
    return new SecurityCurrency(allSecurities, allPairs);
  }

  /**
   * Queries remote servers for price data (for AC_PUSH_OPEN suppliers - no filtering).
   */
  private void queryRemoteServers(List<GTNet> suppliers, InstrumentExchangeSet instruments) {
    for (GTNet supplier : suppliers) {
      if (instruments.allFilled()) {
        break;
      }

      try {
        queryRemoteServer(supplier, instruments, null);
      } catch (Exception e) {
        e.printStackTrace();
        log.warn("Failed to query GTNet server {}: {}", supplier.getDomainRemoteName(), e.getMessage());
      }
    }
  }

  /**
   * Queries remote servers with instrument filtering (for AC_OPEN suppliers).
   * Only sends instruments that the supplier is known to support based on GTNetSupplierDetail.
   */
  private void queryRemoteServersFiltered(List<GTNet> suppliers, InstrumentExchangeSet instruments,
      SupplierInstrumentFilter filter) {
    for (GTNet supplier : suppliers) {
      if (instruments.allFilled()) {
        break;
      }

      try {
        queryRemoteServer(supplier, instruments, filter);
      } catch (Exception e) {
        e.printStackTrace();
        log.warn("Failed to query GTNet server {}: {}", supplier.getDomainRemoteName(), e.getMessage());
      }
    }
  }

  /**
   * Queries a single remote server for price data.
   *
   * @param supplier the GTNet supplier to query
   * @param instruments the instrument exchange set
   * @param filter optional filter for AC_OPEN suppliers (null for AC_PUSH_OPEN)
   */
  private void queryRemoteServer(GTNet supplier, InstrumentExchangeSet instruments, SupplierInstrumentFilter filter) {
    GTNetConfig config = supplier.getGtNetConfig();
    if (config == null || !config.isAuthorizedRemoteEntry()) {
      log.debug("Skipping unauthorized server: {}", supplier.getDomainRemoteName());
      return;
    }

    // Build request with unfilled instruments, their current timestamps, and freshness threshold
    List<InstrumentPriceDTO> securityDTOs;
    List<InstrumentPriceDTO> currencypairDTOs;

    if (filter != null) {
      // AC_OPEN supplier: filter instruments to only those this supplier is known to support
      Set<Integer> allUnfilledIds = new HashSet<>(instruments.getAllInstrumentIds());
      Set<Integer> allowedIds = filter.getInstrumentsForSupplier(supplier.getIdGtNet(), allUnfilledIds, false);

      if (allowedIds.isEmpty()) {
        log.debug("No supported instruments for AC_OPEN supplier {}, skipping", supplier.getDomainRemoteName());
        return;
      }

      securityDTOs = instruments.getUnfilledSecurityDTOsFiltered(allowedIds);
      currencypairDTOs = instruments.getUnfilledCurrencypairDTOsFiltered(allowedIds);
    } else {
      // AC_PUSH_OPEN supplier: send all unfilled instruments (no filtering)
      securityDTOs = instruments.getUnfilledSecurityDTOs();
      currencypairDTOs = instruments.getUnfilledCurrencypairDTOs();
    }

    if (securityDTOs.isEmpty() && currencypairDTOs.isEmpty()) {
      return;
    }

    // Calculate minimum acceptable timestamp for freshness filtering
    Date minAcceptableTimestamp = globalparametersService.getGTNetLastpriceMinAcceptableTimestamp();

    LastpriceExchangeMsg requestPayload = LastpriceExchangeMsg.forRequest(securityDTOs, currencypairDTOs,
        minAcceptableTimestamp);

    // Get local GTNet entry for source identification (use findById to eagerly load - async context has no session)
    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository);
    GTNet myGTNet = gtNetJpaRepository.findById(myGTNetId)
        .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found: " + myGTNetId));

    // Build MessageEnvelope with source identification
    MessageEnvelope requestEnvelope = new MessageEnvelope();
    requestEnvelope.sourceDomain = myGTNet.getDomainRemoteName();
    requestEnvelope.sourceGtNet = new GTNetPublicDTO(myGTNet);
    requestEnvelope.serverBusy = myGTNet.isServerBusy();
    requestEnvelope.messageCode = GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C.getValue();
    requestEnvelope.timestamp = new Date();
    requestEnvelope.payload = objectMapper.valueToTree(requestPayload);

    log.debug("Sending lastprice request to {} with {} securities, {} pairs",
        supplier.getDomainRemoteName(), securityDTOs.size(), currencypairDTOs.size());

    // Send request
    SendResult result = baseDataClient.sendToMsgWithStatus(
        config.getTokenRemote(),
        supplier.getDomainRemoteName(),
        requestEnvelope);

    if (result.isFailed()) {
      if (result.httpError()) {
        log.warn("GTNet server {} returned HTTP error {}", supplier.getDomainRemoteName(), result.httpStatusCode());
      } else {
        log.warn("GTNet server {} is unreachable", supplier.getDomainRemoteName());
      }
      return;
    }

    MessageEnvelope response = result.response();
    if (response == null || response.payload == null) {
      log.debug("No price data received from {}", supplier.getDomainRemoteName());
      return;
    }

    // Parse response
    try {
      LastpriceExchangeMsg responsePayload = objectMapper.treeToValue(response.payload, LastpriceExchangeMsg.class);

      if (responsePayload == null) {
        log.debug("Empty response payload from {}", supplier.getDomainRemoteName());
        return;
      }

      int responseCount = 0;
      if (responsePayload.securities != null) {
        responseCount += responsePayload.securities.size();
      }
      if (responsePayload.currencypairs != null) {
        responseCount += responsePayload.currencypairs.size();
      }

      log.info("Received {} price updates from {}", responseCount, supplier.getDomainRemoteName());

      // Process response - update instruments and mark as filled
      int updatedCount = instruments.processResponse(responsePayload.securities, responsePayload.currencypairs);

      // Log exchange statistics as consumer
      int entitiesSent = securityDTOs.size() + currencypairDTOs.size();
      gtNetExchangeLogService.logAsConsumer(supplier, GTNetExchangeKindType.LAST_PRICE,
          entitiesSent, updatedCount, responseCount);

    } catch (JsonProcessingException e) {
      log.error("Failed to parse lastprice response from {}", supplier.getDomainRemoteName(), e);
    }
  }

  /**
   * Queries the local push pool if this server is configured as AC_PUSH_OPEN.
   * This allows the server to use its own cached prices before contacting remote servers.
   *
   * @param instruments the instrument set to query and update
   */
  private void queryLocalPushPoolIfPushOpen(InstrumentExchangeSet instruments) {
    // Get local GTNet entry
    Integer myGTNetId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myGTNetId == null) {
      return;
    }

    Optional<GTNet> myGTNetOpt = gtNetJpaRepository.findById(myGTNetId);
    if (myGTNetOpt.isEmpty()) {
      return;
    }

    GTNet myGTNet = myGTNetOpt.get();

    // Check if we have an AC_PUSH_OPEN LAST_PRICE entity
    Optional<GTNetEntity> lastpriceEntity = myGTNet.getEntityByKind(GTNetExchangeKindType.LAST_PRICE.getValue());
    if (lastpriceEntity.isEmpty()) {
      return;
    }

    AcceptRequestTypes acceptMode = lastpriceEntity.get().getAcceptRequest();
    if (acceptMode != AcceptRequestTypes.AC_PUSH_OPEN) {
      return;
    }

    log.debug("Querying local push pool (AC_PUSH_OPEN mode)");

    // Query local push pool for securities (no freshness threshold for own pool)
    List<InstrumentPriceDTO> securityDTOs = instruments.getUnfilledSecurityDTOs();
    if (!securityDTOs.isEmpty()) {
      List<InstrumentPriceDTO> securityPrices = pushOpenLastpriceQueryStrategy.querySecurities(
          securityDTOs, Collections.emptySet(), null);
      if (!securityPrices.isEmpty()) {
        instruments.processResponse(securityPrices, null);
        log.debug("Local push pool: found {} security prices", securityPrices.size());
      }
    }

    // Query local push pool for currency pairs (no freshness threshold for own pool)
    List<InstrumentPriceDTO> currencypairDTOs = instruments.getUnfilledCurrencypairDTOs();
    if (!currencypairDTOs.isEmpty()) {
      List<InstrumentPriceDTO> currencypairPrices = pushOpenLastpriceQueryStrategy.queryCurrencypairs(
          currencypairDTOs, Collections.emptySet(), null);
      if (!currencypairPrices.isEmpty()) {
        instruments.processResponse(null, currencypairPrices);
        log.debug("Local push pool: found {} currencypair prices", currencypairPrices.size());
      }
    }
  }

  /**
   * Updates the GTNetLastprice* push pool tables with connector-fetched prices if this server is AC_PUSH_OPEN.
   * This ensures that prices obtained from connectors are available for remote clients requesting data.
   *
   * For each instrument:
   * - If no entry exists in the push pool, creates a new one
   * - If an entry exists but the connector price is newer, updates it
   * - If an entry exists with a newer or equal timestamp, skips it
   *
   * @param securities list of securities with updated prices from connectors
   * @param currencypairs list of currency pairs with updated prices from connectors
   */
  private void updatePushPoolIfPushOpen(List<Security> securities, List<Currencypair> currencypairs) {
    // Get local GTNet entry
    Integer myGTNetId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myGTNetId == null) {
      return;
    }

    Optional<GTNet> myGTNetOpt = gtNetJpaRepository.findById(myGTNetId);
    if (myGTNetOpt.isEmpty()) {
      return;
    }

    GTNet myGTNet = myGTNetOpt.get();

    // Check if we have an AC_PUSH_OPEN LAST_PRICE entity
    Optional<GTNetEntity> lastpriceEntity = myGTNet.getEntityByKind(GTNetExchangeKindType.LAST_PRICE.getValue());
    if (lastpriceEntity.isEmpty()) {
      return;
    }

    AcceptRequestTypes acceptMode = lastpriceEntity.get().getAcceptRequest();
    if (acceptMode != AcceptRequestTypes.AC_PUSH_OPEN) {
      return;
    }

    log.debug("Updating push pool with connector-fetched prices (AC_PUSH_OPEN mode)");

    // Update securities in push pool
    if (securities != null && !securities.isEmpty()) {
      int securityCount = gtNetInstrumentSecurityJpaRepository.updateFromConnectorFetch(securities);
      if (securityCount > 0) {
        log.debug("Updated {} securities in push pool", securityCount);
      }
    }

    // Update currency pairs in push pool
    if (currencypairs != null && !currencypairs.isEmpty()) {
      int currencypairCount = gtNetInstrumentCurrencypairJpaRepository.updateFromConnectorFetch(currencypairs);
      if (currencypairCount > 0) {
        log.debug("Updated {} currency pairs in push pool", currencypairCount);
      }
    }
  }

  private boolean containsSecurity(List<Security> list, Security security) {
    return list.stream().anyMatch(s -> s.getIdSecuritycurrency().equals(security.getIdSecuritycurrency()));
  }

  private boolean containsCurrencypair(List<Currencypair> list, Currencypair currencypair) {
    return list.stream().anyMatch(cp -> cp.getIdSecuritycurrency().equals(currencypair.getIdSecuritycurrency()));
  }

  private boolean needsFilling(InstrumentExchangeSet instruments) {
    return !instruments.isEmpty() && !instruments.allFilled();
  }

  /**
   * Excludes the local server's own entry from the supplier list.
   * This prevents the server from attempting to query itself for data, which would fail token validation.
   *
   * @param suppliers list of GTNet supplier entries
   * @return filtered list excluding the local server's entry
   */
  private List<GTNet> excludeOwnEntry(List<GTNet> suppliers) {
    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myEntryId == null) {
      return suppliers;
    }
    return suppliers.stream()
        .filter(supplier -> !supplier.getIdGtNet().equals(myEntryId))
        .collect(Collectors.toList());
  }

  /**
   * Result container for security and currency pair lists.
   */
  public static class SecurityCurrency {
    public List<Security> securities;
    public List<Currencypair> currencypairs;

    public SecurityCurrency(final List<Security> securities, final List<Currencypair> currencypairs) {
      this.securities = securities;
      this.currencypairs = currencypairs;
    }
  }
}
